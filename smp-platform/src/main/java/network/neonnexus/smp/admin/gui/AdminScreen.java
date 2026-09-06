package network.neonnexus.smp.admin.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A simple rendering model usable by a future Geyser Form adapter as well as Bukkit inventory UI. */
public final class AdminScreen implements InventoryHolder {
    private final UUID viewerId;
    private final String key;
    private final Map<Integer, GuiAction> actions = new HashMap<>();
    private Inventory inventory;

    public AdminScreen(UUID viewerId, String key) { this.viewerId = viewerId; this.key = key; }
    public UUID viewerId() { return viewerId; }
    public String key() { return key; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
    public void button(int slot, GuiAction action) { actions.put(slot, action); }
    public void click(int rawSlot, InventoryClickEvent event) { GuiAction action = actions.get(rawSlot); if (action != null) action.run(event); }
    @FunctionalInterface public interface GuiAction { void run(InventoryClickEvent event); }
}
