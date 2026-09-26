package com.neonnexus.smpplatform.administration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;

public final class ModerationRepository {
    private final DataSource dataSource;
    public ModerationRepository(DataSource dataSource){this.dataSource=dataSource;}

    public void record(UUID target, UUID actor, String action, String reason, Instant expiresAt) throws SQLException {
        try(Connection c=dataSource.getConnection(); PreparedStatement ps=c.prepareStatement("""
            INSERT INTO smp_moderation_records(id,target_minecraft_uuid,actor_minecraft_uuid,action,reason,expires_at,created_at)
            VALUES (?,?,?,?,?,?,NOW())""")){
            ps.setObject(1,UUID.randomUUID());ps.setObject(2,target);ps.setObject(3,actor);ps.setString(4,action);
            ps.setString(5,reason); if(expiresAt==null)ps.setNull(6,Types.TIMESTAMP_WITH_TIMEZONE);else ps.setTimestamp(6,Timestamp.from(expiresAt));
            ps.executeUpdate();
        }
    }

    public JsonObject history(UUID target,int requestedLimit){
        JsonObject out=new JsonObject();JsonArray rows=new JsonArray();int limit=Math.max(1,Math.min(requestedLimit,100));
        try(Connection c=dataSource.getConnection();PreparedStatement ps=c.prepareStatement("""
            SELECT actor_minecraft_uuid,action,reason,expires_at,created_at FROM smp_moderation_records
             WHERE target_minecraft_uuid=? ORDER BY created_at DESC LIMIT ?""")){
            ps.setObject(1,target);ps.setInt(2,limit);
            try(ResultSet rs=ps.executeQuery()){while(rs.next()){
                JsonObject r=new JsonObject();r.addProperty("actor",rs.getObject("actor_minecraft_uuid").toString());
                r.addProperty("action",rs.getString("action"));r.addProperty("reason",rs.getString("reason"));
                r.addProperty("createdAt",rs.getTimestamp("created_at").toInstant().toString());
                if(rs.getTimestamp("expires_at")!=null)r.addProperty("expiresAt",rs.getTimestamp("expires_at").toInstant().toString());
                rows.add(r);
            }}
        }catch(Exception e){out.addProperty("error","Moderation history is temporarily unavailable.");}
        out.add("moderationHistory",rows);return out;
    }
}
