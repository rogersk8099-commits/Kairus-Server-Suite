import { createFileRoute } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { useState } from "react";
import { site } from "@/config/site";
import { worldArtwork } from "@/config/visuals";
import { queries } from "@/services/smp";
import { PageHero } from "@/components/site/primitives";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/worlds")({
  head: () => ({
    meta: [
      { title: `Worlds — ${site.name}` },
      {
        name: "description",
        content: `Survival, hardcore, creative, arena and resource worlds on ${site.name}. One account, six maps, zero progress lost.`,
      },
      { property: "og:title", content: `Worlds — ${site.name}` },
      {
        property: "og:description",
        content: `Explore every map in the ${site.name} network.`,
      },
    ],
  }),
  loader: ({ context }) => context.queryClient.ensureQueryData(queries.worlds),
  component: WorldsPage,
});

const filters = ["All", "Survival", "Hardcore", "Creative", "Events", "Resource"] as const;

function WorldsPage() {
  const { data: worlds } = useSuspenseQuery(queries.worlds);
  const [filter, setFilter] = useState<(typeof filters)[number]>("All");

  const visible = filter === "All" ? worlds : worlds.filter((w) => w.type === filter);

  return (
    <div>
      <PageHero
        eyebrow="The map network"
        title="Worlds of"
        accent={site.shortName}
        description="Every world runs on its own node with its own rules, its own economy and its own reset cadence. Your inventory and progress travel with you."
      />

      <section className="mx-auto max-w-7xl px-5 py-14">
        <div className="flex flex-wrap gap-2">
          {filters.map((f) => (
            <button
              key={f}
              type="button"
              onClick={() => setFilter(f)}
              className={cn(
                "rounded-sm border px-4 py-2 text-[0.74rem] font-bold uppercase tracking-[0.18em] transition-colors",
                filter === f
                  ? "border-primary bg-primary/15 text-foreground"
                  : "border-border text-muted-foreground hover:text-foreground",
              )}
            >
              {f}
            </button>
          ))}
        </div>

        <div className="mt-8 grid gap-4 md:grid-cols-2">
          {visible.map((w) => {
            const artwork = worldArtwork[worlds.findIndex((item) => item.id === w.id)];
            return (
              <article key={w.id} className="panel hover-lift rise overflow-hidden rounded-lg">
                <div className="relative aspect-[16/9] overflow-hidden">
                  <img
                    src={artwork}
                    alt={`${w.name} world environment`}
                    width={1280}
                    height={720}
                    loading="lazy"
                    className="h-full w-full object-cover transition-transform duration-500 hover:scale-[1.025]"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-background via-transparent to-background/10" />
                </div>
                <div className="p-7">
                  <div className="grid grid-cols-[minmax(0,1fr)_auto] items-start gap-4">
                    <div className="min-w-0">
                      <p className="text-[0.72rem] font-bold uppercase tracking-[0.3em] text-magenta">
                        {w.type} · {w.season}
                      </p>
                      <h2 className="mt-2 text-2xl font-extrabold tracking-tight sm:text-3xl">
                        {w.name}
                      </h2>
                    </div>
                    <span
                      className={cn(
                        "shrink-0 rounded-full border px-3 py-1 text-[0.72rem] font-bold uppercase tracking-[0.16em]",
                        w.status === "Live"
                          ? "border-primary/50 text-primary"
                          : "border-border text-muted-foreground",
                      )}
                    >
                      {w.status}
                    </span>
                  </div>
                  <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{w.blurb}</p>
                  <dl className="mt-6 grid grid-cols-3 gap-3 border-t border-border/50 pt-5">
                    {[
                      ["Size", w.size],
                      ["Difficulty", w.difficulty],
                      ["Playing", String(w.players)],
                    ].map(([k, v]) => (
                      <div key={k}>
                        <dt className="text-[0.7rem] font-bold uppercase tracking-[0.22em] text-muted-foreground">
                          {k}
                        </dt>
                        <dd className="mt-1 text-sm font-semibold text-foreground">{v}</dd>
                      </div>
                    ))}
                  </dl>
                </div>
              </article>
            );
          })}
        </div>

        {visible.length === 0 ? (
          <p className="panel mt-8 rounded-lg p-10 text-center text-sm text-muted-foreground">
            No worlds in this category right now.
          </p>
        ) : null}
      </section>
    </div>
  );
}
