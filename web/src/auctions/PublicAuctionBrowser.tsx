import { useEffect, useState } from 'react';

import {
  type Auction,
  type AuctionPage,
  type AuctionView,
  getAuction,
  listAuctions,
} from '../api/client';
import { formatBrl, formatSaoPaulo } from './presentation';

const emptyPage: AuctionPage = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
};

export function PublicAuctionBrowser() {
  const [view, setView] = useState<AuctionView>('SCHEDULED');
  const [pageNumber, setPageNumber] = useState(0);
  const [page, setPage] = useState<AuctionPage>(emptyPage);
  const [selected, setSelected] = useState<Auction | null>(null);
  const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setFailure(null);
    setSelected(null);
    listAuctions(view, pageNumber)
      .then((result) => {
        if (active) setPage(result);
      })
      .catch(() => {
        if (active) setFailure('Auctions could not be loaded. Try again.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [view, pageNumber]);

  async function showDetails(auctionId: string) {
    setFailure(null);
    try {
      setSelected(await getAuction(auctionId));
    } catch {
      setFailure('Auction details could not be loaded. Try again.');
    }
  }

  return (
    <section className="mt-10 border-t border-slate-700 pt-8" aria-labelledby="auction-floor">
      <p className="text-xs font-semibold tracking-[0.2em] text-amber-300 uppercase">
        Public discovery
      </p>
      <h2 className="mt-2 text-2xl font-semibold text-white" id="auction-floor">
        Auction floor
      </h2>
      <p className="mt-2 text-sm leading-6 text-slate-300">
        Browse published collectibles. Amounts use BRL and deadlines use São Paulo time.
      </p>
      <div className="mt-5 flex gap-2" role="tablist" aria-label="Auction state">
        {(['SCHEDULED', 'LIVE', 'ENDED'] as const).map((state) => (
          <button
            aria-selected={view === state}
            className={`rounded-full px-3 py-1.5 text-sm ${view === state ? 'bg-amber-300 text-slate-950' : 'border border-slate-600 text-slate-200'}`}
            key={state}
            onClick={() => {
              setView(state);
              setPageNumber(0);
            }}
            role="tab"
            type="button"
          >
            {stateLabel(state)} auctions
          </button>
        ))}
      </div>

      {loading && <p className="mt-5 text-sm text-slate-300">Loading auctions…</p>}
      {failure !== null && (
        <p className="mt-5 text-sm text-rose-300" role="alert">
          {failure}
        </p>
      )}
      {!loading && failure === null && page.content.length === 0 && (
        <p className="mt-5 rounded-lg border border-slate-700 p-4 text-sm text-slate-300">
          No {stateLabel(view).toLowerCase()} auctions are available.
        </p>
      )}
      <ul className="mt-5 grid gap-3">
        {page.content.map((auction) => (
          <li className="rounded-xl border border-slate-700 bg-slate-950/60 p-4" key={auction.id}>
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-xs font-semibold tracking-wide text-cyan-300 uppercase">
                  {auction.state.replaceAll('_', ' ')} · {auction.sellerHandle}
                </p>
                <h3 className="mt-1 font-semibold text-white">{auction.item.title}</h3>
                <p className="mt-2 text-sm text-slate-300">
                  {formatBrl(auction.currentAmountCents)} · {deadlineLabel(auction)}
                </p>
              </div>
              <ProjectedCountdown auction={auction} />
            </div>
            <button
              aria-label={`View ${auction.item.title}`}
              className="mt-3 text-sm font-semibold text-amber-300"
              onClick={() => showDetails(auction.id)}
              type="button"
            >
              View details
            </button>
          </li>
        ))}
      </ul>
      {page.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between text-sm">
          <button
            disabled={page.page === 0}
            onClick={() => setPageNumber(page.page - 1)}
            type="button"
          >
            Previous
          </button>
          <span>
            Page {page.page + 1} of {page.totalPages}
          </span>
          <button
            disabled={page.page + 1 >= page.totalPages}
            onClick={() => setPageNumber(page.page + 1)}
            type="button"
          >
            Next
          </button>
        </div>
      )}
      {selected !== null && <AuctionDetails auction={selected} />}
    </section>
  );
}

function AuctionDetails({ auction }: { auction: Auction }) {
  return (
    <article className="mt-6 rounded-xl border border-cyan-800 bg-cyan-950/20 p-5">
      <p className="text-xs font-semibold tracking-wide text-cyan-300 uppercase">
        {auction.item.category} · {auction.item.condition}
      </p>
      <h3 className="mt-2 text-xl font-semibold text-white">{auction.item.title}</h3>
      <p className="mt-2 text-sm leading-6 text-slate-300">{auction.item.description}</p>
      {auction.item.media.length > 0 && (
        <div className="mt-4 flex gap-3 overflow-x-auto">
          {auction.item.media.map((image) => (
            <img
              alt={`${auction.item.title} collectible`}
              className="h-28 w-28 rounded-lg object-cover"
              key={image.id}
              src={image.url}
            />
          ))}
        </div>
      )}
      <dl className="mt-4 grid grid-cols-2 gap-3 text-sm">
        <div>
          <dt className="text-slate-400">State</dt>
          <dd>{stateLabel(auction.state)}</dd>
        </div>
        <div>
          <dt className="text-slate-400">Seller</dt>
          <dd>{auction.sellerHandle}</dd>
        </div>
        <div>
          <dt className="text-slate-400">Current amount</dt>
          <dd>{formatBrl(auction.currentAmountCents)}</dd>
        </div>
        <div>
          <dt className="text-slate-400">Opening</dt>
          <dd>{formatBrl(auction.openingAmountCents)}</dd>
        </div>
        <div>
          <dt className="text-slate-400">Increment</dt>
          <dd>{formatBrl(auction.minimumIncrementCents)}</dd>
        </div>
        <div>
          <dt className="text-slate-400">Condition notes</dt>
          <dd>{auction.item.conditionNotes}</dd>
        </div>
        <div>
          <dt className="text-slate-400">Ownership declared</dt>
          <dd>{auction.item.ownershipDeclared ? 'Yes' : 'No'}</dd>
        </div>
      </dl>
      <p className="mt-4 text-sm font-medium text-amber-200">
        {auction.reserveMet ? 'Reserve met' : 'Reserve not met'}
      </p>
      <p className="mt-2 text-sm text-slate-300">
        Starts {formatSaoPaulo(auction.startsAt)} · effective end{' '}
        {formatSaoPaulo(auction.effectiveEndAt)} (São Paulo)
      </p>
      <h4 className="mt-5 font-semibold text-white">Public timeline</h4>
      <ol className="mt-2 space-y-2 text-sm text-slate-300">
        {auction.timeline.map((event) => (
          <li key={`${event.type}-${event.occurredAt}`}>
            <span className="font-medium text-white">{stateLabel(event.type)}</span> ·{' '}
            {formatSaoPaulo(event.occurredAt)}
            {event.publicReason ? ` — ${event.publicReason}` : ''}
          </li>
        ))}
      </ol>
      <h4 className="mt-5 font-semibold text-white">Eligible bid history</h4>
      <p className="mt-2 text-sm text-slate-300">
        {auction.eligibleBidHistory.length === 0
          ? 'No eligible bids.'
          : `${auction.eligibleBidHistory.length} eligible bids.`}
      </p>
      <h4 className="mt-5 font-semibold text-white">Disqualifications</h4>
      <p className="mt-2 text-sm text-slate-300">
        {auction.disqualifications.length === 0
          ? 'No public disqualifications.'
          : `${auction.disqualifications.length} public disqualifications.`}
      </p>
    </article>
  );
}

function ProjectedCountdown({ auction }: { auction: Auction }) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  const deadline = auction.state === 'SCHEDULED' ? auction.startsAt : auction.effectiveEndAt;
  const remainingSeconds = Math.max(0, Math.ceil((new Date(deadline).getTime() - now) / 1000));
  return (
    <span className="shrink-0 rounded-md bg-slate-800 px-2 py-1 font-mono text-xs text-slate-200">
      {formatDuration(remainingSeconds)}
    </span>
  );
}

function deadlineLabel(auction: Auction): string {
  return auction.state === 'SCHEDULED'
    ? `starts ${formatSaoPaulo(auction.startsAt)}`
    : `ends ${formatSaoPaulo(auction.effectiveEndAt)}`;
}

function stateLabel(value: string): string {
  return value
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/^./, (letter) => letter.toUpperCase());
}

function formatDuration(seconds: number): string {
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainder = seconds % 60;
  return days > 0 ? `${days}d ${hours}h` : `${hours}h ${minutes}m ${remainder}s`;
}
