# fix29
- Adds 30-second two-step fixed-price purchase confirmation.
- Locks/reserves listings before payment to prevent double purchase.
- Rejects self-purchase.
- Withdraws through Vault only after reservation.
- Database failure triggers Vault refund and reservation release.
- Successful purchase queues buyer item and seller proceeds durably.
- Buy remains server-authoritative and permission checked.
- Sell, bid and cancel remain locked for the next transaction pass.
