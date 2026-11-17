import { useMemo, useState } from 'react';
import type { Hackathon } from '../api';
import HackathonCard from '../hackathons/HackathonCard';
import { phaseOf } from '../hackathons/dates';

interface Props {
  hackathons: Hackathon[];
  isAdmin: boolean;
  onPost: () => void;
  onOpenChat: (h: Hackathon) => void;
  onToggleInterest: (h: Hackathon) => void;
  onFindTeam: (h: Hackathon) => void;
}

type Tab = 'live' | 'past' | 'mine';
type SourceFilter = 'ALL' | 'OFFICIAL' | 'COMMUNITY';

export default function HackathonsView({ hackathons, isAdmin, onPost, onOpenChat, onToggleInterest, onFindTeam }: Props) {
  const [tab, setTab] = useState<Tab>('live');
  const [source, setSource] = useState<SourceFilter>('ALL');

  const approved = hackathons.filter((h) => h.status === 'APPROVED');
  const mine = hackathons.filter((h) => h.mine && !isAdmin);
  const myPending = mine.filter((h) => h.status !== 'APPROVED').length;

  const list = useMemo(() => {
    let l: Hackathon[];
    if (tab === 'mine') l = mine;
    else if (tab === 'past') l = approved.filter((h) => phaseOf(h) === 'past').reverse();
    else l = approved.filter((h) => phaseOf(h) !== 'past');
    if (tab !== 'mine' && source !== 'ALL') l = l.filter((h) => h.source === source);
    // Official first within live list, then by start date (already sorted by the API).
    if (tab === 'live') l = [...l].sort((a, b) => Number(b.source === 'OFFICIAL') - Number(a.source === 'OFFICIAL'));
    return l;
  }, [tab, source, approved, mine]);

  const liveCount = approved.filter((h) => phaseOf(h) !== 'past').length;

  return (
    <div className="page">
      <header className="page__header">
        <div>
          <h1>Hackathons</h1>
          <p className="muted">Every hackathon your college is talking about, in one place.</p>
        </div>
        <button className="btn btn--primary btn--inline" onClick={onPost}>+ Post a hackathon</button>
      </header>

      <div className="toolbar">
        <div className="tabs" role="tablist">
          <button role="tab" aria-selected={tab === 'live'} className={tab === 'live' ? 'tab tab--on' : 'tab'} onClick={() => setTab('live')}>
            Upcoming & live <span className="tab__count">{liveCount}</span>
          </button>
          <button role="tab" aria-selected={tab === 'past'} className={tab === 'past' ? 'tab tab--on' : 'tab'} onClick={() => setTab('past')}>
            Past
          </button>
          {!isAdmin && (
            <button role="tab" aria-selected={tab === 'mine'} className={tab === 'mine' ? 'tab tab--on' : 'tab'} onClick={() => setTab('mine')}>
              My posts {myPending > 0 && <span className="tab__count tab__count--warn">{myPending} pending</span>}
            </button>
          )}
        </div>
        {tab !== 'mine' && (
          <div className="segmented" role="group" aria-label="Filter by source">
            {(['ALL', 'OFFICIAL', 'COMMUNITY'] as const).map((s) => (
              <button key={s} className={source === s ? 'seg seg--on' : 'seg'} onClick={() => setSource(s)}>
                {s === 'ALL' ? 'All' : s === 'OFFICIAL' ? '✓ Official' : 'Community'}
              </button>
            ))}
          </div>
        )}
      </div>

      {list.length === 0 ? (
        <div className="empty">
          <div className="empty__icon">🏁</div>
          <h3>{tab === 'mine' ? "You haven't posted any hackathons" : 'No hackathons here yet'}</h3>
          <p className="muted">
            {isAdmin
              ? 'Post one and it goes live instantly with its own channel.'
              : 'Spotted one on Devfolio, Unstop or a WhatsApp forward? Post it so your whole college sees it.'}
          </p>
          <button className="btn btn--primary btn--inline" onClick={onPost}>+ Post a hackathon</button>
        </div>
      ) : (
        <div className="hk-grid">
          {list.map((h) => (
            <HackathonCard key={h.id} h={h} onOpenChat={onOpenChat} onToggleInterest={onToggleInterest} onFindTeam={onFindTeam} />
          ))}
        </div>
      )}
    </div>
  );
}
