import { useCallback, useEffect, useState } from 'react';
import { api, type Hackathon, type HackathonInput, type UnarchiveRequest } from '../api';
import HackathonCard from '../hackathons/HackathonCard';
import HackathonForm from '../hackathons/HackathonForm';

interface Props {
  /** Called after any change so the rest of the workspace (sidebar, lists) refreshes. */
  onChanged: () => void;
  onOpenChat: (h: Hackathon) => void;
}

type Section = 'pending' | 'official' | 'community' | 'rejected';

export default function AdminView({ onChanged, onOpenChat }: Props) {
  const [all, setAll] = useState<Hackathon[] | null>(null);
  const [section, setSection] = useState<Section>('pending');
  const [editing, setEditing] = useState<Hackathon | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setAll(await api.admin.hackathons());
    } catch (e) {
      setError((e as Error).message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function run(h: Hackathon, action: () => Promise<unknown>) {
    setBusyId(h.id);
    setError(null);
    try {
      await action();
      await load();
      onChanged();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusyId(null);
    }
  }

  const reject = (h: Hackathon) => {
    const reason = window.prompt(`Reject “${h.title}”? Optionally tell ${h.postedBy.name} why:`, '');
    if (reason === null) return;
    run(h, () => api.admin.reject(h.id, reason));
  };

  const remove = (h: Hackathon) => {
    if (!window.confirm(`Delete “${h.title}”? Its chat channel and messages are kept.`)) return;
    run(h, () => api.admin.remove(h.id));
  };

  async function saveEdit(input: HackathonInput) {
    if (!editing) return;
    await api.admin.update(editing.id, input);
    setEditing(null);
    await load();
    onChanged();
  }

  const groups: Record<Section, Hackathon[]> = {
    pending: (all ?? []).filter((h) => h.status === 'PENDING'),
    official: (all ?? []).filter((h) => h.status === 'APPROVED' && h.source === 'OFFICIAL'),
    community: (all ?? []).filter((h) => h.status === 'APPROVED' && h.source === 'COMMUNITY'),
    rejected: (all ?? []).filter((h) => h.status === 'REJECTED'),
  };

  const labels: Record<Section, string> = {
    pending: 'Pending review',
    official: 'Admin-posted (Official)',
    community: 'Student-posted (Community)',
    rejected: 'Rejected',
  };

  const list = groups[section];

  return (
    <div className="page">
      <header className="page__header">
        <div>
          <h1>Admin</h1>
          <p className="muted">Review student posts and manage every hackathon in your workspace.</p>
        </div>
      </header>

      <UnarchiveRequests onChanged={onChanged} />

      <h2 className="section-title">Hackathons</h2>
      <div className="stat-row">
        {(Object.keys(labels) as Section[]).map((s) => (
          <button key={s} className={`stat${section === s ? ' stat--on' : ''}${s === 'pending' && groups.pending.length ? ' stat--alert' : ''}`}
            onClick={() => setSection(s)}>
            <span className="stat__num">{all ? groups[s].length : '–'}</span>
            <span className="stat__label">{labels[s]}</span>
          </button>
        ))}
      </div>

      {error && <p className="error" role="alert">{error}</p>}

      {all === null ? (
        <p className="muted">Loading…</p>
      ) : list.length === 0 ? (
        <div className="empty">
          <div className="empty__icon">{section === 'pending' ? '✅' : '📭'}</div>
          <h3>{section === 'pending' ? 'All caught up' : `No ${labels[section].toLowerCase()} hackathons`}</h3>
          <p className="muted">
            {section === 'pending' ? 'New student posts will show up here for review.' : 'Nothing in this section yet.'}
          </p>
        </div>
      ) : (
        <div className="hk-grid">
          {list.map((h) => {
            const busy = busyId === h.id;
            return (
              <HackathonCard
                key={h.id}
                h={h}
                onOpenChat={onOpenChat}
                actions={
                  <div className="admin-actions">
                    {h.status === 'PENDING' && (
                      <>
                        <button className="btn btn--small btn--success" disabled={busy} onClick={() => run(h, () => api.admin.approve(h.id, false))}>
                          Approve
                        </button>
                        <button className="btn btn--small" disabled={busy} onClick={() => run(h, () => api.admin.approve(h.id, true))}
                          title="Approve and mark as an Official hackathon">
                          Approve as Official
                        </button>
                        <button className="btn btn--small btn--danger" disabled={busy} onClick={() => reject(h)}>Reject</button>
                      </>
                    )}
                    {h.status === 'APPROVED' && h.source === 'COMMUNITY' && (
                      <button className="btn btn--small" disabled={busy} onClick={() => run(h, () => api.admin.approve(h.id, true))}>
                        ✓ Make Official
                      </button>
                    )}
                    {h.status === 'REJECTED' && (
                      <button className="btn btn--small btn--success" disabled={busy} onClick={() => run(h, () => api.admin.approve(h.id, false))}>
                        Approve anyway
                      </button>
                    )}
                    <button className="btn btn--ghost btn--small-ghost" disabled={busy} onClick={() => setEditing(h)}>Edit</button>
                    <button className="btn btn--ghost btn--small-ghost btn--danger-ghost" disabled={busy} onClick={() => remove(h)}>Delete</button>
                  </div>
                }
              />
            );
          })}
        </div>
      )}

      {editing && (
        <HackathonForm initial={editing} isAdmin onSubmit={saveEdit} onClose={() => setEditing(null)} />
      )}
    </div>
  );
}

/** Team members asking to reopen an archived (read-only) team chat. */
function UnarchiveRequests({ onChanged }: { onChanged: () => void }) {
  const [list, setList] = useState<UnarchiveRequest[] | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showHistory, setShowHistory] = useState(false);

  const load = useCallback(async () => {
    try {
      setList(await api.admin.unarchiveRequests());
    } catch (e) {
      setError((e as Error).message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function review(r: UnarchiveRequest, approve: boolean) {
    const note = window.prompt(
      approve ? `Reopen “${r.teamName}” for 14 days? Optional note for the team:` : `Deny reopening “${r.teamName}”? Optional reason:`,
      '',
    );
    if (note === null) return;
    setBusyId(r.id);
    setError(null);
    try {
      await (approve ? api.admin.approveUnarchive(r.id, note) : api.admin.denyUnarchive(r.id, note));
      await load();
      onChanged();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusyId(null);
    }
  }

  if (list === null) return null;
  const pending = list.filter((r) => r.status === 'PENDING');
  const done = list.filter((r) => r.status !== 'PENDING');
  if (pending.length === 0 && done.length === 0) return null;

  const when = (iso: string) => new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });

  return (
    <section className="admin-requests">
      <h2 className="section-title">
        📦 Unarchive requests {pending.length > 0 && <span className="nav-badge nav-badge--inline">{pending.length}</span>}
      </h2>
      {error && <p className="error" role="alert">{error}</p>}
      {pending.length === 0 && <p className="muted small">No pending requests.</p>}
      {pending.map((r) => (
        <div key={r.id} className="req-card">
          <div className="req-card__body">
            <div><strong>🔒 {r.teamName}</strong> <span className="muted small">· {r.hackathonTitle}</span></div>
            <div className="small">
              <strong>{r.requestedBy}</strong> asked {when(r.createdAt)}
              {r.reason ? <>: “{r.reason}”</> : ' (no reason given)'}
            </div>
            <div className="muted small">
              Members: {r.memberNames.join(', ')}{r.archivedAt ? ` · archived ${when(r.archivedAt)}` : ''}
            </div>
          </div>
          <div className="req-card__actions">
            <button className="btn btn--small btn--success" disabled={busyId === r.id} onClick={() => review(r, true)}>
              Reopen for 14 days
            </button>
            <button className="btn btn--small btn--danger" disabled={busyId === r.id} onClick={() => review(r, false)}>
              Deny
            </button>
          </div>
        </div>
      ))}
      {done.length > 0 && (
        <button className="link small" onClick={() => setShowHistory((v) => !v)}>
          {showHistory ? 'Hide' : 'Show'} past decisions ({done.length})
        </button>
      )}
      {showHistory && done.map((r) => (
        <div key={r.id} className="req-card req-card--done">
          <div className="req-card__body small">
            <strong>{r.teamName}</strong> · {r.status === 'APPROVED' ? '✅ reopened' : '❌ denied'} by {r.reviewedBy}
            {r.reviewNote ? ` (“${r.reviewNote}”)` : ''} · asked by {r.requestedBy}
          </div>
        </div>
      ))}
    </section>
  );
}
