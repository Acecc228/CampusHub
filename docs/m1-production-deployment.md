# M1 production deployment runbook

Status on 2026-10-27: preparation only. User confirmed no formal WeChat AppID/permissions, server, domain/certificate or filing resources. No public service, production database, WeChat submission or deployment exists. Suggested next release **v1.1.0** (authentication capability added; v1.0.0 stays local MVP). Do not tag until real acceptance and explicit publish authorization.

## Topology and cost

One Linux VM (Java 21, Nginx, MySQL 8.4, Python 3, age) is sufficient for a small portfolio pilot; start with 2 CPU / 2–4 GiB RAM and monitor memory and disk. This is a sizing assumption, not a purchased resource or capacity guarantee. No paid resource has been created. Allow inbound 443 and necessary certificate challenge 80; SSH only from trusted administrative addresses. MySQL and Java are private/loopback. Do not tunnel or expose local Windows MySQL.

Use one origin: `https://<domain>/` for Vue and `/api/v1` for API. This avoids cross-origin session cookies; no broad CORS policy is needed. Nginx terminates TLS; the backend cookie is Secure/HttpOnly/SameSite=Strict, path `/`, no Domain. Forwarded headers are ignored and stripped. Spring sessions live in this single process; restart invalidates sessions and clients must log in again. Horizontal scaling requires a separately reviewed session design.

## Inputs and minimum human steps

1. Register an eligible miniapp and grant developer access. Supply public AppID; configure its server-only AppSecret directly in a private server secret file. Confirm required account subject, service category, privacy disclosures and any school authorization in WeChat console. WeChat login alone does not verify school enrollment.
2. Provide an independently provisioned server and administrative access through a private approved channel; choose domain and hosting region, complete applicable domain/filing requirements, point DNS and obtain a valid publicly trusted TLS certificate. Confirm purchase/public exposure separately.
3. Approve the concrete deployment target and maintenance window. Configure miniapp request legal domain as the HTTPS host (no path), verify certificate chain, and retain domain validation in release/device builds.
4. After staging acceptance, owner personally completes required WeChat privacy/subject materials and grants explicit review submission/release authorization. Neither is covered by code-preparation permission.

## Build and immutable release directory

Use the current production branch; confirm no M2 venue sources or V5–V7 SQL in release. Run backend real-MySQL verify using the isolated local test script before packaging. Linux uses `backend/mvnw`; Windows uses the existing wrapper and `scripts/Invoke-Backend.ps1 -Apply`. Do not skip the integration suite to claim acceptance. See client build commands in `m1-production-clients.md`.

Create `/srv/campushub/releases/<version>/` containing `backend.jar`, `admin-web/` from Vue dist, and a `source/` migration bundle with `backend/pom.xml`, `backend/mvnw`, `backend/.mvn/`, and `infra/mysql/migrations/` **V1–V4,V8 only**, preserving these relative paths. Record SHA-256 for each artifact and the Git commit. Set files read-only for the service user; migration runs as a different OS/database operator with access to its own Maven cache. `/srv/campushub/current` points to the chosen release. Do not pack `.local`, test reports with sensitive data, node_modules, Git data or secrets. Miniapp `dist-production/` is a separate artifact imported into official DevTools; it is never hosted as Vue static assets.

## Independent production database and migration

DBA provisions `campushub_prod` with utf8mb4, explicit time zone, separate app/migrator/operator/backup accounts and unique generated passwords. Require TLS, narrow account host patterns and firewall reachability. Apply the reviewed grants template after migrations. Application cannot DDL, DELETE business data, grant privileges, modify roles or alter existing account status/passwords. Trusted operator can manage account credentials/roles; migrator only runs during deployment. App registration necessarily INSERTs users and STUDENT links; service tests enforce the role restriction.

Use a CA-validated MySQL connection: `sslMode=VERIFY_IDENTITY`, exact certificate host and Java truststore if private CA. Do not replace with disabled TLS. Production startup checks expected schema and CURRENT_USER, and rejects development/test identities. `application.env.example` documents configuration; root-owned `/etc/campushub/application.env` must be 0600 and must contain no migrator credentials.

Migration environment is confined to a separate shell/process with private FLYWAY_URL, FLYWAY_USER, FLYWAY_PASSWORD and FLYWAY_SCHEMA. On the verified release run:

```sh
cd /srv/campushub/releases/<version>/source/backend
./mvnw --batch-mode --no-transfer-progress -Pmigration flyway:info
./mvnw --batch-mode --no-transfer-progress -Pmigration flyway:migrate flyway:validate
```

Never run clean/repair/baselineOnMigrate; empty production starts at V1, then V2,V3,V4,V8. Existing local test already has V5–V7 from M2; `scripts/Prepare-M1ProductionTest.ps1` privately resolves those exact historical files and only adds V8 in `campushub_test`. That test history is **not** the production deployment inventory. A fresh independent production migration remains a staging gate; do not copy local database accounts, data or migration resolver to production.

