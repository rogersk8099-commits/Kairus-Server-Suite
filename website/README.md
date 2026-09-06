# Kairu SMP Community Website

The Kairu SMP website is a **TanStack Start, React 19, TypeScript, and Tailwind CSS** application for the public community site and player portal. It consumes the Kairu control plane through the single adapter in `src/services/smp.ts`.

## Local development

Use Node.js 22 and pnpm. No server credential belongs in this project.

```bash
cp .env.example .env.local
pnpm install --frozen-lockfile
pnpm dev
```

When `VITE_SMP_API_URL` is unset, the application uses bundled mock records. When a development build has an API URL but the API is unavailable, mock fallback remains enabled unless `VITE_ENABLE_MOCK_FALLBACK=false`. Production builds fail visibly instead of silently presenting mock data.

## Control-plane connection

Set the public API base URL at build time:

```dotenv
VITE_SMP_API_URL=https://your-service.up.railway.app
VITE_ENABLE_MOCK_FALLBACK=false
```

The adapter consumes the control plane's envelopes exactly: `{online, heartbeat}`, `{worlds}`, `{streams}`, `{events}`, `{players}`, `{tiers}`, `{profile}`, `{minecraftUuid, achievements}`, and `{link, statistics}`. It maps those wire records into the richer presentation types used by pages. Public requests send no bearer token or server secret.

The initial player routes require `X-Discord-User-Id`. For local integration tests only, set `VITE_DEV_DISCORD_USER_ID` to a linked Discord snowflake. This value is not authentication and must not be used for a public portal. Replace the frontend session stub and this header bridge with signed Discord OAuth sessions before production portal launch.

| Endpoint | Control-plane response |
| --- | --- |
| `GET /api/server/status` | `{ online, heartbeat }` |
| `GET /api/worlds` | `{ worlds: string[] }` |
| `GET /api/streams` | `{ streams: StreamRecord[] }` |
| `GET /api/events` | `{ events: EventRecord[] }` |
| `GET /api/leaderboard` | `{ players: PlayerSnapshot[] }` |
| `GET /api/membership/tiers` | `{ tiers: MembershipTier[] }` |
| `GET /api/me` | `{ profile: PlayerLink & { minecraft } }` |
| `GET /api/me/achievements` | `{ minecraftUuid, achievements }` |
| `GET /api/me/minecraft` | `{ link, statistics }` |

## Build

```bash
pnpm install --frozen-lockfile
pnpm build
```

The build output is `.output/` and is intentionally excluded from the source release. Configure the deployed website origin in the control plane's `CORS_ORIGIN` value.

## References

[1]: https://tanstack.com/start/latest "TanStack Start documentation"
[2]: https://react.dev/ "React documentation"
[3]: https://tailwindcss.com/docs "Tailwind CSS documentation"
[4]: https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS "MDN Cross-Origin Resource Sharing guide"
