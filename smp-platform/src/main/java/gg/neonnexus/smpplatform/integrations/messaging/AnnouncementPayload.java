package gg.neonnexus.smpplatform.integrations.messaging;

import gg.neonnexus.smpplatform.integrations.NexusWorld;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import java.util.Optional;
import java.util.UUID;

/** MiniMessage is retained as structured text; it is validated before audience delivery and outbound API publication. */
public record AnnouncementPayload(UUID id, String actor, Channel channel, String titleMiniMessage, String bodyMiniMessage, Optional<NexusWorld> world, boolean platform, boolean discord, boolean website) {
    public enum Channel { CHAT, TITLE, SUBTITLE, ACTION_BAR, BOSS_BAR }
    public AnnouncementPayload {
        if (id == null || channel == null) throw new IllegalArgumentException("id and channel are required");
        actor = bounded(actor == null ? "SYSTEM" : actor, 64, "actor"); titleMiniMessage = bounded(titleMiniMessage == null ? "" : titleMiniMessage, 256, "title"); bodyMiniMessage = bounded(bodyMiniMessage, 512, "body"); world = world == null ? Optional.empty() : world;
        MiniMessage.miniMessage().deserialize(titleMiniMessage); MiniMessage.miniMessage().deserialize(bodyMiniMessage);
    }
    public Component titleComponent() { return MiniMessage.miniMessage().deserialize(titleMiniMessage); }
    public Component bodyComponent() { return MiniMessage.miniMessage().deserialize(bodyMiniMessage); }
    private static String bounded(String value, int max, String name) { if (value.length() > max) throw new IllegalArgumentException(name + " exceeds " + max + " characters"); return value; }
}
