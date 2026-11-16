"use client";

import { control } from "@/components/ui/primitives";
import { cn } from "@/lib/format";
import { useId } from "react";


export function Field({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: (props: { id: string; className: string; "aria-invalid": boolean; "aria-describedby"?: string }) => React.ReactNode;
}) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="text-[12.5px] font-medium text-ink-2">
        {label}
      </label>
      {children({
        id,
        className: cn(control, "mt-1.5 w-full px-3", error && "border-down hover:border-down focus:border-down"),
        "aria-invalid": Boolean(error),
        "aria-describedby": error ? `${id}-error` : undefined,
      })}
      {error && (
        <p id={`${id}-error`} className="mt-1 text-[12px] text-down">
          {error}
        </p>
      )}
    </div>
  );
}
