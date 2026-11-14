# Hypertube

The Spring Boot API lives in `server/`. The Next.js app lives in `client/`.

## Run with Docker

Install Docker with Compose 2.24 or newer and Make. From the repository root, copy the PostgreSQL configuration template.

```sh
cp .env.example .env
```

Set `POSTGRES_PASSWORD` to a local password in `.env`, then start the development stack.

```sh
make up
```

Open `http://localhost:3000`. The API is available at `http://localhost:8080/api/health`. Compose reloads Next.js when you edit the frontend and restarts Spring Boot when you edit backend source files. Changes to dependency manifests rebuild the corresponding image.

PostgreSQL is available at `localhost:5432` from the host and `postgres:5432` from other services. The database, user, and password come from the root `.env` when the database volume is first initialized.

Compose reads `client/.env` if it exists and sets `BACKEND_URL` to `http://server:8080` for the connection between containers. Restart `make up` to apply changes to `client/.env`. If a host port is busy, set `HYPERTUBE_CLIENT_PORT`, `HYPERTUBE_SERVER_PORT`, or `HYPERTUBE_POSTGRES_PORT` before starting Compose. For example, `HYPERTUBE_CLIENT_PORT=3100 make up` serves the frontend at `http://localhost:3100`.

Press Ctrl+C to stop the services. Run `make down` to remove the containers and network while keeping PostgreSQL data. Run `make clean` to also delete the database volume, orphaned project containers, and images built for Hypertube.

## Run without Docker

Install JDK 21 or newer, Maven 3.9 or newer, Node.js 24 or newer, and npm.

From the repository root, copy the local configuration template.

```sh
cp client/.env.example client/.env
```

Set `BACKEND_URL=http://localhost:8080` in `client/.env`. Next.js reads this value on the server. Keep local credentials in ignored `.env` files and commit only variable names and placeholders in `.env.example`.

Start the API in one terminal.

```sh
cd server
POSTGRES_PASSWORD=your-local-postgres-password mvn spring-boot:run
```

Use a running PostgreSQL instance and set `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, or `POSTGRES_USER` if your connection differs from `localhost:5432`, database `hypertube`, user `hypertube`. Spring Boot runs the database migrations on startup.

Start Next.js in a second terminal.

```sh
cd client
npm ci
npm run dev
```

Open `http://localhost:3000`. The home page reports whether it can reach the API at `GET /api/health`. If `BACKEND_URL` is missing or invalid, the page tells you how to configure it. Restart Next.js after changing `client/.env`.

## Test 42 and Google sign-in

Copy the auth server's local environment template and generate a session secret.

```sh
cp auth-server/.env.example auth-server/.env
openssl rand -hex 64
```

Paste the generated value into `AUTH_SESSION_SECRET` in `auth-server/.env`. Add the client ID and secret for each provider you want to test. You can configure either provider on its own. Run `openssl rand -hex 32` and set `AUTH_HANDOFF_SECRET` in the root `.env` to the output. Compose passes that shared secret to Sinatra and Spring Boot.

Register these exact redirect URIs with your 42 application and Google **Web application** OAuth client. If Google OAuth is in testing mode, add the Google account you plan to use as a test user.

| Provider | Redirect URI |
| --- | --- |
| 42 | `http://localhost:4567/auth/marvin/callback` |
| Google | `http://localhost:4567/auth/google_oauth2/callback` |

```sh
docker compose up --build auth-server
```

Open `http://localhost:4567` and select a provider. After a successful callback, Spring Boot saves the user in PostgreSQL, starts a browser session, and redirects to `http://localhost:8080/api/auth/me`. The JSON response contains the database ID, provider, provider user ID, and available name and email. Repeat sign-ins with the same provider account reuse the database ID. `/api/auth/me` returns `401` without a Hypertube session.

Run the Compose command again after changing an `.env` file. Open the sign-in page using `localhost` so the browser can send the same cookies to the OAuth callback and Spring Boot. The auth service starts Spring Boot and PostgreSQL as dependencies.

## Verify changes

From the repository root, run the API smoke test and check the frontend.

```sh
(cd server && mvn verify)
(cd client && npm ci && npm run lint && npm run build)
```

With both apps running, `curl -fsS http://localhost:8080/api/health` should return `{"status":"UP"}` and the home page should show “API is online.” To check the configuration diagnostic, clear `BACKEND_URL` in `client/.env`, restart Next.js, and reload the page.
