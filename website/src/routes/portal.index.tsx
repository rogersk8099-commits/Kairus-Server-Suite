import { createFileRoute, Link } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { CheckCircle2 } from "lucide-react";
import { queries } from "@/services/smp";
import { StatTile } from "@/components/site/primitives";
import { useAuth } from "@/lib/auth";

export const Route = createFileRoute("/portal/")({
  loader: async ({ context }) => {
    await Promise.all([
      context.queryClient.ensureQueryData(queries.minecraftStats),
      context.queryClient.ensureQueryData(queries.events),
      context.queryClient.ensureQueryData(queries.achievements),
      context.queryClient.ensureQueryData(queries.portalUser),
    ]);
  },
  component: Dashboard,
});

function Dashboard() {
  const { session } = useAuth();
  const { data: stats } = useSuspenseQuery(queries.minecraftStats);
  const { data: events } = useSuspenseQuery(queries.events);
  const { data: achievements } = useSuspenseQuery(queries.achievements);
  const { data: user } = useSuspenseQuery(queries.portalUser);

  const unlocked = achievements.filter((a) => a.unlocked).length;
  const peak = Math.max(...stats.weekly.map((d) => d.hours));

  return (
    <div className="space-y-8">
      <header>
        <p className="text-[0.72rem] font-bold uppercase tracking-[0.3em] text-muted-foreground">
          {user.tier} member since {user.joinedAt}
        </p>
        <h1 className="display-xl mt-2 text-4xl sm:text-5xl">
          Welcome back, <span className="gradient-text neon-text">{session?.username}</span>
        </h1>
      </header>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <StatTile label="Playtime" value={`${stats.playtimeHours}h`} hint="Season 7" />
        <StatTile label="Achievements" value={`${unlocked}/${achievements.length}`} />
        <StatTile label="PvP kills" value="3,480" hint="Top 1% this season" />
        <StatTile label="Balance" value="84.2K" hint="Shards available" />
        <StatTile label="Current world" value="Ashfall" hint="Survival · EU node" />
      </div>

      <section className="panel rounded-lg p-6">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="text-[0.7rem] font-bold uppercase tracking-[0.24em] text-magenta">
              Identity bridge
            </p>
            <h2 className="mt-1 text-sm font-bold uppercase tracking-[0.18em]">Linked accounts</h2>
          </div>
          <span className="text-[0.7rem] uppercase tracking-[0.18em] text-muted-foreground">
            3 of 3 connected
          </span>
        </div>
        <div className="mt-5 grid gap-3 md:grid-cols-3">
          {[
            ["Discord", `@${user.discordTag}`, "Community roles synced"],
            ["Minecraft Java", user.username, "Primary game account"],
            ["Minecraft Bedrock", `${user.username}_BE`, "Crossplay identity"],
          ].map(([label, value, hint]) => (
            <div key={label} className="border border-border/70 bg-background/45 p-4">
              <div className="flex items-center justify-between gap-3">
                <p className="text-[0.7rem] font-bold uppercase tracking-[0.2em] text-muted-foreground">
                  {label}
                </p>
                <span className="inline-flex items-center gap-1 text-[0.66rem] font-bold uppercase tracking-[0.16em] text-primary">
                  <CheckCircle2 className="h-3.5 w-3.5" /> Connected
                </span>
              </div>
              <p className="mt-3 truncate font-semibold text-foreground">{value}</p>
              <p className="mt-1 text-xs text-muted-foreground">{hint}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="panel rounded-lg p-6">
        <h2 className="text-sm font-bold uppercase tracking-[0.18em]">This week</h2>
        <div className="mt-6 flex items-end gap-3">
          {stats.weekly.map((d) => (
            <div key={d.day} className="flex flex-1 flex-col items-center gap-2">
              <div
                className="w-full rounded-t-sm bg-gradient-to-t from-electric to-magenta transition-all"
                style={{ height: `${(d.hours / peak) * 120}px` }}
              />
              <span className="text-[0.72rem] uppercase tracking-[0.16em] text-muted-foreground">
                {d.day}
              </span>
            </div>
          ))}
        </div>
      </section>

      <section className="grid gap-4 lg:grid-cols-2">
        <div className="panel rounded-lg p-6">
          <h2 className="text-sm font-bold uppercase tracking-[0.18em]">Next up</h2>
          <ul className="mt-4 space-y-3">
            {events.slice(0, 3).map((e) => (
              <li key={e.id} className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <p className="truncate font-semibold">{e.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {e.date} · {e.time} · {e.world}
                  </p>
                </div>
                <span className="shrink-0 text-[0.72rem] uppercase tracking-[0.18em] text-magenta">
                  {e.type}
                </span>
              </li>
            ))}
          </ul>
          <Link
            to="/portal/events"
            className="mt-5 inline-block text-xs font-bold uppercase tracking-[0.18em] text-primary"
          >
            Manage sign-ups
          </Link>
        </div>
        <div className="panel rounded-lg p-6">
          <h2 className="text-sm font-bold uppercase tracking-[0.18em]">Latest unlocks</h2>
          <ul className="mt-4 space-y-3">
            {achievements
              .filter((a) => a.unlocked)
              .slice(0, 3)
              .map((a) => (
                <li key={a.id} className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <p className="truncate font-semibold">{a.name}</p>
                    <p className="text-xs text-muted-foreground">{a.description}</p>
                  </div>
                  <span className="shrink-0 text-[0.72rem] uppercase tracking-[0.18em] text-electric">
                    {a.rarity}
                  </span>
                </li>
              ))}
          </ul>
          <Link
            to="/portal/achievements"
            className="mt-5 inline-block text-xs font-bold uppercase tracking-[0.18em] text-primary"
          >
            All achievements
          </Link>
        </div>
      </section>
    </div>
  );
}
