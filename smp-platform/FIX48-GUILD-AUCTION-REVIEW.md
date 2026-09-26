# fix48 guild / auction correction

## Guild client
The Guild page had accidentally accumulated Guild/Points admin, World admin, Player admin,
inventory and moderation controls from earlier heuristic UI injections. The player Guild page
is now limited to guild summary, invitations, leaderboard/directory, create/join flow, members,
and leader/officer invite controls. Admin controls remain server-authoritative and are not shown
on the player Guild page.

## Auction PostgreSQL
AuctionService already receives the same PostgreSQL DataSource created by DatabaseService and
all listing/bid/delivery operations use JDBC. The missing piece was visibility/verification.
`auction-db-status` now verifies the live connection and required PostgreSQL tables
`smp_auction_listings`, `smp_auction_deliveries`, and `smp_auction_bids`. The Auction client page
shows Connected/Disconnected and Ready/Not ready instead of silently appearing disconnected.

Existing auction migrations found in this source tree:
- V006__auction_and_search.sql
- V008__auction_runtime_columns.sql
- V009__auction_delivery_claiming.sql
- V010__auction_purchase_delivery.sql
- V011__auction_bidding.sql
