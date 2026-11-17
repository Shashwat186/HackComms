import type { ReactNode } from 'react';
import type { Hackathon } from '../api';
import { deadlineLabel, downloadIcs, formatRange, googleCalendarUrl, phaseOf, startsLabel } from './dates';

interface Props {
  h: Hackathon;
  onOpenChat?: (h: Hackathon) => void;
  onToggleInterest?: (h: Hackathon) => void;
  onFindTeam?: (h: Hackathon) => void;
  /** Extra controls (admin actions). */
  actions?: ReactNode;
}

const MODE_LABEL = { ONLINE: '🌐 Online', OFFLINE: '📍 In person', HYBRID: '🔀 Hybrid' } as const;

export function SourceBadge({ h }: { h: Hackathon }) {
  return h.source === 'OFFICIAL' ? (
    <span className="badge badge--official" title="Posted or verified by a workspace admin">✓ Official</span>
  ) : (
    <span className="badge badge--community" title="Posted by a student">Community</span>
  );
}

export function StatusBadge({ h }: { h: Hackathon }) {
  if (h.status === 'PENDING') return <span className="badge badge--pending">Pending review</span>;
  if (h.status === 'REJECTED') return <span className="badge badge--rejected">Not approved</span>;
  return null;
}

export default function HackathonCard({ h, onOpenChat, onToggleInterest, onFindTeam, actions }: Props) {
  const deadline = deadlineLabel(h);
  const phase = phaseOf(h);
  const live = h.status === 'APPROVED';

  return (
    <article className={`hk-card${phase === 'past' ? ' hk-card--past' : ''}`}>
      <div className="hk-card__top">
        <SourceBadge h={h} />
        <StatusBadge h={h} />
        <span className={`hk-card__when hk-card__when--${phase}`}>{startsLabel(h)}</span>
      </div>

      <h3 className="hk-card__title">{h.title}</h3>
      {h.organizer && <div className="hk-card__org">by {h.organizer}</div>}

      <div className="hk-card__facts">
        <span>📅 {formatRange(h.startDate, h.endDate)}</span>
        <span>{MODE_LABEL[h.mode]}{h.location ? ` · ${h.location}` : ''}</span>
        {h.prizePool && <span>🏆 {h.prizePool}</span>}
      </div>

      {deadline && live && phase !== 'past' && (
        <div className={`deadline${deadline.urgent ? ' deadline--urgent' : ''}${deadline.closed ? ' deadline--closed' : ''}`}>
          ⏰ {deadline.text}
        </div>
      )}

      {h.description && <p className="hk-card__desc">{h.description}</p>}

      {h.status === 'REJECTED' && h.mine && (
        <p className="hk-card__reject">
          An admin didn't approve this{h.rejectionReason ? `: “${h.rejectionReason}”` : '.'}
        </p>
      )}
      {h.status === 'PENDING' && h.mine && (
        <p className="muted small">Only you and the admins can see this until it's approved.</p>
      )}

      <div className="hk-card__footer">
        <span className="muted small">
          {h.source === 'COMMUNITY' ? `Posted by ${h.postedBy.name}` : ''}
        </span>
        {live && onToggleInterest && (
          <button
            className={`chip-btn${h.interested ? ' chip-btn--on' : ''}`}
            onClick={() => onToggleInterest(h)}
            aria-pressed={h.interested}
          >
            {h.interested ? '★' : '☆'} {h.interestedCount} interested
          </button>
        )}
      </div>

      <div className="hk-card__actions">
        {live && h.channel && onOpenChat && (
          <button className="btn btn--small" onClick={() => onOpenChat(h)}>💬 #{h.channel.name}</button>
        )}
        {live && phase !== 'past' && onFindTeam && (
          <button className="btn btn--ghost btn--small-ghost" onClick={() => onFindTeam(h)}
            title="Find teammates for this hackathon">
            🤝 {h.openTeamPosts > 0 ? `${h.openTeamPosts} looking` : 'Find team'}
          </button>
        )}
        {h.websiteUrl && (
          <a className="btn btn--ghost btn--small-ghost" href={h.websiteUrl} target="_blank" rel="noreferrer">
            Website ↗
          </a>
        )}
        {live && (
          <>
            <a className="btn btn--ghost btn--small-ghost" href={googleCalendarUrl(h)} target="_blank" rel="noreferrer"
              title="Add to Google Calendar">
              + Google Cal
            </a>
            <button className="btn btn--ghost btn--small-ghost" onClick={() => downloadIcs(h)}
              title="Download .ics for Apple/Outlook calendar">
              .ics
            </button>
          </>
        )}
        {actions}
      </div>
    </article>
  );
}
