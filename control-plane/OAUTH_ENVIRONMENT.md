# Website OAuth Environment Contract

Configure these values only in Railway or another server-side secret manager. Do not commit them to `.env`, expose them through `VITE_` variables, or send them to browser JavaScript.

| Variable | Service | Notes |
| --- | --- | --- |
| `DATABASE_URL` | Control plane | Railway PostgreSQL URL. |
| `WEBSITE_URL` | Both | Exact HTTPS website origin with no trailing slash or path. |
| `API_URL` | Website | Exact HTTPS control-plane origin. |
| `WEBSITE_API_SECRET` | Both | Same independent random value, at least 32 characters; distinct from `ADMIN_API_KEY` and `PLUGIN_API_KEY`. |
| `SESSION_SECRET` | Both | Same independent random value, at least 32 characters; distinct from all API keys. |
| `DISCORD_OAUTH_CLIENT_ID` | Both | Discord application snowflake. |
| `DISCORD_OAUTH_CLIENT_SECRET` | Control plane only | Discord OAuth application secret. Never configure it on the website. |
| `DISCORD_OAUTH_REDIRECT_URI` | Both | Exact value `https://<website>/auth/discord/callback`. |

The control-plane migration runner must apply `migrations/004_website_auth.sql` before the OAuth-enabled website release starts accepting users.
