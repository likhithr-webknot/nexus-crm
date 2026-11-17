"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { ImageUp, Inbox, Trash2, Wand2 } from "lucide-react";
import { useState } from "react";
import { useSession } from "@/components/auth/session";
import { Card, CardHeader, Field, buttonPrimary, buttonSecondary, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { api, errorMessage } from "@/lib/api";
import { inboxStatus, turnOffInbox } from "@/lib/emails";
import { disconnectMessage } from "@/lib/google";
import { KEYS } from "@/lib/query-keys";
import { cn } from "@/lib/format";

type MailStatus = { configured: boolean; connected: boolean; email: string | null };
type BannerKind = "logo" | "banner";
type Banner = { present: boolean; kind: BannerKind | null; companyName: string | null; previewDataUrl: string | null };

const KINDS: { kind: BannerKind; icon: typeof Wand2; title: string; body: string; size: string }[] = [
  {
    kind: "logo",
    icon: Wand2,
    title: "Generate from your logo",
    body: "Upload the company logo once. Each person's emails end with a card made from it: the logo, their name, designation and address.",
    size: "PNG or JPEG, 64–2000 px on each side. A transparent PNG works best.",
  },
  {
    kind: "banner",
    icon: ImageUp,
    title: "Upload a finished banner",
    body: "Use an image you designed yourself. It is sent exactly as uploaded, the same for every sender.",
    size: "PNG or JPEG, 600–2400 px wide and 100–800 px tall.",
  },
];

/** A file as base64, without the "data:…;base64," prefix. */
function readBase64(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(",")[1] ?? "");
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

export function MailSection() {
  return (
    <div className="space-y-5">
      <MailboxCard />
      <InboxCard />
      <SignatureCard />
    </div>
  );
}

function MailboxCard() {
  const status = useQuery({ queryKey: ["mail-status"], queryFn: () => api<MailStatus>("/mail/status") });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const connect = async () => {
    setBusy(true);
    setError("");
    try {
      const { url } = await api<{ url: string }>("/mail/connect", { method: "POST" });
      window.location.assign(url);
    } catch (cause) {
      setError(errorMessage(cause, "The mailbox connection could not start."));
      setBusy(false);
    }
  };

  return (
    <Card>
      <CardHeader title="Google Workspace mail" subtitle="Connect your own mailbox to send email through Gmail." />
      <div className="border-t border-line p-5 text-[13px]">
        {status.isPending ? (
          <p className="text-ink-3">Checking connection…</p>
        ) : status.isError ? (
          <p className="text-down">
            The connection could not be checked.{" "}
            <button onClick={() => void status.refetch()} className="underline">
              Retry
            </button>
          </p>
        ) : !status.data.configured ? (
          <p className="text-ink-2">
            Mail setup is incomplete. An admin must configure the redirect URI and encryption key on the server.
          </p>
        ) : status.data.connected ? (
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p>
              <span className="text-ink-3">Connected as</span> <span className="font-medium">{status.data.email}</span>
            </p>
            <button onClick={() => void connect()} disabled={busy} className={buttonSecondary}>
              {busy ? "Connecting…" : "Reconnect mailbox"}
            </button>
          </div>
        ) : (
          <button onClick={() => void connect()} disabled={busy} className={buttonPrimary}>
            {busy ? "Connecting…" : "Connect Google Workspace"}
          </button>
        )}
        {error && (
          <p role="alert" className="mt-3 text-[12.5px] text-down">
            {error}
          </p>
        )}
        <p className="mt-4 text-[12px] text-ink-3">
          If Google says access is blocked while the app is in Testing, an owner of the Google Cloud OAuth app must add
          your address to its test-user list.
        </p>
      </div>
    </Card>
  );
}

function SignatureCard() {
  const admin = useSession().role === "ADMIN";
  const banner = useQuery({ queryKey: ["mail-banner"], queryFn: () => api<Banner>("/mail/banner") });
  const [chosenKind, setChosenKind] = useState<BannerKind | null>(null);
  const [company, setCompany] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  // Until the admin picks or types something, the form shows what is saved.
  const kind = chosenKind ?? banner.data?.kind ?? "logo";
  const companyName = company ?? banner.data?.companyName ?? "";
  const option = KINDS.find((item) => item.kind === kind)!;

  const run = async (action: () => Promise<void>, fallback: string) => {
    setBusy(true);
    setError("");
    try {
      await action();
      await banner.refetch();
    } catch (cause) {
      setError(errorMessage(cause, fallback));
    } finally {
      setBusy(false);
    }
  };
  const upload = (file: File) => {
    if (file.size > 2_000_000 || !["image/png", "image/jpeg"].includes(file.type)) {
      setError("Choose a PNG or JPEG under 2 MB.");
      return;
    }
    void run(async () => {
      await api("/mail/banner", {
        method: "POST",
        body: JSON.stringify({ imageBase64: await readBase64(file), kind, companyName }),
      });
      setChosenKind(null);
      setCompany(null);
      toast(kind === "logo" ? "Signature generated from your logo." : "Banner saved.");
    }, "The image could not be saved.");
  };
  const remove = () =>
    void run(async () => {
      await api("/mail/banner", { method: "DELETE" });
      toast("Email signature removed.");
    }, "The signature could not be removed.");

  return (
    <Card>
      <CardHeader title="Email signature" subtitle="An image added to the end of every email sent from the CRM." />

      <div className="border-t border-line p-5">
        <p className="text-[12px] font-medium text-ink-3">How it looks in an inbox</p>
        {/* A stand-in for the recipient's mail: white paper, the message, then the signature at its real size. */}
        <div className="mt-2 rounded-lg border border-line bg-white p-5 text-[13px] leading-6 text-[#1f2328]">
          <p>Hi Priya,</p>
          <p className="mt-2">Thanks for your time today. I&apos;ve attached the proposal we discussed.</p>
          {banner.isPending ? (
            <div
              className="mt-6 h-[130px] w-full max-w-[520px] animate-pulse rounded-lg bg-[#f1f3f6] motion-reduce:animate-none"
              role="status"
              aria-label="Loading signature"
            />
          ) : banner.data?.previewDataUrl ? (
            // eslint-disable-next-line @next/next/no-img-element -- a data URL from our own server
            <img
              src={banner.data.previewDataUrl}
              alt="Your email signature"
              className="mt-6 w-full max-w-[520px] rounded-lg"
            />
          ) : (
            <p className="mt-6 max-w-[520px] rounded-lg border border-dashed border-[#d7dce3] px-4 py-6 text-center text-[12.5px] text-[#68707d]">
              {banner.isError
                ? "The signature could not be loaded."
                : admin
                  ? "No signature yet. Add one below."
                  : "No signature has been set up. Ask an admin to add one."}
            </p>
          )}
        </div>
        {banner.data?.kind === "logo" && (
          <p className="mt-2 text-[12px] text-ink-3">
            Your name and designation come from your profile. Change them under Settings → Profile.
          </p>
        )}
      </div>

      {admin && (
        <div className="space-y-4 border-t border-line p-5">
          <div className="grid gap-3 sm:grid-cols-2" role="radiogroup" aria-label="Signature type">
            {KINDS.map(({ kind: value, icon: Icon, title, body }) => (
              <button
                key={value}
                type="button"
                role="radio"
                aria-checked={kind === value}
                onClick={() => setChosenKind(value)}
                className={cn(
                  "rounded-lg border p-3.5 text-left transition-colors duration-[var(--duration-hover)]",
                  kind === value ? "border-brand bg-brand-soft/50" : "border-line hover:border-line-strong",
                )}
              >
                <span className="flex items-center gap-2 text-[13px] font-medium">
                  <Icon className={cn("size-4", kind === value ? "text-brand" : "text-ink-3")} aria-hidden />
                  {title}
                </span>
                <span className="mt-1 block text-[12.5px] text-ink-2">{body}</span>
              </button>
            ))}
          </div>

          {kind === "logo" && (
            <Field label="Company name" optional hint="Shown under the sender's name.">
              <input
                value={companyName}
                onChange={(event) => setCompany(event.target.value)}
                maxLength={80}
                placeholder="Webknot Technologies"
                className={cn(fieldControl, "max-w-sm")}
              />
            </Field>
          )}

          <div className="flex flex-wrap items-center gap-2">
            <label className={cn(buttonPrimary, "cursor-pointer", busy && "pointer-events-none opacity-50")}>
              {busy ? "Saving…" : kind === "logo" ? "Upload logo" : "Upload banner"}
              <input
                type="file"
                accept="image/png,image/jpeg"
                className="sr-only"
                disabled={busy}
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) upload(file);
                  event.target.value = "";
                }}
              />
            </label>
            {banner.data?.present && (
              <button onClick={remove} disabled={busy} className={buttonSecondary}>
                <Trash2 className="size-4" aria-hidden />
                Remove signature
              </button>
            )}
            <p className="text-[12px] text-ink-3">{option.size}</p>
          </div>
          {error && (
            <p role="alert" className="text-[12.5px] text-down">
              {error}
            </p>
          )}
        </div>
      )}
    </Card>
  );
}

