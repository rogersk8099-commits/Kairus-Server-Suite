package com.neonnexus.smpplatform.administration;
import com.google.gson.*;
import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;
public final class HardcoreAdminRepository {
 private final DataSource ds; public HardcoreAdminRepository(DataSource ds){this.ds=ds;}
 public JsonObject lookup(UUID minecraftUuid)throws SQLException{
  JsonObject out=new JsonObject();out.addProperty("minecraftUuid",minecraftUuid.toString());
  try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement(
   "SELECT state,season,updated_at FROM smp_hardcore_player_state WHERE minecraft_uuid=? ORDER BY updated_at DESC LIMIT 1")){
   ps.setObject(1,minecraftUuid);try(ResultSet r=ps.executeQuery()){if(r.next()){out.addProperty("state",r.getString(1));out.addProperty("season",r.getString(2));out.addProperty("updatedAt",String.valueOf(r.getObject(3)));}else out.addProperty("state","UNKNOWN");}
  }
  JsonArray deaths=new JsonArray();
  try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement(
   "SELECT cause,killer_uuid,x,y,z,survival_seconds,season,created_at FROM smp_hardcore_deaths WHERE minecraft_uuid=? ORDER BY created_at DESC LIMIT 20")){
   ps.setObject(1,minecraftUuid);try(ResultSet r=ps.executeQuery()){while(r.next()){JsonObject d=new JsonObject();d.addProperty("cause",r.getString(1));d.addProperty("killer",String.valueOf(r.getObject(2)));d.addProperty("x",r.getDouble(3));d.addProperty("y",r.getDouble(4));d.addProperty("z",r.getDouble(5));d.addProperty("survivalSeconds",r.getLong(6));d.addProperty("season",r.getString(7));d.addProperty("at",String.valueOf(r.getObject(8)));deaths.add(d);}}
  }out.add("deaths",deaths);return out;
 }
 public void setState(UUID target,String state,UUID actor,String reason)throws SQLException{
  if(!java.util.Set.of("ALIVE","DEAD","SPECTATING","RESET_ELIGIBLE","LOCKED").contains(state))
   throw new IllegalArgumentException("Invalid Hardcore state.");
  try(Connection c=ds.getConnection()){c.setAutoCommit(false);
   try(PreparedStatement ps=c.prepareStatement(
    "INSERT INTO smp_hardcore_player_state(minecraft_uuid,state,updated_at) VALUES (?,?,NOW()) ON CONFLICT (minecraft_uuid) DO UPDATE SET state=EXCLUDED.state,updated_at=NOW()")){
    ps.setObject(1,target);ps.setString(2,state);ps.executeUpdate();}
   try(PreparedStatement ps=c.prepareStatement(
    "INSERT INTO smp_hardcore_admin_audit(id,actor_uuid,target_uuid,action,reason,created_at) VALUES (?,?,?,?,?,NOW())")){
    ps.setObject(1,UUID.randomUUID());ps.setObject(2,actor);ps.setObject(3,target);ps.setString(4,"SET_STATE:"+state);ps.setString(5,reason);ps.executeUpdate();}
   c.commit();
  }
 }
}
