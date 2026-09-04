import { client } from './generated/client.gen';
import {
  csrfToken as requestCsrfToken,
  getAuthenticatedSession as requestAuthenticatedSession,
  getPlatformStatus as requestPlatformStatus,
  registerRegularAccount,
  signInRegularAccount,
  verifyRegularAccountEmail,
} from './generated/sdk.gen';
import type {
  AuthenticatedSession,
  EmailVerification,
  PlatformStatus,
  Registration,
  RegistrationRequest,
  SignInRequest,
} from './generated/types.gen';

export type { AuthenticatedSession, PlatformStatus, Registration, RegistrationRequest, SignInRequest } from './generated/types.gen';

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
