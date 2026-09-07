import { useEffect, useState, type FormEvent, type ReactNode } from 'react';

import {
  ApiError,
  getAuthenticatedSession,
  registerAccount,
  requestPasswordRecovery,
  resetPassword,
  revokeAllSessions,
  signIn,
  signOut,
  verifyEmail,
  type AuthenticatedSession,
} from './api/client';

type Page = 'register' | 'sign-in' | 'verify' | 'recover' | 'reset';
type FieldErrors = Record<string, string>;

const initialRegistration = { email: '', publicHandle: '', password: '' };
const initialSignIn = { email: '', password: '' };
const initialReset = { password: '', confirmation: '' };

export function App() {
  const verificationToken = new URLSearchParams(window.location.search).get('verificationToken') ?? '';
  const recoveryToken = new URLSearchParams(window.location.search).get('recoveryToken') ?? '';
  const [page, setPage] = useState<Page>(
    verificationToken !== '' ? 'verify' : recoveryToken !== '' ? 'reset' : 'register',
  );
  const [session, setSession] = useState<AuthenticatedSession | null | undefined>(undefined);
  const [registration, setRegistration] = useState(initialRegistration);
  const [credentials, setCredentials] = useState(initialSignIn);
  const [token, setToken] = useState(verificationToken);
  const [reset, setReset] = useState(initialReset);
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
  if (session !== null && page !== 'verify' && page !== 'reset') {
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
    if (nextPage !== 'verify' && nextPage !== 'reset' && (verificationToken !== '' || recoveryToken !== '')) {
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
      {verified && <DraftWorkspace />}
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

function DraftWorkspace() {
  const [drafts, setDrafts] = useState<import('./api/client').Draft[]>([]);
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  useEffect(() => { fetch('/api/v1/catalog/drafts', { credentials: 'same-origin' }).then((response) => response.ok ? response.json() : Promise.reject()).then(setDrafts).catch(() => setMessage('Drafts could not be loaded.')); }, []);
  async function save(event: FormEvent) {
    event.preventDefault(); setMessage(null);
    try {
      const csrfResponse = await fetch('/api/v1/csrf', { credentials: 'same-origin' });
      const csrf = await csrfResponse.json() as { headerName: string; token: string };
      const response = await fetch('/api/v1/catalog/drafts', { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token }, body: JSON.stringify({ category: 'OTHER', otherCategoryLabel: 'Collectible', title, description: 'A private collectible draft awaiting its full description.', condition: 'NOT_APPLICABLE', conditionNotes: 'Condition details to be completed.', ownershipDeclared: true }) });
      if (!response.ok) throw new Error('Draft could not be saved.');
      const draft = await response.json() as import('./api/client').Draft;
      setDrafts([draft, ...drafts]); setTitle(''); setMessage('Private draft saved. Add images and complete the details before submitting.');
    } catch (error) { setMessage(error instanceof ApiError ? error.message : 'Draft could not be saved.'); }
  }
  return <section className="mt-8 rounded-xl border border-cyan-900/70 bg-slate-950/60 p-5"><h2 className="text-xl font-semibold text-white">Your private drafts</h2><p className="mt-2 text-sm leading-6 text-slate-300">One physical collectible per draft. Drafts remain private until you submit them for moderation.</p><form className="mt-5 flex gap-2" onSubmit={save}><input aria-label="Draft title" className="min-w-0 flex-1 rounded-lg border border-slate-600 bg-slate-900 px-3 py-2 text-slate-100" minLength={5} placeholder="Draft title" required value={title} onChange={(event) => setTitle(event.target.value)} /><button className="rounded-lg bg-cyan-300 px-4 py-2 font-semibold text-slate-950" type="submit">New draft</button></form>{message && <p className="mt-3 text-sm text-cyan-200" role="status">{message}</p>}<ul className="mt-5 space-y-2">{drafts.map((draft) => <li className="rounded-lg border border-slate-700 p-3" key={draft.id}><span className="font-medium text-white">{draft.title}</span><span className="ml-2 text-sm text-slate-400">{draft.images.length}/5 images</span></li>)}</ul></section>;
}

function PageFrame({ children }: { children: ReactNode }) {
  return (
    <main className="min-h-screen bg-slate-950 px-6 py-12 text-slate-100 sm:px-10 sm:py-16">
      <section className="mx-auto max-w-xl rounded-2xl border border-slate-700 bg-slate-900 p-7 shadow-2xl shadow-slate-950/50 sm:p-8">
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
