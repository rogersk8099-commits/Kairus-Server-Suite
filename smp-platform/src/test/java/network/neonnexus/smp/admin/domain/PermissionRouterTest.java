package network.neonnexus.smp.admin.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionRouterTest {
    private final PermissionRouter router = new PermissionRouter();

    @Test void actionUsesItsGranularPermissionRatherThanSelectorPermission() {
        PermissionRouter.PermissionSubject moderator = Set.of("smpplatform.admin.moderation.kick")::contains;
        assertTrue(router.canPerform(moderator, AdminAction.KICK));
        assertFalse(router.canPerform(moderator, AdminAction.BAN));
        assertFalse(router.canOpen(moderator, "smpplatform.admin.players"));
    }

    @Test void rootAdministratorRoutesToEveryPhaseTwoAction() {
        PermissionRouter.PermissionSubject root = Set.of(PermissionRouter.ROOT)::contains;
        for (AdminAction action : AdminAction.values()) assertTrue(router.canPerform(root, action), action.name());
    }
}
