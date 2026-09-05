import { client } from './generated/client.gen';
import {
  activateOperationalAccount as activateOperationalAccountCommand,
  csrfToken as requestCsrfToken,
  deactivateOperationalAccount as deactivateOperationalAccountCommand,
  getAuthenticatedSession as requestAuthenticatedSession,
  getPlatformStatus as requestPlatformStatus,
  inviteOperationalAccount as inviteOperationalAccountCommand,
  listAdministrativeAuditRecords as requestAdministrativeAuditRecords,
  listOperationalAccounts as requestOperationalAccounts,
  registerRegularAccount,
  requestPasswordRecovery as requestPasswordRecoveryCommand,
  resetPassword as resetPasswordCommand,
  revokeAllSessions as revokeAllSessionsCommand,
  signOut as signOutCommand,
  signInRegularAccount,
  verifyRegularAccountEmail,
} from './generated/sdk.gen';
import type {
  ActivateOperationalAccountRequest,
  AuditRecord,
  AuthenticatedSession,
  DeactivateOperationalAccountRequest,
  EmailVerification,
  InviteOperationalAccountRequest,
  OperationalAccount,
  OperationalAccountActivation,
  OperationalAccountView,
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
  ActivateOperationalAccountRequest,
  AuditRecord,
  AuthenticatedSession,
  DeactivateOperationalAccountRequest,
  InviteOperationalAccountRequest,
  OperationalAccount,
  OperationalAccountActivation,
  OperationalAccountView,
  PasswordRecoveryRequest,
  PasswordRecoveryRequestAccepted,
  PasswordReset,
  PasswordResetRequest,
  PlatformStatus,
  Registration,
  RegistrationRequest,
  SignInRequest,
} from './generated/types.gen';

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

export async function activateOperationalAccount(input: ActivateOperationalAccountRequest): Promise<OperationalAccountActivation> {
  const { data, error } = await activateOperationalAccountCommand({ body: input, ...(await csrfHeaders()) });
  return required(data, error, 'Operational account activation could not be completed.');
}

export async function inviteOperationalAccount(input: InviteOperationalAccountRequest): Promise<OperationalAccount> {
  const { data, error } = await inviteOperationalAccountCommand({ body: input, ...(await csrfHeaders()) });
  return required(data, error, 'Operational account invitation could not be sent.');
}

export async function getOperationalAccounts(): Promise<OperationalAccountView[]> {
  const { data, error } = await requestOperationalAccounts();
  return required(data, error, 'Operational accounts could not be loaded.');
}

export async function deactivateOperationalAccount(accountId: string, input: DeactivateOperationalAccountRequest): Promise<void> {
  const { error } = await deactivateOperationalAccountCommand({
    body: input,
    path: { accountId },
    ...(await csrfHeaders()),
  });
  if (error !== undefined) {
    throw new ApiError(error as ApiProblem, 'Operational account could not be deactivated.');
  }
}

export async function getAdministrativeAuditRecords(): Promise<AuditRecord[]> {
  const { data, error } = await requestAdministrativeAuditRecords();
  return required(data, error, 'Audit records could not be loaded.');
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
