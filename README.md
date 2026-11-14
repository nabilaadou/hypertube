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
mvn spring-boot:run
```

Start Next.js in a second terminal.

```sh
cd client
npm ci
npm run prepare:media
npm run dev
```

Open `http://localhost:3000`. The home page reports whether it can reach the API at `GET /api/health`. If `BACKEND_URL` is missing or invalid, the page tells you how to configure it. Restart Next.js after changing `client/.env`.

## Prepare the landing page media

Run `npm run prepare:media` from `client/` before starting local development or building the Docker client image. It copies the tracked theater GLB from `models/` to `client/public/models/`. It also copies `reel.mp4`, `reel-poster.jpg`, and `theater.png` from the local `design-assets/` folder to `client/public/media/` when available.

The public copies are ignored by Git. A fresh clone includes the theater model. Supply the movie edit and preview images locally in `design-assets/`, then rerun the command. Missing source files produce a message and leave any existing public copy available. The video preview displays an unavailable message when the edit cannot load.

The served model URL is `/models/private-home-cinema-textured.glb`. The edit and image URLs begin with `/media/`. `client/app/data/movies.ts` contains those paths and the draft movie entries. The current frontend phase provides shared styles, a basic media preview, and a Lenis instance synchronized with GSAP. The cinematic section transitions follow in the next tasks.

## Verify changes

From the repository root, run the API smoke test and check the frontend.

```sh
(cd server && mvn verify)
(cd client && npm ci && npm run lint && npm run build)
```

With both apps running, `curl -fsS http://localhost:8080/api/health` should return `{"status":"UP"}` and the home page should show “API is online.” To check the configuration diagnostic, clear `BACKEND_URL` in `client/.env`, restart Next.js, and reload the page.
