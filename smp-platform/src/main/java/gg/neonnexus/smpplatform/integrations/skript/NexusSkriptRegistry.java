package gg.neonnexus.smpplatform.integrations.skript;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.ExpressionType;
import java.util.Objects;

/** One controlled registration site keeps Skript syntax maintainable and lets the feature remain fully optional. */
public final class NexusSkriptRegistry {
    private static volatile NexusSkriptBridge bridge;
    private NexusSkriptRegistry() { }
    public static void register(NexusSkriptBridge suppliedBridge) {
        bridge = Objects.requireNonNull(suppliedBridge, "bridge");
        Skript.registerExpression(ExprNexusWorldType.class, String.class, ExpressionType.SIMPLE, "[the] nexus world type of %player%");
        Skript.registerExpression(ExprNexusGuild.class, String.class, ExpressionType.SIMPLE, "[the] nexus guild of %player%");
        Skript.registerExpression(ExprNexusPoints.class, Long.class, ExpressionType.SIMPLE, "[the] nexus points of %player%", "[the] nexus %string% points of %player%");
        Skript.registerExpression(ExprNexusHardcoreStatus.class, String.class, ExpressionType.SIMPLE, "[the] nexus hardcore status of %player%");
        Skript.registerExpression(ExprNexusMembership.class, String.class, ExpressionType.SIMPLE, "[the] nexus membership of %player%");
        Skript.registerExpression(ExprNexusLinked.class, Boolean.class, ExpressionType.SIMPLE, "[whether] %player% is nexus linked");
        Skript.registerEffect(EffNexusPoints.class, "add %number% nexus points to %players%", "remove %number% nexus points from %players%", "add %number% nexus %string% points to %players%", "remove %number% nexus %string% points from %players%");
        Skript.registerEffect(EffNexusNotification.class, "send nexus notification %string% to %players%");
        Skript.registerEffect(EffNexusPlatformEvent.class, "trigger nexus platform event %string% for %players%", "trigger nexus platform event %string% with metadata %string% for %players%");
        Skript.registerEffect(EffNexusGuildEvent.class, "create nexus guild event %string% for %players%");
    }
    static NexusSkriptBridge bridge() { return Objects.requireNonNull(bridge, "Nexus Skript bridge is not registered"); }
}
