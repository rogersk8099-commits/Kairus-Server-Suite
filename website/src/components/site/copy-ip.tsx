import { Check, Copy } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { cn } from "@/lib/utils";

export function CopyIp({
  value,
  label,
  className,
}: {
  value: string;
  label?: string;
  className?: string;
}) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      toast.success(`${value} copied`);
      setTimeout(() => setCopied(false), 1800);
    } catch {
      toast.error("Couldn't copy — select the address manually.");
    }
  };

  return (
    <button
      type="button"
      onClick={copy}
      className={cn(
        "group panel flex w-full items-center justify-between gap-4 rounded-sm px-4 py-3 text-left transition-colors hover:border-primary/60",
        className,
      )}
    >
      <span className="min-w-0">
        {label ? (
          <span className="block text-[0.72rem] font-bold uppercase tracking-[0.28em] text-muted-foreground">
            {label}
          </span>
        ) : null}
        <span className="block truncate font-mono text-sm text-foreground sm:text-base">
          {value}
        </span>
      </span>
      {copied ? (
        <Check className="h-4 w-4 shrink-0 text-primary" />
      ) : (
        <Copy className="h-4 w-4 shrink-0 text-muted-foreground transition-colors group-hover:text-primary" />
      )}
    </button>
  );
}
