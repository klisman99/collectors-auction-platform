import { type FormEvent, useEffect, useState } from 'react';

import {
  type Auction,
  cancelAuction,
  type Draft,
  decideBelowReserveOffer,
  listMyScheduledAuctions,
  scheduleAuction,
  updateAuctionTerms,
} from '../api/client';
import { formatBrl, formatSaoPaulo } from './presentation';

export function AuctionWorkspace({ approvedItems }: { approvedItems: Draft[] }) {
  const [selected, setSelected] = useState<Draft | null>(null);
  const [published, setPublished] = useState<Auction | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [cancellationReason, setCancellationReason] = useState('');
  const [scheduled, setScheduled] = useState<Auction[]>([]);
  const [scheduledLoading, setScheduledLoading] = useState(true);
  const [scheduledFailure, setScheduledFailure] = useState(false);
  const [decisionSubmitting, setDecisionSubmitting] = useState(false);
  const [terms, setTerms] = useState({
    opening: '',
    increment: '',
    reserve: '',
    startsAt: '',
    endsAt: '',
  });

  useEffect(() => {
    listMyScheduledAuctions()
      .then(setScheduled)
      .catch(() => setScheduledFailure(true))
      .finally(() => setScheduledLoading(false));
  }, []);

  async function publish(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (selected === null) return;
    setFailure(null);
    setNotice(null);
    try {
      const editable = {
        reserveAmountCents: optionalCents(terms.reserve),
        startsAt: saoPauloLocalToUtc(terms.startsAt),
        endsAt: saoPauloLocalToUtc(terms.endsAt),
      };
      const auction =
        published === null
          ? await scheduleAuction({
              itemId: selected.id,
              openingAmountCents: toCents(terms.opening),
              minimumIncrementCents: toCents(terms.increment),
              ...editable,
            })
          : await updateAuctionTerms(published.id, editable);
      setPublished(auction);
      setScheduled((current) => [auction, ...current.filter((item) => item.id !== auction.id)]);
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'The auction could not be published.');
    }
  }

  function manageAuction(auction: Auction) {
    const source = approvedItems.find((item) => item.id === auction.itemId);
    setSelected(
      source ?? {
        id: auction.itemId,
        category: auction.item.category as Draft['category'],
        otherCategoryLabel: auction.item.otherCategoryLabel,
        title: auction.item.title,
        description: auction.item.description,
        condition: auction.item.condition as Draft['condition'],
        conditionNotes: auction.item.conditionNotes,
        ownershipDeclared: auction.item.ownershipDeclared,
        status: 'APPROVED',
        images: auction.item.media.map((image) => ({
          ...image,
          thumbnailUrl: image.url,
        })),
      },
    );
    setPublished(auction);
    setTerms({
      opening: String(auction.openingAmountCents / 100),
      increment: String(auction.minimumIncrementCents / 100),
      reserve:
        auction.reserveAmountCents === undefined ? '' : String(auction.reserveAmountCents / 100),
      startsAt: utcToSaoPauloInput(auction.startsAt),
      endsAt: utcToSaoPauloInput(auction.endsAt),
    });
    setNotice(null);
    setFailure(null);
  }

  async function cancelPublishedAuction(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (published === null) return;
    setFailure(null);
    setNotice(null);
    try {
      setPublished(await cancelAuction(published.id, cancellationReason.trim()));
      setScheduled(scheduled.filter((auction) => auction.id !== published.id));
      setNotice('Auction cancelled.');
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'The auction could not be cancelled.');
    }
  }

  async function decideFinalOffer(decision: 'ACCEPT' | 'REJECT') {
    if (published === null) return;
    setDecisionSubmitting(true);
    setFailure(null);
    setNotice(null);
    try {
      const decided = await decideBelowReserveOffer(published.id, decision);
      setPublished(decided);
      setScheduled((current) => current.filter((auction) => auction.id !== decided.id));
      setNotice(
        decided.state === 'SOLD'
          ? 'Below-reserve offer accepted. A sale was created.'
          : 'Below-reserve offer rejected. The unchanged item is available again.',
      );
    } catch (error) {
      setFailure(
        error instanceof Error ? error.message : 'The seller decision could not be saved.',
      );
    } finally {
      setDecisionSubmitting(false);
    }
  }

  return (
    <section
      className="mt-8 border-t border-slate-700 pt-7"
      aria-labelledby="auction-workspace-heading"
    >
      <h2 className="text-xl font-semibold text-white" id="auction-workspace-heading">
        Schedule an approved collectible
      </h2>
      <p className="mt-2 text-sm leading-6 text-slate-300">
        Publish an English ascending auction in BRL. Times below use São Paulo time.
      </p>
      {scheduledLoading && <p className="mt-4 text-sm text-slate-400">Loading your auctions…</p>}
      {scheduledFailure && (
        <p className="mt-4 text-sm text-rose-300" role="alert">
          Your scheduled auctions could not be loaded.
        </p>
      )}
      {approvedItems.length === 0 && (
        <p className="mt-4 text-sm text-slate-400">
          An approved item will appear here when moderation is complete.
        </p>
      )}
      <div className="mt-4 flex flex-wrap gap-2">
        {approvedItems.map((item) => (
          <button
            aria-label={`Schedule auction for ${item.title}`}
            className="rounded-full border border-cyan-700 px-3 py-1.5 text-sm text-cyan-100 focus:outline-none focus:ring-2 focus:ring-cyan-300"
            key={item.id}
            onClick={() => {
              setSelected(item);
              setPublished(null);
            }}
            type="button"
          >
            {item.title}
          </button>
        ))}
      </div>
      {scheduled.length > 0 && (
        <div className="mt-5">
          <p className="text-sm font-semibold text-amber-300">
            Your editable auctions and decisions
          </p>
          <div className="mt-2 flex flex-wrap gap-2">
            {scheduled.map((auction) => (
              <button
                className="rounded-full border border-amber-700 px-3 py-1.5 text-sm text-amber-100"
                key={auction.id}
                onClick={() => manageAuction(auction)}
                type="button"
              >
                Manage {auction.item.title}
              </button>
            ))}
          </div>
        </div>
      )}
      {selected !== null && (
        <div className="mt-5 overflow-hidden rounded-xl border border-slate-600 bg-slate-900">
          <div className="grid sm:grid-cols-[1.05fr_1fr]">
            <div className="border-b border-emerald-800/80 bg-emerald-950/25 p-5 sm:border-r sm:border-b-0">
              <p className="text-sm font-semibold text-emerald-300">Locked after publication</p>
              <h3 className="mt-3 text-2xl font-semibold text-white">
                {published?.item.title ?? selected.title}
              </h3>
              <p className="mt-2 text-sm leading-6 text-slate-300">
                {published?.item.description ?? selected.description}
              </p>
              <p className="mt-3 text-xs tracking-wide text-slate-400 uppercase">
                {published?.item.category ?? selected.category} ·{' '}
                {published?.item.condition ?? selected.condition}
              </p>
              <p className="mt-2 text-sm text-slate-300">
                Condition notes: {published?.item.conditionNotes ?? selected.conditionNotes}
              </p>
              <p className="mt-2 text-sm text-slate-300">
                Ownership declared:{' '}
                {(published?.item.ownershipDeclared ?? selected.ownershipDeclared) ? 'Yes' : 'No'}
              </p>
              <div className="mt-4 flex gap-2 overflow-x-auto">
                {(published?.item.media ?? selected.images).map((image) => (
                  <img
                    alt={`${published?.item.title ?? selected.title} snapshot`}
                    className="h-20 w-20 rounded-lg border border-emerald-800 object-cover"
                    key={image.id}
                    src={image.url}
                  />
                ))}
              </div>
              <dl className="mt-5 grid grid-cols-2 gap-3 text-sm">
                <div>
                  <dt className="text-slate-400">Opening</dt>
                  <dd className="mt-1 font-semibold text-white">
                    {published ? formatBrl(published.openingAmountCents) : 'Set before publishing'}
                  </dd>
                </div>
                <div>
                  <dt className="text-slate-400">Increment</dt>
                  <dd className="mt-1 font-semibold text-white">
                    {published
                      ? formatBrl(published.minimumIncrementCents)
                      : 'Set before publishing'}
                  </dd>
                </div>
              </dl>
            </div>
            {(published === null ||
              published.state === 'DRAFT' ||
              published.state === 'SCHEDULED') && (
              <form className="grid gap-3 bg-amber-950/15 p-5" onSubmit={publish}>
                <p className="text-sm font-semibold text-amber-300">Editable before start</p>
                {published === null && (
                  <>
                    <MoneyInput
                      label="Opening amount"
                      value={terms.opening}
                      onChange={(opening) => setTerms({ ...terms, opening })}
                    />
                    <MoneyInput
                      label="Minimum increment"
                      value={terms.increment}
                      onChange={(increment) => setTerms({ ...terms, increment })}
                    />
                  </>
                )}
                <MoneyInput
                  label="Optional reserve"
                  required={false}
                  value={terms.reserve}
                  onChange={(reserve) => setTerms({ ...terms, reserve })}
                />
                <label className="grid gap-1 text-sm text-slate-300">
                  Start in São Paulo
                  <input
                    aria-label="Start in São Paulo"
                    className="rounded-lg border border-slate-600 bg-slate-950 px-3 py-2 text-white"
                    required
                    type="datetime-local"
                    value={terms.startsAt}
                    onChange={(event) => setTerms({ ...terms, startsAt: event.target.value })}
                  />
                </label>
                <label className="grid gap-1 text-sm text-slate-300">
                  End in São Paulo
                  <input
                    aria-label="End in São Paulo"
                    className="rounded-lg border border-slate-600 bg-slate-950 px-3 py-2 text-white"
                    required
                    type="datetime-local"
                    value={terms.endsAt}
                    onChange={(event) => setTerms({ ...terms, endsAt: event.target.value })}
                  />
                </label>
                {failure !== null && (
                  <p className="text-sm text-rose-300" role="alert">
                    {failure}
                  </p>
                )}
                <button
                  className="mt-1 rounded-lg bg-amber-300 px-4 py-2.5 font-semibold text-slate-950 focus:outline-none focus:ring-2 focus:ring-amber-100"
                  type="submit"
                >
                  {published === null ? 'Publish auction' : 'Save editable terms'}
                </button>
              </form>
            )}
            {published !== null &&
              published.state !== 'DRAFT' &&
              published.state !== 'SCHEDULED' && (
                <div className="bg-slate-950/60 p-5">
                  <p className="text-sm font-semibold text-slate-200">
                    Auction {published.state.toLowerCase().replaceAll('_', ' ')}.
                  </p>
                  {published.state === 'AWAITING_SELLER_DECISION' && (
                    <section className="mt-4 rounded-lg border border-amber-700/70 bg-amber-950/20 p-4">
                      <h3 className="font-semibold text-amber-100">Below-reserve final offer</h3>
                      <p className="mt-2 text-sm leading-6 text-slate-300">
                        The highest eligible offer is{' '}
                        {published.finalOutcome?.amountCents === undefined
                          ? 'being refreshed'
                          : formatBrl(published.finalOutcome.amountCents)}
                        . The bidder remains pseudonymous until you accept and a sale is created.
                      </p>
                      {published.sellerDecisionDeadlineAt !== undefined && (
                        <p className="mt-2 text-sm text-amber-200">
                          Decide by {formatSaoPaulo(published.sellerDecisionDeadlineAt)}.
                        </p>
                      )}
                      <div className="mt-4 flex flex-wrap gap-3">
                        <button
                          className="rounded-lg bg-emerald-300 px-4 py-2 font-semibold text-slate-950 disabled:cursor-wait disabled:opacity-70"
                          disabled={decisionSubmitting}
                          onClick={() => decideFinalOffer('ACCEPT')}
                          type="button"
                        >
                          {decisionSubmitting ? 'Saving decision…' : 'Accept final offer'}
                        </button>
                        <button
                          className="rounded-lg border border-rose-600 px-4 py-2 font-semibold text-rose-100 disabled:cursor-wait disabled:opacity-70"
                          disabled={decisionSubmitting}
                          onClick={() => decideFinalOffer('REJECT')}
                          type="button"
                        >
                          Reject final offer
                        </button>
                      </div>
                    </section>
                  )}
                  {published.state === 'SOLD' &&
                    published.finalOutcome?.bidderHandle !== undefined && (
                      <p className="mt-4 text-sm text-emerald-200">
                        Buyer: {published.finalOutcome.bidderHandle}
                      </p>
                    )}
                  {failure !== null && (
                    <p className="mt-4 text-sm text-rose-300" role="alert">
                      {failure}
                    </p>
                  )}
                </div>
              )}
          </div>
          {published !== null && (
            <div className="border-t border-slate-700 px-5 py-4">
              <h3 className="text-lg font-semibold text-white">Published snapshot</h3>
              <p className="mt-1 text-sm text-slate-300">
                Starts {formatSaoPaulo(published.startsAt)} · ends{' '}
                {formatSaoPaulo(published.endsAt)}.
              </p>
              <p className="mt-2 text-xs text-slate-400">
                {published.policy.auctionType.replaceAll('_', ' ')} · {published.policy.currency} ·
                amounts {formatBrl(published.policy.minimumAmountCents)}–
                {formatBrl(published.policy.maximumAmountCents)} · lead{' '}
                {published.policy.minimumLeadSeconds / 60} min · duration{' '}
                {published.policy.minimumDurationSeconds / 60} min–
                {published.policy.maximumDurationSeconds / 86400} days · bid protection{' '}
                {published.policy.protectionWindowSeconds / 60} min
              </p>
              {published.state === 'SCHEDULED' && (
                <form
                  className="mt-5 grid gap-3 border-t border-slate-700 pt-4"
                  onSubmit={cancelPublishedAuction}
                >
                  <label className="grid gap-1 text-sm text-slate-300">
                    Public cancellation reason
                    <textarea
                      aria-label="Public cancellation reason"
                      className="rounded-lg border border-slate-600 bg-slate-950 px-3 py-2 text-white"
                      maxLength={500}
                      minLength={1}
                      onChange={(event) => setCancellationReason(event.target.value)}
                      required
                      value={cancellationReason}
                    />
                  </label>
                  <button
                    className="justify-self-start rounded-lg border border-rose-600 px-4 py-2 text-sm font-semibold text-rose-200"
                    type="submit"
                  >
                    Cancel auction
                  </button>
                </form>
              )}
              {notice !== null && (
                <p className="mt-4 text-sm text-emerald-300" role="status">
                  {notice}
                </p>
              )}
            </div>
          )}
        </div>
      )}
    </section>
  );
}

function MoneyInput({
  label,
  onChange,
  required = true,
  value,
}: {
  label: string;
  onChange: (value: string) => void;
  required?: boolean;
  value: string;
}) {
  return (
    <label className="grid gap-1 text-sm text-slate-300">
      {label}
      <input
        aria-label={label}
        className="rounded-lg border border-slate-600 bg-slate-950 px-3 py-2 text-white"
        max="1000000"
        min="10"
        onChange={(event) => onChange(event.target.value)}
        required={required}
        step="0.01"
        type="number"
        value={value}
      />
    </label>
  );
}

function toCents(value: string): number {
  return Math.round(Number(value) * 100);
}
function optionalCents(value: string): number | undefined {
  return value.trim() === '' ? undefined : toCents(value);
}
function saoPauloLocalToUtc(value: string): string {
  return new Date(`${value}:00-03:00`).toISOString();
}
function utcToSaoPauloInput(value: string): string {
  return new Intl.DateTimeFormat('sv-SE', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone: 'America/Sao_Paulo',
  })
    .format(new Date(value))
    .replace(' ', 'T');
}
