require "json"
require "rack/mock"
require "rack/builder"
require "uri"
require "jwt"
require "openssl"

ENV.update("AUTH_BASE_URL" => "http://localhost:4567", "SPRING_PUBLIC_URL" => "http://localhost:8080",
           "SPRING_INTERNAL_URL" => "http://localhost:8080", "AUTH_HANDOFF_SECRET" => "test-secret",
           "AUTH_SESSION_SECRET" => "test-session-secret-" * 8,
           "GOOGLE_CLIENT_ID" => "test-google-id", "GOOGLE_CLIENT_SECRET" => "test-google-secret")
app, = Rack::Builder.parse_file(File.expand_path("../config.ru", __dir__))
$key = OpenSSL::PKey::RSA.generate(2048)
$other_key = OpenSSL::PKey::RSA.generate(2048)
$jwk = JWT::JWK.new($key.public_key, kid: "test-key").export
$mode = nil
$nonce = nil
$verified = 0

module FakeProvider
  def fetch_access_token
    Object.new
  end

  def auth_hash
    claims = { iss: "https://accounts.google.com", aud: "test-google-id", exp: Time.now.to_i + 120,
               sub: "test-user", nonce: $mode == :wrong_nonce ? "wrong" : $nonce }
    claims[:aud] = "wrong" if $mode == :wrong_audience
    claims[:sub] = "wrong" if $mode == :wrong_subject
    claims[:exp] = Time.now.to_i - 1 if $mode == :expired
    claims[:auth_time] = Time.now.to_i if $mode == :fresh
    claims[:auth_time] = Time.now.to_i - 600 if $mode == :stale
    key = $mode == :bad_signature ? $other_key : $key
    id_token = JWT.encode(claims, key, "RS256", kid: "test-key")
    OmniAuth::AuthHash.new(provider: "google_oauth2", uid: "test-user",
                           info: { name: "Test User", email: "test@example.com" }, extra: { id_token: id_token })
  end
end
OmniAuth::Strategies::GoogleOauth2.prepend(FakeProvider)

class << Net::HTTP
  alias_method :real_start_for_reauth_test, :start
  def start(host, *args, **options, &block)
    return real_start_for_reauth_test(host, *args, **options, &block) unless host == "www.googleapis.com"
    response = Net::HTTPOK.new("1.1", "200", "OK")
    response.instance_variable_set(:@read, true)
    response.body = JSON.generate(keys: [$jwk])
    fake = Object.new
    fake.define_singleton_method(:get) { |_| response }
    block.call(fake)
  end
end
AuthServer.class_eval do
  def complete_reauth(_challenge, _secret, _identity)
    $verified += 1
  end
end

request = Rack::MockRequest.new(app)
base = "http://localhost:4567"
def session_cookie(response, old = nil)
  Array(response["set-cookie"]).flat_map { |v| v.split("\n") }
    .find { |v| v.start_with?("hypertube_provider_test=") }&.split(";", 2)&.first || old
end

[:fresh, :no_auth_time, :stale, :wrong_nonce, :bad_signature, :wrong_audience, :wrong_subject, :expired, :wrong_state].each do |mode|
  $mode = mode
  $verified = 0
  response = request.get("#{base}/auth/providers", "HTTP_HOST" => "localhost:4567")
  csrf = JSON.parse(response.body).fetch("csrfToken")
  cookie = session_cookie(response)
  headers = { "HTTP_HOST" => "localhost:4567", "HTTP_COOKIE" => "#{cookie}; hypertube_reauth=browser-proof" }
  denied = request.post("#{base}/auth/reauth/start", **headers, params: { "challenge" => "7d87e5dc-cf71-421f-9e7c-853155898d67" })
  raise "missing CSRF accepted" unless denied.status == 403
  start = request.post("#{base}/auth/reauth/start", **headers,
                       params: { "authenticity_token" => csrf, "challenge" => "7d87e5dc-cf71-421f-9e7c-853155898d67" })
  raise "start failed #{start.status}" unless start.status == 200
  cookie = session_cookie(start, cookie)
  provider = request.post("#{base}/auth/google_oauth2", "HTTP_HOST" => "localhost:4567", "HTTP_COOKIE" => cookie,
                          params: { "authenticity_token" => csrf })
  raise "provider start failed #{provider.status}" unless provider.status == 302
  cookie = session_cookie(provider, cookie)
  query = URI.decode_www_form(URI(provider["location"]).query).to_h
  $nonce = query.fetch("nonce")
  raise "missing auth_time request" unless JSON.parse(query.fetch("claims")).dig("id_token", "auth_time", "essential")
  callback = request.get("#{base}/auth/google_oauth2/callback?#{URI.encode_www_form(code: 'test', state: (mode == :wrong_state ? 'wrong' : query.fetch('state')))}",
                         "HTTP_HOST" => "localhost:4567", "HTTP_COOKIE" => cookie,
                         "HTTP_REFERER" => "https://accounts.google.com/")
  expected = if mode == :wrong_state
               400
             elsif [:fresh, :no_auth_time].include?(mode)
               200
             else
               403
             end
  raise "#{mode}: status #{callback.status}, expected #{expected}" unless callback.status == expected
  raise "#{mode}: wrong callback count" unless $verified == (expected == 200 ? 1 : 0)
  puts "#{mode}: #{callback.status}"
end
