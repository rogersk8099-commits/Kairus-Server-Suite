import { createFileRoute } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Check, CreditCard, Sparkles } from "lucide-react";
import { toast } from "sonner";
import { queries } from "@/services/smp";
import { site } from "@/config/site";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/portal/membership")({
  head: () => ({
    meta: [
      { title: `Membership — ${site.name} portal` },
      {
        name: "description",
        content: `Manage your ${site.name} membership tier, billing history and perks.`,
      },
      { property: "og:title", content: `Membership — ${site.name} portal` },
      { property: "og:description", content: `Manage your ${site.name} membership and billing.` },
    ],
  }),
  loader: ({ context }) =>
    Promise.all([
      context.queryClient.ensureQueryData(queries.tiers),
      context.queryClient.ensureQueryData(queries.portalUser),
    ]),
  component: MembershipPage,
});

const invoices = [
  { id: "i1", date: "01 Nov 2025", amount: "$6.00", status: "Paid" },
  { id: "i2", date: "01 Oct 2025", amount: "$6.00", status: "Paid" },
  { id: "i3", date: "01 Sep 2025", amount: "$6.00", status: "Paid" },
];

function MembershipPage() {
  const { data: tiers } = useSuspenseQuery(queries.tiers);
  const { data: user } = useSuspenseQuery(queries.portalUser);
  const [current, setCurrent] = useState(user.tier);

  return (
    <div className="space-y-8">
      <header>
        <h1 className="display-xl text-4xl">
          Member<span className="gradient-text">ship</span>
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          You're on <span className="text-magenta">{current}</span>. Change tier any time — perks
          apply instantly in game.
        </p>
      </header>

      <div className="grid gap-4 lg:grid-cols-3">
        {tiers.map((t) => {
          const active = t.name === current;
          return (
            <article
              key={t.id}
              className={cn(
                "panel relative flex flex-col rounded-lg p-6",
                active && "border-primary/70 shadow-[0_20px_60px_-30px_var(--violet)]",
              )}
            >
              {active && (
                <span className="absolute right-4 top-4 rounded-sm bg-primary/20 px-2 py-1 text-[0.7rem] font-bold uppercase tracking-[0.18em] text-primary">
                  Current
                </span>
              )}
              <h2 className="text-2xl font-extrabold tracking-tight">{t.name}</h2>
              <p className="mt-1 text-xs text-muted-foreground">{t.blurb}</p>
              <p className="mt-4 text-3xl font-black">
                ${t.price}
                <span className="ml-1 text-xs font-normal text-muted-foreground">{t.cadence}</span>
              </p>
              <ul className="mt-4 flex-1 space-y-2">
                {t.perks.map((p) => (
                  <li key={p} className="flex gap-2 text-xs text-muted-foreground">
                    <Check className="h-3.5 w-3.5 shrink-0 text-electric" /> {p}
                  </li>
                ))}
              </ul>
              <button
                type="button"
                disabled={active}
                onClick={() => {
                  setCurrent(t.name);
                  toast.success(`Switched to ${t.name}`);
                }}
                className={cn(
                  "mt-6 rounded-sm px-4 py-2.5 text-xs font-bold uppercase tracking-[0.16em] transition-transform",
                  active
                    ? "border border-border text-muted-foreground"
                    : "bg-gradient-to-r from-magenta via-violet to-electric text-primary-foreground hover:-translate-y-0.5",
                )}
              >
                {active ? "Active plan" : `Switch to ${t.name}`}
              </button>
            </article>
          );
        })}
      </div>

      <section className="panel rounded-lg p-6">
        <h2 className="flex items-center gap-2 text-sm font-bold uppercase tracking-[0.18em]">
          <CreditCard className="h-4 w-4 text-electric" /> Payment method
        </h2>
        <p className="mt-2 text-sm text-muted-foreground">Visa ending 4429 · expires 08/28</p>
        <button
          type="button"
          onClick={() => toast.info("Billing portal opens once payments are connected")}
          className="mt-4 rounded-sm border border-border px-4 py-2.5 text-xs font-bold uppercase tracking-[0.16em] transition-colors hover:border-accent hover:text-accent"
        >
          Update card
        </button>
      </section>

      <section className="panel rounded-lg p-6">
        <h2 className="flex items-center gap-2 text-sm font-bold uppercase tracking-[0.18em]">
          <Sparkles className="h-4 w-4 text-magenta" /> Billing history
        </h2>
        <ul className="mt-4 divide-y divide-border/60">
          {invoices.map((i) => (
            <li key={i.id} className="flex items-center justify-between gap-2 py-3 text-sm">
              <span>{i.date}</span>
              <span className="font-semibold">{i.amount}</span>
              <span className="text-[0.72rem] uppercase tracking-[0.18em] text-electric">
                {i.status}
              </span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
