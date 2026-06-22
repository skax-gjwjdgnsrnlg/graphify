type Tone = "light" | "dark";

interface ErrorBannerProps {
  message: string;
  onRetry?: () => void;
  /** Visual tone. "light" (default) for cream pages, "dark" for gray-900 pages. */
  tone?: Tone;
  retryLabel?: string;
}

const toneStyles: Record<Tone, { container: string; retry: string }> = {
  light: {
    container: "border-warm-border bg-cream text-charcoal",
    retry: "text-muted-gray hover:text-charcoal",
  },
  dark: {
    container: "border-red-500/30 bg-red-500/10 text-red-300",
    retry: "text-red-200 hover:text-red-100",
  },
};

export function ErrorBanner({
  message,
  onRetry,
  tone = "light",
  retryLabel = "다시 시도",
}: ErrorBannerProps) {
  const s = toneStyles[tone];
  return (
    <div
      role="alert"
      className={`mb-4 rounded-lg border px-4 py-3 text-sm ${s.container}`}
    >
      <p>{message}</p>
      {onRetry ? (
        <button
          type="button"
          onClick={onRetry}
          className={`mt-2 text-sm underline ${s.retry}`}
        >
          {retryLabel}
        </button>
      ) : null}
    </div>
  );
}
