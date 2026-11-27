require "rack/mock"
require "uri"
require_relative "../app"

AuthServer.set :providers, { "google_oauth2" => { id: "google", enabled: true } }
AuthServer.set :spring_public_url, URI("http://localhost:8080")
AuthServer.set :host_authorization, { permitted_hosts: [] }
AuthServer.class_eval do
  def create_login_handoff(_identity)
    "7d87e5dc-cf71-421f-9e7c-853155898d67"
  end
end

env = Rack::MockRequest.env_for("http://localhost:4567/auth/google_oauth2/callback",
                                "HTTP_COOKIE" => "hypertube_login_proof=old-proof")
info = Struct.new(:name, :email).new("Test User", "test@example.com")
env["omniauth.auth"] = Struct.new(:provider, :uid, :info).new("google_oauth2", "test-user", info)
status, headers, body = AuthServer.call(env)
raise "callback returned #{status}: #{body.each.to_a.join}" unless status == 200
cookies = Array(headers.fetch("set-cookie")).flat_map { |value| value.split("\n") }
old_cookie = cookies.find { |cookie| cookie.match?(%r{path=/api/auth/login-handoffs/complete(?:;|$)}i) }
new_cookie = cookies.find { |cookie| cookie.match?(%r{path=/api/auth/login-handoffs(?:;|$)}i) }
raise "legacy cookie was not cleared" unless old_cookie && old_cookie.match?(/max-age=0/i)
raise "new proof cookie was not set" unless new_cookie && new_cookie.match?(/max-age=120/i)
raise "new proof cookie must be HttpOnly" unless new_cookie.match?(/httponly/i)
raise "new proof cookie must differ from old proof" if new_cookie.include?("old-proof")
puts "OAuth proof-cookie migration passed"
