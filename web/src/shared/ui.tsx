import type { ReactNode } from 'react';

export function PageFrame({
  children,
  maxWidth = 'max-w-xl',
}: {
  children: ReactNode;
  maxWidth?: string;
}) {
  return (
    <main className="min-h-screen bg-slate-950 px-6 py-12 text-slate-100 sm:px-10 sm:py-16">
      <section
        className={`mx-auto ${maxWidth} rounded-2xl border border-slate-700 bg-slate-900 p-7 shadow-2xl shadow-slate-950/50 sm:p-8`}
      >
        {children}
      </section>
    </main>
  );
}

export function FormHeading({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div>
      <h2 className="text-2xl font-semibold text-white">{title}</h2>
      <p className="mt-2 leading-6 text-slate-300">{subtitle}</p>
    </div>
  );
}

export function TextField({
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
      <label className="block text-sm font-medium text-slate-100" htmlFor={id}>
        {label}
      </label>
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
      {error !== undefined && (
        <p className="mt-2 text-sm text-rose-300" id={`${id}-error`}>
          {error}
        </p>
      )}
    </div>
  );
}

export function SubmitButton({ children, disabled }: { children: ReactNode; disabled: boolean }) {
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

export function PageLink({ children, onClick }: { children: ReactNode; onClick: () => void }) {
  return (
    <button
      className="font-medium text-cyan-300 hover:text-cyan-200"
      onClick={onClick}
      type="button"
    >
      {children}
    </button>
  );
}

export function Notice({ children }: { children: ReactNode }) {
  return (
    <p
      className="mb-6 rounded-lg border border-emerald-700/70 bg-emerald-950/40 p-4 text-sm leading-6 text-emerald-200"
      role="status"
    >
      {children}
    </p>
  );
}

export function Failure({ children }: { children: ReactNode }) {
  return (
    <p
      className="mb-6 rounded-lg border border-rose-700/70 bg-rose-950/40 p-4 text-sm leading-6 text-rose-200"
      role="alert"
    >
      {children}
    </p>
  );
}
