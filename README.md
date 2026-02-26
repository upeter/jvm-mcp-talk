# Resources for MCP (Model Context Protocol) on the JVM talk

Demo project for a JVM/Kotlin talk about **MCP (Model Context Protocol)** using **Spring AI**.

This repo contains:

- `spring-ai/`: Spring Boot app acting as the **MCP client** and the "main" AI application.
- `spring-mcp-server/`: Spring Boot app acting as the **MCP server** exposing tools/resources/prompts/completions.
- `chatclient-kmp/`: Kotlin Multiplatform (Compose Desktop) **UI client** that talks to `spring-ai` over HTTP.
- `docker-compose.yaml` + `docker-entrypoint-initdb.d/`: PostgreSQL + **pgvector** database used as a vector store.

---

## Quick start (the configuration this repo is set up for)

Checklist:
- Start the pgvector database
- Start `spring-mcp-server` with profiles **`default,streaming`** (required)
- Start `spring-ai`
- (Optional) Start `chatclient-kmp`

### 1) Database (pgvector)
The database is started via Docker Compose and listens on **`localhost:5430`** (mapped to container 5432).

```zsh
docker compose up -d
```

Initialization scripts live in `docker-entrypoint-initdb.d/`.

### 2) MCP server (`spring-mcp-server`) — required profiles
For the application to work *as is*, start the MCP server with the Spring profiles:

- `default` (common settings + OpenAI + pgvector)
- `streaming` (Streamable HTTP transport)

```zsh
cd spring-mcp-server
./mvnw spring-boot:run -Dspring-boot.run.profiles=default,streaming
```

By default, in the `streaming` profile the server listens on **port `8080`**.

### 3) Spring AI app (`spring-ai`)
This is the main application. It runs on **port `8082`**.

It expects an OpenAI API key via environment variable.

```zsh
export OPENAI_API_KEY=...  # required
cd spring-ai
./mvnw spring-boot:run
```

### 4) Desktop UI (`chatclient-kmp`) (optional)
Compose Desktop client that calls `spring-ai` (HTTP API).

```zsh
cd chatclient-kmp
./gradlew :run
```

---

## How the pieces fit together

### Runtime topology

- `chatclient-kmp` (desktop UI)
  - calls → `spring-ai` on **`http://localhost:8082`**
- `spring-ai` (Spring AI "app")
  - calls → `spring-mcp-server` as an MCP **client**
  - default transport in this repo: **Streamable HTTP**
- `spring-mcp-server` (MCP server)
  - uses → OpenAI (chat + embeddings)
  - uses → Postgres/pgvector vector store on **`jdbc:postgresql://localhost:5430/vector_store`**

### Ports

- Postgres/pgvector: `localhost:5430`
- MCP server: `localhost:8080` (when using `streaming` or `sse` profile)
- Spring AI app: `localhost:8082`

---

## Module overview

### `docker-compose.yaml` + `docker-entrypoint-initdb.d/`
- Runs `ankane/pgvector:latest`
- Creates database `vector_store` with user/password `user/password`
- Initialization scripts/SQL are mounted read-only from `./docker-entrypoint-initdb.d`

> Note: the compose file contains a commented-out persistent volume line. If you want persistence across restarts, enable the `pgvector-data` volume mapping.

### `spring-mcp-server/` (MCP server)
Spring Boot MCP server, using `spring-ai-starter-mcp-server-webmvc`.

Key features (enabled in `application-default.properties`):
- tool capability: `spring.ai.mcp.server.capabilities.tool=true`
- resource capability: `spring.ai.mcp.server.capabilities.resource=true`
- prompt capability: `spring.ai.mcp.server.capabilities.prompt=true`
- completion capability: `spring.ai.mcp.server.capabilities.completion=true`

Also configured with:
- OpenAI chat model: `gpt-4o`
- OpenAI embeddings model: `text-embedding-3-small`
- pgvector table: `public.talks`

### `spring-ai/` (MCP client + application)
Spring Boot application using:
- `spring-ai-starter-mcp-client`
- OpenAI chat/embeddings
- pgvector vector store

MCP client configuration in `spring-ai/src/main/resources/application.properties`:

- Streamable HTTP connection (enabled by default in this repo):
  - `spring.ai.mcp.client.streamable-http.connections.conference-advisor-server.url=http://localhost:8080`
  - `spring.ai.mcp.client.streamable=true`
- Local STDIO servers config (available, but not the default):
  - `spring.ai.mcp.client.stdio.servers-configuration=classpath:/mcp-config.json`
- SSE connection (available, commented out by default):
  - `#spring.ai.mcp.client.sse.connections.conference-advisor-server.url=http://localhost:8080`

