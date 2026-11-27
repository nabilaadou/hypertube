require "rack/mock"
require "uri"
require_relative "../app"

AuthServer.set :providers, {
  "google_oauth2" => { id: "google", enabled: true },
  "marvin" => { id: "42", enabled: true }
}
AuthServer.set :spring_public_url, URI("http://localhost:8080")
AuthServer.set :host_authorization, { permitted_hosts: [] }
AuthServer.class_eval do
  def create_login_handoff(_identity)
    "7d87e5dc-cf71-421f-9e7c-853155898d67"
  end
end

info = Struct.new(:name, :email).new("Test User", "test@example.com")
identity = ->(provider, uid = "test-user") { Struct.new(:provider, :uid, :info).new(provider, uid, info) }
request = lambda do |path, auth: nil, origin: nil, method: "GET"|
  env = Rack::MockRequest.env_for("http://localhost:4567#{path}",
                                  "REQUEST_METHOD" => method,
                                  "HTTP_REFERER" => "https://accounts.google.com/")
  env["HTTP_ORIGIN"] = origin if origin
  env["omniauth.auth"] = auth if auth
  status, _headers, body = AuthServer.call(env)
  body.close if body.respond_to?(:close)
  status
end

checks = {
  "Google callback" => [200, -> { request.call("/auth/google_oauth2/callback", auth: identity.call("google_oauth2")) }],
  "42 callback" => [200, -> { request.call("/auth/marvin/callback", auth: identity.call("marvin")) }],
  "providers endpoint" => [403, -> { request.call("/auth/providers") }],
  "unauthenticated callback" => [403, -> { request.call("/auth/google_oauth2/callback") }],
  "mismatched provider" => [403, -> { request.call("/auth/marvin/callback", auth: identity.call("google_oauth2")) }],
  "empty provider identity" => [403, -> { request.call("/auth/google_oauth2/callback", auth: identity.call("google_oauth2", "")) }],
  "other route" => [403, -> { request.call("/auth/google_oauth2/callback/extra", auth: identity.call("google_oauth2")) }]
}
failures = checks.filter_map do |name, (expected, run)|
  actual = run.call
  puts "#{name}: #{actual} (expected #{expected})"
  name unless actual == expected
end
origin_env = Rack::MockRequest.env_for("http://localhost:4567/auth/google_oauth2/callback", "REQUEST_METHOD" => "POST", "HTTP_ORIGIN" => "https://attacker.example")
origin_env["rack.session"] = { "marker" => "present" }
origin_env["omniauth.auth"] = identity.call("google_oauth2")
_status, _headers, body = AuthServer.call(origin_env)
body.close if body.respond_to?(:close)
puts "foreign Origin on POST: session #{origin_env["rack.session"].empty? ? 'cleared' : 'retained'} (expected cleared)"
failures << "foreign Origin on POST" unless origin_env["rack.session"].empty?
abort "Failed: #{failures.join(', ')}" unless failures.empty?
puts "OAuth callback JSON CSRF protection passed"
