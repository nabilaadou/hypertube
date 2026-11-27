require "omniauth-google-oauth2"
require "securerandom"
require "json"

class GoogleReauth < OmniAuth::Strategies::GoogleOauth2
  def authorize_params
    super.tap do |parameters|
      next unless session["hypertube.reauth_challenge"]

      nonce = SecureRandom.hex(32)
      session["hypertube.reauth_nonce"] = nonce
      parameters[:nonce] = nonce
      parameters[:claims] = JSON.generate(id_token: { auth_time: { essential: true } })
    end
  end
end
