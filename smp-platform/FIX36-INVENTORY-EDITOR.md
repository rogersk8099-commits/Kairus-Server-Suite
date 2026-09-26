# fix36
Adds server-authoritative slot inspection, remove and move/swap operations for player inventory and Ender Chest. Every edit validates a serialized item fingerprint to reject stale UI changes and writes an asynchronous audit record. Rich clickable slot-grid binding remains a UI polish task; the protocol is now in place.
