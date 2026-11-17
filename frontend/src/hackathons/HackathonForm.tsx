import { useEffect, useState, type FormEvent } from 'react';
import type { Hackathon, HackathonInput, HackathonMode } from '../api';

interface Props {
  /** Present when editing. */
  initial?: Hackathon;
  isAdmin: boolean;
  onSubmit: (input: HackathonInput) => Promise<void>;
  onClose: () => void;
}

const empty: HackathonInput = {
  title: '',
  organizer: '',
  description: '',
  websiteUrl: '',
  mode: 'ONLINE',
  location: '',
  startDate: '',
  endDate: '',
  registrationDeadline: null,
  prizePool: '',
};

function fromHackathon(h: Hackathon): HackathonInput {
  return {
    title: h.title,
    organizer: h.organizer ?? '',
    description: h.description ?? '',
    websiteUrl: h.websiteUrl ?? '',
    mode: h.mode,
    location: h.location ?? '',
    startDate: h.startDate,
    endDate: h.endDate,
    registrationDeadline: h.registrationDeadline,
    prizePool: h.prizePool ?? '',
  };
}

export default function HackathonForm({ initial, isAdmin, onSubmit, onClose }: Props) {
  const [v, setV] = useState<HackathonInput>(initial ? fromHackathon(initial) : empty);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const set = <K extends keyof HackathonInput>(k: K, value: HackathonInput[K]) => setV((s) => ({ ...s, [k]: value }));

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && !busy && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [busy, onClose]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onSubmit({ ...v, registrationDeadline: v.registrationDeadline || null });
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  }

  const editing = !!initial;

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && !busy && onClose()}>
      <form className="card modal modal--wide" onSubmit={submit} role="dialog" aria-labelledby="hk-form-title">
        <h2 id="hk-form-title">{editing ? 'Edit hackathon' : 'Post a hackathon'}</h2>
        {!editing && (
          <p className={`notice ${isAdmin ? 'notice--official' : ''}`}>
            {isAdmin
              ? '✓ As an admin, this goes live right away as an Official hackathon with its own channel.'
              : 'An admin will review your post before it goes live. Once approved it gets its own channel.'}
          </p>
        )}

        <div className="form-grid">
          <label className="field span-2">
            <span>Hackathon name *</span>
            <input autoFocus required maxLength={100} placeholder="e.g. Smart India Hackathon 2026"
              value={v.title} onChange={(e) => set('title', e.target.value)} />
          </label>

          <label className="field">
            <span>Organiser</span>
            <input maxLength={100} placeholder="e.g. Govt. of India, MLH, Devfolio"
              value={v.organizer} onChange={(e) => set('organizer', e.target.value)} />
          </label>

          <label className="field">
            <span>Website / registration link</span>
            <input type="url" maxLength={500} placeholder="https://…"
              value={v.websiteUrl} onChange={(e) => set('websiteUrl', e.target.value)} />
          </label>

          <label className="field">
            <span>Starts *</span>
            <input type="date" required value={v.startDate} onChange={(e) => set('startDate', e.target.value)} />
          </label>

          <label className="field">
            <span>Ends *</span>
            <input type="date" required min={v.startDate || undefined}
              value={v.endDate} onChange={(e) => set('endDate', e.target.value)} />
          </label>

          <label className="field">
            <span>Registration deadline</span>
            <input type="date" max={v.endDate || undefined}
              value={v.registrationDeadline ?? ''} onChange={(e) => set('registrationDeadline', e.target.value || null)} />
          </label>

          <label className="field">
            <span>Prize pool</span>
            <input maxLength={60} placeholder="e.g. ₹1,00,000"
              value={v.prizePool} onChange={(e) => set('prizePool', e.target.value)} />
          </label>

          <label className="field">
            <span>Mode</span>
            <select value={v.mode} onChange={(e) => set('mode', e.target.value as HackathonMode)}>
              <option value="ONLINE">Online</option>
              <option value="OFFLINE">In person</option>
              <option value="HYBRID">Hybrid</option>
            </select>
          </label>

          <label className="field">
            <span>Location {v.mode === 'ONLINE' && <span className="muted">(optional)</span>}</span>
            <input maxLength={120} placeholder={v.mode === 'ONLINE' ? 'Online' : 'e.g. JIIT Sector 62, Noida'}
              value={v.location} onChange={(e) => set('location', e.target.value)} />
          </label>

          <label className="field span-2">
            <span>About</span>
            <textarea rows={4} maxLength={2000} placeholder="Themes, eligibility, team size, anything students should know…"
              value={v.description} onChange={(e) => set('description', e.target.value)} />
          </label>
        </div>

        {error && <p className="error" role="alert">{error}</p>}

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={busy}>Cancel</button>
          <button type="submit" className="btn btn--primary btn--inline" disabled={busy}>
            {busy ? 'Saving…' : editing ? 'Save changes' : isAdmin ? 'Publish' : 'Submit for review'}
          </button>
        </div>
      </form>
    </div>
  );
}