### `chatclient-kmp/` (Kotlin Multiplatform UI)
Compose Desktop app (`mainClass = "org.course.llm.chatapp.MainKt"`) using Ktor client.

It’s meant as a simple front-end for `spring-ai`.

---

## `spring-mcp-server` configuration options (important)

The MCP server supports multiple transports via Spring profiles. This repo includes:

- `default` (common settings): `spring-mcp-server/src/main/resources/application-default.properties`
- `streaming`: `spring-mcp-server/src/main/resources/application-streaming.properties`
- `sse`: `spring-mcp-server/src/main/resources/application-sse.properties`
- `stdio`: `spring-mcp-server/src/main/resources/application-stdio.properties`

### Option A — Local (STDIO) via `mcp-config.json`
This is the classic "local MCP" setup where the client launches MCP servers as subprocesses and communicates over STDIO.

In this repo:
- The **client-side** STDIO server registry is `spring-ai/src/main/resources/mcp-config.json`.
- `spring-ai` points to it via:
  - `spring.ai.mcp.client.stdio.servers-configuration=classpath:/mcp-config.json`

To run the MCP server itself over STDIO, start it with the `stdio` profile:

```zsh
cd spring-mcp-server
./mvnw spring-boot:run -Dspring-boot.run.profiles=default,stdio
```

Notes for STDIO mode:
- The `stdio` profile disables the Spring banner and console logging and sets `spring.main.web-application-type=none`.
- STDIO is typically used when the MCP server is launched by a client (e.g., an inspector or a desktop client).

### Option B — Streaming / Streamable HTTP (default used by `spring-ai`)
This is the configuration that’s wired in `spring-ai` right now.

Server side (`spring-mcp-server`):
- profile: `streaming`
- key properties:
  - `spring.ai.mcp.server.stdio=false`
  - `spring.ai.mcp.server.protocol=STREAMABLE`
  - `spring.ai.mcp.server.port=8080`

Run it (this is the **required** setup for the repo as-is):

```zsh
cd spring-mcp-server
./mvnw spring-boot:run -Dspring-boot.run.profiles=default,streaming
```

Client side (`spring-ai`):
- `spring.ai.mcp.client.streamable=true`
- `spring.ai.mcp.client.streamable-http.connections.conference-advisor-server.url=http://localhost:8080`

### Option C — SSE (Server-Sent Events)
Server side (`spring-mcp-server`):
- profile: `sse`
- key properties:
  - `spring.ai.mcp.server.protocol=SSE`
  - `spring.ai.mcp.server.keep-alive-interval=20s`
  - `spring.ai.mcp.server.port=8080`

Run it:

```zsh
cd spring-mcp-server
./mvnw spring-boot:run -Dspring-boot.run.profiles=default,sse
```

Client side (`spring-ai`):
- enable and configure the SSE connection in `spring-ai/src/main/resources/application.properties`:
  - uncomment `spring.ai.mcp.client.sse.connections.conference-advisor-server.url=http://localhost:8080`
  - and disable streamable if you want SSE-only (keep configuration consistent)

---

## MCP Inspector
You can use the MCP inspector to connect to the server.

```zsh
npx @modelcontextprotocol/inspector
```

Tip:
- Choose the connection type you need (e.g., via proxy) depending on whether you’re testing STDIO or an HTTP-based transport.

---

## Configuration & secrets

### OpenAI
Both `spring-ai` and `spring-mcp-server` use:

- `spring.ai.openai.api-key=${OPENAI_API_KEY}`

Provide it once in your shell environment (or via your IDE run configuration).

### Database
Both server modules default to:

- `jdbc:postgresql://localhost:5430/vector_store`
- user/password: `user`/`password`

---

## Troubleshooting

- **MCP server not reachable from `spring-ai`**
  - Ensure `spring-mcp-server` is running on port 8080 and started with `default,streaming`.
  - Ensure nothing else uses port 8080.

- **Vector store errors / missing tables**
  - This repo sets `spring.ai.vectorstore.pgvector.initialize-schema=false`.
  - Make sure the init SQL under `docker-entrypoint-initdb.d/` has been applied, or enable schema initialization for local experiments.

- **STDIO mode doesn’t work**
  - The `stdio` profile disables logging/banner for a reason—console output corrupts the STDIO protocol stream.

---

## Development tips

- You can run `spring-ai` and `spring-mcp-server` from your IDE as two separate run configs.
  - `spring-mcp-server`: Active profiles `default,streaming`
  - `spring-ai`: no special profiles needed
- For the DB, keep `docker compose up -d` running in the background.
