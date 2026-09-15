import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest';

vi.mock('../api/client', () => ({
  ApiError: class ApiError extends Error {
    code?: string;
    requiredAmountCents?: number;

    constructor(problem: { code?: string; detail?: string; requiredAmountCents?: number }) {
      super(problem.detail ?? 'Bid failed.');
      this.code = problem.code;
      this.requiredAmountCents = problem.requiredAmountCents;
    }
  },
  getAuction: vi.fn(),
  listAuctions: vi.fn(),
  listPublicBids: vi.fn(),
  placeBid: vi.fn(),
}));

import { type Auction, getAuction, listAuctions, listPublicBids, placeBid } from '../api/client';
import { PublicAuctionBrowser } from './PublicAuctionBrowser';

const liveAuction: Auction = {
  id: 'auction-35',
  itemId: 'item-35',
  sellerHandle: 'seller_35',
  state: 'LIVE',
  openingAmountCents: 10_000,
  currentAmountCents: 10_000,
  minimumIncrementCents: 1_000,
  reserveMet: false,
  startsAt: '2026-09-13T20:00:00Z',
  endsAt: '2026-09-13T22:00:00Z',
  effectiveEndAt: '2026-09-13T22:00:00Z',
  scheduledAt: '2026-09-13T19:00:00Z',
  item: {
    category: 'CARDS',
    title: 'Live bidding card',
    description: 'A card currently accepting bids.',
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
  timeline: [{ type: 'STARTED', occurredAt: '2026-09-13T20:00:00Z' }],
  eligibleBidHistory: [],
  disqualifications: [],
};

describe('PublicAuctionBrowser bidding', () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(listAuctions).mockResolvedValue({
      content: [liveAuction],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    vi.mocked(getAuction).mockResolvedValue(liveAuction);
    vi.mocked(listPublicBids).mockResolvedValue([]);
    vi.mocked(placeBid).mockReset();
  });

  test('submits required amount and renders the authoritative accepted result and history', async () => {
    vi.mocked(placeBid).mockResolvedValue({
      status: 'ACCEPTED',
      code: 'BID_ACCEPTED',
      amountCents: 10_000,
      requiredAmountCents: 10_000,
      sequence: 1,
      bidderPseudonym: 'Bidder-A1B2C3D4',
      acceptedAt: '2026-09-13T20:01:00Z',
    });
    vi.mocked(listPublicBids)
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([
        {
          amountCents: 10_000,
          sequence: 1,
          bidderPseudonym: 'Bidder-A1B2C3D4',
          acceptedAt: '2026-09-13T20:01:00Z',
        },
      ]);

    render(<PublicAuctionBrowser canBid />);

    fireEvent.click(await screen.findByRole('tab', { name: 'Live auctions' }));
    fireEvent.click(await screen.findByRole('button', { name: 'View Live bidding card' }));
    expect(await screen.findByText(/Required bid/)).toHaveTextContent(/R\$\s*100,00/);
    fireEvent.click(screen.getByRole('button', { name: 'Place bid' }));

    await waitFor(() =>
      expect(placeBid).toHaveBeenCalledWith(
        'auction-35',
        expect.objectContaining({ amountCents: 10_000, idempotencyKey: expect.any(String) }),
      ),
    );
    expect(await screen.findByRole('status')).toHaveTextContent('Bid accepted as #1');
    expect(await screen.findByText('Bidder-A1B2C3D4')).toBeInTheDocument();
  });

  test('replaces the live-auction deadline with the committed snapshot after a late bid', async () => {
    const extendedAuction: Auction = {
      ...liveAuction,
      currentAmountCents: 10_000,
      endsAt: '2026-09-13T23:00:00Z',
      effectiveEndAt: '2026-09-13T23:00:00Z',
    };
    vi.mocked(getAuction).mockResolvedValueOnce(liveAuction).mockResolvedValueOnce(extendedAuction);
    vi.mocked(placeBid).mockResolvedValue({
      status: 'ACCEPTED',
      code: 'BID_ACCEPTED',
      amountCents: 10_000,
      requiredAmountCents: 10_000,
      sequence: 1,
      bidderPseudonym: 'Bidder-A1B2C3D4',
      acceptedAt: '2026-09-13T21:00:00Z',
    });

    render(<PublicAuctionBrowser canBid />);

    fireEvent.click(await screen.findByRole('tab', { name: 'Live auctions' }));
    fireEvent.click(await screen.findByRole('button', { name: 'View Live bidding card' }));
    const auctionCard = screen.getByRole('heading', { name: 'Live bidding card' }).closest('li');
    if (auctionCard === null) throw new Error('The live-auction card was not rendered.');
    expect(auctionCard).toHaveTextContent('ends Sep 13, 2026, 7:00 PM');
    fireEvent.click(await screen.findByRole('button', { name: 'Place bid' }));

    await waitFor(() => expect(auctionCard).toHaveTextContent('ends Sep 13, 2026, 8:00 PM'));
    expect(auctionCard).not.toHaveTextContent('ends Sep 13, 2026, 7:00 PM');
  });

  test.each([
    ['BID_RATE_LIMITED', undefined, 'Bid rate limit reached'],
    ['BID_LATE_OR_UNAVAILABLE', undefined, 'bid was late'],
    ['BID_IDEMPOTENCY_CONFLICT', undefined, 'command key was already used'],
    ['BID_AMOUNT_TOO_LOW', 11_000, 'Required amount is R$'],
  ])('renders the %s server outcome without inventing a bid', async (code, required, message) => {
    const { ApiError } = await import('../api/client');
    vi.mocked(placeBid).mockRejectedValue(
      new ApiError(
        { code, detail: 'Server rejected the command.', requiredAmountCents: required },
        'Bid failed.',
      ),
    );
    render(<PublicAuctionBrowser canBid />);

    fireEvent.click(await screen.findByRole('tab', { name: 'Live auctions' }));
    fireEvent.click(await screen.findByRole('button', { name: 'View Live bidding card' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Place bid' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.getByText('No eligible bids.')).toBeInTheDocument();
  });
});
