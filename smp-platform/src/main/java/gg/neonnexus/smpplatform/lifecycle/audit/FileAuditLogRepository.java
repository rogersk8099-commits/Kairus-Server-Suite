package gg.neonnexus.smpplatform.lifecycle.audit;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Fsync-backed audit fallback. Production deployments should inject a PostgreSQL audit_logs adapter. */
public final class FileAuditLogRepository implements AuditLogRepository {
    private final Path file;
    public FileAuditLogRepository(Path file) { this.file = file; }
    @Override public synchronized void append(AuditEntry entry) {
        try {
            Files.createDirectories(file.getParent());
            String line = "{\"id\":\"" + entry.id() + "\",\"occurredAt\":\"" + entry.occurredAt()
                    + "\",\"actorId\":\"" + entry.actorId() + "\",\"action\":\"" + escape(entry.action())
                    + "\",\"target\":\"" + escape(entry.target()) + "\",\"worldId\":\"" + escape(entry.worldId())
                    + "\",\"correlationId\":\"" + escape(entry.correlationId()) + "\",\"reason\":\"" + escape(entry.reason()) + "\"}" + System.lineSeparator();
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                channel.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
                channel.force(true);
            }
        } catch (IOException exception) { throw new IllegalStateException("Cannot durably append audit entry", exception); }
    }
    private static String escape(String value) { return (value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
}
