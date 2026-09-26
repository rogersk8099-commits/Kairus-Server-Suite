package com.neonnexus.smpplatform.auction;

import com.google.gson.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auction persistence is owned by the Control Plane. SMPPlatform never opens a
 * PostgreSQL connection for auction data; it sends authenticated requests to
 * /api/plugin/auction and treats item payloads as opaque base64.
 */
public final class AuctionService {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final URI endpoint;
    private final String token;
    private final String serverId;
    private final Gson gson=new Gson();
    private final Map<UUID,ClaimBundle> claims=new ConcurrentHashMap<>();

    public AuctionService(String baseUrl,String token,String serverId){
        this.endpoint=URI.create(Objects.requireNonNull(baseUrl).replaceAll("/$","")+"/api/plugin/auction");
        this.token=Objects.requireNonNull(token); this.serverId=Objects.requireNonNull(serverId);
    }
    private JsonObject rpc(String operation,JsonObject payload){
        JsonObject body=new JsonObject();body.addProperty("operation",operation);body.add("payload",payload==null?new JsonObject():payload);
        try{
            HttpRequest req=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                .header("Content-Type","application/json").header("Authorization","Bearer "+token)
                .header("X-Kairu-Server-Id",serverId)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body),StandardCharsets.UTF_8)).build();
            HttpResponse<String> res=client.send(req,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonObject root=JsonParser.parseString(res.body()).getAsJsonObject();
            if(res.statusCode()/100!=2){
                String message=root.has("error")&&root.get("error").isJsonObject()&&root.getAsJsonObject("error").has("message")
                    ?root.getAsJsonObject("error").get("message").getAsString():"Control Plane auction request failed ("+res.statusCode()+").";
                throw new IllegalStateException(message);
            }
            return root.getAsJsonObject("auction");
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Control Plane auction request interrupted.",e);}
        catch(Exception e){if(e instanceof IllegalStateException ise)throw ise;throw new IllegalStateException("Control Plane auction service unavailable: "+e.getMessage(),e);}
    }
    private static JsonObject ids(UUID minecraftUuid){JsonObject p=new JsonObject();p.addProperty("minecraftUuid",minecraftUuid.toString());return p;}

    public JsonObject browse(String query,int requestedLimit){
        JsonObject p=new JsonObject();p.addProperty("query",query==null?"":query);p.addProperty("limit",Math.max(1,Math.min(requestedLimit,100)));return rpc("browse",p);
    }
    public UUID createFixed(UUID sellerMinecraftUuid,ItemStack stack,double price,Instant expiresAt){
        JsonObject p=ids(sellerMinecraftUuid);p.addProperty("itemPayload",Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
        p.addProperty("itemName",stack.getItemMeta()!=null&&stack.getItemMeta().hasDisplayName()?stack.getItemMeta().getDisplayName():stack.getType().getKey().asString());
        p.addProperty("quantity",stack.getAmount());p.addProperty("price",price);p.addProperty("expiresAt",expiresAt.toString());
        return UUID.fromString(rpc("create-fixed",p).get("id").getAsString());
    }
    public JsonObject cancel(UUID listingId,UUID sellerMinecraftUuid){
        JsonObject p=ids(sellerMinecraftUuid);p.addProperty("listingId",listingId.toString());return rpc("cancel",p);
    }
    public record PurchaseReservation(UUID listingId,UUID sellerAccountId,byte[] itemPayload,double price){}
    public PurchaseReservation reserveFixedPurchase(UUID listingId,UUID buyerMinecraftUuid){
        JsonObject p=ids(buyerMinecraftUuid);p.addProperty("listingId",listingId.toString());JsonObject r=rpc("reserve-purchase",p);
        return new PurchaseReservation(listingId,UUID.fromString(r.get("sellerMinecraftUuid").getAsString()),Base64.getDecoder().decode(r.get("itemPayload").getAsString()),r.get("price").getAsDouble());
    }
    public void releaseReservation(UUID listingId){JsonObject p=new JsonObject();p.addProperty("listingId",listingId.toString());rpc("release-purchase",p);}
    public void completeFixedPurchase(PurchaseReservation r,UUID buyerMinecraftUuid){JsonObject p=ids(buyerMinecraftUuid);p.addProperty("listingId",r.listingId().toString());rpc("complete-purchase",p);}
    public JsonObject myListings(UUID minecraftUuid,int requestedLimit){JsonObject p=ids(minecraftUuid);p.addProperty("limit",requestedLimit);return rpc("mine",p);}

    public record BidResult(UUID previousBidder,double previousBid){}
    public BidResult placeBid(UUID listingId,UUID bidder,double amount){
        JsonObject p=ids(bidder);p.addProperty("listingId",listingId.toString());p.addProperty("amount",amount);JsonObject r=rpc("place-bid",p);
        UUID previous=r.has("previousBidder")&&!r.get("previousBidder").isJsonNull()?UUID.fromString(r.get("previousBidder").getAsString()):null;
        return new BidResult(previous,r.has("previousBid")?r.get("previousBid").getAsDouble():0);
    }
    public void revertBid(UUID listingId,UUID bidder,double amount,UUID previousBidder,double previousBid){
        JsonObject p=ids(bidder);p.addProperty("listingId",listingId.toString());p.addProperty("amount",amount);
        if(previousBidder!=null)p.addProperty("previousBidder",previousBidder.toString());p.addProperty("previousBid",previousBid);rpc("revert-bid",p);
    }

    private ClaimBundle claim(UUID minecraftUuid){
        return claims.computeIfAbsent(minecraftUuid,id->{
            JsonObject r=rpc("claim",ids(id));List<ItemStack> items=new ArrayList<>();
            if(r.has("items"))for(JsonElement e:r.getAsJsonArray("items"))items.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(e.getAsString())));
            return new ClaimBundle(items,r.has("money")?r.get("money").getAsDouble():0);
        });
    }
    public List<ItemStack> claimPendingItems(UUID minecraftUuid){return new ArrayList<>(claim(minecraftUuid).items);}
    public double claimPendingMoney(UUID minecraftUuid){return claim(minecraftUuid).money;}
    public void finishClaims(UUID minecraftUuid,boolean delivered){finish(minecraftUuid,delivered);}
    public void finishMoneyClaims(UUID minecraftUuid,boolean delivered){if(claims.containsKey(minecraftUuid))finish(minecraftUuid,delivered);}
    private void finish(UUID minecraftUuid,boolean delivered){JsonObject p=ids(minecraftUuid);p.addProperty("delivered",delivered);rpc("finish-claim",p);claims.remove(minecraftUuid);}
    private record ClaimBundle(List<ItemStack> items,double money){}

    public JsonObject databaseStatus(){
        JsonObject r=rpc("status",new JsonObject());
        // Preserve old client keys while making the ownership explicit.
        r.addProperty("platformApiConnected",true);
        return r;
    }
    public static Economy economy(){
        RegisteredServiceProvider<Economy> registration=Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration==null?null:registration.getProvider();
    }
}
