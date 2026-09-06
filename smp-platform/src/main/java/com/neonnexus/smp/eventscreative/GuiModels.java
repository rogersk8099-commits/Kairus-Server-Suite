package com.neonnexus.smp.eventscreative;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Presentation-neutral models; no decorative control is represented as an executable action. */
record GuiScreen(String id, String title, List<GuiItem> items) {
    public GuiScreen { if (id == null || id.isBlank()) throw new IllegalArgumentException("screen id required"); items = List.copyOf(items); }
}
record GuiItem(int slot, String label, List<String> lore, String icon, GuiIntent intent) {
    public GuiItem { if (slot < 0 || slot > 53) throw new IllegalArgumentException("slot must fit a chest GUI"); Objects.requireNonNull(intent, "intent"); lore = List.copyOf(lore); }
}
sealed interface GuiIntent permits GuiIntent.Rsvp, GuiIntent.Withdraw, GuiIntent.CheckIn, GuiIntent.OpenEventAdmin, GuiIntent.OpenReviewQueue, GuiIntent.OpenSubmission {
    record Rsvp(UUID eventId) implements GuiIntent { }
    record Withdraw(UUID eventId) implements GuiIntent { }
    record CheckIn(UUID eventId) implements GuiIntent { }
    record OpenEventAdmin(UUID eventId) implements GuiIntent { }
    record OpenReviewQueue() implements GuiIntent { }
    record OpenSubmission(UUID submissionId) implements GuiIntent { }
}

final class EventGuiFactory {
    GuiScreen eventDetail(ColosseumEvent event, EventRegistration viewerRegistration, boolean staff) {
        java.util.ArrayList<GuiItem> items = new java.util.ArrayList<>();
        if (event.acceptsRsvp() && viewerRegistration == null) items.add(new GuiItem(11, "RSVP", List.of("Reserve a place in Neon Colosseum."), "LIME_DYE", new GuiIntent.Rsvp(event.id())));
        if (viewerRegistration != null && (viewerRegistration.state() == RegistrationState.REGISTERED || viewerRegistration.state() == RegistrationState.WAITLISTED))
            items.add(new GuiItem(13, "Withdraw RSVP", List.of("Release your place or waitlist entry."), "RED_DYE", new GuiIntent.Withdraw(event.id())));
        if (event.acceptsCheckIn() && viewerRegistration != null && viewerRegistration.state() == RegistrationState.REGISTERED)
            items.add(new GuiItem(15, "Check in", List.of("Confirm that you are ready to compete."), "CLOCK", new GuiIntent.CheckIn(event.id())));
        if (staff) items.add(new GuiItem(31, "Event administration", List.of("Lifecycle, teams, rounds and results."), "NETHER_STAR", new GuiIntent.OpenEventAdmin(event.id())));
        return new GuiScreen("event:" + event.id(), "Neon Colosseum", items);
    }
}

final class CreativeGuiFactory {
    GuiScreen creativeHome(boolean staff) {
        var items = new java.util.ArrayList<GuiItem>();
        if (staff) items.add(new GuiItem(22, "Review build submissions", List.of("Open the pending Atrium review queue."), "SPYGLASS", new GuiIntent.OpenReviewQueue()));
        return new GuiScreen("creative:home", "The Atrium", items);
    }
    GuiScreen reviewQueue(List<BuildSubmission> submissions) {
        var items = new java.util.ArrayList<GuiItem>();
        for (int index = 0; index < Math.min(54, submissions.size()); index++) {
            BuildSubmission submission = submissions.get(index);
            items.add(new GuiItem(index, submission.title(), List.of("Plot: " + submission.plotId(), "Creator: " + submission.submitterId()), "MAP", new GuiIntent.OpenSubmission(submission.id())));
        }
        return new GuiScreen("creative:review-queue", "Atrium Review Queue", items);
    }
}
