import { createFileRoute } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { ArrowDownRight, ArrowUpRight, Minus } from "lucide-react";
import { useMemo, useState } from "react";
import { site } from "@/config/site";
import { queries } from "@/services/smp";
import type { LeaderboardEntry } from "@/data/types";
import { PageHero } from "@/components/site/primitives";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/leaderboards")({
  head: () => ({
    meta: [
      { title: `Leaderboards — ${site.name}` },
      {
        name: "description",
        content: `Season 7 standings on ${site.name}: points, playtime, blocks placed, combat and event wins.`,
      },
      { property: "og:title", content: `Leaderboards — ${site.name}` },
      { property: "og:description", content: `Season 7 standings on ${site.name}.` },
    ],
  }),
  loader: ({ context }) => context.queryClient.ensureQueryData(queries.leaderboard),
  component: LeaderboardPage,
});

const sorts = [
  { key: "points", label: "Season points" },
  { key: "playtimeHours", label: "Playtime" },
  { key: "blocks", label: "Blocks placed" },
  { key: "kills", label: "Combat" },
  { key: "events", label: "Event wins" },
] as const;

const trendIcon = {
  up: <ArrowUpRight className="h-3.5 w-3.5 text-primary" />,
  down: <ArrowDownRight className="h-3.5 w-3.5 text-destructive" />,
  flat: <Minus className="h-3.5 w-3.5 text-muted-foreground" />,
};

function LeaderboardPage() {
  const { data: rows } = useSuspenseQuery(queries.leaderboard);
  const [sort, setSort] = useState<(typeof sorts)[number]["key"]>("points");

  const sorted = useMemo(
    () => [...rows].sort((a, b) => (b[sort] as number) - (a[sort] as number)),
    [rows, sort],
  );

  const podium = sorted.slice(0, 3);

  return (
    <div>
      <PageHero
        eyebrow="Season 7 standings"
        title="The"
        accent="ladder"
        description="Points come from events, milestones and peer nominations. Everything resets when the season does — nobody stays untouchable."
      />

      <section className="mx-auto max-w-7xl px-5 py-14">
        <div className="grid gap-4 md:grid-cols-3">
          {podium.map((p, i) => (
            <div
              key={p.player}
              className={cn(
                "panel hover-lift rounded-lg p-6",
                i === 0 && "panel-glow border-magenta/50 md:-translate-y-2",
              )}
            >
              <div className="flex items-center justify-between">
                <span className="display-xl text-5xl gradient-text">{i + 1}</span>
                {trendIcon[p.trend]}
              </div>
              <h2 className="mt-3 text-2xl font-extrabold tracking-tight">{p.player}</h2>
              <p className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{p.guild}</p>
              <dl className="mt-5 grid grid-cols-3 gap-2 border-t border-border/50 pt-4 text-center">
                {(
                  [
                    ["Pts", p.points.toLocaleString()],
                    ["Hrs", p.playtimeHours],
                    ["Wins", p.events],
                  ] as const
                ).map(([k, v]) => (
                  <div key={k}>
                    <dt className="text-[0.7rem] uppercase tracking-[0.18em] text-muted-foreground">
                      {k}
                    </dt>
                    <dd className="mt-1 text-sm font-bold">{v}</dd>
                  </div>
                ))}
              </dl>
            </div>
          ))}
        </div>

        <div className="mt-10 flex flex-wrap gap-2">
          {sorts.map((s) => (
            <button
              key={s.key}
              type="button"
              onClick={() => setSort(s.key)}
              className={cn(
                "rounded-sm border px-4 py-2 text-[0.72rem] font-bold uppercase tracking-[0.18em] transition-colors",
                sort === s.key
                  ? "border-primary bg-primary/15 text-foreground"
                  : "border-border text-muted-foreground hover:text-foreground",
              )}
            >
              {s.label}
            </button>
          ))}
        </div>

        <div className="panel mt-5 overflow-hidden rounded-lg">
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead>
                <tr className="border-b border-border/60 text-[0.7rem] uppercase tracking-[0.22em] text-muted-foreground">
                  <th className="px-5 py-3">#</th>
                  <th className="px-5 py-3">Player</th>
                  <th className="px-5 py-3">Guild</th>
                  <th className="px-5 py-3">Hours</th>
                  <th className="px-5 py-3">Blocks</th>
                  <th className="px-5 py-3">Kills</th>
                  <th className="px-5 py-3">Wins</th>
                  <th className="px-5 py-3 text-right">Points</th>
                </tr>
              </thead>
              <tbody>
                {sorted.map((row: LeaderboardEntry, i) => (
                  <tr
                    key={row.player}
                    className="border-b border-border/40 transition-colors last:border-0 hover:bg-primary/5"
                  >
                    <td className="px-5 py-3 font-mono text-magenta">
                      {String(i + 1).padStart(2, "0")}
                    </td>
                    <td className="px-5 py-3 font-semibold text-foreground">
                      <span className="inline-flex items-center gap-2">
                        {row.player} {trendIcon[row.trend]}
                      </span>
                    </td>
                    <td className="px-5 py-3 text-muted-foreground">{row.guild}</td>
                    <td className="px-5 py-3 text-muted-foreground">{row.playtimeHours}</td>
                    <td className="px-5 py-3 text-muted-foreground">
                      {row.blocks.toLocaleString()}
                    </td>
                    <td className="px-5 py-3 text-muted-foreground">{row.kills}</td>
                    <td className="px-5 py-3 text-muted-foreground">{row.events}</td>
                    <td className="px-5 py-3 text-right font-mono text-foreground">
                      {row.points.toLocaleString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </section>
    </div>
  );
}
