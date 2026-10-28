#!/usr/bin/env bash
set -euo pipefail
umask 077
if [[ ${1:-} != --apply ]]; then
  printf '%s\n' 'PREVIEW: mysqldump consistent InnoDB snapshot -> gzip -> age encrypted file. Set CH_BACKUP_* and pass --apply.'
  exit 0
fi
: "${CH_BACKUP_DEFAULTS:?private absolute mysql option file}"
: "${CH_BACKUP_DATABASE:?independent production schema}"
: "${CH_BACKUP_RECIPIENT:?age public recipient}"
: "${CH_BACKUP_DIRECTORY:?private absolute output directory}"
[[ $CH_BACKUP_DATABASE =~ ^[a-z][a-z0-9_]{0,63}$ && $CH_BACKUP_DATABASE != campushub && $CH_BACKUP_DATABASE != campushub_test ]] || exit 2
[[ $CH_BACKUP_DEFAULTS = /* && -f $CH_BACKUP_DEFAULTS && ! -L $CH_BACKUP_DEFAULTS && $(stat -c %a "$CH_BACKUP_DEFAULTS") = 600 ]] || exit 2
[[ $CH_BACKUP_DIRECTORY = /* && -d $CH_BACKUP_DIRECTORY && ! -L $CH_BACKUP_DIRECTORY ]] || exit 2
[[ $CH_BACKUP_RECIPIENT = age1* ]] || exit 2
identity=$(mysql --defaults-file="$CH_BACKUP_DEFAULTS" --protocol=TCP --batch --skip-column-names --database="$CH_BACKUP_DATABASE" -e 'SELECT DATABASE();' 2>/dev/null) || { echo 'Backup connection failed' >&2; exit 1; }
[[ $identity = "$CH_BACKUP_DATABASE" ]] || exit 2
output="$CH_BACKUP_DIRECTORY/$CH_BACKUP_DATABASE-$(date -u +%Y%m%dT%H%M%SZ).sql.gz.age"
[[ ! -e $output && ! -e $output.partial ]] || exit 2
# No --databases / USE / DROP: restore is into a separately created empty schema.
# Raw diagnostics can contain host/user details; discard, preserve the exit status.
if ! mysqldump --defaults-file="$CH_BACKUP_DEFAULTS" --protocol=TCP --single-transaction --quick \
  --no-tablespaces --set-gtid-purged=OFF --skip-add-drop-table --skip-add-locks --skip-triggers \
  --default-character-set=utf8mb4 "$CH_BACKUP_DATABASE" 2>/dev/null \
  | gzip -c | age -r "$CH_BACKUP_RECIPIENT" -o "$output.partial"; then
  echo 'FAIL: backup pipeline; partial encrypted output retained for diagnosis, not valid backup' >&2
  exit 1
fi
mv -T -- "$output.partial" "$output"
digest=$(sha256sum < "$output")
printf '%s\n' "${digest%% *}" > "$output.sha256"
printf '%s\n' 'PASS: encrypted snapshot and checksum written; restore verification remains required'
