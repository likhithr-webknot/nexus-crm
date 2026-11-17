"use client";

import * as Dialog from "@radix-ui/react-dialog";
import { useQuery } from "@tanstack/react-query";
import { ArrowRight, Mic, MicOff, Send, Sparkles, Volume2, X } from "lucide-react";
import Link from "next/link";
import { useRef, useState } from "react";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { api, apiStream, errorMessage } from "@/lib/api";
import { buttonPrimary, buttonSecondary } from "@/components/ui/primitives";
import { streamAssistant, type AiStreamEvent } from "@/lib/ai-stream";
import { AssistantCreateCard } from "./assistant-create-card";

type AiStatus = { configured: boolean; keyName: string; message: string };
type Message = {
  id: string;
  role: "you" | "assistant";
  text: string;
  request?: string;
  action?: string;
  sources?: string[];
  researching?: boolean;
};

function speechChunks(text: string) {
  const chunks: string[] = [];
  let remaining = text.trim();
  while (remaining.length) {
    if (remaining.length <= 2300) {
      chunks.push(remaining);
      break;
    }
    const cut = remaining.lastIndexOf(" ", 2300);
    const length = cut > 1000 ? cut : 2300;
    chunks.push(remaining.slice(0, length));
    remaining = remaining.slice(length).trimStart();
  }
  return chunks;
}

