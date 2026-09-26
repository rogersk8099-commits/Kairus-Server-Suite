# fix37
Introduces a reusable 9-column Compose AdminInventoryGrid with selectable slots and a visual inventory/Ender Chest mode foundation.
Server-side destructive slot removal can now be prepared for confirmation while retaining the inspected item fingerprint, so confirmation cannot silently remove a changed item.
The grid component is intentionally separated from the server protocol. The existing client state transport still needs JSON inventory-array decoding into AdminInventoryCell objects before real item contents can populate the visual grid; this build does not falsely render fabricated inventory contents.
