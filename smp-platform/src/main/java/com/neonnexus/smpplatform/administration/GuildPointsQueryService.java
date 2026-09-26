package com.neonnexus.smpplatform.administration;
import com.google.gson.*;
import javax.sql.DataSource;
import java.sql.*;
public final class GuildPointsQueryService {
 private final DataSource ds; public GuildPointsQueryService(DataSource ds){this.ds=ds;}
 public JsonObject pointHistory(String target,String currency,int limit)throws SQLException{
  int n=Math.max(1,Math.min(limit,100));JsonArray rows=new JsonArray();
  String sql="SELECT currency,amount,balance_before,balance_after,reason,source,created_at FROM smp_point_transactions WHERE (player_id::text=? OR guild_id::text=?) AND (?='' OR currency=?) ORDER BY created_at DESC LIMIT ?";
  try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement(sql)){
   ps.setString(1,target);ps.setString(2,target);ps.setString(3,currency);ps.setString(4,currency);ps.setInt(5,n);
   try(ResultSet r=ps.executeQuery()){while(r.next()){JsonObject x=new JsonObject();x.addProperty("currency",r.getString(1));x.addProperty("amount",r.getLong(2));x.addProperty("before",r.getLong(3));x.addProperty("after",r.getLong(4));x.addProperty("reason",r.getString(5));x.addProperty("source",r.getString(6));x.addProperty("at",String.valueOf(r.getObject(7)));rows.add(x);}}
  }JsonObject out=new JsonObject();out.add("pointHistory",rows);return out;
 }
 public JsonObject leaderboard(String currency,int limit)throws SQLException{
  int n=Math.max(1,Math.min(limit,100));JsonArray rows=new JsonArray();
  String sql="SELECT player_id,SUM(amount) AS balance FROM smp_point_transactions WHERE currency=? GROUP BY player_id ORDER BY balance DESC LIMIT ?";
  try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,currency);ps.setInt(2,n);
   try(ResultSet r=ps.executeQuery()){while(r.next()){JsonObject x=new JsonObject();x.addProperty("playerId",String.valueOf(r.getObject(1)));x.addProperty("balance",r.getLong(2));rows.add(x);}}
  }JsonObject out=new JsonObject();out.add("pointLeaderboard",rows);return out;
 }
 public JsonObject guildSearch(String query,int limit)throws SQLException{
  int n=Math.max(1,Math.min(limit,100));JsonArray rows=new JsonArray();
  String sql="SELECT id,name,tag,owner_player_id,points,level,visibility FROM smp_guilds WHERE lower(name) LIKE lower(?) OR lower(tag) LIKE lower(?) ORDER BY name LIMIT ?";
  try(Connection c=ds.getConnection();PreparedStatement ps=c.prepareStatement(sql)){String q="%"+query+"%";ps.setString(1,q);ps.setString(2,q);ps.setInt(3,n);
   try(ResultSet r=ps.executeQuery()){while(r.next()){JsonObject x=new JsonObject();x.addProperty("id",String.valueOf(r.getObject(1)));x.addProperty("name",r.getString(2));x.addProperty("tag",r.getString(3));x.addProperty("owner",String.valueOf(r.getObject(4)));x.addProperty("points",r.getLong(5));x.addProperty("level",r.getInt(6));x.addProperty("visibility",r.getString(7));rows.add(x);}}
  }JsonObject out=new JsonObject();out.add("guildSearch",rows);return out;
 }
}
