import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

vi.mock('./api/client', () => ({
  activateOperationalAccount: vi.fn(),
  ApiError: class ApiError extends Error {
    code?: string;
    fieldErrors?: Array<{ field?: string; message?: string }>;
  },
  deactivateOperationalAccount: vi.fn(),
  getAdministrativeAuditRecords: vi.fn(),
  getAuthenticatedSession: vi.fn(),
  getOperationalAccounts: vi.fn(),
  getRegularAccounts: vi.fn(),
  inviteOperationalAccount: vi.fn(),
  registerAccount: vi.fn(),
  requestPasswordRecovery: vi.fn(),
  resetPassword: vi.fn(),
  revokeAllSessions: vi.fn(),
  reactivateRegularAccount: vi.fn(),
  signOut: vi.fn(),
  signIn: vi.fn(),
  verifyEmail: vi.fn(),
  listDrafts: vi.fn(),
  listAuctions: vi.fn(),
  listOperationalBidHistory: vi.fn(),
  listPublicBids: vi.fn(),
  placeBid: vi.fn(),
  listSuspendedAuctions: vi.fn(),
  listMyScheduledAuctions: vi.fn(),
  getAuction: vi.fn(),
  cancelAuction: vi.fn(),
  createDraft: vi.fn(),
  updateDraft: vi.fn(),
  deleteDraft: vi.fn(),
  uploadDraftImage: vi.fn(),
  reorderDraftImages: vi.fn(),
  scheduleAuction: vi.fn(),
  submitDraft: vi.fn(),
  updateAuctionTerms: vi.fn(),
  suspendAuction: vi.fn(),
  suspendRegularAccount: vi.fn(),
  releaseSuspendedAuction: vi.fn(),
  resumeSuspendedAuction: vi.fn(),
  administrativelyCancelAuction: vi.fn(),
}));

vi.mock('./auctions/auctionRealtime', () => ({
  connectAuctionRoom: vi.fn(() => () => undefined),
}));

import { App } from './App';
import {
  ApiError,
  activateOperationalAccount,
  cancelAuction,
  deactivateOperationalAccount,
  getAdministrativeAuditRecords,
  getAuction,
  getAuthenticatedSession,
  getOperationalAccounts,
  getRegularAccounts,
  inviteOperationalAccount,
  listAuctions,
  listDrafts,
  listMyScheduledAuctions,
  listOperationalBidHistory,
  listPublicBids,
  listSuspendedAuctions,
  reactivateRegularAccount,
  registerAccount,
  requestPasswordRecovery,
  resetPassword,
  revokeAllSessions,
  scheduleAuction,
  signIn,
  signOut,
  suspendRegularAccount,
  verifyEmail,
} from './api/client';

