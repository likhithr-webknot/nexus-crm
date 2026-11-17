"use client";

import { useQuery } from "@tanstack/react-query";
import { Save, Send, Sparkles } from "lucide-react";
import { useState } from "react";
import { Dialog, DialogBody, FormError } from "@/components/ui/dialog";
import { buttonPrimary, buttonSecondary, fieldControl } from "@/components/ui/primitives";
import { toast } from "@/components/ui/toast";
import { api, errorMessage } from "@/lib/api";
import { isValidEmail, MAX_COPIES, splitAddresses } from "@/lib/email";
import { modulesApi, type ModuleRecord } from "@/lib/modules";

type MailStatus = { configured: boolean; connected: boolean; email: string | null };

export function CommunicationComposer({
  record,
  onClose,
  onSaved,
}: {
  record?: ModuleRecord;
  onClose: () => void;
  onSaved: (item: ModuleRecord) => Promise<unknown>;
}) {
  const [recipient, setRecipient] = useState(record?.recipient ?? "");
  const [cc, setCc] = useState((record?.cc ?? []).join(", "));
  const [bcc, setBcc] = useState((record?.bcc ?? []).join(", "));
  const [showCc, setShowCc] = useState(Boolean(record?.cc?.length));
  const [showBcc, setShowBcc] = useState(Boolean(record?.bcc?.length));
  const [subject, setSubject] = useState(record?.name ?? "");
  const [account, setAccount] = useState(record?.account ?? "");
  const [body, setBody] = useState(record?.description ?? "");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const [drafting, setDrafting] = useState(false);
  const [suggestion, setSuggestion] = useState("");
  const [draftId, setDraftId] = useState(record?.id ?? null);
  const mail = useQuery({ queryKey: ["mail-status"], queryFn: () => api<MailStatus>("/mail/status") });

  const connect = async () => {
    setError("");
    try {
      const { url } = await api<{ url: string }>("/mail/connect", { method: "POST" });
      window.location.assign(url);
    } catch (cause) {
      setError(errorMessage(cause, "The mailbox connection could not start."));
    }
  };
  const draftWithAi = async () => {
    setDrafting(true);
    setError("");
    try {
      const context = `Recipient: ${recipient}. Account: ${account}. Subject: ${subject}. Context and notes: ${body}`;
      const result = await api<{ body: string }>("/ai/draft-email", {
        method: "POST",
        body: JSON.stringify({ text: context }),
      });
      setSuggestion(result.body);
    } catch (cause) {
      setError(errorMessage(cause, "AI could not draft this email."));
    } finally {
      setDrafting(false);
    }
  };
  const save = async (send: boolean) => {
    if (!subject.trim() || subject.trim().length < 2) {
      setError("Add a subject of at least two characters.");
      return;
    }
    const to = recipient.trim().toLowerCase();
    const copies = { Cc: splitAddresses(cc), Bcc: splitAddresses(bcc) };
    if ((send || to) && !isValidEmail(to)) {
      setError("Enter one valid email address in To. Use Cc for more people.");
      return;
    }
    for (const [label, list] of Object.entries(copies)) {
      const bad = list.find((address) => !isValidEmail(address));
      if (bad) {
        setError(`${label} has an address that is not valid: ${bad}`);
        return;
      }
      if (list.length > MAX_COPIES) {
        setError(`${label} can have at most ${MAX_COPIES} addresses.`);
        return;
      }
    }
    const repeated = [...copies.Bcc, ...copies.Cc, to].find(
      (address, index, all) => address && all.indexOf(address) !== index,
    );
    if (repeated) {
      setError(`${repeated} is listed more than once across To, Cc and Bcc.`);
      return;
    }
    setSaving(true);
    setError("");
    try {
      const values = {
        name: subject.trim(),
        status: "Draft",
        account: account.trim(),
        description: body,
        amount: 0,
        recipient: to,
        cc: copies.Cc,
        bcc: copies.Bcc,
        bodyHtml: "",
      };
      const draft = draftId
        ? await modulesApi.update("communications", draftId, values)
        : await modulesApi.create("communications", values);
      setDraftId(draft.id);
      const item = send ? await api<ModuleRecord>(`/mail/send/${draft.id}`, { method: "POST" }) : draft;
      await onSaved(item);
      toast(send ? "Email sent through Gmail." : "Draft saved. It expires 30 days after creation.");
      onClose();
    } catch (cause) {
      setError(
        errorMessage(
          cause,
          send ? "The email could not be sent. Your draft was saved." : "The draft could not be saved.",
        ),
      );
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog
      open
      onClose={onClose}
      title={record ? "Edit email" : "Compose email"}
      description="Send from your connected Google Workspace mailbox."
      size="lg"
    >
      <DialogBody>
        <div className="rounded-lg border border-line bg-canvas px-3 py-2 text-[12px] text-ink-2">
          {mail.isPending ? (
            "Checking mailbox…"
          ) : mail.isError ? (
            "Mailbox status unavailable."
          ) : !mail.data?.configured ? (
            "Mail connection is not configured. Ask an admin to complete setup."
          ) : mail.data.connected ? (
            `Sending as ${mail.data.email}`
          ) : (
            <span>
              Mailbox not connected.{" "}
              <button onClick={() => void connect()} className="font-medium text-brand underline">
                Connect Google Workspace
              </button>
            </span>
          )}
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="text-[12px] font-medium text-ink-2">
            To
            <input
              type="email"
              value={recipient}
              onChange={(event) => setRecipient(event.target.value)}
              placeholder="name@company.com"
              className={fieldControl}
            />
          </label>
          <label className="text-[12px] font-medium text-ink-2">
            Account
            <input
              value={account}
              onChange={(event) => setAccount(event.target.value)}
              placeholder="Company name"
              className={fieldControl}
            />
          </label>
        </div>
        {(!showCc || !showBcc) && (
          <div className="flex gap-4 text-[12px]">
            {!showCc && (
              <button type="button" onClick={() => setShowCc(true)} className="font-medium text-brand hover:underline">
                Add Cc
              </button>
            )}
            {!showBcc && (
              <button type="button" onClick={() => setShowBcc(true)} className="font-medium text-brand hover:underline">
                Add Bcc
              </button>
            )}
          </div>
        )}
        {showCc && (
          <label className="text-[12px] font-medium text-ink-2">
            Cc
            <input
              value={cc}
              onChange={(event) => setCc(event.target.value)}
              placeholder="Separate addresses with commas"
              autoComplete="off"
              className={fieldControl}
            />
          </label>
        )}
        {showBcc && (
          <label className="text-[12px] font-medium text-ink-2">
            Bcc
            <input
              value={bcc}
              onChange={(event) => setBcc(event.target.value)}
              placeholder="Hidden from the other people on this email"
              autoComplete="off"
              className={fieldControl}
            />
          </label>
        )}
        <label className="text-[12px] font-medium text-ink-2">
          Subject
          <input
            value={subject}
            onChange={(event) => setSubject(event.target.value)}
            maxLength={160}
            className={fieldControl}
          />
        </label>
        <label className="text-[12px] font-medium text-ink-2">
          Message
          <textarea
            value={body}
            onChange={(event) => setBody(event.target.value)}
            rows={12}
            maxLength={10000}
            className={`${fieldControl} h-auto py-2`}
          />
        </label>
        <button onClick={() => void draftWithAi()} disabled={drafting} className={buttonSecondary}>
          <Sparkles className="size-4" />
          {drafting ? "Drafting…" : "Draft with AI from this context"}
        </button>
        {suggestion && (
          <div className="rounded-lg border border-brand/30 bg-brand-soft p-3 text-[12px]">
            <p className="whitespace-pre-wrap">{suggestion}</p>
            <button
              onClick={() => {
                setBody(suggestion);
                setSuggestion("");
              }}
              className="mt-3 font-medium text-brand underline"
            >
              Use this draft
            </button>
          </div>
        )}
        <p className="text-[11.5px] text-ink-3">
          Drafts are stored in Nexus CRM and automatically deleted 30 days after creation.
        </p>
        {error && (
          <>
            <FormError>{error}</FormError>
            {error.includes("PERMISSION_DENIED") && (
              <button
                type="button"
                onClick={() => void connect()}
                className="text-left text-[12px] font-medium text-brand underline"
              >
                Reconnect Google Workspace before retrying
              </button>
            )}
          </>
        )}
      </DialogBody>
      <footer className="flex flex-wrap justify-end gap-2 border-t border-line bg-canvas/50 px-5 py-3.5">
        <button onClick={onClose} className={buttonSecondary}>
          Cancel
        </button>
        <button onClick={() => void save(false)} disabled={saving} className={buttonSecondary}>
          <Save className="size-4" />
          Save draft
        </button>
        <button onClick={() => void save(true)} disabled={saving || !mail.data?.connected} className={buttonPrimary}>
          <Send className="size-4" />
          Send email
        </button>
      </footer>
    </Dialog>
  );
}
