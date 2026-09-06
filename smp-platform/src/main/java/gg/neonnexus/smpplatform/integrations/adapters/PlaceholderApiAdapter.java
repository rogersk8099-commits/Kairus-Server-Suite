package gg.neonnexus.smpplatform.integrations.adapters;

/** Register callback is invoked only after PlaceholderAPI is confirmed enabled, avoiding a hard startup requirement. */
public final class PlaceholderApiAdapter extends SoftAdapter {
    @FunctionalInterface public interface Registrar { boolean register(); }
    private final Registrar registrar;
    public PlaceholderApiAdapter(SoftDependency dependency, Registrar registrar) { super(dependency); this.registrar = registrar; }
    public AdapterResult<Boolean> registerExpansion() { return available() ? AdapterResult.of(registrar.register()) : unavailable(); }
}
