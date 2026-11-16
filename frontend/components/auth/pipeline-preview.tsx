import { cn } from "@/lib/format";

/* Illustration for the sign-in page: a still of what the product looks like once you are in. */

const STAGES = [
  { name: "Qualified", deals: 12, value: "₹62.4L", share: 100, tone: "bg-brand/55" },
  { name: "Demo", deals: 8, value: "₹48.0L", share: 77, tone: "bg-brand/70" },
  { name: "Proposal", deals: 5, value: "₹41.5L", share: 66, tone: "bg-brand/85" },
  { name: "Won", deals: 4, value: "₹38.2L", share: 61, tone: "bg-emerald-400/80" },
];

const FOLLOW_UPS = [
  { time: "10:30", title: "Demo follow-up call", stage: "Demo" },
  { time: "14:00", title: "Send revised proposal", stage: "Proposal" },
  { time: "16:15", title: "Qualify inbound lead", stage: "New" },
];

export function PipelinePreview() {
  return (
    <div aria-hidden className="w-full max-w-[460px] select-none">
      <div className="rounded-xl border border-white/10 bg-nav-2/70 p-5">
        <div className="flex items-baseline justify-between">
          <p className="text-[13px] font-medium text-white">Pipeline</p>
          <p className="text-[12px] text-nav-ink">This quarter</p>
        </div>
        <p className="tnum mt-3 text-[28px] font-semibold tracking-[-0.02em] text-white">
          ₹1.52 Cr
          <span className="ml-2 text-[13px] font-normal tracking-normal text-nav-ink">open · 25 deals</span>
        </p>

        <ul className="mt-5 space-y-3.5">
          {STAGES.map((stage) => (
            <li key={stage.name}>
              <div className="flex items-baseline justify-between text-[12px]">
                <span className="text-nav-ink">
                  {stage.name}
                  <span className="tnum ml-1.5 text-nav-ink/60">{stage.deals}</span>
                </span>
                <span className="tnum font-medium text-white">{stage.value}</span>
              </div>
              <div className="mt-1.5 h-1.5 rounded-full bg-white/[0.06]">
                <div className={cn("h-full rounded-full", stage.tone)} style={{ width: `${stage.share}%` }} />
              </div>
            </li>
          ))}
        </ul>
      </div>

      <div className="mt-3 rounded-xl border border-white/10 bg-nav-2/70 px-5 py-4">
        <div className="flex items-baseline justify-between">
          <p className="text-[13px] font-medium text-white">Due today</p>
          <p className="text-[12px] text-nav-ink">3 follow-ups</p>
        </div>
        <ul className="mt-3 divide-y divide-white/[0.06]">
          {FOLLOW_UPS.map((item) => (
            <li key={item.time} className="flex items-center gap-3 py-2 text-[13px]">
              <span className="tnum w-10 shrink-0 font-mono text-[12px] text-nav-ink">{item.time}</span>
              <span className="min-w-0 flex-1 truncate text-white/90">{item.title}</span>
              <span className="rounded-md bg-white/[0.06] px-1.5 py-0.5 text-[11px] font-medium text-nav-ink">
                {item.stage}
              </span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
