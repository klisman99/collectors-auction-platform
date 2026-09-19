import { useEffect, useState } from 'react';

import {
  ApiError,
  confirmSaleDelivery,
  listMySales,
  recordSaleShipment,
  type Sale,
  simulateSalePayment,
} from '../api/client';
import { formatBrl, formatSaoPaulo } from '../auctions/presentation';
import { Failure } from '../shared/ui';

type ShipmentDraft = { carrier: string; trackingReference: string };

export function SettlementWorkspace() {
  const [sales, setSales] = useState<Sale[]>([]);
  const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState<string | null>(null);
  const [shipmentDrafts, setShipmentDrafts] = useState<Record<string, ShipmentDraft>>({});

  useEffect(() => {
    let active = true;
    listMySales()
      .then((result) => {
        if (active) setSales(result);
      })
      .catch(() => {
        if (active) setFailure('Your settlement sales could not be loaded. Try again.');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  function replace(updated: Sale) {
    setSales((current) => current.map((sale) => (sale.id === updated.id ? updated : sale)));
  }

  async function recordPayment(sale: Sale) {
    setSubmitting(sale.id);
    setFailure(null);
    setNotice(null);
    try {
      replace(await simulateSalePayment(sale.id));
      setNotice('Payment recorded. The seller can now record shipment.');
    } catch (error) {
      setFailure(actionFailure(error, 'Payment could not be recorded. Retry safely.'));
    } finally {
      setSubmitting(null);
    }
  }

  async function recordShipment(sale: Sale, event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const draft = shipmentDrafts[sale.id] ?? { carrier: '', trackingReference: '' };
    setSubmitting(sale.id);
    setFailure(null);
    setNotice(null);
    try {
      replace(await recordSaleShipment(sale.id, draft));
      setNotice('Shipment recorded. The buyer can now see the carrier and tracking reference.');
    } catch (error) {
      setFailure(actionFailure(error, 'Shipment could not be recorded. Retry safely.'));
    } finally {
      setSubmitting(null);
    }
  }

  async function confirmDelivery(sale: Sale) {
    setSubmitting(sale.id);
    setFailure(null);
    setNotice(null);
    try {
      replace(await confirmSaleDelivery(sale.id));
      setNotice('Delivery confirmed. The settlement is complete and the collectible is archived.');
    } catch (error) {
      setFailure(
        actionFailure(error, 'Delivery confirmation could not be recorded. Retry safely.'),
      );
    } finally {
      setSubmitting(null);
    }
  }

  return (
    <section className="mt-10 border-t border-slate-700 pt-8" aria-labelledby="settlement-heading">
      <h2 className="text-2xl font-semibold text-white" id="settlement-heading">
        Settlement desk
      </h2>
      <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-300">
        Follow the simulated payment and shipment handoff for your sold collectibles. Participant
        handles are visible here; addresses and real payment details are never requested.
      </p>
      {failure !== null && <Failure>{failure}</Failure>}
      {notice !== null && (
        <p className="mt-4 text-sm text-emerald-200" role="status">
          {notice}
        </p>
      )}
      {loading && <p className="mt-5 text-sm text-slate-300">Loading your settlements…</p>}
      {!loading && failure === null && sales.length === 0 && (
        <p className="mt-5 rounded-xl border border-slate-700 bg-slate-950/50 p-4 text-sm text-slate-300">
          No settlement actions are waiting for you.
        </p>
      )}
      <div className="mt-5 grid gap-4">
        {sales.map((sale) => {
          const draft = shipmentDrafts[sale.id] ?? { carrier: '', trackingReference: '' };
          const pending = submitting === sale.id;
          return (
            <article
              className="overflow-hidden rounded-xl border border-slate-700 bg-slate-950/60"
              key={sale.id}
            >
              <div className="border-b border-slate-700 bg-slate-900/70 px-5 py-4 sm:flex sm:items-start sm:justify-between sm:gap-4">
                <div>
                  <h3 className="text-lg font-semibold text-white">{sale.item.title}</h3>
                  <p className="mt-1 text-sm text-slate-300">
                    {formatBrl(sale.amountCents)} · You are the {sale.participantRole.toLowerCase()}
                  </p>
                </div>
                <p className={`mt-2 text-sm font-semibold sm:mt-0 ${stateTone(sale.state)}`}>
                  {stateLabel(sale.state)}
                </p>
              </div>
              <div className="grid gap-5 p-5 lg:grid-cols-[minmax(0,1fr)_minmax(16rem,0.8fr)]">
                <dl className="grid gap-3 text-sm sm:grid-cols-2">
                  <div>
                    <dt className="text-slate-400">Buyer</dt>
                    <dd className="mt-1 font-medium text-white">{sale.buyer.handle}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-400">Seller</dt>
                    <dd className="mt-1 font-medium text-white">{sale.seller.handle}</dd>
                  </div>
                  <div>
                    <dt className="text-slate-400">Payment deadline</dt>
                    <dd className="mt-1 text-slate-200">
                      {formatSaoPaulo(sale.paymentDeadlineAt)}
                    </dd>
                  </div>
                  {sale.shipmentDeadlineAt !== undefined && (
                    <div>
                      <dt className="text-slate-400">Shipment deadline</dt>
                      <dd className="mt-1 text-slate-200">
                        {formatSaoPaulo(sale.shipmentDeadlineAt)}
                      </dd>
                    </div>
                  )}
                  {sale.deliveryConfirmationDeadlineAt !== undefined && (
                    <div>
                      <dt className="text-slate-400">Delivery confirmation deadline</dt>
                      <dd className="mt-1 text-slate-200">
                        {formatSaoPaulo(sale.deliveryConfirmationDeadlineAt)}
                      </dd>
                    </div>
                  )}
                  {sale.carrier !== undefined && (
                    <div>
                      <dt className="text-slate-400">Carrier</dt>
                      <dd className="mt-1 text-slate-200">{sale.carrier}</dd>
                    </div>
                  )}
                  {sale.trackingReference !== undefined && (
                    <div>
                      <dt className="text-slate-400">Tracking reference</dt>
                      <dd className="mt-1 text-slate-200">{sale.trackingReference}</dd>
                    </div>
                  )}
                  {sale.terminalReason !== undefined && (
                    <div>
                      <dt className="text-slate-400">Terminal reason</dt>
                      <dd className="mt-1 text-slate-200">
                        {terminalReasonLabel(sale.terminalReason)}
                      </dd>
                    </div>
                  )}
                  {sale.itemDisposition !== undefined && (
                    <div>
                      <dt className="text-slate-400">Item disposition</dt>
                      <dd className="mt-1 text-slate-200">
                        {itemDispositionLabel(sale.itemDisposition)}
                      </dd>
                    </div>
                  )}
                </dl>
                <div className="border-t border-slate-700 pt-5 lg:border-t-0 lg:border-l lg:pl-5 lg:pt-0">
                  {sale.state === 'PAYMENT_PENDING' && sale.participantRole === 'BUYER' && (
                    <>
                      <p className="text-sm leading-6 text-slate-300">
                        Payment is simulated only. Record it before the payment deadline.
                      </p>
                      <button
                        className="mt-4 rounded-lg bg-cyan-300 px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:cursor-wait disabled:opacity-60"
                        disabled={pending}
                        onClick={() => recordPayment(sale)}
                        type="button"
                      >
                        {pending ? 'Recording payment…' : 'Simulate payment'}
                      </button>
                    </>
                  )}
                  {sale.state === 'PAYMENT_PENDING' && sale.participantRole === 'SELLER' && (
                    <p className="text-sm leading-6 text-slate-300">
                      Waiting for {sale.buyer.handle} to simulate payment before you record
                      shipment.
                    </p>
                  )}
                  {sale.state === 'SHIPMENT_PENDING' && sale.participantRole === 'BUYER' && (
                    <p className="text-sm leading-6 text-slate-300">
                      Payment is recorded. Waiting for {sale.seller.handle} to record the carrier
                      and tracking reference.
                    </p>
                  )}
                  {sale.state === 'SHIPMENT_PENDING' && sale.participantRole === 'SELLER' && (
                    <form className="grid gap-3" onSubmit={(event) => recordShipment(sale, event)}>
                      <p className="text-sm leading-6 text-slate-300">
                        Record simulated shipment before the shipment deadline.
                      </p>
                      <label className="grid gap-1 text-sm text-slate-200">
                        Carrier
                        <input
                          className="rounded-md border border-slate-600 bg-slate-950 px-3 py-2 text-white"
                          disabled={pending}
                          maxLength={120}
                          onChange={(event) =>
                            setShipmentDrafts((current) => ({
                              ...current,
                              [sale.id]: { ...draft, carrier: event.target.value },
                            }))
                          }
                          required
                          value={draft.carrier}
                        />
                      </label>
                      <label className="grid gap-1 text-sm text-slate-200">
                        Tracking reference
                        <input
                          className="rounded-md border border-slate-600 bg-slate-950 px-3 py-2 text-white"
                          disabled={pending}
                          maxLength={160}
                          onChange={(event) =>
                            setShipmentDrafts((current) => ({
                              ...current,
                              [sale.id]: { ...draft, trackingReference: event.target.value },
                            }))
                          }
                          required
                          value={draft.trackingReference}
                        />
                      </label>
                      <button
                        className="rounded-lg bg-cyan-300 px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:cursor-wait disabled:opacity-60"
                        disabled={pending}
                        type="submit"
                      >
                        {pending ? 'Recording shipment…' : 'Record shipment'}
                      </button>
                    </form>
                  )}
                  {sale.state === 'SHIPPED' && sale.participantRole === 'BUYER' && (
                    <>
                      <p className="text-sm leading-6 text-emerald-100">
                        Shipment is recorded. Confirm delivery before the deadline to complete the
                        settlement now. Without confirmation, it completes automatically.
                      </p>
                      <button
                        className="mt-4 rounded-lg bg-cyan-300 px-4 py-2.5 text-sm font-semibold text-slate-950 disabled:cursor-wait disabled:opacity-60"
                        disabled={pending}
                        onClick={() => confirmDelivery(sale)}
                        type="button"
                      >
                        {pending ? 'Confirming delivery…' : 'Confirm delivery'}
                      </button>
                    </>
                  )}
                  {sale.state === 'SHIPPED' && sale.participantRole === 'SELLER' && (
                    <p className="text-sm leading-6 text-emerald-100">
                      Shipment is recorded. The buyer may confirm delivery before the deadline;
                      otherwise the settlement completes automatically.
                    </p>
                  )}
                  {sale.state === 'COMPLETED' && (
                    <p className="text-sm leading-6 text-emerald-100">
                      This settlement is complete. The collectible is archived and cannot be
                      relisted.
                    </p>
                  )}
                  {sale.state === 'FAILED' && (
                    <p className="text-sm leading-6 text-rose-200">
                      This settlement failed before its required action was recorded. The unchanged
                      approved item is eligible for relisting; no further settlement action is
                      available.
                    </p>
                  )}
                </div>
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}

function actionFailure(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.message;
  return fallback;
}

function stateLabel(state: Sale['state']): string {
  return state
    .replaceAll('_', ' ')
    .toLowerCase()
    .replace(/^./, (letter) => letter.toUpperCase());
}

function stateTone(state: Sale['state']): string {
  return state === 'FAILED'
    ? 'text-rose-300'
    : state === 'SHIPPED' || state === 'COMPLETED'
      ? 'text-emerald-300'
      : 'text-amber-300';
}

function terminalReasonLabel(reason: NonNullable<Sale['terminalReason']>): string {
  return {
    PAYMENT_DEADLINE_EXPIRED: 'Payment deadline expired',
    SHIPMENT_DEADLINE_EXPIRED: 'Shipment deadline expired',
    BUYER_CONFIRMED_DELIVERY: 'Buyer confirmed delivery',
    DELIVERY_CONFIRMATION_DEADLINE_EXPIRED: 'Delivery confirmation deadline expired',
  }[reason];
}

function itemDispositionLabel(disposition: NonNullable<Sale['itemDisposition']>): string {
  return disposition === 'ARCHIVED' ? 'Archived — cannot be relisted' : 'Eligible for relisting';
}