export function AiDrawer() {
  const [open, setOpen] = useState(false);
  const [prompt, setPrompt] = useState("");
  const [messages, setMessages] = useState<Message[]>([]);
  const [busy, setBusy] = useState(false);
  const [recording, setRecording] = useState(false);
  const [voiceBusy, setVoiceBusy] = useState(false);
  const [voiceError, setVoiceError] = useState("");
  const [speakingId, setSpeakingId] = useState<string | null>(null);
  const active = useRef<AbortController | null>(null);
  const recorder = useRef<MediaRecorder | null>(null);
  const audio = useRef<HTMLAudioElement | null>(null);
  const audioUrl = useRef<string | null>(null);
  const playToken = useRef(0);
  const status = useQuery({ queryKey: ["ai-status"], queryFn: () => api<AiStatus>("/ai/status"), enabled: open });
  const voice = useQuery({
    queryKey: ["ai-voice-status"],
    queryFn: () => api<{ configured: boolean }>("/ai/voice/status"),
    enabled: open,
  });
  const creation = (action: string) => (action.startsWith("create_") ? action.slice(7) : null);
  const act = (action: string) => {
    const kind = creation(action);
    if (kind) window.dispatchEvent(new CustomEvent("nexus:create", { detail: kind }));
    setOpen(false);
  };
  const stopSpeech = () => {
    playToken.current += 1;
    audio.current?.pause();
    audio.current = null;
    if (audioUrl.current) URL.revokeObjectURL(audioUrl.current);
    audioUrl.current = null;
    setSpeakingId(null);
  };
  const speak = async (message: Message) => {
    if (speakingId === message.id) {
      stopSpeech();
      return;
    }
    stopSpeech();
    const token = playToken.current;
    setSpeakingId(message.id);
    setVoiceError("");
    try {
      for (const chunk of speechChunks(message.text)) {
        if (token !== playToken.current) break;
        const response = await apiStream("/ai/voice/speak", { method: "POST", body: JSON.stringify({ text: chunk }) });
        if (token !== playToken.current) break;
        const url = URL.createObjectURL(await response.blob());
        audioUrl.current = url;
        const player = new Audio(url);
        audio.current = player;
        await new Promise<void>((resolve, reject) => {
          player.onended = () => resolve();
          player.onpause = () => resolve();
          player.onerror = () => reject(new Error("Audio playback failed."));
          void player.play().catch(reject);
        });
        URL.revokeObjectURL(url);
        audioUrl.current = null;
      }
    } catch (cause) {
      if (token === playToken.current) setVoiceError(errorMessage(cause, "Voice playback failed. Try again."));
    } finally {
      if (token === playToken.current) stopSpeech();
    }
  };
  const toggleRecording = async () => {
    if (recorder.current?.state === "recording") {
      recorder.current.stop();
      setRecording(false);
      return;
    }
    setVoiceError("");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const mime = ["audio/webm", "audio/mp4"].find((type) => MediaRecorder.isTypeSupported(type)) ?? "";
      const capture = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined);
      const chunks: BlobPart[] = [];
      capture.ondataavailable = (event) => {
        if (event.data.size) chunks.push(event.data);
      };
      capture.onstop = async () => {
        stream.getTracks().forEach((track) => track.stop());
        recorder.current = null;
        setRecording(false);
        const file = new Blob(chunks, { type: capture.mimeType || "audio/webm" });
        if (!file.size || file.size > 4_000_000) {
          setVoiceError("Record up to 30 seconds and try again.");
          return;
        }
        const body = new FormData();
        body.append("file", file, "recording");
        setVoiceBusy(true);
        try {
          const result = await api<{ text: string }>("/ai/voice/transcribe", { method: "POST", body });
          setPrompt(result.text);
        } catch (cause) {
          setVoiceError(errorMessage(cause, "Voice input could not be transcribed."));
        } finally {
          setVoiceBusy(false);
        }
      };
      recorder.current = capture;
      capture.start();
      setRecording(true);
      window.setTimeout(() => {
        if (capture.state === "recording") capture.stop();
      }, 30_000);
    } catch {
      setVoiceError("Microphone access is needed for voice input.");
    }
  };
  const send = async (text = prompt) => {
    if (!text.trim() || busy) return;
    const id = crypto.randomUUID();
    const controller = new AbortController();
    active.current = controller;
    setMessages((items) => [
      ...items,
      { id: crypto.randomUUID(), role: "you", text: text.trim() },
      { id, role: "assistant", text: "", request: text.trim() },
    ]);
    setPrompt("");
    setBusy(true);
    try {
      await streamAssistant(
        text.trim(),
        (event: AiStreamEvent) =>
          setMessages((items) =>
            items.map((item) =>
              item.id !== id
                ? item
                : event.name === "start"
                  ? { ...item, researching: event.data.researching }
                  : event.name === "delta"
                    ? { ...item, text: item.text + event.data.text, researching: false }
                    : event.name === "sources"
                      ? { ...item, sources: event.data.urls }
                      : event.name === "done"
                        ? { ...item, action: event.data.action, researching: false }
                        : {
                            ...item,
                            text: item.text ? `${item.text}\n\n${event.data.message}` : event.data.message,
                            researching: false,
                          },
            ),
          ),
        controller.signal,
      );
    } catch (cause) {
      setMessages((items) =>
        items.map((item) =>
          item.id === id
            ? {
                ...item,
                text:
                  item.text ||
                  (controller.signal.aborted
                    ? "Stopped."
                    : errorMessage(cause, "The assistant could not answer. Try again.")),
                researching: false,
              }
            : item,
        ),
      );
    } finally {
      if (active.current === controller) active.current = null;
      setBusy(false);
    }
  };
  return (
    <Dialog.Root
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) {
          active.current?.abort();
          stopSpeech();
          if (recorder.current?.state === "recording") recorder.current.stop();
        }
      }}
    >
      <Dialog.Trigger asChild>
        <button
          aria-label="Open AI assistant"
          title="AI assistant"
          className="grid size-8 place-items-center rounded-md text-ink-2 hover:bg-surface-muted hover:text-brand"
        >
          <Sparkles className="size-[17px]" />
        </button>
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-scrim backdrop-blur-[3px] motion-safe:data-[state=open]:animate-[assistant-overlay-in_300ms_ease-out] motion-safe:data-[state=closed]:animate-[assistant-overlay-out_220ms_ease-in]" />
        <Dialog.Content className="fixed inset-y-0 right-0 z-50 flex w-full max-w-[460px] flex-col border-l border-white/10 bg-surface shadow-[-25px_0_80px_-30px_rgba(15,23,42,.55)] motion-safe:data-[state=open]:animate-[assistant-in_390ms_var(--ease-enter)] motion-safe:data-[state=closed]:animate-[assistant-out_240ms_ease-in]">
          <div className="flex items-center gap-3 border-b border-line bg-gradient-to-r from-brand-soft via-surface to-surface px-5 py-4">
            <span className="grid size-10 place-items-center rounded-xl bg-brand text-white shadow-lg shadow-brand/20">
              <Sparkles className="size-5" />
            </span>
            <div className="flex-1">
              <Dialog.Title className="text-[16px] font-semibold tracking-tight">Nexus assistant</Dialog.Title>
              <Dialog.Description className="text-[11.5px] text-ink-3">
                Research, create records, and work across your CRM.
              </Dialog.Description>
            </div>
            <Dialog.Close asChild>
              <button
                aria-label="Close assistant"
                className="rounded-lg p-1.5 text-ink-3 transition-colors hover:bg-canvas"
              >
                <X className="size-5" />
              </button>
            </Dialog.Close>
          </div>
          <div className="flex-1 space-y-3 overflow-y-auto p-5" role="log" aria-live="polite">
            {status.isPending ? (
              <p className="text-[12px] text-ink-3">Checking AI availability…</p>
            ) : status.isError ? (
              <p className="text-[12px] text-down">AI status could not be checked.</p>
            ) : !status.data?.configured ? (
              <p className="rounded-lg border border-line bg-canvas p-4 text-[12.5px] text-ink-2">
                {status.data?.message}
              </p>
            ) : messages.length === 0 ? (
              <div className="space-y-3">
                <p className="text-[13px] text-ink-2">
                  Ask about your leads, deals and companies, find new companies to approach, or research any company and
                  its market from public sources.
                </p>
                {[
                  "Which of my leads should I follow up first?",
                  "Find companies looking for…",
                  "Research the market for a company",
                  "Add a new contact",
                ].map((text) => (
                  <button
                    key={text}
                    onClick={() =>
                      text.includes("looking for")
                        ? setPrompt("Find companies that are looking for ")
                        : text.includes("a company")
                          ? setPrompt("Research the market for ")
                          : void send(text)
                    }
                    className={buttonSecondary + " w-full justify-start text-left text-[12px]"}
                  >
                    {text}
                  </button>
                ))}
              </div>
            ) : null}
            {messages.map((message) => (
              <div
                key={message.id}
                className={`rounded-2xl p-3.5 text-[12.5px] leading-5 motion-safe:animate-[drawer-in_280ms_var(--ease-enter)] ${message.role === "you" ? "ml-8 bg-brand-soft text-ink" : "mr-5 border border-line bg-canvas/75 text-ink shadow-sm"}`}
              >
                {message.role === "assistant" && message.text ? (
                  <div className="assistant-markdown">
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.text}</ReactMarkdown>
                  </div>
                ) : (
                  <p className="whitespace-pre-wrap">
                    {message.text || (message.researching ? "Researching public sources…" : "Thinking…")}
                  </p>
                )}
                {message.sources && message.sources.length > 0 && (
                  <div className="mt-3 border-t border-line pt-2">
                    <p className="text-[11px] font-semibold text-ink-3">Public sources</p>
                    <ul className="mt-1 space-y-1">
                      {message.sources.map((source) => (
                        <li key={source}>
                          <a
                            href={source}
                            target="_blank"
                            rel="noreferrer"
                            className="block truncate text-brand hover:underline"
                          >
                            {source}
                          </a>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                {message.role === "assistant" && message.text && !busy && voice.data?.configured && (
                  <button
                    onClick={() => void speak(message)}
                    aria-label={speakingId === message.id ? "Stop speaking" : "Speak response"}
                    title={speakingId === message.id ? "Stop speaking" : "Speak response with Sarvam AI"}
                    className="mt-2 inline-flex items-center gap-1 rounded-lg px-1.5 py-1 text-[11px] font-medium text-brand transition-colors hover:bg-brand-soft"
                  >
                    <Volume2 className="size-3.5" />
                    {speakingId === message.id ? "Stop" : "Listen"}
                  </button>
                )}
                {message.action &&
                  message.action !== "none" &&
                  (creation(message.action) === "contact" || creation(message.action) === "company" ? (
                    <AssistantCreateCard
                      kind={creation(message.action) as "contact" | "company"}
                      request={message.request ?? ""}
                    />
                  ) : creation(message.action) ? (
                    <button
                      onClick={() => act(message.action!)}
                      className="mt-2 inline-flex items-center gap-1 font-medium text-brand"
                    >
                      Add {creation(message.action)}
                      <ArrowRight className="size-3" />
                    </button>
                  ) : (
                    <Link
                      href={`/${message.action}`}
                      onClick={() => setOpen(false)}
                      className="mt-2 inline-flex items-center gap-1 font-medium text-brand"
                    >
                      Open {message.action}
                      <ArrowRight className="size-3" />
                    </Link>
                  ))}
              </div>
            ))}
          </div>
          {voiceError && (
            <p role="alert" className="px-5 pt-2 text-[11px] text-down">
              {voiceError}
            </p>
          )}
          <form
            onSubmit={(event) => {
              event.preventDefault();
              void send();
            }}
            className="flex gap-2 border-t border-line bg-surface p-4 shadow-[0_-10px_30px_-25px_rgba(15,23,42,.5)]"
          >
            <input
              value={prompt}
              onChange={(event) => setPrompt(event.target.value)}
              disabled={!status.data?.configured || busy || voiceBusy}
              maxLength={2000}
              aria-label="Ask Nexus assistant"
              placeholder={
                voiceBusy
                  ? "Transcribing your voice…"
                  : recording
                    ? "Recording… click the microphone to stop"
                    : "Ask about your CRM…"
              }
              className="min-w-0 flex-1 rounded-xl border border-line bg-canvas px-3 py-2 text-[13px] outline-none transition-colors focus:border-brand"
            />
            {voice.data?.configured && (
              <button
                type="button"
                onClick={() => void toggleRecording()}
                disabled={voiceBusy || busy}
                aria-label={recording ? "Stop recording" : "Record voice message"}
                className={`grid size-9 shrink-0 place-items-center rounded-xl border transition-colors ${recording ? "tone-chip tone-red animate-pulse" : "border-line text-ink-2 hover:border-brand hover:text-brand"}`}
              >
                {recording ? <MicOff className="size-4" /> : <Mic className="size-4" />}
              </button>
            )}
            <button
              type="submit"
              disabled={!prompt.trim() || busy || !status.data?.configured}
              aria-label="Send question"
              className={buttonPrimary}
            >
              <Send className="size-4" />
            </button>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
