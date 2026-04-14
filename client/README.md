# Distributed Secrets Vault Client

This `client` folder is a standalone project that represents the external client system in the architecture.

## What it does

- Connects to the future gateway over HTTP.
- Sends create/get/update/delete requests to `/api/v1/secrets`.
- Supports optional bearer token auth.
- Retries retryable failures (`503`, `429`) with fixed delay.

## Project structure

- `src/main/java/.../Client.java` - reusable HTTP client.
- `src/main/java/.../ClientCli.java` - tiny runnable CLI.
- `src/test/java/.../ClientTest.java` - local HTTP server tests.

## Environment variables

- `DSV_API_BASE_URL` (default `http://localhost:8080`)
- `DSV_CLIENT_CONNECT_TIMEOUT_MS` (default `3000`)
- `DSV_CLIENT_READ_TIMEOUT_MS` (default `5000`)
- `DSV_CLIENT_MAX_RETRIES` (default `2`)
- `DSV_CLIENT_RETRY_DELAY_MS` (default `200`)
- `DSV_CLIENT_BEARER_TOKEN` (optional)

## Run tests

```powershell
Set-Location "C:\Users\Ari\CS Projects\DistributedSecretsVault\client"
..\mvnw.cmd test
```

## Run CLI examples

```powershell
Set-Location "C:\Users\Ari\CS Projects\DistributedSecretsVault\client"
$env:DSV_API_BASE_URL="http://localhost:8080"
..\mvnw.cmd -q exec:java -Dexec.args="create db-password hunter2"
..\mvnw.cmd -q exec:java -Dexec.args="get db-password"
..\mvnw.cmd -q exec:java -Dexec.args="update db-password hunter2 db-password new-secret"
..\mvnw.cmd -q exec:java -Dexec.args="delete db-password"
```

