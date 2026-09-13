import { type FormEvent, useCallback, useEffect, useState } from 'react';

import {
  type Auction,
  administrativelyCancelAuction,
  listAuctions,
  listSuspendedAuctions,
  releaseSuspendedAuction,
  resumeSuspendedAuction,
  type SuspendedAuction,
  suspendAuction,
} from '../api/client';
import { Failure, FormHeading, Notice, SubmitButton, TextField } from '../shared/ui';

type Props = { administrator: boolean };

export function AuctionOperationsWorkspace({ administrator }: Props) {
  const [candidates, setCandidates] = useState<Auction[]>([]);
  const [queue, setQueue] = useState<SuspendedAuction[]>([]);
  const [auctionId, setAuctionId] = useState('');
  const [reasonCategory, setReasonCategory] = useState('POLICY_REVIEW');
  const [publicReason, setPublicReason] = useState('');
  const [internalNote, setInternalNote] = useState('');
  const [itemDisposition, setItemDisposition] = useState('RELEASE_APPROVED_ITEM');
  const [notice, setNotice] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [scheduled, live, suspended] = await Promise.all([
        listAuctions('SCHEDULED'),
        listAuctions('LIVE'),
        listSuspendedAuctions(),
      ]);
      setCandidates(
        [...scheduled.content, ...live.content].filter((auction) => auction.state !== 'SUSPENDED'),
      );
      setQueue(suspended);
      setFailure(null);
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'The auction queue could not be loaded.');
    }
  }, []);

  useEffect(() => void load(), [load]);

  async function submitSuspension(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (auctionId === '' || publicReason.trim() === '') {
      setFailure('Select an auction and enter a public reason.');
      return;
    }
    await run('Auction suspended.', () =>
      suspendAuction(auctionId, {
        reasonCategory,
        publicReason: publicReason.trim(),
        internalNote: internalNote.trim() || undefined,
      }),
    );
  }

  async function resolve(auction: SuspendedAuction, action: 'release' | 'resume' | 'cancel') {
    if (action === 'cancel' && publicReason.trim() === '') {
      setFailure('Enter the public cancellation reason before cancelling.');
      return;
    }
    await run(
      action === 'release'
        ? 'Auction released to Draft.'
        : action === 'resume'
          ? 'Auction resumed.'
          : 'Auction cancelled.',
      () => {
        if (action === 'release') return releaseSuspendedAuction(auction.id);
        if (action === 'resume') return resumeSuspendedAuction(auction.id);
        return administrativelyCancelAuction(auction.id, {
          reasonCategory,
          publicReason: publicReason.trim(),
          internalNote: internalNote.trim() || undefined,
          itemDisposition,
        });
      },
    );
  }

  async function run(message: string, command: () => Promise<unknown>) {
    setBusy(true);
    setFailure(null);
    setNotice(null);
    try {
      await command();
      setNotice(message);
      setAuctionId('');
      await load();
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'The auction operation failed.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section
      className="mt-8 border-t border-slate-700 pt-8"
      aria-labelledby="auction-operations-heading"
    >
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-2xl font-semibold text-white" id="auction-operations-heading">
            Auction intervention
          </h2>
          <p className="mt-1 max-w-2xl text-sm text-slate-400">
            Freeze scheduled starts or live closing time while an operational review is active.
          </p>
        </div>
        <p className="border-l-2 border-amber-300 pl-3 text-sm text-amber-100">
          {queue.length} frozen
        </p>
      </div>
      {notice !== null && <Notice>{notice}</Notice>}
      {failure !== null && <Failure>{failure}</Failure>}
      <div className="grid gap-6 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.4fr)]">
        <form
          className="space-y-4 rounded-xl border border-slate-700 bg-slate-950/60 p-5"
          onSubmit={submitSuspension}
        >
          <FormHeading
            title="Suspend an auction"
            subtitle="The public reason appears on the auction timeline. Internal notes stay restricted."
          />
          <label className="block text-sm font-medium text-slate-100" htmlFor="suspension-auction">
            Auction
          </label>
          <select
            className="-mt-2 block w-full rounded-lg border border-slate-600 bg-slate-950 px-3 py-2.5 text-slate-100"
            id="suspension-auction"
            onChange={(event) => setAuctionId(event.target.value)}
            value={auctionId}
          >
            <option value="">Select a scheduled or live auction</option>
            {candidates.map((auction) => (
              <option key={auction.id} value={auction.id}>
                {auction.item.title} — {auction.state}
              </option>
            ))}
          </select>
          <label className="block text-sm font-medium text-slate-100" htmlFor="suspension-category">
            Reason category
          </label>
          <select
            className="-mt-2 block w-full rounded-lg border border-slate-600 bg-slate-950 px-3 py-2.5 text-slate-100"
            id="suspension-category"
            onChange={(event) => setReasonCategory(event.target.value)}
            value={reasonCategory}
          >
            <option value="POLICY_REVIEW">Policy review</option>
            <option value="SECURITY">Security</option>
            <option value="ITEM_CONCERN">Item concern</option>
            <option value="OTHER">Other</option>
          </select>
          <TextField
            id="suspension-public-reason"
            label="Public reason"
            onChange={setPublicReason}
            value={publicReason}
          />
          <TextField
            id="suspension-internal-note"
            label="Internal note (optional)"
            onChange={setInternalNote}
            value={internalNote}
          />
          {administrator && (
            <>
              <label
                className="block text-sm font-medium text-slate-100"
                htmlFor="item-disposition"
              >
                Cancellation item disposition
              </label>
              <select
                className="-mt-2 block w-full rounded-lg border border-slate-600 bg-slate-950 px-3 py-2.5 text-slate-100"
                id="item-disposition"
                onChange={(event) => setItemDisposition(event.target.value)}
                value={itemDisposition}
              >
                <option value="RELEASE_APPROVED_ITEM">Release approved item</option>
                <option value="REVOKE_APPROVAL_TO_DRAFT">Revoke approval to Draft</option>
              </select>
            </>
          )}
          <SubmitButton disabled={busy}>{busy ? 'Suspending…' : 'Suspend auction'}</SubmitButton>
        </form>
        <div className="overflow-hidden rounded-xl border border-slate-700 bg-slate-950/60">
          <div className="grid grid-cols-[1fr_auto] border-b border-slate-700 px-5 py-3 text-sm text-slate-400">
            <span>Suspension queue</span>
            <span>Oldest first</span>
          </div>
          {queue.length === 0 ? (
            <p className="p-5 text-sm text-slate-400">No auctions are suspended.</p>
          ) : (
            <ul className="divide-y divide-slate-800">
              {queue.map((auction) => (
                <li className="p-5" key={auction.id}>
                  <div className="flex flex-wrap justify-between gap-3">
                    <div>
                      <h3 className="font-semibold text-white">{auction.itemTitle}</h3>
                      <p className="text-sm text-slate-400">
                        {auction.sourceState} · seller {auction.sellerHandle}
                      </p>
                    </div>
                    <p className="text-right text-sm font-semibold text-amber-200">
                      {formatRemaining(auction.remainingDurationMillis)}
                    </p>
                  </div>
                  <p className="mt-3 text-sm text-slate-300">
                    {auction.timeline.at(-1)?.publicReason}
                  </p>
                  {administrator ? (
                    <div className="mt-4 flex flex-wrap gap-2">
                      {auction.sourceState === 'SCHEDULED' ? (
                        <button
                          className="rounded-lg border border-cyan-600 px-3 py-2 text-sm font-semibold text-cyan-100"
                          disabled={busy}
                          onClick={() => void resolve(auction, 'release')}
                          type="button"
                        >
                          Release to Draft
                        </button>
                      ) : (
                        <button
                          className="rounded-lg border border-cyan-600 px-3 py-2 text-sm font-semibold text-cyan-100"
                          disabled={busy}
                          onClick={() => void resolve(auction, 'resume')}
                          type="button"
                        >
                          Resume with remaining time
                        </button>
                      )}
                      <button
                        className="rounded-lg border border-rose-700 px-3 py-2 text-sm font-semibold text-rose-200"
                        disabled={busy}
                        onClick={() => void resolve(auction, 'cancel')}
                        type="button"
                      >
                        Cancel auction
                      </button>
                    </div>
                  ) : (
                    <p className="mt-4 text-xs text-slate-500">
                      An administrator must release, resume, or cancel this auction.
                    </p>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </section>
  );
}

function formatRemaining(milliseconds: number | null | undefined): string {
  if (milliseconds == null) return 'Start blocked';
  const seconds = Math.floor(milliseconds / 1000);
  const minutes = Math.floor(seconds / 60);
  return `${minutes}m ${seconds % 60}s remaining`;
}
