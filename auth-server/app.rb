require "sinatra/base"

class AuthServer < Sinatra::Base
  set :show_exceptions, false
  set :dump_errors, false
  set :absolute_redirects, false

  before do
    headers "Cache-Control" => "no-store",
            "Referrer-Policy" => "no-referrer",
            "Content-Security-Policy" => "default-src 'none'; style-src 'self'; base-uri 'none'; frame-ancestors 'none'"
  end

  helpers do
    def h(value)
      Rack::Utils.escape_html(value.to_s)
    end
  end

  get "/" do
    @result = session.delete("identity")
    @failure = session.delete("failure")
    @csrf = Rack::Protection::AuthenticityToken.token(session)
    erb :index
  end

  get "/auth/:provider/callback" do
    auth = request.env["omniauth.auth"]
    halt 404 unless auth && settings.configured_providers.include?(params[:provider])

    if auth.uid.to_s.empty?
      session["failure"] = "failed"
    else
      session["identity"] = {
        "provider" => settings.providers.fetch(auth.provider),
        "uid" => auth.uid.to_s[0, 128],
        "name" => auth.info.name.to_s[0, 128],
        "email" => auth.info.email.to_s[0, 254]
      }
    end

    redirect "/", 303
  end

  error do
    "The auth test could not complete. Return to the sign-in page and retry."
  end
end
