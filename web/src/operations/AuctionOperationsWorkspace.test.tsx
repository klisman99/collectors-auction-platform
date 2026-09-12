import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

vi.mock('../api/client', () => ({
  administrativelyCancelAuction: vi.fn(),
  listAuctions: vi.fn(),
  listSuspendedAuctions: vi.fn(),
  releaseSuspendedAuction: vi.fn(),
  resumeSuspendedAuction: vi.fn(),
  suspendAuction: vi.fn(),
}));

import {
  administrativelyCancelAuction,
  listAuctions,
  listSuspendedAuctions,
  resumeSuspendedAuction,
  suspendAuction,
} from '../api/client';
import { AuctionOperationsWorkspace } from './AuctionOperationsWorkspace';

const suspended = {
  id: 'auction-34',
  itemTitle: 'Provenance review card',
  sellerHandle: 'collector_34',
  state: 'SUSPENDED',
  sourceState: 'LIVE' as const,
  suspendedAt: '2026-09-12T12:00:00Z',
  remainingDurationMillis: 137_000,
  effectiveEndAt: '2026-09-12T12:02:17Z',
  timeline: [
    {
      type: 'SUSPENDED',
      occurredAt: '2026-09-12T12:00:00Z',
      reasonCategory: 'POLICY_REVIEW',
      publicReason: 'The listing requires an operational review.',
    },
  ],
};

describe('AuctionOperationsWorkspace', () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(listAuctions).mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    vi.mocked(listSuspendedAuctions).mockResolvedValue([suspended]);
    vi.mocked(suspendAuction).mockReset();
    vi.mocked(administrativelyCancelAuction).mockReset();
    vi.mocked(resumeSuspendedAuction).mockReset();
  });

  test('shows frozen live time while keeping resolution administrator-only', async () => {
    render(<AuctionOperationsWorkspace administrator={false} />);

    expect(await screen.findByText('Provenance review card')).toBeInTheDocument();
    expect(screen.getByText('2m 17s remaining')).toBeInTheDocument();
    expect(
      screen.getByText('An administrator must release, resume, or cancel this auction.'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Resume with remaining time' })).toBeNull();
  });

  test('administrator cancellation sends the selected item disposition', async () => {
    vi.mocked(administrativelyCancelAuction).mockResolvedValue({
      ...suspended,
      state: 'CANCELLED',
    });
    render(<AuctionOperationsWorkspace administrator />);
    await screen.findByText('Provenance review card');
    fireEvent.change(screen.getByLabelText('Public reason'), {
      target: { value: 'The item approval must be reviewed.' },
    });
    fireEvent.change(screen.getByLabelText('Cancellation item disposition'), {
      target: { value: 'REVOKE_APPROVAL_TO_DRAFT' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Cancel auction' }));

    await waitFor(() =>
      expect(administrativelyCancelAuction).toHaveBeenCalledWith('auction-34', {
        reasonCategory: 'POLICY_REVIEW',
        publicReason: 'The item approval must be reviewed.',
        internalNote: undefined,
        itemDisposition: 'REVOKE_APPROVAL_TO_DRAFT',
      }),
    );
  });

  test('shows an authorization failure returned while resolving a suspension', async () => {
    vi.mocked(resumeSuspendedAuction).mockRejectedValue(
      new Error('Only an administrator may resolve a suspended auction.'),
    );
    render(<AuctionOperationsWorkspace administrator />);
    await screen.findByText('Provenance review card');
    fireEvent.click(screen.getByRole('button', { name: 'Resume with remaining time' }));

    expect(
      await screen.findByText('Only an administrator may resolve a suspended auction.'),
    ).toBeInTheDocument();
  });
});
