require "json"
require "rack/mock"
require "rack/builder"
require "uri"

# Isolate configuration from the container's real environment and credentials.
ENV.update(
  "AUTH_BASE_URL" => "http://localhost:4567",
  "SPRING_PUBLIC_URL" => "http://localhost:8080",
  "SPRING_INTERNAL_URL" => "http://localhost:8080",
  "AUTH_HANDOFF_SECRET" => "test-handoff-secret",
  "AUTH_SESSION_SECRET" => "test-session-secret-" * 8,
  "GOOGLE_CLIENT_ID" => "test-google-id",
  "GOOGLE_CLIENT_SECRET" => "test-google-secret",
  "FORTY_TWO_CLIENT_ID" => "test-42-id",
  "FORTY_TWO_CLIENT_SECRET" => "test-42-secret"
)
app, = Rack::Builder.parse_file(File.expand_path("../config.ru", __dir__))

# Stub only provider I/O and the backend handoff. OAuth request and callback
# handling, including state validation and the signed session, remain real.
module FakeProviderIO
  def fetch_access_token
    $token_exchanges += 1
    Object.new
  end

  def auth_hash
    OmniAuth::AuthHash.new(provider: options.name, uid: "test-user",
                           info: { name: "Test User", email: "test@example.com" })
  end
end
OmniAuth::Strategies::GoogleOauth2.prepend(FakeProviderIO)
OmniAuth::Strategies::Marvin.prepend(FakeProviderIO)
AuthServer.class_eval do
  def create_login_handoff(_identity)
    $handoffs += 1
    "7d87e5dc-cf71-421f-9e7c-853155898d67"
  end
end

request = Rack::MockRequest.new(app)
base = "http://localhost:4567"

def session_cookie(response)
  Array(response["set-cookie"]).flat_map { |header| header.split("\n") }
    .find { |cookie| cookie.start_with?("hypertube_provider_test=") }&.split(";", 2)&.first
end

def begin_sign_in(request, base, provider)
  providers = request.get("#{base}/auth/providers", "HTTP_HOST" => "localhost:4567")
  raise "providers returned #{providers.status}: #{providers.body}" unless providers.status == 200
  cookie = session_cookie(providers) or raise "missing session cookie"
  csrf = JSON.parse(providers.body).fetch("csrfToken")
  start = request.post("#{base}/auth/#{provider}",
                       "HTTP_COOKIE" => cookie,
                       "HTTP_HOST" => "localhost:4567",
                       params: { "authenticity_token" => csrf })
  raise "request phase returned #{start.status}" unless start.status == 302
  cookie = session_cookie(start) || cookie
  state = URI.decode_www_form(URI(start["location"]).query).to_h.fetch("state")
  [cookie, state]
end

%w[google_oauth2 marvin].each do |provider|
  ["missing", "incorrect", "valid"].each do |scenario|
    $token_exchanges = 0
    $handoffs = 0
    cookie, state = begin_sign_in(request, base, provider)
    query = { "code" => "test-code" }
    query["state"] = scenario == "valid" ? state : "wrong-state" unless scenario == "missing"
    result = request.get("#{base}/auth/#{provider}/callback?#{URI.encode_www_form(query)}",
                         "HTTP_COOKIE" => cookie,
                         "HTTP_HOST" => "localhost:4567",
                         "HTTP_REFERER" => "https://accounts.google.com/")
    expected_status = scenario == "valid" ? 200 : 400
    expected_calls = scenario == "valid" ? 1 : 0
    unless result.status == expected_status && $token_exchanges == expected_calls && $handoffs == expected_calls
      raise "#{provider} #{scenario}: status=#{result.status}, exchanges=#{$token_exchanges}, handoffs=#{$handoffs}"
    end
    puts "#{provider} #{scenario} state: status=#{result.status}, exchanges=#{$token_exchanges}, handoffs=#{$handoffs}"
  end
end
puts "OAuth stack state validation passed"
