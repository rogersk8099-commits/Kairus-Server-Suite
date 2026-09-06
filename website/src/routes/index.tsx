import { createFileRoute, Link } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import {
  ArrowRight,
  CalendarDays,
  Crown,
  Flame,
  Gamepad2,
  MessageSquare,
  Radio,
  Shield,
  Swords,
  Trophy,
  Users,
} from "lucide-react";
import { site } from "@/config/site";
import { visuals, worldArtwork } from "@/config/visuals";
import { queries } from "@/services/smp";
import { communityPillars } from "@/data/mock";
import { CopyIp } from "@/components/site/copy-ip";
import {
  Atmosphere,
  CornerFrame,
  Eyebrow,
  LiveBadge,
  SectionHeading,
  StatTile,
  XMarks,
} from "@/components/site/primitives";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: `${site.name} — ${site.tagline}` },
      { name: "description", content: site.description },
      { property: "og:title", content: `${site.name} — ${site.tagline}` },
      { property: "og:description", content: site.description },
    ],
  }),
  loader: async ({ context }) => {
    await Promise.all([
      context.queryClient.ensureQueryData(queries.serverStatus),
      context.queryClient.ensureQueryData(queries.worlds),
      context.queryClient.ensureQueryData(queries.streams),
      context.queryClient.ensureQueryData(queries.events),
      context.queryClient.ensureQueryData(queries.leaderboard),
      context.queryClient.ensureQueryData(queries.tiers),
    ]);
  },
  component: Home,
});

const accentRing: Record<string, string> = {
  magenta: "from-magenta/60",
  violet: "from-violet/60",
  electric: "from-electric/60",
};

