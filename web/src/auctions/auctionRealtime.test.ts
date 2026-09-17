import { describe, expect, test, vi } from 'vitest';

import type { Auction, PublicBid } from '../api/client';
import { connectAuctionRoom, loadConsistentAuctionRoomSnapshot } from './auctionRealtime';
import type { AuctionRoomSnapshot } from './auctionRoomState';

describe('auction room STOMP recovery', () => {
  test('retries a REST snapshot that straddles a committed auction version', async () => {
    const getAuction = vi
      .fn<(id: string) => Promise<Auction>>()
      .mockResolvedValueOnce(snapshot(1).auction)
      .mockResolvedValueOnce(snapshot(2).auction)
      .mockResolvedValueOnce(snapshot(2).auction)
      .mockResolvedValueOnce(snapshot(2).auction);
    const staleBids: PublicBid[] = [];
    const currentBids: PublicBid[] = [
      {
        amountCents: 12_000,
        sequence: 2,
        bidderPseudonym: 'Bidder-A1B2C3D4',
        acceptedAt: '2026-09-17T19:01:00Z',
      },
    ];
    const getBids = vi.fn().mockResolvedValueOnce(staleBids).mockResolvedValueOnce(currentBids);

    await expect(
      loadConsistentAuctionRoomSnapshot('auction-39', getAuction, getBids),
    ).resolves.toEqual({ auction: snapshot(2).auction, bids: currentBids });
    expect(getBids).toHaveBeenCalledTimes(2);
  });

  test('subscribes before loading a snapshot and recovers an event received during that load', async () => {
    const socket = new FakeWebSocket();
    const actions: string[] = [];
    const firstSnapshot = deferred<AuctionRoomSnapshot>();
    const secondSnapshot = deferred<AuctionRoomSnapshot>();
    const loadSnapshot = vi
      .fn<() => Promise<AuctionRoomSnapshot>>()
      .mockImplementationOnce(() => {
        actions.push('snapshot');
        return firstSnapshot.promise;
      })
      .mockImplementationOnce(() => secondSnapshot.promise);
    const onSnapshot = vi.fn();

    connectAuctionRoom({
      auctionId: 'auction-39',
      loadSnapshot,
      onSnapshot,
      socketFactory: () => socket as unknown as WebSocket,
      reconnect: () => 0,
    });

    socket.open();
    socket.receive('CONNECTED\nversion:1.2\n\n\0');
    actions.unshift(socket.sent.find((frame) => frame.startsWith('SUBSCRIBE')) ? 'subscribe' : '');
    socket.receive(
      'MESSAGE\ndestination:/topic/auctions/auction-39\ncontent-type:application/json\n\n' +
        JSON.stringify({
          auctionId: 'auction-39',
          type: 'BID_ACCEPTED',
          projectionVersion: 2,
          bidSequence: 1,
          occurredAt: '2026-09-17T19:01:00Z',
        }) +
        '\0',
    );
    firstSnapshot.resolve(snapshot(1));
    await vi.waitFor(() => expect(loadSnapshot).toHaveBeenCalledTimes(2));
    secondSnapshot.resolve(snapshot(2));
    await vi.waitFor(() => expect(onSnapshot).toHaveBeenLastCalledWith(snapshot(2)));

    expect(actions).toEqual(['subscribe', 'snapshot']);
  });

  test('loads a new authoritative snapshot after every reconnect', async () => {
    const first = new FakeWebSocket();
    const second = new FakeWebSocket();
    const socketFactory = vi
      .fn<() => WebSocket>()
      .mockReturnValueOnce(first as unknown as WebSocket)
      .mockReturnValueOnce(second as unknown as WebSocket);
    const loadSnapshot = vi.fn().mockResolvedValue(snapshot(3));
    const reconnect = vi.fn((callback: () => void) => {
      callback();
      return 0;
    });

    const disconnect = connectAuctionRoom({
      auctionId: 'auction-39',
      loadSnapshot,
      onSnapshot: vi.fn(),
      socketFactory,
      reconnect,
    });
    first.open();
    first.receive('CONNECTED\nversion:1.2\n\n\0');
    await vi.waitFor(() => expect(loadSnapshot).toHaveBeenCalledTimes(1));
    first.closeFromServer();
    second.open();
    second.receive('CONNECTED\nversion:1.2\n\n\0');
    await vi.waitFor(() => expect(loadSnapshot).toHaveBeenCalledTimes(2));

    disconnect();
  });
});

function snapshot(projectionVersion: number): AuctionRoomSnapshot {
  return {
    auction: {
      id: 'auction-39',
      projectionVersion,
    } as Auction,
    bids: [],
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((complete) => {
    resolve = complete;
  });
  return { promise, resolve };
}

class FakeWebSocket {
  sent: string[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((event: MessageEvent<string>) => void) | null = null;
  onclose: (() => void) | null = null;

  send(frame: string) {
    this.sent.push(frame);
  }

  close() {}

  open() {
    this.onopen?.();
  }

  receive(data: string) {
    this.onmessage?.({ data } as MessageEvent<string>);
  }

  closeFromServer() {
    this.onclose?.();
  }
}
