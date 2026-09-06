import { createFileRoute } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { queries } from "@/services/smp";
import { site } from "@/config/site";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/portal/settings")({
  head: () => ({
    meta: [
      { title: `Settings — ${site.name} portal` },
      {
        name: "description",
        content: `Update your ${site.name} account details, notifications and privacy preferences.`,
      },
      { property: "og:title", content: `Settings — ${site.name} portal` },
      {
        property: "og:description",
        content: `Account and notification settings for ${site.name}.`,
      },
    ],
  }),
  loader: ({ context }) => context.queryClient.ensureQueryData(queries.portalUser),
  component: SettingsPage,
});

function Toggle({
  label,
  hint,
  on,
  onChange,
}: {
  label: string;
  hint: string;
  on: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <div className="flex items-center justify-between gap-4 py-3">
      <div className="min-w-0">
        <p className="text-sm font-semibold">{label}</p>
        <p className="text-xs text-muted-foreground">{hint}</p>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={on}
        aria-label={label}
        onClick={() => onChange(!on)}
        className={cn(
          "relative h-6 w-11 shrink-0 rounded-full border transition-colors",
          on ? "border-primary bg-primary/40" : "border-border bg-muted",
        )}
      >
        <span
          className={cn(
            "absolute top-0.5 h-4 w-4 rounded-full bg-foreground transition-transform",
            on ? "translate-x-6" : "translate-x-1",
          )}
        />
      </button>
    </div>
  );
}

function SettingsPage() {
  const { data: user } = useSuspenseQuery(queries.portalUser);
  const [email, setEmail] = useState(user.email);
  const [prefs, setPrefs] = useState({
    eventPings: true,
    streamAlerts: true,
    weeklyRecap: false,
    publicProfile: true,
    showOnLeaderboard: true,
  });

  return (
    <div className="space-y-8">
      <header>
        <h1 className="display-xl text-4xl">
          Set<span className="gradient-text">tings</span>
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Account, notifications and privacy for your {site.name} profile.
        </p>
      </header>

      <form
        className="panel rounded-lg p-6"
        onSubmit={(e) => {
          e.preventDefault();
          toast.success("Account details saved");
        }}
      >
        <h2 className="text-sm font-bold uppercase tracking-[0.18em]">Account</h2>
        <div className="mt-4 grid gap-4 sm:grid-cols-2">
          <label className="text-xs uppercase tracking-[0.16em] text-muted-foreground">
            Username
            <input
              value={user.username}
              readOnly
              className="mt-2 w-full rounded-sm border border-border bg-muted/40 px-3 py-2.5 text-sm normal-case tracking-normal text-muted-foreground"
            />
          </label>
          <label className="text-xs uppercase tracking-[0.16em] text-muted-foreground">
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="mt-2 w-full rounded-sm border border-border bg-background px-3 py-2.5 text-sm normal-case tracking-normal text-foreground focus:border-primary focus:outline-none"
            />
          </label>
          <label className="text-xs uppercase tracking-[0.16em] text-muted-foreground sm:col-span-2">
            Minecraft UUID
            <input
              value={user.minecraftUuid}
              readOnly
              className="mt-2 w-full rounded-sm border border-border bg-muted/40 px-3 py-2.5 text-sm normal-case tracking-normal text-muted-foreground"
            />
          </label>
        </div>
        <button
          type="submit"
          className="mt-5 rounded-sm bg-gradient-to-r from-magenta via-violet to-electric px-5 py-2.5 text-xs font-bold uppercase tracking-[0.16em] text-primary-foreground transition-transform hover:-translate-y-0.5"
        >
          Save changes
        </button>
      </form>

      <section className="panel rounded-lg p-6">
        <h2 className="text-sm font-bold uppercase tracking-[0.18em]">Notifications</h2>
        <div className="mt-2 divide-y divide-border/60">
          <Toggle
            label="Event pings"
            hint="Discord DM when an event you joined is about to start."
            on={prefs.eventPings}
            onChange={(v) => setPrefs((p) => ({ ...p, eventPings: v }))}
          />
          <Toggle
            label="Stream alerts"
            hint="Get notified when featured creators go live."
            on={prefs.streamAlerts}
            onChange={(v) => setPrefs((p) => ({ ...p, streamAlerts: v }))}
          />
          <Toggle
            label="Weekly recap"
            hint="A Monday email with your stats and season standing."
            on={prefs.weeklyRecap}
            onChange={(v) => setPrefs((p) => ({ ...p, weeklyRecap: v }))}
          />
        </div>
      </section>

      <section className="panel rounded-lg p-6">
        <h2 className="text-sm font-bold uppercase tracking-[0.18em]">Privacy</h2>
        <div className="mt-2 divide-y divide-border/60">
          <Toggle
            label="Public profile"
            hint="Let other members view your build gallery and stats."
            on={prefs.publicProfile}
            onChange={(v) => setPrefs((p) => ({ ...p, publicProfile: v }))}
          />
          <Toggle
            label="Show on leaderboards"
            hint="Hide to compete privately without appearing in standings."
            on={prefs.showOnLeaderboard}
            onChange={(v) => setPrefs((p) => ({ ...p, showOnLeaderboard: v }))}
          />
        </div>
      </section>

      <section className="panel rounded-lg border-destructive/40 p-6">
        <h2 className="text-sm font-bold uppercase tracking-[0.18em] text-destructive">
          Danger zone
        </h2>
        <p className="mt-2 text-xs text-muted-foreground">
          Deleting your account removes your stats, claims and event history. This cannot be undone.
        </p>
        <button
          type="button"
          onClick={() => toast.error("Account deletion needs admin confirmation")}
          className="mt-4 rounded-sm border border-destructive/60 px-4 py-2.5 text-xs font-bold uppercase tracking-[0.16em] text-destructive transition-colors hover:bg-destructive/10"
        >
          Delete account
        </button>
      </section>
    </div>
  );
}
