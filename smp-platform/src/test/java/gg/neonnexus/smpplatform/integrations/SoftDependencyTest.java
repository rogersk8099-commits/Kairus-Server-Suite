package gg.neonnexus.smpplatform.integrations;

import gg.neonnexus.smpplatform.integrations.adapters.OptionalAdapters;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SoftDependencyTest {
    @Test void all_optional_integrations_degrade_without_startup_failure() {
        IntegrationHealth health = new IntegrationHealth();
        OptionalAdapters adapters = new OptionalAdapters(name -> false, health);
        for (String integration : adapters.dependencies().keySet()) assertFalse(adapters.available(integration));
        assertEquals(IntegrationHealth.State.UNAVAILABLE, health.status(OptionalAdapters.LUCKPERMS).state());
        assertEquals(IntegrationHealth.State.UNAVAILABLE, health.status(OptionalAdapters.SKRIPT).state());
    }
}
