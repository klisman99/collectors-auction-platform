import { describe, expect, test } from 'vitest';

import type { Auction, PublicBid } from '../api/client';
import {
  type AuctionRoomEvent,
  type AuctionRoomSnapshot,
  applyAuctionEvent,
} from './auctionRoomState';

const auction = {
  id: 'auction-39',
  projectionVersion: 4,
  state: 'LIVE',
  currentAmountCents: 10_000,
  effectiveEndAt: '2026-09-17T20:00:00Z',
} as Auction;

const bids: PublicBid[] = [
  {
    amountCents: 10_000,
    acceptedAt: '2026-09-17T19:00:00Z',
    sequence: 1,
    bidderPseudonym: 'Bidder-A1B2C3D4',
  },
];

const snapshot: AuctionRoomSnapshot = { auction, bids };

describe('auction room event ordering', () => {
  test.each([3, 4])('ignores duplicate or reordered version %s', (projectionVersion) => {
    expect(applyAuctionEvent(snapshot, event(projectionVersion))).toEqual({
      kind: 'UNCHANGED',
      snapshot,
    });
  });

  test('requires an authoritative snapshot for the next committed version', () => {
    expect(applyAuctionEvent(snapshot, event(5))).toEqual({
      kind: 'REFRESH_REQUIRED',
      afterVersion: 4,
    });
  });

  test('requires the same recovery path when frames reveal a missed version', () => {
    expect(applyAuctionEvent(snapshot, event(7))).toEqual({
      kind: 'REFRESH_REQUIRED',
      afterVersion: 4,
    });
  });
});

function event(projectionVersion: number): AuctionRoomEvent {
  return {
    auctionId: 'auction-39',
    type: 'BID_ACCEPTED',
    projectionVersion,
    bidSequence: projectionVersion,
    occurredAt: '2026-09-17T19:01:00Z',
  };
}
