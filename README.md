# Hypertube

The Spring Boot API lives in `server/`. The Next.js app lives in `client/`.

## Run with Docker

Install Docker with Compose 2.24 or newer and Make. From the repository root, copy the Docker environment template.

```sh
cp .env.example .env
```

Set `POSTGRES_PASSWORD` to a local password in `.env`, then start the development stack.

```sh
make up
```

Open `http://localhost:3000`. The API is available at `http://localhost:8080/api/health`. Compose reloads Next.js when you edit the frontend and restarts Spring Boot when you edit backend source files. Changes to dependency manifests rebuild the corresponding image.

PostgreSQL is available at `localhost:5432` from the host and `postgres:5432` from other services. The database, user, and password come from the root `.env` when the database volume is first initialized.

Compose reads service settings from the root `.env`. If you already have one, add any missing settings from `.env.example`. To change a host port, edit `HYPERTUBE_CLIENT_PORT`, `HYPERTUBE_SERVER_PORT`, or `HYPERTUBE_POSTGRES_PORT` in `.env` and restart `make up`. For example, `HYPERTUBE_CLIENT_PORT=3100` serves the frontend at `http://localhost:3100`.

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

Use a running PostgreSQL instance and set `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, or `POSTGRES_USER` if your connection differs from `localhost:5432`, database `hypertube`, user `hypertube`.

Spring Boot runs [Flyway migrations](server/src/main/resources/db/migration) on startup. Flyway tracks which scripts have run in `flyway_schema_history`. For a new schema change, add a versioned SQL script such as `V2__add_user_preferences.sql`.

Start Next.js in a second terminal.

```sh
cd client
npm ci
npm run dev
```

Open `http://localhost:3000`. The home page reports whether it can reach the API at `GET /api/health`. If `BACKEND_URL` is missing or invalid, the page tells you how to configure it. Restart Next.js after changing `client/.env`.

## Test 42 and Google sign-in

Use the root `.env` from the Docker setup above. If you have an existing `auth-server/.env`, move its values for `AUTH_BASE_URL`, `AUTH_SESSION_SECRET`, and the provider credentials into the root `.env`.

Generate the two secrets.

```sh
openssl rand -hex 64  # AUTH_SESSION_SECRET
openssl rand -hex 32  # AUTH_HANDOFF_SECRET
```

Set both secrets in `.env` along with the client ID and secret for each provider you want to test. You can configure either provider on its own.

Register the redirect URIs with your 42 application and Google **Web application** OAuth client. These are the URIs for the ports in `.env.example`. If Google OAuth is in testing mode, add the Google account you plan to use as a test user.

| Provider | Redirect URI |
| --- | --- |
| 42 | `http://localhost:4567/auth/marvin/callback` |
| Google | `http://localhost:4567/auth/google_oauth2/callback` |

```sh
docker compose up --build auth-server
```

Open `http://localhost:4567` and select a provider. After a successful callback, Spring Boot saves the user in PostgreSQL, starts a browser session, and redirects to `http://localhost:8080/api/auth/me`. The JSON response contains the database ID, provider, provider user ID, and available name and email. Repeat sign-ins with the same provider account reuse the database ID. `/api/auth/me` returns `401` without a Hypertube session.

Run the Compose command again after changing `.env`. Use the same browser-facing hostname and scheme in `AUTH_BASE_URL` and `SPRING_PUBLIC_URL` so the browser can carry the login cookie to Spring Boot. The auth service starts Spring Boot and PostgreSQL as dependencies.

## Verify changes

From the repository root, run the API smoke test and check the frontend.

```sh
(cd server && mvn verify)
(cd client && npm ci && npm run lint && npm run build)
```

With both apps running, `curl -fsS http://localhost:8080/api/health` should return `{"status":"UP"}` and the home page should show “API is online.” To check the configuration diagnostic, clear `BACKEND_URL` in the `.env` used to start the client, restart it, and reload the page.
