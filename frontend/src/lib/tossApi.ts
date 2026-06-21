import { apiGet, apiPost } from "@/lib/apiClient";

const BASE = "/api/v1/toss";

export interface TossCredentialStatus {
  configured: boolean;
  tokenValid: boolean;
  tokenExpiresAt: string | null;
}

export interface TossAccount {
  accountNumber: string;
  accountName: string;
  balance: number;
  availableBalance: number;
}

export async function fetchTossStatus() {
  return apiGet<TossCredentialStatus>(`${BASE}/credentials/status`);
}

export async function saveTossCredentials(clientId: string, clientSecret: string) {
  return apiPost<TossCredentialStatus, { clientId: string; clientSecret: string }>(
    `${BASE}/credentials`,
    { clientId, clientSecret }
  );
}

export async function refreshTossToken() {
  return apiPost<TossCredentialStatus, null>(
    `${BASE}/credentials/token/refresh`,
    null
  );
}

export async function fetchTossAccounts() {
  return apiGet<TossAccount[]>(`${BASE}/accounts`);
}
