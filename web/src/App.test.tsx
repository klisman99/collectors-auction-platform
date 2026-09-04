import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

vi.mock('./api/client', () => ({
  ApiError: class ApiError extends Error {
    code?: string;
    fieldErrors?: Array<{ field?: string; message?: string }>;
  },
  getAuthenticatedSession: vi.fn(),
  registerAccount: vi.fn(),
  signIn: vi.fn(),
  verifyEmail: vi.fn(),
}));

import { App } from './App';
import { ApiError, getAuthenticatedSession, registerAccount, signIn, verifyEmail } from './api/client';

describe('App', () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(getAuthenticatedSession).mockResolvedValue(null);
    vi.mocked(registerAccount).mockReset();
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
});
