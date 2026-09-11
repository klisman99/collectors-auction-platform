import { useCallback, useEffect, useState } from 'react';

import {
  approveModerationSubmission,
  listModerationSubmissions,
  type ModerationSubmission,
  rejectModerationSubmission,
} from '../api/client';

export function ModerationWorkspace() {
  const [submissions, setSubmissions] = useState<ModerationSubmission[]>([]);
  const [reasons, setReasons] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setSubmissions(await listModerationSubmissions());
      setMessage(null);
    } catch (error) {
      setMessage(
        error instanceof Error ? error.message : 'Moderation submissions could not be loaded.',
      );
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function approve(id: string) {
    try {
      await approveModerationSubmission(id);
      await load();
      setMessage('Collectible approved.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'The approval could not be saved.');
    }
  }

  async function reject(id: string) {
    const reason = reasons[id]?.trim() ?? '';
    if (reason.length < 5) {
      setMessage('A rejection reason must contain at least five characters.');
      return;
    }
    try {
      await rejectModerationSubmission(id, reason);
      await load();
      setMessage('Collectible returned to Draft.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'The rejection could not be saved.');
    }
  }

  return (
    <section
      className="mt-8 rounded-xl border border-cyan-900/70 bg-slate-950/60 p-5"
      aria-labelledby="moderation-queue-heading"
    >
      <h2 className="text-xl font-semibold text-white" id="moderation-queue-heading">
        Moderation queue
      </h2>
      <p className="mt-2 text-sm leading-6 text-slate-300">
        Review the seller's description, declared ownership, condition, and processed images. Do not
        certify authenticity or value.
      </p>
      {message !== null && (
        <p className="mt-3 text-sm text-cyan-200" role="status">
          {message}
        </p>
      )}
      <ul className="mt-5 space-y-5">
        {submissions.length === 0 && (
          <li className="text-sm text-slate-400">No submissions are awaiting review.</li>
        )}
        {submissions.map((submission) => (
          <li className="rounded-lg border border-slate-700 p-4" key={submission.id}>
            <h3 className="font-semibold text-white">{submission.title}</h3>
            <p className="mt-2 text-sm text-slate-300">
              {submission.category}
              {submission.otherCategoryLabel === undefined
                ? ''
                : ` · ${submission.otherCategoryLabel}`}{' '}
              · {submission.condition}
            </p>
            <p className="mt-2 whitespace-pre-wrap text-sm text-slate-300">
              {submission.description}
            </p>
            <p className="mt-2 text-sm text-slate-300">
              Condition notes: {submission.conditionNotes}
            </p>
            <p className="mt-2 text-sm text-slate-300">
              Ownership declared: {submission.ownershipDeclared ? 'Yes' : 'No'}
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              {submission.images.map((image) => (
                <img
                  alt={`Submission for ${submission.title}`}
                  className="h-24 w-24 rounded object-cover"
                  key={image.id}
                  src={image.url}
                />
              ))}
            </div>
            <div className="mt-4 flex flex-col gap-2 sm:flex-row">
              <button
                className="rounded-lg bg-cyan-300 px-3 py-2 font-semibold text-slate-950"
                onClick={() => approve(submission.id)}
                type="button"
              >
                Approve
              </button>
              <input
                aria-label={`Rejection reason for ${submission.title}`}
                className="rounded-lg border border-slate-600 bg-slate-900 px-3 py-2"
                minLength={5}
                onChange={(event) =>
                  setReasons({ ...reasons, [submission.id]: event.target.value })
                }
                placeholder="Public rejection reason"
                value={reasons[submission.id] ?? ''}
              />
              <button
                className="rounded-lg border border-rose-700 px-3 py-2 font-semibold text-rose-200"
                onClick={() => reject(submission.id)}
                type="button"
              >
                Reject
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
