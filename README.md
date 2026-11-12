# Hypertube

The Spring Boot API lives in `server/`. The Next.js app lives in `client/`.

## Run with Docker

Install Docker with Compose 2.24 or newer, then run from the repository root.

```sh
docker compose up --build --watch
```

Open `http://localhost:3000`. The API is available at `http://localhost:8080/api/health`. Compose reloads Next.js when you edit the frontend and restarts Spring Boot when you edit backend source files. Changes to dependency manifests rebuild the corresponding image.

Compose reads `client/.env` if it exists and sets `BACKEND_URL` to `http://server:8080` for the connection between containers. After editing `client/.env`, restart the Compose command to apply the changes. If a host port is busy, set `HYPERTUBE_CLIENT_PORT` or `HYPERTUBE_SERVER_PORT` before starting Compose. For example, `HYPERTUBE_CLIENT_PORT=3100 docker compose up --build --watch` serves the frontend at `http://localhost:3100`.

Press Ctrl+C to stop the services, then run `docker compose down` to remove the containers and network.

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
mvn spring-boot:run
```

Start Next.js in a second terminal.

```sh
cd client
npm ci
npm run dev
```

Open `http://localhost:3000`. The home page reports whether it can reach the API at `GET /api/health`. If `BACKEND_URL` is missing or invalid, the page tells you how to configure it. Restart Next.js after changing `client/.env`.

## Verify changes

From the repository root, run the API smoke test and check the frontend.

```sh
(cd server && mvn verify)
(cd client && npm ci && npm run lint && npm run build)
```

With both apps running, `curl -fsS http://localhost:8080/api/health` should return `{"status":"UP"}` and the home page should show “API is online.” To check the configuration diagnostic, clear `BACKEND_URL` in `client/.env`, restart Next.js, and reload the page.
