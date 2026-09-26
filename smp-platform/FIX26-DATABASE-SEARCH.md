# fix26
- Adds bounded, prepared PostgreSQL player search for username/partial name/UUID.
- Supports recent, Java and Bedrock filters and merges current online presence on the Paper thread.
- Search SQL runs only on SMPPlatform IO executors.
- Aligns client-manageable LuckPerms roles to member/trusted/moderator/admin/owner.
- Adds V007 indexes without modifying deployed migrations.
