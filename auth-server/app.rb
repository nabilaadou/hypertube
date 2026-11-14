require "sinatra/base"
require "digest"
require "json"
require "net/http"
require "securerandom"

class AuthServer < Sinatra::Base
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
  end

  get "/" do
    @failure = session.delete("failure")
    @csrf = Rack::Protection::AuthenticityToken.token(session)
    erb :index
  end

  get "/auth/:provider/callback" do
    auth = request.env["omniauth.auth"]
    halt 404 unless auth && settings.providers.dig(params[:provider], :enabled)

    proof = SecureRandom.hex(32)
    identity = {
      provider: auth.provider == "marvin" ? "42" : "google",
      providerUserId: auth.uid.to_s,
      displayName: auth.info.name.to_s.strip[0, 128],
      email: auth.info.email.to_s.strip[0, 254],
      proofHash: Digest::SHA256.hexdigest(proof)
    }
    handoff = Net::HTTP::Post.new(settings.handoff_url)
    handoff["Authorization"] = "Bearer #{settings.handoff_secret}"
    handoff["Content-Type"] = "application/json"
    handoff.body = JSON.generate(identity.transform_values { |value| value.empty? ? nil : value })
    result = Net::HTTP.start(settings.handoff_url.host, settings.handoff_url.port,
                             use_ssl: settings.handoff_url.scheme == "https",
                             open_timeout: 3, read_timeout: 5) { |http| http.request(handoff) }
    unless result.is_a?(Net::HTTPOK) && result.body.to_s.match?(/\A[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\z/)
      halt 502, "Spring Boot could not finish sign-in. Return to the sign-in page and retry."
    end

    response.set_cookie("hypertube_login_proof", value: proof, path: "/api/auth/complete",
                        max_age: 120, httponly: true, same_site: :lax,
                        secure: settings.spring_public_url.scheme == "https")
    redirect "#{settings.spring_public_url}/api/auth/complete?code=#{result.body}", 303
  rescue Timeout::Error, IOError, SystemCallError, SocketError
    halt 502, "Spring Boot is unavailable. Return to the sign-in page and retry."
  end

  error do
    "The auth test could not complete. Return to the sign-in page and retry."
  end
end
