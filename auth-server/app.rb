require "sinatra/base"
require "digest"
require "json"
require "net/http"
require "securerandom"

class AuthServer < Sinatra::Base
  LOGIN_COMPLETE_PATH = "/api/auth/login-handoffs/complete".freeze
  LOGIN_CODE_FORMAT = /\A[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\z/

  set :show_exceptions, false
  set :dump_errors, false
  set :absolute_redirects, false

  before do
    headers "Cache-Control" => "no-store",
            "Referrer-Policy" => "no-referrer",
            "Content-Security-Policy" => "default-src 'none'; style-src 'self'; base-uri 'none'; frame-ancestors 'none'"
  end

  helpers do
    def h(value)
      Rack::Utils.escape_html(value.to_s)
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
        halt 502, "Spring Boot could not finish sign-in. Return to the sign-in page and retry."
      end

      login_code
    rescue Timeout::Error, IOError, SystemCallError, SocketError
      halt 502, "Spring Boot is unavailable. Return to the sign-in page and retry."
    end
  end

  get "/" do
    @sign_in_failure = session.delete("failure")
    @csrf_token = Rack::Protection::AuthenticityToken.token(session)
    erb :index
  end

  get "/auth/:provider/callback" do
    provider_account = request.env["omniauth.auth"]
    provider = settings.providers[provider_account&.provider]
    halt 404 unless provider&.fetch(:enabled)

    browser_proof = SecureRandom.hex(32)
    identity = {
      provider: provider.fetch(:id),
      providerUserId: provider_account.uid.to_s,
      displayName: provider_account.info.name.to_s.strip[0, 128],
      email: provider_account.info.email.to_s.strip[0, 254],
      proofHash: Digest::SHA256.hexdigest(browser_proof)
    }
    @login_code = create_login_handoff(identity.transform_values { |value| value.empty? ? nil : value })

    response.set_cookie("hypertube_login_proof", value: browser_proof, path: LOGIN_COMPLETE_PATH,
                        max_age: 120, httponly: true, same_site: :lax,
                        secure: settings.spring_public_url.scheme == "https")
    @login_complete_url = "#{settings.spring_public_url.origin}#{LOGIN_COMPLETE_PATH}"
    @script_nonce = SecureRandom.base64(16)
    headers "Content-Security-Policy" => "default-src 'none'; style-src 'self'; " \
              "script-src 'nonce-#{@script_nonce}'; form-action #{settings.spring_public_url.origin}; " \
              "base-uri 'none'; frame-ancestors 'none'"
    erb :complete
  end

  error do
    "The auth test could not complete. Return to the sign-in page and retry."
  end
end
