require "omniauth"
require "omniauth-google-oauth2"
require "rack/session/cookie"
require "uri"
require_relative "strategies/marvin"
require_relative "app"

origin = URI.parse(ENV.fetch("AUTH_BASE_URL", "http://localhost:4567"))
unless origin.is_a?(URI::HTTP) && origin.host && !origin.userinfo &&
       ["", "/"].include?(origin.path) && !origin.query && !origin.fragment
  abort "AUTH_BASE_URL must be an HTTP or HTTPS origin without a path."
end
base_url = origin.to_s.delete_suffix("/")
session_secret = ENV.fetch("AUTH_SESSION_SECRET", "")
abort "Set AUTH_SESSION_SECRET to at least 64 bytes. Generate one with openssl rand -hex 64." if session_secret.bytesize < 64
handoff_secret = ENV.fetch("AUTH_HANDOFF_SECRET", "")
abort "Set AUTH_HANDOFF_SECRET in the root .env. Generate one with openssl rand -hex 32." if handoff_secret.bytesize < 64

spring_public_url = URI.parse(ENV.fetch("SPRING_PUBLIC_URL", "http://localhost:8080"))
unless spring_public_url.is_a?(URI::HTTP) && spring_public_url.host == origin.host &&
       spring_public_url.scheme == origin.scheme && !spring_public_url.userinfo &&
       spring_public_url.path.empty? && !spring_public_url.query && !spring_public_url.fragment
  abort "SPRING_PUBLIC_URL must use the same host and scheme as AUTH_BASE_URL, without a path."
end

providers = {
  "marvin" => ["42", ENV["FORTY_TWO_CLIENT_ID"], ENV["FORTY_TWO_CLIENT_SECRET"]],
  "google_oauth2" => ["Google", ENV["GOOGLE_CLIENT_ID"], ENV["GOOGLE_CLIENT_SECRET"]]
}
configured = providers.select { |_, (_, id, secret)| !id.to_s.strip.empty? && !secret.to_s.strip.empty? }

AuthServer.set :providers, providers.transform_values(&:first)
AuthServer.set :configured_providers, configured.keys
AuthServer.set :base_url, base_url
AuthServer.set :spring_public_url, spring_public_url
AuthServer.set :handoff_secret, handoff_secret
AuthServer.set :handoff_url, URI.join(ENV.fetch("SPRING_INTERNAL_URL", "http://localhost:8080"), "/internal/auth/identities")
AuthServer.set :host_authorization, { permitted_hosts: [origin.hostname] }

OmniAuth.config.full_host = base_url
OmniAuth.config.allowed_hosts = [origin.host]
OmniAuth.config.logger = Logger.new(File::NULL)
OmniAuth.config.on_failure = lambda do |env|
  env["rack.session"]["failure"] = env["omniauth.error.type"] == :access_denied ? "cancelled" : "failed"
  [303, { "location" => "/", "cache-control" => "no-store", "referrer-policy" => "no-referrer" }, []]
end

use Rack::Session::Cookie,
    key: "hypertube_provider_test",
    secret: session_secret,
    same_site: :lax,
    httponly: true,
    secure: origin.scheme == "https",
    expire_after: 600

use OmniAuth::Builder do
  if configured.key?("marvin")
    _, id, secret = configured.fetch("marvin")
    provider OmniAuth::Strategies::Marvin, id, secret, origin_param: false
  end

  if configured.key?("google_oauth2")
    _, id, secret = configured.fetch("google_oauth2")
    provider :google_oauth2, id, secret,
             scope: "openid email profile",
             access_type: "online",
             overridable_authorize_options: [],
             origin_param: false
  end
end

run AuthServer
