package network.neonnexus.smp.admin.domain;

import java.util.Objects;

/** Authorization is evaluated at dispatch time, not only when a button is rendered. */
public final class PermissionRouter {
    public static final String ROOT = "smpplatform.admin";

    public boolean canOpen(PermissionSubject subject, String permission) {
        Objects.requireNonNull(subject, "subject");
        return subject.hasPermission(ROOT) || subject.hasPermission(permission);
    }

    public boolean canPerform(PermissionSubject subject, AdminAction action) {
        Objects.requireNonNull(action, "action");
        return canOpen(subject, action.permission());
    }

    @FunctionalInterface
    public interface PermissionSubject {
        boolean hasPermission(String permission);
    }
}
