# fix38
- Full inventory snapshots now include a serialized fingerprint for every occupied slot.
- Visual grid accepts real AdminInventoryCell data rather than hard-coded empty cells.
- Clicking a populated slot selects it, copies its fingerprint for stale-state protection, and requests current slot details.
- Adds selected-item detail text and Inventory / Ender Chest controls.
- No fabricated item contents are rendered.
- The existing client bridge still needs to assign the received inventory JSON payload into `inventoryPayload` if its state model does not already expose raw JSON; this is deliberately documented instead of claiming an unverified binding.
