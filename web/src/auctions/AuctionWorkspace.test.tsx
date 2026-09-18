import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

vi.mock('../api/client', () => ({
  cancelAuction: vi.fn(),
  decideBelowReserveOffer: vi.fn(),
  listMyScheduledAuctions: vi.fn(),
  scheduleAuction: vi.fn(),
  updateAuctionTerms: vi.fn(),
}));

import { type Auction, decideBelowReserveOffer, listMyScheduledAuctions } from '../api/client';
import { AuctionWorkspace } from './AuctionWorkspace';

const awaitingDecision: Auction = {
  id: 'auction-40',
  itemId: 'item-40',
  sellerHandle: 'seller_40',
  projectionVersion: 9,
  state: 'AWAITING_SELLER_DECISION',
  openingAmountCents: 10_000,
  currentAmountCents: 10_000,
  minimumIncrementCents: 1_000,
  reserveMet: false,
  startsAt: '2026-09-17T20:00:00Z',
  endsAt: '2026-09-17T22:00:00Z',
  effectiveEndAt: '2026-09-17T22:00:00Z',
  scheduledAt: '2026-09-17T19:00:00Z',
  sellerDecisionDeadlineAt: '2026-09-18T22:00:00Z',
  finalOutcome: {
    amountCents: 10_000,
    bidderPseudonym: 'Bidder-40ABCD',
    recordedAt: '2026-09-17T22:00:00Z',
  },
  item: {
    category: 'CARDS',
    title: 'Below-reserve card',
    description: 'A collectible card with an awaiting final offer.',
    condition: 'EXCELLENT',
    conditionNotes: 'No visible wear.',
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
  timeline: [],
  eligibleBidHistory: [],
  disqualifications: [],
};

describe('AuctionWorkspace seller decisions', () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(listMyScheduledAuctions).mockResolvedValue([awaitingDecision]);
    vi.mocked(decideBelowReserveOffer).mockReset();
  });

  test('lets the seller accept the pseudonymous below-reserve offer by its durable deadline', async () => {
    vi.mocked(decideBelowReserveOffer).mockResolvedValue({
      ...awaitingDecision,
      state: 'SOLD',
      sellerDecisionDeadlineAt: undefined,
      finalOutcome: {
        ...awaitingDecision.finalOutcome,
        bidderHandle: 'winning_bidder_40',
      },
    });
    render(<AuctionWorkspace approvedItems={[]} />);

    fireEvent.click(await screen.findByRole('button', { name: 'Manage Below-reserve card' }));

    expect(await screen.findByText('Below-reserve final offer')).toBeInTheDocument();
    expect(screen.getByText(/The bidder remains pseudonymous/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Accept final offer' }));

    await waitFor(() =>
      expect(decideBelowReserveOffer).toHaveBeenCalledWith('auction-40', 'ACCEPT'),
    );
    expect(
      await screen.findByText('Below-reserve offer accepted. A sale was created.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Buyer: winning_bidder_40')).toBeInTheDocument();
  });
});
