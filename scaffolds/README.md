# Scaffolds

`make start-<language>` copies one of these into `service/`. They exist so you do
not spend the first ten minutes on project setup.

Each one serves `POST /v1/devices/{serial}/status` and `GET /health` on port
3000, parses the request body and the change id, and has `get` / `put` / `post`
helpers for calling the other services. The status handler logs what it received
and returns 200. Everything else is yours.

Every scaffold uses only its language's standard library, so none of them need a
package install to run. Add whatever framework you prefer.

| Language | Command | Runs as |
|---|---|---|
| TypeScript / Node | `make start-typescript` | `node server.mjs` |
| Go | `make start-go` | `go run .` |
| Python | `make start-python` | `python3 server.py` |
| C# | `make start-csharp` | `dotnet run` |
| Java | `make start-java` | `java Server.java` |

`make run` picks the right one from whatever is in `service/`.
