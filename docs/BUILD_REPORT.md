# Build Report

**Kairu SMP Complete Suite · Manus AI · 6 September 2026**

## Result

The complete suite was integrated against `CONTRACT.md`. Source mismatches were corrected rather than merely reported. The Paper plugin now sends the strict control-plane heartbeat, snapshot, link, queue, and acknowledgement shapes. The website now unwraps and maps every control-plane response envelope, sends no bearer secret, and retains mock fallback only for local/development use. The compiled bridge is bundled in the server pack.

## Integrated components

| Component | Result | Primary artifact |
| --- | --- | --- |
| Railway control plane and Discord bot | Pass | `control-plane/README.md` |
| KairuBridge Paper/Purpur plugin | Pass | `paper-plugin/build/libs/KairuBridge-1.0.0.jar` |
| Paper/Purpur server pack | Pass | `server-pack/README.md` |
| TanStack/React website | Pass | `website/README.md` |

## Corrections made

| Boundary | Corrected behavior |
| --- | --- |
| Plugin heartbeat | Removed body-only `serverId`, `maxPlayers`, and split version fields; added required `online`, `players`, and exact contract field names. Server identity remains in `X-Kairu-Server-Id`. |
| Plugin snapshots | Renamed fields to `minecraftUuid`, `playtimeSeconds`, `blocksBroken`, `distanceMeters`, `rankName`, and `worldName`; converted ticks to seconds and centimeters to meters; supplied contract-safe Vault/LuckPerms defaults. |
| Link completion | Renamed `minecraftUsername` to `javaUsername` and aligned validation with generated 27-character uppercase URL-safe codes. |
| Queue integration | Parsed `{commandType,payload}` records, mapped only explicit whitelist add/remove and notification actions, and sent `{status,errorMessage}` acknowledgements. |
| Website | Consumed `{online,heartbeat}`, `{worlds}`, `{streams}`, `{events}`, `{players}`, `{tiers}`, `{profile}`, `{minecraftUuid,achievements}`, and `{link,statistics}` envelopes; added development fallback controls. |
| Server pack | Bundled the newly compiled `plugins/KairuBridge.jar` and replaced the old incompatible config example with the exact plugin config schema. |

## Verification results

| Check | Command or method | Result |
| --- | --- | --- |
| Control-plane tests | `npm test` | **Pass:** 5 Fastify inject tests, 0 failures. |
| Control-plane types | `npm run typecheck` | **Pass:** strict TypeScript. |
| Control-plane build | `npm run build` | **Pass:** production JavaScript emitted to `dist/`. |
| Plugin clean test/build | `./gradlew clean test build` | **Pass:** 7 JUnit tests after integration corrections; shaded JAR generated. |
| Website dependencies/build | `pnpm install --frozen-lockfile && pnpm build` | **Pass:** Nitro/TanStack production output generated. |
| Website lint and types | `pnpm lint && pnpm exec tsc --noEmit` | **Pass:** 0 lint errors; 7 pre-existing React Fast Refresh export warnings. |
| Shell syntax | `bash -n` on suite shell scripts | **Pass.** |
| JSON validation | Python/JQ parsing of source JSON files | **Pass.** |
| YAML validation | Python PyYAML parsing of source YAML files | **Pass.** |
| JAR inspection | ZIP/JAR inventory and required entries | **Pass:** plugin class, `plugin.yml`, and `config.yml` present. |
| Control-plane runtime | Compiled process with no database/Discord variables; `GET /health` | **Pass:** status `ok`, storage `memory`. |
| Server installer guard | Isolated existing-JAR test | **Pass:** refused overwrite and did not create `eula.txt`. |
| Secret inspection | Text/binary/archive scan with allowlisted placeholders | **Pass:** no real credentials detected; populated `.env`, VCS, cache, and build-output directories excluded. |
| Release integrity | `sha256sum` and archive test | **Pass:** all listed artifacts verify and all ZIPs test cleanly. |
| Railway database migration | Container startup against managed PostgreSQL | **Pass:** `001_initial.sql` applied before API startup. |
| Railway public API | `/health` and all seven public data routes | **Pass:** every route returned HTTP 200. |
| Production plugin authentication | Unauthorized and authorized heartbeat requests | **Pass:** missing bearer key returned 401; generated production key returned 200. |
| Production telemetry projection | Authenticated heartbeat followed by `/api/server/status` | **Pass:** the public route returned the `production-01` server record. |

Windows PowerShell execution was not available in the Linux build environment. The scripts received structural review and safe-copy design validation, but must receive a native Windows smoke test before operational use. Paper 1.21.4 is officially unsupported; this is a compatibility caveat, not a build failure.[1]

## Release artifacts

| Artifact | Purpose |
| --- | --- |
| `release/Kairu-SMP-Complete-Suite.zip` | Complete source/documentation/server-pack distribution without dependency caches or generated build trees. |
| `release/KairuBridge.jar` | Deployable Paper/Purpur plugin. |
| `release/Kairu-Server-Pack.zip` | Standalone server pack including `plugins/KairuBridge.jar`. |
| `release/Kairu-Control-Plane-Source.zip` | Control-plane source and lockfile without dependencies, environment files, or compiled output. |
| `release/SHA256SUMS.txt` | SHA-256 integrity manifest for the four artifacts above. |

## Remaining production setup

Operators must provision real Railway/PostgreSQL, Discord, DNS, firewall, and server-host resources. Generate secrets outside source; set the same plugin key in Railway and the server-side KairuBridge config. Manually accept the Minecraft EULA only after review. Replace the player header bridge with signed Discord OAuth before public portal access. Stage-test Java and Bedrock joins, permission/economy behavior, backup restoration, and the unsupported 1.21.4 dependency set.

## References

[1]: https://fill-ui.papermc.io/projects/paper/version/1.21.4 "Paper 1.21.4 official version listing"
[2]: https://docs.papermc.io/paper/getting-started/ "Paper getting started and Java requirements"
[3]: https://docs.gradle.org/current/userguide/command_line_interface.html "Gradle command-line documentation"
[4]: https://nodejs.org/docs/latest-v22.x/api/ "Node.js 22 documentation"
[5]: https://pnpm.io/cli/install "pnpm install documentation"
