import { useMemo, useState } from 'react';
import type { PersonSummary } from '../api';
import FeaturedStrip, { initials } from '../profile/FeaturedStrip';
import RatingBadge from '../profile/RatingBadge';

interface Props {
  people: PersonSummary[];
  meId: number;
  onOpenProfile: (userId: number) => void;
}

export default function PeopleView({ people, meId, onOpenProfile }: Props) {
  const [q, setQ] = useState('');

  const filtered = useMemo(() => {
    const s = q.trim().toLowerCase();
    if (!s) return people;
    return people.filter((p) =>
      [p.displayName, p.headline, p.branch, p.year, ...p.skills].some((x) => x?.toLowerCase().includes(s)),
    );
  }, [people, q]);

  return (
    <div className="page">
      <header className="page__header">
        <div>
          <h1>People</h1>
          <p className="muted">Everyone in your college workspace. Link a verified LeetCode account to get featured.</p>
        </div>
        <button className="btn btn--primary btn--inline" onClick={() => onOpenProfile(meId)}>Edit my profile</button>
      </header>

      <FeaturedStrip onOpenProfile={onOpenProfile} />

      <div className="filters">
        <input className="search" type="search" placeholder="Search by name, skill or branch…" value={q}
          onChange={(e) => setQ(e.target.value)} />
        <span className="muted small">{filtered.length} {filtered.length === 1 ? 'student' : 'students'}</span>
      </div>

      <div className="people-grid">
        {filtered.map((p) => (
          <button key={p.userId} className="person-card" onClick={() => onOpenProfile(p.userId)}>
            <span className="person-card__avatar">{initials(p.displayName)}</span>
            <span className="person-card__body">
              <span className="person-card__name">
                {p.displayName}
                {p.userId === meId && <span className="muted small"> (you)</span>}
              </span>
              <span className="person-card__meta">
                {[p.headline, [p.year, p.branch].filter(Boolean).join(' · ')].filter(Boolean).join(' — ') || 'No headline yet'}
              </span>
              {p.skills.length > 0 && (
                <span className="person-card__skills">
                  {p.skills.slice(0, 4).map((s) => <span key={s} className="skill skill--static">{s}</span>)}
                </span>
              )}
            </span>
            <RatingBadge rating={p.leetcodeRating} featured={p.featured} />
          </button>
        ))}
      </div>
    </div>
  );
}
