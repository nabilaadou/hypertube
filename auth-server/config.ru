require "omniauth"
require "omniauth-google-oauth2"
require "rack/session/cookie"
require "uri"
require_relative "strategies/marvin"
require_relative "strategies/google_reauth"
require_relative "app"

def oauth_credentials(provider_prefix)
  client_id = ENV["#{provider_prefix}_CLIENT_ID"]
  client_secret = ENV["#{provider_prefix}_CLIENT_SECRET"]
  return if client_id.to_s.strip.empty? || client_secret.to_s.strip.empty?

  [client_id, client_secret]
end

auth_base_url = URI(ENV.fetch("AUTH_BASE_URL"))
spring_public_url = URI(ENV.fetch("SPRING_PUBLIC_URL"))
frontend_origin = URI(ENV.fetch("FRONTEND_ORIGIN", "http://localhost:3000"))
unless frontend_origin.is_a?(URI::HTTP) && frontend_origin.host && frontend_origin.userinfo.nil? &&
       ["", "/"].include?(frontend_origin.path) && frontend_origin.query.nil? && frontend_origin.fragment.nil?
  abort "FRONTEND_ORIGIN must be an http(s) origin"
end
spring_internal_url = ENV.fetch("SPRING_INTERNAL_URL")
forty_two_credentials = oauth_credentials("FORTY_TWO")
google_credentials = oauth_credentials("GOOGLE")

AuthServer.set :providers, {
  "marvin" => { id: "42", enabled: !forty_two_credentials.nil? },
  "google_oauth2" => { id: "google", enabled: !google_credentials.nil? }
}
AuthServer.set :frontend_origin, frontend_origin.origin
AuthServer.set :google_client_id, google_credentials&.first
AuthServer.set :spring_public_url, spring_public_url
AuthServer.set :login_handoff_secret, ENV.fetch("AUTH_HANDOFF_SECRET")
AuthServer.set :login_handoff_url, URI.join(spring_internal_url, "/internal/auth/login-handoffs")
AuthServer.set :reauth_callback_url, URI.join(spring_internal_url, "/internal/auth/reauth/callback")
AuthServer.set :host_authorization, { permitted_hosts: [auth_base_url.hostname] }

OmniAuth.configure do |config|
  config.full_host = auth_base_url.origin
  config.allowed_hosts = [auth_base_url.host]
  config.logger = Logger.new(File::NULL)
  config.on_failure do |request_env|
    AuthServer.clear_reauth_session(request_env["rack.session"]) if request_env["PATH_INFO"].end_with?("/callback")
    error_type = request_env["omniauth.error.type"]
    reason = error_type == :access_denied ? "cancelled" : "sign_in_failed"
    [400, { "content-type" => "application/json", "cache-control" => "no-store",
            "referrer-policy" => "no-referrer" }, [JSON.generate(error: reason)]]
  end
end

use Rack::Session::Cookie,
    key: "hypertube_provider_test",
    secret: ENV.fetch("AUTH_SESSION_SECRET"),
    same_site: :lax,
    httponly: true,
    secure: auth_base_url.scheme == "https",
    expire_after: 600

use ReauthIntent

use OmniAuth::Builder do
  if forty_two_credentials
    provider OmniAuth::Strategies::Marvin, *forty_two_credentials, origin_param: false
  end

  if google_credentials
    provider GoogleReauth, *google_credentials,
             scope: "openid email profile",
             access_type: "online",
             overridable_authorize_options: [],
             origin_param: false
  end
end

run AuthServer
