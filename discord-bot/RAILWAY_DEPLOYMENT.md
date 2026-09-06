# Railway Deployment Runbook

**Document status:** This is a deployment design and procedure. It does not assert that a Railway project, service, domain, database, variable, deployment, or rollback target exists.

## Required Railway topology

Deploy the central Kairu API and the dedicated Discord bot as **separate Railway services**. Attach both to the same PostgreSQL service only if they need the same canonical data and the API remains the owner of the shared schema and migration process. Separate services permit independent scaling, release, logs, health checks, permissions, and rollback. They do not relax source-of-truth or secret boundaries.

| Railway service | Deployable unit | PostgreSQL relationship | Required dependencies |
|---|---|---|---|
| `kairu-api-<environment>` | Central API image/build. | Reads/writes canonical data; owns Prisma schema and migration release. | PostgreSQL, provider secrets, bot service trust. |
| `kairu-discord-bot-<environment>` | Dedicated bot image/build. | Prefer API-only canonical access. If a direct DB connection is explicitly approved, limit it to its owned operational schema/tables and never auto-migrate shared schema. | Central API URL/token, Discord configuration. |
| `kairu-postgres-<environment>` | Railway PostgreSQL service. | Single shared data store per environment; never share staging/production database. | Backup, restore, access and migration owner. |
| `kairu-control-plane-migrate-<environment>` | One-off/CI release job, not a permanent service. | Applies reviewed control-plane SQL exactly once; sole DDL authority. | Control-plane release artifact and `DATABASE_URL`. |

Railway reference variables can establish dependency ordering when staged changes deploy related services; for example, an API referencing `${{Postgres.DATABASE_URL}}` waits for the PostgreSQL service in a coordinated deployment. Independent GitHub push deploys do not receive that ordering guarantee. [1]

## Service configuration procedure

1. Create a Railway project/environment for **staging** first. Add a Railway PostgreSQL service. Record the database service ownership and backup expectations outside this repository.
2. Create the `kairu-api-staging` service from the reviewed source/release artifact. Set its build and start commands according to the project’s actual package manifest. Add API variables from [Environment variables](ENVIRONMENT_VARIABLES.md), including a reference to the staging PostgreSQL service.
3. Create the `kairu-discord-bot-staging` service separately from the reviewed source/release artifact. Give it a distinct start command and only bot-specific variables, including `API_URL` and `API_SECRET`. Do not copy all API provider secrets into the bot service.
4. Configure a health endpoint for each HTTP-capable service. For a Gateway-only bot, configure startup/log/uptime monitoring appropriate to its runtime and include an authenticated API connectivity health indicator without revealing secret values.
5. Create a dedicated control-plane SQL migration step. It applies reviewed SQL exactly once before code requiring the schema is active. Never place `prisma migrate`, `prisma db push`, or DDL in the bot build/start commands.
6. Add a production environment only after staging activation, failed-path testing, backup verification, and ownership approval. Create separate Discord, provider, and database credentials for production.

## Release sequence

| Phase | Operator action | Acceptance gate |
|---|---|---|
| 1. Preflight | Confirm reviewed commit/image, secret bindings, current backup, migration review, and known-good deployment IDs. | All owners approve and no secret appears in build logs. |
| 2. Migration | Run the designated control-plane SQL job. | Exit success and the control plane reports the expected schema release. |
| 3. API deploy | Deploy API release; wait for health and dependency checks. | API authenticates bot service and serves expected version. |
| 4. Bot deploy | Deploy separate bot service. | Bot connects/validates configuration and exposes no error loop. |
| 5. Discord activation | Install/enable in staging, test interaction and `/setup-server` replay. | One audit result and no duplicated artifacts. |
| 6. Observe | Monitor errors, latency, Discord 429s, queue/retry depth, and provider failures. | Metrics remain within approved operating budget. |
| 7. Promote | Repeat controlled release in production. | Explicit release record and rollback target retained. |

The bot schema is a read/write client mapping only. All DDL remains in reviewed control-plane SQL; the bot generates a client but never migrates a database.

## Rollback procedure

Railway rollback reverts to a previously successful deployment and restores its Docker image and custom variables. It does **not** necessarily reverse database changes or external provider side effects. Select the prior deployment from the relevant service’s **Deployments** view, use the action menu, and confirm the rollback. Retention limits can make older deployments unavailable, so record the known-good release before a change. [1]

1. **Contain.** Pause unsafe bot commands, disable affected webhook subscription or integration, or remove the bot from the impacted guild if abuse risk exists. Preserve logs and correlation IDs.
2. **Classify.** Determine whether the failure is bot-only, API-only, shared schema, secret, or provider-related. Do not roll back an unrelated service as a reflex.
3. **Roll back code/config.** From the correct Railway service’s Deployments view, select the last known-good successful deployment and confirm **Rollback**. Verify the returned image and custom variables are expected.
4. **Handle data separately.** If a migration ran, decide with the database owner whether the rolled-back code is compatible with the existing schema. Prefer code compatible with the expanded schema; use reviewed forward repair or tested restore only when necessary.
5. **Verify.** Check health, command invocation, API authorization, idempotency, provider callbacks, and error rate. Keep elevated monitoring until the incident is closed.
6. **Document.** Record impacted release IDs, migration IDs, duration, data decision, secrets rotated, and preventive follow-up.

| Failure mode | Primary rollback target | Important caveat |
|---|---|---|
| Bot cannot authenticate to Discord | Bot service and bot token configuration. | Resetting a Discord token invalidates old runtime instances; deploy new secret before re-enabling. |
| Bot cannot reach central API | Bot service variable/configuration or API service. | Do not add direct database access as an emergency workaround. |
| API release regression | API service. | Ensure previous API is compatible with current schema. |
| Bad migration | Database forward-fix or restore plan. | Railway deployment rollback alone does not undo schema/data. |
| Provider webhook loop | Central API/provider subscription. | Disable subscription or endpoint path before reprocessing. |

## References

[1]: https://docs.railway.com/deployments/deployment-actions "Railway Documentation: Deployment Actions"
[2]: https://www.prisma.io/docs/orm/prisma-client/deployment/deploy-database-changes-with-prisma-migrate "Prisma Documentation: Deploying database changes with Prisma Migrate"
[3]: https://docs.railway.com/guides/variables "Railway Documentation: Variables"
[4]: https://docs.railway.com/guides/healthchecks "Railway Documentation: Healthchecks"
