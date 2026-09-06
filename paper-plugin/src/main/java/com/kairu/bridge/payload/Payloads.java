package com.kairu.bridge.payload;

import java.util.*;

public final class Payloads {
    private Payloads() { }

    public record ServerHeartbeat(double tps, int playerCount, List<String> worlds, List<String> players, String version, long uptimeSeconds) {
        public String toJson() {
            return Json.object(new LinkedHashMap<>(Map.of(
                    "online", true, "tps", tps, "playerCount", playerCount, "worlds", worlds,
                    "players", players, "version", version, "uptimeSeconds", uptimeSeconds
            )));
        }
    }

    public record PlayerSnapshot(UUID minecraftUuid, String name, long playtimeSeconds, long blocksBroken, long kills, long deaths, double distanceMeters, double balance, String rankName, String worldName) {
        public String toJson() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("minecraftUuid", minecraftUuid.toString()); fields.put("name", name); fields.put("playtimeSeconds", playtimeSeconds);
            fields.put("blocksBroken", blocksBroken); fields.put("kills", kills); fields.put("deaths", deaths);
            fields.put("distanceMeters", distanceMeters); fields.put("balance", balance); fields.put("rankName", rankName);
            if (worldName != null && !worldName.isBlank()) fields.put("worldName", worldName);
            return Json.object(fields);
        }
    }

    public record LinkCompletion(String code, UUID minecraftUuid, String javaUsername, String bedrockXuid) {
        public LinkCompletion {
            if (code == null || !code.matches("[A-Z0-9_-]{20,128}")) throw new IllegalArgumentException("Link code format is invalid");
            Objects.requireNonNull(minecraftUuid, "minecraftUuid");
            if (javaUsername == null || !javaUsername.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Minecraft username is invalid");
        }
        public String toJson() {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("code", code); fields.put("minecraftUuid", minecraftUuid.toString()); fields.put("javaUsername", javaUsername);
            if (bedrockXuid != null && !bedrockXuid.isBlank()) fields.put("bedrockXuid", bedrockXuid);
            return Json.object(fields);
        }
    }
}