## Trusted account operations

Use a dedicated production operator option file (`[client]`, host, port, protocol=TCP, user, password, ssl-mode=VERIFY_IDENTITY, ssl-ca if needed), absolute path and mode 0600. No credentials on command lines. Operator CLI previews unless `--apply` is supplied:

```sh
python3 infra/production/operations.py create --defaults-file /etc/campushub/operator.cnf --database campushub_prod --account campushub_prod_operator@localhost --login organizer1 --display-name '活动组织者' --role ORGANIZER
# After reviewing the exact target, repeat with --apply; password is prompted without echo.
python3 infra/production/operations.py disable --defaults-file /etc/campushub/operator.cnf --database campushub_prod --account campushub_prod_operator@localhost --login organizer1
```

`reset-password` uses the same trusted process. Create conflicts abort, never overwrite an existing account. Record action/time/operator separately, never the password/hash. For role changes, trusted DBA performs a transaction on app_user_role after verifying the target ID and desired role; no public role-management endpoint exists. Subsequent authenticated requests revoke stale roles, disabled status or changed password. A request already authorized before revocation can finish; emergency removal also stops ingress/restarts sessions. No self-service password reset or account recovery is implemented.

## Nginx and process deployment

Adapt `infra/production/nginx.conf` domain/cert paths and the management allow CIDR; closed default deliberately denies remote admin login. Install proxy snippet as `/etc/nginx/snippets/campushub-proxy.conf`. Verify `nginx -t` before a reload. Use an unprivileged `campushub` service account, install `campushub.service`, load secret environment, and run `systemd-analyze verify` before enabling it. Java stays on 127.0.0.1:8080.

Nginx applies per-client login/public/management rate limits and returns 429; the application has a bounded global/account login limiter as defense in depth. The proxy never retries writes. Do not introduce a CDN/LB without reviewing real-client IP trust; arbitrary forwarded IPs must not bypass throttles.

Check local `http://127.0.0.1:8080/actuator/health` from the server and public API categories via HTTPS. Do not expose actuator, docs or demo-login. Verify Cookie attributes with DevTools without copying Cookie headers to reports. Check browser formal organizer login/create/publish; official miniapp wx.login/browse/register/cancel/re-register; organizer list; student forbidden management; disabled account revoked; last-slot race; TLS renewal and app restart recovery. Log each evidence type separately.

## Logs, monitoring and backup

Proxy access logs contain method, path, status, latency only. Do not enable query/body/header logging or HTTP-client wire DEBUG. Error logs can include request URLs; restrict log access/retention and keep authentication codes in JSON bodies. Use logrotate/journald size limits and 7–14 day initial retention, adjusted to approved privacy policy. Monitor health failures, 5xx, 429, disk space, certificate expiry and backup age; configure notification destination when available. No monitoring account/notification service was created.

Back up before each migration and daily. `backup.sh` defaults to preview; set CH_BACKUP_DEFAULTS (backup-only credential), CH_BACKUP_DATABASE, CH_BACKUP_DIRECTORY and CH_BACKUP_RECIPIENT (age public key), then `bash backup.sh --apply`. Private decryption key belongs off-server. Output is encrypted, checksummed and retained on failure as `.partial`; partial files are never valid backups. Store an encrypted off-host copy and verify retention/recovery; no automatic deletion in this tooling.

Monthly and before release, provision a separate empty `<name>_restore_<timestamp>` database with a restore account restricted to that schema, preferably on a separate host. Set CH_RESTORE_DEFAULTS, CH_RESTORE_DATABASE, CH_RESTORE_BACKUP, CH_RESTORE_IDENTITY, then preview and run `restore-drill.sh --apply`. It refuses nonempty schemas. Only trusted dumps from this backup script are allowed because dumps execute SQL. Compare full row counts, migration versions and invariant results, then run representative reads/business smoke against the restored instance. Record elapsed backup/restore time and determine RPO/RTO from measurements; none is claimed yet. Full production encrypted backup/restore is BLOCKED pending resources/tools, not replaced by static syntax checks.

## Rollback

Stop write ingress, retain logs and take an encrypted snapshot. If schema compatibility is verified, switch `current` to the prior release and restart the service (all sessions expire); check health and core read flows before reopening writes. V8 is additive, so do not drop it during application rollback. M1 local demo v1.0.0 is not an acceptable public authentication rollback: if no previously approved production artifact exists, keep service offline while repairing. For incompatible schema changes/data loss, restore to an independent database, validate it, then explicitly switch credentials/target during a reviewed outage. Never overwrite a live database or execute a reverse migration blindly.

## Unexecuted deployment gates

Real production MySQL creation/fresh migration, Linux Nginx/systemd startup, public TLS/Cookie verification, production encrypted full restore, official WeChat exchange, native device login/business flow, privacy submission, review and publishing remain BLOCKED or NOT_RUN. Local test and build PASS cannot satisfy these gates.
