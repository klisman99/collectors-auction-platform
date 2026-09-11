import type { AuthenticatedSession } from '../api/client';
import { DraftWorkspace } from '../collectibles/DraftWorkspace';
import { Failure, PageFrame } from '../shared/ui';

export function AuthenticatedHome({
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
      <p className="text-sm font-semibold tracking-[0.2em] text-cyan-300 uppercase">
        Authenticated home
      </p>
      <h1 className="mt-3 text-3xl font-semibold tracking-tight text-white sm:text-4xl">
        Welcome back, {handle}.
      </h1>
      {failure !== null && <Failure>{failure}</Failure>}
      <section
        className="mt-8 rounded-xl border border-slate-700 bg-slate-950/60 p-5"
        aria-live="polite"
      >
        <p className={`text-sm font-semibold ${verified ? 'text-emerald-300' : 'text-amber-300'}`}>
          {verified
            ? 'Trading access is active.'
            : 'Email verification is still required for trading.'}
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
