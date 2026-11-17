/** LeetCode-style tiers: Guardian ≥ 2150 (≈ top 5%), Knight ≥ 1850 (≈ top 25%). */
export function tierOf(rating: number): 'guardian' | 'knight' | 'rated' {
  if (rating >= 2150) return 'guardian';
  if (rating >= 1850) return 'knight';
  return 'rated';
}

export default function RatingBadge({ rating, featured, size = 'sm' }: {
  rating: number | null | undefined;
  featured?: boolean;
  size?: 'sm' | 'lg';
}) {
  if (rating == null) return null;
  const tier = tierOf(rating);
  const label = tier === 'guardian' ? 'Guardian' : tier === 'knight' ? 'Knight' : 'LeetCode';
  return (
    <span className={`rating rating--${tier} rating--${size}`} title={`Verified LeetCode contest rating: ${rating}`}>
      {featured && <span aria-label="Featured">⭐</span>}
      <span className="rating__num">{rating}</span>
      {size === 'lg' && <span className="rating__label">{label}</span>}
    </span>
  );
}
