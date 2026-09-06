import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { LoaderCircle, LockKeyhole, MessageCircle, ShieldCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { z } from "zod";
import { Atmosphere, CornerFrame, Eyebrow } from "@/components/site/primitives";
import { site } from "@/config/site";
import { useAuth } from "@/lib/auth";

const searchSchema = z.object({ auth_error: z.string().max(64).optional() });
const messages: Record<string, string> = {
  access_denied: "Discord sign-in was cancelled. Nothing was changed.",
  invalid_state: "That sign-in attempt expired or could not be verified. Please try again.",
  provider_error: "Discord could not complete sign-in. Please try again shortly.",
  callback_failed: "We could not finish creating your session. Please try again.",
};

export const Route = createFileRoute("/login")({
  validateSearch: searchSchema,
  head: () => ({
    meta: [
      { title: `Sign in — ${site.name}` },
      { name: "description", content: `Sign in to the ${site.name} member portal with Discord.` },
      { property: "og:title", content: `Sign in — ${site.name}` },
      {
        property: "og:description",
        content: `Secure Discord access to the ${site.name} member portal.`,
      },
    ],
  }),
  component: LoginPage,
});

function LoginPage() {
  const { session, signInDemo, ready } = useAuth();
  const { auth_error: authError } = Route.useSearch();
  const navigate = useNavigate();
  const discordAuthEnabled = import.meta.env["VITE_DISCORD_AUTH_ENABLED"] === "true";
  const [starting, setStarting] = useState(false);
  const [demoName, setDemoName] = useState("Emberlyn");
  const [demoLoading, setDemoLoading] = useState(false);

  useEffect(() => {
    if (ready && session) void navigate({ to: "/portal", replace: true });
  }, [ready, session, navigate]);

  return (
    <section className="relative flex min-h-[80vh] items-center overflow-hidden py-16">
      <Atmosphere />
      <div className="relative mx-auto w-full max-w-lg px-5">
        <div className="panel panel-glow relative rounded-lg p-8 sm:p-10">
          <CornerFrame />
          <Eyebrow>Member portal</Eyebrow>
          <h1 className="display-xl mt-4 text-4xl">
            Your world, <span className="gradient-text neon-text">connected</span>
          </h1>
          <p className="mt-3 text-sm leading-6 text-muted-foreground">
            Use Discord to create or access your Kairu account. Your Discord password and OAuth
            tokens never pass through browser JavaScript.
          </p>

          {authError ? (
            <div
              role="alert"
              className="mt-6 rounded-sm border border-destructive/40 bg-destructive/10 px-4 py-3 text-sm text-foreground"
            >
              {messages[authError] ?? "Sign-in did not complete. Please try again."}
            </div>
          ) : null}

          {discordAuthEnabled ? (
            <a
              href="/auth/discord"
              onClick={() => setStarting(true)}
              aria-disabled={starting}
              className="mt-8 flex w-full items-center justify-center gap-3 rounded-sm bg-[#5865F2] px-6 py-4 text-sm font-extrabold uppercase tracking-[0.12em] text-white shadow-[0_0_32px_rgba(88,101,242,0.28)] transition hover:-translate-y-0.5 hover:bg-[#6875f5] aria-disabled:pointer-events-none aria-disabled:opacity-70"
            >
              {starting ? (
                <LoaderCircle className="h-5 w-5 animate-spin" />
              ) : (
                <MessageCircle className="h-5 w-5" />
              )}
              {starting ? "Opening Discord…" : "Continue with Discord"}
            </a>
          ) : (
            <div className="mt-8">
              <button
                type="button"
                disabled
                className="flex w-full items-center justify-center gap-3 rounded-sm bg-[#5865F2] px-6 py-4 text-sm font-extrabold uppercase tracking-[0.12em] text-white opacity-60"
              >
                <MessageCircle className="h-5 w-5" />
                Continue with Discord
              </button>
              <p className="mt-3 text-center text-xs text-muted-foreground">
                Secure Discord sign-in is installed and awaiting the server&apos;s Discord
                application credentials.
              </p>
            </div>
          )}

          <div className="mt-6 grid gap-3 text-xs text-muted-foreground sm:grid-cols-2">
            <div className="flex gap-2 rounded-sm border border-border/70 bg-background/35 p-3">
              <ShieldCheck className="h-4 w-4 shrink-0 text-primary" />
              <span>One-time state and PKCE verification</span>
            </div>
            <div className="flex gap-2 rounded-sm border border-border/70 bg-background/35 p-3">
              <LockKeyhole className="h-4 w-4 shrink-0 text-primary" />
              <span>Secure, HttpOnly website session</span>
            </div>
          </div>

          {import.meta.env.DEV ? (
            <form
              className="mt-7 border-t border-border/70 pt-6"
              onSubmit={async (event) => {
                event.preventDefault();
                setDemoLoading(true);
                try {
                  await signInDemo(demoName.trim());
                  toast.success("Development demo session created");
                  await navigate({ to: "/portal" });
                } catch {
                  toast.error("Demo sign-in failed");
                } finally {
                  setDemoLoading(false);
                }
              }}
            >
              <p className="text-[0.68rem] font-bold uppercase tracking-[0.2em] text-muted-foreground">
                Development preview only
              </p>
              <div className="mt-3 flex gap-2">
                <input
                  value={demoName}
                  onChange={(event) => setDemoName(event.target.value)}
                  maxLength={32}
                  className="min-w-0 flex-1 rounded-sm border border-border bg-background/70 px-4 py-3 text-sm outline-none focus:border-primary"
                  aria-label="Demo display name"
                />
                <button
                  disabled={demoLoading || !demoName.trim()}
                  className="rounded-sm border border-primary/50 px-4 text-xs font-bold uppercase tracking-[0.12em] text-primary disabled:opacity-50"
                >
                  {demoLoading ? "Loading…" : "Demo"}
                </button>
              </div>
            </form>
          ) : null}

          <p className="mt-6 text-center text-xs text-muted-foreground">
            Need help?{" "}
            <a href={site.discordUrl} target="_blank" rel="noreferrer" className="text-primary">
              Visit our Discord
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
