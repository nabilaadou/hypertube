#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd "$(dirname "$0")/.." && pwd)
migration_name=${1:-update_entities}
if [[ ! "$migration_name" =~ ^[a-z][a-z0-9_]*$ ]]; then
    echo "Use a migration name such as add_user_preferences." >&2
    exit 1
fi

work_dir=$(mktemp -d)
network_name="hypertube-migration-$$"
database_name="$network_name-db"
atlas_image=arigaio/atlas:1.3.0
migrations_dir="$repo_dir/server/src/main/resources/db/migration"

cleanup() {
    docker rm -f "$database_name" >/dev/null 2>&1 || true
    docker network rm "$network_name" >/dev/null 2>&1 || true
    rm -rf "$work_dir"
}
trap cleanup EXIT

cd "$repo_dir"
mkdir -p "$migrations_dir"
docker compose build server >&2
docker compose run --rm --no-deps -T \
    --volume "$repo_dir/server/src:/app/src:ro" \
    --volume "$repo_dir/server/pom.xml:/app/pom.xml:ro" \
    server sh -ec '
        mvn -B -ntp -DskipTests clean package >&2
        java -jar target/hypertube-server-0.1.0-SNAPSHOT.jar --spring.profiles.active=schema-export >&2
        cat /tmp/hypertube-schema.sql
    ' > "$work_dir/schema.sql"

docker network create "$network_name" >/dev/null
docker run -d --rm --name "$database_name" --network "$network_name" \
    --env POSTGRES_PASSWORD=migration-only \
    postgres:17-alpine >/dev/null

for attempt in {1..40}; do
    if docker exec "$database_name" pg_isready -U postgres >/dev/null 2>&1; then
        break
    fi
    sleep 1
done
docker exec "$database_name" pg_isready -U postgres >/dev/null

if [[ ! -f "$migrations_dir/atlas.sum" ]]; then
    docker run --rm --user "$(id -u):$(id -g)" \
        --volume "$migrations_dir:/migrations" \
        "$atlas_image" migrate hash --dir 'file:///migrations?format=flyway'
fi

docker run --rm --network "$network_name" --user "$(id -u):$(id -g)" \
    --volume "$migrations_dir:/migrations" \
    --volume "$work_dir/schema.sql:/schema.sql:ro" \
    "$atlas_image" migrate diff "$migration_name" \
    --dir 'file:///migrations?format=flyway' \
    --to file:///schema.sql \
    --dev-url "postgres://postgres:migration-only@$database_name:5432/postgres?search_path=public&sslmode=disable"
