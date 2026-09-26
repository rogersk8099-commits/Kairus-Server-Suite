package com.neonnexus.smpplatform.search;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.UUID;

/** Prepared, bounded PostgreSQL search used by the client/admin surfaces. Never call from the Paper thread. */
public final class PlayerSearchService {
    private final DataSource dataSource;
    public PlayerSearchService(DataSource dataSource) { this.dataSource = dataSource; }

    public JsonObject search(String rawQuery, String rawFilter, int requestedLimit) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        String filter = rawFilter == null ? "" : rawFilter.toLowerCase(Locale.ROOT);
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        JsonObject result = new JsonObject();
        JsonArray rows = new JsonArray();
        String like = "%" + query.toLowerCase(Locale.ROOT) + "%";
        boolean uuid = false;
        try { UUID.fromString(query); uuid = true; } catch (RuntimeException ignored) {}

        StringBuilder sql = new StringBuilder("""
            SELECT p.id, p.platform_user_id, p.username, p.last_seen_at,
                   a.minecraft_uuid, a.edition, a.last_known_name, a.last_seen_at AS account_last_seen
              FROM smp_players p
              LEFT JOIN smp_minecraft_accounts a ON a.player_id = p.id
             WHERE (? = '' OR lower(p.username) LIKE ? OR lower(COALESCE(a.last_known_name,'')) LIKE ?
                    OR (? AND a.minecraft_uuid::text = ?))
            """);
        if (filter.equals("recent")) sql.append(" AND COALESCE(a.last_seen_at,p.last_seen_at) >= NOW() - INTERVAL '30 days'");
        if (filter.equals("java")) sql.append(" AND a.edition = 'JAVA'");
        if (filter.equals("bedrock")) sql.append(" AND a.edition = 'BEDROCK'");
        sql.append(" ORDER BY COALESCE(a.last_seen_at,p.last_seen_at) DESC NULLS LAST, lower(p.username) LIMIT ?");

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, query);
            statement.setString(2, like);
            statement.setString(3, like);
            statement.setBoolean(4, uuid);
            statement.setString(5, uuid ? query : "");
            statement.setInt(6, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    JsonObject row = new JsonObject();
                    UUID minecraft = (UUID) rs.getObject("minecraft_uuid");
                    row.addProperty("id", minecraft == null ? rs.getObject("id").toString() : minecraft.toString());
                    row.addProperty("name", rs.getString("last_known_name") == null ? rs.getString("username") : rs.getString("last_known_name"));
                    row.addProperty("platform", rs.getString("edition") == null ? "UNKNOWN" : rs.getString("edition"));
                    UUID platform = (UUID) rs.getObject("platform_user_id");
                    if (platform != null) row.addProperty("platformUserId", platform.toString());
                    row.addProperty("online", false);
                    if (rs.getTimestamp("account_last_seen") != null) row.addProperty("lastSeen", rs.getTimestamp("account_last_seen").toInstant().toString());
                    rows.add(row);
                }
            }
        } catch (Exception exception) {
            result.addProperty("error", "Player search is temporarily unavailable.");
        }
        result.add("searchResults", rows);
        return result;
    }
}
