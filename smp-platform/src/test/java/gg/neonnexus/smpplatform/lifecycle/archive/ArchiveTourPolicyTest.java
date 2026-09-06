package gg.neonnexus.smpplatform.lifecycle.archive;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArchiveTourPolicyTest {
    @Test void allMutationInteractionsAreDeniedInConfiguredArchive() {
        ArchiveTourPolicy policy = new ArchiveTourPolicy(Set.of("verdance", "future-season-archive"));
        for (ArchiveInteraction interaction : ArchiveInteraction.values()) assertTrue(policy.shouldCancel("verdance", interaction));
        assertTrue(policy.shouldCancel("future-season-archive", ArchiveInteraction.CONTAINER_OPEN));
        assertFalse(policy.shouldCancel("ashfall", ArchiveInteraction.BLOCK_BREAK));
    }
}
