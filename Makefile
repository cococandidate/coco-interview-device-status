SCAFFOLDS := go typescript python csharp java

.PHONY: pull up down shell logs verify reset state run test traffic-on traffic-off $(addprefix start-,$(SCAFFOLDS))

pull:
	docker compose pull

up:
	docker compose up -d
	@echo "fleet-api        http://localhost:4001"
	@echo "partner-supply   http://localhost:4002"
	@echo "event-bus        http://localhost:4003"
	@echo ""
	@echo "run 'make start-<language>' to pick a language"

$(addprefix start-,$(SCAFFOLDS)): start-%:
	@echo $* > .service
	@echo "your code is service/$*, edit it in place"
	@echo "start it with 'make run'"

run:
	@docker compose exec dev bash -lc 'dir=$$(cat /work/.service 2>/dev/null); \
	  [ -n "$$dir" ] && [ -d "/work/service/$$dir" ] || { echo "pick a language first: make start-<language>"; exit 1; }; \
	  cd "/work/service/$$dir"; \
	  if [ -f go.mod ]; then exec go run .; \
	  elif [ -f server.ts ]; then exec node server.ts; \
	  elif [ -f server.py ]; then exec python3 server.py; \
	  elif [ -f Server.java ]; then exec java -cp "$$GSON_JAR" Server.java; \
	  elif [ -f candidate.csproj ]; then exec dotnet run; \
	  else echo "nothing recognisable in service/$$dir"; exit 1; fi'

test:
	@docker compose exec dev bash -lc 'dir=$$(cat /work/.service 2>/dev/null); \
	  [ -n "$$dir" ] && [ -d "/work/service/$$dir" ] || { echo "pick a language first: make start-<language>"; exit 1; }; \
	  cd "/work/service/$$dir"; \
	  if [ -f go.mod ]; then exec go test ./...; \
	  elif [ -f server.ts ]; then exec node --test; \
	  elif [ -f server.py ]; then exec pytest -q; \
	  elif [ -f Server.java ]; then javac -cp "$$JUNIT_JAR:$$GSON_JAR" *.java && exec java -jar "$$JUNIT_JAR" execute --class-path ".:$$GSON_JAR" --scan-class-path --details=summary; \
	  elif [ -f candidate.csproj ]; then \
	    if [ -d tests ]; then exec dotnet test tests; else echo "no test project yet: make shell, then dotnet new xunit -o tests"; exit 1; fi; \
	  else echo "nothing recognisable in service/$$dir"; exit 1; fi'

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

traffic-on:
	@curl -s -X POST -H 'Content-Type: application/json' -d '{"enabled":true}' http://localhost:4001/v1/_debug/traffic > /dev/null
	@echo "fleet traffic on"

traffic-off:
	@curl -s -X POST -H 'Content-Type: application/json' -d '{"enabled":false}' http://localhost:4001/v1/_debug/traffic > /dev/null
	@echo "fleet traffic off"

state:
	@echo "--- fleet emitted ---"
	@curl -s http://localhost:4001/v1/_debug/emitted
	@echo "--- partner calls ---"
	@curl -s http://localhost:4002/v1/_debug/calls
	@echo "--- bus deliveries ---"
	@curl -s http://localhost:4003/v1/deliveries
	@echo "--- dead letter ---"
	@curl -s http://localhost:4003/v1/dead-letter
