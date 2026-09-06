import { Link } from "@tanstack/react-router";
import { site } from "@/config/site";

const columns = [
  {
    title: "Server",
    links: [
      { to: "/play", label: "How to join" },
      { to: "/worlds", label: "Worlds" },
      { to: "/events", label: "Events" },
      { to: "/leaderboards", label: "Leaderboards" },
    ],
  },
  {
    title: "Community",
    links: [
      { to: "/live", label: "Live streams" },
      { to: "/community", label: "Creator programme" },
      { to: "/membership", label: "Membership" },
      { to: "/portal", label: "Member portal" },
    ],
  },
] as const;

export function SiteFooter() {
  return (
    <footer className="relative overflow-hidden border-t border-border/60 bg-background">
      <div className="pointer-events-none absolute inset-x-0 top-0 h-px bg-gradient-to-r from-transparent via-primary to-transparent" />
      <div className="mx-auto grid max-w-7xl gap-10 px-5 py-14 md:grid-cols-4">
        <div className="md:col-span-2">
          <p className="display-xl text-3xl">
            {site.shortName} <span className="gradient-text">SMP</span>
          </p>
          <p className="mt-3 max-w-sm text-sm leading-relaxed text-muted-foreground">
            {site.description}
          </p>
          <p className="mt-5 font-mono text-xs uppercase tracking-[0.18em] text-muted-foreground">
            Java {site.javaIp} · Bedrock {site.bedrockIp}:{site.bedrockPort}
          </p>
        </div>
        {columns.map((col) => (
          <div key={col.title}>
            <p className="text-[0.74rem] font-bold uppercase tracking-[0.3em] text-primary">
              {col.title}
            </p>
            <ul className="mt-4 space-y-2.5">
              {col.links.map((l) => (
                <li key={l.to}>
                  <Link
                    to={l.to}
                    className="text-sm text-muted-foreground transition-colors hover:text-foreground"
                  >
                    {l.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="mx-auto flex max-w-7xl flex-col gap-3 border-t border-border/60 px-5 py-6 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
        <p>
          © {new Date().getFullYear()} {site.name}. Not affiliated with Mojang or Microsoft.
        </p>
        <div className="flex gap-4">
          <a
            href={site.socials.tiktok}
            target="_blank"
            rel="noreferrer"
            className="hover:text-foreground"
          >
            TikTok
          </a>
          <a
            href={site.socials.twitch}
            target="_blank"
            rel="noreferrer"
            className="hover:text-foreground"
          >
            Twitch
          </a>
          <a
            href={site.socials.youtube}
            target="_blank"
            rel="noreferrer"
            className="hover:text-foreground"
          >
            YouTube
          </a>
          <a
            href={site.discordUrl}
            target="_blank"
            rel="noreferrer"
            className="hover:text-foreground"
          >
            Discord
          </a>
        </div>
      </div>
    </footer>
  );
}
