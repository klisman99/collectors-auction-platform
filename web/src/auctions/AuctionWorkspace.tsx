import { type FormEvent, useState } from 'react';

import { type Auction, type Draft, scheduleAuction, updateAuctionTerms } from '../api/client';

export function AuctionWorkspace({ approvedItems }: { approvedItems: Draft[] }) {
  const [selected, setSelected] = useState<Draft | null>(null);
  const [published, setPublished] = useState<Auction | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [terms, setTerms] = useState({
    opening: '',
    increment: '',
    reserve: '',
    startsAt: '',
    endsAt: '',
  });

  async function publish(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (selected === null) return;
    setFailure(null);
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
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'The auction could not be published.');
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
function formatBrl(cents: number): string {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(cents / 100);
}
function formatSaoPaulo(value: string): string {
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(new Date(value));
}
