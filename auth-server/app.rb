require "sinatra/base"
require "digest"
require "json"
require "net/http"
require "securerandom"
require "jwt"

class AuthServer < Sinatra::Base
  LOGIN_COMPLETE_PATH = "/api/auth/login-handoffs/complete".freeze
  LOGIN_PROOF_PATH = "/api/auth/login-handoffs".freeze
  LOGIN_CODE_FORMAT = /\A[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\z/

  def self.clear_reauth_session(session)
    return unless session

    %w[hypertube.reauth_challenge hypertube.reauth_proof hypertube.reauth_started hypertube.reauth_nonce].each do |key|
      session.delete(key)
    end
  end

  set :show_exceptions, false
  set :dump_errors, false
  set :static, false
  set :protection, except: :json_csrf

  use Rack::Protection::JsonCsrf, allow_if: ->(env) {
    env["REQUEST_METHOD"] == "GET" &&
      %w[/auth/google_oauth2/callback /auth/marvin/callback].include?(env["PATH_INFO"]) &&
      env["hypertube.authenticated_callback"] == true
  }

  before do
    content_type :json
    headers "Cache-Control" => "no-store",
            "Referrer-Policy" => "no-referrer",
            "Content-Security-Policy" => "default-src 'none'; frame-ancestors 'none'"
  end

  helpers do
    def verify_google_reauth(account)
      nonce = session.delete("hypertube.reauth_nonce").to_s
      started = session.delete("hypertube.reauth_started").to_i
      token = account.extra&.id_token.to_s
      halt 401, JSON.generate(error: "reauth_failed") if nonce.empty? || started.zero? || token.empty?
      uri = URI("https://www.googleapis.com/oauth2/v3/certs")
      response = Net::HTTP.start(uri.host, uri.port, use_ssl: true, open_timeout: 3, read_timeout: 5) do |http|
        http.get(uri.request_uri)
      end
      raise "key retrieval failed" unless response.is_a?(Net::HTTPSuccess)
      keys = JWT::JWK::Set.new(JSON.parse(response.body))
      claims = JWT.decode(token, nil, true, algorithms: ["RS256"], jwks: keys,
                          required_claims: %w[iss aud exp sub nonce],
                          iss: ["accounts.google.com", "https://accounts.google.com"], verify_iss: true,
                          aud: settings.google_client_id, verify_aud: true, verify_expiration: true).first
      raise "invalid identity" unless claims["nonce"] == nonce && claims["sub"] == account.uid
      auth_time = claims["auth_time"]
      raise "stale authentication" if auth_time && (!auth_time.is_a?(Numeric) || auth_time < started || auth_time > Time.now.to_i + 60)
    rescue StandardError
      halt 401, JSON.generate(error: "reauth_failed")
    end

    def complete_reauth(challenge, secret, identity)
      url = settings.reauth_callback_url
      request = Net::HTTP::Post.new(url)
      request["Authorization"] = "Bearer #{settings.login_handoff_secret}"
      request["Content-Type"] = "application/json"
      request.body = JSON.generate(challenge: challenge, secret: secret,
                                   provider: identity[:provider], providerUserId: identity[:providerUserId])
      result = Net::HTTP.start(url.host, url.port, use_ssl: url.scheme == "https",
                               open_timeout: 3, read_timeout: 5) { |http| http.request(request) }
      halt 401, JSON.generate(error: "reauth_failed") unless result.is_a?(Net::HTTPNoContent)
    rescue Timeout::Error, IOError, SystemCallError, SocketError
      halt 502, JSON.generate(error: "reauth_unavailable")
    end

    def create_login_handoff(identity)
      handoff_url = settings.login_handoff_url
      handoff_request = Net::HTTP::Post.new(handoff_url)
      handoff_request["Authorization"] = "Bearer #{settings.login_handoff_secret}"
      handoff_request["Content-Type"] = "application/json"
      handoff_request.body = JSON.generate(identity)

      handoff_response = Net::HTTP.start(handoff_url.host, handoff_url.port,
                                         use_ssl: handoff_url.scheme == "https",
                                         open_timeout: 3, read_timeout: 5) do |http|
        http.request(handoff_request)
      end

      login_code = handoff_response.body.to_s
      unless handoff_response.is_a?(Net::HTTPOK) && LOGIN_CODE_FORMAT.match?(login_code)
        halt 502, JSON.generate(error: "handoff_failed")
      end

      login_code
    rescue Timeout::Error, IOError, SystemCallError, SocketError
      halt 502, JSON.generate(error: "handoff_unavailable")
    end
  end

  get "/auth/providers" do
    JSON.generate(
      csrfToken: Rack::Protection::AuthenticityToken.token(session),
      providers: settings.providers.select { |_, provider| provider.fetch(:enabled) }.keys
    )
  end

  post "/auth/reauth/start" do
    halt 403 unless Rack::Protection::AuthenticityToken.new(nil).accepts?(request.env)
    challenge = params[:challenge].to_s
    halt 400, JSON.generate(error: "invalid_challenge") unless LOGIN_CODE_FORMAT.match?(challenge)
    halt 400, JSON.generate(error: "missing_browser_proof") if request.cookies["hypertube_reauth"].to_s.empty?
    session.delete("omniauth.state")
    session["hypertube.reauth_challenge"] = challenge
    session["hypertube.reauth_started"] = Time.now.to_i
    session["hypertube.reauth_proof"] = request.cookies["hypertube_reauth"]
    JSON.generate(status: "ready")
  end

  get "/auth/:provider/callback" do
    provider_account = request.env["omniauth.auth"]
    provider = settings.providers[provider_account&.provider]
    unless provider&.fetch(:enabled) && provider_account.provider == params[:provider] && !provider_account.uid.to_s.empty?
      AuthServer.clear_reauth_session(request.env["rack.session"])
      halt 404
    end

    browser_proof = SecureRandom.hex(32)
    identity = {
      provider: provider.fetch(:id),
      providerUserId: provider_account.uid.to_s,
      displayName: provider_account.info.name.to_s.strip[0, 128],
      email: provider_account.info.email.to_s.strip[0, 254],
      proofHash: Digest::SHA256.hexdigest(browser_proof)
    }
    challenge = request.env["rack.session"]&.delete("hypertube.reauth_challenge")
    if challenge
      begin
        verify_google_reauth(provider_account) if provider.fetch(:id) == "google"
        complete_reauth(challenge, session["hypertube.reauth_proof"].to_s, identity)
        request.env["hypertube.authenticated_callback"] = true
        return JSON.generate(status: "verified")
      ensure
        AuthServer.clear_reauth_session(session)
      end
    end
    login_code = create_login_handoff(identity.transform_values { |value| value.empty? ? nil : value })

    response.delete_cookie("hypertube_login_proof", path: LOGIN_COMPLETE_PATH)
    response.set_cookie("hypertube_login_proof", value: browser_proof, path: LOGIN_PROOF_PATH,
                        max_age: 120, httponly: true, same_site: :lax,
                        secure: settings.spring_public_url.scheme == "https")
    request.env["hypertube.authenticated_callback"] = true
    JSON.generate(code: login_code, completionUrl: "#{settings.spring_public_url.origin}#{LOGIN_COMPLETE_PATH}")
  end

  not_found do
    JSON.generate(error: "not_found")
  end

  error do
    JSON.generate(error: "sign_in_failed")
  end
end
