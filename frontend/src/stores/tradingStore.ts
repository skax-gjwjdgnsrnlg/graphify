import { create } from "zustand";
import type { TradingMode } from "@/types/user";

interface TradingState {
  darkMode: boolean;
  enableDarkMode: () => void;
  disableDarkMode: () => void;

  mode: TradingMode;
  setMode: (mode: TradingMode) => void;
}

export const useTradingStore = create<TradingState>((set) => ({
  darkMode: false,
  enableDarkMode: () => set({ darkMode: true }),
  disableDarkMode: () => set({ darkMode: false }),

  mode: "PAPER",
  setMode: (mode) => set({ mode }),
}));
