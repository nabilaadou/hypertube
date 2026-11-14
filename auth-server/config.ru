require "omniauth"
require "omniauth-google-oauth2"
require "rack/session/cookie"
require "uri"
require_relative "strategies/marvin"
require_relative "app"

def read_origin(name, default)
  url = URI.parse(ENV.fetch(name, default).delete_suffix("/"))
  return url if url.is_a?(URI::HTTP) && url.host && url.to_s == url.origin

  abort "#{name} must be an HTTP or HTTPS origin without a path, query, fragment, or credentials."
rescue URI::InvalidURIError
  abort "#{name} is not a valid URL."
end

def oauth_credentials(prefix)
  client_id = ENV["#{prefix}_CLIENT_ID"]
  client_secret = ENV["#{prefix}_CLIENT_SECRET"]
  return if client_id.to_s.strip.empty? || client_secret.to_s.strip.empty?

  [client_id, client_secret]
end

auth_url = read_origin("AUTH_BASE_URL", "http://localhost:4567")
spring_public_url = read_origin("SPRING_PUBLIC_URL", "http://localhost:8080")
unless spring_public_url.host == auth_url.host && spring_public_url.scheme == auth_url.scheme
  abort "SPRING_PUBLIC_URL must use the same host and scheme as AUTH_BASE_URL."
end

session_secret = ENV.fetch("AUTH_SESSION_SECRET", "")
abort "Set AUTH_SESSION_SECRET to at least 64 bytes. Generate one with openssl rand -hex 64." if session_secret.bytesize < 64
handoff_secret = ENV.fetch("AUTH_HANDOFF_SECRET", "")
abort "Set AUTH_HANDOFF_SECRET in the root .env. Generate one with openssl rand -hex 32." if handoff_secret.bytesize < 64

marvin_credentials = oauth_credentials("FORTY_TWO")
google_credentials = oauth_credentials("GOOGLE")

AuthServer.set :providers, {
  "marvin" => { label: "42", enabled: !marvin_credentials.nil? },
  "google_oauth2" => { label: "Google", enabled: !google_credentials.nil? }
}
AuthServer.set :base_url, auth_url.origin
AuthServer.set :spring_public_url, spring_public_url
AuthServer.set :handoff_secret, handoff_secret
AuthServer.set :handoff_url, URI.join(ENV.fetch("SPRING_INTERNAL_URL", "http://localhost:8080"), "/internal/auth/identities")
AuthServer.set :host_authorization, { permitted_hosts: [auth_url.hostname] }

OmniAuth.configure do |config|
  config.full_host = auth_url.origin
  config.allowed_hosts = [auth_url.host]
  config.logger = Logger.new(File::NULL)
  config.on_failure do |env|
    env["rack.session"]["failure"] = env["omniauth.error.type"] == :access_denied ? "cancelled" : "failed"
    [303, { "location" => "/", "cache-control" => "no-store", "referrer-policy" => "no-referrer" }, []]
  end
end

use Rack::Session::Cookie,
    key: "hypertube_provider_test",
    secret: session_secret,
    same_site: :lax,
    httponly: true,
    secure: auth_url.scheme == "https",
    expire_after: 600

use OmniAuth::Builder do
  if marvin_credentials
    provider OmniAuth::Strategies::Marvin, *marvin_credentials, origin_param: false
  end

  if google_credentials
    provider :google_oauth2, *google_credentials,
             scope: "openid email profile",
             access_type: "online",
             overridable_authorize_options: [],
             origin_param: false
  end
end

run AuthServer
