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
  inviteOperationalAccount: vi.fn(),
  registerAccount: vi.fn(),
  requestPasswordRecovery: vi.fn(),
  resetPassword: vi.fn(),
  revokeAllSessions: vi.fn(),
  signOut: vi.fn(),
  signIn: vi.fn(),
  verifyEmail: vi.fn(),
}));

import { App } from './App';
import {
  activateOperationalAccount,
  ApiError,
  deactivateOperationalAccount,
  getAdministrativeAuditRecords,
  getAuthenticatedSession,
  getOperationalAccounts,
  inviteOperationalAccount,
  registerAccount,
  requestPasswordRecovery,
  resetPassword,
  revokeAllSessions,
  signIn,
  signOut,
  verifyEmail,
} from './api/client';

describe('App', () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(getAuthenticatedSession).mockResolvedValue(null);
    vi.mocked(activateOperationalAccount).mockReset();
    vi.mocked(deactivateOperationalAccount).mockReset();
    vi.mocked(getAdministrativeAuditRecords).mockReset();
    vi.mocked(getOperationalAccounts).mockReset();
    vi.mocked(inviteOperationalAccount).mockReset();
    vi.mocked(registerAccount).mockReset();
    vi.mocked(requestPasswordRecovery).mockReset();
    vi.mocked(resetPassword).mockReset();
    vi.mocked(revokeAllSessions).mockReset();
    vi.mocked(signOut).mockReset();
    vi.mocked(signIn).mockReset();
    vi.mocked(verifyEmail).mockReset();
    window.history.replaceState({}, '', '/');
  });

  test('validates registration fields before making a request', async () => {
    render(<App />);

    await screen.findByRole('heading', { name: 'Start collecting with confidence.' });
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Enter a valid email address.')).toBeInTheDocument();
    expect(screen.getByText('Use 3–30 letters, numbers, or underscores.')).toBeInTheDocument();
    expect(screen.getByText('Password must contain between 12 and 128 characters.')).toBeInTheDocument();
    expect(registerAccount).not.toHaveBeenCalled();
  });

  test('moves from successful registration to verification', async () => {
    vi.mocked(registerAccount).mockResolvedValue({ publicHandle: 'collector_27', status: 'PENDING_VERIFICATION' });
    render(<App />);

    await screen.findByRole('heading', { name: 'Start collecting with confidence.' });
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'collector@example.com' } });
    fireEvent.change(screen.getByLabelText('Public handle'), { target: { value: 'collector_27' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'a secure passphrase' } });
    fireEvent.click(screen.getByRole('button', { name: 'Create account' }));

    await waitFor(() => expect(registerAccount).toHaveBeenCalledWith({
      email: 'collector@example.com',
      publicHandle: 'collector_27',
      password: 'a secure passphrase',
    }));
    expect(await screen.findByRole('heading', { name: 'Verify your email' })).toBeInTheDocument();
    expect(screen.getByText('Registration received. Check Mailpit for your single-use verification link.')).toBeInTheDocument();
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

  test('prioritizes verification link over a pending authenticated session', async () => {
    vi.mocked(getAuthenticatedSession).mockResolvedValue({
      publicHandle: 'collector_27', status: 'PENDING_VERIFICATION', verified: false, canTrade: false,
    });
    vi.mocked(verifyEmail).mockResolvedValue({ publicHandle: 'collector_27', status: 'ACTIVE' });
    vi.mocked(signIn).mockResolvedValue({
      publicHandle: 'collector_27', status: 'ACTIVE', verified: true, canTrade: true,
    });
    window.history.replaceState({}, '', '/?verificationToken=token-27');
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Verify your email' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Verify email' }));
    await waitFor(() => expect(verifyEmail).toHaveBeenCalledWith('token-27'));
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'collector@example.com' } });
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'a secure passphrase' } });
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
    expect(await screen.findByRole('alert')).toHaveTextContent('invalid, expired, or has already been used');
  });

  test('requests password recovery without revealing whether the email exists', async () => {
    vi.mocked(requestPasswordRecovery).mockResolvedValue({ status: 'RECOVERY_REQUEST_RECEIVED' });
    render(<App />);

    await screen.findByRole('heading', { name: 'Start collecting with confidence.' });
    fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    fireEvent.click(screen.getByRole('button', { name: 'Forgot password?' }));
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'collector@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send recovery link' }));

    await waitFor(() => expect(requestPasswordRecovery).toHaveBeenCalledWith({ email: 'collector@example.com' }));
    expect(await screen.findByRole('status')).toHaveTextContent('If an account exists for that email, a recovery link is on its way.');
  });

  test('consumes a recovery link and returns to sign in after resetting the password', async () => {
    vi.mocked(resetPassword).mockResolvedValue({ status: 'PASSWORD_RESET' });
    window.history.replaceState({}, '', '/?recoveryToken=recovery-token-28');
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Reset your password' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('New password'), { target: { value: 'a replacement password' } });
    fireEvent.change(screen.getByLabelText('Confirm new password'), { target: { value: 'a replacement password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Reset password' }));

    await waitFor(() => expect(resetPassword).toHaveBeenCalledWith({
      token: 'recovery-token-28',
      password: 'a replacement password',
    }));
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Your password has been reset. Sign in with your new password.');
  });

  test('activates an operational account from its single-use link', async () => {
    vi.mocked(activateOperationalAccount).mockResolvedValue({
      email: 'moderator@example.com', role: 'MODERATOR', status: 'ACTIVE',
    });
    window.history.replaceState({}, '', '/?operationalActivationToken=activation-token-29');
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Activate your operational account' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('New password'), { target: { value: 'a moderator password' } });
    fireEvent.change(screen.getByLabelText('Confirm new password'), { target: { value: 'a moderator password' } });
    fireEvent.click(screen.getByRole('button', { name: 'Activate account' }));

    await waitFor(() => expect(activateOperationalAccount).toHaveBeenCalledWith({
      token: 'activation-token-29', password: 'a moderator password',
    }));
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  test('renders the administrator console and submits invite and deactivation reasons', async () => {
    vi.mocked(getAuthenticatedSession).mockResolvedValue({
      accountId: 'admin-29', accountType: 'OPERATIONAL', role: 'ADMINISTRATOR', status: 'ACTIVE',
      verified: true, canTrade: false,
    });
    vi.mocked(getOperationalAccounts).mockResolvedValue([{
      id: 'moderator-29', email: 'moderator@example.com', role: 'MODERATOR', status: 'ACTIVE',
      invitedAt: '2026-09-04T12:00:00Z',
    }]);
    vi.mocked(getAdministrativeAuditRecords).mockResolvedValue([{
      id: 'audit-29', action: 'OPERATIONAL_ACCOUNT_INVITED', metadata: 'reasonCategory=STAFFING',
      occurredAt: '2026-09-04T12:00:00Z',
    }]);
    vi.mocked(inviteOperationalAccount).mockResolvedValue({ email: 'new-moderator@example.com', role: 'MODERATOR', status: 'INVITED' });
    vi.mocked(deactivateOperationalAccount).mockResolvedValue(undefined);
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Manage operational accounts' })).toBeInTheDocument();
    expect(screen.getByText('OPERATIONAL_ACCOUNT_INVITED')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'new-moderator@example.com' } });
    fireEvent.change(screen.getByLabelText('Public reason', { selector: '#operational-invite-public-reason' }), { target: { value: 'Add evening coverage' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send invitation' }));
    await waitFor(() => expect(inviteOperationalAccount).toHaveBeenCalledWith({
      email: 'new-moderator@example.com', role: 'MODERATOR', reasonCategory: 'STAFFING',
      publicReason: 'Add evening coverage', internalNote: undefined,
    }));

    fireEvent.change(screen.getByLabelText('Account'), { target: { value: 'moderator-29' } });
    fireEvent.change(screen.getByLabelText('Public reason', { selector: '#operational-deactivation-public-reason' }), { target: { value: 'Access is no longer required' } });
    fireEvent.click(screen.getByRole('button', { name: 'Deactivate account' }));
    await waitFor(() => expect(deactivateOperationalAccount).toHaveBeenCalledWith('moderator-29', {
      reasonCategory: 'SECURITY', publicReason: 'Access is no longer required', internalNote: undefined,
    }));
  });

  test('keeps the administrator console hidden from moderators', async () => {
    vi.mocked(getAuthenticatedSession).mockResolvedValue({
      accountId: 'moderator-29', accountType: 'OPERATIONAL', role: 'MODERATOR', status: 'ACTIVE',
      verified: true, canTrade: false,
    });
    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Moderator access is active.' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Manage operational accounts' })).not.toBeInTheDocument();
    expect(getOperationalAccounts).not.toHaveBeenCalled();
    expect(getAdministrativeAuditRecords).not.toHaveBeenCalled();
  });

  test('can sign out or revoke every session from the authenticated home', async () => {
    vi.mocked(getAuthenticatedSession).mockResolvedValue({
      publicHandle: 'collector_28', status: 'ACTIVE', verified: true, canTrade: true,
    });
    vi.mocked(signOut).mockResolvedValue(undefined);
    render(<App />);

    await screen.findByText('Welcome back, collector_28.');
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    await waitFor(() => expect(signOut).toHaveBeenCalled());
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();

    vi.mocked(getAuthenticatedSession).mockResolvedValue({
      publicHandle: 'collector_28', status: 'ACTIVE', verified: true, canTrade: true,
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
      publicHandle: 'collector_28', status: 'ACTIVE', verified: true, canTrade: true,
    });
    vi.mocked(signOut).mockRejectedValue(new Error('network unavailable'));
    render(<App />);

    await screen.findByText('Welcome back, collector_28.');
    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The request could not be completed. Please try again.');
    expect(screen.getByText('Welcome back, collector_28.')).toBeInTheDocument();
  });
});
