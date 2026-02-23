#!/bin/bash
set -e
PGDATA="${PGDATA:-/var/lib/postgresql/data}"

# If already a standby (restart), just start Postgres
if [ -f "$PGDATA/standby.signal" ]; then
  exec /usr/local/bin/docker-entrypoint.sh postgres
fi

# Wait for primary to be ready
until PGPASSWORD="$POSTGRES_REPLICATION_PASSWORD" pg_isready -h "$POSTGRES_PRIMARY_HOST" -U "$POSTGRES_REPLICATION_USER"; do
  echo "Waiting for primary at $POSTGRES_PRIMARY_HOST..."
  sleep 2
done

# Bootstrap standby from primary
echo "Running pg_basebackup from $POSTGRES_PRIMARY_HOST..."
PGPASSWORD="$POSTGRES_REPLICATION_PASSWORD" pg_basebackup -h "$POSTGRES_PRIMARY_HOST" -U "$POSTGRES_REPLICATION_USER" -D "$PGDATA" -Fp -Xs -P -R -w

# Set application_name for synchronous_standby_names (must match postgresql.conf: postgres-1, postgres-2)
if [ -n "$REPLICA_NAME" ] && [ -f "$PGDATA/postgresql.auto.conf" ]; then
  sed -i "s/'$/ application_name='$REPLICA_NAME'/'/" "$PGDATA/postgresql.auto.conf"
fi

exec /usr/local/bin/docker-entrypoint.sh postgres
