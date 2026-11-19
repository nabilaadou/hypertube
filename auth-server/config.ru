require "omniauth"
require "omniauth-github"
require "omniauth-google-oauth2"
require "rack/session/cookie"
require "uri"
require_relative "strategies/marvin"
require_relative "app"

def oauth_credentials(provider_prefix)
  client_id = ENV["#{provider_prefix}_CLIENT_ID"]
  client_secret = ENV["#{provider_prefix}_CLIENT_SECRET"]
  return if client_id.to_s.strip.empty? || client_secret.to_s.strip.empty?

  [client_id, client_secret]
end

auth_base_url = URI(ENV.fetch("AUTH_BASE_URL"))
auth_server_url = URI(ENV.fetch("AUTH_SERVER_URL", "http://localhost:4567"))
spring_internal_url = ENV.fetch("SPRING_INTERNAL_URL")
forty_two_credentials = oauth_credentials("FORTY_TWO")
google_credentials = oauth_credentials("GOOGLE")
github_credentials = oauth_credentials("GITHUB")

AuthServer.set :providers, {
  "marvin" => { id: "42", enabled: !forty_two_credentials.nil? },
  "google_oauth2" => { id: "google", enabled: !google_credentials.nil? },
  "github" => { id: "github", enabled: !github_credentials.nil? }
}
AuthServer.set :secure_cookies, auth_base_url.scheme == "https"
AuthServer.set :login_handoff_secret, ENV.fetch("AUTH_HANDOFF_SECRET")
AuthServer.set :login_handoff_url, URI.join(spring_internal_url, "/internal/auth/login-handoffs")
AuthServer.set :host_authorization, { permitted_hosts: [auth_base_url.hostname, auth_server_url.hostname] }

OmniAuth.configure do |config|
  config.full_host = auth_base_url.origin
  config.allowed_hosts = [auth_base_url.host]
  config.logger = Logger.new(File::NULL)
  config.on_failure do |request_env|
    error_type = request_env["omniauth.error.type"]
    reason = error_type == :access_denied ? "cancelled" : "failed"
    [303, { "location" => "/login?error=#{reason}", "cache-control" => "no-store", "referrer-policy" => "no-referrer" }, []]
  end
end

use Rack::Session::Cookie,
    key: "hypertube_oauth_session",
    secret: ENV.fetch("AUTH_SESSION_SECRET"),
    same_site: :lax,
    httponly: true,
    secure: auth_base_url.scheme == "https",
    expire_after: 600

use OmniAuth::Builder do
  if forty_two_credentials
    provider OmniAuth::Strategies::Marvin, *forty_two_credentials, origin_param: false
  end

  if google_credentials
    provider :google_oauth2, *google_credentials,
             scope: "openid email profile",
             access_type: "online",
             overridable_authorize_options: [],
             origin_param: false
  end

  if github_credentials
    provider :github, *github_credentials, scope: "user:email", origin_param: false, pkce: true
  end
end

run AuthServer
