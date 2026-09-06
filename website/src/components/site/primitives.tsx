import { cn } from "@/lib/utils";
import type { ReactNode } from "react";

export function Atmosphere({ className }: { className?: string }) {
  return (
    <div
      className={cn("pointer-events-none absolute inset-0 overflow-hidden", className)}
      aria-hidden
    >
      <div className="absolute inset-0 grid-noise opacity-40" />
      <div className="smoke absolute -left-32 top-[-10%] h-[520px] w-[520px] rounded-full bg-[radial-gradient(circle,color-mix(in_oklab,var(--violet)_38%,transparent),transparent_65%)] blur-3xl" />
      <div className="smoke absolute -right-24 top-[10%] h-[560px] w-[560px] rounded-full bg-[radial-gradient(circle,color-mix(in_oklab,var(--electric)_30%,transparent),transparent_65%)] blur-3xl [animation-delay:-6s]" />
      <div className="smoke absolute bottom-[-20%] left-1/3 h-[420px] w-[420px] rounded-full bg-[radial-gradient(circle,color-mix(in_oklab,var(--magenta)_28%,transparent),transparent_65%)] blur-3xl [animation-delay:-11s]" />
    </div>
  );
}

export function CornerFrame({ className }: { className?: string }) {
  const corner = "absolute h-10 w-10 border-primary/70";
  return (
    <div className={cn("pointer-events-none absolute inset-3 sm:inset-5", className)} aria-hidden>
      <span className={cn(corner, "left-0 top-0 border-l-2 border-t-2")} />
      <span className={cn(corner, "right-0 top-0 border-r-2 border-t-2")} />
      <span className={cn(corner, "bottom-0 left-0 border-b-2 border-l-2")} />
      <span className={cn(corner, "bottom-0 right-0 border-b-2 border-r-2")} />
    </div>
  );
}

export function XMarks() {
  const marks = [
    "left-[6%] top-[18%]",
    "right-[10%] top-[30%]",
    "left-[14%] bottom-[22%]",
    "right-[6%] bottom-[14%]",
    "left-[46%] top-[8%]",
  ];
  return (
    <div className="pointer-events-none absolute inset-0" aria-hidden>
      {marks.map((pos, i) => (
        <span
          key={pos}
          className={cn(
            "absolute text-sm text-primary/40",
            pos,
            i % 2 ? "text-accent/40" : "text-magenta/40",
          )}
        >
          ✕
        </span>
      ))}
    </div>
  );
}

export function Eyebrow({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-2 text-[0.75rem] font-semibold uppercase tracking-[0.22em] text-muted-foreground",
        className,
      )}
    >
      <span className="h-px w-6 bg-gradient-to-r from-transparent to-primary" />
      {children}
    </span>
  );
}

export function SectionHeading({
  eyebrow,
  title,
  accent,
  description,
  align = "left",
}: {
  eyebrow?: string;
  title: string;
  accent?: string;
  description?: string;
  align?: "left" | "center";
}) {
  return (
    <div className={cn("max-w-2xl", align === "center" && "mx-auto text-center")}>
      {eyebrow ? <Eyebrow>{eyebrow}</Eyebrow> : null}
      <h2 className="display-xl mt-4 text-4xl sm:text-5xl">
        {title} {accent ? <span className="gradient-text neon-text">{accent}</span> : null}
      </h2>
      {description ? (
        <p className="mt-4 text-sm leading-relaxed text-muted-foreground sm:text-base">
          {description}
        </p>
      ) : null}
    </div>
  );
}

export function LiveBadge({ label = "Live" }: { label?: string }) {
  return (
    <span className="inline-flex items-center gap-2 rounded-full border border-destructive/50 bg-destructive/10 px-3 py-1 text-[0.74rem] font-bold uppercase tracking-[0.18em] text-destructive-foreground">
      <span className="live-dot h-2 w-2 rounded-full bg-destructive" />
      {label}
    </span>
  );
}

export function Panel({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={cn("panel rounded-lg", className)}>{children}</div>;
}

export function StatTile({
  label,
  value,
  hint,
}: {
  label: string;
  value: ReactNode;
  hint?: string;
}) {
  return (
    <div className="panel hover-lift rounded-lg p-4">
      <p className="text-[0.72rem] font-semibold uppercase tracking-[0.28em] text-muted-foreground">
        {label}
      </p>
      <p className="display-xl mt-2 text-3xl text-foreground">{value}</p>
      {hint ? <p className="mt-1 text-xs text-muted-foreground">{hint}</p> : null}
    </div>
  );
}

export function PageHero({
  eyebrow,
  title,
  accent,
  description,
  children,
}: {
  eyebrow: string;
  title: string;
  accent?: string;
  description?: string;
  children?: ReactNode;
}) {
  return (
    <section className="relative overflow-hidden border-b border-border/60 py-20 sm:py-28">
      <Atmosphere />
      <XMarks />
      <div className="relative mx-auto max-w-6xl px-5">
        <Eyebrow>{eyebrow}</Eyebrow>
        <h1 className="display-xl mt-4 text-5xl sm:text-7xl">
          {title} {accent ? <span className="gradient-text neon-text">{accent}</span> : null}
        </h1>
        {description ? (
          <p className="mt-5 max-w-2xl text-sm leading-relaxed text-muted-foreground sm:text-base">
            {description}
          </p>
        ) : null}
        {children ? <div className="mt-8">{children}</div> : null}
      </div>
    </section>
  );
}
