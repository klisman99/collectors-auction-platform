import { useEffect, useState } from 'react';

import {
  type AdministrativeAuditHistoryEvent,
  type AuditHistoryEvent,
  type AuditHistoryPage,
  listAdministrativeAuditHistory,
  listMyHistory,
  listOperationalAuditHistory,
  listPublicAuctionTimeline,
} from '../api/client';
import { formatBrl, formatSaoPaulo } from '../auctions/presentation';

type HistoryLoader = (page: number, size: number) => Promise<AuditHistoryPage>;

export function AccountHistoryWorkspace() {
  return (
    <HistoryLedger
      emptyMessage="No account history has been recorded yet."
      introductoryText="Your submitted items, auctions, bids, account actions, purchases, sales, and settlement updates appear here."
      load={listMyHistory}
      title="Your history"
    />
  );
}

export function OperationalHistoryWorkspace({ administrator }: { administrator: boolean }) {
  return (
    <HistoryLedger
      administrator={administrator}
      emptyMessage={
        administrator
          ? 'No operational history has been recorded yet.'
          : 'No moderation or auction-suspension history has been recorded yet.'
      }
      introductoryText={
        administrator
          ? 'Complete operational history includes actor attribution, protected identities, exact reserve context, and internal notes.'
          : 'This scope contains moderation and auction-suspension facts only. Reserve values and unrelated account data stay restricted.'
      }
      load={administrator ? listAdministrativeAuditHistory : listOperationalAuditHistory}
      title={administrator ? 'Operational history' : 'Moderation history'}
    />
  );
}

export function PublicAuctionAuditTimeline({ auctionId }: { auctionId: string }) {
  const [timeline, setTimeline] = useState<AuditHistoryPage | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  useEffect(() => {
    setTimeline(null);
    setFailure(null);
    listPublicAuctionTimeline(auctionId)
      .then(setTimeline)
      .catch((error) =>
        setFailure(
          error instanceof Error ? error.message : 'Auction timeline could not be loaded.',
        ),
      );
  }, [auctionId]);

  if (failure !== null) {
    return <p className="mt-2 text-sm text-rose-300">{failure}</p>;
  }

  if (timeline === null) {
    return <p className="mt-2 text-sm text-slate-400">Loading recorded auction history…</p>;
  }

  if (timeline.content.length === 0) {
    return <p className="mt-2 text-sm text-slate-400">No recorded public events yet.</p>;
  }

  return <EventList events={timeline.content} />;
}

function HistoryLedger({
  administrator = false,
  emptyMessage,
  introductoryText,
  load,
  title,
}: {
  administrator?: boolean;
  emptyMessage: string;
  introductoryText: string;
  load: HistoryLoader;
  title: string;
}) {
  const [history, setHistory] = useState<AuditHistoryPage | null>(null);
  const [page, setPage] = useState(0);
  const [failure, setFailure] = useState<string | null>(null);

  useEffect(() => {
    setHistory(null);
    setFailure(null);
    load(page, 20)
      .then(setHistory)
      .catch((error) =>
        setFailure(error instanceof Error ? error.message : 'History could not be loaded.'),
      );
  }, [load, page]);

  return (
    <section
      aria-labelledby={`${title.toLowerCase().replaceAll(' ', '-')}-heading`}
      className="mt-8 border-t border-slate-700 pt-7"
    >
      <h2
        className="text-2xl font-semibold text-white"
        id={`${title.toLowerCase().replaceAll(' ', '-')}-heading`}
      >
        {title}
      </h2>
      <p className="mt-2 max-w-3xl leading-6 text-slate-300">{introductoryText}</p>
      {failure !== null && <p className="mt-4 text-sm text-rose-300">{failure}</p>}
      {history === null && failure === null && (
        <p className="mt-4 text-sm text-slate-400">Loading history…</p>
      )}
      {history !== null && (
        <>
          {history.content.length === 0 ? (
            <p className="mt-4 text-sm text-slate-400">{emptyMessage}</p>
          ) : (
            <EventList administrator={administrator} events={history.content} />
          )}
          <Pagination page={history} onPageChange={setPage} />
        </>
      )}
    </section>
  );
}

function EventList({
  administrator = false,
  events,
}: {
  administrator?: boolean;
  events: AuditHistoryEvent[];
}) {
  return (
    <ol className="mt-5 border-l border-cyan-800/80 pl-5">
      {events.map((event) => (
        <li className="relative pb-5 last:pb-0" key={event.id}>
          <span className="absolute -left-[1.58rem] top-1.5 h-2.5 w-2.5 rounded-full border-2 border-slate-950 bg-cyan-300" />
          <p className="text-sm font-semibold text-white">{actionLabel(event.action)}</p>
          <p className="mt-1 text-sm text-slate-400">{formatSaoPaulo(event.occurredAt)}</p>
          <p className="mt-1 text-sm text-slate-300">{targetLabel(event)}</p>
          {event.amountCents !== undefined && (
            <p className="mt-1 text-sm text-emerald-200">{formatBrl(event.amountCents)}</p>
          )}
          {event.bidderPseudonym !== undefined && (
            <p className="mt-1 text-sm text-slate-300">Bidder {event.bidderPseudonym}</p>
          )}
          {event.reasonCategory !== undefined && (
            <p className="mt-1 text-sm text-slate-300">
              Reason category: {reasonLabel(event.reasonCategory)}
            </p>
          )}
          {event.publicReason !== undefined && event.publicReason !== '' && (
            <p className="mt-1 text-sm text-slate-300">{event.publicReason}</p>
          )}
          {event.internalNote !== undefined && event.internalNote !== '' && (
            <p className="mt-2 border-l-2 border-amber-500/70 pl-3 text-sm text-amber-100">
              Internal note: {event.internalNote}
            </p>
          )}
          {administrator && 'metadata' in event && (
            <AdministratorDetails event={event as AdministrativeAuditHistoryEvent} />
          )}
        </li>
      ))}
    </ol>
  );
}

