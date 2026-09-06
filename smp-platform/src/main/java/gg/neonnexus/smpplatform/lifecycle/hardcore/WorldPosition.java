package gg.neonnexus.smpplatform.lifecycle.hardcore;

/** Stable, serializable death coordinate capture independent from Bukkit objects. */
public record WorldPosition(String worldId, double x, double y, double z, float yaw, float pitch) { }
