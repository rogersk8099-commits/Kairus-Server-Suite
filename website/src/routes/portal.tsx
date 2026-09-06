import { createFileRoute, Link, Outlet, useNavigate } from "@tanstack/react-router";
import { useEffect } from "react";
import {
  Award,
  CalendarDays,
  CreditCard,
  LayoutDashboard,
  LogOut,
  MessageSquare,
  Settings,
  Swords,
  UserRound,
} from "lucide-react";
import { site } from "@/config/site";
import { useAuth } from "@/lib/auth";
import { portalUser } from "@/data/mock";
import { Atmosphere } from "@/components/site/primitives";

export const Route = createFileRoute("/portal")({
  head: () => ({
    meta: [
      { title: `Member portal — ${site.name}` },
      {
        name: "description",
        content: `Your ${site.name} dashboard: Minecraft stats, achievements, events, Discord link and membership.`,
      },
      { property: "og:title", content: `Member portal — ${site.name}` },
      { property: "og:description", content: `Your ${site.name} member dashboard.` },
    ],
  }),
  component: PortalLayout,
});

const links = [
  { to: "/portal", label: "Dashboard", icon: LayoutDashboard, exact: true },
  { to: "/portal/profile", label: "Profile", icon: UserRound },
  { to: "/portal/stats", label: "Minecraft stats", icon: Swords },
  { to: "/portal/achievements", label: "Achievements", icon: Award },
  { to: "/portal/events", label: "My events", icon: CalendarDays },
  { to: "/portal/discord", label: "Discord", icon: MessageSquare },
  { to: "/portal/membership", label: "Membership", icon: CreditCard },
  { to: "/portal/settings", label: "Settings", icon: Settings },
] as const;

function PortalLayout() {
  const { session, ready, signOut } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (ready && !session) navigate({ to: "/login", replace: true });
  }, [ready, session, navigate]);

  if (!ready || !session) {
    return (
      <div className="grid min-h-[60vh] place-items-center text-sm text-muted-foreground">
        Checking your session…
      </div>
    );
  }

  return (
    <div className="relative overflow-hidden">
      <Atmosphere className="opacity-50" />
      <div className="relative mx-auto grid max-w-7xl gap-8 px-5 py-10 lg:grid-cols-[240px_minmax(0,1fr)]">
        <aside className="lg:sticky lg:top-24 lg:self-start">
          <div className="panel rounded-lg p-5">
            <div className="flex min-w-0 items-center gap-3">
              <span className="grid h-11 w-11 shrink-0 place-items-center rounded-sm bg-gradient-to-br from-magenta to-electric text-sm font-black text-primary-foreground">
                {portalUser.avatarSeed}
              </span>
              <div className="min-w-0">
                <p className="truncate font-bold">{session.username}</p>
                <p className="text-[0.72rem] uppercase tracking-[0.18em] text-magenta">
                  {portalUser.rank}
                </p>
              </div>
            </div>
          </div>
          <nav className="mt-3 flex gap-1 overflow-x-auto lg:flex-col lg:overflow-visible">
            {links.map((l) => (
              <Link
                key={l.to}
                to={l.to}
                activeOptions={{ exact: "exact" in l ? l.exact : false }}
                className="flex shrink-0 items-center gap-2.5 rounded-sm px-3.5 py-2.5 text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground transition-colors hover:text-foreground"
                activeProps={{ className: "bg-primary/15 text-foreground" }}
              >
                <l.icon className="h-4 w-4" />
                {l.label}
              </Link>
            ))}
            <button
              type="button"
              onClick={() => {
                signOut();
                navigate({ to: "/", replace: true });
              }}
              className="flex shrink-0 items-center gap-2.5 rounded-sm px-3.5 py-2.5 text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground transition-colors hover:text-destructive"
            >
              <LogOut className="h-4 w-4" /> Sign out
            </button>
          </nav>
        </aside>
        <div className="min-w-0">
          <Outlet />
        </div>
      </div>
    </div>
  );
}
