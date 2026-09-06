package com.neonnexus.smpplatform.database;

import com.google.gson.Gson;
import com.neonnexus.smpplatform.world.RegistryDocument;
import com.neonnexus.smpplatform.world.WorldDefinition;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

/** Persists the applied registry snapshot only after its cache transaction has safely completed. */
public final class WorldRegistryStateRepository {
    private final Gson gson = new Gson();
    public void save(Connection connection, RegistryDocument document, Instant syncedAt) {
        Objects.requireNonNull(connection); Objects.requireNonNull(document); Objects.requireNonNull(syncedAt);
        String sql = "INSERT INTO smp_worlds (id,minecraft_world_name,display_name,world_type,season,status,registry_revision,definition_json,updated_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT (id) DO UPDATE SET minecraft_world_name=EXCLUDED.minecraft_world_name,display_name=EXCLUDED.display_name,world_type=EXCLUDED.world_type,season=EXCLUDED.season,status=EXCLUDED.status,registry_revision=EXCLUDED.registry_revision,definition_json=EXCLUDED.definition_json,updated_at=EXCLUDED.updated_at";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (WorldDefinition world : document.worlds()) {
                statement.setString(1, world.id()); statement.setString(2, world.minecraftWorldName()); statement.setString(3, world.displayName());
                statement.setString(4, world.type().value()); statement.setString(5, world.season()); statement.setString(6, world.status().name());
                statement.setLong(7, document.revision()); statement.setObject(8, gson.toJson(world), java.sql.Types.OTHER); statement.setTimestamp(9, Timestamp.from(syncedAt)); statement.addBatch();
            }
            statement.executeBatch();
        } catch (java.sql.SQLException exception) { throw new DatabaseExecutor.DatabaseOperationException("Unable to save World Registry state", exception); }
    }
}
