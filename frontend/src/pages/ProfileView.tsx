import { useEffect, useState, type FormEvent } from 'react';
import { api, type Profile, type ProfileUpdate } from '../api';
import { initials } from '../profile/FeaturedStrip';
import RatingBadge, { tierOf } from '../profile/RatingBadge';

interface Props {
  userId: number;
  onMessage: (nexconnId: string) => void;
  /** Called after the user edits their own profile (refresh lists / sidebar). */
  onChanged: () => void;
}

export default function ProfileView({ userId, onMessage, onChanged }: Props) {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);

  useEffect(() => {
    setProfile(null);
    setEditing(false);
    setError(null);
    api.profile(userId).then(setProfile).catch((e) => setError((e as Error).message));
  }, [userId]);

  if (error && !profile) return <div className="page"><p className="error">{error}</p></div>;
  if (!profile) return <div className="page"><p className="muted">Loading…</p></div>;

  const updated = (p: Profile) => {
    setProfile(p);
    onChanged();
  };

  const p = profile;
  const subtitle = [p.year, p.branch].filter(Boolean).join(' · ');

  return (
    <div className="page page--narrow">
      <section className="profile-hero card">
        <div className="profile-hero__avatar">{initials(p.displayName)}</div>
        <div className="profile-hero__body">
          <div className="profile-hero__name">
            <h1>{p.displayName}</h1>
            {p.featured && <span className="badge badge--featured">⭐ Featured coder</span>}
            {p.role === 'ADMIN' && <span className="badge badge--official">Admin</span>}
          </div>
          {p.headline && <p className="profile-hero__headline">{p.headline}</p>}
          <p className="muted small">{[subtitle, p.email].filter(Boolean).join(' · ')}</p>
          <div className="profile-hero__links">
            {p.githubUrl && <a className="btn btn--ghost btn--small-ghost" href={p.githubUrl} target="_blank" rel="noreferrer">GitHub ↗</a>}
            {p.linkedinUrl && <a className="btn btn--ghost btn--small-ghost" href={p.linkedinUrl} target="_blank" rel="noreferrer">LinkedIn ↗</a>}
            {p.leetcode?.verified && <a className="btn btn--ghost btn--small-ghost" href={p.leetcode.profileUrl} target="_blank" rel="noreferrer">LeetCode ↗</a>}
          </div>
        </div>
        <div className="profile-hero__cta">
          {p.mine ? (
            !editing && <button className="btn btn--primary btn--inline" onClick={() => setEditing(true)}>Edit profile</button>
          ) : (
            <button className="btn btn--primary btn--inline" onClick={() => onMessage(p.nexconnId)}>
              💬 Message
            </button>
          )}
        </div>
      </section>

      {editing ? (
        <EditForm profile={p} onCancel={() => setEditing(false)} onSaved={(np) => { updated(np); setEditing(false); }} />
      ) : (
        <>
          {(p.bio || p.skills.length > 0 || p.mine) && (
            <section className="card profile-section">
              <h3>About</h3>
              {p.bio ? <p className="profile-bio">{p.bio}</p> : p.mine && <p className="muted">Add a short bio so teammates know what you do.</p>}
              {p.skills.length > 0 && (
                <div className="skill-row">
                  {p.skills.map((s) => <span key={s} className="skill skill--static">{s}</span>)}
                </div>
              )}
            </section>
          )}
        </>
      )}

      {p.mine ? (
        <LeetCodeConnect profile={p} onUpdated={updated} />
      ) : (
        p.leetcode?.verified && <LeetCodeStats profile={p} />
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------

function LeetCodeStats({ profile }: { profile: Profile }) {
  const lc = profile.leetcode!;
  const total = lc.solved ?? 0;
  const bar = (n: number | null, cls: string, label: string) => (
    <div className="lc-bar">
      <span className="lc-bar__label">{label}</span>
      <span className="lc-bar__track"><span className={`lc-bar__fill ${cls}`} style={{ width: `${total ? ((n ?? 0) / total) * 100 : 0}%` }} /></span>
      <span className="lc-bar__num">{n ?? 0}</span>
    </div>
  );
  const toFeatured = lc.rating != null && !profile.featured ? profile.featuredMinRating - lc.rating : null;

  return (
    <div className="lc-stats">
      <div className={`lc-rating lc-rating--${lc.rating != null ? tierOf(lc.rating) : 'none'}`}>
        <span className="lc-rating__label">Contest rating</span>
        {lc.rating != null ? (
          <>
            <span className="lc-rating__num">{lc.rating}</span>
            <span className="lc-rating__sub">
              {lc.badge ?? (tierOf(lc.rating) === 'rated' ? 'Rated' : '')}
              {lc.topPercent != null && ` · Top ${lc.topPercent}%`}
              {lc.contests != null && ` · ${lc.contests} contests`}
            </span>
          </>
        ) : (
          <>
            <span className="lc-rating__num lc-rating__num--none">—</span>
            <span className="lc-rating__sub">No rated contests yet</span>
          </>
        )}
      </div>
      <div className="lc-solved">
        <span className="lc-rating__label">Problems solved: <strong>{total}</strong></span>
        {bar(lc.easy, 'lc-bar__fill--easy', 'Easy')}
        {bar(lc.medium, 'lc-bar__fill--medium', 'Medium')}
        {bar(lc.hard, 'lc-bar__fill--hard', 'Hard')}
      </div>
      {profile.mine && toFeatured != null && toFeatured > 0 && (
        <p className="muted small lc-hint">{toFeatured} more rating points to get featured (needs {profile.featuredMinRating}+).</p>
      )}
      {profile.mine && lc.rating == null && (
        <p className="muted small lc-hint">Take part in a LeetCode weekly contest to get a rating. {profile.featuredMinRating}+ gets you featured.</p>
      )}
    </div>
  );
}

function LeetCodeConnect({ profile, onUpdated }: { profile: Profile; onUpdated: (p: Profile) => void }) {
  const lc = profile.leetcode;
  const [username, setUsername] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  async function run(action: () => Promise<Profile>) {
    setBusy(true);
    setError(null);
    try {
      onUpdated(await action());
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  function copy(code: string) {
    navigator.clipboard?.writeText(code).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    }).catch(() => {});
  }

  return (
    <section className="card profile-section">
      <div className="profile-section__head">
        <h3>LeetCode</h3>
        {lc?.verified && <RatingBadge rating={lc.rating} featured={profile.featured} size="lg" />}
      </div>

      {!lc && (
        <form onSubmit={(e: FormEvent) => { e.preventDefault(); run(() => api.linkLeetcode(username)); }}>
          <p className="muted">
            Link your LeetCode account to show your stats. A verified contest rating of <strong>{profile.featuredMinRating}+</strong> gets
            you <strong>⭐ featured</strong> to everyone looking for teammates.
          </p>
          <div className="inline-form">
            <input className="search" placeholder="LeetCode username or profile link" value={username}
              onChange={(e) => setUsername(e.target.value)} disabled={busy} />
            <button className="btn btn--primary btn--inline" disabled={busy || !username.trim()}>Continue</button>
          </div>
        </form>
      )}

      {lc && !lc.verified && (
        <div className="verify">
          <p>Prove that <strong>{lc.username}</strong> is your account. This stops anyone claiming someone else's rating:</p>
          <ol className="verify__steps">
            <li>
              Copy this code:{' '}
              <button type="button" className="code-chip" onClick={() => copy(lc.verifyCode!)} title="Copy">
                {lc.verifyCode} <span className="muted small">{copied ? '✓ copied' : 'copy'}</span>
              </button>
            </li>
            <li>
              Open <a href="https://leetcode.com/profile/" target="_blank" rel="noreferrer">leetcode.com/profile ↗</a> and paste it
              into <strong>Summary</strong> (your "About me"). Save.
            </li>
            <li>Come back and click Verify. You can remove the code from LeetCode afterwards.</li>
          </ol>
          <div className="inline-form">
            <button className="btn btn--primary btn--inline" disabled={busy} onClick={() => run(api.verifyLeetcode)}>
              {busy ? 'Checking…' : 'Verify'}
            </button>
            <button className="btn btn--ghost" disabled={busy} onClick={() => run(api.unlinkLeetcode)}>Use a different username</button>
          </div>
        </div>
      )}

      {lc?.verified && (
        <>
          <LeetCodeStats profile={profile} />
          <div className="inline-form lc-actions">
            <span className="muted small">
              ✓ Verified as <a href={lc.profileUrl} target="_blank" rel="noreferrer">{lc.username}</a>
              {lc.syncedAt && ` · updated ${new Date(lc.syncedAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}`}
            </span>
            <button className="btn btn--ghost btn--small-ghost" disabled={busy} onClick={() => run(api.refreshLeetcode)}>
              {busy ? 'Refreshing…' : '↻ Refresh'}
            </button>
            <button className="btn btn--ghost btn--small-ghost btn--danger-ghost" disabled={busy}
              onClick={() => window.confirm('Unlink LeetCode? You will stop being featured.') && run(api.unlinkLeetcode)}>
              Unlink
            </button>
          </div>
        </>
      )}

      {error && <p className="error" role="alert">{error}</p>}
    </section>
  );
}

const YEARS = ['1st year', '2nd year', '3rd year', '4th year', 'Masters', 'PhD', 'Alumni'];

function EditForm({ profile, onCancel, onSaved }: { profile: Profile; onCancel: () => void; onSaved: (p: Profile) => void }) {
  const [v, setV] = useState<ProfileUpdate>({
    displayName: profile.displayName,
    headline: profile.headline ?? '',
    bio: profile.bio ?? '',
    year: profile.year ?? '',
    branch: profile.branch ?? '',
    skills: profile.skills,
    githubUrl: profile.githubUrl ?? '',
    linkedinUrl: profile.linkedinUrl ?? '',
  });
  const [skillInput, setSkillInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const set = <K extends keyof ProfileUpdate>(k: K, val: ProfileUpdate[K]) => setV((s) => ({ ...s, [k]: val }));

  const addSkill = (raw: string) => {
    const s = raw.trim();
    if (!s || v.skills.length >= 12 || v.skills.some((x) => x.toLowerCase() === s.toLowerCase())) return;
    set('skills', [...v.skills, s.slice(0, 24)]);
  };

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const skills = skillInput.trim() ? [...v.skills, skillInput.trim()] : v.skills;
      onSaved(await api.updateProfile({ ...v, skills }));
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  }

  return (
    <form className="card profile-section" onSubmit={submit}>
      <h3>Edit profile</h3>
      <div className="form-grid">
        <label className="field">
          <span>Name *</span>
          <input required maxLength={60} value={v.displayName} onChange={(e) => set('displayName', e.target.value)} />
        </label>
        <label className="field">
          <span>Headline</span>
          <input maxLength={80} placeholder="e.g. Full-stack dev · ML enthusiast" value={v.headline}
            onChange={(e) => set('headline', e.target.value)} />
        </label>
        <label className="field">
          <span>Year</span>
          <select value={v.year} onChange={(e) => set('year', e.target.value)}>
            <option value="">—</option>
            {YEARS.map((y) => <option key={y} value={y}>{y}</option>)}
          </select>
        </label>
        <label className="field">
          <span>Branch</span>
          <input maxLength={40} placeholder="e.g. CSE, ECE, IT" value={v.branch} onChange={(e) => set('branch', e.target.value)} />
        </label>
        <div className="field span-2">
          <span>Skills</span>
          <div className="tag-input">
            {v.skills.map((s) => (
              <span key={s} className="skill skill--removable">
                {s}
                <button type="button" aria-label={`Remove ${s}`} onClick={() => set('skills', v.skills.filter((x) => x !== s))}>×</button>
              </span>
            ))}
            <input value={skillInput} placeholder={v.skills.length ? '' : 'Type a skill and press Enter'}
              onChange={(e) => {
                const val = e.target.value;
                if (val.includes(',')) {
                  val.split(',').slice(0, -1).forEach(addSkill);
                  setSkillInput(val.split(',').pop() ?? '');
                } else setSkillInput(val);
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter') { e.preventDefault(); addSkill(skillInput); setSkillInput(''); }
                else if (e.key === 'Backspace' && !skillInput && v.skills.length) set('skills', v.skills.slice(0, -1));
              }} />
          </div>
        </div>
        <label className="field span-2">
          <span>Bio</span>
          <textarea rows={3} maxLength={500} placeholder="What do you build? What are you looking for?" value={v.bio}
            onChange={(e) => set('bio', e.target.value)} />
        </label>
        <label className="field">
          <span>GitHub</span>
          <input placeholder="github.com/you" value={v.githubUrl} onChange={(e) => set('githubUrl', e.target.value)} />
        </label>
        <label className="field">
          <span>LinkedIn</span>
          <input placeholder="linkedin.com/in/you" value={v.linkedinUrl} onChange={(e) => set('linkedinUrl', e.target.value)} />
        </label>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="modal__actions">
        <button type="button" className="btn btn--ghost" onClick={onCancel} disabled={busy}>Cancel</button>
        <button type="submit" className="btn btn--primary btn--inline" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>
  );
}
