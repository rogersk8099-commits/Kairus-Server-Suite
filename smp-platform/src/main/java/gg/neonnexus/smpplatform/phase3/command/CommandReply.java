package gg.neonnexus.smpplatform.phase3.command;

import java.util.List;

/** Framework-neutral result to render with Adventure; no synchronous persistence is performed here. */
public record CommandReply(boolean success, List<String> lines, String openGui) {
    public CommandReply { lines = List.copyOf(lines); }
    public static CommandReply ok(String... lines) { return new CommandReply(true, List.of(lines), null); }
    public static CommandReply gui(String screen, String... lines) { return new CommandReply(true, List.of(lines), screen); }
    public static CommandReply error(String message) { return new CommandReply(false, List.of(message), null); }
}
