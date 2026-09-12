import { type FormEvent, useCallback, useEffect, useState } from 'react';

import {
  type AuditRecord,
  type AuthenticatedSession,
  deactivateOperationalAccount,
  getAdministrativeAuditRecords,
  getOperationalAccounts,
  inviteOperationalAccount,
  type OperationalAccountView,
} from '../api/client';
import { ModerationWorkspace } from '../moderation/ModerationWorkspace';
import { applyApiError, type FieldErrors, validEmail } from '../shared/forms';
import { Failure, FormHeading, Notice, PageFrame, SubmitButton, TextField } from '../shared/ui';
import { AuctionOperationsWorkspace } from './AuctionOperationsWorkspace';

export function OperationalModeratorHome({
  onRevokeAllSessions,
  onSignOut,
  session,
}: {
  onRevokeAllSessions: () => void;
  onSignOut: () => void;
  session: AuthenticatedSession;
}) {
  return (
    <PageFrame>
      <p className="text-sm font-semibold tracking-[0.2em] text-cyan-300 uppercase">
        Operations workspace
      </p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white sm:text-4xl">
        Moderator access is active.
      </h1>
      <p className="mt-4 leading-7 text-slate-300">
        This dedicated moderator identity can review collectible submissions. It cannot sell, bid,
        invite operational accounts, or view administrator audit records.
      </p>
      <p className="mt-4 text-sm text-slate-400">
        Signed in as {session.role === 'MODERATOR' ? 'Moderator' : 'Operational user'}.
      </p>
      <div className="mt-6 flex flex-col gap-3 sm:flex-row">
        <button
          className="rounded-lg border border-slate-600 px-4 py-2.5 font-semibold text-slate-100 transition hover:border-cyan-300 hover:text-cyan-200"
          onClick={onSignOut}
          type="button"
        >
          Sign out
        </button>
        <button
          className="rounded-lg border border-rose-700/70 px-4 py-2.5 font-semibold text-rose-200 transition hover:border-rose-300 hover:text-rose-100"
          onClick={onRevokeAllSessions}
          type="button"
        >
          Sign out all sessions
        </button>
      </div>
      <ModerationWorkspace />
      <AuctionOperationsWorkspace administrator={false} />
    </PageFrame>
  );
}

