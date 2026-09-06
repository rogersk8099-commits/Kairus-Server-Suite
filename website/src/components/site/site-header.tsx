import { Link } from "@tanstack/react-router";
import { Menu, X } from "lucide-react";
import { useState } from "react";
import { site } from "@/config/site";
import { useAuth } from "@/lib/auth";
import { cn } from "@/lib/utils";

const nav = [
  { to: "/", label: "Home" },
  { to: "/play", label: "Play" },
  { to: "/worlds", label: "Worlds" },
  { to: "/live", label: "Live" },
  { to: "/events", label: "Events" },
  { to: "/leaderboards", label: "Leaderboards" },
  { to: "/community", label: "Community" },
  { to: "/membership", label: "Membership" },
] as const;

export function SiteHeader() {
  const [open, setOpen] = useState(false);
  const { session } = useAuth();

  return (
    <header className="sticky top-0 z-50 border-b border-border/60 bg-background/80 backdrop-blur-xl">
      <div className="mx-auto grid max-w-7xl grid-cols-[minmax(0,1fr)_auto] items-center gap-4 px-5 py-3.5 lg:flex lg:justify-between">
        <Link to="/" className="flex min-w-0 items-center gap-3" onClick={() => setOpen(false)}>
          <span className="grid h-9 w-9 shrink-0 place-items-center rounded-sm border border-primary/60 bg-primary/10 text-sm font-black text-primary">
            {site.shortName.slice(0, 2)}
          </span>
          <span className="display-xl truncate text-xl tracking-wide">{site.name}</span>
        </Link>

        <nav className="hidden items-center gap-1 lg:flex">
          {nav.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              className="rounded-sm px-3 py-2 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground transition-colors hover:text-foreground"
              activeProps={{ className: "text-foreground" }}
            >
              {item.label}
            </Link>
          ))}
        </nav>

        <div className="hidden items-center gap-2 lg:flex">
          <a
            href={site.discordUrl}
            target="_blank"
            rel="noreferrer"
            className="rounded-sm bg-gradient-to-r from-magenta via-violet to-electric px-4 py-2 text-xs font-bold uppercase tracking-[0.16em] text-primary-foreground shadow-[0_10px_30px_-12px_var(--violet)] transition-transform hover:-translate-y-0.5"
          >
            Join Discord
          </a>
          <Link
            to={session ? "/portal" : "/login"}
            className="rounded-sm border border-border bg-surface/70 px-4 py-2 text-xs font-bold uppercase tracking-[0.16em] text-foreground transition-colors hover:border-accent hover:text-accent"
          >
            {session ? "Portal" : "Sign in"}
          </Link>
        </div>

        <button
          type="button"
          aria-label={open ? "Close menu" : "Open menu"}
          onClick={() => setOpen((v) => !v)}
          className="grid h-10 w-10 shrink-0 place-items-center rounded-sm border border-border text-foreground lg:hidden"
        >
          {open ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
        </button>
      </div>

      <div
        className={cn(
          "overflow-hidden border-t border-border/60 transition-[max-height,opacity] duration-300 lg:hidden",
          open ? "max-h-[32rem] opacity-100" : "max-h-0 opacity-0",
        )}
      >
        <nav className="mx-auto flex max-w-7xl flex-col gap-1 px-5 py-4">
          {nav.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              onClick={() => setOpen(false)}
              className="rounded-sm px-2 py-2.5 text-sm font-semibold uppercase tracking-[0.16em] text-muted-foreground"
              activeProps={{ className: "text-foreground" }}
            >
              {item.label}
            </Link>
          ))}
          <div className="mt-2 flex gap-2">
            <a
              href={site.discordUrl}
              target="_blank"
              rel="noreferrer"
              className="flex-1 rounded-sm bg-gradient-to-r from-magenta via-violet to-electric px-4 py-2.5 text-center text-xs font-bold uppercase tracking-[0.16em] text-primary-foreground"
            >
              Join Discord
            </a>
            <Link
              to={session ? "/portal" : "/login"}
              onClick={() => setOpen(false)}
              className="flex-1 rounded-sm border border-border bg-surface/70 px-4 py-2.5 text-center text-xs font-bold uppercase tracking-[0.16em] text-foreground"
            >
              {session ? "Portal" : "Sign in"}
            </Link>
          </div>
        </nav>
      </div>
    </header>
  );
}
