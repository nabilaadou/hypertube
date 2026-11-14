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
npm run dev
```

Open `http://localhost:3000`. The public landing page also runs without the API.

## Preview the cinematic landing page

Supply the local preview media from the repository root before starting Next.js or building the client image.

```sh
mkdir -p client/public/cinema
cp design-assets/reel.mp4 design-assets/theater.png client/public/cinema/
cp design-assets/reel-poster.jpg client/public/cinema/
cp models/private-home-cinema-textured.glb client/public/cinema/
```

If your still is named `reel-poster.jpg.jpeg`, copy it as `client/public/cinema/reel-poster.jpg`. The retextured runtime model is already in the repository. The reel and render are provided separately. Keep the served copies and any editable source media in ignored directories.

For external hosting, set the optional `NEXT_PUBLIC_CINEMA_*` URLs in `client/.env` before starting or building Next.js. The reel host must support byte-range requests and allow cross-origin access from the site for the WebGL video texture. The model host must also allow cross-origin access. Keep the fallback room image at its original 1440 × 900 framing and use the retextured model's `Projection screen` mesh.

The first downward scroll starts a timed 850 ms reveal, followed by one viewport of scroll travel before the diagonal wipe. Keep scrolling from the screening room to see the poster move into Best Picture, then continue past the last film to the Hypertube statement. Menu links and year buttons give direct access. Opening reel in the menu replays the fullscreen view. Reduced-motion users get the same content in a vertical layout with manual reel playback.

The preview follows the reel, cinema, and poster choreography from `design.html`. The live JJettas site stayed on its loading screen during review, so the framing and transition timing use the prototype as the reference.

The preview uses the draft film selection, the Academy Awards ceremony years 2024–2020, and the prototype statement. Film descriptions, poster paths, and trailer IDs are edited in `client/app/cinema/films.ts`. The prototype posters are served by TMDB and the trailers by YouTube. The sign-in and registration destinations are public entry pages pending the account flow.

## Verify changes

From the repository root, run the API smoke test and check the frontend.

```sh
(cd server && mvn verify)
(cd client && npm ci && npm run lint && npm run build)
```

With the API running, `curl -fsS http://localhost:8080/api/health` should return `{"status":"UP"}`. For a browser smoke check, activate the cinema with a large first scroll, verify the room stays visible, then scroll to Best Picture and the closing statement. Open a film with the keyboard, close it with Escape, and check that focus returns to its poster.
