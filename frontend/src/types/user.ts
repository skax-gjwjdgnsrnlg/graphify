import type { AuthProvider } from "@/types/auth";

export type TradingMode = "PAPER" | "LIVE";

export interface UserMe {
  id: number;
  email: string;
  displayName: string;
  authProvider: AuthProvider;
  isPremium: boolean;
  customPrompt: string | null;
  tradingEnabled: boolean;
  tradingMode: TradingMode;
}

export interface TradingSettings {
  tradingEnabled: boolean;
  tradingMode: TradingMode;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface UpdatePromptRequest {
  customPrompt: string;
}

export interface MessageResponse {
  message: string;
}
