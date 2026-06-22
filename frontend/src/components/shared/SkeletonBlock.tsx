type Tone = "light" | "dark";

interface SkeletonBlockProps {
  className?: string;
  /** Visual tone. "light" (default) for cream pages, "dark" for gray-900 pages. */
  tone?: Tone;
}

const toneBg: Record<Tone, string> = {
  light: "bg-light-cream",
  dark: "bg-white/10",
};

export function SkeletonBlock({ className = "", tone = "light" }: SkeletonBlockProps) {
  return (
    <div
      className={`animate-pulse rounded-md ${toneBg[tone]} ${className}`}
      aria-hidden
    />
  );
}
