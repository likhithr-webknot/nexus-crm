"use client";

import { UserAvatar, useSession, useSignOut } from "@/components/auth/session";
import { Badge, Card, CardHeader, buttonPrimary, buttonSecondary } from "@/components/ui/primitives";
import { ROLE_LABELS } from "@/lib/auth";
import { cn } from "@/lib/format";
import { useState } from "react";


export function ProfileSection() {
  const user = useSession();
  const { signOut, pending, failed } = useSignOut();
  // Signing out everywhere interrupts other devices, so it takes a second click.
  const [confirming, setConfirming] = useState(false);

  return (
    <Card>
      <CardHeader title="Your profile" subtitle="Your name, email and photo come from your Google account." />
      <div className="flex items-center gap-4 border-t border-line p-5">
        <UserAvatar user={user} className="size-14 text-[17px]" />
        <div className="min-w-0">
          <p className="truncate text-[15px] font-semibold text-ink">{user.displayName}</p>
          <p className="truncate text-[13px] text-ink-2">{user.email}</p>
          <p className="mt-1.5">
            <Badge tone="brand">{ROLE_LABELS[user.role]}</Badge>
          </p>
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line p-5">
        <div>
          <p className="text-[13px] font-medium text-ink">Sign out of all devices</p>
          <p className="text-[12.5px] text-ink-3">Ends every session, including this one. Use it if a device is lost.</p>
          {failed && (
            <p role="alert" className="mt-1 text-[12.5px] text-down">
              Couldn&apos;t reach the server. Try again.
            </p>
          )}
        </div>
        {confirming ? (
          <div className="flex items-center gap-2">
            <button className={buttonSecondary} disabled={pending} onClick={() => setConfirming(false)}>
              Cancel
            </button>
            <button
              className={cn(buttonPrimary, "bg-rose-600 hover:bg-rose-700 disabled:hover:bg-rose-600")}
              disabled={pending}
              onClick={() => signOut({ everywhere: true })}
            >
              {pending ? "Signing out…" : "Yes, sign out everywhere"}
            </button>
          </div>
        ) : (
          <button className={buttonSecondary} onClick={() => setConfirming(true)}>
            Sign out everywhere
          </button>
        )}
      </div>
    </Card>
  );
}
