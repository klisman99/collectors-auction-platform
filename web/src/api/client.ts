import { client } from './generated/client.gen';
import {
  csrfToken as requestCsrfToken,
  getAuthenticatedSession as requestAuthenticatedSession,
  getPlatformStatus as requestPlatformStatus,
  registerRegularAccount,
  requestPasswordRecovery as requestPasswordRecoveryCommand,
  resetPassword as resetPasswordCommand,
  revokeAllSessions as revokeAllSessionsCommand,
  signOut as signOutCommand,
  signInRegularAccount,
  verifyRegularAccountEmail,
} from './generated/sdk.gen';
import type {
  AuthenticatedSession,
  EmailVerification,
  PlatformStatus,
  Registration,
  RegistrationRequest,
  PasswordRecoveryRequest,
  PasswordRecoveryRequestAccepted,
  PasswordResetRequest,
  PasswordReset,
  SignInRequest,
} from './generated/types.gen';

export type {
  AuthenticatedSession,
  PasswordRecoveryRequest,
  PasswordRecoveryRequestAccepted,
  PasswordReset,
  PasswordResetRequest,
  PlatformStatus,
  Registration,
  RegistrationRequest,
  SignInRequest,
} from './generated/types.gen';

export type Draft = { id: string; category: string; otherCategoryLabel?: string; title: string; description: string; condition: string; conditionNotes: string; ownershipDeclared: boolean; images: Array<{ id: string; url: string; contentType: string; sortOrder: number }> };

client.setConfig({ baseUrl: '/', credentials: 'same-origin' });

export type ApiProblem = {
  code?: string;
  detail?: string;
  fieldErrors?: Array<{ field?: string; message?: string }>;
};

export class ApiError extends Error {
  readonly code?: string;
  readonly fieldErrors: ApiProblem['fieldErrors'];

  constructor(problem: ApiProblem, fallbackMessage: string) {
    super(problem.detail ?? fallbackMessage);
    this.name = 'ApiError';
    this.code = problem.code;
    this.fieldErrors = problem.fieldErrors;
  }
}

export async function getPlatformStatus(): Promise<PlatformStatus> {
  const { data, error } = await requestPlatformStatus();

  return required(data, error, 'The platform status endpoint did not return a status document.');
}

export async function getAuthenticatedSession(): Promise<AuthenticatedSession | null> {
  const { data } = await requestAuthenticatedSession();
  return data ?? null;
}

export async function registerAccount(input: RegistrationRequest): Promise<Registration> {
  const { data, error } = await registerRegularAccount({ body: input, ...(await csrfHeaders()) });
  return required(data, error, 'Registration could not be completed.');
}

export async function verifyEmail(token: string): Promise<EmailVerification> {
  const { data, error } = await verifyRegularAccountEmail({ body: { token }, ...(await csrfHeaders()) });
  return required(data, error, 'Email verification could not be completed.');
}

export async function signIn(input: SignInRequest): Promise<AuthenticatedSession> {
  const { data, error } = await signInRegularAccount({ body: input, ...(await csrfHeaders()) });
  return required(data, error, 'Sign-in could not be completed.');
}

export async function requestPasswordRecovery(input: PasswordRecoveryRequest): Promise<PasswordRecoveryRequestAccepted> {
  const { data, error } = await requestPasswordRecoveryCommand({ body: input, ...(await csrfHeaders()) });
  return required(data, error, 'Password recovery could not be requested.');
}

export async function resetPassword(input: PasswordResetRequest): Promise<PasswordReset> {
  const { data, error } = await resetPasswordCommand({ body: input, ...(await csrfHeaders()) });
  return required(data, error, 'Password could not be reset.');
}

export async function signOut(): Promise<void> {
  const { error } = await signOutCommand(await csrfHeaders());
  if (error !== undefined) {
    throw new ApiError(error as ApiProblem, 'Sign-out could not be completed.');
  }
}

export async function revokeAllSessions(): Promise<void> {
  const { error } = await revokeAllSessionsCommand(await csrfHeaders());
  if (error !== undefined) {
    throw new ApiError(error as ApiProblem, 'Sessions could not be revoked.');
  }
}

export async function listDrafts(): Promise<Draft[]> {
  const response = await fetch('/api/v1/catalog/drafts', { credentials: 'same-origin' });
  if (!response.ok) throw await apiError(response, 'Drafts could not be loaded.');
  return response.json() as Promise<Draft[]>;
}

export async function createDraft(input: Omit<Draft, 'id' | 'images'>): Promise<Draft> {
  return draftRequest('/api/v1/catalog/drafts', 'POST', input);
}

export async function updateDraft(id: string, input: Omit<Draft, 'id' | 'images'>): Promise<Draft> {
  return draftRequest(`/api/v1/catalog/drafts/${id}`, 'PUT', input);
}

async function draftRequest(url: string, method: string, body: unknown): Promise<Draft> {
  const csrf = await csrfHeaders();
  const response = await fetch(url, { method, credentials: 'same-origin', headers: { 'Content-Type': 'application/json', ...csrf.headers }, body: JSON.stringify(body) });
  if (!response.ok) throw await apiError(response, 'Draft could not be saved.');
  return response.json() as Promise<Draft>;
}

async function apiError(response: Response, fallback: string): Promise<ApiError> {
  const problem = await response.json().catch(() => ({}));
  return new ApiError(problem as ApiProblem, fallback);
}

async function csrfHeaders(): Promise<{ headers: Record<string, string> }> {
  const { data, error } = await requestCsrfToken();
  const csrf = required(data, error, 'A CSRF token could not be obtained.');

  if (csrf.headerName === undefined || csrf.token === undefined) {
    throw new ApiError({}, 'The CSRF token response was incomplete.');
  }

  return { headers: { [csrf.headerName]: csrf.token } };
}

function required<T>(data: T | undefined, error: unknown, fallbackMessage: string): T {
  if (data !== undefined) {
    return data;
  }

  if (typeof error === 'object' && error !== null) {
    throw new ApiError(error as ApiProblem, fallbackMessage);
  }

  throw new ApiError({}, fallbackMessage);
}
