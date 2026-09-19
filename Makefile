.PHONY: pull up down shell logs verify reset state

pull:
	docker compose pull

up:
	docker compose up -d
	@echo "fleet-api        http://localhost:4001"
	@echo "partner-supply   http://localhost:4002"
	@echo "event-bus        http://localhost:4003"
	@echo ""
	@echo "run 'make shell' for a terminal with every toolchain installed"

shell:
	docker compose exec dev bash

down:
	docker compose down

logs:
	docker compose logs -f stack

verify:
	@docker compose exec stack /stack verify

reset:
	@curl -s -X POST http://localhost:4002/v1/_debug/reset > /dev/null
	@curl -s -X POST http://localhost:4003/v1/_debug/reset > /dev/null
	@echo "partner and bus state cleared"

state:
	@echo "--- partner calls ---"
	@curl -s http://localhost:4002/v1/_debug/calls
	@echo "--- bus deliveries ---"
	@curl -s http://localhost:4003/v1/deliveries
	@echo "--- dead letter ---"
	@curl -s http://localhost:4003/v1/dead-letter
