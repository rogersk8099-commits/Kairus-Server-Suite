import { createFileRoute } from "@tanstack/react-router";
import { site } from "@/config/site";
import { communityPillars } from "@/data/mock";
import { PageHero, SectionHeading, StatTile } from "@/components/site/primitives";

export const Route = createFileRoute("/community")({
  head: () => ({
    meta: [
      { title: `Community & Creator Programme — ${site.name}` },
      {
        name: "description",
        content: `Guilds, moderation, and the ${site.name} creator programme: promo slots, capture worlds and revenue share.`,
      },
      { property: "og:title", content: `Community & Creator Programme — ${site.name}` },
      {
        property: "og:description",
        content: `How the ${site.name} community and creator programme work.`,
      },
    ],
  }),
  component: CommunityPage,
});

const creatorPerks = [
  {
    title: "Promo slots",
    body: "Featured placement on the /live page and a push to 14,200 Discord members.",
  },
  {
    title: "Capture world",
    body: "Private cinematic world with WorldEdit, camera paths and custom shader presets.",
  },
  {
    title: "Revenue share",
    body: "20% of membership sign-ups attributed to your link, paid monthly.",
  },
  {
    title: "Event priority",
    body: "Guaranteed slot in every tournament plus a co-caster seat if you want it.",
  },
];

const requirements = [
  ["Followers", "1,000+ on any single platform"],
  ["Cadence", "Two SMP streams or videos a month"],
  ["Standing", "Clean moderation record for 60 days"],
  ["Review", "Applications reviewed on the 1st of each month"],
];

const guilds = [
  { name: "Spire", focus: "Megabuilds and terraforming", members: 42 },
  { name: "Ronin", focus: "PvP and event dominance", members: 38 },
  { name: "Circuit", focus: "Redstone, farms and logistics", members: 27 },
  { name: "Concord", focus: "Trade, diplomacy and economy", members: 51 },
  { name: "Hollow", focus: "Hardcore and exploration", members: 19 },
];

function CommunityPage() {
  return (
    <div>
      <PageHero
        eyebrow="People first"
        title="Community &"
        accent="creators"
        description="The server exists because of the guilds, moderators and creators who run it. Here's how to plug in — whatever your play style."
      />

      <section className="mx-auto max-w-7xl px-5 py-14">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StatTile label="Discord members" value="14,200" />
          <StatTile label="Active guilds" value="5" hint="Open recruitment" />
          <StatTile label="Partnered creators" value="38" />
          <StatTile label="Avg. report response" value="8 min" />
        </div>

        <div className="mt-16">
          <SectionHeading eyebrow="Why people stay" title="How this place" accent="runs" />
          <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {communityPillars.map((p) => (
              <div key={p.title} className="panel hover-lift rounded-lg p-6">
                <h3 className="text-base font-bold uppercase tracking-[0.1em]">{p.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{p.body}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="mt-16">
          <SectionHeading eyebrow="Find your people" title="Active" accent="guilds" />
          <div className="panel mt-8 overflow-hidden rounded-lg">
            {guilds.map((g) => (
              <div
                key={g.name}
                className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-4 border-b border-border/40 px-5 py-4 last:border-0"
              >
                <div className="min-w-0">
                  <p className="font-bold uppercase tracking-[0.12em]">{g.name}</p>
                  <p className="text-sm text-muted-foreground">{g.focus}</p>
                </div>
                <span className="shrink-0 text-xs uppercase tracking-[0.18em] text-muted-foreground">
                  {g.members} members
                </span>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="border-t border-border/60 bg-surface/30 py-16">
        <div className="mx-auto max-w-7xl px-5">
          <SectionHeading
            eyebrow="Creator programme"
            title="Stream the"
            accent="SMP"
            description="Built for creators of any size who want a server that actively promotes them back."
          />
          <div className="mt-10 grid gap-10 lg:grid-cols-[1.2fr_0.8fr]">
            <div className="grid gap-4 sm:grid-cols-2">
              {creatorPerks.map((p) => (
                <div key={p.title} className="panel hover-lift rounded-lg p-6">
                  <h3 className="text-sm font-bold uppercase tracking-[0.12em]">{p.title}</h3>
                  <p className="mt-2 text-sm text-muted-foreground">{p.body}</p>
                </div>
              ))}
            </div>
            <div>
              <div className="panel rounded-lg">
                {requirements.map(([k, v]) => (
                  <div
                    key={k}
                    className="grid grid-cols-[minmax(0,0.5fr)_minmax(0,1fr)] gap-4 border-b border-border/40 px-5 py-4 last:border-0"
                  >
                    <span className="text-[0.72rem] font-bold uppercase tracking-[0.18em] text-muted-foreground">
                      {k}
                    </span>
                    <span className="text-sm">{v}</span>
                  </div>
                ))}
              </div>
              <a
                href={site.discordUrl}
                target="_blank"
                rel="noreferrer"
                className="mt-4 block rounded-sm bg-gradient-to-r from-magenta via-violet to-electric px-6 py-3.5 text-center text-xs font-bold uppercase tracking-[0.18em] text-primary-foreground"
              >
                Apply as a creator
              </a>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
