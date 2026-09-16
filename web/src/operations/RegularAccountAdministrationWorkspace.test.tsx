import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

vi.mock('../api/client', () => ({
  getRegularAccounts: vi.fn(),
  reactivateRegularAccount: vi.fn(),
  suspendRegularAccount: vi.fn(),
}));

import { getRegularAccounts, reactivateRegularAccount, suspendRegularAccount } from '../api/client';
import { RegularAccountAdministrationWorkspace } from './RegularAccountAdministrationWorkspace';

describe('RegularAccountAdministrationWorkspace', () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(getRegularAccounts).mockResolvedValue([
      {
        id: 'active-37',
        email: 'active-37@example.com',
        publicHandle: 'active_37',
        status: 'ACTIVE',
        verified: true,
      },
      {
        id: 'suspended-37',
        email: 'suspended-37@example.com',
        publicHandle: 'suspended_37',
        status: 'SUSPENDED',
        verified: true,
      },
    ]);
    vi.mocked(suspendRegularAccount).mockReset();
    vi.mocked(reactivateRegularAccount).mockReset();
  });

  test('suspends an active account with the public reason and private note', async () => {
    vi.mocked(suspendRegularAccount).mockResolvedValue({
      id: 'active-37',
      email: 'active-37@example.com',
      publicHandle: 'active_37',
      status: 'SUSPENDED',
      verified: true,
    });
    render(<RegularAccountAdministrationWorkspace />);

    await screen.findByText('active-37@example.com');
    fireEvent.change(screen.getByLabelText('Regular account'), {
      target: { value: 'active-37' },
    });
    fireEvent.change(screen.getByLabelText('Public reason'), {
      target: { value: 'The account needs a security review.' },
    });
    fireEvent.change(screen.getByLabelText('Internal note'), {
      target: { value: 'Retain the reviewer case number privately.' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Suspend account' }));

    await waitFor(() =>
      expect(suspendRegularAccount).toHaveBeenCalledWith('active-37', {
        reasonCategory: 'SECURITY',
        publicReason: 'The account needs a security review.',
        internalNote: 'Retain the reviewer case number privately.',
      }),
    );
  });

  test('allows an administrator to reactivate an account without restoring its bids', async () => {
    vi.mocked(reactivateRegularAccount).mockResolvedValue({
      id: 'suspended-37',
      email: 'suspended-37@example.com',
      publicHandle: 'suspended_37',
      status: 'ACTIVE',
      verified: true,
    });
    render(<RegularAccountAdministrationWorkspace />);

    fireEvent.click(await screen.findByRole('button', { name: 'Reactivate suspended_37' }));
    fireEvent.change(screen.getByLabelText('Public reason'), {
      target: { value: 'The security review is complete.' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Reactivate account' }));

    await waitFor(() =>
      expect(reactivateRegularAccount).toHaveBeenCalledWith('suspended-37', {
        reasonCategory: 'SECURITY',
        publicReason: 'The security review is complete.',
        internalNote: undefined,
      }),
    );
    expect(
      await screen.findByText(
        'The account was reactivated. Its disqualified bids remain permanent.',
      ),
    ).toBeInTheDocument();
  });
});
