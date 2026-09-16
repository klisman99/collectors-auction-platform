import { type FormEvent, useCallback, useEffect, useState } from 'react';

import {
  getRegularAccounts,
  type RegularAccountView,
  reactivateRegularAccount,
  suspendRegularAccount,
} from '../api/client';
import { Failure, FormHeading, Notice, SubmitButton, TextField } from '../shared/ui';

export function RegularAccountAdministrationWorkspace() {
  const [accounts, setAccounts] = useState<RegularAccountView[]>([]);
  const [accountId, setAccountId] = useState('');
  const [action, setAction] = useState<'SUSPEND' | 'REACTIVATE'>('SUSPEND');
  const [reasonCategory, setReasonCategory] = useState<
    'SECURITY' | 'POLICY_VIOLATION' | 'FRAUD' | 'OTHER'
  >('SECURITY');
  const [publicReason, setPublicReason] = useState('');
  const [internalNote, setInternalNote] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setAccounts(await getRegularAccounts());
      setFailure(null);
    } catch (error) {
      setFailure(error instanceof Error ? error.message : 'Regular accounts could not be loaded.');
    }
  }, []);

  useEffect(() => void load(), [load]);

  async function submitAccountAction(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (accountId === '' || publicReason.trim() === '') {
      setFailure('Select an account and enter a public reason.');
      return;
    }
    setBusy(true);
    setFailure(null);
    setNotice(null);
    try {
      const reason = {
        reasonCategory,
        publicReason: publicReason.trim(),
        internalNote: internalNote.trim() || undefined,
      };
      if (action === 'SUSPEND') {
        await suspendRegularAccount(accountId, reason);
      } else {
        await reactivateRegularAccount(accountId, reason);
      }
      setAccountId('');
      setPublicReason('');
      setInternalNote('');
      setAction('SUSPEND');
      setNotice(
        action === 'SUSPEND'
          ? 'The account was suspended. Its sessions, seller auctions, and eligible bids were updated.'
          : 'The account was reactivated. Its disqualified bids remain permanent.',
      );
      await load();
    } catch (error) {
      setFailure(
        error instanceof Error
          ? error.message
          : action === 'SUSPEND'
            ? 'The account could not be suspended.'
            : 'The account could not be reactivated.',
      );
    } finally {
      setBusy(false);
    }
  }

  function prepareReactivation(account: RegularAccountView) {
    setAction('REACTIVATE');
    setAccountId(account.id);
    setFailure(null);
    setNotice(null);
  }

  const actionableAccounts = accounts.filter((account) =>
    action === 'SUSPEND' ? account.status === 'ACTIVE' : account.status === 'SUSPENDED',
  );

  return (
    <section
      className="mt-8 border-t border-slate-700 pt-8"
      aria-labelledby="regular-accounts-heading"
    >
      <div>
        <h2 className="text-2xl font-semibold text-white" id="regular-accounts-heading">
          Regular account restrictions
        </h2>
        <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-300">
          Suspension signs out every current session, freezes the seller&apos;s scheduled and live
          auctions, and permanently disqualifies the account&apos;s accepted bids in non-terminal
          auctions.
        </p>
      </div>
      {notice !== null && <Notice>{notice}</Notice>}
      {failure !== null && <Failure>{failure}</Failure>}
      <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.4fr)]">
        <form
          className="space-y-4 rounded-xl border border-slate-700 bg-slate-950/60 p-5"
          onSubmit={submitAccountAction}
        >
          <FormHeading
            title={
              action === 'SUSPEND' ? 'Suspend a regular account' : 'Reactivate a regular account'
            }
            subtitle={
              action === 'SUSPEND'
                ? 'The public reason is visible where the restriction affects an auction. The internal note is restricted to administrators.'
                : 'Reactivation restores access only. It never restores a permanently disqualified bid.'
            }
          />
          <label className="block text-sm font-medium text-slate-100" htmlFor="regular-account">
            Regular account
          </label>
          <select
            className="-mt-2 block w-full rounded-lg border border-slate-600 bg-slate-950 px-3 py-2.5 text-slate-100"
            id="regular-account"
            onChange={(event) => setAccountId(event.target.value)}
            value={accountId}
          >
            <option value="">
              {action === 'SUSPEND' ? 'Select an active account' : 'Select a suspended account'}
            </option>
            {actionableAccounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.email} · {account.publicHandle}
              </option>
            ))}
          </select>
          <label
            className="block text-sm font-medium text-slate-100"
            htmlFor="regular-account-reason-category"
          >
            Reason category
          </label>
          <select
            className="-mt-2 block w-full rounded-lg border border-slate-600 bg-slate-950 px-3 py-2.5 text-slate-100"
            id="regular-account-reason-category"
            onChange={(event) =>
              setReasonCategory(
                event.target.value as 'SECURITY' | 'POLICY_VIOLATION' | 'FRAUD' | 'OTHER',
              )
            }
            value={reasonCategory}
          >
            <option value="SECURITY">Security</option>
            <option value="POLICY_VIOLATION">Policy violation</option>
            <option value="FRAUD">Fraud</option>
            <option value="OTHER">Other</option>
          </select>
          <TextField
            id="regular-account-public-reason"
            label="Public reason"
            onChange={setPublicReason}
            value={publicReason}
          />
          <TextField
            id="regular-account-internal-note"
            label="Internal note"
            onChange={setInternalNote}
            value={internalNote}
          />
          <SubmitButton disabled={busy}>
            {busy
              ? action === 'SUSPEND'
                ? 'Suspending…'
                : 'Reactivating…'
              : `${action === 'SUSPEND' ? 'Suspend' : 'Reactivate'} account`}
          </SubmitButton>
        </form>
        <div className="overflow-hidden rounded-xl border border-slate-700 bg-slate-950/60">
          <div className="border-b border-slate-700 p-5">
            <h3 className="text-xl font-semibold text-white">Account status</h3>
            <p className="mt-1 text-sm text-slate-400">
              Reactivation restores account access, never a disqualified bid.
            </p>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full text-left text-sm">
              <thead className="bg-slate-900 text-slate-300">
                <tr>
                  <th className="px-5 py-3 font-medium">Account</th>
                  <th className="px-5 py-3 font-medium">Status</th>
                  <th className="px-5 py-3 font-medium">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {accounts.map((account) => (
                  <tr key={account.id}>
                    <td className="px-5 py-3 text-slate-100">
                      <div>{account.email}</div>
                      <div className="text-slate-400">{account.publicHandle}</div>
                    </td>
                    <td className="px-5 py-3 text-slate-300">{account.status}</td>
                    <td className="px-5 py-3">
                      {account.status === 'SUSPENDED' && (
                        <button
                          className="rounded-md border border-emerald-700 px-3 py-1.5 text-emerald-200"
                          disabled={busy}
                          onClick={() => prepareReactivation(account)}
                          type="button"
                        >
                          Reactivate {account.publicHandle}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </section>
  );
}
