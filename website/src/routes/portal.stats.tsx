import { createFileRoute } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { queries } from "@/services/smp";
import { StatTile } from "@/components/site/primitives";

export const Route = createFileRoute("/portal/stats")({
  loader: ({ context }) => context.queryClient.ensureQueryData(queries.minecraftStats),
  component: StatsPage,
});

function StatsPage() {
  const { data: s } = useSuspenseQuery(queries.minecraftStats);
  const peak = Math.max(...s.weekly.map((d) => d.hours));
  const kd = (s.mobKills / Math.max(s.deaths, 1)).toFixed(1);

  return (
    <div className="space-y-8">
      <header>
        <h1 className="display-xl text-4xl">
          Minecraft <span className="gradient-text">stats</span>
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Synced from the server every five minutes. Season 7 totals.
        </p>
      </header>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile label="Playtime" value={`${s.playtimeHours}h`} />
        <StatTile label="Blocks placed" value={s.blocksPlaced.toLocaleString()} />
        <StatTile label="Blocks mined" value={s.blocksMined.toLocaleString()} />
        <StatTile label="Distance" value={`${s.distanceKm.toLocaleString()} km`} />
        <StatTile label="Mob kills" value={s.mobKills.toLocaleString()} />
        <StatTile label="Deaths" value={s.deaths} />
        <StatTile label="K/D" value={kd} />
        <StatTile label="Events won" value={s.eventsWon} />
      </div>

      <section className="panel rounded-lg p-6">
        <h2 className="text-sm font-bold uppercase tracking-[0.18em]">Hours played, last 7 days</h2>
        <div className="mt-6 flex items-end gap-3">
          {s.weekly.map((d) => (
            <div key={d.day} className="flex flex-1 flex-col items-center gap-2">
              <span className="text-[0.72rem] text-muted-foreground">{d.hours}h</span>
              <div
                className="w-full rounded-t-sm bg-gradient-to-t from-electric to-magenta"
                style={{ height: `${(d.hours / peak) * 140}px` }}
              />
              <span className="text-[0.72rem] uppercase tracking-[0.16em] text-muted-foreground">
                {d.day}
              </span>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
