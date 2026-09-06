import { createFileRoute } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { Radio, Search } from "lucide-react";
import { useMemo, useState } from "react";
import { site } from "@/config/site";
import { queries } from "@/services/smp";
import { LiveBadge, PageHero, StatTile } from "@/components/site/primitives";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/live")({
  head: () => ({
    meta: [
      { title: `Live streams — ${site.name}` },
      {
        name: "description",
        content: `Every ${site.name} creator streaming right now on TikTok, Twitch and YouTube, plus the schedule for the week.`,
      },
      { property: "og:title", content: `Live streams — ${site.name}` },
      {
        property: "og:description",
        content: `Watch ${site.name} creators live across TikTok, Twitch and YouTube.`,
      },
    ],
  }),
  loader: ({ context }) => context.queryClient.ensureQueryData(queries.streams),
  component: LivePage,
});

const platforms = ["All", "TikTok", "Twitch", "YouTube"] as const;
const statuses = ["All", "Live now", "Scheduled"] as const;

function LivePage() {
  const { data: streams } = useSuspenseQuery(queries.streams);
  const [platform, setPlatform] = useState<(typeof platforms)[number]>("All");
  const [status, setStatus] = useState<(typeof statuses)[number]>("All");
  const [q, setQ] = useState("");

  const visible = useMemo(
    () =>
      streams.filter((s) => {
        if (platform !== "All" && s.platform !== platform) return false;
        if (status === "Live now" && !s.live) return false;
        if (status === "Scheduled" && s.live) return false;
        if (q && !`${s.creator} ${s.title} ${s.category}`.toLowerCase().includes(q.toLowerCase()))
          return false;
        return true;
      }),
    [streams, platform, status, q],
  );

  const liveCount = streams.filter((s) => s.live).length;
  const viewers = streams.reduce((sum, s) => sum + s.viewers, 0);

  return (
    <div>
      <PageHero
        eyebrow="Creators on air"
        title="Live"
        accent="channels"
        description={`${site.name} creators broadcast the server every day. Filter by platform, catch a raid, or check who's scheduled this week.`}
      />

      <section className="mx-auto max-w-7xl px-5 py-14">
        <div className="grid gap-3 sm:grid-cols-3">
          <StatTile label="Live now" value={liveCount} hint="Across three platforms" />
          <StatTile label="Combined viewers" value={viewers.toLocaleString()} />
          <StatTile label="Partnered creators" value="38" hint="Legend tier and above" />
        </div>

        <div className="panel mt-8 grid gap-4 rounded-lg p-5 lg:grid-cols-[minmax(0,1fr)_auto]">
          <label className="flex min-w-0 items-center gap-3 rounded-sm border border-border bg-background/60 px-4 py-2.5">
            <Search className="h-4 w-4 shrink-0 text-muted-foreground" />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search creators, titles or categories"
              className="w-full bg-transparent text-sm outline-none placeholder:text-muted-foreground"
            />
          </label>
          <div className="flex flex-wrap gap-2">
            {platforms.map((p) => (
              <button
                key={p}
                type="button"
                onClick={() => setPlatform(p)}
                className={cn(
                  "rounded-sm border px-3.5 py-2 text-[0.72rem] font-bold uppercase tracking-[0.18em] transition-colors",
                  platform === p
                    ? "border-primary bg-primary/15 text-foreground"
                    : "border-border text-muted-foreground hover:text-foreground",
                )}
              >
                {p}
              </button>
            ))}
          </div>
          <div className="flex flex-wrap gap-2 lg:col-span-2">
            {statuses.map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => setStatus(s)}
                className={cn(
                  "rounded-sm border px-3.5 py-2 text-[0.72rem] font-bold uppercase tracking-[0.18em] transition-colors",
                  status === s
                    ? "border-magenta bg-magenta/15 text-foreground"
                    : "border-border text-muted-foreground hover:text-foreground",
                )}
              >
                {s}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-8 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {visible.map((s) => (
            <article key={s.id} className="panel hover-lift rise rounded-lg p-6">
              <div className="flex items-center justify-between gap-3">
                {s.live ? (
                  <LiveBadge />
                ) : (
                  <span className="rounded-full border border-border px-3 py-1 text-[0.72rem] font-bold uppercase tracking-[0.18em] text-muted-foreground">
                    {s.scheduledFor}
                  </span>
                )}
                <span className="text-[0.72rem] font-bold uppercase tracking-[0.18em] text-muted-foreground">
                  {s.platform}
                </span>
              </div>
              <h2 className="mt-4 text-lg font-bold leading-snug">{s.title}</h2>
              <p className="mt-2 text-sm text-muted-foreground">
                {s.creator} · {s.handle}
              </p>
              <div className="mt-5 flex items-center justify-between border-t border-border/50 pt-4 text-xs uppercase tracking-[0.16em] text-muted-foreground">
                <span>{s.category}</span>
                {s.live ? (
                  <span className="inline-flex items-center gap-1.5 text-foreground">
                    <Radio className="h-3.5 w-3.5 text-magenta" />
                    {s.viewers.toLocaleString()}
                  </span>
                ) : (
                  <span>Reminder available</span>
                )}
              </div>
            </article>
          ))}
        </div>

        {visible.length === 0 ? (
          <p className="panel mt-8 rounded-lg p-12 text-center text-sm text-muted-foreground">
            Nothing matches those filters. Try clearing the search.
          </p>
        ) : null}
      </section>
    </div>
  );
}
