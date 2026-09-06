package gg.neonnexus.smpplatform.integrations.adapters;

import java.util.UUID;

/** Edition detection uses Floodgate UUID mappings where available; Geyser alone is health-only. */
public final class GeyserFloodgateAdapter extends SoftAdapter {
    private final EditionAdapter editionAdapter;
    public GeyserFloodgateAdapter(SoftDependency geyser, EditionAdapter editionAdapter) { super(geyser); this.editionAdapter = editionAdapter; }
    public EditionAdapter.Edition edition(UUID minecraftUuid) { return editionAdapter.edition(minecraftUuid); }
    public boolean geyserAvailable() { return available(); }
}
