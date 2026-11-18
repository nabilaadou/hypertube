.PHONY: up down clean

up:
	docker compose up --build --watch

down:
	docker compose down

clean:
	docker compose down --volumes --remove-orphans --rmi local
