import { createFileRoute, Link } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { Check, Crown } from "lucide-react";
import { useState } from "react";
import { site } from "@/config/site";
import { queries } from "@/services/smp";
import { PageHero, SectionHeading } from "@/components/site/primitives";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/membership")({
  head: () => ({
    meta: [
      { title: `Membership — ${site.name}` },
      {
        name: "description",
        content: `Support ${site.name} with a monthly membership. Cosmetics, priority queue and creator tooling — never pay-to-win.`,
      },
      { property: "og:title", content: `Membership — ${site.name}` },
      { property: "og:description", content: `Membership tiers for ${site.name}.` },
    ],
  }),
  loader: ({ context }) => context.queryClient.ensureQueryData(queries.tiers),
  component: MembershipPage,
});

const faqs = [
  {
    q: "Is any of this pay-to-win?",
    a: "No. Memberships never grant stats, gear, currency or claim advantages that affect gameplay balance. Priority queue and cosmetics only.",
  },
  {
    q: "Can I cancel any time?",
    a: "Yes. Cancel from the portal in two clicks. Perks stay active until the end of the billing period.",
  },
  {
    q: "Where does the money go?",
    a: "Server hosting across three regions, plugin licensing, event prize pools and the creator revenue share.",
  },
  {
    q: "Do perks carry across seasons?",
    a: "Cosmetics and tags carry forever. Claim bonuses reapply on each new map at the start of every season.",
  },
];

function MembershipPage() {
  const { data: tiers } = useSuspenseQuery(queries.tiers);
  const [annual, setAnnual] = useState(false);

  return (
    <div>
      <PageHero
        eyebrow="Support the server"
        title="Membership"
        accent="tiers"
        description="Three tiers, no loot boxes, no gameplay advantage. Everything you unlock is either convenience or flair."
      >
        <div className="inline-flex items-center gap-1 rounded-sm border border-border p-1">
          {(["Monthly", "Annual · 2 months free"] as const).map((label, i) => (
            <button
              key={label}
              type="button"
              onClick={() => setAnnual(i === 1)}
              className={cn(
                "rounded-sm px-4 py-2 text-[0.72rem] font-bold uppercase tracking-[0.18em] transition-colors",
                annual === (i === 1)
                  ? "bg-primary/20 text-foreground"
                  : "text-muted-foreground hover:text-foreground",
              )}
            >
              {label}
            </button>
          ))}
        </div>
      </PageHero>

      <section className="mx-auto max-w-7xl px-5 py-14">
        <div className="grid gap-4 lg:grid-cols-3">
          {tiers.map((t) => {
            const price = t.price === 0 ? 0 : annual ? t.price * 10 : t.price;
            return (
              <div
                key={t.id}
                className={cn(
                  "panel hover-lift relative rounded-lg p-7",
                  t.featured && "panel-glow border-primary/60",
                )}
              >
                {t.featured ? (
                  <span className="absolute -top-3 left-7 rounded-full bg-gradient-to-r from-magenta to-electric px-3 py-1 text-[0.72rem] font-bold uppercase tracking-[0.18em] text-primary-foreground">
                    Most popular
                  </span>
                ) : null}
                <div className="flex items-center gap-2">
                  {t.id === "legend" ? <Crown className="h-4 w-4 text-magenta" /> : null}
                  <h2 className="text-2xl font-extrabold tracking-tight">{t.name}</h2>
                </div>
                <p className="mt-3">
                  <span className="display-xl gradient-text text-5xl">
                    {price === 0 ? "Free" : `£${price}`}
                  </span>
                  <span className="ml-2 text-xs uppercase tracking-[0.18em] text-muted-foreground">
                    {t.price === 0 ? t.cadence : annual ? "per year" : "per month"}
                  </span>
                </p>
                <p className="mt-3 text-sm text-muted-foreground">{t.blurb}</p>
                <ul className="mt-6 space-y-2.5 text-sm">
                  {t.perks.map((p) => (
                    <li key={p} className="flex gap-2.5 text-muted-foreground">
                      <Check className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
                      {p}
                    </li>
                  ))}
                </ul>
                <Link
                  to="/login"
                  className={cn(
                    "mt-8 block rounded-sm px-4 py-3.5 text-center text-xs font-bold uppercase tracking-[0.18em] transition-transform hover:-translate-y-0.5",
                    t.featured
                      ? "bg-gradient-to-r from-magenta via-violet to-electric text-primary-foreground"
                      : "border border-border text-foreground",
                  )}
                >
                  {t.price === 0 ? "Start playing" : `Choose ${t.name}`}
                </Link>
              </div>
            );
          })}
        </div>
      </section>

      <section className="border-t border-border/60 bg-surface/30 py-16">
        <div className="mx-auto max-w-3xl px-5">
          <SectionHeading
            eyebrow="Before you subscribe"
            title="Common"
            accent="questions"
            align="center"
          />
          <div className="mt-10 space-y-3">
            {faqs.map((f) => (
              <details key={f.q} className="panel group rounded-lg p-5">
                <summary className="cursor-pointer list-none text-sm font-bold uppercase tracking-[0.1em] text-foreground">
                  {f.q}
                </summary>
                <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{f.a}</p>
              </details>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
