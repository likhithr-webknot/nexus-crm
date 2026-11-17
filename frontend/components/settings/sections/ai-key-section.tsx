"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
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
import { api, errorMessage } from "@/lib/api";

type KeyStatus = {
  configured: boolean;
  keyName: string;
  source: "ADMIN" | "SERVER" | "NONE";
  updatedAt: string | null;
};

type Provider = {
  /** Where the key is read and saved. */
  path: string;
  title: string;
  subtitle: string;
  product: string;
  placeholder: string;
  /** Queries that depend on this key, refreshed after a change. */
  affects: string[];
};

const PROVIDERS: Provider[] = [
  {
    path: "/ai/admin/key",
    title: "Assistant · OpenAI",
    subtitle: "Powers CRM answers, company research, email drafts and workflow building.",
    product: "OpenAI",
    placeholder: "sk-…",
    affects: ["ai-status"],
  },
  {
    path: "/ai/admin/key/sarvam",
    title: "Voice · Sarvam AI",
    subtitle: "Powers speaking to the assistant and having its answers read aloud.",
    product: "Sarvam AI",
    placeholder: "Sarvam API subscription key",
    affects: ["ai-voice-status"],
  },
];

export function AiKeySection() {
  return (
    <div className="space-y-5">
      {PROVIDERS.map((provider) => (
        <KeyCard key={provider.path} provider={provider} />
      ))}
      <p className="text-[12px] text-ink-3">
        Keys are encrypted on the server and never shown again. Removing a key saved here falls back to the
        server&apos;s own key, if it has one.
      </p>
    </div>
  );
}

function KeyCard({ provider }: { provider: Provider }) {
  const client = useQueryClient();
  const status = useQuery({ queryKey: ["ai-admin-key", provider.path], queryFn: () => api<KeyStatus>(provider.path) });
  const [name, setName] = useState("");
  const [key, setKey] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const run = async (action: () => Promise<unknown>, done: string, fallback: string) => {
    setBusy(true);
    setError("");
    try {
      await action();
      setKey("");
      setName("");
      await Promise.all([
        status.refetch(),
        ...provider.affects.map((queryKey) => client.invalidateQueries({ queryKey: [queryKey] })),
      ]);
      toast(done);
    } catch (cause) {
      setError(errorMessage(cause, fallback));
    } finally {
      setBusy(false);
    }
  };
  const save = () =>
    run(
      () => api(provider.path, { method: "PUT", body: JSON.stringify({ key: key.trim(), name: name.trim() }) }),
      `${provider.product} key saved.`,
      "The key could not be saved.",
    );
  const remove = () =>
    run(
      () => api(provider.path, { method: "DELETE" }),
      `${provider.product} key removed.`,
      "The key could not be removed.",
    );

  const source = status.data?.source;
  return (
    <Card>
      <CardHeader
        title={provider.title}
        subtitle={provider.subtitle}
        action={
          status.data && (
            <Badge tone={status.data.configured ? "green" : "amber"}>
              {status.data.configured ? "Active" : "Not set"}
            </Badge>
          )
        }
      />
      <div className="space-y-4 border-t border-line p-5">
        {status.isPending ? (
          <p className="text-[12.5px] text-ink-3">Checking key…</p>
        ) : status.isError ? (
          <p className="text-[12.5px] text-down">
            The key status could not be loaded.{" "}
            <button onClick={() => void status.refetch()} className="underline">
              Retry
            </button>
          </p>
        ) : (
          <p className="text-[13px] text-ink-2">
            {source === "ADMIN" && (
              <>
                Using <span className="font-medium text-ink">{status.data.keyName}</span>, saved here by an admin.
              </>
            )}
            {source === "SERVER" && "Using the key from the server's environment. Save one here to replace it."}
            {source === "NONE" && `No ${provider.product} key yet. Add one to turn this on.`}
          </p>
        )}
        <form
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
          className="grid max-w-2xl gap-4 sm:grid-cols-2"
        >
          <Field label="Key name" hint="A label for your team, such as the account it bills to.">
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={80}
              required
              placeholder={`Primary ${provider.product} key`}
              className={fieldControl}
            />
          </Field>
          <Field label={`${provider.product} API key`}>
            <input
              type="password"
              value={key}
              onChange={(event) => setKey(event.target.value)}
              autoComplete="new-password"
              required
              placeholder={provider.placeholder}
              className={fieldControl}
            />
          </Field>
          <div className="flex flex-wrap gap-2 sm:col-span-2">
            <button type="submit" disabled={busy || !name.trim() || !key.trim()} className={buttonPrimary}>
              {busy ? "Saving…" : source === "ADMIN" ? "Replace key" : "Save key"}
            </button>
            {source === "ADMIN" && (
              <button type="button" onClick={() => void remove()} disabled={busy} className={buttonSecondary}>
                Remove key
              </button>
            )}
          </div>
        </form>
        {error && (
          <p role="alert" className="text-[12.5px] text-down">
            {error}
          </p>
        )}
      </div>
    </Card>
  );
}
