package com.neonnexus.smpplatform.administration;
import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;
public final class InventoryAuditRepository {
 private final DataSource ds; public InventoryAuditRepository(DataSource ds){this.ds=ds;}
 public void record(UUID actor,UUID target,String type,String action,Integer from,Integer to,String fingerprint)throws SQLException{
  try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement(
   "INSERT INTO smp_admin_inventory_audit(id,actor_minecraft_uuid,target_minecraft_uuid,inventory_type,action,source_slot,destination_slot,item_fingerprint,created_at) VALUES (?,?,?,?,?,?,?,?,NOW())")){
   ps.setObject(1,UUID.randomUUID());ps.setObject(2,actor);ps.setObject(3,target);ps.setString(4,type);ps.setString(5,action);
   if(from==null)ps.setNull(6,Types.INTEGER);else ps.setInt(6,from);if(to==null)ps.setNull(7,Types.INTEGER);else ps.setInt(7,to);
   ps.setString(8,fingerprint);ps.executeUpdate();
  }
 }
}
