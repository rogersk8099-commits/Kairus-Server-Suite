package com.neonnexus.smpplatform.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Explicit wire/cache codec; avoids unsafe polymorphic Gson deserialization for sealed policy types. */
public final class WorldRegistryJsonCodec {
    public String encodeCache(RegistryDocument document, Instant lastSyncAt) {
        JsonObject root = documentObject(document);
        if (lastSyncAt != null) root.addProperty("lastSyncAt", lastSyncAt.toString());
        return root.toString();
    }
    public WorldRegistryCache.CachedRegistry decodeCache(String source) {
        JsonObject root = JsonParser.parseString(source).getAsJsonObject();
        RegistryDocument document = parseDocument(root);
        Instant synced = root.has("lastSyncAt") ? Instant.parse(root.get("lastSyncAt").getAsString()) : null;
        return new WorldRegistryCache.CachedRegistry(document, synced);
    }
    public String encodeDocument(RegistryDocument document) { return documentObject(document).toString(); }
    public RegistryDocument decodeDocument(String source) { return parseDocument(JsonParser.parseString(source).getAsJsonObject()); }

    private JsonObject documentObject(RegistryDocument document) {
        JsonObject root = new JsonObject(); root.addProperty("revision", document.revision()); root.addProperty("generatedAt", document.generatedAt().toString());
        JsonArray worlds = new JsonArray(); document.worlds().forEach(world -> worlds.add(worldObject(world))); root.add("worlds", worlds); return root;
    }
    private RegistryDocument parseDocument(JsonObject root) {
        long revision = required(root, "revision").getAsLong(); Instant generatedAt = Instant.parse(required(root, "generatedAt").getAsString());
        List<WorldDefinition> worlds = new ArrayList<>(); for (JsonElement element : required(root, "worlds").getAsJsonArray()) worlds.add(parseWorld(element.getAsJsonObject()));
        return new RegistryDocument(revision, generatedAt, worlds);
    }
    private JsonObject worldObject(WorldDefinition world) {
        JsonObject value = new JsonObject();
        add(value, "id", world.id()); add(value, "minecraftWorldName", world.minecraftWorldName()); add(value, "displayName", world.displayName()); add(value, "description", world.description()); add(value, "type", world.type().value()); add(value, "season", world.season()); add(value, "status", world.status().name()); add(value, "difficulty", world.difficulty()); value.addProperty("borderSize", world.borderSize()); add(value, "pvpMode", world.pvpMode().name());
        value.addProperty("guildsEnabled", world.guildsEnabled()); value.addProperty("pointsEnabled", world.pointsEnabled()); add(value, "currencyId", world.currencyId()); value.addProperty("claimsEnabled", world.claimsEnabled()); value.addProperty("economyEnabled", world.economyEnabled()); add(value, "inventoryGroup", world.inventoryGroup()); value.add("resetPolicy", resetObject(world.resetPolicy())); value.add("archivePolicy", archiveObject(world.archivePolicy())); value.addProperty("discordEnabled", world.discordEnabled()); value.addProperty("websiteVisible", world.websiteVisible()); value.addProperty("mapVisible", world.mapVisible()); value.addProperty("playerCount", world.playerCount()); value.addProperty("maintenanceMode", world.maintenanceMode()); add(value, "accessPermission", world.accessPermission()); value.add("spawnLocation", spawnObject(world.spawnLocation())); return value;
    }
    private WorldDefinition parseWorld(JsonObject value) {
        return new WorldDefinition(string(value,"id"), string(value,"minecraftWorldName"), string(value,"displayName"), string(value,"description"), WorldType.of(string(value,"type")), string(value,"season"), WorldStatus.valueOf(string(value,"status")), string(value,"difficulty"), required(value,"borderSize").getAsLong(), PvpMode.valueOf(string(value,"pvpMode")), bool(value,"guildsEnabled"), bool(value,"pointsEnabled"), string(value,"currencyId"), bool(value,"claimsEnabled"), bool(value,"economyEnabled"), string(value,"inventoryGroup"), parseReset(required(value,"resetPolicy").getAsJsonObject()), parseArchive(required(value,"archivePolicy").getAsJsonObject()), bool(value,"discordEnabled"), bool(value,"websiteVisible"), bool(value,"mapVisible"), required(value,"playerCount").getAsInt(), bool(value,"maintenanceMode"), string(value,"accessPermission"), parseSpawn(required(value,"spawnLocation").getAsJsonObject()));
    }
    private static JsonObject resetObject(ResetPolicy policy) { JsonObject value = new JsonObject(); value.addProperty("kind", policy.kind()); if (policy instanceof ResetPolicy.Weekly weekly) { add(value,"day",weekly.day().name()); add(value,"time",weekly.time().toString()); add(value,"timezone",weekly.timezone().getId()); value.addProperty("safetyBackupRequired",weekly.safetyBackupRequired()); } else if (policy instanceof ResetPolicy.Manual manual) add(value,"reason",manual.reason()); return value; }
    private static ResetPolicy parseReset(JsonObject value) { return switch (string(value,"kind")) { case "none" -> new ResetPolicy.None(); case "manual" -> new ResetPolicy.Manual(string(value,"reason")); case "weekly" -> new ResetPolicy.Weekly(DayOfWeek.valueOf(string(value,"day")), LocalTime.parse(string(value,"time")), ZoneId.of(string(value,"timezone")), bool(value,"safetyBackupRequired")); default -> throw new IllegalArgumentException("Unknown reset policy kind"); }; }
    private static JsonObject archiveObject(ArchivePolicy policy) { JsonObject value = new JsonObject(); value.addProperty("tourMode",policy.tourMode()); value.addProperty("blockBreakDenied",policy.blockBreakDenied()); value.addProperty("blockPlaceDenied",policy.blockPlaceDenied()); value.addProperty("containerMutationDenied",policy.containerMutationDenied()); value.addProperty("terrainDamageDenied",policy.terrainDamageDenied()); return value; }
    private static ArchivePolicy parseArchive(JsonObject value) { return new ArchivePolicy(bool(value,"tourMode"),bool(value,"blockBreakDenied"),bool(value,"blockPlaceDenied"),bool(value,"containerMutationDenied"),bool(value,"terrainDamageDenied")); }
    private static JsonObject spawnObject(SpawnLocation spawn) { JsonObject value = new JsonObject(); add(value,"worldName",spawn.worldName()); value.addProperty("x",spawn.x()); value.addProperty("y",spawn.y()); value.addProperty("z",spawn.z()); value.addProperty("yaw",spawn.yaw()); value.addProperty("pitch",spawn.pitch()); return value; }
    private static SpawnLocation parseSpawn(JsonObject value) { return new SpawnLocation(string(value,"worldName"),required(value,"x").getAsDouble(),required(value,"y").getAsDouble(),required(value,"z").getAsDouble(),required(value,"yaw").getAsFloat(),required(value,"pitch").getAsFloat()); }
    private static void add(JsonObject object, String key, String value) { object.addProperty(key,value); }
    private static JsonElement required(JsonObject object, String key) { JsonElement value=object.get(key); if(value==null||value.isJsonNull()) throw new IllegalArgumentException("Missing JSON field: "+key); return value; }
    private static String string(JsonObject object,String key) { return required(object,key).getAsString(); }
    private static boolean bool(JsonObject object,String key) { return required(object,key).getAsBoolean(); }
}
