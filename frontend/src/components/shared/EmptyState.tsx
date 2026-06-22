import { Link } from "react-router-dom";

type Tone = "light" | "dark";

interface EmptyStateProps {
  title: string;
  description?: string;
  suggestions?: string[];
  onSuggestionClick?: (query: string) => void;
  /** Visual tone. "light" (default) for cream pages, "dark" for gray-900 pages. */
  tone?: Tone;
  /** Show the "홈으로 돌아가기" link. Default true; set false when embedded in a section. */
  showHomeLink?: boolean;
}

const toneStyles: Record<
  Tone,
  { container: string; title: string; description: string; chip: string; link: string }
> = {
  light: {
    container: "border-warm-border bg-cream",
    title: "text-charcoal",
    description: "text-muted-gray",
    chip: "border-warm-border text-charcoal hover:bg-charcoal/[0.03]",
    link: "text-charcoal",
  },
  dark: {
    container: "border-white/10 bg-gray-900/50",
    title: "text-gray-200",
    description: "text-gray-400",
    chip: "border-white/10 text-gray-300 hover:bg-white/5",
    link: "text-gray-300",
  },
};

export function EmptyState({
  title,
  description,
  suggestions = [],
  onSuggestionClick,
  tone = "light",
  showHomeLink = true,
}: EmptyStateProps) {
  const s = toneStyles[tone];
  return (
    <div
      className={`flex min-h-[320px] flex-col items-center justify-center rounded-xl border px-6 py-12 text-center ${s.container}`}
    >
      <p className={`text-lg font-medium ${s.title}`}>{title}</p>
      {description ? (
        <p className={`mt-2 max-w-md text-sm ${s.description}`}>{description}</p>
      ) : null}
      {suggestions.length > 0 ? (
        <div className="mt-6 flex flex-wrap justify-center gap-2">
          {suggestions.map((suggestion) => (
            <button
              key={suggestion}
              type="button"
              onClick={() => onSuggestionClick?.(suggestion)}
              className={`rounded-full border px-3 py-1 text-xs ${s.chip}`}
            >
              {suggestion}
            </button>
          ))}
        </div>
      ) : null}
      {showHomeLink ? (
        <Link
          to="/"
          className={`mt-8 text-sm underline underline-offset-2 ${s.link}`}
        >
          홈으로 돌아가기
        </Link>
      ) : null}
    </div>
  );
}
