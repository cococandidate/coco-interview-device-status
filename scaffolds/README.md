# Scaffolds

`make start-<language>` copies one of these into `service/`. They exist so you do
not spend the first ten minutes on project setup.

Each one serves `POST /v1/devices/{serial}/status` and `GET /health` on port
3000, parses the request body and the change id, and has `get` / `put` / `post`
helpers for calling the other services. The status handler logs what it received
and returns 200. Everything else is yours.

Every scaffold uses only its language's standard library, so none of them need a
package install to run. Add whatever framework you prefer.

The TypeScript one is real TypeScript. Node 24 runs it directly, `@types/node`
is already vendored so your editor works offline, and `tsc --noEmit` is on the
path if you want a typecheck.

| Language | Command | Runs as |
|---|---|---|
| TypeScript / Node | `make start-typescript` | `node server.ts` |
| Go | `make start-go` | `go run .` |
| Python | `make start-python` | `python3 server.py` |
| C# | `make start-csharp` | `dotnet run` |
| Java | `make start-java` | `java Server.java` |

`make run` picks the right one from whatever is in `service/`.

## Tests

`make shell` gets you a terminal in the container. Every language has a runner
already installed, so none of these need a download.

| Language | Write | Run |
|---|---|---|
| TypeScript / Node | `*.test.ts` using `node:test` | `node --test` |
| Go | `*_test.go` | `go test ./...` |
| Python | `test_*.py` | `pytest` |
| Java | a JUnit 5 class | `javac -cp $JUNIT_JAR *.java && java -jar $JUNIT_JAR execute --class-path . --select-class ServerTest` |
| C# | `dotnet new xunit -o tests` | `cd tests && dotnet test` |

The container also has `curl`, `jq`, `git`, `vim`, `nano` and `less`.
