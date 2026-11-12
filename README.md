# Hypertube

The Spring Boot API lives in `server/`. The Next.js app lives in `client/`.

## Prerequisites

- JDK 21 or newer and Maven 3.9 or newer
- Node.js 24 or newer and npm

## Run locally

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
