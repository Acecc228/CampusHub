#!/usr/bin/env bash
set -euo pipefail
umask 077
if [[ ${1:-} != --apply ]]; then
  printf '%s\n' 'PREVIEW: restore encrypted snapshot into an existing EMPTY *_restore_* schema only. No drop/create/overwrite.'
  exit 0
fi
: "${CH_RESTORE_DEFAULTS:?private mysql option file for separately approved restore host}"
: "${CH_RESTORE_DATABASE:?empty restore schema}"
: "${CH_RESTORE_BACKUP:?absolute encrypted snapshot}"
: "${CH_RESTORE_IDENTITY:?private age identity file}"
[[ $CH_RESTORE_DATABASE =~ ^[a-z][a-z0-9_]*_restore_[0-9]{8,14}$ ]] || exit 2
for private_file in "$CH_RESTORE_DEFAULTS" "$CH_RESTORE_IDENTITY"; do
  [[ $private_file = /* && -f $private_file && ! -L $private_file && $(stat -c %a "$private_file") = 600 ]] || exit 2
done
[[ $CH_RESTORE_BACKUP = /* && -f $CH_RESTORE_BACKUP && ! -L $CH_RESTORE_BACKUP ]] || exit 2
bash "$(dirname -- "$0")/verify-backup.sh" "$CH_RESTORE_BACKUP"
rows=$(mysql --defaults-file="$CH_RESTORE_DEFAULTS" --protocol=TCP --batch --skip-column-names --database="$CH_RESTORE_DATABASE" -e 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();' 2>/dev/null) || exit 1
[[ $rows = 0 ]] || { echo 'Refusing nonempty restore schema' >&2; exit 2; }
# Only consume a trusted backup created by backup.sh. A dump is executable SQL.
# The restore credential MUST have privileges solely on this scratch schema.
if ! age -d -i "$CH_RESTORE_IDENTITY" "$CH_RESTORE_BACKUP" | gzip -dc | mysql --defaults-file="$CH_RESTORE_DEFAULTS" --protocol=TCP --database="$CH_RESTORE_DATABASE" 2>/dev/null; then
  echo 'FAIL: restore incomplete; retain scratch schema for inspection, never retry into it' >&2
  exit 1
fi
mysql --defaults-file="$CH_RESTORE_DEFAULTS" --protocol=TCP --batch --database="$CH_RESTORE_DATABASE" 2>/dev/null <<'SQL'
SELECT version,success FROM flyway_schema_history ORDER BY installed_rank;
SELECT COUNT(*) AS count_mismatches FROM activity a LEFT JOIN
 (SELECT activity_id,COUNT(*) n FROM activity_registration WHERE status='CONFIRMED' GROUP BY activity_id) r
 ON r.activity_id=a.id WHERE a.registered_count<>COALESCE(r.n,0);
SELECT COUNT(*) AS over_capacity FROM activity WHERE registered_count>capacity OR registered_count<0;
SELECT COUNT(*) AS unbound_identities FROM app_wechat_identity WHERE user_id IS NULL;
SQL
printf '%s\n' 'RESTORED: verify migration versions, zero anomalies, table counts and representative login/business reads before declaring PASS'
