package gg.neonnexus.smpplatform.integrations.api;

/** Stable API routes; no credentials are persisted in this module. */
public enum CentralApiOperation {
    PLAYER_SYNC("/v1/minecraft/players/sync"),
    WORLD_SYNC("/v1/minecraft/worlds/sync"),
    GUILD_SYNC("/v1/minecraft/guilds/sync"),
    POINTS_SYNC("/v1/minecraft/points/sync"),
    EVENT("/v1/minecraft/events"),
    ACHIEVEMENT("/v1/minecraft/achievements"),
    NOTIFICATION("/v1/minecraft/notifications"),
    MEMBERSHIP("/v1/minecraft/memberships"),
    ACCOUNT_LINK("/v1/minecraft/account-links"),
    STATISTICS("/v1/minecraft/statistics"),
    SERVER_STATUS("/v1/minecraft/server-status"),
    WORLD_CHAT("/v1/minecraft/chat"),
    ANNOUNCEMENT("/v1/minecraft/announcements");

    private final String path;
    CentralApiOperation(String path) { this.path = path; }
    public String path() { return path; }
}
