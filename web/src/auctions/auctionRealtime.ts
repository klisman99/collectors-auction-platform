import {
  type Auction,
  getAuction as getAuctionSnapshot,
  listPublicBids as getPublicBids,
  type PublicBid,
} from '../api/client';
import type { AuctionRoomEvent, AuctionRoomSnapshot } from './auctionRoomState';

export async function loadConsistentAuctionRoomSnapshot(
  auctionId: string,
  getAuction: (id: string) => Promise<Auction> = getAuctionSnapshot,
  listPublicBids: (id: string) => Promise<PublicBid[]> = getPublicBids,
): Promise<AuctionRoomSnapshot> {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const before = await getAuction(auctionId);
    const bids = await listPublicBids(auctionId);
    const after = await getAuction(auctionId);
    if (before.projectionVersion === after.projectionVersion) {
      return { auction: after, bids };
    }
  }
  throw new Error('The auction changed repeatedly while loading its snapshot.');
}

type AuctionRoomConnection = {
  auctionId: string;
  loadSnapshot: () => Promise<AuctionRoomSnapshot>;
  onSnapshot: (snapshot: AuctionRoomSnapshot) => void;
  onConnectionState?: (state: 'CONNECTING' | 'CONNECTED' | 'RECONNECTING') => void;
  onError?: () => void;
  socketFactory?: () => WebSocket;
  reconnect?: (callback: () => void) => number;
};

export function connectAuctionRoom(options: AuctionRoomConnection): () => void {
  let stopped = false;
  let socket: WebSocket | null = null;
  let currentSnapshot: AuctionRoomSnapshot | null = null;
  let pendingVersion = 0;
  let refreshing = false;
  let reconnectTimer: number | null = null;

  const reconnect = options.reconnect ?? ((callback) => window.setTimeout(callback, 1_000));
  const socketFactory = options.socketFactory ?? sameOriginWebSocket;

  async function refreshSnapshot() {
    if (refreshing || stopped) return;
    refreshing = true;
    try {
      do {
        pendingVersion = 0;
        const snapshot = await options.loadSnapshot();
        if (stopped) return;
        currentSnapshot = snapshot;
        options.onSnapshot(snapshot);
      } while (pendingVersion > (currentSnapshot?.auction.projectionVersion ?? 0) && !stopped);
    } catch {
      options.onError?.();
      socket?.close();
    } finally {
      refreshing = false;
    }
  }

  function receive(frameData: string) {
    for (const rawFrame of frameData.split('\0')) {
      const frame = rawFrame.trimStart();
      if (frame === '') continue;
      const separator = frame.indexOf('\n\n');
      const headerLines = (separator === -1 ? frame : frame.slice(0, separator)).split('\n');
      const command = headerLines[0];
      if (command === 'CONNECTED') {
        socket?.send(
          `SUBSCRIBE\nid:auction-room\ndestination:/topic/auctions/${options.auctionId}\nack:auto\n\n\0`,
        );
        options.onConnectionState?.('CONNECTED');
        void refreshSnapshot();
        continue;
      }
      if (command !== 'MESSAGE' || separator === -1) continue;
      const event = JSON.parse(frame.slice(separator + 2)) as AuctionRoomEvent;
      if (event.auctionId !== options.auctionId) continue;
      const currentVersion = currentSnapshot?.auction.projectionVersion ?? 0;
      if (event.projectionVersion <= currentVersion) continue;
      pendingVersion = Math.max(pendingVersion, event.projectionVersion);
      void refreshSnapshot();
    }
  }

  function open() {
    if (stopped) return;
    options.onConnectionState?.(currentSnapshot === null ? 'CONNECTING' : 'RECONNECTING');
    socket = socketFactory();
    socket.onopen = () => {
      const host = globalThis.location?.host ?? 'localhost';
      socket?.send(`CONNECT\naccept-version:1.2\nhost:${host}\nheart-beat:0,0\n\n\0`);
    };
    socket.onmessage = (event) => receive(String(event.data));
    socket.onerror = () => socket?.close();
    socket.onclose = () => {
      if (stopped) return;
      options.onConnectionState?.('RECONNECTING');
      reconnectTimer = reconnect(open);
    };
  }

  open();

  return () => {
    stopped = true;
    if (reconnectTimer !== null) window.clearTimeout(reconnectTimer);
    if (socket !== null) {
      socket.onclose = null;
      socket.close();
    }
  };
}

function sameOriginWebSocket(): WebSocket {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  return new WebSocket(`${protocol}//${window.location.host}/ws`);
}
