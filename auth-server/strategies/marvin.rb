require "omniauth/strategies/oauth2"

module OmniAuth
  module Strategies
    class Marvin < OAuth2
      option :name, "marvin"
      option :scope, "public"
      option :pkce, true
      option :client_options, {
        site: "https://api.intra.42.fr",
        authorize_url: "/v2/oauth/authorize",
        token_url: "/oauth/token",
        auth_scheme: :request_body
      }

      uid { profile.fetch("id").to_s }

      info do
        {
          name: profile["usual_full_name"] || profile["displayname"],
          email: profile["email"],
          nickname: profile["login"]
        }
      end

      def callback_url
        full_host + callback_path
      end

      private

      def profile
        @profile ||= access_token.get("/v2/me").parsed
      end
    end
  end
end
