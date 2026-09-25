package com.neonnexus.smpplatform.auction;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

public final class AuctionService {
    private static final String CURRENCY = "KAIRU_POINTS";
    private final JavaPlugin plugin;
    private final DataSource dataSource;
    private final Executor io;

    public AuctionService(JavaPlugin plugin, DataSource dataSource, Executor io) {
        this.plugin = plugin;
        this.dataSource = dataSource;
        this.io = io;
    }

    public void execute(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("browse")) {
            browse(player);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell", "list" -> list(player, args);
            case "bid" -> bid(player, args);
            case "cancel" -> cancel(player, args);
            default -> help(player);
        }
    }

    private void help(Player p) {
        p.sendMessage("§d/auction browse");
        if (p.hasPermission("smpplatform.auction.create")) p.sendMessage("§d/auction sell <starting-price> [minutes]");
        if (p.hasPermission("smpplatform.auction.bid")) p.sendMessage("§d/auction bid <listing-id> <amount>");
        if (p.hasPermission("smpplatform.auction.create")) p.sendMessage("§d/auction cancel <listing-id>");
    }

    private void list(Player p, String[] args) {
        if (!p.hasPermission("smpplatform.auction.create")) { p.sendMessage("§cYou do not have permission to create auction listings."); return; }
        if (args.length < 2) { p.sendMessage("§cUsage: /auction sell <starting-price> [minutes]"); return; }
        long price; int minutes = 1440;
        try { price = Long.parseLong(args[1]); if (args.length > 2) minutes = Integer.parseInt(args[2]); }
        catch (NumberFormatException e) { p.sendMessage("§cPrice and duration must be numbers."); return; }
        if (price <= 0 || minutes < 5 || minutes > 10080) { p.sendMessage("§cPrice must be positive and duration must be 5-10080 minutes."); return; }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) { p.sendMessage("§cHold the item you want to auction in your main hand."); return; }
        ItemStack escrow = hand.clone();
        p.getInventory().setItemInMainHand(null);
        UUID id = UUID.randomUUID();
        io.execute(() -> {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO smp_auction_listings (id,seller_id,currency_id,item_data,quantity,starting_bid,current_bid,created_at,expires_at,status,version) VALUES (?,?,?,?,?,?,?,?,?,?,0)")) {
                    Instant now = Instant.now();
                    ps.setObject(1,id); ps.setObject(2,p.getUniqueId()); ps.setString(3,CURRENCY); ps.setBytes(4,escrow.serializeAsBytes());
                    ps.setInt(5,escrow.getAmount()); ps.setLong(6,price); ps.setLong(7,price); ps.setTimestamp(8,Timestamp.from(now)); ps.setTimestamp(9,Timestamp.from(now.plusSeconds(minutes*60L))); ps.setString(10,"OPEN"); ps.executeUpdate();
                }
                c.commit();
                Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage("§aAuction listed: §f"+id+" §7starting at §e"+price+" "+CURRENCY+"."));
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Map<Integer, ItemStack> left = p.getInventory().addItem(escrow);
                    if (!left.isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(), left.values().iterator().next());
                    p.sendMessage("§cAuction listing failed; your item was returned.");
                });
            }
        });
    }

    private void browse(Player p) {
        if (!p.hasPermission("smpplatform.auction.browse")) { p.sendMessage("§cYou do not have permission to browse auctions."); return; }
        io.execute(() -> {
            List<String> rows = new ArrayList<>();
            try (Connection c=dataSource.getConnection();
                 PreparedStatement ps=c.prepareStatement("SELECT id,seller_id,current_bid,expires_at FROM smp_auction_listings WHERE status='OPEN' AND expires_at>? ORDER BY created_at DESC LIMIT 20")) {
                ps.setTimestamp(1,Timestamp.from(Instant.now()));
                try(ResultSet r=ps.executeQuery()) {
                    while(r.next()) rows.add(r.getObject(1)+" | seller="+r.getObject(2)+" | bid="+r.getLong(3)+" "+CURRENCY+" | expires="+r.getTimestamp(4).toInstant());
                }
            } catch(Exception e) { rows.add("Auction database unavailable."); }
            Bukkit.getScheduler().runTask(plugin, () -> { p.sendMessage("§d--- Player Auctions ---"); rows.forEach(p::sendMessage); });
        });
    }

    private void bid(Player p, String[] args) {
        if (!p.hasPermission("smpplatform.auction.bid")) { p.sendMessage("§cYou do not have permission to bid."); return; }
        if (args.length < 3) { p.sendMessage("§cUsage: /auction bid <listing-id> <amount>"); return; }
        UUID listing; long amount;
        try { listing=UUID.fromString(args[1]); amount=Long.parseLong(args[2]); }
        catch(Exception e) { p.sendMessage("§cInvalid listing ID or amount."); return; }
        if (amount <= 0) { p.sendMessage("§cBid must be positive."); return; }
        io.execute(() -> {
            String result;
            try (Connection c=dataSource.getConnection()) {
                c.setAutoCommit(false);
                UUID seller, previousBidder=null; long previousBid=0, current;
                try(PreparedStatement ps=c.prepareStatement("SELECT seller_id,current_bid,highest_bidder,status,expires_at FROM smp_auction_listings WHERE id=? FOR UPDATE")) {
                    ps.setObject(1,listing);
                    try(ResultSet r=ps.executeQuery()) {
                        if(!r.next()) throw new IllegalArgumentException("Listing not found.");
                        seller=(UUID)r.getObject(1); current=r.getLong(2); previousBidder=(UUID)r.getObject(4);
                        if(!"OPEN".equals(r.getString(3)) || r.getTimestamp(5).toInstant().isBefore(Instant.now())) throw new IllegalArgumentException("Listing is closed.");
                        if(seller.equals(p.getUniqueId())) throw new IllegalArgumentException("You cannot bid on your own listing.");
                        if(amount <= current) throw new IllegalArgumentException("Bid must exceed the current bid.");
                    }
                }
                debit(c,p.getUniqueId(),amount);
                if(previousBidder!=null && previousBid>0) credit(c,previousBidder,previousBid);
                try(PreparedStatement ps=c.prepareStatement("UPDATE smp_auction_listings SET current_bid=?,highest_bidder=?,version=version+1 WHERE id=?")) {
                    ps.setLong(1,amount); ps.setObject(2,p.getUniqueId()); ps.setObject(3,listing); ps.executeUpdate();
                }
                try(PreparedStatement ps=c.prepareStatement("INSERT INTO smp_auction_bids(id,listing_id,bidder_id,amount,created_at) VALUES (?,?,?,?,?)")) {
                    ps.setObject(1,UUID.randomUUID()); ps.setObject(2,listing); ps.setObject(3,p.getUniqueId()); ps.setLong(4,amount); ps.setTimestamp(5,Timestamp.from(Instant.now())); ps.executeUpdate();
                }
                c.commit(); result="§aBid accepted: §e"+amount+" "+CURRENCY+".";
            } catch(Exception e) { result="§cBid failed: "+e.getMessage(); }
            String message=result; Bukkit.getScheduler().runTask(plugin,()->p.sendMessage(message));
        });
    }

    private void cancel(Player p, String[] args) {
        if (!p.hasPermission("smpplatform.auction.create")) { p.sendMessage("§cYou do not have permission to cancel your listing."); return; }
        if(args.length<2){p.sendMessage("§cUsage: /auction cancel <listing-id>");return;}
        UUID id; try{id=UUID.fromString(args[1]);}catch(Exception e){p.sendMessage("§cInvalid listing ID.");return;}
        io.execute(()->{
            ItemStack item=null; String message;
            try(Connection c=dataSource.getConnection()){
                c.setAutoCommit(false);
                try(PreparedStatement ps=c.prepareStatement("SELECT seller_id,item_data,status FROM smp_auction_listings WHERE id=? FOR UPDATE")){
                    ps.setObject(1,id); try(ResultSet r=ps.executeQuery()){
                        if(!r.next()) throw new IllegalArgumentException("Listing not found.");
                        if(!p.getUniqueId().equals(r.getObject(1))) throw new IllegalArgumentException("You do not own this listing.");
                        if(!"OPEN".equals(r.getString(3))) throw new IllegalArgumentException("Listing is no longer open.");
                        item=ItemStack.deserializeBytes(r.getBytes(2));
                    }
                }
                try(PreparedStatement ps=c.prepareStatement("UPDATE smp_auction_listings SET status='CANCELLED',version=version+1 WHERE id=?")){ps.setObject(1,id);ps.executeUpdate();}
                c.commit(); message="§aAuction cancelled.";
            }catch(Exception e){message="§cCancel failed: "+e.getMessage();}
            ItemStack refund=item; Bukkit.getScheduler().runTask(plugin,()->{p.sendMessage(message);if(refund!=null){Map<Integer,ItemStack> left=p.getInventory().addItem(refund);if(!left.isEmpty())p.getWorld().dropItemNaturally(p.getLocation(),left.values().iterator().next());}});
        });
    }

    private void debit(Connection c, UUID player, long amount) throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("UPDATE smp_point_accounts SET balance=balance-?,version=version+1,updated_at=? WHERE owner_type='PLAYER' AND owner_id=? AND currency_id=? AND balance>=?")){
            ps.setLong(1,amount);ps.setTimestamp(2,Timestamp.from(Instant.now()));ps.setObject(3,player);ps.setString(4,CURRENCY);ps.setLong(5,amount);
            if(ps.executeUpdate()!=1) throw new IllegalArgumentException("Insufficient "+CURRENCY+".");
        }
    }
    private void credit(Connection c, UUID player, long amount) throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO smp_point_accounts(account_id,owner_type,owner_id,currency_id,balance,version,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(owner_type,owner_id,currency_id) DO UPDATE SET balance=smp_point_accounts.balance+EXCLUDED.balance,version=smp_point_accounts.version+1,updated_at=EXCLUDED.updated_at")){
            Instant now=Instant.now();ps.setObject(1,UUID.randomUUID());ps.setString(2,"PLAYER");ps.setObject(3,player);ps.setString(4,CURRENCY);ps.setLong(5,amount);ps.setLong(6,0);ps.setTimestamp(7,Timestamp.from(now));ps.setTimestamp(8,Timestamp.from(now));ps.executeUpdate();
        }
    }
}
