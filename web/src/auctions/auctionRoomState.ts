import type { Auction, PublicBid } from '../api/client';

export type AuctionRoomSnapshot = {
  auction: Auction;
  bids: PublicBid[];
};

export type AuctionRoomEvent = {
  auctionId: string;
  type:
    | 'BID_ACCEPTED'
    | 'BID_DISQUALIFIED'
    | 'AUCTION_SCHEDULED'
    | 'AUCTION_RESCHEDULED'
    | 'AUCTION_STARTED'
    | 'AUCTION_CANCELLED'
    | 'AUCTION_ENDED'
    | 'AUCTION_SOLD'
    | 'AUCTION_UNSOLD'
    | 'AUCTION_AWAITING_SELLER_DECISION'
    | 'AUCTION_SUSPENDED'
    | 'AUCTION_RELEASED'
    | 'AUCTION_RESUMED';
  projectionVersion: number;
  bidSequence?: number;
  amountCents?: number;
  bidderPseudonym?: string;
  currentAmountCents?: number;
  nextMinimumAmountCents?: number;
  reserveMet?: boolean;
  effectiveEndAt?: string;
  occurredAt: string;
};

export type AuctionRoomEventResult =
  | { kind: 'UNCHANGED'; snapshot: AuctionRoomSnapshot }
  | { kind: 'REFRESH_REQUIRED'; afterVersion: number };

export function applyAuctionEvent(
  snapshot: AuctionRoomSnapshot,
  event: AuctionRoomEvent,
): AuctionRoomEventResult {
  if (
    event.auctionId !== snapshot.auction.id ||
    event.projectionVersion <= snapshot.auction.projectionVersion
  ) {
    return { kind: 'UNCHANGED', snapshot };
  }
  return {
    kind: 'REFRESH_REQUIRED',
    afterVersion: snapshot.auction.projectionVersion,
  };
}
