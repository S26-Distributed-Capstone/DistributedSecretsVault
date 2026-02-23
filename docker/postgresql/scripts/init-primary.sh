#!/bin/bash
set -e
# Ensure archive directory exists and is writable by postgres (for postgresql.conf archive_command)
mkdir -p /var/lib/postgresql/archive
chown postgres:postgres /var/lib/postgresql/archive
# Create replication user (used by production standbys)
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
CREATE USER ${POSTGRES_REPLICATION_USER} WITH REPLICATION PASSWORD '${POSTGRES_REPLICATION_PASSWORD}';
EOSQL
# Allow replication connections from standbys
echo "host replication ${POSTGRES_REPLICATION_USER} 0.0.0.0/0 scram-sha-256" >> "$PGDATA/pg_hba.conf"
