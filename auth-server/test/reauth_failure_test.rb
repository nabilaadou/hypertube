require "json"
require "rack/mock"
require "rack/builder"
require "uri"

ENV.update(
  "AUTH_BASE_URL" => "http://localhost:4567", "SPRING_PUBLIC_URL" => "http://localhost:8080",
  "SPRING_INTERNAL_URL" => "http://localhost:8080", "AUTH_HANDOFF_SECRET" => "test-secret",
  "AUTH_SESSION_SECRET" => "test-session-secret-" * 8,
  "GOOGLE_CLIENT_ID" => "test-google-id", "GOOGLE_CLIENT_SECRET" => "test-google-secret",
  "FORTY_TWO_CLIENT_ID" => "test-42-id", "FORTY_TWO_CLIENT_SECRET" => "test-42-secret"
)
app, = Rack::Builder.parse_file(File.expand_path("../config.ru", __dir__))

module FakeReauthFailureProvider
  def fetch_access_token
    Object.new
  end

  def auth_hash
    OmniAuth::AuthHash.new(provider: options.name, uid: "test-user",
                           info: { name: "Test User", email: "test@example.com" })
  end
end
OmniAuth::Strategies::GoogleOauth2.prepend(FakeReauthFailureProvider)
OmniAuth::Strategies::Marvin.prepend(FakeReauthFailureProvider)
AuthServer.class_eval do
  def create_login_handoff(_identity)
    $handoffs += 1
    "7d87e5dc-cf71-421f-9e7c-853155898d67"
  end

  def complete_reauth(*_args)
    $reauth_callbacks += 1
  end
end

def session_cookie(response, previous = nil)
  Array(response["set-cookie"]).flat_map { |value| value.split("\n") }
    .find { |value| value.start_with?("hypertube_provider_test=") }&.split(";", 2)&.first || previous
end

request = Rack::MockRequest.new(app)
base = "http://localhost:4567"
host = { "HTTP_HOST" => "localhost:4567" }
%w[google_oauth2 marvin].each do |provider|
  %w[cancelled invalid_state].each do |failure|
    $handoffs = 0
    $reauth_callbacks = 0
    providers = request.get("#{base}/auth/providers", **host)
    csrf = JSON.parse(providers.body).fetch("csrfToken")
    cookie = session_cookie(providers)
    start = request.post("#{base}/auth/reauth/start", **host,
                         "HTTP_COOKIE" => "#{cookie}; hypertube_reauth=browser-proof",
                         params: { "authenticity_token" => csrf, "challenge" => "7d87e5dc-cf71-421f-9e7c-853155898d67" })
    raise "reauth start failed" unless start.status == 200
    cookie = session_cookie(start, cookie)
    provider_start = request.post("#{base}/auth/#{provider}", **host, "HTTP_COOKIE" => cookie,
                                  params: { "authenticity_token" => csrf, "purpose" => "reauth" })
    raise "provider start failed" unless provider_start.status == 302
    cookie = session_cookie(provider_start, cookie)
    state = URI.decode_www_form(URI(provider_start["location"]).query).to_h.fetch("state")
    query = failure == "cancelled" ? { error: "access_denied", state: state } : { code: "test", state: "incorrect" }
    failed = request.get("#{base}/auth/#{provider}/callback?#{URI.encode_www_form(query)}", **host,
                         "HTTP_COOKIE" => cookie, "HTTP_REFERER" => "https://provider.example/")
    raise "failure accepted" unless failed.status == 400
    cookie = session_cookie(failed, cookie)
    raise "failure attempted backend reauth" unless $reauth_callbacks.zero?

    normal = request.post("#{base}/auth/#{provider}", **host, "HTTP_COOKIE" => cookie,
                          params: { "authenticity_token" => csrf })
    raise "normal login start failed after #{failure}" unless normal.status == 302
    cookie = session_cookie(normal, cookie)
    normal_query = URI.decode_www_form(URI(normal["location"]).query).to_h
    raise "reauth options leaked into ordinary sign-in" if normal_query.key?("max_age") || normal_query.key?("claims")
    callback = request.get("#{base}/auth/#{provider}/callback?#{URI.encode_www_form(code: 'test', state: normal_query.fetch('state'))}",
                           **host, "HTTP_COOKIE" => cookie, "HTTP_REFERER" => "https://provider.example/")
    unless callback.status == 200 && $handoffs == 1 && $reauth_callbacks.zero?
      raise "normal login after #{provider} #{failure}: status #{callback.status}, handoffs #{$handoffs}, reauth #{$reauth_callbacks}"
    end
    puts "#{provider} #{failure}: ordinary sign-in recovered"
  end
