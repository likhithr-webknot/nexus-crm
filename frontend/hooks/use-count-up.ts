"use client";

import { useEffect, useRef, useState } from "react";

const DURATION_MS = 700;
/** Fast start, soft landing: the number is nearly there early and settles gently. */
const easeOut = (t: number) => 1 - Math.pow(1 - t, 4);

/**
 * The value, counted up (or down) from what was shown before. A number that changes because of a
 * filter moves from its old value, not from zero. Jumps straight there when the user prefers less motion.
 */
export function useCountUp(target: number) {
  const [shown, setShown] = useState(0);
  const from = useRef(0);

  useEffect(() => {
    const start = from.current;
    if (start === target) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      from.current = target;
      const frame = requestAnimationFrame(() => setShown(target));
      return () => cancelAnimationFrame(frame);
    }
    let frame = 0;
    const began = performance.now();
    const step = (now: number) => {
      const progress = Math.min(1, (now - began) / DURATION_MS);
      const value = start + (target - start) * easeOut(progress);
      from.current = value;
      setShown(progress === 1 ? target : value);
      if (progress < 1) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [target]);

  return shown;
}
