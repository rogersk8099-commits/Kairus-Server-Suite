# fix42
Adds bounded read-only query endpoints for guild search, point transaction history and currency-specific point leaderboards, plus client controls for currency selection and guild search.

Schema caution: these SQL statements are intentionally isolated in GuildPointsQueryService because current migrations must be checked in staging for exact table/column names before production. Queries are prepared and capped at 100 rows.
