require "sinatra/base"
require "digest"
require "json"
require "net/http"
require "securerandom"

class AuthServer < Sinatra::Base
  LOGIN_COMPLETE_PATH = "/api/auth/login-handoffs/complete".freeze
  LOGIN_PROOF_PATH = "/api/auth/login-handoffs".freeze
  LOGIN_CODE_FORMAT = /\A[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\z/

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

  get "/auth/:provider/callback" do
    provider_account = request.env["omniauth.auth"]
    provider = settings.providers[provider_account&.provider]
    halt 404 unless provider&.fetch(:enabled) && provider_account.provider == params[:provider] &&
                    !provider_account.uid.to_s.empty?

    browser_proof = SecureRandom.hex(32)
    identity = {
      provider: provider.fetch(:id),
      providerUserId: provider_account.uid.to_s,
      displayName: provider_account.info.name.to_s.strip[0, 128],
      email: provider_account.info.email.to_s.strip[0, 254],
      proofHash: Digest::SHA256.hexdigest(browser_proof)
    }
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
