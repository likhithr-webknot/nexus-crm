"use client";

import { UserAvatar, useSession, useSignOut } from "@/components/auth/session";
import {
  Badge,
  Card,
  CardHeader,
  Field,
  buttonPrimary,
  buttonSecondary,
  fieldControl,
} from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { errorMessage } from "@/lib/api";
import { ROLE_LABELS, updateProfile } from "@/lib/auth";
import { cn } from "@/lib/format";
import { KEYS } from "@/lib/query-keys";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

export function ProfileSection() {
  return (
    <div className="space-y-5">
      <ProfileCard />
      <SignOutEverywhereCard />
    </div>
  );
}

function ProfileCard() {
  const user = useSession();
  const client = useQueryClient();
  // null means "not edited": the field shows what is saved.
  const [name, setName] = useState<string | null>(null);
  const [title, setTitle] = useState<string | null>(null);
  const displayName = name ?? user.displayName;
  const jobTitle = title ?? user.jobTitle ?? "";
  const changed = displayName.trim() !== user.displayName || jobTitle.trim() !== (user.jobTitle ?? "");

  const save = useMutation({
    mutationFn: () => updateProfile({ displayName: displayName.trim(), jobTitle: jobTitle.trim() }),
    onSuccess: async (saved) => {
      client.setQueryData(KEYS.me, saved);
      setName(null);
      setTitle(null);
      // Names appear on records, team lists and the email signature.
      await Promise.all(
        [KEYS.users, KEYS.assignable, ["mail-banner"]].map((queryKey) => client.invalidateQueries({ queryKey })),
      );
      toast("Profile updated.");
    },
  });

  return (
    <Card>
      <CardHeader
        title="Your profile"
        subtitle="How your name appears on records, in the team list and in your email signature."
      />
      <div className="flex items-center gap-4 border-t border-line p-5">
        <UserAvatar user={user} className="size-14 text-[17px]" />
        <div className="min-w-0">
          <p className="truncate text-[15px] font-semibold text-ink">{user.displayName}</p>
          <p className="truncate text-[13px] text-ink-2">{[user.jobTitle, user.email].filter(Boolean).join(" · ")}</p>
          <p className="mt-1.5">
            <Badge tone="brand">{ROLE_LABELS[user.role]}</Badge>
          </p>
        </div>
      </div>
      <form
        className="border-t border-line p-5"
        onSubmit={(event) => {
          event.preventDefault();
          if (changed && displayName.trim()) save.mutate();
        }}
      >
        <div className="grid max-w-2xl gap-4 sm:grid-cols-2">
          <Field label="Name">
            <input
              value={displayName}
              onChange={(event) => setName(event.target.value)}
              required
              maxLength={120}
              autoComplete="name"
              className={fieldControl}
            />
          </Field>
          <Field label="Designation" optional>
            <input
              value={jobTitle}
              onChange={(event) => setTitle(event.target.value)}
              maxLength={120}
              autoComplete="organization-title"
              placeholder="Software Engineer"
              className={fieldControl}
            />
          </Field>
        </div>
        <p className="mt-3 text-[12px] text-ink-3">
          Your email and photo come from your Google account and can&apos;t be changed here.
        </p>
        {save.isError && (
          <p role="alert" className="mt-3 text-[12.5px] text-down">
            {errorMessage(save.error, "Your profile could not be saved. Try again.")}
          </p>
        )}
        <div className="mt-4 flex gap-2">
          <button type="submit" disabled={!changed || !displayName.trim() || save.isPending} className={buttonPrimary}>
            {save.isPending ? "Saving…" : "Save changes"}
          </button>
          {changed && (
            <button
              type="button"
              disabled={save.isPending}
              className={buttonSecondary}
              onClick={() => {
                setName(null);
                setTitle(null);
              }}
            >
              Discard
            </button>
          )}
        </div>
      </form>
    </Card>
  );
}

function SignOutEverywhereCard() {
  const { signOut, pending, failed } = useSignOut();
  // Signing out everywhere interrupts other devices, so it takes a second click.
  const [confirming, setConfirming] = useState(false);

  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3 p-5">
        <div>
          <p className="text-[13px] font-medium text-ink">Sign out of all devices</p>
          <p className="text-[12.5px] text-ink-3">
            Ends every session, including this one. Use it if a device is lost.
          </p>
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
              className={cn(buttonPrimary, "bg-danger hover:bg-danger-strong disabled:hover:bg-danger")}
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
