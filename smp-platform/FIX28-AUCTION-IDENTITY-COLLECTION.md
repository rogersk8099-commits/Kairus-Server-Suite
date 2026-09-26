# fix28
- Resolves linked Minecraft account IDs for auction ownership.
- My Listings now queries the authenticated linked account.
- Collect now uses durable PENDING -> CLAIMING -> DELIVERED delivery state.
- Inventory delivery occurs only on the Paper thread; database work remains async.
- Sell/buy/bid/cancel remain fail-closed until confirmation and Vault atomicity are complete.
