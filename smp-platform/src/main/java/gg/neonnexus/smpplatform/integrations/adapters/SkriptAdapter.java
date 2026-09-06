package gg.neonnexus.smpplatform.integrations.adapters;

/** Keeps optional Skript registration in a single guarded entry point. */
public final class SkriptAdapter extends SoftAdapter {
    @FunctionalInterface public interface Registrar { void register(); }
    private final Registrar registrar;
    public SkriptAdapter(SoftDependency dependency, Registrar registrar) { super(dependency); this.registrar = registrar; }
    public AdapterResult<Boolean> registerSyntax() {
        if (!available()) return unavailable();
        try { registrar.register(); return AdapterResult.of(true); }
        catch (RuntimeException exception) { return AdapterResult.failed("Skript registration failed: " + exception.getClass().getSimpleName()); }
    }
}
