import { createFileRoute } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { site } from "@/config/site";
import { queries } from "@/services/smp";
import { CopyIp } from "@/components/site/copy-ip";
import { PageHero, SectionHeading, StatTile } from "@/components/site/primitives";

export const Route = createFileRoute("/play")({
  head: () => ({
    meta: [
      { title: `How to join — ${site.name}` },
      {
        name: "description",
        content: `Connect to ${site.name} on Java or Bedrock in under two minutes. Whitelist steps, rules and server addresses.`,
      },
      { property: "og:title", content: `How to join — ${site.name}` },
      {
        property: "og:description",
        content: `Server addresses, whitelist steps and rules for ${site.name}.`,
      },
    ],
  }),
  loader: ({ context }) => context.queryClient.ensureQueryData(queries.serverStatus),
  component: PlayPage,
});

const steps = [
  {
    n: "01",
    title: "Join the Discord",
    body: "Whitelisting runs through Discord so we can verify accounts and keep alt farms out.",
  },
  {
    n: "02",
    title: "Run /apply",
    body: "Answer four short questions in #whitelist. Most applications are reviewed within an hour.",
  },
  {
    n: "03",
    title: "Link your account",
    body: "The bot links your Minecraft UUID to your portal profile so stats start tracking immediately.",
  },
  {
    n: "04",
    title: "Add the server",
    body: "Copy the address below, add it in Minecraft multiplayer, and pick your starting world.",
  },
];

const rules = [
  {
    title: "No griefing, ever",
    body: "Claims are enforced by plugin and by staff. Rollbacks are free and fast.",
  },
  {
    title: "One account per human",
    body: "Alts are allowed only for creators with an approved capture account.",
  },
  {
    title: "Keep chat playable",
    body: "Banter is fine. Harassment, slurs and doxxing are instant permanent bans.",
  },
  {
    title: "No cheat clients",
    body: "Anti-cheat plus manual review. Fly, reach, x-ray and macro farms are out.",
  },
  {
    title: "Respect the map",
    body: "Mine bulk resources in The Quarry, not in Ashfall's protected biomes.",
  },
  {
    title: "Report, don't retaliate",
    body: "Use /report in game. Staff respond in under ten minutes on average.",
  },
];

const requirements = [
  ["Edition", "Java 1.21.x or Bedrock 1.21.x"],
  ["Account", "Legitimate Microsoft account"],
  ["Age", "13+ (16+ for voice events)"],
  ["Mods", "Vanilla-friendly. Optifine, Sodium, Lunar and Litematica allowed."],
];

function PlayPage() {
  const { data: status } = useSuspenseQuery(queries.serverStatus);

  return (
    <div>
      <PageHero
        eyebrow="Getting started"
        title="Play the"
        accent="server"
        description={`Whitelist to keep the community healthy, crossplay so nobody gets left out. Here's everything you need to be online tonight.`}
      >
        <div className="grid gap-3 sm:max-w-xl sm:grid-cols-2">
          <CopyIp label="Java address" value={site.javaIp} />
          <CopyIp label="Bedrock address" value={`${site.bedrockIp}:${site.bedrockPort}`} />
        </div>
      </PageHero>

      <section className="mx-auto max-w-7xl px-5 py-16">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <StatTile
            label="Status"
            value={status.online ? "Online" : "Down"}
            hint={`Restart ${status.lastRestart}`}
          />
          <StatTile label="Players" value={`${status.players}/${status.maxPlayers}`} />
          <StatTile label="Version" value={status.version} />
          <StatTile label="Whitelist" value="Open" hint="Reviewed hourly" />
        </div>
      </section>

      <section className="border-y border-border/60 bg-surface/30 py-16">
        <div className="mx-auto max-w-7xl px-5">
          <SectionHeading eyebrow="Four steps" title="From zero to" accent="spawn" />
          <div className="mt-10 grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            {steps.map((s) => (
              <div key={s.n} className="panel hover-lift rounded-lg p-6">
                <span className="display-xl text-4xl gradient-text">{s.n}</span>
                <h3 className="mt-3 text-base font-bold uppercase tracking-[0.1em]">{s.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{s.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-7xl px-5 py-16">
        <div className="grid gap-10 lg:grid-cols-[1.2fr_0.8fr]">
          <div>
            <SectionHeading eyebrow="House rules" title="Six rules," accent="strictly enforced" />
            <div className="mt-8 grid gap-3 sm:grid-cols-2">
              {rules.map((r) => (
                <div key={r.title} className="panel rounded-lg p-5">
                  <h3 className="text-sm font-bold uppercase tracking-[0.12em] text-foreground">
                    {r.title}
                  </h3>
                  <p className="mt-2 text-sm text-muted-foreground">{r.body}</p>
                </div>
              ))}
            </div>
          </div>
          <div>
            <SectionHeading eyebrow="Before you connect" title="Requirements" />
            <div className="panel mt-8 rounded-lg">
              {requirements.map(([k, v]) => (
                <div
                  key={k}
                  className="grid grid-cols-[minmax(0,0.5fr)_minmax(0,1fr)] gap-4 border-b border-border/40 px-5 py-4 last:border-0"
                >
                  <span className="text-[0.72rem] font-bold uppercase tracking-[0.18em] text-muted-foreground">
                    {k}
                  </span>
                  <span className="text-sm text-foreground">{v}</span>
                </div>
              ))}
            </div>
            <a
              href={site.discordUrl}
              target="_blank"
              rel="noreferrer"
              className="mt-4 block rounded-sm bg-gradient-to-r from-magenta via-violet to-electric px-6 py-3.5 text-center text-xs font-bold uppercase tracking-[0.18em] text-primary-foreground"
            >
              Apply in Discord
            </a>
          </div>
        </div>
      </section>
    </div>
  );
}
