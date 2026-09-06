package gg.neonnexus.smpplatform.integrations.adapters;

import gg.neonnexus.smpplatform.integrations.IntegrationHealth;
import java.util.Objects;

/** Base for all optional integrations: unavailable means graceful feature degradation, never a startup failure. */
public abstract class SoftAdapter {
    private final SoftDependency dependency;
    protected SoftAdapter(SoftDependency dependency) { this.dependency = Objects.requireNonNull(dependency); }
    public final boolean available() { return dependency.available(); }
    public final String integrationName() { return dependency.pluginName(); }
    protected final <T> AdapterResult<T> unavailable() { return AdapterResult.unavailable(integrationName() + " is unavailable"); }
}