describe('App', () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(getAuthenticatedSession).mockResolvedValue(null);
    vi.mocked(activateOperationalAccount).mockReset();
    vi.mocked(cancelAuction).mockReset();
    vi.mocked(deactivateOperationalAccount).mockReset();
    vi.mocked(getAdministrativeAuditRecords).mockReset();
    vi.mocked(getOperationalAccounts).mockReset();
    vi.mocked(getRegularAccounts).mockResolvedValue([]);
    vi.mocked(inviteOperationalAccount).mockReset();
    vi.mocked(registerAccount).mockReset();
    vi.mocked(requestPasswordRecovery).mockReset();
    vi.mocked(resetPassword).mockReset();
    vi.mocked(revokeAllSessions).mockReset();
    vi.mocked(reactivateRegularAccount).mockReset();
    vi.mocked(signOut).mockReset();
    vi.mocked(signIn).mockReset();
    vi.mocked(verifyEmail).mockReset();
    vi.mocked(listDrafts).mockResolvedValue([]);
    vi.mocked(listAuctions).mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    vi.mocked(listOperationalBidHistory).mockReset();
    vi.mocked(listSuspendedAuctions).mockResolvedValue([]);
    vi.mocked(listMyScheduledAuctions).mockResolvedValue([]);
    vi.mocked(listPublicBids).mockResolvedValue([]);
    vi.mocked(getAuction).mockReset();
    vi.mocked(scheduleAuction).mockReset();
    vi.mocked(suspendRegularAccount).mockReset();
    window.history.replaceState({}, '', '/');
  });

  test('validates registration fields before making a request', async () => {
    render(<App />);

    await screen.findByRole('heading', { name: 'Start collecting with confidence.' });
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Enter a valid email address.')).toBeInTheDocument();
    expect(screen.getByText('Use 3–30 letters, numbers, or underscores.')).toBeInTheDocument();
    expect(
      screen.getByText('Password must contain between 12 and 128 characters.'),
    ).toBeInTheDocument();
    expect(registerAccount).not.toHaveBeenCalled();
  });

  test('moves from successful registration to verification', async () => {
    vi.mocked(registerAccount).mockResolvedValue({
      publicHandle: 'collector_27',
      status: 'PENDING_VERIFICATION',
    });
    render(<App />);

    await screen.findByRole('heading', { name: 'Start collecting with confidence.' });
    fireEvent.change(screen.getByLabelText('Email address'), {
      target: { value: 'collector@example.com' },
    });
    fireEvent.change(screen.getByLabelText('Public handle'), { target: { value: 'collector_27' } });
    fireEvent.change(screen.getByLabelText('Password'), {
      target: { value: 'a secure passphrase' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));

    await waitFor(() =>
      expect(registerAccount).toHaveBeenCalledWith({
        email: 'collector@example.com',
        publicHandle: 'collector_27',
        password: 'a secure passphrase',
      }),
    );
    expect(await screen.findByRole('heading', { name: 'Verify your email' })).toBeInTheDocument();
    expect(
      screen.getByText(
        'Registration received. Check Mailpit for your single-use verification link.',
      ),
    ).toBeInTheDocument();
  });

  test('shows verified authenticated home state', async () => {
    vi.mocked(getAuthenticatedSession).mockResolvedValue({
      publicHandle: 'collector_27',
      status: 'ACTIVE',
      verified: true,
      canTrade: true,
    });
    render(<App />);

    expect(await screen.findByText('Welcome back, collector_27.')).toBeInTheDocument();
    expect(screen.getByText('Trading access is active.')).toBeInTheDocument();
  });

  test('keeps a suspended account in read-only mode without marketplace workspaces', async () => {
    vi.mocked(getAuthenticatedSession).mockResolvedValue({
      publicHandle: 'restricted_37',
      status: 'SUSPENDED',
      verified: true,
      canTrade: false,
    });

    render(<App />);

    expect(await screen.findByText('Account access is restricted.')).toBeInTheDocument();
    expect(
      screen.getByText(
        'You can browse public history and complete existing settlements, but marketplace commands are unavailable while your account is suspended.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Your private drafts' })).toBeNull();
    expect(screen.queryByText('Trading access is active.')).toBeNull();
  });

  test('publishes an approved item and previews immutable and editable auction terms', async () => {
    vi.mocked(getAuthenticatedSession).mockResolvedValue({
      accountId: 'seller-32',
      publicHandle: 'collector_32',
      status: 'ACTIVE',
      verified: true,
      canTrade: true,
    });
    vi.mocked(listDrafts).mockResolvedValue([
      {
        id: 'item-32',
        category: 'CARDS',
        title: 'A rare approved card',
        description: 'A complete description for the approved collectible card.',
        condition: 'EXCELLENT',
        conditionNotes: 'Excellent and carefully stored.',
        ownershipDeclared: true,
        status: 'APPROVED',
        images: [],
      },
    ]);
    vi.mocked(scheduleAuction).mockResolvedValue({
      id: 'auction-32',
      itemId: 'item-32',
      sellerHandle: 'collector_32',
      projectionVersion: 1,
      state: 'SCHEDULED',
      openingAmountCents: 10_000,
      currentAmountCents: 10_000,
      minimumIncrementCents: 1_000,
      reserveAmountCents: 15_000,
      reserveMet: false,
      startsAt: '2026-09-10T13:00:00Z',
      endsAt: '2026-09-10T15:00:00Z',
      effectiveEndAt: '2026-09-10T15:00:00Z',
      scheduledAt: '2026-09-09T12:00:00Z',
      item: {
        category: 'CARDS',
        title: 'A rare approved card',
        description: 'A complete description for the approved collectible card.',
        condition: 'EXCELLENT',
        conditionNotes: 'Excellent and carefully stored.',
        ownershipDeclared: true,
        media: [],
      },
      policy: {
        auctionType: 'ENGLISH_ASCENDING',
        currency: 'BRL',
        minimumAmountCents: 1_000,
        maximumAmountCents: 100_000_000,
        minimumLeadSeconds: 300,
        minimumDurationSeconds: 600,
        maximumDurationSeconds: 604_800,
        protectionWindowSeconds: 120,
      },
      timeline: [{ type: 'SCHEDULED', occurredAt: '2026-09-09T12:00:00Z' }],
      eligibleBidHistory: [],
      disqualifications: [],
    });
    vi.mocked(cancelAuction).mockImplementation(async () => ({
      ...(await vi.mocked(scheduleAuction).mock.results[0].value),
      state: 'CANCELLED',
      endedAt: '2026-09-09T12:10:00Z',
      timeline: [
        { type: 'SCHEDULED', occurredAt: '2026-09-09T12:00:00Z' },
        {
          type: 'CANCELLED',
          occurredAt: '2026-09-09T12:10:00Z',
          publicReason: 'The collectible is no longer available.',
        },
      ],
    }));
    render(<App />);

    fireEvent.click(
      await screen.findByRole('button', { name: 'Schedule auction for A rare approved card' }),
    );
    fireEvent.change(screen.getByLabelText('Opening amount'), { target: { value: '100.00' } });
    fireEvent.change(screen.getByLabelText('Minimum increment'), { target: { value: '10.00' } });
    fireEvent.change(screen.getByLabelText('Optional reserve'), { target: { value: '150.00' } });
    fireEvent.change(screen.getByLabelText('Start in São Paulo'), {
      target: { value: '2026-09-10T10:00' },
    });
    fireEvent.change(screen.getByLabelText('End in São Paulo'), {
      target: { value: '2026-09-10T12:00' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Publish auction' }));

    await waitFor(() =>
      expect(scheduleAuction).toHaveBeenCalledWith({
        itemId: 'item-32',
        openingAmountCents: 10_000,
        minimumIncrementCents: 1_000,
        reserveAmountCents: 15_000,
        startsAt: '2026-09-10T13:00:00.000Z',
        endsAt: '2026-09-10T15:00:00.000Z',
      }),
    );
    expect(await screen.findByRole('heading', { name: 'Published snapshot' })).toBeInTheDocument();
    expect(screen.getByText('Locked after publication')).toBeInTheDocument();
    expect(screen.getByText('Editable before start')).toBeInTheDocument();
    expect(
      screen.getByText('Condition notes: Excellent and carefully stored.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Ownership declared: Yes')).toBeInTheDocument();
    expect(screen.getByText(/ENGLISH ASCENDING · BRL/)).toBeInTheDocument();
    expect(screen.getByText(/R\$\s*100,00/)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Public cancellation reason'), {
      target: { value: 'The collectible is no longer available.' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel auction' }));
    await waitFor(() =>
      expect(cancelAuction).toHaveBeenCalledWith(
        'auction-32',
        'The collectible is no longer available.',
      ),
    );
    expect(await screen.findByRole('status')).toHaveTextContent('Auction cancelled.');
  });

  test('anonymous visitor browses localized auction details and safe timeline', async () => {
    const auction = {
      id: 'auction-33',
      itemId: 'item-33',
      sellerHandle: 'clock_collector',
      projectionVersion: 1,
      state: 'SCHEDULED' as const,
      openingAmountCents: 10_000,
      currentAmountCents: 10_000,
      minimumIncrementCents: 1_000,
      reserveMet: false,
      startsAt: '2026-09-11T13:00:00Z',
      endsAt: '2026-09-11T15:00:00Z',
      effectiveEndAt: '2026-09-11T15:00:00Z',
      scheduledAt: '2026-09-10T12:00:00Z',
      item: {
        category: 'CARDS',
        title: 'Mechanical countdown card',
        description: 'A public immutable snapshot of the collectible.',
        condition: 'EXCELLENT',
        conditionNotes: 'Light archival wear only.',
        ownershipDeclared: true,
        media: [],
      },
      policy: {
        auctionType: 'ENGLISH_ASCENDING' as const,
        currency: 'BRL' as const,
        minimumAmountCents: 1_000,
        maximumAmountCents: 100_000_000,
        minimumLeadSeconds: 300,
        minimumDurationSeconds: 600,
        maximumDurationSeconds: 604_800,
        protectionWindowSeconds: 120,
      },
      timeline: [{ type: 'SCHEDULED' as const, occurredAt: '2026-09-10T12:00:00Z' }],
      eligibleBidHistory: [],
      disqualifications: [],
    };
    vi.mocked(listAuctions).mockResolvedValue({
      content: [auction],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    vi.mocked(getAuction).mockResolvedValue(auction);

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Auction floor' })).toBeInTheDocument();
    expect(await screen.findByText('Mechanical countdown card')).toBeInTheDocument();
    expect(screen.getByText(/R\$\s*100,00/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'View Mechanical countdown card' }));
    expect(
      await screen.findByText('A public immutable snapshot of the collectible.'),
    ).toBeInTheDocument();
    expect(screen.getAllByText('Scheduled')).not.toHaveLength(0);
    expect(screen.getAllByText(/São Paulo/)).not.toHaveLength(0);
    expect(screen.getByText(/Reserve not met/)).toBeInTheDocument();
    expect(screen.getByText('Light archival wear only.')).toBeInTheDocument();
    expect(screen.getByText('No accepted bids.')).toBeInTheDocument();
    expect(screen.getByText('No public disqualifications.')).toBeInTheDocument();
  });

  test('prioritizes verification link over a pending authenticated session', async () => {
    vi.mocked(getAuthenticatedSession).mockResolvedValue({
      publicHandle: 'collector_27',
      status: 'PENDING_VERIFICATION',
      verified: false,
      canTrade: false,
    });
    vi.mocked(verifyEmail).mockResolvedValue({ publicHandle: 'collector_27', status: 'ACTIVE' });
    vi.mocked(signIn).mockResolvedValue({
      publicHandle: 'collector_27',
      status: 'ACTIVE',
      verified: true,
      canTrade: true,
    });
    window.history.replaceState({}, '', '/?verificationToken=token-27');
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Verify your email' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Verify email' }));
    await waitFor(() => expect(verifyEmail).toHaveBeenCalledWith('token-27'));
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Email address'), {
      target: { value: 'collector@example.com' },
    });
    fireEvent.change(screen.getByLabelText('Password'), {
      target: { value: 'a secure passphrase' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Trading access is active.')).toBeInTheDocument();
  });

  test('shows a clear error for an invalid or expired verification token', async () => {
    const error = new Error('invalid token');
    Object.setPrototypeOf(error, (ApiError as unknown as { prototype: object }).prototype);
    Object.assign(error, { code: 'VERIFICATION_TOKEN_INVALID' });
    vi.mocked(verifyEmail).mockRejectedValue(error);
    window.history.replaceState({}, '', '/?verificationToken=expired-token');
    render(<App />);

    fireEvent.click(await screen.findByRole('button', { name: 'Verify email' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'invalid, expired, or has already been used',
    );
  });

  test('requests password recovery without revealing whether the email exists', async () => {
    vi.mocked(requestPasswordRecovery).mockResolvedValue({ status: 'RECOVERY_REQUEST_RECEIVED' });
    render(<App />);

    await screen.findByRole('heading', { name: 'Start collecting with confidence.' });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    fireEvent.click(screen.getByRole('button', { name: 'Forgot password?' }));
    fireEvent.change(screen.getByLabelText('Email address'), {
      target: { value: 'collector@example.com' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Send recovery link' }));

    await waitFor(() =>
      expect(requestPasswordRecovery).toHaveBeenCalledWith({ email: 'collector@example.com' }),
    );
    expect(await screen.findByRole('status')).toHaveTextContent(
      'If an account exists for that email, a recovery link is on its way.',
    );
  });

  test('consumes a recovery link and returns to sign in after resetting the password', async () => {
    vi.mocked(resetPassword).mockResolvedValue({ status: 'PASSWORD_RESET' });
    window.history.replaceState({}, '', '/?recoveryToken=recovery-token-28');
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Reset your password' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('New password'), {
      target: { value: 'a replacement password' },
    });
    fireEvent.change(screen.getByLabelText('Confirm new password'), {
      target: { value: 'a replacement password' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Reset password' }));

    await waitFor(() =>
      expect(resetPassword).toHaveBeenCalledWith({
        token: 'recovery-token-28',
        password: 'a replacement password',
      }),
    );
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(
      'Your password has been reset. Sign in with your new password.',
    );
  });

  test('activates an operational account from its single-use link', async () => {
    vi.mocked(activateOperationalAccount).mockResolvedValue({
      email: 'moderator@example.com',
      role: 'MODERATOR',
      status: 'ACTIVE',
    });
    window.history.replaceState({}, '', '/?operationalActivationToken=activation-token-29');
    render(<App />);

    expect(
      await screen.findByRole('heading', { name: 'Activate your operational account' }),
    ).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('New password'), {
      target: { value: 'a moderator password' },
    });
    fireEvent.change(screen.getByLabelText('Confirm new password'), {
      target: { value: 'a moderator password' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Activate account' }));

    await waitFor(() =>
      expect(activateOperationalAccount).toHaveBeenCalledWith({
        token: 'activation-token-29',
        password: 'a moderator password',
      }),
    );
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  test('renders the administrator console and submits invite and deactivation reasons', async () => {
    vi.mocked(getAuthenticatedSession).mockResolvedValue({
      accountId: 'admin-29',
      accountType: 'OPERATIONAL',
      role: 'ADMINISTRATOR',
      status: 'ACTIVE',
      verified: true,
      canTrade: false,
    });
    vi.mocked(getOperationalAccounts).mockResolvedValue([
      {
        id: 'moderator-29',
        email: 'moderator@example.com',
        role: 'MODERATOR',
        status: 'ACTIVE',
        invitedAt: '2026-09-04T12:00:00Z',
      },
    ]);
    vi.mocked(getAdministrativeAuditRecords).mockResolvedValue([
      {
        id: 'audit-29',
        action: 'OPERATIONAL_ACCOUNT_INVITED',
        metadata: 'reasonCategory=STAFFING',
        occurredAt: '2026-09-04T12:00:00Z',
      },
    ]);
    vi.mocked(inviteOperationalAccount).mockResolvedValue({
      email: 'new-moderator@example.com',
      role: 'MODERATOR',
      status: 'INVITED',
    });
    vi.mocked(deactivateOperationalAccount).mockResolvedValue(undefined);
    render(<App />);

    expect(
      await screen.findByRole('heading', { name: 'Manage operational accounts' }),
    ).toBeInTheDocument();
    expect(await screen.findByText('OPERATIONAL_ACCOUNT_INVITED')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Email address'), {
      target: { value: 'new-moderator@example.com' },
    });
    fireEvent.change(
      screen.getByLabelText('Public reason', { selector: '#operational-invite-public-reason' }),
      { target: { value: 'Add evening coverage' } },
    );
    fireEvent.click(screen.getByRole('button', { name: 'Send invitation' }));
    await waitFor(() =>
      expect(inviteOperationalAccount).toHaveBeenCalledWith({
        email: 'new-moderator@example.com',
        role: 'MODERATOR',
        reasonCategory: 'STAFFING',
        publicReason: 'Add evening coverage',
        internalNote: undefined,
      }),
    );

    fireEvent.change(screen.getByLabelText('Account'), { target: { value: 'moderator-29' } });
    fireEvent.change(
      screen.getByLabelText('Public reason', {
        selector: '#operational-deactivation-public-reason',
      }),
      { target: { value: 'Access is no longer required' } },
    );
    fireEvent.click(screen.getByRole('button', { name: 'Deactivate account' }));
    await waitFor(() =>
      expect(deactivateOperationalAccount).toHaveBeenCalledWith('moderator-29', {
        reasonCategory: 'SECURITY',
        publicReason: 'Access is no longer required',
        internalNote: undefined,
      }),
    );
  });

  test('keeps the administrator console hidden from moderators', async () => {
    vi.mocked(getAuthenticatedSession).mockResolvedValue({
      accountId: 'moderator-29',
      accountType: 'OPERATIONAL',
      role: 'MODERATOR',
      status: 'ACTIVE',
      verified: true,
      canTrade: false,
    });
    render(<App />);

    expect(
      await screen.findByRole('heading', { name: 'Moderator access is active.' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: 'Manage operational accounts' }),
    ).not.toBeInTheDocument();
    expect(getOperationalAccounts).not.toHaveBeenCalled();
    expect(getAdministrativeAuditRecords).not.toHaveBeenCalled();
  });

  test('can sign out or revoke every session from the authenticated home', async () => {
    vi.mocked(getAuthenticatedSession).mockResolvedValue({
      publicHandle: 'collector_28',
      status: 'ACTIVE',
      verified: true,
      canTrade: true,
    });
    vi.mocked(signOut).mockResolvedValue(undefined);
    render(<App />);

    await screen.findByText('Welcome back, collector_28.');
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    await waitFor(() => expect(signOut).toHaveBeenCalled());
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();

    vi.mocked(getAuthenticatedSession).mockResolvedValue({
      publicHandle: 'collector_28',
      status: 'ACTIVE',
      verified: true,
      canTrade: true,
    });
    cleanup();
    render(<App />);
    await screen.findByText('Welcome back, collector_28.');
    fireEvent.click(screen.getByRole('button', { name: 'Sign out all sessions' }));
    await waitFor(() => expect(revokeAllSessions).toHaveBeenCalled());
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  test('shows a failure when session management cannot complete', async () => {
    vi.mocked(getAuthenticatedSession).mockResolvedValue({
      publicHandle: 'collector_28',
      status: 'ACTIVE',
      verified: true,
      canTrade: true,
    });
    vi.mocked(signOut).mockRejectedValue(new Error('network unavailable'));
    render(<App />);

    await screen.findByText('Welcome back, collector_28.');
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The request could not be completed. Please try again.',
    );
    expect(screen.getByText('Welcome back, collector_28.')).toBeInTheDocument();
  });
});
