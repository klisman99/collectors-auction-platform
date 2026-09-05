import { useEffect, useState, type FormEvent, type ReactNode } from 'react';

import {
  activateOperationalAccount,
  ApiError,
  deactivateOperationalAccount,
  getAdministrativeAuditRecords,
  getAuthenticatedSession,
  getOperationalAccounts,
  inviteOperationalAccount,
  registerAccount,
  requestPasswordRecovery,
  resetPassword,
  revokeAllSessions,
  signIn,
  signOut,
  verifyEmail,
  type AuditRecord,
  type AuthenticatedSession,
  type OperationalAccountView,
} from './api/client';

type Page = 'register' | 'sign-in' | 'verify' | 'recover' | 'reset' | 'activate-operational';
type FieldErrors = Record<string, string>;

const initialRegistration = { email: '', publicHandle: '', password: '' };
const initialSignIn = { email: '', password: '' };
const initialReset = { password: '', confirmation: '' };
const initialActivation = { password: '', confirmation: '' };

export function App() {
  const verificationToken = new URLSearchParams(window.location.search).get('verificationToken') ?? '';
  const recoveryToken = new URLSearchParams(window.location.search).get('recoveryToken') ?? '';
  const operationalActivationToken = new URLSearchParams(window.location.search).get('operationalActivationToken') ?? '';
  const [page, setPage] = useState<Page>(
    verificationToken !== '' ? 'verify' : recoveryToken !== '' ? 'reset' : operationalActivationToken !== '' ? 'activate-operational' : 'register',
  );
  const [session, setSession] = useState<AuthenticatedSession | null | undefined>(undefined);
  const [registration, setRegistration] = useState(initialRegistration);
  const [credentials, setCredentials] = useState(initialSignIn);
  const [token, setToken] = useState(verificationToken);
  const [reset, setReset] = useState(initialReset);
  const [activation, setActivation] = useState(initialActivation);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [notice, setNotice] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    getAuthenticatedSession().then(setSession).catch(() => setSession(null));
  }, []);

  if (session === undefined) {
    return <PageFrame><p className="text-slate-300">Loading your account…</p></PageFrame>;
  }

  // A verification link must take precedence over an existing (possibly stale)
  // session. This is important when the user registered in another tab and is
  // already signed in with the pending account.
  if (session !== null && page !== 'verify' && page !== 'reset' && page !== 'activate-operational') {
    if (session.accountType === 'OPERATIONAL') {
      if (session.role !== 'ADMINISTRATOR') {
        return (
          <OperationalModeratorHome
            onRevokeAllSessions={() => endSession(true)}
            onSignOut={() => endSession(false)}
            session={session}
          />
        );
      }
      return (
        <OperationalHome
          failure={failure}
          notice={notice}
          onRevokeAllSessions={() => endSession(true)}
          onSignOut={() => endSession(false)}
          session={session}
        />
      );
    }
    return (
      <AuthenticatedHome
        failure={failure}
        onRevokeAllSessions={() => endSession(true)}
        onSignOut={() => endSession(false)}
        session={session}
      />
    );
  }

  function moveTo(nextPage: Page) {
    if (nextPage !== 'verify' && nextPage !== 'reset' && nextPage !== 'activate-operational'
      && (verificationToken !== '' || recoveryToken !== '' || operationalActivationToken !== '')) {
      window.history.replaceState({}, '', window.location.pathname);
      setToken('');
    }
    setPage(nextPage);
    setNotice(null);
    setFailure(null);
    setFieldErrors({});
  }

  async function submitRegistration(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = validateRegistration(registration);
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setSubmitting(true);
    setFieldErrors({});
    setFailure(null);
    try {
      await registerAccount({
        email: registration.email.trim(),
        publicHandle: registration.publicHandle.trim(),
        password: registration.password,
      });
      setNotice('Registration received. Check Mailpit for your single-use verification link.');
      setPage('verify');
    } catch (error) {
      applyApiError(error, setFieldErrors, setFailure);
    } finally {
      setSubmitting(false);
    }
  }

  async function submitVerification(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (token.trim() === '') {
      setFieldErrors({ token: 'Enter the verification token from your email.' });
      return;
    }

    setSubmitting(true);
    setFieldErrors({});
    setFailure(null);
    try {
      await verifyEmail(token.trim());
      window.history.replaceState({}, '', window.location.pathname);
      setNotice('Your email is verified. Sign in to continue.');
      // The existing pending cookie still contains the old account state. Make
      // the user sign in again so the server issues a fresh verified session.
      setSession(null);
      setPage('sign-in');
    } catch (error) {
      applyApiError(error, setFieldErrors, setFailure);
    } finally {
      setSubmitting(false);
    }
  }

  async function submitSignIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = validateSignIn(credentials);
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setSubmitting(true);
    setFieldErrors({});
    setFailure(null);
    try {
      setSession(await signIn({ email: credentials.email.trim(), password: credentials.password }));
    } catch (error) {
      applyApiError(error, setFieldErrors, setFailure);
    } finally {
      setSubmitting(false);
    }
  }

  async function submitPasswordRecovery(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = validatePasswordRecovery(credentials.email);
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setSubmitting(true);
    setFieldErrors({});
    setFailure(null);
    try {
      await requestPasswordRecovery({ email: credentials.email.trim() });
      setNotice('If an account exists for that email, a recovery link is on its way.');
    } catch (error) {
      applyApiError(error, setFieldErrors, setFailure);
    } finally {
      setSubmitting(false);
    }
  }

  async function submitPasswordReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = validatePasswordReset(reset);
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setSubmitting(true);
    setFieldErrors({});
    setFailure(null);
    try {
      await resetPassword({ token: recoveryToken.trim(), password: reset.password });
      window.history.replaceState({}, '', window.location.pathname);
      setNotice('Your password has been reset. Sign in with your new password.');
      setSession(null);
      setPage('sign-in');
    } catch (error) {
      applyApiError(error, setFieldErrors, setFailure);
    } finally {
      setSubmitting(false);
    }
  }

  async function submitOperationalActivation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const errors = validatePasswordReset(activation);
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setSubmitting(true);
    setFieldErrors({});
    setFailure(null);
    try {
      await activateOperationalAccount({ token: operationalActivationToken.trim(), password: activation.password });
      window.history.replaceState({}, '', window.location.pathname);
      setNotice('Your operational account is active. Sign in with your new password.');
      setSession(null);
      setPage('sign-in');
    } catch (error) {
      applyApiError(error, setFieldErrors, setFailure);
    } finally {
      setSubmitting(false);
    }
  }

  async function endSession(revokeAll: boolean) {
    setSubmitting(true);
    setFailure(null);
    try {
      if (revokeAll) {
        await revokeAllSessions();
        setNotice('All sessions have been signed out.');
      } else {
        await signOut();
        setNotice('You have been signed out.');
      }
      setSession(null);
      setPage('sign-in');
    } catch (error) {
      applyApiError(error, setFieldErrors, setFailure);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <PageFrame>
      <header className="mb-8">
        <p className="text-sm font-semibold tracking-[0.2em] text-cyan-300 uppercase">Collectors Auction Platform</p>
        <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white sm:text-4xl">
          Start collecting with confidence.
        </h1>
        <p className="mt-3 max-w-xl text-base leading-7 text-slate-300">
          Register a regular account, verify your email, and sign in with a secure server-side session.
        </p>
      </header>

      {notice !== null && <Notice>{notice}</Notice>}
      {failure !== null && <Failure>{failure}</Failure>}

      {page === 'register' && (
        <form className="space-y-5" noValidate onSubmit={submitRegistration}>
          <FormHeading title="Create your account" subtitle="Your public handle is permanent and appears on your seller profile." />
          <TextField
            autoComplete="email"
            error={fieldErrors.email}
            id="registration-email"
            label="Email address"
            onChange={(email) => setRegistration({ ...registration, email })}
            type="email"
            value={registration.email}
          />
          <TextField
            autoComplete="username"
            error={fieldErrors.publicHandle}
            id="registration-handle"
            label="Public handle"
            onChange={(publicHandle) => setRegistration({ ...registration, publicHandle })}
            value={registration.publicHandle}
          />
          <p className="-mt-3 text-sm text-slate-400">3–30 letters, numbers, or underscores.</p>
          <TextField
            autoComplete="new-password"
            error={fieldErrors.password}
            id="registration-password"
            label="Password"
            onChange={(password) => setRegistration({ ...registration, password })}
            type="password"
            value={registration.password}
          />
          <p className="-mt-3 text-sm text-slate-400">Use 12–128 characters. No composition rules apply.</p>
          <SubmitButton disabled={submitting}>{submitting ? 'Creating account…' : 'Create account'}</SubmitButton>
          <p className="text-sm text-slate-300">
            Already registered?{' '}
            <PageLink onClick={() => moveTo('sign-in')}>Sign in</PageLink>
          </p>
        </form>
      )}

      {page === 'verify' && (
        <form className="space-y-5" noValidate onSubmit={submitVerification}>
          <FormHeading title="Verify your email" subtitle="Use the token from your verification email. It expires after 24 hours and can be used only once." />
          <TextField
            error={fieldErrors.token}
            id="verification-token"
            label="Verification token"
            onChange={setToken}
            value={token}
          />
          <SubmitButton disabled={submitting}>{submitting ? 'Verifying…' : 'Verify email'}</SubmitButton>
          <p className="text-sm text-slate-300">
            Prefer to sign in?{' '}
            <PageLink onClick={() => moveTo('sign-in')}>Sign in</PageLink>
          </p>
        </form>
      )}

      {page === 'activate-operational' && (
        <form className="space-y-5" noValidate onSubmit={submitOperationalActivation}>
          <FormHeading title="Activate your operational account" subtitle="Choose a password for your dedicated operations identity. This link expires after 24 hours and can be used only once." />
          <TextField
            autoComplete="new-password"
            error={fieldErrors.password}
            id="operational-password"
            label="New password"
            onChange={(password) => setActivation({ ...activation, password })}
            type="password"
            value={activation.password}
          />
          <TextField
            autoComplete="new-password"
            error={fieldErrors.confirmation}
            id="operational-password-confirmation"
            label="Confirm new password"
            onChange={(confirmation) => setActivation({ ...activation, confirmation })}
            type="password"
            value={activation.confirmation}
          />
          <SubmitButton disabled={submitting}>{submitting ? 'Activating…' : 'Activate account'}</SubmitButton>
          <p className="text-sm text-slate-300">
            Already activated?{' '}
            <PageLink onClick={() => moveTo('sign-in')}>Sign in</PageLink>
          </p>
        </form>
      )}

      {page === 'sign-in' && (
        <form className="space-y-5" noValidate onSubmit={submitSignIn}>
          <FormHeading title="Sign in" subtitle="Your session expires after 30 days of inactivity or 90 days in total." />
          <TextField
            autoComplete="email"
            error={fieldErrors.email}
            id="sign-in-email"
            label="Email address"
            onChange={(email) => setCredentials({ ...credentials, email })}
            type="email"
            value={credentials.email}
          />
          <TextField
            autoComplete="current-password"
            error={fieldErrors.password}
            id="sign-in-password"
            label="Password"
            onChange={(password) => setCredentials({ ...credentials, password })}
            type="password"
            value={credentials.password}
          />
          <SubmitButton disabled={submitting}>{submitting ? 'Signing in…' : 'Sign in'}</SubmitButton>
          <p className="text-sm text-slate-300">
            Forgot your password?{' '}
            <PageLink onClick={() => moveTo('recover')}>Forgot password?</PageLink>
          </p>
          <p className="text-sm text-slate-300">
            New here?{' '}
            <PageLink onClick={() => moveTo('register')}>Create an account</PageLink>
          </p>
        </form>
      )}

      {page === 'recover' && (
        <form className="space-y-5" noValidate onSubmit={submitPasswordRecovery}>
          <FormHeading title="Recover your password" subtitle="Enter your email and we’ll send a single-use recovery link if an account exists." />
          <TextField
            autoComplete="email"
            error={fieldErrors.email}
            id="recovery-email"
            label="Email address"
            onChange={(email) => setCredentials({ ...credentials, email })}
            type="email"
            value={credentials.email}
          />
          <SubmitButton disabled={submitting}>{submitting ? 'Sending…' : 'Send recovery link'}</SubmitButton>
          <p className="text-sm text-slate-300">
            Remembered your password?{' '}
            <PageLink onClick={() => moveTo('sign-in')}>Sign in</PageLink>
          </p>
        </form>
      )}

      {page === 'reset' && (
        <form className="space-y-5" noValidate onSubmit={submitPasswordReset}>
          <FormHeading title="Reset your password" subtitle="Choose a new password. The recovery link expires after one hour and can be used only once." />
          <TextField
            autoComplete="new-password"
            error={fieldErrors.password}
            id="reset-password"
            label="New password"
            onChange={(password) => setReset({ ...reset, password })}
            type="password"
            value={reset.password}
          />
          <TextField
            autoComplete="new-password"
            error={fieldErrors.confirmation}
            id="reset-password-confirmation"
            label="Confirm new password"
            onChange={(confirmation) => setReset({ ...reset, confirmation })}
            type="password"
            value={reset.confirmation}
          />
          <SubmitButton disabled={submitting}>{submitting ? 'Resetting…' : 'Reset password'}</SubmitButton>
          <p className="text-sm text-slate-300">
            Prefer to sign in?{' '}
            <PageLink onClick={() => moveTo('sign-in')}>Sign in</PageLink>
          </p>
        </form>
      )}
    </PageFrame>
  );
}

