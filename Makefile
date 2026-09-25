SCAFFOLDS := go typescript python csharp java

.PHONY: pull up down shell logs verify reset state run test traffic-on traffic-off $(SCAFFOLDS)

# 'make run go' passes the language as a second goal, which needs a rule of its own
LANGUAGE := $(word 2,$(MAKECMDGOALS))

$(SCAFFOLDS):
	@:

pull:
	docker compose pull

up:
	docker compose up -d
	@echo "fleet-api        http://localhost:4001"
	@echo "deliverme-supply http://localhost:4002"
	@echo "rabbitmq         localhost:5672, management UI http://localhost:15672 (guest/guest)"
	@echo ""
	@echo "run 'make run <language>' to start your service"

run:
	@test -n "$(LANGUAGE)" || { echo "usage: make run <language>   [$(SCAFFOLDS)]"; exit 1; }
	@docker compose exec dev bash -lc 'cd "/work/service/$(LANGUAGE)" 2>/dev/null || { echo "no service/$(LANGUAGE) directory"; exit 1; }; \
	  if [ -f go.mod ]; then exec go run .; \
	  elif [ -f server.ts ]; then npm install --silent --no-audit --no-fund; exec node server.ts; \
	  elif [ -f server.py ]; then exec python3 server.py; \
	  elif [ -f Server.java ]; then javac -cp "$$GSON_JAR:$$AMQP_JAR:$$SLF4J_JARS" *.java && exec java -cp ".:$$GSON_JAR:$$AMQP_JAR:$$SLF4J_JARS" Server; \
	  elif [ -f candidate.csproj ]; then exec dotnet run; \
	  else echo "nothing recognisable in service/$(LANGUAGE)"; exit 1; fi'

test:
	@test -n "$(LANGUAGE)" || { echo "usage: make test <language>   [$(SCAFFOLDS)]"; exit 1; }
	@docker compose exec dev bash -lc 'cd "/work/service/$(LANGUAGE)" 2>/dev/null || { echo "no service/$(LANGUAGE) directory"; exit 1; }; \
	  if [ -f go.mod ]; then exec go test ./...; \
	  elif [ -f server.ts ]; then npm install --silent --no-audit --no-fund; exec node --test; \
	  elif [ -f server.py ]; then exec pytest -q; \
	  elif [ -f Server.java ]; then javac -cp "$$JUNIT_JAR:$$GSON_JAR:$$AMQP_JAR:$$SLF4J_JARS" *.java && exec java -jar "$$JUNIT_JAR" execute --class-path ".:$$GSON_JAR:$$AMQP_JAR:$$SLF4J_JARS" --scan-class-path --details=summary; \
	  elif [ -f candidate.csproj ]; then \
	    if [ -d tests ]; then exec dotnet test tests; else echo "no test project yet. from make shell, in service/csharp:"; echo "  dotnet new xunit -o tests && dotnet add tests reference candidate.csproj"; exit 1; fi; \
	  else echo "nothing recognisable in service/$(LANGUAGE)"; exit 1; fi'

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
	@docker compose exec -T rabbitmq rabbitmqctl purge_queue device-status > /dev/null 2>&1 || true
	@docker compose exec -T rabbitmq rabbitmqctl purge_queue device-status.dlq > /dev/null 2>&1 || true
	@echo "DeliverMe state cleared and queues purged"

traffic-on:
	@curl -s -X POST -H 'Content-Type: application/json' -d '{"enabled":true}' http://localhost:4001/v1/_debug/traffic > /dev/null
	@echo "fleet traffic on"

traffic-off:
	@curl -s -X POST -H 'Content-Type: application/json' -d '{"enabled":false}' http://localhost:4001/v1/_debug/traffic > /dev/null
	@echo "fleet traffic off"

state:
	@echo "--- queue ---"
	@docker compose exec -T rabbitmq rabbitmqctl list_queues name messages consumers 2>/dev/null | grep -v "^Listing"
	@echo "--- fleet published ---"
	@curl -s http://localhost:4001/v1/_debug/emitted
	@echo "--- DeliverMe calls ---"
	@curl -s http://localhost:4002/v1/_debug/calls
