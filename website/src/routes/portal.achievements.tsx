import { createFileRoute } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Lock, Trophy } from "lucide-react";
import { queries } from "@/services/smp";
import { StatTile } from "@/components/site/primitives";
import { cn } from "@/lib/utils";
import { site } from "@/config/site";

export const Route = createFileRoute("/portal/achievements")({
  head: () => ({
    meta: [
      { title: `Achievements — ${site.name} portal` },
      {
        name: "description",
        content: `Track unlocked and in-progress ${site.name} achievements, rarities and season milestones.`,
      },
      { property: "og:title", content: `Achievements — ${site.name} portal` },
      { property: "og:description", content: `Your ${site.name} achievement progress.` },
    ],
  }),
  loader: ({ context }) => context.queryClient.ensureQueryData(queries.achievements),
  component: AchievementsPage,
});

const rarityStyles: Record<string, string> = {
  Common: "text-muted-foreground border-border",
  Rare: "text-electric border-electric/50",
  Epic: "text-magenta border-magenta/50",
  Legendary: "text-violet border-violet/60",
};

const filters = ["All", "Unlocked", "In progress"] as const;

function AchievementsPage() {
  const { data: achievements } = useSuspenseQuery(queries.achievements);
  const [filter, setFilter] = useState<(typeof filters)[number]>("All");

  const unlocked = achievements.filter((a) => a.unlocked).length;
  const shown = achievements.filter((a) =>
    filter === "All" ? true : filter === "Unlocked" ? a.unlocked : !a.unlocked,
  );

  return (
    <div className="space-y-8">
      <header>
        <h1 className="display-xl text-4xl">
          Achieve<span className="gradient-text">ments</span>
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          {unlocked} of {achievements.length} unlocked this season.
        </p>
      </header>

      <div className="grid gap-3 sm:grid-cols-3">
        <StatTile label="Unlocked" value={unlocked} />
        <StatTile label="Remaining" value={achievements.length - unlocked} />
        <StatTile
          label="Completion"
          value={`${Math.round((unlocked / achievements.length) * 100)}%`}
        />
      </div>

      <div className="flex flex-wrap gap-2">
        {filters.map((f) => (
          <button
            key={f}
            type="button"
            onClick={() => setFilter(f)}
            className={cn(
              "rounded-sm border px-4 py-2 text-xs font-bold uppercase tracking-[0.16em] transition-colors",
              filter === f
                ? "border-primary bg-primary/15 text-foreground"
                : "border-border text-muted-foreground hover:text-foreground",
            )}
          >
            {f}
          </button>
        ))}
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        {shown.map((a) => (
          <article key={a.id} className={cn("panel rounded-lg p-5", !a.unlocked && "opacity-80")}>
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-3">
                <span
                  className={cn(
                    "grid h-10 w-10 shrink-0 place-items-center rounded-sm border",
                    a.unlocked
                      ? "border-primary/60 bg-primary/10 text-primary"
                      : "border-border text-muted-foreground",
                  )}
                >
                  {a.unlocked ? <Trophy className="h-4 w-4" /> : <Lock className="h-4 w-4" />}
                </span>
                <div>
                  <h2 className="font-bold">{a.name}</h2>
                  <p className="text-xs text-muted-foreground">{a.description}</p>
                </div>
              </div>
              <span
                className={cn(
                  "shrink-0 rounded-sm border px-2 py-1 text-[0.7rem] font-bold uppercase tracking-[0.18em]",
                  rarityStyles[a.rarity],
                )}
              >
                {a.rarity}
              </span>
            </div>

            <div className="mt-4">
              {a.unlocked ? (
                <p className="text-[0.74rem] uppercase tracking-[0.18em] text-magenta">
                  Unlocked {a.unlockedAt}
                </p>
              ) : (
                <>
                  <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full bg-gradient-to-r from-electric to-magenta"
                      style={{ width: `${a.progress ?? 0}%` }}
                    />
                  </div>
                  <p className="mt-2 text-[0.74rem] uppercase tracking-[0.18em] text-muted-foreground">
                    {a.progress ?? 0}% complete
                  </p>
                </>
              )}
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}