function AuthenticatedHome({
  failure,
  onRevokeAllSessions,
  onSignOut,
  session,
}: {
  failure: string | null;
  onRevokeAllSessions: () => void;
  onSignOut: () => void;
  session: AuthenticatedSession;
}) {
  const verified = session.verified === true;
  const handle = session.publicHandle ?? 'collector';

  return (
    <PageFrame>
      <p className="text-sm font-semibold tracking-[0.2em] text-cyan-300 uppercase">Authenticated home</p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white sm:text-4xl">Welcome back, {handle}.</h1>
      {failure !== null && <Failure>{failure}</Failure>}
      <section className="mt-8 rounded-xl border border-slate-700 bg-slate-950/60 p-5" aria-live="polite">
        <p className={`text-sm font-semibold ${verified ? 'text-emerald-300' : 'text-amber-300'}`}>
          {verified ? 'Trading access is active.' : 'Email verification is still required for trading.'}
        </p>
        <p className="mt-2 leading-6 text-slate-300">
          {verified
            ? 'You can now use marketplace commands when they become available.'
            : 'You can browse the platform, but submitting items, scheduling auctions, and bidding remain unavailable.'}
        </p>
      </section>
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
    </PageFrame>
  );
}

function OperationalModeratorHome({
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
      <p className="text-sm font-semibold tracking-[0.2em] text-cyan-300 uppercase">Operations workspace</p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white sm:text-4xl">Moderator access is active.</h1>
      <p className="mt-4 leading-7 text-slate-300">
        This dedicated moderator identity can review platform activity when moderation tools are enabled. It cannot sell, bid, invite operational accounts, or view administrator audit records.
      </p>
      <p className="mt-4 text-sm text-slate-400">Signed in as {session.role === 'MODERATOR' ? 'Moderator' : 'Operational user'}.</p>
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
    </PageFrame>
  );
}

