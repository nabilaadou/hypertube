.PHONY: up down clean migration

up:
	docker compose up --build --watch

down:
	docker compose down

clean:
	docker compose down --volumes --remove-orphans --rmi local

migration:
	bash scripts/generate-migration.sh "$(NAME)"