function AdministratorDetails({ event }: { event: AdministrativeAuditHistoryEvent }) {
  return (
    <details className="mt-2 text-sm text-slate-400">
      <summary className="cursor-pointer text-cyan-200">Protected operational details</summary>
      <p className="mt-2 break-words text-slate-300">{event.metadata}</p>
      <p className="mt-1 break-all text-xs text-slate-500">
        Actor: {event.actorType === 'SYSTEM' ? 'System' : `${event.actorType} ${event.actorId}`} ·{' '}
        Target: {event.targetType} {event.targetId}
      </p>
    </details>
  );
}

function Pagination({
  onPageChange,
  page,
}: {
  onPageChange: (page: number) => void;
  page: AuditHistoryPage;
}) {
  if (page.totalPages < 2) return null;

  return (
    <div className="mt-5 flex items-center justify-between text-sm text-slate-300">
      <button
        className="rounded border border-slate-600 px-3 py-1.5 disabled:cursor-not-allowed disabled:opacity-40"
        disabled={page.page === 0}
        onClick={() => onPageChange(page.page - 1)}
        type="button"
      >
        Previous
      </button>
      <span>
        Page {page.page + 1} of {page.totalPages}
      </span>
      <button
        className="rounded border border-slate-600 px-3 py-1.5 disabled:cursor-not-allowed disabled:opacity-40"
        disabled={page.page + 1 >= page.totalPages}
        onClick={() => onPageChange(page.page + 1)}
        type="button"
      >
        Next
      </button>
    </div>
  );
}

function actionLabel(action: string): string {
  return (
    {
      REGULAR_ACCOUNT_REGISTERED: 'Account registered',
      REGULAR_ACCOUNT_VERIFIED: 'Email verified',
      REGULAR_ACCOUNT_PASSWORD_RESET: 'Password reset',
      REGULAR_ACCOUNT_SUSPENDED: 'Account suspended',
      REGULAR_ACCOUNT_REACTIVATED: 'Account reactivated',
      INITIAL_ADMINISTRATOR_CREATED: 'Initial administrator created',
      OPERATIONAL_ACCOUNT_INVITED: 'Operational account invited',
      OPERATIONAL_ACCOUNT_ACTIVATED: 'Operational account activated',
      OPERATIONAL_ACCOUNT_DEACTIVATED: 'Operational account deactivated',
      COLLECTIBLE_SUBMITTED: 'Collectible submitted',
      COLLECTIBLE_APPROVED: 'Collectible approved',
      COLLECTIBLE_REJECTED: 'Collectible rejected',
      AUCTION_SCHEDULED: 'Auction scheduled',
      AUCTION_RESCHEDULED: 'Auction rescheduled',
      AUCTION_STARTED: 'Auction started',
      AUCTION_CANCELLED: 'Auction cancelled',
      AUCTION_ENDED: 'Auction ended',
      AUCTION_SOLD: 'Auction sold',
      AUCTION_UNSOLD: 'Auction ended unsold',
      AUCTION_AWAITING_SELLER_DECISION: 'Awaiting seller decision',
      AUCTION_SELLER_DECISION_ACCEPTED: 'Seller accepted final offer',
      AUCTION_SELLER_DECISION_REJECTED: 'Seller rejected final offer',
      AUCTION_SELLER_DECISION_EXPIRED: 'Seller decision expired',
      AUCTION_SELLER_DECISION_REOPENED: 'Seller decision reopened',
      AUCTION_SELLER_DECISION_NO_ELIGIBLE_BID: 'No eligible bid remained',
      AUCTION_SUSPENDED: 'Auction suspended',
      AUCTION_RELEASED: 'Auction released for rescheduling',
      AUCTION_RESUMED: 'Auction resumed',
      AUCTION_ADMINISTRATIVELY_CANCELLED: 'Auction administratively cancelled',
      BID_ACCEPTED: 'Bid accepted',
      BID_DISQUALIFIED: 'Bid disqualified',
      SALE_CREATED: 'Sale created',
      SALE_PAYMENT_RECORDED: 'Payment recorded',
      SALE_SHIPMENT_RECORDED: 'Shipment recorded',
      SALE_PAYMENT_EXPIRED: 'Payment deadline expired',
      SALE_SHIPMENT_EXPIRED: 'Shipment deadline expired',
      SALE_DELIVERY_CONFIRMED: 'Delivery confirmed',
      SALE_DELIVERY_CONFIRMATION_DEADLINE_EXPIRED: 'Delivery confirmation deadline expired',
    }[action] ?? action.replaceAll('_', ' ').toLowerCase()
  );
}

function targetLabel(event: AuditHistoryEvent): string {
  if (event.saleId !== undefined) return 'Settlement event';
  if (event.auctionId !== undefined) return 'Auction event';
  if (event.itemId !== undefined) return 'Collectible event';
  return event.targetType === 'REGULAR_ACCOUNT' ? 'Account event' : 'Operational event';
}

function reasonLabel(category: string): string {
  return category
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/^./, (letter) => letter.toUpperCase());
}
