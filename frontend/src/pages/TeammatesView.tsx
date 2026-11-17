import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { api, type Hackathon, type Team, type TeamPost, type TeamPostType } from '../api';
import { phaseOf } from '../hackathons/dates';
import FeaturedStrip from '../profile/FeaturedStrip';
import RatingBadge from '../profile/RatingBadge';

interface Props {
  hackathons: Hackathon[];
  /** Pre-select a hackathon (when coming from a hackathon card). */
  hackathonId: number | null;
  onHackathonChange: (id: number | null) => void;
  onMessage: (post: TeamPost) => void;
  /** Called after create/close/delete so hackathon counts refresh. */
  onChanged: () => void;
  onOpenProfile: (userId: number) => void;
  /** Teams I lead, so I can add a solo student straight from their post. */
  myTeams: Team[];
}

type TypeFilter = 'ALL' | TeamPostType;

const SUGGESTED_SKILLS = ['React', 'Node.js', 'Python', 'ML / AI', 'UI/UX', 'Flutter', 'Java', 'Blockchain', 'DevOps', 'Pitching'];

export default function TeammatesView({ hackathons, hackathonId, onHackathonChange, onMessage, onChanged, onOpenProfile, myTeams }: Props) {
  const [notice, setNotice] = useState<string | null>(null);

  /** An active team I lead for this post's hackathon that still has room. */
  const teamFor = (p: TeamPost) =>
    myTeams.find((t) => t.amLeader && !t.archived && t.hackathon.id === p.hackathon.id
      && t.members.length < t.maxSize && !t.members.some((m) => m.userId === p.author.id));

  async function addToTeam(p: TeamPost, t: Team) {
    setNotice(null);
    await act(() => api.addTeamMember(t.id, p.author.id));
    setNotice(`Added ${p.author.name} to 🔒 ${t.name}.`);
  }
  const [posts, setPosts] = useState<TeamPost[] | null>(null);
  const [type, setType] = useState<TypeFilter>('ALL');
  const [skillQuery, setSkillQuery] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setPosts(await api.teamPosts());
    } catch (e) {
      setError((e as Error).message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // Only live/upcoming approved hackathons can take new posts.
  const postable = useMemo(
    () => hackathons.filter((h) => h.status === 'APPROVED' && phaseOf(h) !== 'past'),
    [hackathons],
  );

  const filtered = useMemo(() => {
    const q = skillQuery.trim().toLowerCase();
    return (posts ?? []).filter(
      (p) =>
        (hackathonId === null || p.hackathon.id === hackathonId) &&
        (type === 'ALL' || p.type === type) &&
        (!q || p.skills.some((s) => s.toLowerCase().includes(q)) || (p.message ?? '').toLowerCase().includes(q)),
    );
  }, [posts, hackathonId, type, skillQuery]);

  const open = filtered.filter((p) => p.open);
  const myClosed = filtered.filter((p) => !p.open && p.mine);

  async function act(action: () => Promise<unknown>) {
    setError(null);
    try {
      await action();
      await load();
      onChanged();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  const selected = hackathons.find((h) => h.id === hackathonId);

  return (
    <div className="page">
      <header className="page__header">
        <div>
          <h1>Find teammates</h1>
          <p className="muted">Need a team, or does your team need people? Post it, then DM anyone who fits.</p>
        </div>
        <button className="btn btn--primary btn--inline" onClick={() => setShowForm(true)} disabled={postable.length === 0}
          title={postable.length === 0 ? 'No upcoming hackathons to post for yet' : undefined}>
          + New post
        </button>
      </header>

      <FeaturedStrip onOpenProfile={onOpenProfile} title="⭐ Top coders you could team up with" />

      <div className="filters">
        <select className="select" value={hackathonId ?? ''} aria-label="Hackathon"
          onChange={(e) => onHackathonChange(e.target.value ? Number(e.target.value) : null)}>
          <option value="">All hackathons</option>
          {hackathons.filter((h) => h.status === 'APPROVED').map((h) => (
            <option key={h.id} value={h.id}>{h.title}</option>
          ))}
        </select>
        <div className="segmented" role="group" aria-label="Post type">
          {([['ALL', 'All'], ['LOOKING_FOR_MEMBERS', 'Teams hiring'], ['LOOKING_FOR_TEAM', 'People free']] as const).map(([k, label]) => (
            <button key={k} className={type === k ? 'seg seg--on' : 'seg'} onClick={() => setType(k)}>{label}</button>
          ))}
        </div>
        <input className="search" type="search" placeholder="Filter by skill, e.g. React" value={skillQuery}
          onChange={(e) => setSkillQuery(e.target.value)} />
      </div>

      {error && <p className="error" role="alert">{error}</p>}
      {notice && <p className="notice">{notice}</p>}

      {posts === null ? (
        <p className="muted">Loading…</p>
      ) : open.length === 0 && myClosed.length === 0 ? (
        <div className="empty">
          <div className="empty__icon">🤝</div>
          <h3>{selected ? `No one's posted for ${selected.title} yet` : 'No teammate posts yet'}</h3>
          <p className="muted">Be the first. Say what you're good at and what you need.</p>
          {postable.length > 0 && (
            <button className="btn btn--primary btn--inline" onClick={() => setShowForm(true)}>+ New post</button>
          )}
        </div>
      ) : (
        <div className="tp-list">
          {open.map((p) => (
            <PostCard key={p.id} p={p} onMessage={onMessage} onSkill={setSkillQuery} onOpenProfile={onOpenProfile}
              addTeam={p.type === 'LOOKING_FOR_TEAM' ? teamFor(p) : undefined}
              onAddToTeam={(t) => addToTeam(p, t)}
              onClose={() => act(() => api.setTeamPostOpen(p.id, false))}
              onDelete={() => window.confirm('Delete this post?') && act(() => api.deleteTeamPost(p.id))} />
          ))}
          {myClosed.length > 0 && <h3 className="tp-subhead">Your filled posts</h3>}
          {myClosed.map((p) => (
            <PostCard key={p.id} p={p} onMessage={onMessage} onSkill={setSkillQuery} onOpenProfile={onOpenProfile}
              onReopen={() => act(() => api.setTeamPostOpen(p.id, true))}
              onDelete={() => window.confirm('Delete this post?') && act(() => api.deleteTeamPost(p.id))} />
          ))}
        </div>
      )}

      {showForm && (
        <TeamPostForm
          hackathons={postable}
          defaultHackathonId={postable.some((h) => h.id === hackathonId) ? hackathonId : null}
          onClose={() => setShowForm(false)}
          onCreated={async (p) => {
            setShowForm(false);
            onHackathonChange(p.hackathon.id);
            await load();
            onChanged();
          }}
        />
      )}
    </div>
  );
}

function PostCard({ p, onMessage, onSkill, onOpenProfile, addTeam, onAddToTeam, onClose, onReopen, onDelete }: {
  p: TeamPost;
  addTeam?: Team;
  onAddToTeam?: (t: Team) => void;
  onMessage: (p: TeamPost) => void;
  onSkill: (s: string) => void;
  onOpenProfile: (userId: number) => void;
  onClose?: () => void;
  onReopen?: () => void;
  onDelete: () => void;
}) {
  const hiring = p.type === 'LOOKING_FOR_MEMBERS';
  return (
    <article className={`tp-card${p.open ? '' : ' tp-card--closed'}`}>
      <button className="tp-card__avatar" onClick={() => onOpenProfile(p.author.id)} title="View profile">
        {initials(p.author.name)}
      </button>
      <div className="tp-card__body">
        <div className="tp-card__head">
          <button className="link tp-card__name" onClick={() => onOpenProfile(p.author.id)}>{p.author.name}</button>
          <RatingBadge rating={p.author.leetcodeRating} featured={p.author.featured} />
          <span className={`badge ${hiring ? 'badge--hiring' : 'badge--free'}`}>
            {hiring ? `Team needs ${p.spotsOpen ?? 1} more` : 'Looking for a team'}
          </span>
          {!p.open && <span className="badge badge--pending">Filled</span>}
          <span className="muted small tp-card__time">{timeAgo(p.createdAt)}</span>
        </div>
        <div className="tp-card__hk muted small">
          for <strong>{p.hackathon.title}</strong>{p.hackathon.source === 'OFFICIAL' && <span className="mini-badge">✓</span>}
        </div>
        {p.skills.length > 0 && (
          <div className="skill-row">
            <span className="muted small">{hiring ? 'Needs:' : 'Skills:'}</span>
            {p.skills.map((s) => (
              <button key={s} className="skill" onClick={() => onSkill(s)} title={`Show posts mentioning ${s}`}>{s}</button>
            ))}
          </div>
        )}
        {p.message && <p className="tp-card__msg">{p.message}</p>}
        <div className="tp-card__actions">
          {!p.mine && p.open && (
            <button className="btn btn--small" onClick={() => onMessage(p)}>💬 Message {p.author.name.split(' ')[0]}</button>
          )}
          {!p.mine && p.open && addTeam && onAddToTeam && (
            <button className="btn btn--small btn--success" onClick={() => onAddToTeam(addTeam)}>
              ➕ Add to 🔒 {addTeam.name}
            </button>
          )}
          {p.mine && p.open && onClose && (
            <button className="btn btn--ghost btn--small-ghost" onClick={onClose}>✓ Mark filled</button>
          )}
          {p.mine && !p.open && onReopen && (
            <button className="btn btn--ghost btn--small-ghost" onClick={onReopen}>Reopen</button>
          )}
          {p.mine && (
            <button className="btn btn--ghost btn--small-ghost btn--danger-ghost" onClick={onDelete}>Delete</button>
          )}
        </div>
      </div>
    </article>
  );
}

function TeamPostForm({ hackathons, defaultHackathonId, onClose, onCreated }: {
  hackathons: Hackathon[];
  defaultHackathonId: number | null;
  onClose: () => void;
  onCreated: (p: TeamPost) => void;
}) {
  const [hackathonId, setHackathonId] = useState<number | ''>(defaultHackathonId ?? hackathons[0]?.id ?? '');
  const [type, setType] = useState<TeamPostType>('LOOKING_FOR_TEAM');
  const [skills, setSkills] = useState<string[]>([]);
  const [skillInput, setSkillInput] = useState('');
  const [message, setMessage] = useState('');
  const [spots, setSpots] = useState(1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && !busy && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [busy, onClose]);

  const addSkill = (raw: string) => {
    const s = raw.trim();
    if (!s || skills.length >= 8 || skills.some((x) => x.toLowerCase() === s.toLowerCase())) return;
    setSkills([...skills, s.slice(0, 24)]);
  };

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (hackathonId === '') return;
    // Include whatever is still typed in the skill box.
    const allSkills = skillInput.trim() ? [...skills, skillInput.trim()] : skills;
    setBusy(true);
    setError(null);
    try {
      onCreated(await api.createTeamPost({
        hackathonId, type, skills: allSkills, message,
        spotsOpen: type === 'LOOKING_FOR_MEMBERS' ? spots : null,
      }));
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  }

  const hiring = type === 'LOOKING_FOR_MEMBERS';

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && !busy && onClose()}>
      <form className="card modal modal--wide" onSubmit={submit} role="dialog" aria-labelledby="tp-title">
        <h2 id="tp-title">New teammate post</h2>

        <div className="choice-row" role="radiogroup" aria-label="Post type">
          <button type="button" role="radio" aria-checked={!hiring}
            className={`choice${!hiring ? ' choice--on' : ''}`} onClick={() => setType('LOOKING_FOR_TEAM')}>
            <span className="choice__icon">🙋</span>
            <span><strong>I need a team</strong><br /><span className="muted small">I'm solo and want to join one</span></span>
          </button>
          <button type="button" role="radio" aria-checked={hiring}
            className={`choice${hiring ? ' choice--on' : ''}`} onClick={() => setType('LOOKING_FOR_MEMBERS')}>
            <span className="choice__icon">👥</span>
            <span><strong>My team needs people</strong><br /><span className="muted small">We have open spots</span></span>
          </button>
        </div>

        <label className="field">
          <span>Hackathon</span>
          <select value={hackathonId} onChange={(e) => setHackathonId(Number(e.target.value))} required>
            {hackathons.map((h) => <option key={h.id} value={h.id}>{h.title}</option>)}
          </select>
        </label>

        {hiring && (
          <label className="field">
            <span>Open spots</span>
            <input type="number" min={1} max={10} value={spots} onChange={(e) => setSpots(Number(e.target.value))} />
          </label>
        )}

        <div className="field">
          <span>{hiring ? 'Skills you need' : 'Your skills'}</span>
          <div className="tag-input">
            {skills.map((s) => (
              <span key={s} className="skill skill--removable">
                {s}
                <button type="button" aria-label={`Remove ${s}`} onClick={() => setSkills(skills.filter((x) => x !== s))}>×</button>
              </span>
            ))}
            <input
              value={skillInput}
              placeholder={skills.length ? '' : 'Type a skill and press Enter'}
              onChange={(e) => {
                const v = e.target.value;
                if (v.includes(',')) {
                  v.split(',').slice(0, -1).forEach(addSkill);
                  setSkillInput(v.split(',').pop() ?? '');
                } else setSkillInput(v);
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  addSkill(skillInput);
                  setSkillInput('');
                } else if (e.key === 'Backspace' && !skillInput && skills.length) {
                  setSkills(skills.slice(0, -1));
                }
              }}
            />
          </div>
          <div className="skill-suggest">
            {SUGGESTED_SKILLS.filter((s) => !skills.some((x) => x.toLowerCase() === s.toLowerCase())).map((s) => (
              <button type="button" key={s} className="skill skill--suggest" onClick={() => addSkill(s)}>+ {s}</button>
            ))}
          </div>
        </div>

        <label className="field">
          <span>Note <span className="muted">(optional)</span></span>
          <textarea rows={3} maxLength={500} value={message} onChange={(e) => setMessage(e.target.value)}
            placeholder={hiring
              ? "e.g. We're building an AI attendance app. Need a frontend dev who knows React."
              : "e.g. 2nd year CSE, built 3 React projects, free all weekend."} />
        </label>

        {error && <p className="error" role="alert">{error}</p>}

        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={busy}>Cancel</button>
          <button type="submit" className="btn btn--primary btn--inline" disabled={busy || hackathonId === ''}>
            {busy ? 'Posting…' : 'Post'}
          </button>
        </div>
      </form>
    </div>
  );
}

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]!.toUpperCase()).join('');
}

function timeAgo(iso: string) {
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return 'just now';
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  return `${Math.floor(s / 86400)}d ago`;
}
