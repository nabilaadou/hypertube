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

The `assets` service downloads the landing page media from the public R2 bucket before the client starts. The service verifies each file against its SHA-256 hash and stores it in Docker volumes. Later starts reuse verified files. If a download fails or a checksum differs, the client waits until the asset is available and a subsequent start succeeds.

PostgreSQL is available at `localhost:5432` from the host and `postgres:5432` from other services. The database, user, and password come from the root `.env` when the database volume is first initialized.

Compose reads `client/.env` if it exists and sets `BACKEND_URL` to `http://server:8080` for the connection between containers. Restart `make up` to apply changes to `client/.env`. If a host port is busy, set `HYPERTUBE_CLIENT_PORT`, `HYPERTUBE_SERVER_PORT`, or `HYPERTUBE_POSTGRES_PORT` before starting Compose. For example, `HYPERTUBE_CLIENT_PORT=3100 make up` serves the frontend at `http://localhost:3100`.

Press Ctrl+C to stop the services. Run `make down` to remove the containers and network while keeping PostgreSQL data and downloaded assets. Run `make clean` to also delete the database and asset volumes, orphaned project containers, and images built for Hypertube.

## Run without Docker

Install JDK 21 or newer, Maven 3.9 or newer, Node.js 24 or newer, and npm.

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

Open `http://localhost:3000` to view the landing page.

## Search the movie catalog

Get your API Read Access Token from your TMDB account. With Docker, set `TMDB_READ_ACCESS_TOKEN` in the root `.env`, then run `make up` to apply the change. When you run Maven directly, set `TMDB_READ_ACCESS_TOKEN` in the server process environment before starting the API. [TMDB authentication guide](https://developer.themoviedb.org/docs/authentication-application)

Both endpoints are public. Search by title, then pass a result's `tmdbId` to the details endpoint.

```sh
curl -fsS 'http://localhost:8080/api/movies/search?q=alien&page=1'
curl -fsS 'http://localhost:8080/api/movies/348'
```

Search supports pages 1 through 500 and starts on page 1 when you omit `page`. An empty search result returns a page with an empty `results` list. An unknown movie returns HTTP 404. Without a TMDB token, the catalog returns HTTP 503 and `/api/health` remains available.

## Prepare the landing page media

Docker downloads the files from R2 into persistent volumes. To run without Docker, run `npm run prepare:media` from `client/`. It copies the tracked theater GLB from `models/` to `client/public/models/`. It also copies `reel.mp4`, `reel-poster.jpg`, and `theater.png` from the local `design-assets/` folder to `client/public/media/` when available.

The public copies are ignored by Git. A fresh clone includes the theater model. Supply the movie edit and preview images locally in `design-assets/`, then rerun the command. Missing source files produce a message and leave any existing public copy available. The video preview displays an unavailable message when the edit cannot load.

The media script prefers `design-assets/reel-with-sound.mp4` when present. To prepare it from the supplied two-track reel, run this command from the repository root with FFmpeg installed.

```sh
ffmpeg -i design-assets/reel.mp4 -map 0:v:0 -map 0:a:1 -c copy -movflags +faststart design-assets/reel-with-sound.mp4
(cd client && npm run prepare:media)
```

The command copies the original video and its audible second audio track. For a reel that already has a single audible track, supply it as `design-assets/reel.mp4`.

The served model URL is `/models/private-home-cinema-textured.glb`. The edit and image URLs begin with `/media/`. The media paths and poster lineup are in `client/app/data/movies.ts`.

The public R2 bucket must contain these objects. The setup checks their hashes against the manifest in `client/scripts/fetch-assets.mjs`.

| R2 object | Local source |
| --- | --- |
| `media/reel.mp4` | `design-assets/reel-with-sound.mp4` |
| `media/reel-poster.jpg` | `design-assets/reel-poster.jpg` |
| `media/theater.png` | `design-assets/theater.png` |
| `models/private-home-cinema-textured.glb` | `models/private-home-cinema-textured.glb` |

To change the bucket, set `ASSET_BASE_URL` in the root `.env`. To update an asset, upload the replacement and update its SHA-256 hash in the manifest in the same change.

## Verify changes

From the repository root, run the API smoke test and check the frontend.

```sh
(cd server && mvn verify)
(cd client && npm ci && npm run lint && npm run build)
```

With the API running, `curl -fsS http://localhost:8080/api/health` should return `{"status":"UP"}`.
