package gg.neonnexus.smpplatform.lifecycle.events;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * JSONL append-only fallback outbox. Each append is forced to disk so a failed central API does
 * not lose Quarry/Hardcore lifecycle events. A delivery worker may import these into event_outbox.
 */
public final class FileDurableEventOutbox implements DurableEventOutbox {
    private final Path path;

    public FileDurableEventOutbox(Path path) { this.path = path; }

    @Override public synchronized void publish(DurableEvent event) {
        try {
            Files.createDirectories(path.getParent());
            String line = toJson(event) + System.lineSeparator();
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND)) {
                channel.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
                channel.force(true);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot durably append lifecycle event to " + path, exception);
        }
    }

    private static String toJson(DurableEvent event) {
        StringBuilder attributes = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> item : event.attributes().entrySet()) {
            if (!first) attributes.append(',');
            first = false;
            attributes.append('\"').append(escape(item.getKey())).append("\":\"")
                    .append(escape(item.getValue())).append('\"');
        }
        return "{\"id\":\"" + event.id() + "\",\"type\":\"" + escape(event.type())
                + "\",\"occurredAt\":\"" + event.occurredAt() + "\",\"correlationId\":\""
                + escape(event.correlationId()) + "\",\"attributes\":{" + attributes + "}}";
    }

    private static String escape(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }
}
