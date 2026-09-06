package gg.neonnexus.smpplatform.integrations.adapters;

import java.lang.reflect.Method;
import java.util.UUID;

/** Floodgate reflection avoids a hard class link and never treats a username prefix as identity. */
public final class EditionAdapter {
    public enum Edition { JAVA, BEDROCK, UNKNOWN }
    private final SoftDependency floodgate;
    public EditionAdapter(SoftDependency floodgate) { this.floodgate = floodgate; }
    public Edition edition(UUID minecraftUuid) {
        if (!floodgate.available()) return Edition.JAVA;
        try {
            Class<?> apiType = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiType.getMethod("getInstance").invoke(null);
            Method isFloodgatePlayer = apiType.getMethod("isFloodgatePlayer", UUID.class);
            return Boolean.TRUE.equals(isFloodgatePlayer.invoke(api, minecraftUuid)) ? Edition.BEDROCK : Edition.JAVA;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Edition.UNKNOWN;
        }
    }
}
