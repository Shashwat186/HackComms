import { useMemo, useState } from 'react';
import { api, type PersonSummary, type Team } from '../api';
import { formatDay } from '../hackathons/dates';
import { initials } from '../profile/FeaturedStrip';
import RatingBadge from '../profile/RatingBadge';

interface Props {
  team: Team;
  meId: number;
  people: PersonSummary[];
  onOpenChat: (team: Team) => void;
  onOpenProfile: (userId: number) => void;
  onMessage: (nexconnId: string) => void;
  /** Re-fetch teams after a change; `left` = I'm no longer a member. */
  onChanged: (left?: boolean) => void;
}

export default function TeamView({ team, meId, people, onOpenChat, onOpenProfile, onMessage, onChanged }: Props) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [query, setQuery] = useState('');

  const full = team.members.length >= team.maxSize;
  const candidates = useMemo(() => {
    const memberIds = new Set(team.members.map((m) => m.userId));
    const q = query.trim().toLowerCase();
    return people
      .filter((p) => !memberIds.has(p.userId))
      .filter((p) => !q || [p.displayName, ...p.skills].some((x) => x.toLowerCase().includes(q)))
      .slice(0, 8);
  }, [people, query, team.members]);

  async function run(action: () => Promise<unknown>, left = false) {
    setBusy(true);
    setError(null);
    try {
      await action();
      onChanged(left);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  function requestUnarchive() {
    const reason = window.prompt('Why should the admin reopen this team chat? (e.g. selected for finals)', '');
    if (reason === null) return;
    run(() => api.requestUnarchive(team.id, reason));
  }

  return (
    <div className="page page--narrow">
      <section className="card team-hero">
        <div className="team-hero__icon">🔒</div>
        <div className="team-hero__body">
          <h1>{team.name}</h1>
          <p className="muted">
            Private team for <strong>{team.hackathon.title}</strong> · {team.members.length}/{team.maxSize} members
          </p>
          <ArchiveStatus team={team} />
        </div>
        <button className="btn btn--primary btn--inline" onClick={() => onOpenChat(team)}>💬 Open team chat</button>
      </section>

      {team.archived && (
        <section className="card archive-card">
          <h3>📦 This team chat is archived</h3>
          <p className="muted">
            The history is still here, but nobody can send messages. Need it again, say you made the finals? Ask the
            workspace admin to reopen it.
          </p>
          {team.pendingRequest ? (
            <p className="notice">
              ⏳ {team.pendingRequest.requestedBy} asked the admin to reopen it
              {team.pendingRequest.reason ? `: “${team.pendingRequest.reason}”` : ''}. Waiting for a decision.
            </p>
          ) : (
            <button className="btn btn--primary btn--inline" disabled={busy} onClick={requestUnarchive}>
              Request unarchive
            </button>
          )}
        </section>
      )}

      <section className="card profile-section">
        <div className="profile-section__head">
          <h3>Members</h3>
          {team.amLeader && !team.archived && !full && !adding && (
            <button className="btn btn--small" onClick={() => setAdding(true)}>+ Add member</button>
          )}
          {team.amLeader && full && !team.archived && <span className="muted small">Team is full</span>}
        </div>

        {adding && (
          <div className="add-member">
            <input className="search" autoFocus placeholder="Search classmates by name or skill…" value={query}
              onChange={(e) => setQuery(e.target.value)} />
            <div className="add-member__list">
              {candidates.length === 0 && <p className="muted small">No one matches.</p>}
              {candidates.map((p) => (
                <div key={p.userId} className="member-row">
                  <span className="member-row__avatar">{initials(p.displayName)}</span>
                  <span className="member-row__name">{p.displayName}
                    {p.skills.length > 0 && <span className="muted small"> · {p.skills.slice(0, 3).join(', ')}</span>}
                  </span>
                  <RatingBadge rating={p.leetcodeRating} featured={p.featured} />
                  <button className="btn btn--small" disabled={busy}
                    onClick={() => run(() => api.addTeamMember(team.id, p.userId)).then(() => setQuery(''))}>
                    Add
                  </button>
                </div>
              ))}
            </div>
            <button className="link small" onClick={() => setAdding(false)}>Done</button>
          </div>
        )}

        <div className="member-list">
          {team.members.map((m) => (
            <div key={m.userId} className="member-row">
              <button className="member-row__avatar" onClick={() => onOpenProfile(m.userId)}>{initials(m.displayName)}</button>
              <button className="link member-row__name" onClick={() => onOpenProfile(m.userId)}>
                {m.displayName}
                {m.userId === team.leaderId && <span className="badge badge--official">Leader</span>}
                {m.userId === meId && <span className="muted small"> (you)</span>}
              </button>
              <RatingBadge rating={m.leetcodeRating} featured={m.featured} />
              {m.userId !== meId && (
                <button className="btn btn--ghost btn--small-ghost" onClick={() => onMessage(m.nexconnId)}>💬</button>
              )}
              {team.amLeader && m.userId !== meId && (
                <button className="btn btn--ghost btn--small-ghost btn--danger-ghost" disabled={busy}
                  onClick={() => window.confirm(`Remove ${m.displayName} from the team?`) && run(() => api.removeTeamMember(team.id, m.userId))}>
                  Remove
                </button>
              )}
            </div>
          ))}
        </div>
      </section>

      <section className="card profile-section">
        <h3>Team settings</h3>
        <div className="inline-form">
          {team.amLeader && !team.archived && (
            <button className="btn btn--ghost" disabled={busy}
              onClick={() => window.confirm('Archive now? The chat becomes read-only for everyone. Only the admin can reopen it.')
                && run(() => api.archiveTeam(team.id))}>
              📦 Archive now (results are out)
            </button>
          )}
          {!team.amLeader && (
            <button className="btn btn--ghost btn--danger-ghost" disabled={busy}
              onClick={() => window.confirm(`Leave ${team.name}? You'll lose access to the team chat.`)
                && run(() => api.removeTeamMember(team.id, meId), true)}>
              Leave team
            </button>
          )}
        </div>
        {team.amLeader && <p className="muted small">As the leader you can't leave the team. Archive it when you're done.</p>}
      </section>

      {error && <p className="error" role="alert">{error}</p>}
    </div>
  );
}

export function ArchiveStatus({ team }: { team: Team }) {
  if (team.archived) {
    return (
      <p className="small archive-status archive-status--archived">
        📦 Read-only since {team.archivedAt ? new Date(team.archivedAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }) : 'recently'}
      </p>
    );
  }
  return <p className="small archive-status">Becomes a read-only archive on {formatDay(team.archivesOn, true)}</p>;
}
