import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { api, ApiError, type Channel, type Hackathon, type HackathonInput, type PersonSummary, type Team } from '../api';
import { phaseOf } from '../hackathons/dates';
import TeamView, { ArchiveStatus } from './TeamView';
import NotificationBell from '../notifications/NotificationBell';
import type { AppNotification } from '../api';
import { useAuth } from '../auth';
import { openDirect, openGroup, startChat, stopChat } from '../chat/nexconn';
import HackathonForm from '../hackathons/HackathonForm';
import AdminView from './AdminView';
import CalendarView from './CalendarView';
import HackathonsView from './HackathonsView';
import TeammatesView from './TeammatesView';
import PeopleView from './PeopleView';
import ProfileView from './ProfileView';

type ChatStatus =
  | { state: 'connecting' }
  | { state: 'ready' }
  | { state: 'error'; message: string; notConfigured: boolean };

type View = 'chat' | 'hackathons' | 'calendar' | 'teammates' | 'people' | 'profile' | 'team' | 'admin';

export default function WorkspacePage() {
  const { user, logout, reloadUser } = useAuth();
  const chatHost = useRef<HTMLDivElement>(null);
  const isAdmin = user?.role === 'ADMIN';

  const [view, setView] = useState<View>('chat');
  const [status, setStatus] = useState<ChatStatus>({ state: 'connecting' });
  const [channels, setChannels] = useState<Channel[]>([]);
  const [people, setPeople] = useState<PersonSummary[]>([]);
  const [profileUserId, setProfileUserId] = useState<number | null>(null);
  const [hackathons, setHackathons] = useState<Hackathon[]>([]);
  const [pendingCount, setPendingCount] = useState(0);
  const [teamHackathonId, setTeamHackathonId] = useState<number | null>(null);
  const [active, setActive] = useState<string | null>(null);
  const [showBrowse, setShowBrowse] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [showPost, setShowPost] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const [sidebarError, setSidebarError] = useState<string | null>(null);
  const [teams, setTeams] = useState<Team[]>([]);
  const [openedTeamId, setOpenedTeamId] = useState<number | null>(null);
  const [showCreateTeam, setShowCreateTeam] = useState(false);
  const [showArchived, setShowArchived] = useState(false);

  const refresh = useCallback(async () => {
    const [c, p, h, t] = await Promise.all([api.channels(), api.profiles(), api.hackathons(), api.teams()]);
    setChannels(c);
    setPeople(p);
    setHackathons(h);
    setTeams(t);
    if (isAdmin) {
      const [all, reqs] = await Promise.all([api.admin.hackathons(), api.admin.unarchiveRequests()]);
      setPendingCount(all.filter((x) => x.status === 'PENDING').length + reqs.filter((r) => r.status === 'PENDING').length);
    }
  }, [isAdmin]);

  // New notification usually means something changed (added to a team, post approved): refresh lists.
  const onNewNotifications = useCallback(() => {
    refresh().catch(() => {});
  }, [refresh]);

  // Connect to Nexconn and mount the Chat UI once. It stays mounted (just hidden) on other views.
  useEffect(() => {
    let cancelled = false;
    let element: HTMLElement | null = null;

    (async () => {
      try {
        const session = await api.chatSession(); // also auto-joins #general
        await startChat(session);
        if (cancelled || !chatHost.current) return;
        element = document.createElement('nc-chat-ui-app-provider');
        element.style.width = '100%';
        element.style.height = '100%';
        chatHost.current.appendChild(element);
        setStatus({ state: 'ready' });
      } catch (e) {
        if (cancelled) return;
        const notConfigured = e instanceof ApiError && e.status === 503;
        setStatus({ state: 'error', message: (e as Error).message, notConfigured });
      } finally {
        if (!cancelled) refresh().catch(() => {});
      }
    })();

    return () => {
      cancelled = true;
      element?.remove();
    };
  }, [refresh]);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 4000);
    return () => clearTimeout(t);
  }, [toast]);

  if (!user) return null;

  const chatReady = status.state === 'ready';
  const joined = channels.filter((c) => c.joined);
  const officialChannels = joined.filter((c) => c.hackathonSource === 'OFFICIAL');
  const communityChannels = joined.filter((c) => c.hackathonSource === 'COMMUNITY');
  const plainChannels = joined.filter((c) => !c.hackathonSource);
  const browsable = channels.filter((c) => !c.joined);
  const others = people.filter((p) => p.userId !== user.id);
  const activeTeams = teams.filter((t) => !t.archived);
  const archivedTeams = teams.filter((t) => t.archived);
  const activeTeam = teams.find((t) => t.channel.nexconnId === active) ?? null;
  const openedTeam = teams.find((t) => t.id === openedTeamId) ?? null;

  /** Jump to whatever a notification is about. */
  async function openNotification(n: AppNotification) {
    switch (n.link) {
      case 'HACKATHON':
        setView('hackathons');
        break;
      case 'TEAMMATES':
        setTeamHackathonId(n.linkId);
        setView('teammates');
        break;
      case 'TEAM':
        await refresh().catch(() => {}); // you may have just been added
        if (n.linkId != null) openTeam(n.linkId);
        break;
      case 'ADMIN':
        if (isAdmin) setView('admin');
        break;
      case 'PROFILE':
        if (n.linkId != null) openProfile(n.linkId);
        break;
    }
  }

  function openTeam(teamId: number) {
    setOpenedTeamId(teamId);
    setView('team');
  }

  async function selectChannel(c: { nexconnId: string }) {
    setView('chat');
    setActive(c.nexconnId);
    await openGroup(c.nexconnId);
  }

  async function joinChannel(c: Channel) {
    setSidebarError(null);
    try {
      const updated = await api.joinChannel(c.id);
      setChannels((all) => all.map((x) => (x.id === updated.id ? updated : x)));
      await selectChannel(updated);
    } catch (e) {
      setSidebarError((e as Error).message);
    }
  }

  async function openHackathonChat(h: Hackathon) {
    if (!h.channel) return;
    const ch = channels.find((c) => c.id === h.channel!.id);
    try {
      if (!ch?.joined) {
        const updated = await api.joinChannel(h.channel.id);
        setChannels((all) => (all.some((x) => x.id === updated.id) ? all.map((x) => (x.id === updated.id ? updated : x)) : [...all, updated]));
      }
      await selectChannel(h.channel);
    } catch (e) {
      setToast((e as Error).message);
    }
  }

  async function toggleInterest(h: Hackathon) {
    try {
      const updated = await api.toggleInterest(h.id);
      setHackathons((all) => all.map((x) => (x.id === updated.id ? updated : x)));
    } catch (e) {
      setToast((e as Error).message);
    }
  }

  async function postHackathon(input: HackathonInput) {
    const created = await api.createHackathon(input);
    setShowPost(false);
    await refresh();
    setToast(
      created.status === 'APPROVED'
        ? `Published! #${created.channel?.name} is ready.`
        : 'Submitted! An admin will review it soon. Track it under “My posts”.',
    );
  }

  function messagePerson(p: { nexconnId: string }) {
    setView('chat');
    setActive(p.nexconnId);
    openDirect(p.nexconnId);
  }

  function openProfile(userId: number) {
    setProfileUserId(userId);
    setView('profile');
  }

  function findTeam(h: Hackathon) {
    setTeamHackathonId(h.id);
    setView('teammates');
  }

  async function handleLogout() {
    await stopChat();
    logout();
    window.location.assign('/login'); // full reload resets the Nexconn SDK singletons
  }

  const channelButton = (c: Channel, icon = '#') => (
    <button
      key={c.id}
      className={`nav-item${view === 'chat' && active === c.nexconnId ? ' nav-item--active' : ''}`}
      disabled={!chatReady}
      onClick={() => selectChannel(c)}
      title={c.description ?? undefined}
    >
      <span className="nav-item__hash">{icon}</span>
      <span className="nav-item__label">{c.name}</span>
    </button>
  );

  return (
    <div className="workspace">
      <aside className="sidebar">
        <div className="sidebar__header">
          <div className="ws-avatar">{user.workspace.name.slice(0, 2)}</div>
          <div className="sidebar__ws">
            <div className="sidebar__ws-name">{user.workspace.name}</div>
            <div className="sidebar__ws-domain">{user.workspace.domain}</div>
          </div>
          <NotificationBell onOpen={openNotification} onNew={onNewNotifications} />
        </div>

        <nav className="sidebar__scroll">
          <div className="main-nav">
            <button className={`nav-item${view === 'hackathons' ? ' nav-item--active' : ''}`} onClick={() => setView('hackathons')}>
              <span className="nav-item__hash">🏆</span><span className="nav-item__label">Hackathons</span>
            </button>
            <button className={`nav-item${view === 'calendar' ? ' nav-item--active' : ''}`} onClick={() => setView('calendar')}>
              <span className="nav-item__hash">📅</span><span className="nav-item__label">Calendar</span>
            </button>
            <button className={`nav-item${view === 'teammates' ? ' nav-item--active' : ''}`}
              onClick={() => { setTeamHackathonId(null); setView('teammates'); }}>
              <span className="nav-item__hash">🤝</span><span className="nav-item__label">Find teammates</span>
            </button>
            <button className={`nav-item${view === 'people' || (view === 'profile' && profileUserId !== user.id) ? ' nav-item--active' : ''}`}
              onClick={() => setView('people')}>
              <span className="nav-item__hash">👥</span><span className="nav-item__label">People</span>
            </button>
            {isAdmin && (
              <button className={`nav-item${view === 'admin' ? ' nav-item--active' : ''}`} onClick={() => setView('admin')}>
                <span className="nav-item__hash">🛡️</span><span className="nav-item__label">Admin</span>
                {pendingCount > 0 && <span className="nav-badge">{pendingCount}</span>}
              </button>
            )}
          </div>

          <section className="nav-section">
            <div className="nav-section__title">
              <span>🔒 My teams</span>
              <button className="icon-btn" title="Create a private team" aria-label="Create a private team"
                disabled={!chatReady} onClick={() => setShowCreateTeam(true)}>
                +
              </button>
            </div>
            {activeTeams.length === 0 && <p className="nav-empty">Formed a team? Create a private channel for it.</p>}
            {activeTeams.map((t) => (
              <button key={t.id}
                className={`nav-item${(view === 'chat' && active === t.channel.nexconnId) || (view === 'team' && openedTeamId === t.id) ? ' nav-item--active' : ''}`}
                disabled={!chatReady} onClick={() => selectChannel(t.channel)} title={`Private · ${t.hackathon.title}`}>
                <span className="nav-item__hash">🔒</span>
                <span className="nav-item__label">{t.name}</span>
              </button>
            ))}
            {archivedTeams.length > 0 && (
              <button className="nav-item nav-item--muted" onClick={() => setShowArchived((v) => !v)}>
                <span className="nav-item__hash">{showArchived ? '▾' : '▸'}</span>
                <span className="nav-item__label">📦 Archived ({archivedTeams.length})</span>
              </button>
            )}
            {showArchived && archivedTeams.map((t) => (
              <button key={t.id}
                className={`nav-item nav-item--archived${view === 'chat' && active === t.channel.nexconnId ? ' nav-item--active' : ''}`}
                disabled={!chatReady} onClick={() => selectChannel(t.channel)} title="Read-only archive">
                <span className="nav-item__hash">📦</span>
                <span className="nav-item__label">{t.name}</span>
              </button>
            ))}
          </section>

          {officialChannels.length > 0 && (
            <section className="nav-section">
              <div className="nav-section__title"><span>✓ Official hackathons</span></div>
              {officialChannels.map((c) => channelButton(c))}
            </section>
          )}

          {communityChannels.length > 0 && (
            <section className="nav-section">
              <div className="nav-section__title"><span>Community hackathons</span></div>
              {communityChannels.map((c) => channelButton(c))}
            </section>
          )}

          <section className="nav-section">
            <div className="nav-section__title">
              <span>Channels</span>
              {isAdmin && (
                <button className="icon-btn" title="Create a public channel" aria-label="Create a public channel"
                  disabled={!chatReady} onClick={() => setShowCreate(true)}>
                  +
                </button>
              )}
            </div>
            {plainChannels.map((c) => channelButton(c))}
            {browsable.length > 0 && (
              <button className="nav-item nav-item--muted" onClick={() => setShowBrowse((v) => !v)}>
                <span className="nav-item__hash">{showBrowse ? '▾' : '▸'}</span>
                <span className="nav-item__label">Browse {browsable.length} more</span>
              </button>
            )}
            {showBrowse &&
              browsable.map((c) => (
                <div key={c.id} className="browse-item">
                  <div className="browse-item__text">
                    <div className="browse-item__name">
                      #{c.name}
                      {c.hackathonSource === 'OFFICIAL' && <span className="mini-badge">✓</span>}
                    </div>
                    <div className="browse-item__meta">
                      {c.memberCount} member{c.memberCount === 1 ? '' : 's'}
                      {c.description ? ` · ${c.description}` : ''}
                    </div>
                  </div>
                  <button className="btn btn--small" disabled={!chatReady} onClick={() => joinChannel(c)}>Join</button>
                </div>
              ))}
            {sidebarError && <p className="error small sidebar__error">{sidebarError}</p>}
          </section>

          <section className="nav-section">
            <div className="nav-section__title">
              <span>Direct messages</span>
              <span className="count">{others.length}</span>
            </div>
            {others.length === 0 && (
              <p className="nav-empty">No one else yet. Invite classmates from {user.workspace.domain}!</p>
            )}
            {others.map((p) => (
              <button key={p.userId}
                className={`nav-item${view === 'chat' && active === p.nexconnId ? ' nav-item--active' : ''}`}
                disabled={!chatReady} onClick={() => messagePerson(p)}
                title={p.featured ? `⭐ Featured · LeetCode ${p.leetcodeRating}` : p.email}>
                <span className="person-avatar">{initials(p.displayName)}</span>
                <span className="nav-item__label">{p.displayName}</span>
                {p.featured && <span className="nav-star" aria-label="Featured">⭐</span>}
              </button>
            ))}
          </section>
        </nav>

        <div className="sidebar__footer">
          <button className="sidebar__me-btn" onClick={() => openProfile(user.id)} title="View your profile">
            <span className="person-avatar person-avatar--me">{initials(user.displayName)}</span>
            <span className="sidebar__me">
              <span className="sidebar__me-name">{user.displayName}</span>
              <span className="sidebar__me-role">{isAdmin ? 'Workspace admin' : 'Member'} · View profile</span>
            </span>
          </button>
          <button className="icon-btn" title="Log out" aria-label="Log out" onClick={handleLogout}>⎋</button>
        </div>
      </aside>

      <main className="main">
        {/* Chat stays mounted so the Nexconn connection and UI state survive view switches. */}
        <div className="chat" hidden={view !== 'chat'}>
          {activeTeam && (
            <div className={`team-bar${activeTeam.archived ? ' team-bar--archived' : ''}`}>
              <span className="team-bar__name">🔒 {activeTeam.name}</span>
              <span className="team-bar__meta">
                {activeTeam.hackathon.title} · {activeTeam.members.length}/{activeTeam.maxSize}
              </span>
              <span className="team-bar__status"><ArchiveStatus team={activeTeam} /></span>
              {activeTeam.archived && !activeTeam.pendingRequest && (
                <button className="btn btn--small" onClick={() => openTeam(activeTeam.id)}>Request unarchive</button>
              )}
              {activeTeam.archived && activeTeam.pendingRequest && <span className="small">⏳ Unarchive requested</span>}
              <button className="btn btn--ghost btn--small-ghost" onClick={() => openTeam(activeTeam.id)}>Manage team</button>
            </div>
          )}
          <div ref={chatHost} className="chat__host" />
          {status.state === 'connecting' && <div className="chat__overlay">Connecting to chat…</div>}
          {status.state === 'error' && (
            <div className="chat__overlay">
              <div className="card chat__error">
                <h2>{status.notConfigured ? 'Chat needs setting up' : 'Chat is unavailable'}</h2>
                <p className="muted">{status.message}</p>
                {status.notConfigured && (
                  <ol className="setup-steps">
                    <li>Create an app in the Nexconn Console and copy its App Key and App Secret.</li>
                    <li>Put them in <code>secrets.properties</code> in the backend folder.</li>
                    <li>Restart the Spring Boot app and reload this page.</li>
                  </ol>
                )}
                <button className="btn btn--primary" onClick={() => window.location.reload()}>Retry</button>
              </div>
            </div>
          )}
        </div>

        {view === 'hackathons' && (
          <HackathonsView hackathons={hackathons} isAdmin={isAdmin} onPost={() => setShowPost(true)}
            onOpenChat={openHackathonChat} onToggleInterest={toggleInterest} onFindTeam={findTeam} />
        )}
        {view === 'calendar' && (
          <CalendarView hackathons={hackathons} onOpenChat={openHackathonChat} onToggleInterest={toggleInterest}
            onFindTeam={findTeam} />
        )}
        {view === 'teammates' && (
          <TeammatesView hackathons={hackathons} hackathonId={teamHackathonId} onHackathonChange={setTeamHackathonId}
            onMessage={(post) => messagePerson(post.author)} onChanged={() => refresh().catch(() => {})}
            onOpenProfile={openProfile} myTeams={teams} />
        )}
        {view === 'people' && <PeopleView people={people} meId={user.id} onOpenProfile={openProfile} />}
        {view === 'profile' && profileUserId !== null && (
          <ProfileView userId={profileUserId} onMessage={(nexconnId) => messagePerson({ nexconnId })}
            onChanged={() => {
              refresh().catch(() => {});
              reloadUser().catch(() => {});
            }} />
        )}
        {view === 'admin' && isAdmin && (
          <AdminView onChanged={() => refresh().catch(() => {})} onOpenChat={openHackathonChat} />
        )}
        {view === 'team' && openedTeam && (
          <TeamView team={openedTeam} meId={user.id} people={people}
            onOpenChat={(t) => selectChannel(t.channel)} onOpenProfile={openProfile}
            onMessage={(nexconnId) => messagePerson({ nexconnId })}
            onChanged={(left) => {
              refresh().catch(() => {});
              if (left) setView('chat');
            }} />
        )}
      </main>

      {showCreate && (
        <CreateChannelDialog
          onClose={() => setShowCreate(false)}
          onCreated={async (c) => {
            setShowCreate(false);
            setChannels((all) => [...all, c]);
            await selectChannel(c);
          }}
        />
      )}

      {showPost && <HackathonForm isAdmin={isAdmin} onSubmit={postHackathon} onClose={() => setShowPost(false)} />}

      {showCreateTeam && (
        <CreateTeamDialog
          hackathons={hackathons.filter((h) => h.status === 'APPROVED' && phaseOf(h) !== 'past'
            && !teams.some((t) => t.hackathon.id === h.id))}
          onClose={() => setShowCreateTeam(false)}
          onCreated={async (t) => {
            setShowCreateTeam(false);
            await refresh();
            openTeam(t.id);
            setToast(`🔒 ${t.name} created. Add your teammates.`);
          }}
        />
      )}

      {toast && <div className="toast" role="status">{toast}</div>}
    </div>
  );
}

function CreateChannelDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (c: Channel) => void }) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const preview = name.trim().toLowerCase().replace(/^#/, '').replace(/[\s_]+/g, '-').replace(/[^a-z0-9-]/g, '');

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onCreated(await api.createChannel(name, description));
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <form className="card modal" onSubmit={submit} role="dialog" aria-labelledby="create-title">
        <h2 id="create-title">Create a channel</h2>
        <p className="muted small">For topics like a society, a course or a study group. Hackathons get their own channel automatically.</p>
        <label className="field">
          <span>Name</span>
          <input autoFocus placeholder="e.g. dsa-prep" value={name} maxLength={40}
            onChange={(e) => setName(e.target.value)} disabled={busy} />
        </label>
        {preview && preview !== name && <p className="muted small field-hint">Will be created as #{preview}</p>}
        <label className="field">
          <span>Description <span className="muted">(optional)</span></span>
          <input placeholder="What's this channel about?" value={description} maxLength={280}
            onChange={(e) => setDescription(e.target.value)} disabled={busy} />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={busy}>Cancel</button>
          <button type="submit" className="btn btn--primary btn--inline" disabled={busy || preview.length < 2}>
            {busy ? 'Creating…' : 'Create'}
          </button>
        </div>
      </form>
    </div>
  );
}

