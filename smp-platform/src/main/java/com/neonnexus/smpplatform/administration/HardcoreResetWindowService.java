package com.neonnexus.smpplatform.administration;
import com.google.gson.JsonObject;
import javax.sql.DataSource;
import java.sql.*;
import java.time.*;
public final class HardcoreResetWindowService {
 private final DataSource ds;
 private volatile Instant opensAt,closesAt;
 public HardcoreResetWindowService(DataSource ds){this.ds=ds;load();}
 private void load(){
  try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement("SELECT opens_at,closes_at FROM smp_hardcore_reset_window WHERE id=1");ResultSet r=ps.executeQuery()){
   if(r.next()){opensAt=r.getTimestamp(1).toInstant();closesAt=r.getTimestamp(2).toInstant();}
  }catch(Exception ignored){}
 }
 public synchronized void configure(Instant open,Instant close){
  if(open==null||close==null||!close.isAfter(open))throw new IllegalArgumentException("Reset close must be after open.");
  try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement(
   "INSERT INTO smp_hardcore_reset_window(id,opens_at,closes_at,updated_at) VALUES(1,?,?,NOW()) ON CONFLICT(id) DO UPDATE SET opens_at=EXCLUDED.opens_at,closes_at=EXCLUDED.closes_at,updated_at=NOW()")){
   ps.setTimestamp(1,Timestamp.from(open));ps.setTimestamp(2,Timestamp.from(close));ps.executeUpdate();
  }catch(SQLException e){throw new IllegalStateException("Unable to persist reset window.",e);}
  opensAt=open;closesAt=close;
 }
 public boolean isOpen(){Instant now=Instant.now();return opensAt!=null&&closesAt!=null&&!now.isBefore(opensAt)&&now.isBefore(closesAt);}
 public JsonObject status(){JsonObject o=new JsonObject();o.addProperty("open",isOpen());o.addProperty("opensAt",opensAt==null?"":opensAt.toString());o.addProperty("closesAt",closesAt==null?"":closesAt.toString());return o;}
}
