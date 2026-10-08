#!/usr/bin/env bash
# Parallel reserve check for the weekly limit (PRD US5). Run on the server: a throwaway Free user sends 20
# reservations at once from separate database sessions; at most 10 may be granted. The user is deleted afterwards.
# Usage: concurrency_check.sh <db container>
set -euo pipefail

DB="${1:?db container name}"
USER_ID="cccccccc-0000-4000-8000-$(date +%s | tail -c 13 | xargs printf '%012d')"
psql() { docker exec -i "$DB" psql -U postgres -X -q -tA -v ON_ERROR_STOP=1 "$@"; }

cleanup() { psql -c "delete from auth.users where id = '$USER_ID'" >/dev/null || true; }
trap cleanup EXIT

psql -c "insert into auth.users (id, email) values ('$USER_ID', 'concurrency-$USER_ID@example.com')" >/dev/null

seq 1 20 | xargs -P 20 -I{} docker exec "$DB" psql -U postgres -X -q -tA -c \
    "select public.reserve_separation('$USER_ID', '[{\"jobRef\": \"c-{}\"}]')" >/dev/null

granted=$(psql -c "select count(*) from public.ai_usage where user_id = '$USER_ID' and status = 'reserved'")
denied=$(psql -c "select count(*) from public.ai_usage where user_id = '$USER_ID' and status = 'denied'")
echo "concurrency: granted=$granted denied=$denied"
if [ "$granted" -le 10 ] && [ $((granted + denied)) -eq 20 ]; then
    echo "ok - 20 parallel reservations granted at most 10"
else
    echo "not ok - parallel reservations passed the limit"
    exit 1
fi
