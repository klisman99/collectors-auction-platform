import { type FormEvent, useEffect, useState } from 'react';

import {
  ApiError,
  createDraft,
  type Draft,
  type DraftInput,
  deleteDraft,
  listDrafts,
  submitDraft,
  updateDraft,
  uploadDraftImage,
} from '../api/client';
import { AuctionWorkspace } from '../auctions/AuctionWorkspace';

export function DraftWorkspace() {
  const blank = (): DraftInput => ({
    category: 'CARDS',
    title: '',
    description: '',
    condition: 'EXCELLENT',
    conditionNotes: '',
    ownershipDeclared: false,
  });
  const [drafts, setDrafts] = useState<Draft[]>([]);
  const [editing, setEditing] = useState(blank());
  const [selected, setSelected] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    listDrafts()
      .then(setDrafts)
      .catch(() => setMessage('Drafts could not be loaded.'));
  }, []);

  async function save(event: FormEvent) {
    event.preventDefault();
    setMessage(null);
    try {
      const draft = selected ? await updateDraft(selected, editing) : await createDraft(editing);
      setDrafts(
        selected ? drafts.map((item) => (item.id === draft.id ? draft : item)) : [draft, ...drafts],
      );
      setSelected(draft.id);
      setEditing({ ...draft, images: undefined } as DraftInput);
      setMessage('Private draft saved.');
    } catch (error) {
      setMessage(error instanceof ApiError ? error.message : 'Draft could not be saved.');
    }
  }

  async function uploadImage(file?: File) {
    if (!file || !selected) return;
    try {
      const uploaded = await uploadDraftImage(selected, file);
      setDrafts(
        drafts.map((draft) =>
          draft.id === selected ? { ...draft, images: [...draft.images, uploaded] } : draft,
        ),
      );
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Image upload failed.');
    }
  }

  async function submit(id: string) {
    try {
      const draft = await submitDraft(id);
      setDrafts(drafts.map((item) => (item.id === id ? draft : item)));
      setMessage('Submitted for moderation.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Submission failed.');
    }
  }

  return (
    <section className="mt-8 rounded-xl border border-cyan-900/70 bg-slate-950/60 p-5">
      <h2 className="text-xl font-semibold text-white">Your private drafts</h2>
      <p className="mt-2 text-sm leading-6 text-slate-300">
        One physical collectible per draft. Drafts remain private until you submit them for
        moderation.
      </p>
      <form className="mt-5 grid gap-3" onSubmit={save}>
        <input
          aria-label="Draft title"
          className="rounded-lg border border-slate-600 bg-slate-900 px-3 py-2"
          minLength={5}
          placeholder="Title"
          required
          value={editing.title}
          onChange={(event) => setEditing({ ...editing, title: event.target.value })}
        />
        <select
          aria-label="Category"
          value={editing.category}
          onChange={(event) =>
            setEditing({ ...editing, category: event.target.value as Draft['category'] })
          }
        >
          {[
            'CARDS',
            'COINS_AND_CURRENCY',
            'STAMPS',
            'COMICS_AND_BOOKS',
            'TOYS_AND_FIGURES',
            'MEMORABILIA',
            'ART_AND_ANTIQUES',
            'OTHER',
          ].map((value) => (
            <option key={value}>{value}</option>
          ))}
        </select>
        {editing.category === 'OTHER' && (
          <input
            aria-label="Other category"
            value={editing.otherCategoryLabel ?? ''}
            onChange={(event) => setEditing({ ...editing, otherCategoryLabel: event.target.value })}
          />
        )}
        <select
          aria-label="Condition"
          value={editing.condition}
          onChange={(event) =>
            setEditing({ ...editing, condition: event.target.value as Draft['condition'] })
          }
        >
          {['NEW_SEALED', 'EXCELLENT', 'VERY_GOOD', 'GOOD', 'FAIR', 'POOR', 'NOT_APPLICABLE'].map(
            (value) => (
              <option key={value}>{value}</option>
            ),
          )}
        </select>
        <textarea
          aria-label="Description"
          minLength={20}
          required
          value={editing.description}
          onChange={(event) => setEditing({ ...editing, description: event.target.value })}
        />
        <textarea
          aria-label="Condition notes"
          minLength={10}
          required
          value={editing.conditionNotes}
          onChange={(event) => setEditing({ ...editing, conditionNotes: event.target.value })}
        />
        <label>
          <input
            aria-label="Ownership declaration"
            type="checkbox"
            checked={editing.ownershipDeclared}
            onChange={(event) =>
              setEditing({ ...editing, ownershipDeclared: event.target.checked })
            }
          />{' '}
          I declare that I own this physical collectible and have the right to sell it.
        </label>
        <button
          className="rounded-lg bg-cyan-300 px-4 py-2 font-semibold text-slate-950"
          type="submit"
        >
          {selected ? 'Save draft' : 'Create draft'}
        </button>
      </form>
      {selected && (
        <div className="mt-4">
          <label>
            Images (JPEG, PNG, or WebP; max 5)
            <input
              aria-label="Draft image"
              type="file"
              accept="image/jpeg,image/png,image/webp"
              onChange={(event) => uploadImage(event.target.files?.[0])}
            />
          </label>
        </div>
      )}
      {message && (
        <p className="mt-3 text-sm text-cyan-200" role="status">
          {message}
        </p>
      )}
      <ul className="mt-5 space-y-2">
        {drafts.map((draft) => (
          <li className="rounded-lg border border-slate-700 p-3" key={draft.id}>
            <button
              type="button"
              onClick={() => {
                setSelected(draft.id);
                setEditing(draft);
              }}
            >
              <span className="font-medium text-white">{draft.title}</span>
              <span className="ml-2 text-sm text-slate-400">
                {draft.images.length}/5 images · {draft.status}
              </span>
            </button>
            {draft.status === 'DRAFT' && (
              <>
                <button type="button" onClick={() => submit(draft.id)}>
                  Submit for review
                </button>
                <button
                  type="button"
                  onClick={async () => {
                    await deleteDraft(draft.id);
                    setDrafts(drafts.filter((item) => item.id !== draft.id));
                  }}
                >
                  Delete
                </button>
              </>
            )}
          </li>
        ))}
      </ul>
      <AuctionWorkspace approvedItems={drafts.filter((draft) => draft.status === 'APPROVED')} />
    </section>
  );
}
