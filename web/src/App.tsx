import { Description } from '@headlessui/react';
import { useEffect, useState } from 'react';

import { getPlatformStatus, type PlatformStatus } from './api/client';

type StatusState =
  | { kind: 'loading' }
  | { kind: 'ready'; status: PlatformStatus }
  | { kind: 'error' };

export function App() {
  const [state, setState] = useState<StatusState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;

    getPlatformStatus()
      .then((status) => {
        if (active) {
          setState({ kind: 'ready', status });
        }
      })
      .catch(() => {
        if (active) {
          setState({ kind: 'error' });
        }
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <main className="min-h-screen bg-slate-950 px-6 py-16 text-slate-100 sm:px-10">
      <section className="mx-auto max-w-2xl rounded-2xl border border-slate-700 bg-slate-900 p-8 shadow-2xl shadow-slate-950/50">
        <p className="text-sm font-semibold tracking-[0.2em] text-cyan-300 uppercase">Platform baseline</p>
        <h1 className="mt-4 text-3xl font-semibold tracking-tight sm:text-4xl">Collectors Auction Platform</h1>
        <Description as="p" className="mt-3 max-w-xl text-base leading-7 text-slate-300">
          The application shell is connected to the authoritative backend status endpoint.
        </Description>

        <div className="mt-8 rounded-xl border border-slate-700 bg-slate-950/60 p-5" aria-live="polite">
          {state.kind === 'loading' && <p className="text-slate-300">Checking platform status…</p>}
          {state.kind === 'error' && (
            <p className="text-rose-300">The backend status endpoint is unavailable. Start the local platform and retry.</p>
          )}
          {state.kind === 'ready' && (
            <dl className="grid gap-4 sm:grid-cols-2">
              <div>
                <dt className="text-sm text-slate-400">Service</dt>
                <dd className="mt-1 font-medium text-slate-100">{state.status.service}</dd>
              </div>
              <div>
                <dt className="text-sm text-slate-400">Status</dt>
                <dd className="mt-1 font-medium text-emerald-300">{state.status.status}</dd>
              </div>
              <div className="sm:col-span-2">
                <dt className="text-sm text-slate-400">Reported at</dt>
                <dd className="mt-1 font-medium text-slate-100">
                  {new Intl.DateTimeFormat('en-US', {
                    dateStyle: 'medium',
                    timeStyle: 'medium',
                    timeZone: 'America/Sao_Paulo',
                  }).format(new Date(state.status.timestamp))}
                </dd>
              </div>
            </dl>
          )}
        </div>
      </section>
    </main>
  );
}
