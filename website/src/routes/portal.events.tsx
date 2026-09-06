import { createFileRoute } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { useState } from "react";
import { CalendarDays, MapPin, Trophy } from "lucide-react";
import { toast } from "sonner";
import { queries } from "@/services/smp";
import { StatTile } from "@/components/site/primitives";
import { site } from "@/config/site";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/portal/events")({
  head: () => ({
    meta: [
      { title: `My events — ${site.name} portal` },
      {
        name: "description",
        content: `Manage your ${site.name} event sign-ups, tournament slots and past results.`,
      },
      { property: "og:title", content: `My events — ${site.name} portal` },
      { property: "og:description", content: `Your ${site.name} event sign-ups and results.` },
    ],
  }),
  loader: ({ context }) => context.queryClient.ensureQueryData(queries.events),
  component: MyEventsPage,
});

const pastResults = [
  { id: "p1", name: "The Gauntlet — Round 11", placement: "2nd of 64", reward: "6,000 shards" },
  { id: "p2", name: "Build Battle: Deep Vaults", placement: "Finalist", reward: "Spawn plot" },
  { id: "p3", name: "Sky Duels Ladder", placement: "1st of 32", reward: "Legend tag" },
];

function MyEventsPage() {
  const { data: events } = useSuspenseQuery(queries.events);
  const [signedUp, setSignedUp] = useState<string[]>(["e1", "e4"]);

  const toggle = (id: string, name: string) => {
    setSignedUp((prev) => {
      const has = prev.includes(id);
      toast[has ? "info" : "success"](has ? `Left ${name}` : `You're in for ${name}`);
      return has ? prev.filter((x) => x !== id) : [...prev, id];
    });
  };

  return (
    <div className="space-y-8">
      <header>
        <h1 className="display-xl text-4xl">
          My <span className="gradient-text">events</span>
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Reserve your slot, drop out, and review how past runs went.
        </p>
      </header>

      <div className="grid gap-3 sm:grid-cols-3">
        <StatTile label="Signed up" value={signedUp.length} />
        <StatTile label="Events won" value={6} />
        <StatTile label="Podiums" value={11} />
      </div>

      <section className="space-y-3">
        <h2 className="text-sm font-bold uppercase tracking-[0.18em]">Upcoming</h2>
        {events.map((e) => {
          const joined = signedUp.includes(e.id);
          const pct = Math.round((e.slotsTaken / e.slots) * 100);
          return (
            <article key={e.id} className="panel rounded-lg p-5">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="min-w-0">
                  <span className="text-[0.7rem] font-bold uppercase tracking-[0.22em] text-magenta">
                    {e.type}
                  </span>
                  <h3 className="text-xl font-extrabold leading-snug tracking-tight">{e.name}</h3>
                  <p className="mt-1 text-xs text-muted-foreground">{e.blurb}</p>
                  <div className="mt-3 flex flex-wrap gap-4 text-[0.74rem] uppercase tracking-[0.16em] text-muted-foreground">
                    <span className="flex items-center gap-1.5">
                      <CalendarDays className="h-3.5 w-3.5" /> {e.date} · {e.time}
                    </span>
                    <span className="flex items-center gap-1.5">
                      <MapPin className="h-3.5 w-3.5" /> {e.world}
                    </span>
                    <span className="flex items-center gap-1.5">
                      <Trophy className="h-3.5 w-3.5" /> {e.prize}
                    </span>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => toggle(e.id, e.name)}
                  className={cn(
                    "shrink-0 rounded-sm px-4 py-2.5 text-xs font-bold uppercase tracking-[0.16em] transition-transform hover:-translate-y-0.5",
                    joined
                      ? "border border-border text-muted-foreground"
                      : "bg-gradient-to-r from-magenta via-violet to-electric text-primary-foreground",
                  )}
                >
                  {joined ? "Leave" : "Sign up"}
                </button>
              </div>
              <div className="mt-4 h-1.5 w-full overflow-hidden rounded-full bg-muted">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-electric to-magenta"
                  style={{ width: `${pct}%` }}
                />
              </div>
              <p className="mt-2 text-[0.72rem] uppercase tracking-[0.18em] text-muted-foreground">
                {e.slotsTaken} / {e.slots} slots taken
              </p>
            </article>
          );
        })}
      </section>

      <section className="panel rounded-lg p-6">
        <h2 className="text-sm font-bold uppercase tracking-[0.18em]">Past results</h2>
        <ul className="mt-4 divide-y divide-border/60">
          {pastResults.map((r) => (
            <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 py-3">
              <span className="font-semibold">{r.name}</span>
              <span className="text-xs uppercase tracking-[0.16em] text-electric">
                {r.placement}
              </span>
              <span className="text-xs text-muted-foreground">{r.reward}</span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