function OperationalHome({
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

  async function loadConsole() {
    try {
      const [loadedAccounts, loadedAuditRecords] = await Promise.all([
        getOperationalAccounts(),
        getAdministrativeAuditRecords(),
      ]);
      setAccounts(loadedAccounts);
      setAuditRecords(loadedAuditRecords);
      setFailureMessage(null);
    } catch (error) {
      setFailureMessage(error instanceof Error ? error.message : 'The operations console could not be loaded.');
    }
  }

  useEffect(() => {
    void loadConsole();
  }, []);

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
        ...(deactivation.publicReason.trim() === '' ? { deactivationPublicReason: 'Enter a public reason.' } : {}),
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
          <p className="text-sm font-semibold tracking-[0.2em] text-cyan-300 uppercase">Operations console</p>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white sm:text-4xl">Manage operational accounts</h1>
          <p className="mt-3 max-w-2xl leading-7 text-slate-300">You are signed in as an {roleLabel}. Operational identities can moderate platform activity but never sell or bid.</p>
        </div>
        <div className="flex gap-3">
          <button className="rounded-lg border border-slate-600 px-4 py-2.5 font-semibold text-slate-100 transition hover:border-cyan-300 hover:text-cyan-200" onClick={onSignOut} type="button">Sign out</button>
          <button className="rounded-lg border border-rose-700/70 px-4 py-2.5 font-semibold text-rose-200 transition hover:border-rose-300 hover:text-rose-100" onClick={onRevokeAllSessions} type="button">Sign out all sessions</button>
        </div>
      </header>
      {notice !== null && <Notice>{notice}</Notice>}
      {failure !== null && <Failure>{failure}</Failure>}
      {failureMessage !== null && <Failure>{failureMessage}</Failure>}

      <div className="mt-8 grid gap-6 lg:grid-cols-2">
        <form className="space-y-4 rounded-xl border border-slate-700 bg-slate-950/60 p-5" noValidate onSubmit={submitInvite}>
          <FormHeading title="Invite an operational account" subtitle="The invitee receives a single-use activation link. Every invitation is recorded with its reason and optional internal note." />
          <TextField error={formErrors.email} id="operational-invite-email" label="Email address" onChange={(email) => setInvite({ ...invite, email })} type="email" value={invite.email} />
          <label className="block text-sm font-medium text-slate-100" htmlFor="operational-invite-role">Role</label>
          <select className="-mt-2 block w-full rounded-lg border border-slate-600 bg-slate-950 px-3 py-2.5 text-slate-100" id="operational-invite-role" onChange={(event) => setInvite({ ...invite, role: event.target.value })} value={invite.role}>
            <option value="MODERATOR">Moderator</option>
            <option value="ADMINISTRATOR">Administrator</option>
          </select>
          <label className="block text-sm font-medium text-slate-100" htmlFor="operational-invite-reason-category">Reason category</label>
          <select className="-mt-2 block w-full rounded-lg border border-slate-600 bg-slate-950 px-3 py-2.5 text-slate-100" id="operational-invite-reason-category" onChange={(event) => setInvite({ ...invite, reasonCategory: event.target.value })} value={invite.reasonCategory}>
            <option value="STAFFING">Staffing</option>
            <option value="SECURITY">Security</option>
            <option value="ROLE_CHANGE">Role change</option>
            <option value="OTHER">Other</option>
          </select>
          <TextField error={formErrors.publicReason} id="operational-invite-public-reason" label="Public reason" onChange={(publicReason) => setInvite({ ...invite, publicReason })} value={invite.publicReason} />
          <TextField id="operational-invite-internal-note" label="Internal note (optional)" onChange={(internalNote) => setInvite({ ...invite, internalNote })} value={invite.internalNote} />
          <SubmitButton disabled={submitting}>{submitting ? 'Sending invitation…' : 'Send invitation'}</SubmitButton>
        </form>

        <form className="space-y-4 rounded-xl border border-slate-700 bg-slate-950/60 p-5" noValidate onSubmit={submitDeactivation}>
          <FormHeading title="Deactivate an account" subtitle="Deactivation is permanent, revokes all sessions, and preserves the account as the actor on historical records." />
          <label className="block text-sm font-medium text-slate-100" htmlFor="operational-deactivation-account">Account</label>
          <select aria-describedby={formErrors.accountId === undefined ? undefined : 'operational-deactivation-account-error'} className="-mt-2 block w-full rounded-lg border border-slate-600 bg-slate-950 px-3 py-2.5 text-slate-100" id="operational-deactivation-account" onChange={(event) => setDeactivation({ ...deactivation, accountId: event.target.value })} value={deactivation.accountId}>
            <option value="">Select an account</option>
            {deactivatableAccounts.map((account) => <option key={account.id} value={account.id}>{account.email} · {account.role} · {account.status}</option>)}
          </select>
          {formErrors.accountId !== undefined && <p className="text-sm text-rose-300" id="operational-deactivation-account-error">{formErrors.accountId}</p>}
          <label className="block text-sm font-medium text-slate-100" htmlFor="operational-deactivation-reason-category">Reason category</label>
          <select className="-mt-2 block w-full rounded-lg border border-slate-600 bg-slate-950 px-3 py-2.5 text-slate-100" id="operational-deactivation-reason-category" onChange={(event) => setDeactivation({ ...deactivation, reasonCategory: event.target.value })} value={deactivation.reasonCategory}>
            <option value="SECURITY">Security</option>
            <option value="STAFFING">Staffing</option>
            <option value="ROLE_CHANGE">Role change</option>
            <option value="OTHER">Other</option>
          </select>
          <TextField error={formErrors.deactivationPublicReason} id="operational-deactivation-public-reason" label="Public reason" onChange={(publicReason) => setDeactivation({ ...deactivation, publicReason })} value={deactivation.publicReason} />
          <TextField id="operational-deactivation-internal-note" label="Internal note (optional)" onChange={(internalNote) => setDeactivation({ ...deactivation, internalNote })} value={deactivation.internalNote} />
          <SubmitButton disabled={submitting}>{submitting ? 'Deactivating…' : 'Deactivate account'}</SubmitButton>
        </form>
      </div>

      <section className="mt-8 overflow-hidden rounded-xl border border-slate-700 bg-slate-950/60" aria-labelledby="operational-accounts-heading">
        <div className="border-b border-slate-700 p-5"><h2 className="text-xl font-semibold text-white" id="operational-accounts-heading">Operational accounts</h2><p className="mt-1 text-sm text-slate-400">Role and activation status are shown for every dedicated identity.</p></div>
        <div className="overflow-x-auto"><table className="min-w-full text-left text-sm"><thead className="bg-slate-900 text-slate-300"><tr><th className="px-5 py-3 font-medium">Email</th><th className="px-5 py-3 font-medium">Role</th><th className="px-5 py-3 font-medium">Status</th><th className="px-5 py-3 font-medium">Invited</th></tr></thead><tbody className="divide-y divide-slate-800">{accounts.map((account) => <tr key={account.id}><td className="px-5 py-3 text-slate-100">{account.email}</td><td className="px-5 py-3 text-slate-300">{account.role}</td><td className="px-5 py-3 text-slate-300">{account.status}</td><td className="px-5 py-3 text-slate-400">{formatDate(account.invitedAt)}</td></tr>)}</tbody></table></div>
      </section>

      <section className="mt-8 rounded-xl border border-slate-700 bg-slate-950/60 p-5" aria-labelledby="audit-summary-heading">
        <h2 className="text-xl font-semibold text-white" id="audit-summary-heading">Audit summary</h2>
        <p className="mt-1 text-sm text-slate-400">Complete administrator-visible identity history, including categorized reasons and internal notes.</p>
        <ul className="mt-4 space-y-3 text-sm">{auditRecords.length === 0 ? <li className="text-slate-400">No audit records yet.</li> : auditRecords.map((record) => <li className="rounded-lg border border-slate-800 p-3" key={record.id}><p className="font-medium text-slate-100">{record.action}</p><p className="mt-1 text-slate-400">{formatDate(record.occurredAt)} · {record.metadata}</p><p className="mt-1 text-xs text-slate-500">Actor: {record.actorType === 'SYSTEM' ? 'System' : `${record.actorType ?? 'Unknown'} ${record.actorId ?? 'unknown'}`} · Target: {record.targetType ?? 'Unknown'} {record.targetId ?? 'unknown'}</p></li>)}</ul>
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

function PageFrame({ children, maxWidth = 'max-w-xl' }: { children: ReactNode; maxWidth?: string }) {
  return (
    <main className="min-h-screen bg-slate-950 px-6 py-12 text-slate-100 sm:px-10 sm:py-16">
      <section className={`mx-auto ${maxWidth} rounded-2xl border border-slate-700 bg-slate-900 p-7 shadow-2xl shadow-slate-950/50 sm:p-8`}>
        {children}
      </section>
    </main>
  );
}

function FormHeading({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div>
      <h2 className="text-2xl font-semibold text-white">{title}</h2>
      <p className="mt-2 leading-6 text-slate-300">{subtitle}</p>
    </div>
  );
}

function TextField({
  autoComplete,
  error,
  id,
  label,
  onChange,
  type = 'text',
  value,
}: {
  autoComplete?: string;
  error?: string;
  id: string;
  label: string;
  onChange: (value: string) => void;
  type?: string;
  value: string;
}) {
  return (
    <div>
      <label className="block text-sm font-medium text-slate-100" htmlFor={id}>{label}</label>
      <input
        aria-describedby={error === undefined ? undefined : `${id}-error`}
        aria-invalid={error !== undefined}
        autoComplete={autoComplete}
        className="mt-2 block w-full rounded-lg border border-slate-600 bg-slate-950 px-3 py-2.5 text-slate-100 outline-none transition focus:border-cyan-300 focus:ring-2 focus:ring-cyan-300/30"
        id={id}
        onChange={(event) => onChange(event.target.value)}
        type={type}
        value={value}
      />
      {error !== undefined && <p className="mt-2 text-sm text-rose-300" id={`${id}-error`}>{error}</p>}
    </div>
  );
}

function SubmitButton({ children, disabled }: { children: ReactNode; disabled: boolean }) {
  return (
    <button
      className="w-full rounded-lg bg-cyan-300 px-4 py-2.5 font-semibold text-slate-950 transition hover:bg-cyan-200 disabled:cursor-not-allowed disabled:opacity-60"
      disabled={disabled}
      type="submit"
    >
      {children}
    </button>
  );
}

function PageLink({ children, onClick }: { children: ReactNode; onClick: () => void }) {
  return <button className="font-medium text-cyan-300 hover:text-cyan-200" onClick={onClick} type="button">{children}</button>;
}

function Notice({ children }: { children: ReactNode }) {
  return <p className="mb-6 rounded-lg border border-emerald-700/70 bg-emerald-950/40 p-4 text-sm leading-6 text-emerald-200" role="status">{children}</p>;
}

function Failure({ children }: { children: ReactNode }) {
  return <p className="mb-6 rounded-lg border border-rose-700/70 bg-rose-950/40 p-4 text-sm leading-6 text-rose-200" role="alert">{children}</p>;
}

function validateRegistration(input: typeof initialRegistration): FieldErrors {
  const errors: FieldErrors = {};
  if (!validEmail(input.email)) {
    errors.email = 'Enter a valid email address.';
  }
  if (!/^[A-Za-z0-9_]{3,30}$/.test(input.publicHandle.trim())) {
    errors.publicHandle = 'Use 3–30 letters, numbers, or underscores.';
  }
  const passwordLength = [...input.password].length;
  if (passwordLength < 12 || passwordLength > 128) {
    errors.password = 'Password must contain between 12 and 128 characters.';
  }
  return errors;
}

function validateSignIn(input: typeof initialSignIn): FieldErrors {
  const errors: FieldErrors = {};
  if (!validEmail(input.email)) {
    errors.email = 'Enter a valid email address.';
  }
  if (input.password.length === 0) {
    errors.password = 'Enter your password.';
  }
  return errors;
}

function validatePasswordRecovery(email: string): FieldErrors {
  return validEmail(email) ? {} : { email: 'Enter a valid email address.' };
}

function validatePasswordReset(input: typeof initialReset): FieldErrors {
  const errors: FieldErrors = {};
  const passwordLength = [...input.password].length;
  if (passwordLength < 12 || passwordLength > 128) {
    errors.password = 'Password must contain between 12 and 128 characters.';
  }
  if (input.password !== input.confirmation) {
    errors.confirmation = 'Passwords must match.';
  }
  return errors;
}

function validEmail(email: string): boolean {
  return /^\S+@\S+\.\S+$/.test(email.trim());
}

function applyApiError(
  error: unknown,
  setFieldErrors: (errors: FieldErrors) => void,
  setFailure: (message: string) => void,
) {
  if (error instanceof ApiError) {
    const serverErrors = Object.fromEntries(
      (error.fieldErrors ?? [])
        .filter((fieldError) => fieldError.field !== undefined && fieldError.message !== undefined)
        .map((fieldError) => [fieldError.field as string, fieldError.message as string]),
    );
    setFieldErrors(serverErrors);
    if (error.code === 'VERIFICATION_TOKEN_INVALID') {
      setFailure('This verification link is invalid, expired, or has already been used. Register again to request a new link.');
      return;
    }
    if (error.code === 'PASSWORD_RECOVERY_TOKEN_INVALID') {
      setFailure('This recovery link is invalid, expired, or has already been used. Request a new link to reset your password.');
      return;
    }
    setFailure(error.message);
    return;
  }

  setFailure('The request could not be completed. Please try again.');
}
