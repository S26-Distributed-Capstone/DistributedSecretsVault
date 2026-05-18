# Local CRUD Smoke Test (PowerShell)

These commands assume the local Kubernetes service is port-forwarded to `127.0.0.1:8080`.

Start the port-forward in one PowerShell terminal:

```powershell
kubectl port-forward service/dsv-app-service 8080:80
```

Run the CRUD commands in another PowerShell terminal:

```powershell
$BASE = "http://127.0.0.1:8080"
```

## Health

```powershell
Invoke-RestMethod "$BASE/actuator/health" | ConvertTo-Json -Depth 10
```

## Create

```powershell
$body = @{
  user = "noam"
  secretName = "db-password"
  secretValue = "super-secret-123"
} | ConvertTo-Json -Compress

Invoke-RestMethod -Method Post `
  -Uri "$BASE/api/v1/secrets" `
  -ContentType "application/json" `
  -Body $body
```

Expected response:

```text
Secret created (version: 1)
```

## Get Latest

```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE/api/v1/secrets/db-password?user=noam"
```

Expected response:

```text
super-secret-123
```

## Update

```powershell
$body = @{
  user = "noam"
  secretCurrentName = "db-password"
  secretUpdatedValue = "new-secret-456"
} | ConvertTo-Json -Compress

Invoke-RestMethod -Method Put `
  -Uri "$BASE/api/v1/secrets" `
  -ContentType "application/json" `
  -Body $body
```

Expected response:

```text
Secret updated (version: 2)
```

## Get Version 1

```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE/api/v1/secrets/db-password?user=noam&version=1"
```

Expected response:

```text
super-secret-123
```

## Get All Versions

```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE/api/v1/secrets/db-password/all?user=noam" |
  ConvertTo-Json -Compress
```

Expected response:

```json
{"1":"super-secret-123","2":"new-secret-456"}
```

## Delete

```powershell
$body = @{
  user = "noam"
  deleteName = "db-password"
} | ConvertTo-Json -Compress

Invoke-RestMethod -Method Delete `
  -Uri "$BASE/api/v1/secrets" `
  -ContentType "application/json" `
  -Body $body
```

Expected response: no body with HTTP `204 No Content`.

## Confirm Deleted

```powershell
Invoke-RestMethod -Method Get `
  -Uri "$BASE/api/v1/secrets/db-password?user=noam"
```

Expected response: an error response indicating the secret was not found.
