import { type FormEvent, useState } from 'react';

import {
  type AuthenticatedSession,
  activateOperationalAccount,
  registerAccount,
  requestPasswordRecovery,
  resetPassword,
  signIn,
  verifyEmail,
} from '../api/client';
import { applyApiError, type FieldErrors, validEmail } from '../shared/forms';
import {
  Failure,
  FormHeading,
  Notice,
  PageFrame,
  PageLink,
  SubmitButton,
  TextField,
} from '../shared/ui';

type Page = 'register' | 'sign-in' | 'verify' | 'recover' | 'reset' | 'activate-operational';
const initialRegistration = { email: '', publicHandle: '', password: '' };
const initialSignIn = { email: '', password: '' };
const initialReset = { password: '', confirmation: '' };
const initialActivation = { password: '', confirmation: '' };

export function hasIdentityAction(search: string): boolean {
  const parameters = new URLSearchParams(search);
  return (
    (parameters.get('verificationToken') ?? '') !== '' ||
    (parameters.get('recoveryToken') ?? '') !== '' ||
    (parameters.get('operationalActivationToken') ?? '') !== ''
  );
}

export function IdentityAccess({
  initialNotice = null,
  onSessionChange,
}: {
  initialNotice?: string | null;
  onSessionChange: (session: AuthenticatedSession | null) => void;
}) {
  const verificationToken =
    new URLSearchParams(window.location.search).get('verificationToken') ?? '';
  const recoveryToken = new URLSearchParams(window.location.search).get('recoveryToken') ?? '';
  const operationalActivationToken =
    new URLSearchParams(window.location.search).get('operationalActivationToken') ?? '';
  const [page, setPage] = useState<Page>(
    verificationToken !== ''
      ? 'verify'
      : recoveryToken !== ''
        ? 'reset'
        : operationalActivationToken !== ''
          ? 'activate-operational'
          : initialNotice === null
            ? 'register'
            : 'sign-in',
  );
  const [registration, setRegistration] = useState(initialRegistration);
  const [credentials, setCredentials] = useState(initialSignIn);
  const [token, setToken] = useState(verificationToken);
  const [reset, setReset] = useState(initialReset);
  const [activation, setActivation] = useState(initialActivation);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [notice, setNotice] = useState<string | null>(initialNotice);
  const [failure, setFailure] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function moveTo(nextPage: Page) {
    if (
      nextPage !== 'verify' &&
      nextPage !== 'reset' &&
      nextPage !== 'activate-operational' &&
      (verificationToken !== '' || recoveryToken !== '' || operationalActivationToken !== '')
    ) {
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
      onSessionChange(null);
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
      onSessionChange(
        await signIn({ email: credentials.email.trim(), password: credentials.password }),
      );
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
      onSessionChange(null);
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
      await activateOperationalAccount({
        token: operationalActivationToken.trim(),
        password: activation.password,
      });
      window.history.replaceState({}, '', window.location.pathname);
      setNotice('Your operational account is active. Sign in with your new password.');
      onSessionChange(null);
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
        <p className="text-sm font-semibold tracking-[0.2em] text-cyan-300 uppercase">
          Collectors Auction Platform
        </p>
        <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white sm:text-4xl">
          Start collecting with confidence.
        </h1>
        <p className="mt-3 max-w-xl text-base leading-7 text-slate-300">
          Register a regular account, verify your email, and sign in with a secure server-side
          session.
        </p>
      </header>

      {notice !== null && <Notice>{notice}</Notice>}
      {failure !== null && <Failure>{failure}</Failure>}

      {page === 'register' && (
        <form className="space-y-5" noValidate onSubmit={submitRegistration}>
          <FormHeading
            title="Create your account"
            subtitle="Your public handle is permanent and appears on your seller profile."
          />
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
          <p className="-mt-3 text-sm text-slate-400">
            Use 12–128 characters. No composition rules apply.
          </p>
          <SubmitButton disabled={submitting}>
            {submitting ? 'Creating account…' : 'Create account'}
          </SubmitButton>
          <p className="text-sm text-slate-300">
            Already registered? <PageLink onClick={() => moveTo('sign-in')}>Sign in</PageLink>
          </p>
        </form>
      )}

      {page === 'verify' && (
        <form className="space-y-5" noValidate onSubmit={submitVerification}>
          <FormHeading
            title="Verify your email"
            subtitle="Use the token from your verification email. It expires after 24 hours and can be used only once."
          />
          <TextField
            error={fieldErrors.token}
            id="verification-token"
            label="Verification token"
            onChange={setToken}
            value={token}
          />
          <SubmitButton disabled={submitting}>
            {submitting ? 'Verifying…' : 'Verify email'}
          </SubmitButton>
          <p className="text-sm text-slate-300">
            Prefer to sign in? <PageLink onClick={() => moveTo('sign-in')}>Sign in</PageLink>
          </p>
        </form>
      )}

      {page === 'activate-operational' && (
        <form className="space-y-5" noValidate onSubmit={submitOperationalActivation}>
          <FormHeading
            title="Activate your operational account"
            subtitle="Choose a password for your dedicated operations identity. This link expires after 24 hours and can be used only once."
          />
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
          <SubmitButton disabled={submitting}>
            {submitting ? 'Activating…' : 'Activate account'}
          </SubmitButton>
          <p className="text-sm text-slate-300">
            Already activated? <PageLink onClick={() => moveTo('sign-in')}>Sign in</PageLink>
          </p>
        </form>
      )}

      {page === 'sign-in' && (
        <form className="space-y-5" noValidate onSubmit={submitSignIn}>
          <FormHeading
            title="Sign in"
            subtitle="Your session expires after 30 days of inactivity or 90 days in total."
          />
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
          <SubmitButton disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </SubmitButton>
          <p className="text-sm text-slate-300">
            Forgot your password?{' '}
            <PageLink onClick={() => moveTo('recover')}>Forgot password?</PageLink>
          </p>
          <p className="text-sm text-slate-300">
            New here? <PageLink onClick={() => moveTo('register')}>Create an account</PageLink>
          </p>
        </form>
      )}

      {page === 'recover' && (
        <form className="space-y-5" noValidate onSubmit={submitPasswordRecovery}>
          <FormHeading
            title="Recover your password"
            subtitle="Enter your email and we’ll send a single-use recovery link if an account exists."
          />
          <TextField
            autoComplete="email"
            error={fieldErrors.email}
            id="recovery-email"
            label="Email address"
            onChange={(email) => setCredentials({ ...credentials, email })}
            type="email"
            value={credentials.email}
          />
          <SubmitButton disabled={submitting}>
            {submitting ? 'Sending…' : 'Send recovery link'}
          </SubmitButton>
          <p className="text-sm text-slate-300">
            Remembered your password? <PageLink onClick={() => moveTo('sign-in')}>Sign in</PageLink>
          </p>
        </form>
      )}

      {page === 'reset' && (
        <form className="space-y-5" noValidate onSubmit={submitPasswordReset}>
          <FormHeading
            title="Reset your password"
            subtitle="Choose a new password. The recovery link expires after one hour and can be used only once."
          />
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
          <SubmitButton disabled={submitting}>
            {submitting ? 'Resetting…' : 'Reset password'}
          </SubmitButton>
          <p className="text-sm text-slate-300">
            Prefer to sign in? <PageLink onClick={() => moveTo('sign-in')}>Sign in</PageLink>
          </p>
        </form>
      )}
    </PageFrame>
  );
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
