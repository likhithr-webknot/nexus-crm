const FEATURES = [
  { title: "Lead context", description: "Keep each conversation, owner and next step together." },
  { title: "Pipeline view", description: "Move deals between stages and see their history." },
  { title: "Follow-ups", description: "Plan work in one calendar and keep the team aligned." },
];

export function PipelinePreview() {
  return (
    <div aria-hidden className="w-full max-w-[460px] space-y-3">
      {FEATURES.map((feature, index) => (
        <div key={feature.title} className="rounded-xl border border-white/10 bg-nav-2/70 px-5 py-4">
          <div className="flex items-center gap-3">
            <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-white/[0.08] text-[12px] font-semibold text-white/80">0{index + 1}</span>
            <div><p className="text-[13px] font-semibold text-white">{feature.title}</p><p className="mt-0.5 text-[12px] leading-5 text-nav-ink">{feature.description}</p></div>
          </div>
        </div>
      ))}
    </div>
  );
}
