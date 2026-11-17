"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { CalendarPlus, CalendarSync, Copy, RefreshCw, Trash2 } from "lucide-react";
import { useState } from "react";
import { Card, CardHeader, buttonPrimary, buttonSecondary, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { useRefresh } from "@/hooks/use-refresh";
import { api, errorMessage } from "@/lib/api";
import { cn } from "@/lib/format";

type FeedStatus = { enabled: boolean };
type FeedLink = { url: string; webcalUrl: string };

const FEED_KEY = ["calendar-feed"] as const;

type GoogleStatus = {
  configured: boolean;
  connected: boolean;
  needsReconnect: boolean;
  email: string | null;
  lastSyncedAt: string | null;
  lastError: string | null;
};

const GOOGLE_KEY = ["calendar-google"] as const;

export function CalendarSection() {
  return (
    <div className="space-y-5">
      <GoogleCard />
      <FeedCard />
    </div>
  );
}

/** A private link that Apple Calendar, Outlook or any calendar app can subscribe to. It shows the user's follow-ups. */
function FeedCard() {
  const refresh = useRefresh();
  const status = useQuery({ queryKey: FEED_KEY, queryFn: () => api<FeedStatus>("/calendar/feed") });
  // The secret is only known right after it is made. It is never stored on the page.
  const [link, setLink] = useState<FeedLink | null>(null);

  const make = useMutation({
    mutationFn: () => api<FeedLink>("/calendar/feed", { method: "POST" }),
    onSuccess: (made) => {
      setLink(made);
      void refresh([FEED_KEY]);
    },
  });
  const revoke = useMutation({
    mutationFn: () => api("/calendar/feed", { method: "DELETE" }),
    onSuccess: () => {
      setLink(null);
      toast("Calendar link removed. Apps that used it stop updating.");
      void refresh([FEED_KEY]);
    },
  });
  const error = make.isError ? errorMessage(make.error) : revoke.isError ? errorMessage(revoke.error) : null;

  const copy = async () => {
    if (!link) return;
    try {
      await navigator.clipboard.writeText(link.url);
      toast("Link copied.");
    } catch {
      toast("Copy failed. Select the link and copy it by hand.");
    }
  };

  return (
    <Card>
      <CardHeader
        title="Apple Calendar and other calendar apps"
        subtitle="Your follow-ups appear in your own calendar app, one way: changes you make here show up there."
      />
      <div className="space-y-4 border-t border-line p-5 text-[13px]">
        {status.isPending ? (
          <p className="text-ink-3">Checking…</p>
        ) : status.isError ? (
          <p className="text-down">
            This could not be loaded.{" "}
            <button onClick={() => void status.refetch()} className="underline">
              Retry
            </button>
          </p>
        ) : (
          <>
            {link && (
              <div className="space-y-2 rounded-lg border border-line bg-canvas p-3.5">
                <p className="font-medium text-ink">Your private link</p>
                <p className="text-[12.5px] text-ink-2">
                  Anyone with this link can see your follow-ups, so keep it to yourself. It is shown only now. If you
                  lose it, make a new one.
                </p>
                <div className="flex flex-wrap gap-2">
                  <input
                    readOnly
                    value={link.url}
                    aria-label="Calendar link"
                    onFocus={(e) => e.currentTarget.select()}
                    className={cn(fieldControl, "min-w-0 flex-1 font-mono text-[12px]")}
                  />
                  <button onClick={() => void copy()} className={buttonSecondary}>
                    <Copy className="size-4" aria-hidden />
                    Copy
                  </button>
                  <a href={link.webcalUrl} className={buttonPrimary}>
                    <CalendarPlus className="size-4" aria-hidden />
                    Add to Apple Calendar
                  </a>
                </div>
              </div>
            )}

            <div className="flex flex-wrap items-center gap-2">
              <button
                onClick={() => make.mutate()}
                disabled={make.isPending}
                className={status.data.enabled ? buttonSecondary : buttonPrimary}
              >
                {status.data.enabled ? (
                  <RefreshCw className="size-4" aria-hidden />
                ) : (
                  <CalendarPlus className="size-4" aria-hidden />
                )}
                {make.isPending ? "Working…" : status.data.enabled ? "Make a new link" : "Create calendar link"}
              </button>
              {status.data.enabled && (
                <button onClick={() => revoke.mutate()} disabled={revoke.isPending} className={buttonSecondary}>
                  <Trash2 className="size-4" aria-hidden />
                  Remove link
                </button>
              )}
            </div>
            {status.data.enabled && !link && (
              <p className="text-[12.5px] text-ink-3">A link is active. Making a new one stops the old one working.</p>
            )}

            <ul className="list-disc space-y-1 pl-5 text-[12.5px] text-ink-3">
              <li>Shows follow-ups due from 30 days ago to 6 months ahead, each as a 30-minute event.</li>
              <li>
                Apple Calendar refreshes subscribed calendars on its own schedule (often every hour or so), not
                instantly.
              </li>
              <li>To change an event, change the follow-up here. Edits made in the calendar app are not sent back.</li>
            </ul>
          </>
        )}
        {error && (
          <p role="alert" className="text-[12.5px] text-down">
            {error}
          </p>
        )}
      </div>
    </Card>
  );
}

/** Pushes follow-ups into the user's Google Calendar. One way: Google edits are not read back. */
function GoogleCard() {
  const refresh = useRefresh();
  const status = useQuery({
    queryKey: GOOGLE_KEY,
    queryFn: () => api<GoogleStatus>("/calendar/google"),
    refetchInterval: 60_000,
  });
  const connect = useMutation({
    mutationFn: async () => {
      const { url } = await api<{ url: string }>("/mail/connect?feature=calendar", { method: "POST" });
      window.location.assign(url);
    },
  });
  const disconnect = useMutation({
    mutationFn: () => api("/calendar/google", { method: "DELETE" }),
    onSuccess: () => {
      toast("Google Calendar disconnected. Events made by the CRM were removed where possible.");
      void refresh([GOOGLE_KEY, ["mail-status"]]);
    },
  });
  const error = connect.isError
    ? errorMessage(connect.error, "The connection could not start.")
    : disconnect.isError
      ? errorMessage(disconnect.error)
      : null;
  const data = status.data;

  return (
    <Card>
      <CardHeader
        title="Google Calendar"
        subtitle="Follow-ups are added to your Google Calendar automatically. Changes here appear there within a minute or two."
      />
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
                <span className="text-ink-3">Syncing to</span> <span className="font-medium">{data.email}</span>
                <span className="ml-2 text-[12px] text-ink-3">
                  {data.lastSyncedAt
                    ? `Last synced ${new Date(data.lastSyncedAt).toLocaleString()}`
                    : "Waiting for the first sync…"}
                </span>
              </p>
              <button onClick={() => disconnect.mutate()} disabled={disconnect.isPending} className={buttonSecondary}>
                <Trash2 className="size-4" aria-hidden />
                {disconnect.isPending ? "Disconnecting…" : "Disconnect"}
              </button>
            </div>
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
                Google no longer accepts the saved permission. Connect again to resume syncing.
              </p>
            )}
            <button onClick={() => connect.mutate()} disabled={connect.isPending} className={buttonPrimary}>
              <CalendarSync className="size-4" aria-hidden />
              {connect.isPending
                ? "Connecting…"
                : data.needsReconnect
                  ? "Reconnect Google Calendar"
                  : "Connect Google Calendar"}
            </button>
          </div>
        )}
        {error && (
          <p role="alert" className="text-[12.5px] text-down">
            {error}
          </p>
        )}
        <ul className="list-disc space-y-1 pl-5 text-[12.5px] text-ink-3">
          <li>
            Use the same Google account you sign in to the CRM with. The CRM only creates and edits its own events, and
            shows follow-ups due from last week to three months ahead.
          </li>
          <li>
            One way only: changes you make to an event in Google are not read back, and an event you delete in Google
            stays deleted.
          </li>
          <li>
            While the Google app is in Testing, an owner of the Google Cloud project must add your address as a test
            user.
          </li>
        </ul>
      </div>
    </Card>
  );
}
