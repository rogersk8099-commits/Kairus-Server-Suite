# fix30
- Two-step 30-second held-item sale confirmation with item fingerprint.
- Item removed only at confirmation; DB failure returns it safely.
- Seller proceeds are collected through Vault with failed deposits returned to PENDING.
- Listing cancellation queues the item for durable collection.
- Bid remains fail-closed pending escrow/refund implementation.
