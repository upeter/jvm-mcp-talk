#!/usr/bin/env bash
set -euo pipefail

# This script is executed by the official Postgres entrypoint during first-time DB init
# when placed under /docker-entrypoint-initdb.d inside the container.
# It will iterate over all .sql files in this directory and execute them.

log() { echo "[INITDB] $(date -u +"%Y-%m-%dT%H:%M:%SZ") $*"; }

# Default envs used by the postgres image
: "${POSTGRES_DB:=postgres}"
: "${POSTGRES_USER:=postgres}"
: "${POSTGRES_PASSWORD:=}"

PSQL=(psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB")

log "Starting initialization for database '$POSTGRES_DB' as user '$POSTGRES_USER'"

# Execute SQL files in deterministic order
shopt -s nullglob
SQL_FILES=(/docker-entrypoint-initdb.d/*.sql)

if [ ${#SQL_FILES[@]} -eq 0 ]; then
  log "No .sql files found to execute."
else
  for f in "${SQL_FILES[@]}"; do
    log "Executing $(basename "$f")"
    # Use -f to run the file; psql will read from the file directly
    "${PSQL[@]}" -f "$f"
    log "Finished $(basename "$f")"
  done
fi

log "Initialization completed."