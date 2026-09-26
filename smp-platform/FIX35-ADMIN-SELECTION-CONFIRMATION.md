# fix35
- Adds independent 30-second server-side confirmations for destructive player-admin actions.
- Clear Inventory now has Prepare/Confirm client controls and is revalidated server-side at confirmation.
- Client search-result selection is wired to the selected admin target where the existing result model exposes its UUID, while the UUID field remains as fallback.
- Profile refresh is directly available from the Players page.
- Inventory/Ender Chest remain read-only in this build; slot-level editing requires a transactional UI protocol and is not falsely exposed as complete.