function CreateTeamDialog({ hackathons, onClose, onCreated }: {
  hackathons: Hackathon[];
  onClose: () => void;
  onCreated: (t: Team) => void;
}) {
  const [hackathonId, setHackathonId] = useState<number | ''>(hackathons[0]?.id ?? '');
  const [name, setName] = useState('');
  const [size, setSize] = useState(4);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (hackathonId === '') return;
    setBusy(true);
    setError(null);
    try {
      onCreated(await api.createTeam(hackathonId, name, size));
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <form className="card modal" onSubmit={submit} role="dialog" aria-labelledby="team-title">
        <h2 id="team-title">🔒 Create a private team</h2>
        <p className="muted small">
          Only members you add can see this channel. It becomes a read-only archive a week after the hackathon ends. An
          admin can reopen it if you need it.
        </p>
        {hackathons.length === 0 ? (
          <p className="notice">You're already in a team for every upcoming hackathon, or none are listed yet.</p>
        ) : (
          <>
            <label className="field">
              <span>Hackathon</span>
              <select value={hackathonId} onChange={(e) => setHackathonId(Number(e.target.value))}>
                {hackathons.map((h) => <option key={h.id} value={h.id}>{h.title}</option>)}
              </select>
            </label>
            <label className="field">
              <span>Team name</span>
              <input autoFocus required maxLength={40} placeholder="e.g. Byte Busters" value={name}
                onChange={(e) => setName(e.target.value)} disabled={busy} />
            </label>
            <label className="field">
              <span>Max team size</span>
              <input type="number" min={2} max={8} value={size} onChange={(e) => setSize(Number(e.target.value))} />
            </label>
          </>
        )}
        {error && <p className="error" role="alert">{error}</p>}
        <div className="modal__actions">
          <button type="button" className="btn btn--ghost" onClick={onClose} disabled={busy}>Cancel</button>
          <button type="submit" className="btn btn--primary btn--inline"
            disabled={busy || hackathonId === '' || name.trim().length < 2}>
            {busy ? 'Creating…' : 'Create team'}
          </button>
        </div>
      </form>
    </div>
  );
}

function initials(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]!.toUpperCase()).join('');
}