end

%w[google_oauth2 marvin].each do |provider|
  $handoffs = 0
  $reauth_callbacks = 0
  providers = request.get("#{base}/auth/providers", **host)
  csrf = JSON.parse(providers.body).fetch("csrfToken")
  cookie = session_cookie(providers)
  start = request.post("#{base}/auth/reauth/start", **host,
                       "HTTP_COOKIE" => "#{cookie}; hypertube_reauth=browser-proof",
                       params: { "authenticity_token" => csrf, "challenge" => "7d87e5dc-cf71-421f-9e7c-853155898d67" })
  raise "reauth start failed" unless start.status == 200
  cookie = session_cookie(start, cookie)
  normal = request.post("#{base}/auth/#{provider}", **host, "HTTP_COOKIE" => cookie,
                        params: { "authenticity_token" => csrf })
  raise "normal login start failed after abandoned reauth" unless normal.status == 302
  cookie = session_cookie(normal, cookie)
  query = URI.decode_www_form(URI(normal["location"]).query).to_h
  raise "abandoned reauth changed ordinary sign-in" if query.key?("max_age") || query.key?("claims")
  callback = request.get("#{base}/auth/#{provider}/callback?#{URI.encode_www_form(code: 'test', state: query.fetch('state'))}",
                         **host, "HTTP_COOKIE" => cookie, "HTTP_REFERER" => "https://provider.example/")
  unless callback.status == 200 && $handoffs == 1 && $reauth_callbacks.zero?
    raise "#{provider} abandoned reauth: status #{callback.status}, handoffs #{$handoffs}, reauth #{$reauth_callbacks}"
  end
  puts "#{provider}: ordinary sign-in recovered after abandoned reauth"
end

%w[google_oauth2 marvin].each do |provider|
  providers = request.get("#{base}/auth/providers", **host)
  csrf = JSON.parse(providers.body).fetch("csrfToken")
  cookie = session_cookie(providers)
  without_challenge = request.post("#{base}/auth/#{provider}", **host, "HTTP_COOKIE" => cookie,
                                   params: { "authenticity_token" => csrf, "purpose" => "reauth" })
  raise "#{provider} accepted reauth without a challenge" unless without_challenge.status == 400
  puts "#{provider}: marked reauth without challenge rejected"
end

$handoffs = 0
$reauth_callbacks = 0
providers = request.get("#{base}/auth/providers", **host)
csrf = JSON.parse(providers.body).fetch("csrfToken")
cookie = session_cookie(providers)
start = request.post("#{base}/auth/reauth/start", **host,
                     "HTTP_COOKIE" => "#{cookie}; hypertube_reauth=browser-proof",
                     params: { "authenticity_token" => csrf, "challenge" => "7d87e5dc-cf71-421f-9e7c-853155898d67" })
cookie = session_cookie(start, cookie)
denied = request.post("#{base}/auth/marvin", **host, "HTTP_COOKIE" => cookie)
raise "provider start without CSRF returned #{denied.status}" unless denied.status == 400
cookie = session_cookie(denied, cookie)
started = request.post("#{base}/auth/marvin", **host, "HTTP_COOKIE" => cookie,
                       params: { "authenticity_token" => csrf, "purpose" => "reauth" })
raise "CSRF failure cleared a pending reauth" unless started.status == 302
cookie = session_cookie(started, cookie)
state = URI.decode_www_form(URI(started["location"]).query).to_h.fetch("state")
result = request.get("#{base}/auth/marvin/callback?#{URI.encode_www_form(code: 'test', state: state)}", **host,
                     "HTTP_COOKIE" => cookie, "HTTP_REFERER" => "https://provider.example/")
unless result.status == 200 && $reauth_callbacks == 1 && $handoffs.zero?
  raise "marked 42 reauth did not complete through the reauthentication route"
end
puts "42: marked reauthentication survives a rejected CSRF request"
