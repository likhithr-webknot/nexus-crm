/* Motion tokens for motion/react. The same values exist as CSS variables in app/globals.css
   (--duration-*, --ease-*) for plain CSS transitions; change both together. */

/** Seconds. */
export const DURATION = {
  hover: 0.14,
  press: 0.1,
  menu: 0.17,
  menuExit: 0.12,
  dialog: 0.2,
  drawer: 0.26,
  sidebar: 0.22,
  page: 0.2,
  toast: 0.22,
} as const;

export const EASE = {
  /** Things appearing: fast start, soft landing. */
  enter: [0.16, 1, 0.3, 1],
  /** Things moving from one place to another. */
  move: [0.22, 1, 0.36, 1],
  /** Things leaving: accelerate away. */
  exit: [0.4, 0, 1, 1],
} as const;

/** Springs are for physical movement only. Each has its own feel; do not reuse one everywhere. */
export const SPRING = {
  /** A dropped card settling into place: quick, no visible bounce. */
  settle: { type: "spring", stiffness: 520, damping: 40, mass: 0.8 },
  /** Neighbours making room while something is dragged past them. */
  reorder: { type: "spring", stiffness: 380, damping: 34, mass: 0.9 },
} as const;
