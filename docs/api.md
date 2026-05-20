# Distributed Secrets Vault API Reference

The DSV API provides endpoints to securely create, retrieve, update, and delete secret shards.

This API is intended for use when making a client to communicate with the DSV cluster. See the [DSV Client](https://github.com/S26-Distributed-Capstone/DSVClient) for a ready-made client for users to interact with the DSV system.

> [!NOTE]
> All requests are routed through the Traefik ingress gateway and handled by a DSV Worker acting as a Coordinating Node.

## Base URL
All endpoints are relative to the `/api/v1/secrets` path.

---

## Endpoints

### 1. Create a Secret
Creates a new secret and distributes its shards across the cluster.

**POST** `/api/v1/secrets`

**Request Body** (application/json):
```json
{
  "user": "string",
  "secretName": "string",
  "secretValue": "string"
}
```

**Responses:**
- `200 OK`: Secret created successfully. Returns the version number.
- `409 Conflict`: A secret with this name already exists for the user, or a concurrent create request arrived first.
- `503 Service Unavailable`: Failed to reach quorum during the write phase.

---

### 2. Update a Secret
Creates a new version of an existing secret. 

**PUT** `/api/v1/secrets`

**Request Body** (application/json):
```json
{
  "user": "string",
  "secretCurrentName": "string",
  "secretUpdatedValue": "string"
}
```

**Responses:**
- `200 OK`: Secret updated successfully. Returns the new version number.
- `404 Not Found`: The secret does not exist.
- `409 Conflict`: A concurrent update arrived first. Retry required.
- `503 Service Unavailable`: Failed to reach quorum during the write phase.

---

### 3. Retrieve Latest or Specific Version
Retrieves the plaintext value of a secret by fetching `k` shards from the cluster and reconstructing them in memory.

**GET** `/api/v1/secrets/{id}`

**Query Parameters:**
- `user` (Required): The owner of the secret.
- `version` (Optional): The specific historical version to retrieve. If omitted, the latest version is returned.

**Responses:**
- `200 OK`: Returns the plaintext secret string.
- `404 Not Found`: The secret (or specified version) does not exist.
- `503 Service Unavailable`: Insufficient shards available to reconstruct the secret (fewer than `k` shards).

---

### 4. Retrieve All Versions
Retrieves a map of all historical versions and their plaintext values for a given secret.

**GET** `/api/v1/secrets/{id}/all`

**Query Parameters:**
- `user` (Required): The owner of the secret.

**Responses:**
- `200 OK`: Returns a JSON map of versions to their plaintext values.
  ```json
  {
    "1": "old-value",
    "2": "new-value"
  }
  ```
- `404 Not Found`: The secret does not exist.
- `503 Service Unavailable`: Insufficient shards available to reconstruct the secret.

---

### 5. Delete a Secret
Broadcasts a delete command to permanently remove all shards of a secret from the cluster.

**DELETE** `/api/v1/secrets`

**Request Body** (application/json):
```json
{
  "user": "string",
  "deleteName": "string"
}
```

**Responses:**
- `204 No Content`: The secret shards were successfully deleted from at least `m-k+1` nodes.
- `404 Not Found`: The secret does not exist.

---

### 6. Process a `.env` File
Processes a client-supplied `.env` file containing create, update, and delete operations.

**POST** `/api/v1/secrets/env`

Each non-empty line must use:

```env
Key1=new:val
Key2=update:val
Key3=delete
```

The action must be `new`, `update`, or `delete`. `new` and `update` require a value after `:`;
`delete` only needs the key name and action. Keys must be unique within the file; duplicate keys fail the request before any write is attempted.

Supported request formats:

- `text/plain` body with `user` as a query parameter.
- `multipart/form-data` with `user` and a `file` part.
- `application/json` with `user` and `envFileContent` fields. The content field also accepts aliases `content`, `env`, or `envFile`.

**Responses:**
- `200 OK`: The file was processed. Returns operation counts.
- `400 Bad Request`: The file is malformed, has duplicate keys, or is missing a user.
- `404 Not Found`: An `update` or `delete` key does not exist.
- `409 Conflict`: A `new` key already exists.
- `503 Service Unavailable`: Failed to reach quorum during one of the write phases.