/** Copies mail with people who are already contacts or leads onto their records. Needs its own, read-only, Google permission. */
function InboxCard() {
  const refresh = useRefresh();
  const status = useQuery({ queryKey: KEYS.inbox, queryFn: inboxStatus, refetchInterval: 60_000 });
  const [deleteImported, setDeleteImported] = useState(false);
  const connect = useMutation({
    mutationFn: async () => {
      const { url } = await api<{ url: string }>("/mail/connect?feature=inbox", { method: "POST" });
      window.location.assign(url);
    },
  });
  const turnOff = useMutation({
    mutationFn: () => turnOffInbox(deleteImported),
    onSuccess: (result) => {
      const copies = deleteImported
        ? "The copied emails were deleted."
        : "Emails already copied stay on their records.";
      toast(`${disconnectMessage("Inbox sync", result)} ${copies}`);
      setDeleteImported(false);
      void refresh([KEYS.inbox, KEYS.emails, ["mail-status"]]);
    },
  });
  const error = connect.isError
    ? errorMessage(connect.error, "The connection could not start.")
    : turnOff.isError
      ? errorMessage(turnOff.error)
      : null;
  const data = status.data;

  return (
    <Card>
      <CardHeader title="Inbox sync" subtitle="Show emails with your contacts and leads on their records." />
      <div className="space-y-4 border-t border-line p-5 text-[13px]">
        {status.isPending ? (
          <p className="text-ink-3">Checking connection…</p>
        ) : status.isError || !data ? (
          <p className="text-down">
            The connection could not be checked.{" "}
            <button onClick={() => void status.refetch()} className="underline">
              Retry
            </button>
          </p>
        ) : !data.configured ? (
          <p className="text-ink-2">
            Google setup is incomplete. An admin must configure the Google client and encryption key on the server.
          </p>
        ) : data.connected ? (
          <>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p>
                <span className="text-ink-3">Reading</span> <span className="font-medium">{data.email}</span>
                <span className="tnum ml-2 text-[12px] text-ink-3">
                  {data.imported} {data.imported === 1 ? "email" : "emails"} copied
                  {data.backfilling
                    ? " · reading the last 30 days…"
                    : data.lastSyncedAt
                      ? ` · last checked ${new Date(data.lastSyncedAt).toLocaleString()}`
                      : ""}
                </span>
              </p>
              <button onClick={() => turnOff.mutate()} disabled={turnOff.isPending} className={buttonSecondary}>
                <Trash2 className="size-4" aria-hidden />
                {turnOff.isPending ? "Turning off…" : "Turn off"}
              </button>
            </div>
            <label className="flex items-center gap-2 text-[12.5px] text-ink-2">
              <input
                type="checkbox"
                checked={deleteImported}
                onChange={(e) => setDeleteImported(e.target.checked)}
                className="accent-brand"
              />
              When turning off, also delete the emails already copied from my mailbox
            </label>
            {data.lastError && (
              <p role="status" className="rounded-lg border border-line bg-canvas px-3 py-2 text-[12.5px] text-ink-2">
                {data.lastError}
              </p>
            )}
          </>
        ) : (
          <div className="space-y-2">
            {data.needsReconnect && (
              <p className="text-[12.5px] text-down">
                Google no longer accepts the saved permission. Connect again to resume.
              </p>
            )}
            <button onClick={() => connect.mutate()} disabled={connect.isPending} className={buttonPrimary}>
              <Inbox className="size-4" aria-hidden />
              {connect.isPending ? "Connecting…" : data.needsReconnect ? "Reconnect inbox sync" : "Turn on inbox sync"}
            </button>
          </div>
        )}
        {error && (
          <p role="alert" className="text-[12.5px] text-down">
            {error}
          </p>
        )}
        <ul className="list-disc space-y-1 pl-5 text-[12.5px] text-ink-3">
          <li>Read-only: the CRM can never send, change or delete anything in your mailbox with this permission.</li>
          <li>
            Only emails to or from someone who is already one of your contacts or leads are kept. Everything else is
            ignored and not stored. Spam, trash and drafts are skipped.
          </li>
          <li>
            What is kept: the subject, who it was from and to, the date, and Gmail&apos;s short preview. Not the full
            message or attachments.
          </li>
          <li>
            Private to you: only you can see the emails copied from your mailbox, even on a contact or lead that
            colleagues can open. They are only attached to contacts and leads you can open yourself.
          </li>
          <li>Starts with the last 30 days, then checks every few minutes.</li>
          <li>
            Google treats reading mail as a sensitive permission. While the Google app is in Testing, an owner of the
            Google Cloud project must add your address as a test user.
          </li>
        </ul>
      </div>
    </Card>
  );
}
