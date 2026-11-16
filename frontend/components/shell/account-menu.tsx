"use client";

import { UserAvatar, useSession, useSignOut } from "@/components/auth/session";
import { Popover } from "@/components/ui/popover";
import { ROLE_LABELS } from "@/lib/auth";
import { ChevronDown, LogOut, Settings, User } from "lucide-react";
import Link from "next/link";

const LINKS = [
  { href: "/settings", label: "Profile", icon: User },
  { href: "/settings", label: "Settings", icon: Settings },
];

/** The signed-in user's name and photo, with links to their settings and the sign-out button. */
export function AccountMenu() {
  const user = useSession();
  const { signOut, pending, failed } = useSignOut();

  return (
    <Popover
      label="Account menu"
      className="w-60 py-1.5"
      trigger={
        <span className="flex items-center gap-2 py-1 pr-1.5 pl-1">
          <UserAvatar user={user} />
          <span className="hidden max-w-40 truncate text-[13px] font-medium text-ink sm:block">{user.displayName}</span>
          <ChevronDown className="hidden size-4 text-ink-3 sm:block" />
        </span>
      }
    >
      {(close) => (
        <>
          <div className="border-b border-line px-3.5 pt-1.5 pb-2.5">
            <p className="truncate text-[13px] font-semibold">{user.displayName}</p>
            <p className="truncate text-[12px] text-ink-3">{user.email}</p>
            <p className="mt-1 text-[12px] text-ink-3">{ROLE_LABELS[user.role]}</p>
          </div>
          <div className="p-1.5">
            {LINKS.map(({ href, label, icon: Icon }) => (
              <Link key={label} href={href} onClick={close} className="flex items-center gap-2.5 rounded-md px-2 py-1.5 text-[13px] text-ink-2 hover:bg-canvas hover:text-ink">
                <Icon className="size-4" /> {label}
              </Link>
            ))}
            <button
              onClick={() => signOut()}
              disabled={pending}
              className="flex w-full items-center gap-2.5 rounded-md px-2 py-1.5 text-[13px] text-down hover:bg-danger-soft disabled:opacity-50"
            >
              <LogOut className="size-4" /> {pending ? "Signing out…" : "Sign out"}
            </button>
            {failed && (
              <p role="alert" className="px-2 pt-1 text-[12px] text-down">
                Couldn&apos;t reach the server. Try again.
              </p>
            )}
          </div>
        </>
      )}
    </Popover>
  );
}
