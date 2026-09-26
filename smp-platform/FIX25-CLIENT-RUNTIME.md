# fix25 client runtime
- Wires client Setup status/preview/apply/validate to SMPPlatform.
- Adds permission-checked online player Search responses for the client.
- Recent/offline search deliberately fails transparently until the PostgreSQL directory adapter is wired.
- Auction configuration/schema remain present, but purchase/listing runtime is not falsely reported as complete.
