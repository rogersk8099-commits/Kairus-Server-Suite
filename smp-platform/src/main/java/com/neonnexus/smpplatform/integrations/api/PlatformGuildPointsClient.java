package com.neonnexus.smpplatform.integrations.api;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public final class PlatformGuildPointsClient {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final URI endpoint; private final String token,serverId; private final Gson gson=new Gson();
    public PlatformGuildPointsClient(String baseUrl,String token,String serverId){
        endpoint=URI.create(baseUrl.replaceAll("/$","")+"/api/plugin/guild-points");this.token=token;this.serverId=serverId;
    }
    public JsonObject request(String operation,JsonObject payload){
        JsonObject body=new JsonObject();body.addProperty("operation",operation);body.add("payload",payload==null?new JsonObject():payload);
        try{
            HttpRequest req=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
              .header("Content-Type","application/json").header("Authorization","Bearer "+token).header("X-Kairu-Server-Id",serverId)
              .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body),StandardCharsets.UTF_8)).build();
            HttpResponse<String> res=client.send(req,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonObject root=JsonParser.parseString(res.body()).getAsJsonObject();
            if(res.statusCode()/100!=2){
                String msg=root.has("error")&&root.get("error").isJsonObject()?root.getAsJsonObject("error").get("message").getAsString():"Platform service request failed.";
                throw new IllegalStateException(msg);
            }
            return root.getAsJsonObject("result");
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Platform service request interrupted.");}
        catch(Exception e){if(e instanceof IllegalStateException x)throw x;throw new IllegalStateException("Control Plane guild/points service unavailable: "+e.getMessage(),e);}
    }
    private static JsonObject player(UUID id){JsonObject p=new JsonObject();p.addProperty("minecraftUuid",id.toString());return p;}
    public JsonObject view(UUID id,String operation){return request(operation,player(id));}
    public JsonObject pointsView(UUID id,String operation,String currency){JsonObject p=player(id);p.addProperty("currency",currency);return request(operation,p);}
    public JsonObject createGuild(UUID id,String name,String tag,String description){JsonObject p=player(id);p.addProperty("name",name);p.addProperty("tag",tag);p.addProperty("description",description);return request("guild-create",p);}
    public JsonObject guildAction(UUID id,String action,UUID target){JsonObject p=player(id);p.addProperty("action",action);if(target!=null)p.addProperty("targetMinecraftUuid",target.toString());return request("guild-action",p);}
    public JsonObject pointHistory(UUID id,String currency,int limit){JsonObject p=player(id);p.addProperty("currency",currency);p.addProperty("limit",limit);return request("points-history",p);}
    public JsonObject pointTop(UUID id,String currency,int limit){JsonObject p=player(id);p.addProperty("currency",currency);p.addProperty("limit",limit);return request("points-top",p);}
    public JsonObject adjust(UUID actor,UUID target,String mode,String currency,long amount,String reason){JsonObject p=player(actor);p.addProperty("targetMinecraftUuid",target.toString());p.addProperty("mode",mode);p.addProperty("currency",currency);p.addProperty("amount",amount);p.addProperty("reason",reason);return request("points-adjust",p);}
}
