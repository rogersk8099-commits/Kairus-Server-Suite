# fix27 auction runtime
Adds real PostgreSQL auction browsing to the client, bounded search, durable cancellation primitives, Vault discovery, and Bukkit item-component serialization.
Mutation buttons deliberately remain fail-closed until Minecraft-account identity resolution and the atomic Vault + DB transaction workflow are verified. They never remove an item or charge money while incomplete.
