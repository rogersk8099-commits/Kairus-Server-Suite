package com.kairu.bridge.chat;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Main-thread delivery guard. The control-plane queue remains authoritative; this prevents local duplicate broadcasts. */
final class RecentMessageIds {
    private static final int MAXIMUM_IDS = 2_048;
    private final long ttlMillis;
    private final LinkedHashMap<String, Long> seen = new LinkedHashMap<>();

    RecentMessageIds(long ttlMillis) { this.ttlMillis = ttlMillis; }

    boolean alreadyDelivered(String id) {
        long now = System.currentTimeMillis();
        prune(now);
        return seen.containsKey(id);
    }

    void markDelivered(String id) {
        long now = System.currentTimeMillis();
        prune(now);
        seen.put(id, now);
        while (seen.size() > MAXIMUM_IDS) seen.remove(seen.keySet().iterator().next());

    }

    private void prune(long now) {
        Iterator<Map.Entry<String, Long>> iterator = seen.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue() > ttlMillis) iterator.remove();
            else break;
        }
    }
}
