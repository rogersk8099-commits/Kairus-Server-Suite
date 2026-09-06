# Kairu SMP Community Website

Kairu SMP is a cinematic Minecraft community website and player portal built with **TanStack Start, React 19, TypeScript, and Tailwind CSS**. The interface uses a dark cyberpunk visual system with restrained purple, magenta, and electric-blue lighting, custom voxel artwork, glass panels, neon edge treatments, and reduced-motion support.

The project is production-connected to the Railway control plane and includes server-side Discord OAuth sign-up/sign-in. Public content can still use local mock data during development, but private portal access is validated by a revocable server-side session.

## Experience Map

| Area              | Routes                                                                                                                                               | Included capabilities                                                                                                                                                                                                                                             |
| ----------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Public website    | `/`, `/play`, `/worlds`, `/live`, `/events`, `/leaderboards`, `/membership`, `/community`                                                            | Cinematic landing page, copyable Java and Bedrock addresses, server telemetry, visual world browser, creator discovery, platform and status filters, event registration feedback, sortable leaderboards, membership comparison, and creator programme information |
| Authentication    | `/login`, `/auth/discord`, `/auth/discord/callback`                                                                                                   | Discord authorization-code sign-up/sign-in with PKCE, one-use state and tickets, host-only secure cookies, CSRF-protected logout, and protected portal routing                                                                                                     |
| Player portal     | `/portal`, `/portal/profile`, `/portal/stats`, `/portal/discord`, `/portal/achievements`, `/portal/events`, `/portal/membership`, `/portal/settings` | Player KPIs, linked-account states, Minecraft statistics, achievements, event sign-ups, Discord identity, membership management, preferences, and responsive dashboard navigation                                                                                 |
| Shared foundation | `src/config`, `src/data`, `src/services`, `src/components/site`                                                                                      | Central branding, typed mock data, Railway-ready API service boundary, reusable visual primitives, global navigation, footer, and interaction components                                                                                                          |

## Local Development

Use Node.js 22 or a compatible modern Node.js release. The repository uses `pnpm`.

```sh
pnpm install
pnpm dev
```

The development server binds to `0.0.0.0:3000`. Create a production build with:

```sh
pnpm build
```

The primary account action is **Continue with Discord**. The legacy demonstration login is available only outside production for isolated UI development and is never accepted as a production identity.

## Discord OAuth

The website keeps its session in a same-origin `__Host-kairu_session` cookie with `HttpOnly`, `Secure`, and `SameSite=Lax`. Discord provider tokens, the OAuth client secret, database credentials, API secrets, and session material never enter browser JavaScript or browser storage. The control plane owns platform users, Discord identities, OAuth states, login tickets, sessions, and revocation.

Configure the website server with `API_URL`, `WEBSITE_URL`, `WEBSITE_API_SECRET`, `SESSION_SECRET`, `DISCORD_OAUTH_CLIENT_ID`, and `DISCORD_OAUTH_REDIRECT_URI`. Configure the same public identifiers and matching website/session secrets on the control plane, plus `DISCORD_OAUTH_CLIENT_SECRET`. None of these server-only variables may use a `VITE_` prefix. After both services are configured and the callback is registered, set the non-secret build flag `VITE_DISCORD_AUTH_ENABLED=true`; until then the CTA remains visible but safely disabled.

The exact redirect URI is:

```text
https://YOUR-WEBSITE-DOMAIN/auth/discord/callback
```

See [`docs/DISCORD_OAUTH.md`](docs/DISCORD_OAUTH.md) for the full security model, Railway variables, migration order, and activation tests.

## Branding and Content

Change `src/config/site.ts` to update the website name, server addresses, supported Minecraft version, Discord invite, social links, and global description. All major pages consume that configuration.

Visual asset URLs are centralized in `src/config/visuals.ts`. The site currently references CDN-hosted artwork so production builds stay lightweight. Typed demonstration content lives in `src/data/mock.ts`.

High-resolution source artwork is included in `design-assets/` in the downloadable project package. These files are not bundled by the application and can be rehosted or replaced without changing page components.

## Railway API Connection

Set `VITE_SMP_API_URL` to the public base URL of the Railway service:

```sh
VITE_SMP_API_URL=https://your-service.up.railway.app
```

When the variable is absent, the site uses local mock data. When it is present, `src/services/smp.ts` calls the endpoints below and sends cookies with `credentials: "include"`.

| Method | Endpoint                | Expected response    |
| ------ | ----------------------- | -------------------- |
| `GET`  | `/api/server/status`    | `ServerStatus`       |
| `GET`  | `/api/worlds`           | `World[]`            |
| `GET`  | `/api/streams`          | `Stream[]`           |
| `GET`  | `/api/events`           | `SmpEvent[]`         |
| `GET`  | `/api/leaderboard`      | `LeaderboardEntry[]` |
| `GET`  | `/api/membership/tiers` | `MembershipTier[]`   |
| `GET`  | `/api/me`               | `PortalUser`         |
| `GET`  | `/api/me/achievements`  | `Achievement[]`      |
| `GET`  | `/api/me/minecraft`     | `MinecraftStats`     |

The TypeScript contracts are defined in `src/data/types.ts`. The server-status query refreshes every 30 seconds and the stream query refreshes every 60 seconds when a remote API is configured. The Railway service must allow the website origin through Cross-Origin Resource Sharing and must allow credentials if cookie-based authentication is retained.

> The public service layer contains only safe browser requests. API secrets, Discord bot tokens, streaming platform credentials, subscription signing keys, and administrative operations must remain on the Railway backend.

## Validation

The project has been validated with a production build, strict type checking, linting, the complete OAuth test suite, protected server-route checks, desktop and mobile route captures, and nested portal navigation. Interactive filters, copy controls, registration feedback, membership cadence controls, account-state controls, and revocable session persistence are implemented.

## References

[1]: https://tanstack.com/start/latest "TanStack Start Documentation"
[2]: https://react.dev/ "React Documentation"
[3]: https://tailwindcss.com/docs "Tailwind CSS Documentation"
[4]: https://docs.railway.com/ "Railway Documentation"
