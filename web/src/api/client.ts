import { client } from './generated/client.gen';
import {
  activateOperationalAccount as activateOperationalAccountCommand,
  deactivateOperationalAccount as deactivateOperationalAccountCommand,
  inviteOperationalAccount as inviteOperationalAccountCommand,
  registerRegularAccount,
  listAdministrativeAuditRecords as requestAdministrativeAuditRecords,
  getAuthenticatedSession as requestAuthenticatedSession,
  csrfToken as requestCsrfToken,
  listOperationalAccounts as requestOperationalAccounts,
  requestPasswordRecovery as requestPasswordRecoveryCommand,
  getPlatformStatus as requestPlatformStatus,
  resetPassword as resetPasswordCommand,
  revokeAllSessions as revokeAllSessionsCommand,
  signInRegularAccount,
  signOut as signOutCommand,
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
  PasswordRecoveryRequest,
  PasswordRecoveryRequestAccepted,
  PasswordReset,
  PasswordResetRequest,
  PlatformStatus,
  Registration,
  RegistrationRequest,
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

export type Category =
  | 'CARDS'
  | 'COINS_AND_CURRENCY'
  | 'STAMPS'
  | 'COMICS_AND_BOOKS'
  | 'TOYS_AND_FIGURES'
  | 'MEMORABILIA'
  | 'ART_AND_ANTIQUES'
  | 'OTHER';
export type Condition =
  | 'NEW_SEALED'
  | 'EXCELLENT'
  | 'VERY_GOOD'
  | 'GOOD'
  | 'FAIR'
  | 'POOR'
  | 'NOT_APPLICABLE';
export type DraftInput = {
  category: Category;
  otherCategoryLabel?: string;
  title: string;
  description: string;
  condition: Condition;
  conditionNotes: string;
  ownershipDeclared: boolean;
};
export type Draft = DraftInput & {
  id: string;
  status: 'DRAFT' | 'UNDER_REVIEW' | 'APPROVED';
  submissionReason?: string;
  images: Array<{
    id: string;
    url: string;
    thumbnailUrl: string;
    contentType: string;
    sortOrder: number;
  }>;
};
export type ModerationSubmission = {
  id: string;
  category: string;
  otherCategoryLabel?: string;
  title: string;
  description: string;
  condition: string;
  conditionNotes: string;
  ownershipDeclared: boolean;
  submittedAt: string;
  images: Array<{ id: string; url: string; contentType: string; sortOrder: number }>;
};
export type AuctionInput = {
  itemId: string;
  openingAmountCents: number;
  minimumIncrementCents: number;
  reserveAmountCents?: number;
  startsAt: string;
  endsAt: string;
};
export type EditableAuctionTerms = {
  reserveAmountCents?: number;
  startsAt: string;
  endsAt: string;
};
export type Auction = {
  id: string;
  itemId: string;
  sellerHandle: string;
  state:
    | 'DRAFT'
    | 'SCHEDULED'
    | 'LIVE'
    | 'SUSPENDED'
    | 'CLOSING'
    | 'AWAITING_SELLER_DECISION'
    | 'SOLD'
    | 'UNSOLD'
    | 'CANCELLED';
  openingAmountCents: number;
  currentAmountCents: number;
  minimumIncrementCents: number;
  reserveAmountCents?: number;
  reserveMet: boolean;
  startsAt: string;
  endsAt: string;
  effectiveEndAt: string;
  scheduledAt: string;
  endedAt?: string;
  item: {
    category: string;
    otherCategoryLabel?: string;
    title: string;
    description: string;
    condition: string;
    conditionNotes: string;
    ownershipDeclared: boolean;
    media: Array<{ id: string; url: string; contentType: string; sortOrder: number }>;
  };
  policy: {
    auctionType: 'ENGLISH_ASCENDING';
    currency: 'BRL';
    minimumAmountCents: number;
    maximumAmountCents: number;
    minimumLeadSeconds: number;
    minimumDurationSeconds: number;
    maximumDurationSeconds: number;
    protectionWindowSeconds: number;
  };
  timeline: Array<{
    type:
      | 'SCHEDULED'
      | 'RESCHEDULED'
      | 'STARTED'
      | 'CANCELLED'
      | 'ENDED'
      | 'SUSPENDED'
      | 'RELEASED'
      | 'RESUMED';
    occurredAt: string;
    reasonCategory?: string;
    publicReason?: string;
    internalNote?: string;
    itemDisposition?: string;
  }>;
  eligibleBidHistory: Array<unknown>;
  disqualifications: Array<unknown>;
};
export type SuspendedAuction = {
  id: string;
  itemTitle: string;
  sellerHandle: string;
  state: string;
  sourceState?: 'SCHEDULED' | 'LIVE';
  suspendedAt?: string;
  remainingDurationMillis?: number | null;
  effectiveEndAt: string;
  timeline: Array<{
    type: string;
    occurredAt: string;
    reasonCategory?: string;
    publicReason?: string;
    internalNote?: string;
    itemDisposition?: string;
  }>;
};
export type AuctionAdministrativeReason = {
  reasonCategory: string;
  publicReason: string;
  internalNote?: string;
};
export type AuctionView = 'SCHEDULED' | 'LIVE' | 'ENDED';
export type AuctionPage = {
  content: Auction[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

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
  const { data, error } = await verifyRegularAccountEmail({
    body: { token },
    ...(await csrfHeaders()),
  });
  return required(data, error, 'Email verification could not be completed.');
}

export async function signIn(input: SignInRequest): Promise<AuthenticatedSession> {
  const { data, error } = await signInRegularAccount({ body: input, ...(await csrfHeaders()) });
  return required(data, error, 'Sign-in could not be completed.');
}

export async function requestPasswordRecovery(
  input: PasswordRecoveryRequest,
): Promise<PasswordRecoveryRequestAccepted> {
  const { data, error } = await requestPasswordRecoveryCommand({
    body: input,
    ...(await csrfHeaders()),
  });
  return required(data, error, 'Password recovery could not be requested.');
}

export async function resetPassword(input: PasswordResetRequest): Promise<PasswordReset> {
  const { data, error } = await resetPasswordCommand({ body: input, ...(await csrfHeaders()) });
  return required(data, error, 'Password could not be reset.');
}

export async function activateOperationalAccount(
  input: ActivateOperationalAccountRequest,
): Promise<OperationalAccountActivation> {
  const { data, error } = await activateOperationalAccountCommand({
    body: input,
    ...(await csrfHeaders()),
  });
  return required(data, error, 'Operational account activation could not be completed.');
}

export async function inviteOperationalAccount(
  input: InviteOperationalAccountRequest,
): Promise<OperationalAccount> {
  const { data, error } = await inviteOperationalAccountCommand({
    body: input,
    ...(await csrfHeaders()),
  });
  return required(data, error, 'Operational account invitation could not be sent.');
}

export async function getOperationalAccounts(): Promise<OperationalAccountView[]> {
  const { data, error } = await requestOperationalAccounts();
  return required(data, error, 'Operational accounts could not be loaded.');
}

export async function deactivateOperationalAccount(
  accountId: string,
  input: DeactivateOperationalAccountRequest,
): Promise<void> {
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

export async function listDrafts(): Promise<Draft[]> {
  const response = await fetch('/api/v1/catalog/drafts', { credentials: 'same-origin' });
  if (!response.ok) throw await apiError(response, 'Drafts could not be loaded.');
  return response.json() as Promise<Draft[]>;
}

export async function createDraft(input: DraftInput): Promise<Draft> {
  return draftRequest('/api/v1/catalog/drafts', 'POST', input);
}

export async function updateDraft(id: string, input: DraftInput): Promise<Draft> {
  return draftRequest(`/api/v1/catalog/drafts/${id}`, 'PUT', input);
}
export async function submitDraft(id: string): Promise<Draft> {
  return draftRequest(`/api/v1/catalog/drafts/${id}/submit`, 'POST');
}

export async function scheduleAuction(input: AuctionInput): Promise<Auction> {
  return auctionRequest('/api/v1/auctions', 'POST', input);
}

export async function updateAuctionTerms(
  id: string,
  input: EditableAuctionTerms,
): Promise<Auction> {
  return auctionRequest(`/api/v1/auctions/${id}/terms`, 'PUT', input);
}

export async function cancelAuction(id: string, publicReason: string): Promise<Auction> {
  return auctionRequest(`/api/v1/auctions/${id}/cancellation`, 'POST', { publicReason });
}

export async function listAuctions(state: AuctionView, page = 0, size = 20): Promise<AuctionPage> {
  const parameters = new URLSearchParams({ state, page: String(page), size: String(size) });
  const response = await fetch(`/api/v1/auctions?${parameters}`, { credentials: 'same-origin' });
  if (!response.ok) throw await apiError(response, 'Auctions could not be loaded.');
  return response.json() as Promise<AuctionPage>;
}

export async function getAuction(id: string): Promise<Auction> {
  const response = await fetch(`/api/v1/auctions/${id}`, { credentials: 'same-origin' });
  if (!response.ok) throw await apiError(response, 'Auction details could not be loaded.');
  return response.json() as Promise<Auction>;
}

export async function listMyScheduledAuctions(): Promise<Auction[]> {
  const response = await fetch('/api/v1/auctions/mine', { credentials: 'same-origin' });
  if (!response.ok) throw await apiError(response, 'Your scheduled auctions could not be loaded.');
  return response.json() as Promise<Auction[]>;
}

export async function listSuspendedAuctions(): Promise<SuspendedAuction[]> {
  return operationsAuctionRequest('/api/v1/operations/auctions/suspended', 'GET');
}

export async function suspendAuction(
  id: string,
  reason: AuctionAdministrativeReason,
): Promise<SuspendedAuction> {
  return operationsAuctionRequest(`/api/v1/operations/auctions/${id}/suspension`, 'POST', reason);
}

export async function releaseSuspendedAuction(id: string): Promise<SuspendedAuction> {
  return operationsAuctionRequest(`/api/v1/operations/auctions/${id}/release`, 'POST');
}

export async function resumeSuspendedAuction(id: string): Promise<SuspendedAuction> {
  return operationsAuctionRequest(`/api/v1/operations/auctions/${id}/resume`, 'POST');
}

export async function administrativelyCancelAuction(
  id: string,
  reason: AuctionAdministrativeReason & { itemDisposition: string },
): Promise<SuspendedAuction> {
  return operationsAuctionRequest(`/api/v1/operations/auctions/${id}/cancellation`, 'POST', reason);
}

export async function listModerationSubmissions(): Promise<ModerationSubmission[]> {
  const response = await fetch('/api/v1/moderation/submissions', { credentials: 'same-origin' });
  if (!response.ok) throw await apiError(response, 'Moderation submissions could not be loaded.');
  return response.json() as Promise<ModerationSubmission[]>;
}

export async function approveModerationSubmission(id: string): Promise<ModerationSubmission> {
  return moderationRequest(`/api/v1/moderation/submissions/${id}/approve`, 'POST');
}

export async function rejectModerationSubmission(
  id: string,
  publicReason: string,
): Promise<ModerationSubmission> {
  return moderationRequest(`/api/v1/moderation/submissions/${id}/reject`, 'POST', { publicReason });
}

export async function deleteDraft(id: string): Promise<void> {
  await emptyDraftRequest(`/api/v1/catalog/drafts/${id}`, 'DELETE');
}
export async function uploadDraftImage(id: string, file: File): Promise<Draft['images'][number]> {
  const csrf = await csrfHeaders();
  const form = new FormData();
  form.append('file', file);
  const response = await fetch(`/api/v1/catalog/drafts/${id}/images`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: csrf.headers,
    body: form,
  });
  if (!response.ok) throw await apiError(response, 'The image could not be uploaded.');
  return response.json() as Promise<Draft['images'][number]>;
}
export async function reorderDraftImages(id: string, mediaIds: string[]): Promise<void> {
  await emptyDraftRequest(`/api/v1/catalog/drafts/${id}/images/order`, 'PUT', { mediaIds });
}
async function emptyDraftRequest(url: string, method: string, body?: unknown): Promise<void> {
  const csrf = await csrfHeaders();
  const response = await fetch(url, {
    method,
    credentials: 'same-origin',
    headers: { ...csrf.headers, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) throw await apiError(response, 'The draft could not be changed.');
}

async function draftRequest(url: string, method: string, body?: unknown): Promise<Draft> {
  const csrf = await csrfHeaders();
  const response = await fetch(url, {
    method,
    credentials: 'same-origin',
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...csrf.headers },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) throw await apiError(response, 'Draft could not be saved.');
  return response.json() as Promise<Draft>;
}

async function moderationRequest(
  url: string,
  method: string,
  body?: unknown,
): Promise<ModerationSubmission> {
  const csrf = await csrfHeaders();
  const response = await fetch(url, {
    method,
    credentials: 'same-origin',
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...csrf.headers },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) throw await apiError(response, 'The moderation decision could not be saved.');
  return response.json() as Promise<ModerationSubmission>;
}

async function operationsAuctionRequest<T>(
  url: string,
  method: string,
  body?: unknown,
): Promise<T> {
  const csrf = method === 'GET' ? { headers: {} } : await csrfHeaders();
  const response = await fetch(url, {
    method,
    credentials: 'same-origin',
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...csrf.headers },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) throw await apiError(response, 'The auction operation could not be completed.');
  return response.json() as Promise<T>;
}

async function auctionRequest(url: string, method: string, body: unknown): Promise<Auction> {
  const csrf = await csrfHeaders();
  const response = await fetch(url, {
    method,
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...csrf.headers },
    body: JSON.stringify(body),
  });
  if (!response.ok) throw await apiError(response, 'The auction could not be published.');
  return response.json() as Promise<Auction>;
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
