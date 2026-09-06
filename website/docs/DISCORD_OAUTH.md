# Discord OAuth Sign-Up and Sign-In

The Kairu SMP website uses Discord OAuth 2.0 as its production account entry point. A first successful Discord authorization creates a **platform user** and links the Discord identity. Later authorizations sign the same user in. The browser never receives the Discord access token, refresh token, application secret, database URL, or website-to-API secret.

## Request flow

1. The visitor selects **Continue with Discord** on `/login`.
2. The website server requests a one-time OAuth state from the Kairu control plane. The state expires quickly and is stored only as an HMAC hash.
3. The website redirects the browser to Discord using the authorization-code flow, `identify` scope, an exact callback URI, and PKCE S256.
4. Discord returns the browser to `/auth/discord/callback` with a short-lived code and state.
5. The website server forwards the callback to the control plane over authenticated server-to-server HTTPS. The control plane atomically consumes the state, exchanges the code, retrieves the Discord profile, revokes the provider token, and upserts the platform user and OAuth identity.
6. The control plane returns a one-use, short-lived login ticket. The website server redeems it and sets `__Host-kairu_session`, a host-only `HttpOnly`, `Secure`, `SameSite=Lax` cookie.
7. Protected portal routes validate the session server-side. Logout requires an exact allowed `Origin` and a CSRF token, revokes the server-side session, and clears the cookie.

No OAuth or session credential is stored in `localStorage` or `sessionStorage`, and no secret uses a `VITE_` prefix.

## Discord Developer Portal

Create or select the Discord application that owns the Kairu community integration. In **OAuth2**, register one redirect URI that exactly matches:

```text
https://YOUR-WEBSITE-DOMAIN/auth/discord/callback
```

Use the **authorization code** flow and request only the `identify` scope. Do not enable the implicit grant. The website OAuth client secret belongs only in the control-plane service's secret store.

## Railway variables

| Service | Variable | Requirement |
| --- | --- | --- |
| Both | `WEBSITE_URL` | Exact HTTPS website origin; no path, query, fragment, or trailing slash. |
| Both | `WEBSITE_API_SECRET` | Same independently generated value on both services; at least 32 characters; server-side only. |
| Both | `SESSION_SECRET` | Same independently generated value on both services; at least 32 characters and different from every API/admin secret. |
| Both | `DISCORD_OAUTH_CLIENT_ID` | Discord application snowflake. |
| Both | `DISCORD_OAUTH_REDIRECT_URI` | Exact website callback URL. |
| Website | `API_URL` | HTTPS origin of the Kairu control plane. |
| Website | `VITE_DISCORD_AUTH_ENABLED` | Public boolean only. Set to `true` **after** every server-side OAuth variable is configured and verified; it contains no secret. |
| Control plane | `DISCORD_OAUTH_CLIENT_SECRET` | Discord OAuth application secret; never configure it on the website. |
| Control plane | `DATABASE_URL` | Shared PostgreSQL connection supplied by Railway. |

`WEBSITE_API_SECRET` is separate from `ADMIN_API_KEY`, `PLUGIN_API_KEY`, and the dedicated Discord bot's `API_SECRET`. This separation limits the effect of credential compromise.

## Database migration

The control plane owns database migrations. `migrations/004_website_auth.sql` adds platform users, Discord identities, OAuth states, one-use login tickets, sessions, and cleanup indexes/functions. Deploy the control plane before deploying the website so the pre-deploy migration completes first.

Never run browser-side migrations or allow the website process to create tables at startup.

## Local development

The production cookie is Secure and therefore requires HTTPS. The demo username flow remains available only when both services run outside production. It is not an alternative production authentication mechanism.

Run the complete validation before every release:

```bash
# Control plane
npm install
npm run typecheck
npm test
npm run build

# Website
pnpm install
pnpm typecheck
pnpm lint
pnpm test
pnpm build
```

## Activation test plan

Test a first-time sign-up, returning sign-in, user cancellation, invalid state, replayed state, expired state, invalid callback, a revoked or expired session, logout with valid and invalid CSRF values, direct navigation to `/portal`, and direct access to private server functions. Confirm that logs never contain authorization codes, state values, PKCE verifiers, tickets, sessions, CSRF tokens, client secrets, or provider tokens.

## References

[Discord OAuth2 documentation](https://discord.com/developers/docs/topics/oauth2) describes the authorization-code flow and application configuration. [RFC 7636](https://www.rfc-editor.org/rfc/rfc7636) defines PKCE. [OWASP OAuth security guidance](https://cheatsheetseries.owasp.org/cheatsheets/OAuth2_Cheat_Sheet.html) covers state, redirect validation, token handling, and replay defenses.
