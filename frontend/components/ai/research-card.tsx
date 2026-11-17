"use client";

import { useQuery } from "@tanstack/react-query";
import { Sparkles } from "lucide-react";
import { useState } from "react";
import { api, errorMessage } from "@/lib/api";
import { formatDay } from "@/lib/format";
import { Card, buttonSecondary } from "@/components/ui/primitives";

type Research = {
  website: string | null;
  logoUrl: string | null;
  email: string | null;
  summary: string | null;
  researchedAt: string;
};
type Status = { configured: boolean; message: string };
type Field = "website" | "logo" | "email";

/**
 * What the AI found about a record on the public internet.
 * For a company the main content is a short business summary. A found website, logo or email
 * is offered only while the record does not have one yet (`has` says which it already has).
 */
export function ResearchCard({
  kind,
  id,
  has = {},
  onApplied,
}: {
  kind: "company" | "contact";
  id: string;
  has?: Partial<Record<Field, boolean>>;
  onApplied: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const status = useQuery({ queryKey: ["ai-status"], queryFn: () => api<Status>("/ai/status") });
  // The server answers 204 when nothing has been researched yet; a query may not return undefined.
  const research = useQuery({
    queryKey: ["ai-research", kind, id],
    queryFn: async () => (await api<Research | undefined>(`/ai/research/${kind}/${id}`)) ?? null,
  });

  const run = async (action: () => Promise<unknown>, fallback: string) => {
    setBusy(true);
    setError("");
    try {
      await action();
    } catch (cause) {
      setError(errorMessage(cause, fallback));
    } finally {
      setBusy(false);
    }
  };
  const refresh = () =>
    run(async () => {
      await api(`/ai/research/${kind}/${id}`, { method: "POST" });
      await research.refetch();
    }, "Research could not be completed.");
  const apply = (field: Field) =>
    run(async () => {
      await api(`/ai/research/${kind}/${id}/apply`, { method: "POST", body: JSON.stringify({ field }) });
      onApplied();
    }, "The suggestion could not be applied.");

  const data = research.data;
  const suggestions: { field: Field; label: string; value: string }[] = [];
  if (data && kind === "company" && data.website && !has.website)
    suggestions.push({ field: "website", label: "Website", value: data.website });
  if (data && kind === "company" && data.logoUrl && !has.logo)
    suggestions.push({ field: "logo", label: "Logo", value: data.logoUrl });
  if (data && kind === "contact" && data.email && !has.email)
    suggestions.push({ field: "email", label: "Public email", value: data.email });
  const foundNothing = data && !data.summary && !data.website && !data.logoUrl && !data.email;

  return (
    <Card className="p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 items-start gap-2">
          <Sparkles className="mt-0.5 size-4 shrink-0 text-brand" aria-hidden />
          <div>
            <h3 className="text-[14px] font-semibold">
              {kind === "company" ? "Business snapshot" : "Internet research"}
            </h3>
            <p className="mt-0.5 text-[12px] text-ink-3">
              {data
                ? `Researched from public sources on ${formatDay(data.researchedAt.slice(0, 10))}`
                : "Researched by AI from public sources."}
            </p>
          </div>
        </div>
        {status.data?.configured && (
          <button onClick={() => void refresh()} disabled={busy} className={buttonSecondary}>
            {busy ? "Researching…" : data ? "Refresh" : "Research with AI"}
          </button>
        )}
      </div>

      {status.isPending ? (
        <p className="mt-3 text-[12px] text-ink-3">Checking AI availability…</p>
      ) : (
        !status.data?.configured && (
          <p className="mt-3 text-[12px] text-ink-2">{status.data?.message ?? "AI status unavailable."}</p>
        )
      )}
      {research.isError && (
        <p className="mt-3 text-[12px] text-down">
          Saved research could not be loaded.{" "}
          <button onClick={() => void research.refetch()} className="underline">
            Retry
          </button>
        </p>
      )}

      {data?.summary && (
        <p className="mt-4 border-t border-line pt-4 text-[13.5px] leading-6 text-ink">{data.summary}</p>
      )}
      {data && !data.summary && kind === "company" && !foundNothing && (
        <p className="mt-4 border-t border-line pt-4 text-[12.5px] text-ink-3">
          No summary yet. Refresh to generate one.
        </p>
      )}
      {foundNothing && (
        <p className="mt-4 border-t border-line pt-4 text-[12.5px] text-ink-3">
          No verified public details were found.
        </p>
      )}

      {suggestions.length > 0 && (
        <ul className="mt-4 space-y-3 border-t border-line pt-4 text-[12.5px]">
          {suggestions.map(({ field, label, value }) => (
            <li key={field} className="flex items-center gap-3">
              <div className="min-w-0 flex-1">
                <p className="text-ink-3">{label}</p>
                {field === "logo" ? (
                  // eslint-disable-next-line @next/next/no-img-element -- a remote logo of unknown size and host
                  <img
                    src={value}
                    alt={`Suggested ${label.toLowerCase()}`}
                    referrerPolicy="no-referrer"
                    className="mt-1 max-h-10 max-w-32 rounded bg-white object-contain p-1"
                  />
                ) : (
                  <p className="truncate font-medium">{value}</p>
                )}
              </div>
              <button onClick={() => void apply(field)} disabled={busy} className={buttonSecondary}>
                Add to record
              </button>
            </li>
          ))}
        </ul>
      )}
      {error && (
        <p role="alert" className="mt-3 text-[12px] text-down">
          {error}
        </p>
      )}
    </Card>
  );
}
