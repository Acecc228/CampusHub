# M1 production implementation plan — 2026-10-27

Goal: prepare the accepted M1 activity platform for real deployment while preserving M2 and v1.0.0. User has authorized autonomous implementation, local commits and necessary tests, but not public exposure, production changes, purchases, pushes, merging or WeChat submission.

## Architecture and decisions

- Base main `7ba348d`; isolated `codex/m1-production`. Original M2 checkout remains `4f373ea`; v1.0.0 remains `685066a`.
- One Java 21 / Boot 4.1.1 process, MySQL 8.4, Nginx TLS and Vue same-origin static assets. In-memory sessions intentionally expire on restart. No Redis, Kubernetes or M2 functionality.
- Keep existing password hashing and form authentication for operator-provisioned non-demo management accounts. No public registration or role editing API. Reject student/demo accounts at formal password login.
- WeChat code exchange uses fixed official HTTPS endpoint, bounded timeout, no automatic retry, no secret/code/session_key logging. Unique appid/openid mapping creates STUDENT transactionally. WeChat identity is not proof of school enrollment. No automatic binding to an existing privileged account.
- Preserve Session/CSRF; rotate session on login and refresh token. Validate enabled status, current roles and password credential snapshot on subsequent authenticated requests. Revoke stale sessions rather than silently elevating them.
- V8 is additive, V5–V7 reserved for separate M2 branch. Ship only V1–V4,V8. Shared local test already has M2 migrations: privately resolve their exact original files for validation, never include them in release. No repair/baseline/ignore-missing.
- Production requires explicit database and WeChat configuration, secure cookies and no demo authentication. MySQL on the production host may use loopback, but local development credentials/database are prohibited. No production secrets in public client build.
- Single-instance bounded login limiting plus proxy public/write rate limits. Restrict management access to trusted operator network at Nginx. Redacted route/status/latency logs without query, bodies, cookies or identities.
- Alternative considered: token/JWT and Redis session cluster; rejected because it adds revocation and operational complexity without a current multi-instance requirement. Managed identity provider deferred; existing PBKDF2 and trusted provisioning fit the established architecture.

## Tasks and ownership

- [x] Inspect main/M2/tag/worktree and current authentication contracts (main).
- [x] Backend formal authentication, V8 and tests (Sol; owns backend except application-prod.yml, V8).
- [x] Vue/miniprogram configuration, formal login, build guards and tests (Terra; owns client directories).
- [x] Production config, Nginx/systemd, migration/backup/provisioning tooling and deployment runbook (main).
- [x] Serialized migration/grants/test execution on 127.0.0.1:3307/campushub_test only (main).
- [x] Review integrated diff, real MySQL security/business suite, client checks, production package/config checks (main).
- [x] Record external blockers, Git/Release state and handoff (main); retain implementation and evidence in the local production branch, inspect `git log -1` for its commit.

## Interfaces and verification

Existing GET csrf, GET me, POST logout are retained. POST `/api/v1/auth/login` accepts form username/password. POST `/api/v1/auth/wechat-login` accepts JSON code only. Both return existing Identity. Client refreshes CSRF after login. Production Vue is same-origin `/api/v1`; miniapp uses explicit HTTPS origin and AppID.

Critical tests before implementation: client-controlled roles/IDs rejected; first-login races map one identity; wrong/used code fails without credentials or raw upstream errors; student cannot management-login; revoked/disabled users lose session; CSRF/session fixation; production demo/config rejects; invalid public build rejects. Existing real MySQL tests cover activity ownership, registration last-slot races, capacity/cutoff/offline constraints.

Evidence labels: unit/contract simulation is not real WeChat; real MySQL HTTP is not UI; simulator is not device; local production-profile smoke is not deployment. Production backup restore and real public TLS/WeChat verification require independent resources. Detailed evidence belongs under docs/verification.

## Execution ledger

2026-10-27: original worktree clean; new native worktree attached. Cross-model tools support explicit overrides; Sol backend and Terra clients dispatched, no recursion and no agent database writes. Historical stage skill path no longer exists in repo/user skill directory; use current AGENTS and focused existing scripts instead.

2026-10-28: 98 backend tests PASS after first-run failure and security review fixes; Vue14/miniprogram29 PASS; real Edge formal login/create/publish/logout PASS, activity845. Main fixed definite401 miniapp recovery and portable backup checksum; full results and remaining external gates in verification/m1-production.md. No production deployment or official WeChat validation inferred.
