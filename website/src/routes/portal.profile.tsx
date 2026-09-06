import { createFileRoute } from "@tanstack/react-router";
import { useSuspenseQuery } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { queries } from "@/services/smp";

export const Route = createFileRoute("/portal/profile")({
  loader: ({ context }) => context.queryClient.ensureQueryData(queries.portalUser),
  component: ProfilePage,
});

function ProfilePage() {
  const { data: user } = useSuspenseQuery(queries.portalUser);
  const [displayName, setDisplayName] = useState(user.displayName);
  const [bio, setBio] = useState(
    "Ronin guild PvP main. Gauntlet finalist twice. Building a floating market district in Ashfall.",
  );

  return (
    <div className="space-y-8">
      <header>
        <h1 className="display-xl text-4xl">
          Your <span className="gradient-text">profile</span>
        </h1>
        <p className="mt-2 text-sm text-muted-foreground">
          This is what other members see on the leaderboard and in event brackets.
        </p>
      </header>

      <div className="panel rounded-lg p-6">
        <div className="grid gap-4 sm:grid-cols-2">
          {[
            ["Username", user.username],
            ["Minecraft UUID", user.minecraftUuid],
            ["Rank", user.rank],
            ["Member since", user.joinedAt],
          ].map(([k, v]) => (
            <div key={k}>
              <p className="text-[0.72rem] font-bold uppercase tracking-[0.22em] text-muted-foreground">
                {k}
              </p>
              <p className="mt-1 font-mono text-sm">{v}</p>
            </div>
          ))}
        </div>
      </div>

      <form
        className="panel space-y-5 rounded-lg p-6"
        onSubmit={(e) => {
          e.preventDefault();
          toast.success("Profile updated");
        }}
      >
        <label className="block">
          <span className="text-[0.72rem] font-bold uppercase tracking-[0.22em] text-muted-foreground">
            Display name
          </span>
          <input
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            className="mt-2 w-full rounded-sm border border-border bg-background/70 px-4 py-3 text-sm outline-none focus:border-primary"
          />
        </label>
        <label className="block">
          <span className="text-[0.72rem] font-bold uppercase tracking-[0.22em] text-muted-foreground">
            Bio
          </span>
          <textarea
            value={bio}
            rows={4}
            onChange={(e) => setBio(e.target.value)}
            className="mt-2 w-full resize-none rounded-sm border border-border bg-background/70 px-4 py-3 text-sm outline-none focus:border-primary"
          />
        </label>
        <button
          type="submit"
          className="rounded-sm bg-gradient-to-r from-magenta via-violet to-electric px-6 py-3 text-xs font-bold uppercase tracking-[0.18em] text-primary-foreground"
        >
          Save changes
        </button>
      </form>
    </div>
  );
}