export function OperationalHome({
  failure,
  notice,
  onRevokeAllSessions,
  onSignOut,
  session,
}: {
  failure: string | null;
  notice: string | null;
  onRevokeAllSessions: () => void;
  onSignOut: () => void;
  session: AuthenticatedSession;
}) {
  const [accounts, setAccounts] = useState<OperationalAccountView[]>([]);
  const [auditRecords, setAuditRecords] = useState<AuditRecord[]>([]);
  const [invite, setInvite] = useState({
    email: '',
    role: 'MODERATOR',
    reasonCategory: 'STAFFING',
    publicReason: '',
    internalNote: '',
  });
  const [deactivation, setDeactivation] = useState({
    accountId: '',
    reasonCategory: 'SECURITY',
    publicReason: '',
    internalNote: '',
  });
  const [formErrors, setFormErrors] = useState<FieldErrors>({});
  const [failureMessage, setFailureMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const loadConsole = useCallback(async () => {
    try {
      const [loadedAccounts, loadedAuditRecords] = await Promise.all([
        getOperationalAccounts(),
        getAdministrativeAuditRecords(),
      ]);
      setAccounts(loadedAccounts);
      setAuditRecords(loadedAuditRecords);
      setFailureMessage(null);
    } catch (error) {
      setFailureMessage(
        error instanceof Error ? error.message : 'The operations console could not be loaded.',
      );
    }
  }, []);

  useEffect(() => {
    void loadConsole();
  }, [loadConsole]);

  async function submitInvite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!validEmail(invite.email) || invite.publicReason.trim() === '') {
      setFormErrors({
        ...(validEmail(invite.email) ? {} : { email: 'Enter a valid email address.' }),
        ...(invite.publicReason.trim() === '' ? { publicReason: 'Enter a public reason.' } : {}),
      });
      return;
    }

    setSubmitting(true);
    setFormErrors({});
    setFailureMessage(null);
    try {
      await inviteOperationalAccount({
        email: invite.email.trim(),
        role: invite.role,
        reasonCategory: invite.reasonCategory,
        publicReason: invite.publicReason.trim(),
        internalNote: invite.internalNote.trim() || undefined,
      });
      setInvite({ ...invite, email: '', publicReason: '', internalNote: '' });
      await loadConsole();
    } catch (error) {
      applyApiError(error, setFormErrors, setFailureMessage);
    } finally {
      setSubmitting(false);
    }
  }

  async function submitDeactivation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (deactivation.accountId === '' || deactivation.publicReason.trim() === '') {
      setFormErrors({
        ...(deactivation.accountId === '' ? { accountId: 'Select an account.' } : {}),
        ...(deactivation.publicReason.trim() === ''
          ? { deactivationPublicReason: 'Enter a public reason.' }
          : {}),
      });
      return;
    }

    setSubmitting(true);
    setFormErrors({});
    setFailureMessage(null);
    try {
      await deactivateOperationalAccount(deactivation.accountId, {
        reasonCategory: deactivation.reasonCategory,
        publicReason: deactivation.publicReason.trim(),
        internalNote: deactivation.internalNote.trim() || undefined,
      });
      setDeactivation({ ...deactivation, accountId: '', publicReason: '', internalNote: '' });
      await loadConsole();
    } catch (error) {
      applyApiError(error, setFormErrors, setFailureMessage);
    } finally {
      setSubmitting(false);
    }
  }

  const deactivatableAccounts = accounts.filter((account) => account.status !== 'DEACTIVATED');
  const roleLabel = session.role === 'ADMINISTRATOR' ? 'Administrator' : 'Moderator';

  return (
    <PageFrame maxWidth="max-w-6xl">
      <header className="flex flex-col justify-between gap-4 sm:flex-row sm:items-start">
        <div>
          <p className="text-sm font-semibold tracking-[0.2em] text-cyan-300 uppercase">
            Operations console
          </p>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white sm:text-4xl">
            Manage operational accounts
          </h1>
          <p className="mt-3 max-w-2xl leading-7 text-slate-300">
            You are signed in as an {roleLabel}. Operational identities can moderate platform
            activity but never sell or bid.
          </p>
        </div>
        <div className="flex gap-3">
          <button
            className="rounded-lg border border-slate-600 px-4 py-2.5 font-semibold text-slate-100 transition hover:border-cyan-300 hover:text-cyan-200"
            onClick={onSignOut}
            type="button"
          >
            Sign out
          </button>
          <button
            className="rounded-lg border border-rose-700/70 px-4 py-2.5 font-semibold text-rose-200 transition hover:border-rose-300 hover:text-rose-100"
            onClick={onRevokeAllSessions}
            type="button"
          >
            Sign out all sessions
          </button>
        </div>
      </header>
      <AuctionOperationsWorkspace administrator />
      {notice !== null && <Notice>{notice}</Notice>}
      {failure !== null && <Failure>{failure}</Failure>}
      {failureMessage !== null && <Failure>{failureMessage}</Failure>}

      <ModerationWorkspace />

      <div className="mt-8 grid gap-6 lg:grid-cols-2">
        <form
          className="space-y-4 rounded-xl border border-slate-700 bg-slate-950/60 p-5"
          noValidate
          onSubmit={submitInvite}
        >
          <FormHeading
            title="Invite an operational account"
            subtitle="The invitee receives a single-use activation link. Every invitation is recorded with its reason and optional internal note."
          />
          <TextField
            error={formErrors.email}
            id="operational-invite-email"
            label="Email address"
            onChange={(email) => setInvite({ ...invite, email })}
            type="email"
            value={invite.email}
          />
          <label
            className="block text-sm font-medium text-slate-100"
            htmlFor="operational-invite-role"
          >
            Role
          </label>
          <select
            className="-mt-2 block w-full rounded-lg border border-slate-600 bg-slate-950 px-3 py-2.5 text-slate-100"
            id="operational-invite-role"
            onChange={(event) => setInvite({ ...invite, role: event.target.value })}
            value={invite.role}
          >
            <option value="MODERATOR">Moderator</option>
            <option value="ADMINISTRATOR">Administrator</option>
          </select>
          <label
            className="block text-sm font-medium text-slate-100"
            htmlFor="operational-invite-reason-category"
          >
            Reason category
          </label>
          <select
            className="-mt-2 block w-full rounded-lg border border-slate-600 bg-slate-950 px-3 py-2.5 text-slate-100"
            id="operational-invite-reason-category"
            onChange={(event) => setInvite({ ...invite, reasonCategory: event.target.value })}
            value={invite.reasonCategory}
          >
            <option value="STAFFING">Staffing</option>
            <option value="SECURITY">Security</option>
            <option value="ROLE_CHANGE">Role change</option>
            <option value="OTHER">Other</option>
          </select>
          <TextField
            error={formErrors.publicReason}
            id="operational-invite-public-reason"
            label="Public reason"
            onChange={(publicReason) => setInvite({ ...invite, publicReason })}
            value={invite.publicReason}
          />
          <TextField
            id="operational-invite-internal-note"
            label="Internal note (optional)"
            onChange={(internalNote) => setInvite({ ...invite, internalNote })}
            value={invite.internalNote}
          />
          <SubmitButton disabled={submitting}>
            {submitting ? 'Sending invitation…' : 'Send invitation'}
          </SubmitButton>
        </form>

        <form
          className="space-y-4 rounded-xl border border-slate-700 bg-slate-950/60 p-5"
          noValidate
          onSubmit={submitDeactivation}
        >
          <FormHeading
            title="Deactivate an account"
            subtitle="Deactivation is permanent, revokes all sessions, and preserves the account as the actor on historical records."
          />
          <label
            className="block text-sm font-medium text-slate-100"
            htmlFor="operational-deactivation-account"
          >
            Account
          </label>
          <select
            aria-describedby={
              formErrors.accountId === undefined
                ? undefined
                : 'operational-deactivation-account-error'
            }
            className="-mt-2 block w-full rounded-lg border border-slate-600 bg-slate-950 px-3 py-2.5 text-slate-100"
            id="operational-deactivation-account"
            onChange={(event) =>
              setDeactivation({ ...deactivation, accountId: event.target.value })
            }
            value={deactivation.accountId}
          >
            <option value="">Select an account</option>
            {deactivatableAccounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.email} · {account.role} · {account.status}
              </option>
            ))}
          </select>
          {formErrors.accountId !== undefined && (
            <p className="text-sm text-rose-300" id="operational-deactivation-account-error">
              {formErrors.accountId}
            </p>
          )}
          <label
            className="block text-sm font-medium text-slate-100"
            htmlFor="operational-deactivation-reason-category"
          >
            Reason category
          </label>
          <select
            className="-mt-2 block w-full rounded-lg border border-slate-600 bg-slate-950 px-3 py-2.5 text-slate-100"
            id="operational-deactivation-reason-category"
            onChange={(event) =>
              setDeactivation({ ...deactivation, reasonCategory: event.target.value })
            }
            value={deactivation.reasonCategory}
          >
            <option value="SECURITY">Security</option>
            <option value="STAFFING">Staffing</option>
            <option value="ROLE_CHANGE">Role change</option>
            <option value="OTHER">Other</option>
          </select>
          <TextField
            error={formErrors.deactivationPublicReason}
            id="operational-deactivation-public-reason"
            label="Public reason"
            onChange={(publicReason) => setDeactivation({ ...deactivation, publicReason })}
            value={deactivation.publicReason}
          />
          <TextField
            id="operational-deactivation-internal-note"
            label="Internal note (optional)"
            onChange={(internalNote) => setDeactivation({ ...deactivation, internalNote })}
            value={deactivation.internalNote}
          />
          <SubmitButton disabled={submitting}>
            {submitting ? 'Deactivating…' : 'Deactivate account'}
          </SubmitButton>
        </form>
      </div>

      <section
        className="mt-8 overflow-hidden rounded-xl border border-slate-700 bg-slate-950/60"
        aria-labelledby="operational-accounts-heading"
      >
        <div className="border-b border-slate-700 p-5">
          <h2 className="text-xl font-semibold text-white" id="operational-accounts-heading">
            Operational accounts
          </h2>
          <p className="mt-1 text-sm text-slate-400">
            Role and activation status are shown for every dedicated identity.
          </p>
        </div>
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-slate-900 text-slate-300">
              <tr>
                <th className="px-5 py-3 font-medium">Email</th>
                <th className="px-5 py-3 font-medium">Role</th>
                <th className="px-5 py-3 font-medium">Status</th>
                <th className="px-5 py-3 font-medium">Invited</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {accounts.map((account) => (
                <tr key={account.id}>
                  <td className="px-5 py-3 text-slate-100">{account.email}</td>
                  <td className="px-5 py-3 text-slate-300">{account.role}</td>
                  <td className="px-5 py-3 text-slate-300">{account.status}</td>
                  <td className="px-5 py-3 text-slate-400">{formatDate(account.invitedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section
        className="mt-8 rounded-xl border border-slate-700 bg-slate-950/60 p-5"
        aria-labelledby="audit-summary-heading"
      >
        <h2 className="text-xl font-semibold text-white" id="audit-summary-heading">
          Audit summary
        </h2>
        <p className="mt-1 text-sm text-slate-400">
          Complete administrator-visible identity history, including categorized reasons and
          internal notes.
        </p>
        <ul className="mt-4 space-y-3 text-sm">
          {auditRecords.length === 0 ? (
            <li className="text-slate-400">No audit records yet.</li>
          ) : (
            auditRecords.map((record) => (
              <li className="rounded-lg border border-slate-800 p-3" key={record.id}>
                <p className="font-medium text-slate-100">{record.action}</p>
                <p className="mt-1 text-slate-400">
                  {formatDate(record.occurredAt)} · {record.metadata}
                </p>
                <p className="mt-1 text-xs text-slate-500">
                  Actor:{' '}
                  {record.actorType === 'SYSTEM'
                    ? 'System'
                    : `${record.actorType ?? 'Unknown'} ${record.actorId ?? 'unknown'}`}{' '}
                  · Target: {record.targetType ?? 'Unknown'} {record.targetId ?? 'unknown'}
                </p>
              </li>
            ))
          )}
        </ul>
      </section>
    </PageFrame>
  );
}

function formatDate(value: string | undefined): string {
  if (value === undefined) return '—';
  return new Intl.DateTimeFormat('en-US', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(new Date(value));
}
