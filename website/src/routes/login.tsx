import { createFileRoute, useNavigate, Link } from "@tanstack/react-router";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { site } from "@/config/site";
import { useAuth } from "@/lib/auth";
import { Atmosphere, CornerFrame, Eyebrow } from "@/components/site/primitives";

export const Route = createFileRoute("/login")({
  head: () => ({
    meta: [
      { title: `Sign in — ${site.name}` },
      {
        name: "description",
        content: `Sign in to the ${site.name} member portal to track stats, achievements, events and membership.`,
      },
      { property: "og:title", content: `Sign in — ${site.name}` },
      { property: "og:description", content: `Access the ${site.name} member portal.` },
    ],
  }),
  component: LoginPage,
});

function LoginPage() {
  const { session, signIn, ready } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState("Emberlyn");
  const [password, setPassword] = useState("");

  useEffect(() => {
    if (ready && session) navigate({ to: "/portal", replace: true });
  }, [ready, session, navigate]);

  return (
    <section className="relative flex min-h-[80vh] items-center overflow-hidden py-16">
      <Atmosphere />
      <div className="relative mx-auto w-full max-w-md px-5">
        <div className="panel panel-glow relative rounded-lg p-8 sm:p-10">
          <CornerFrame />
          <Eyebrow>Member portal</Eyebrow>
          <h1 className="display-xl mt-4 text-4xl">
            Sign <span className="gradient-text neon-text">in</span>
          </h1>
          <p className="mt-3 text-sm text-muted-foreground">
            Use the credentials linked to your Minecraft account in Discord.
          </p>
          <form
            className="mt-8 space-y-4"
            onSubmit={(e) => {
              e.preventDefault();
              if (!username.trim()) {
                toast.error("Enter your username");
                return;
              }
              signIn(username.trim());
              toast.success(`Welcome back, ${username.trim()}`);
              navigate({ to: "/portal" });
            }}
          >
            <label className="block">
              <span className="text-[0.72rem] font-bold uppercase tracking-[0.22em] text-muted-foreground">
                Username
              </span>
              <input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="mt-2 w-full rounded-sm border border-border bg-background/70 px-4 py-3 text-sm outline-none focus:border-primary"
                placeholder="Your Minecraft name"
              />
            </label>
            <label className="block">
              <span className="text-[0.72rem] font-bold uppercase tracking-[0.22em] text-muted-foreground">
                Password
              </span>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="mt-2 w-full rounded-sm border border-border bg-background/70 px-4 py-3 text-sm outline-none focus:border-primary"
                placeholder="••••••••"
              />
            </label>
            <button
              type="submit"
              className="w-full rounded-sm bg-gradient-to-r from-magenta via-violet to-electric px-6 py-3.5 text-xs font-bold uppercase tracking-[0.18em] text-primary-foreground transition-transform hover:-translate-y-0.5"
            >
              Enter the portal
            </button>
          </form>
          <p className="mt-6 text-center text-xs text-muted-foreground">
            No account yet?{" "}
            <a href={site.discordUrl} target="_blank" rel="noreferrer" className="text-primary">
              Apply in Discord
            </a>{" "}
            ·{" "}
            <Link to="/play" className="text-primary">
              How to join
            </Link>
          </p>
        </div>
      </div>
    </section>
  );
}
