SCAFFOLDS := go typescript python csharp java

.PHONY: pull up down shell logs verify reset state run $(addprefix start-,$(SCAFFOLDS))

pull:
	docker compose pull

up:
	docker compose up -d
	@echo "fleet-api        http://localhost:4001"
	@echo "partner-supply   http://localhost:4002"
	@echo "event-bus        http://localhost:4003"
	@echo ""
	@echo "run 'make start-<language>' to drop a scaffold into service/"

$(addprefix start-,$(SCAFFOLDS)): start-%:
	@test -z "$$(ls -A service 2>/dev/null)" || { echo "service/ already has files in it, refusing to overwrite"; exit 1; }
	@mkdir -p service
	@cp -R scaffolds/$*/. service/
	@echo "copied scaffolds/$* into service/"
	@echo "start it with 'make run'"

run:
	@docker compose exec dev bash -lc 'cd /work/service 2>/dev/null || { echo "service/ is empty, run make start-<language> first"; exit 1; }; \
	  if [ -f go.mod ]; then exec go run .; \
	  elif [ -f server.mjs ]; then exec node server.mjs; \
	  elif [ -f server.py ]; then exec python3 server.py; \
	  elif [ -f Server.java ]; then exec java Server.java; \
	  elif [ -f candidate.csproj ]; then exec dotnet run; \
	  else echo "nothing recognisable in service/"; exit 1; fi'

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
