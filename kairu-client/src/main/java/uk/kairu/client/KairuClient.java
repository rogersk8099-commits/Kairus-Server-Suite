package uk.kairu.client;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.*;

public final class KairuClient implements ClientModInitializer {
    static KeyMapping openKey;
    static JsonObject state, inventory;
    static String message="",pending;
    static long requestedAt,lastReply;
    static boolean opening;
    static int revision;
    private static final Minecraft MC=Minecraft.getInstance();
    @Override public void onInitializeClient() {
        openKey=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.kairu_admin.open",InputConstants.Type.KEYSYM,InputConstants.KEY_K,
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("kairu_admin","menu"))));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,context)->dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("smpadmin").executes(c->{MC.execute(KairuClient::open);return 1;})));
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->reset());
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            while(openKey.consumeClick()) if(client.gui.screen()==null)open();
            if(!connected()) {
                if(client.gui.screen() instanceof AdminScreen) client.gui.setScreen(null);
                reset();return;
            }
            long now=System.currentTimeMillis();
            if(pending!=null&&now-requestedAt>6000) {
                pending=null;state=null;inventory=null;opening=false;revision++;
                if(client.gui.screen() instanceof AdminScreen)client.gui.setScreen(null);
                notice("No reply from the Kairu server plugin. The menu was not opened or has been closed.");
            }
            // The menu is command-driven. Polling status while a form is open rebuilds the screen
            // and interrupts text entry; connection events above already close it on disconnect.
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((component,overlay)->{
            String text=component.getString();
            if(overlay||!text.startsWith("KAIRU_ADMIN_V2:"))return true;
            if(text.length()>262144)return false;
            try {
                JsonObject response=JsonParser.parseString(text.substring("KAIRU_ADMIN_V2:".length())).getAsJsonObject();
                if(pending==null||!pending.equals(response.get("id").getAsString())||!connected())return false;
                boolean authorized=response.get("authorized").getAsBoolean();
                String result=response.get("message").getAsString();
                if(authorized) {
                    Objects.requireNonNull(response.getAsJsonArray("permissions"));Objects.requireNonNull(response.getAsJsonArray("players"));
                    Objects.requireNonNull(response.getAsJsonArray("flags"));response.get("admin").getAsBoolean();response.get("plots").getAsBoolean();
                }
                pending=null;lastReply=System.currentTimeMillis();message=result;
                if(!authorized) {
                    state=null;inventory=null;opening=false;
                    if(MC.gui.screen() instanceof AdminScreen)MC.gui.setScreen(null);
                    notice(result);revision++;return false;
                }
                state=response;
                if(!can("inventory"))inventory=null;
                else if(response.has("inventory"))inventory=response.getAsJsonObject("inventory");
                revision++;
                if(opening) {opening=false;MC.gui.setScreen(new AdminScreen());}
            } catch(RuntimeException ignored) { /* A malformed response cannot grant access; timeout closes the menu. */ }
            return false;
        });
    }
    static boolean connected(){return MC.player!=null&&MC.level!=null&&MC.getConnection()!=null&&!MC.hasSingleplayerServer();}
    static void open() {
        if(!connected()){notice("Join your SMP server to open this menu.");return;}
        if(pending!=null)return;
        state=null;inventory=null;opening=true;notice("Checking SMP access...");request("status");
    }
    static void reset(){state=null;inventory=null;pending=null;opening=false;lastReply=0;revision++;}
    static void notice(String s){message=s;if(MC.player!=null)MC.gui.hud.setOverlayMessage(Component.literal(s),true);}
    static boolean admin(){return state!=null&&state.get("admin").getAsBoolean();}
    static boolean can(String p){return admin()&&java.util.stream.StreamSupport.stream(state.getAsJsonArray("permissions").spliterator(),false).anyMatch(v->v.getAsString().equals(p));}
    static void request(String... args) {
        if(!connected()||pending!=null)return;
        pending=UUID.randomUUID().toString();requestedAt=System.currentTimeMillis();message="Waiting for server...";
        MC.getConnection().sendCommand("kairuadmin "+pending+" "+String.join(" ",args));
    }
}
