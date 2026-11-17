import { apiStream } from "@/lib/api";

export type AiStreamEvent =
  | { name: "start"; data: { researching: boolean } }
  | { name: "delta"; data: { text: string } }
  | { name: "sources"; data: { urls: string[] } }
  | { name: "done"; data: { action: string } }
  | { name: "error"; data: { message: string } };

export async function streamAssistant(text: string, onEvent: (event: AiStreamEvent) => void, signal: AbortSignal) {
  const response = await apiStream("/ai/assist/stream", {
    method: "POST",
    body: JSON.stringify({ text }),
    headers: { Accept: "text/event-stream" },
    signal,
  });
  if (!response.body) throw new Error("The assistant stream is unavailable.");
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let pending = "";
  let eventName = "";
  let data: string[] = [];
  let finished = false;
  const dispatch = () => {
    if (eventName && data.length) {
      const event = { name: eventName, data: JSON.parse(data.join("\n")) } as AiStreamEvent;
      onEvent(event);
      if (event.name === "done" || event.name === "error") finished = true;
    }
    eventName = "";
    data = [];
  };
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      pending += decoder.decode(value, { stream: true });
      let newline: number;
      while ((newline = pending.indexOf("\n")) >= 0) {
        const line = pending.slice(0, newline).replace(/\r$/, "");
        pending = pending.slice(newline + 1);
        if (!line) dispatch();
        else if (line.startsWith("event:")) eventName = line.slice(6).trim();
        else if (line.startsWith("data:")) data.push(line.slice(5).trimStart());
      }
    }
    if (pending) {
      data.push(pending);
      dispatch();
    }
    if (!finished && !signal.aborted) throw new Error("The assistant stopped before finishing. Try again.");
  } finally {
    reader.releaseLock();
  }
}
