package com.kairu.bridge;

import com.kairu.bridge.api.AuthHeaders;
import com.kairu.bridge.api.ControlPlaneClient;
import com.kairu.bridge.payload.Payloads;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractBehaviorTest {
    @Test
    void appliesContractBearerAndServerHeaders() {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://control.example/api/plugin/heartbeat"));
        HttpRequest request = AuthHeaders.apply(builder, "plugin-secret", "production-01").GET().build();
        assertEquals("Bearer plugin-secret", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("production-01", request.headers().firstValue("X-Kairu-Server-Id").orElseThrow());
        assertEquals("application/json", request.headers().firstValue("Accept").orElseThrow());
    }

    @Test
    void rejectsHeaderInjectionInCredentials() {
        assertThrows(IllegalArgumentException.class, () -> AuthHeaders.apply(HttpRequest.newBuilder(URI.create("https://control.example")), "safe\r\nInjected: yes", "server"));
        assertThrows(IllegalArgumentException.class, () -> AuthHeaders.apply(HttpRequest.newBuilder(URI.create("https://control.example")), "safe", "server\nother"));
    }

    @Test
    void linkPayloadCarriesJavaIdentityAndOptionalFloodgateXuid() {
        UUID uuid = UUID.fromString("6b3a5b8f-0dda-4dcc-8a48-579ef5b2c78a");
        Payloads.LinkCompletion payload = new Payloads.LinkCompletion("KAIRU_8Z-ABCDEFGHIJKLMNOP", uuid, "Steve", "2535412345678901");
        String json = payload.toJson();
        assertTrue(json.contains("\"code\":\"KAIRU_8Z-ABCDEFGHIJKLMNOP\""));
        assertTrue(json.contains("\"minecraftUuid\":\"6b3a5b8f-0dda-4dcc-8a48-579ef5b2c78a\""));
        assertTrue(json.contains("\"javaUsername\":\"Steve\""));
        assertTrue(json.contains("\"bedrockXuid\":\"2535412345678901\""));
    }

    @Test
    void linkPayloadRejectsUnsafeCodeAndUsername() {
        UUID uuid = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new Payloads.LinkCompletion("bad code", uuid, "Steve", null));
        assertThrows(IllegalArgumentException.class, () -> new Payloads.LinkCompletion("VALID_CODE_1234567890", uuid, "sixteen_chars_nope", null));
    }

    @Test
    void heartbeatPayloadEscapesWorldNamesAndUsesContractFields() {
        String json = new Payloads.ServerHeartbeat(19.75, 2, List.of("world", "quote\"world"), List.of("Steve", "Alex"), "Paper 1.21.4 / KairuBridge 1.0.0", 99).toJson();
        assertTrue(json.contains("\"online\":true"));
        assertTrue(json.contains("\"playerCount\":2"));
        assertTrue(json.contains("\"players\":[\"Steve\",\"Alex\"]"));
        assertTrue(json.contains("quote\\\"world"));
    }
    @Test
    void playerSnapshotUsesStrictControlPlaneFieldNamesAndUnits() {
        UUID uuid = UUID.fromString("6b3a5b8f-0dda-4dcc-8a48-579ef5b2c78a");
        String json = new Payloads.PlayerSnapshot(uuid, "Steve", 120, 42, 3, 1, 12.5, 7.25, "Member", "world").toJson();
        assertTrue(json.contains("\"minecraftUuid\":\"6b3a5b8f-0dda-4dcc-8a48-579ef5b2c78a\""));
        assertTrue(json.contains("\"playtimeSeconds\":120"));
        assertTrue(json.contains("\"blocksBroken\":42"));
        assertTrue(json.contains("\"distanceMeters\":12.5"));
        assertTrue(json.contains("\"rankName\":\"Member\""));
        assertFalse(json.contains("\"uuid\""));
        assertFalse(json.contains("\"playtime\""));
    }

    @Test
    @SuppressWarnings("unchecked")
    void parsesControlPlaneCommandEnvelopeAndPayload() throws Exception {
        Method method = ControlPlaneClient.class.getDeclaredMethod("parseCommands", String.class);
        method.setAccessible(true);
        String body = "{\"commands\":[{\"id\":\"123e4567-e89b-42d3-a456-426614174000\",\"commandType\":\"whitelist\",\"payload\":{\"action\":\"add\",\"player\":\"Steve\"}}]}";
        List<ControlPlaneClient.RemoteCommand> commands = (List<ControlPlaneClient.RemoteCommand>) method.invoke(null, body);
        assertEquals(1, commands.size());
        assertEquals("WHITELIST_ADD", commands.getFirst().type());
        assertEquals("Steve", commands.getFirst().player());
    }

}
