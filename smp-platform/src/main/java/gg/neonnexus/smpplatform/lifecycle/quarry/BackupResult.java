package gg.neonnexus.smpplatform.lifecycle.quarry;

import java.nio.file.Path;

/** A backup is accepted only when it has a location and independent verification passed. */
public record BackupResult(Path location, boolean verified, String detail) {
    public static BackupResult failed(String detail) { return new BackupResult(null, false, detail); }
}
