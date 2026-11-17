import { useEffect, useState } from 'react';
import { api, type PersonSummary } from '../api';
import RatingBadge from './RatingBadge';

/** "Top coders" row: verified LeetCode ratings above the featured threshold, best first. */
export default function FeaturedStrip({ onOpenProfile, title = '⭐ Top coders at your college' }: {
  onOpenProfile: (userId: number) => void;
  title?: string;
}) {
  const [people, setPeople] = useState<PersonSummary[] | null>(null);

  useEffect(() => {
    api.featured().then(setPeople).catch(() => setPeople([]));
  }, []);

  if (!people || people.length === 0) return null;

  return (
    <section className="featured">
      <div className="featured__head">
        <h3>{title}</h3>
        <span className="muted small">Verified LeetCode contest ratings</span>
      </div>
      <div className="featured__row">
        {people.map((p, i) => (
          <button key={p.userId} className="featured__card" onClick={() => onOpenProfile(p.userId)}>
            <span className="featured__rank">#{i + 1}</span>
            <span className="featured__avatar">{initials(p.displayName)}</span>
            <span className="featured__name">{p.displayName}</span>
            <RatingBadge rating={p.leetcodeRating} />
            {p.skills.length > 0 && <span className="featured__skills">{p.skills.slice(0, 3).join(' · ')}</span>}
          </button>
        ))}
      </div>
    </section>
  );
}

export function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]!.toUpperCase()).join('');
}