function Home() {
  const { data: status } = useSuspenseQuery(queries.serverStatus);
  const { data: worlds } = useSuspenseQuery(queries.worlds);
  const { data: streams } = useSuspenseQuery(queries.streams);
  const { data: events } = useSuspenseQuery(queries.events);
  const { data: leaderboard } = useSuspenseQuery(queries.leaderboard);
  const { data: tiers } = useSuspenseQuery(queries.tiers);

  const liveStreams = streams.filter((s) => s.live);

  return (
    <div>
      {/* HERO */}
      <section className="relative overflow-hidden">
        <Atmosphere />
        <XMarks />
        <CornerFrame className="hidden sm:block" />
        <div className="relative mx-auto grid max-w-7xl items-center gap-10 px-5 py-16 lg:grid-cols-[1.05fr_0.95fr] lg:py-24">
          <div className="rise">
            <div className="flex flex-wrap items-center gap-3">
              <LiveBadge label={`${status.players} online`} />
              <span className="text-[0.74rem] font-semibold uppercase tracking-[0.3em] text-muted-foreground">
                Season 7 · Java + Bedrock · {site.version}
              </span>
            </div>
            <h1 className="display-xl mt-6 text-6xl sm:text-8xl">
              <span className="block text-foreground">Build.</span>
              <span className="gradient-text neon-text block">Survive.</span>
              <span className="block text-foreground">Dominate.</span>
            </h1>
            <p className="mt-6 max-w-xl text-base font-semibold uppercase leading-relaxed tracking-[0.12em] text-muted-foreground">
              A cross-platform Minecraft community for Java &amp; Bedrock players. Your world. Your
              story.
            </p>
            <div className="mt-8 grid gap-3 sm:max-w-lg sm:grid-cols-2">
              <CopyIp label="Java" value={site.javaIp} />
              <CopyIp label="Bedrock" value={`${site.bedrockIp}:${site.bedrockPort}`} />
            </div>
            <div className="mt-6 flex flex-wrap gap-3">
              <Link
                to="/play"
                className="inline-flex items-center gap-2 rounded-sm bg-gradient-to-r from-magenta via-violet to-electric px-6 py-3 text-xs font-bold uppercase tracking-[0.18em] text-primary-foreground shadow-[0_18px_40px_-18px_var(--magenta)] transition-transform hover:-translate-y-0.5"
              >
                Play now <ArrowRight className="h-4 w-4" />
              </Link>
              <a
                href={site.discordUrl}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-2 rounded-sm border border-border px-6 py-3 text-xs font-bold uppercase tracking-[0.18em] transition-colors hover:border-accent hover:text-accent"
              >
                <MessageSquare className="h-4 w-4" /> Join Discord
              </a>
            </div>
          </div>

          <div className="relative rise [animation-delay:120ms]">
            <div className="pointer-events-none absolute -inset-10 rounded-full bg-[radial-gradient(circle,color-mix(in_oklab,var(--violet)_34%,transparent),transparent_68%)] blur-3xl" />
            <div className="relative mx-auto max-w-md border border-violet/30 bg-background/50 p-2 shadow-[0_0_80px_-24px_var(--violet)]">
              <img
                src={visuals.hero}
                alt="Neon-lit voxel warrior emerging from purple and blue smoke"
                width={1024}
                height={1280}
                className="aspect-[4/5] w-full object-cover"
              />
              <span className="absolute -left-px -top-px h-12 w-12 border-l-2 border-t-2 border-magenta" />
              <span className="absolute -bottom-px -right-px h-12 w-12 border-b-2 border-r-2 border-electric" />
              <div className="absolute bottom-6 left-6 border-l-2 border-magenta bg-background/80 px-3 py-2 backdrop-blur-md">
                <p className="text-[0.64rem] font-bold uppercase tracking-[0.28em] text-muted-foreground">
                  Network identity
                </p>
                <p className="display-xl mt-1 text-xl">{site.name}</p>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* SERVER STATUS */}
      <section className="relative border-y border-border/60 bg-surface/30">
        <div className="mx-auto max-w-7xl px-5 py-12">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <SectionHeading eyebrow="Live telemetry" title="Server" accent="status" />
            <span className="inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.22em] text-primary">
              <span className="live-dot h-2 w-2 rounded-full bg-primary" />
              {status.online ? "All systems nominal" : "Maintenance"}
            </span>
          </div>
          <div className="mt-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
            <StatTile
              label="Players"
              value={`${status.players}/${status.maxPlayers}`}
              hint="Peak today 341"
            />
            <StatTile label="TPS" value={status.tps} hint="20.0 is perfect" />
            <StatTile
              label="Uptime"
              value={`${status.uptimeDays}d`}
              hint={`Restart ${status.lastRestart}`}
            />
            <StatTile label="Version" value={status.version} hint="Java + Bedrock crossplay" />
            <StatTile label="Regions" value={status.region} hint="Three-node network" />
          </div>
        </div>
      </section>

      {/* WORLDS */}
      <section className="relative overflow-hidden py-20">
        <Atmosphere className="opacity-60" />
        <div className="relative mx-auto max-w-7xl px-5">
          <SectionHeading
            eyebrow="The map network"
            title="Choose your"
            accent="world"
            description="Four distinct ways to play, one connected community. Move between worlds without losing your identity or season progress."
          />
          <div className="mt-10 grid gap-4 md:grid-cols-2">
            {worlds.slice(0, 4).map((w, index) => (
              <Link
                key={w.id}
                to="/worlds"
                className="panel hover-lift group relative overflow-hidden rounded-lg"
              >
                <div className="relative aspect-[16/9] overflow-hidden">
                  <img
                    src={worldArtwork[index]}
                    alt={`${w.name} world environment`}
                    width={1280}
                    height={720}
                    loading="lazy"
                    className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-[1.035]"
                  />
                  <div className="absolute inset-0 bg-gradient-to-t from-background via-background/15 to-transparent" />
                  <span className="absolute right-4 top-4 rounded-full border border-primary/50 bg-background/75 px-2.5 py-1 text-[0.68rem] font-bold uppercase tracking-[0.16em] text-primary backdrop-blur-md">
                    {w.status}
                  </span>
                  <div className="absolute inset-x-0 bottom-0 p-6">
                    <p className="text-[0.68rem] font-bold uppercase tracking-[0.3em] text-magenta">
                      {w.type} · {w.season}
                    </p>
                    <h3 className="display-xl mt-2 text-4xl tracking-wide">{w.name}</h3>
                  </div>
                </div>
                <div className="relative p-6 pt-4">
                  <div
                    className={`pointer-events-none absolute inset-x-0 top-0 h-16 bg-gradient-to-b ${accentRing[w.accent]} to-transparent opacity-10`}
                  />
                  <p className="relative text-sm leading-relaxed text-muted-foreground">
                    {w.blurb}
                  </p>
                  <div className="relative mt-5 flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-border/50 pt-4 text-[0.72rem] uppercase tracking-[0.16em] text-muted-foreground">
                    <span>{w.difficulty}</span>
                    <span className="text-foreground">{w.players} playing</span>
                    <span className="ml-auto inline-flex items-center gap-2 font-bold text-primary">
                      Enter world <ArrowRight className="h-3.5 w-3.5" />
                    </span>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* LIVE */}
      <section className="relative border-y border-border/60 bg-surface/30 py-20">
        <div className="mx-auto max-w-7xl px-5">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <SectionHeading
              eyebrow="Creators on air"
              title="Live"
              accent="right now"
              description="Members streaming the SMP across TikTok, Twitch and YouTube."
            />
            <Link
              to="/live"
              className="inline-flex items-center gap-2 text-xs font-bold uppercase tracking-[0.18em] text-primary hover:text-magenta"
            >
              All streams <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
          <div className="mt-10 grid gap-4 md:grid-cols-3">
            {liveStreams.map((s) => (
              <article key={s.id} className="panel hover-lift rounded-lg p-6">
                <div className="flex items-center justify-between gap-3">
                  <LiveBadge />
                  <span className="text-[0.72rem] font-bold uppercase tracking-[0.18em] text-muted-foreground">
                    {s.platform}
                  </span>
                </div>
                <h3 className="mt-4 text-lg font-bold leading-snug text-foreground">{s.title}</h3>
                <p className="mt-2 text-sm text-muted-foreground">
                  {s.creator} · {s.handle}
                </p>
                <div className="mt-5 flex items-center justify-between text-xs uppercase tracking-[0.16em] text-muted-foreground">
                  <span className="inline-flex items-center gap-1.5 text-foreground">
                    <Radio className="h-3.5 w-3.5 text-magenta" />
                    {s.viewers.toLocaleString()} watching
                  </span>
                  <span>{s.startedAt}</span>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>

      {/* COMMUNITY PILLARS */}
      <section className="relative overflow-hidden py-20">
        <Atmosphere className="opacity-50" />
        <div className="relative mx-auto max-w-7xl px-5">
          <SectionHeading
            eyebrow="Why people stay"
            title="Built for"
            accent="the long haul"
            description="No pay-to-win, no dead chat, no admin ghosting. Just a server that respects the hours you put in."
          />
          <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {communityPillars.map((p, i) => (
              <div key={p.title} className="panel hover-lift rounded-lg p-6">
                <span className="grid h-10 w-10 place-items-center rounded-sm border border-primary/40 bg-primary/10 text-primary">
                  {
                    [
                      <Shield key="a" className="h-5 w-5" />,
                      <Gamepad2 key="b" className="h-5 w-5" />,
                      <Flame key="c" className="h-5 w-5" />,
                      <Users key="d" className="h-5 w-5" />,
                    ][i]
                  }
                </span>
                <h3 className="mt-4 text-base font-bold uppercase tracking-[0.1em]">{p.title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{p.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* EVENTS */}
      <section className="relative border-y border-border/60 bg-surface/30 py-20">
        <div className="mx-auto max-w-7xl px-5">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <SectionHeading eyebrow="What's next" title="Upcoming" accent="events" />
            <Link
              to="/events"
              className="inline-flex items-center gap-2 text-xs font-bold uppercase tracking-[0.18em] text-primary hover:text-magenta"
            >
              Full calendar <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
          <div className="mt-10 grid gap-4 lg:grid-cols-2">
            {events.slice(0, 4).map((e) => (
              <article key={e.id} className="panel hover-lift rounded-lg p-6">
                <div className="grid grid-cols-[minmax(0,1fr)_auto] items-start gap-4">
                  <div className="min-w-0">
                    <p className="text-[0.72rem] font-bold uppercase tracking-[0.3em] text-magenta">
                      {e.type} · {e.world}
                    </p>
                    <h3 className="mt-2 text-xl font-extrabold leading-snug tracking-tight">
                      {e.name}
                    </h3>
                    <p className="mt-2 text-sm text-muted-foreground">{e.blurb}</p>
                  </div>
                  <div className="shrink-0 rounded-sm border border-border px-3 py-2 text-center">
                    <p className="text-xs font-bold uppercase tracking-[0.12em] text-foreground">
                      {e.date}
                    </p>
                    <p className="mt-1 text-[0.74rem] text-muted-foreground">{e.time}</p>
                  </div>
                </div>
                <div className="mt-5">
                  <div className="h-1 w-full overflow-hidden rounded-full bg-muted">
                    <div
                      className="h-full rounded-full bg-gradient-to-r from-magenta to-electric"
                      style={{ width: `${(e.slotsTaken / e.slots) * 100}%` }}
                    />
                  </div>
                  <p className="mt-2 flex items-center gap-2 text-[0.75rem] uppercase tracking-[0.16em] text-muted-foreground">
                    <CalendarDays className="h-3.5 w-3.5" />
                    {e.slotsTaken}/{e.slots} signed up · {e.prize}
                  </p>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>

      {/* LEADERBOARD */}
      <section className="relative overflow-hidden py-20">
        <Atmosphere className="opacity-50" />
        <div className="relative mx-auto max-w-7xl px-5">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <SectionHeading eyebrow="Season 7 standings" title="Top of the" accent="ladder" />
            <Link
              to="/leaderboards"
              className="inline-flex items-center gap-2 text-xs font-bold uppercase tracking-[0.18em] text-primary hover:text-magenta"
            >
              Full leaderboard <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
          <div className="panel mt-10 overflow-hidden rounded-lg">
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] text-left text-sm">
                <thead>
                  <tr className="border-b border-border/60 text-[0.72rem] uppercase tracking-[0.22em] text-muted-foreground">
                    <th className="px-5 py-3">#</th>
                    <th className="px-5 py-3">Player</th>
                    <th className="px-5 py-3">Guild</th>
                    <th className="px-5 py-3">Hours</th>
                    <th className="px-5 py-3">Events</th>
                    <th className="px-5 py-3 text-right">Points</th>
                  </tr>
                </thead>
                <tbody>
                  {leaderboard.slice(0, 5).map((row) => (
                    <tr key={row.rank} className="border-b border-border/40 last:border-0">
                      <td className="px-5 py-3 font-mono text-magenta">
                        {String(row.rank).padStart(2, "0")}
                      </td>
                      <td className="px-5 py-3 font-semibold text-foreground">{row.player}</td>
                      <td className="px-5 py-3 text-muted-foreground">{row.guild}</td>
                      <td className="px-5 py-3 text-muted-foreground">{row.playtimeHours}</td>
                      <td className="px-5 py-3 text-muted-foreground">{row.events}</td>
                      <td className="px-5 py-3 text-right font-mono text-foreground">
                        {row.points.toLocaleString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </section>

      {/* MEMBERSHIP */}
      <section className="relative border-y border-border/60 bg-surface/30 py-20">
        <div className="mx-auto max-w-7xl px-5">
          <SectionHeading
            eyebrow="Support the server"
            title="Membership"
            accent="tiers"
            align="center"
            description="Cosmetics, priority and creator tooling. Never stats, never gear, never an unfair advantage."
          />
          <div className="mt-12 grid gap-4 lg:grid-cols-3">
            {tiers.map((t) => (
              <div
                key={t.id}
                className={`panel hover-lift relative rounded-lg p-7 ${t.featured ? "panel-glow border-primary/60" : ""}`}
              >
                {t.featured ? (
                  <span className="absolute -top-3 left-7 rounded-full bg-gradient-to-r from-magenta to-electric px-3 py-1 text-[0.72rem] font-bold uppercase tracking-[0.18em] text-primary-foreground">
                    Most popular
                  </span>
                ) : null}
                <div className="flex items-center gap-2">
                  {t.id === "legend" ? <Crown className="h-4 w-4 text-magenta" /> : null}
                  <h3 className="text-2xl font-extrabold tracking-tight">{t.name}</h3>
                </div>
                <p className="mt-3">
                  <span className="display-xl text-4xl gradient-text">
                    {t.price === 0 ? "Free" : `£${t.price}`}
                  </span>
                  <span className="ml-2 text-xs uppercase tracking-[0.18em] text-muted-foreground">
                    {t.cadence}
                  </span>
                </p>
                <p className="mt-3 text-sm text-muted-foreground">{t.blurb}</p>
                <ul className="mt-5 space-y-2 text-sm text-muted-foreground">
                  {t.perks.map((p) => (
                    <li key={p} className="flex gap-2">
                      <span className="mt-1.5 h-1 w-1 shrink-0 rounded-full bg-magenta" />
                      {p}
                    </li>
                  ))}
                </ul>
                <Link
                  to="/membership"
                  className={`mt-7 block rounded-sm px-4 py-3 text-center text-xs font-bold uppercase tracking-[0.18em] transition-transform hover:-translate-y-0.5 ${
                    t.featured
                      ? "bg-gradient-to-r from-magenta via-violet to-electric text-primary-foreground"
                      : "border border-border text-foreground"
                  }`}
                >
                  {t.price === 0 ? "Start playing" : `Choose ${t.name}`}
                </Link>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* DISCORD CTA */}
      <section className="relative overflow-hidden py-20">
        <Atmosphere />
        <div className="relative mx-auto max-w-5xl px-5">
          <div className="panel panel-glow relative overflow-hidden rounded-lg px-6 py-14 text-center sm:px-14">
            <CornerFrame />
            <Eyebrow className="justify-center">Come say hi</Eyebrow>
            <h2 className="display-xl mt-5 text-5xl sm:text-6xl">
              Join the <span className="gradient-text neon-text">stream</span>
            </h2>
            <p className="mx-auto mt-4 max-w-xl text-sm text-muted-foreground sm:text-base">
              14,200 members, live voice every night, event pings, build critique channels and
              direct access to the admin council.
            </p>
            <div className="mt-8 flex flex-wrap justify-center gap-3">
              <a
                href={site.discordUrl}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-2 rounded-sm bg-gradient-to-r from-magenta via-violet to-electric px-7 py-3.5 text-xs font-bold uppercase tracking-[0.18em] text-primary-foreground shadow-[0_20px_45px_-20px_var(--magenta)] transition-transform hover:-translate-y-0.5"
              >
                <MessageSquare className="h-4 w-4" /> Open Discord
              </a>
              <Link
                to="/community"
                className="inline-flex items-center gap-2 rounded-sm border border-border px-7 py-3.5 text-xs font-bold uppercase tracking-[0.18em] transition-colors hover:border-magenta hover:text-magenta"
              >
                <Swords className="h-4 w-4" /> Creator programme
              </Link>
            </div>
            <div className="mt-10 grid gap-4 border-t border-border/60 pt-8 sm:grid-cols-3">
              {[
                { icon: <Users className="h-4 w-4" />, k: "14,200", v: "Discord members" },
                { icon: <Trophy className="h-4 w-4" />, k: "312", v: "Events run" },
                { icon: <Flame className="h-4 w-4" />, k: "7", v: "Seasons deep" },
              ].map((s) => (
                <div key={s.v} className="flex flex-col items-center gap-1">
                  <span className="text-primary">{s.icon}</span>
                  <span className="display-xl text-2xl">{s.k}</span>
                  <span className="text-[0.74rem] uppercase tracking-[0.18em] text-muted-foreground">
                    {s.v}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
