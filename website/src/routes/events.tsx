import { createFileRoute } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { site } from "@/config/site";
import { queries } from "@/services/smp";
import { PageHero, SectionHeading } from "@/components/site/primitives";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/events")({
  head: () => ({
    meta: [
      { title: `Events — ${site.name}` },
      {
        name: "description",
        content: `Tournaments, build battles, summits and seasonal drops on ${site.name}. Sign-ups open every Monday.`,
      },
      { property: "og:title", content: `Events — ${site.name}` },
      { property: "og:description", content: `The ${site.name} event calendar.` },
    ],
  }),
  loader: ({ context }) => context.queryClient.ensureQueryData(queries.events),
  component: EventsPage,
});

const types = ["All", "PvP", "Build", "Community", "Seasonal"] as const;

const past = [
  { name: "The Gauntlet — Round 11", winner: "Ronin", date: "31 Oct", entrants: 64 },
  { name: "Spooky Build Battle", winner: "Kilnwork", date: "26 Oct", entrants: 40 },
  { name: "Quarry Speedmine", winner: "Voltcrafter", date: "18 Oct", entrants: 92 },
  { name: "Ashfall Border War II", winner: "Concord", date: "11 Oct", entrants: 148 },
];

function EventsPage() {
  const { data: events } = useSuspenseQuery(queries.events);
  const [type, setType] = useState<(typeof types)[number]>("All");

  const visible = type === "All" ? events : events.filter((e) => e.type === type);

  return (
    <div>
      <PageHero
        eyebrow="Season 7 calendar"
        title="Events &"
        accent="tournaments"
        description="Something scheduled every weekend, plus surprise drops during the week. Sign-ups open to all members, 24 hours early for Ronin and Legend."
      />

      <section className="mx-auto max-w-7xl px-5 py-14">
        <div className="flex flex-wrap gap-2">
          {types.map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => setType(t)}
              className={cn(
                "rounded-sm border px-4 py-2 text-[0.74rem] font-bold uppercase tracking-[0.18em] transition-colors",
                type === t
                  ? "border-primary bg-primary/15 text-foreground"
                  : "border-border text-muted-foreground hover:text-foreground",
              )}
            >
              {t}
            </button>
          ))}
        </div>

        <div className="mt-8 space-y-4">
          {visible.map((e) => (
            <article key={e.id} className="panel hover-lift rise rounded-lg p-6 sm:p-7">
              <div className="grid gap-6 lg:grid-cols-[auto_minmax(0,1fr)_auto] lg:items-center">
                <div className="w-fit rounded-sm border border-primary/40 bg-primary/10 px-4 py-3 text-center">
                  <p className="text-xl font-extrabold tracking-tight text-foreground">{e.date}</p>
                  <p className="mt-1 text-[0.72rem] uppercase tracking-[0.18em] text-muted-foreground">
                    {e.time}
                  </p>
                </div>
                <div className="min-w-0">
                  <p className="text-[0.72rem] font-bold uppercase tracking-[0.3em] text-magenta">
                    {e.type} · {e.world}
                  </p>
                  <h2 className="mt-1.5 text-2xl font-extrabold leading-snug tracking-tight">
                    {e.name}
                  </h2>
                  <p className="mt-2 text-sm text-muted-foreground">{e.blurb}</p>
                  <div className="mt-4 max-w-md">
                    <div className="h-1 w-full overflow-hidden rounded-full bg-muted">
                      <div
                        className="h-full rounded-full bg-gradient-to-r from-magenta to-electric"
                        style={{ width: `${(e.slotsTaken / e.slots) * 100}%` }}
                      />
                    </div>
                    <p className="mt-2 text-[0.75rem] uppercase tracking-[0.16em] text-muted-foreground">
                      {e.slotsTaken}/{e.slots} slots · Prize {e.prize}
                    </p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => toast.success(`You're on the list for ${e.name}`)}
                  className="rounded-sm bg-gradient-to-r from-magenta via-violet to-electric px-6 py-3 text-xs font-bold uppercase tracking-[0.18em] text-primary-foreground transition-transform hover:-translate-y-0.5"
                >
                  Sign up
                </button>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="border-t border-border/60 bg-surface/30 py-16">
        <div className="mx-auto max-w-7xl px-5">
          <SectionHeading eyebrow="Archive" title="Recent" accent="results" />
          <div className="panel mt-8 overflow-hidden rounded-lg">
            {past.map((p) => (
              <div
                key={p.name}
                className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-4 border-b border-border/40 px-5 py-4 last:border-0 sm:grid-cols-[auto_minmax(0,1fr)_auto_auto]"
              >
                <span className="text-xs uppercase tracking-[0.18em] text-muted-foreground">
                  {p.date}
                </span>
                <span className="truncate font-semibold">{p.name}</span>
                <span className="hidden text-sm text-muted-foreground sm:block">
                  {p.entrants} entrants
                </span>
                <span className="rounded-sm border border-magenta/40 px-3 py-1 text-[0.72rem] font-bold uppercase tracking-[0.16em] text-magenta">
                  {p.winner}
                </span>
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
