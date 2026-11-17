import { useMemo, useState } from 'react';
import type { Hackathon } from '../api';
import HackathonCard from '../hackathons/HackathonCard';
import { parseDay, toIsoDay, today } from '../hackathons/dates';

interface Props {
  hackathons: Hackathon[];
  onOpenChat: (h: Hackathon) => void;
  onToggleInterest: (h: Hackathon) => void;
  onFindTeam: (h: Hackathon) => void;
}

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const monthFmt = new Intl.DateTimeFormat('en-IN', { month: 'long', year: 'numeric' });

/** Month grid (Mon-first) showing each hackathon across its dates, plus ⏰ on registration deadlines. */
export default function CalendarView({ hackathons, onOpenChat, onToggleInterest, onFindTeam }: Props) {
  const t = today();
  const [month, setMonth] = useState(new Date(t.getFullYear(), t.getMonth(), 1));
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const approved = useMemo(() => hackathons.filter((h) => h.status === 'APPROVED'), [hackathons]);
  const selected = approved.find((h) => h.id === selectedId) ?? null;

  const cells = useMemo(() => {
    const first = new Date(month.getFullYear(), month.getMonth(), 1);
    const offset = (first.getDay() + 6) % 7; // Monday = 0
    const start = new Date(first.getFullYear(), first.getMonth(), 1 - offset);
    return Array.from({ length: 42 }, (_, i) => new Date(start.getFullYear(), start.getMonth(), start.getDate() + i));
  }, [month]);

  const eventsOn = (d: Date) => {
    const iso = toIsoDay(d);
    return {
      running: approved.filter((h) => h.startDate <= iso && iso <= h.endDate),
      deadlines: approved.filter((h) => h.registrationDeadline === iso),
    };
  };

  const shift = (n: number) => setMonth((m) => new Date(m.getFullYear(), m.getMonth() + n, 1));

  // Upcoming deadlines list beside/below the grid.
  const upcomingDeadlines = approved
    .filter((h) => h.registrationDeadline && parseDay(h.registrationDeadline) >= t)
    .sort((a, b) => a.registrationDeadline!.localeCompare(b.registrationDeadline!))
    .slice(0, 6);

  return (
    <div className="page">
      <header className="page__header">
        <div>
          <h1>Calendar</h1>
          <p className="muted">Hackathon dates and registration deadlines. Click one for details.</p>
        </div>
        <div className="cal-nav">
          <button className="btn btn--ghost" onClick={() => shift(-1)} aria-label="Previous month">‹</button>
          <button className="btn btn--ghost" onClick={() => setMonth(new Date(t.getFullYear(), t.getMonth(), 1))}>Today</button>
          <button className="btn btn--ghost" onClick={() => shift(1)} aria-label="Next month">›</button>
        </div>
      </header>

      <div className="cal-layout">
        <div className="cal">
          <div className="cal__title">{monthFmt.format(month)}</div>
          <div className="cal__grid">
            {WEEKDAYS.map((w) => <div key={w} className="cal__weekday">{w}</div>)}
            {cells.map((d) => {
              const { running, deadlines } = eventsOn(d);
              const outside = d.getMonth() !== month.getMonth();
              const isToday = d.getTime() === t.getTime();
              return (
                <div key={d.toISOString()} className={`cal__cell${outside ? ' cal__cell--outside' : ''}${isToday ? ' cal__cell--today' : ''}`}>
                  <div className="cal__day">{d.getDate()}</div>
                  {deadlines.map((h) => (
                    <button key={`d${h.id}`} className="cal__pill cal__pill--deadline" onClick={() => setSelectedId(h.id)}
                      title={`Registration deadline: ${h.title}`}>
                      ⏰ {h.title}
                    </button>
                  ))}
                  {running.map((h) => (
                    <button key={h.id}
                      className={`cal__pill ${h.source === 'OFFICIAL' ? 'cal__pill--official' : 'cal__pill--community'}${selectedId === h.id ? ' cal__pill--selected' : ''}`}
                      onClick={() => setSelectedId(h.id)} title={h.title}>
                      {h.title}
                    </button>
                  ))}
                </div>
              );
            })}
          </div>
          <div className="cal__legend">
            <span><i className="dot dot--official" /> Official</span>
            <span><i className="dot dot--community" /> Community</span>
            <span>⏰ Registration deadline</span>
          </div>
        </div>

        <aside className="cal-side">
          {selected ? (
            <>
              <button className="link small" onClick={() => setSelectedId(null)}>← Upcoming deadlines</button>
              <HackathonCard h={selected} onOpenChat={onOpenChat} onToggleInterest={onToggleInterest} onFindTeam={onFindTeam} />
            </>
          ) : (
            <div className="card cal-deadlines">
              <h3>Upcoming deadlines</h3>
              {upcomingDeadlines.length === 0 ? (
                <p className="muted small">No upcoming registration deadlines.</p>
              ) : (
                <ul>
                  {upcomingDeadlines.map((h) => (
                    <li key={h.id}>
                      <button className="link" onClick={() => setSelectedId(h.id)}>{h.title}</button>
                      <span className="muted small">
                        {new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short' }).format(parseDay(h.registrationDeadline!))}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </aside>
      </div>
    </div>
  );
}
