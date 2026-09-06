import { createFileRoute } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Check, MessageSquare, ShieldCheck, Unlink } from "lucide-react";
import { toast } from "sonner";
import { queries } from "@/services/smp";
import { site } from "@/config/site";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/portal/discord")({
  head: () => ({
    meta: [
      { title: `Discord linking — ${site.name} portal` },
      {
        name: "description",
        content: `Link your Discord account to ${site.name} to sync roles, event pings and creator channels.`,
      },
      { property: "og:title", content: `Discord linking — ${site.name} portal` },
      { property: "og:description", content: `Connect Discord to your ${site.name} account.` },
    ],
  }),
  loader: ({ context }) => context.queryClient.ensureQueryData(queries.portalUser),
  component: DiscordPage,
});

const roles = ["Member", "Ronin", "Creator Council", "Event Regular", "Ashfall Nation"];

function DiscordPage() {
  const { data: user } = useSuspenseQuery(queries.portalUser);
  const [tag, setTag] = useState<string | null>(user.discordTag);

  return (
    <div className="space-y-8">
      <header>
        <h1 className="display-xl text-4xl">
          Dis<span className="gradient-text">cord</span>
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          Linking syncs your rank, event pings and creator channels automatically.
        </p>
      </header>

      <section className="panel rounded-lg p-6">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <span className="grid h-11 w-11 place-items-center rounded-sm border border-primary/60 bg-primary/10 text-primary">
              <MessageSquare className="h-5 w-5" />
            </span>
            <div>
              <p className="font-bold">{tag ? `@${tag}` : "Not linked yet"}</p>
              <p
                className={cn(
                  "text-[0.72rem] uppercase tracking-[0.18em]",
                  tag ? "text-electric" : "text-muted-foreground",
                )}
              >
                {tag ? "Linked and syncing" : "Link to unlock member channels"}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => {
              if (tag) {
                setTag(null);
                toast.info("Discord unlinked");
              } else {
                setTag(user.discordTag ?? "kajikairu");
                toast.success("Discord linked — roles syncing");
              }
            }}
            className={cn(
              "flex items-center gap-2 rounded-sm px-4 py-2.5 text-xs font-bold uppercase tracking-[0.16em] transition-transform hover:-translate-y-0.5",
              tag
                ? "border border-border text-muted-foreground"
                : "bg-gradient-to-r from-magenta via-violet to-electric text-primary-foreground",
            )}
          >
            {tag ? <Unlink className="h-4 w-4" /> : <MessageSquare className="h-4 w-4" />}
            {tag ? "Unlink" : "Link Discord"}
          </button>
        </div>
      </section>

      <section className="panel rounded-lg p-6">
        <h2 className="text-sm font-bold uppercase tracking-[0.18em]">Synced roles</h2>
        <div className="mt-4 flex flex-wrap gap-2">
          {roles.map((r) => (
            <span
              key={r}
              className={cn(
                "flex items-center gap-1.5 rounded-sm border px-3 py-1.5 text-[0.72rem] font-bold uppercase tracking-[0.18em]",
                tag ? "border-primary/50 text-foreground" : "border-border text-muted-foreground",
              )}
            >
              {tag && <Check className="h-3 w-3 text-electric" />}
              {r}
            </span>
          ))}
        </div>
        {!tag && (
          <p className="mt-4 text-xs text-muted-foreground">
            Roles apply within a minute of linking your account.
          </p>
        )}
      </section>

      <section className="panel rounded-lg p-6">
        <h2 className="flex items-center gap-2 text-sm font-bold uppercase tracking-[0.18em]">
          <ShieldCheck className="h-4 w-4 text-electric" /> Server
        </h2>
        <p className="mt-2 text-sm text-muted-foreground">
          {site.discordName} — announcements, event lobbies, build reviews and creator drops.
        </p>
        <a
          href={site.discordUrl}
          target="_blank"
          rel="noreferrer"
          className="mt-4 inline-flex rounded-sm border border-border px-4 py-2.5 text-xs font-bold uppercase tracking-[0.16em] transition-colors hover:border-accent hover:text-accent"
        >
          Open Discord
        </a>
      </section>
    </div>
  );
}
