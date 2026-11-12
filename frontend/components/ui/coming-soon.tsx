import Link from "next/link";
import { Hammer } from "lucide-react";

export function ComingSoon({ title, description }: { title: string; description: string }) {
  return (
    <div className="mx-auto max-w-[1600px] px-4 py-6 sm:px-6 lg:px-8">
      <h1 className="text-[24px] font-semibold tracking-[-0.025em]">{title}</h1>
      <div className="mt-5 flex flex-col items-center rounded-xl border border-dashed border-line-strong bg-surface px-6 py-16 text-center">
        <span className="grid size-11 place-items-center rounded-full bg-brand-soft text-brand">
          <Hammer className="size-5" aria-hidden />
        </span>
        <p className="mt-4 text-[15px] font-semibold">{title} is on the way</p>
        <p className="mt-1 max-w-sm text-[13px] text-ink-3">{description}</p>
        <Link href="/" className="mt-5 text-[13px] font-medium text-brand hover:underline">
          Back to dashboard
        </Link>
      </div>
    </div>
  );
}
