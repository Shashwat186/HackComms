import { useCallback, useEffect, useRef, useState } from 'react';
import { api, type AppNotification } from '../api';

const POLL_MS = 30_000;

interface Props {
  /** Navigate to what a notification points at. */
  onOpen: (n: AppNotification) => void;
  /** Fired when new notifications arrive, so the workspace can refresh teams/lists. */
  onNew: () => void;
}

/** 🔔 with an unread badge; opens a slide-out panel. Polls a cheap count endpoint every 30 s. */
export default function NotificationBell({ onOpen, onNew }: Props) {
  const [unread, setUnread] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<AppNotification[] | null>(null);
  const lastUnread = useRef(0);

  const poll = useCallback(async () => {
    try {
      const { unread: n } = await api.unreadCount();
      if (n > lastUnread.current) onNew();
      lastUnread.current = n;
      setUnread(n);
    } catch {
      /* offline or logged out: try again next tick */
    }
  }, [onNew]);

  useEffect(() => {
    poll();
    const t = setInterval(poll, POLL_MS);
    const onFocus = () => poll();
    window.addEventListener('focus', onFocus);
    return () => {
      clearInterval(t);
      window.removeEventListener('focus', onFocus);
    };
  }, [poll]);

  // "(3) HackComms" in the browser tab.
  useEffect(() => {
    document.title = unread > 0 ? `(${unread}) HackComms` : 'HackComms';
  }, [unread]);

  const load = useCallback(async () => {
    const inbox = await api.notifications();
    setItems(inbox.items);
    setUnread(inbox.unread);
    lastUnread.current = inbox.unread;
  }, []);

  useEffect(() => {
    if (!open) return;
    load().catch(() => setItems([]));
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, load]);

  async function click(n: AppNotification) {
    if (!n.read) {
      setItems((list) => list?.map((x) => (x.id === n.id ? { ...x, read: true } : x)) ?? null);
      setUnread((u) => Math.max(0, u - 1));
      lastUnread.current = Math.max(0, lastUnread.current - 1);
      api.markNotificationRead(n.id).catch(() => {});
    }
    if (n.link !== 'NONE') {
      setOpen(false);
      onOpen(n);
    }
  }

  async function readAll() {
    await api.markAllNotificationsRead().catch(() => {});
    setItems((list) => list?.map((x) => ({ ...x, read: true })) ?? null);
    setUnread(0);
    lastUnread.current = 0;
  }

  const today = new Date().toDateString();
  const groups: [string, AppNotification[]][] = [
    ['Today', (items ?? []).filter((n) => new Date(n.createdAt).toDateString() === today)],
    ['Earlier', (items ?? []).filter((n) => new Date(n.createdAt).toDateString() !== today)],
  ];

  return (
    <>
      <button className="bell" onClick={() => setOpen(true)} aria-label={`Notifications${unread ? `, ${unread} unread` : ''}`}
        title="Notifications">
        🔔
        {unread > 0 && <span className="bell__badge">{unread > 99 ? '99+' : unread}</span>}
      </button>

      {open && (
        <div className="drawer-backdrop" onMouseDown={(e) => e.target === e.currentTarget && setOpen(false)}>
          <aside className="drawer" role="dialog" aria-label="Notifications">
            <header className="drawer__head">
              <h2>Notifications</h2>
              {unread > 0 && <button className="link small" onClick={readAll}>Mark all read</button>}
              <button className="icon-btn icon-btn--dark" onClick={() => setOpen(false)} aria-label="Close">✕</button>
            </header>
            <div className="drawer__body">
              {items === null && <p className="muted small drawer__empty">Loading…</p>}
              {items?.length === 0 && (
                <div className="drawer__empty">
                  <div className="empty__icon">🔕</div>
                  <p className="muted">You're all caught up. Approvals, team invites and deadline reminders show up here.</p>
                </div>
              )}
              {groups.map(([label, list]) => list.length > 0 && (
                <section key={label}>
                  <h3 className="drawer__group">{label}</h3>
                  {list.map((n) => (
                    <button key={n.id} className={`notif${n.read ? '' : ' notif--unread'}${n.link === 'NONE' ? ' notif--static' : ''}`}
                      onClick={() => click(n)}>
                      <span className="notif__icon">{n.icon ?? '🔔'}</span>
                      <span className="notif__text">
                        <span className="notif__title">{n.title}</span>
                        {n.body && <span className="notif__body">{n.body}</span>}
                        <span className="notif__time">{timeAgo(n.createdAt)}</span>
                      </span>
                      {!n.read && <span className="notif__dot" aria-label="Unread" />}
                    </button>
                  ))}
                </section>
              ))}
            </div>
          </aside>
        </div>
      )}
    </>
  );
}

function timeAgo(iso: string) {
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return 'just now';
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  const d = Math.floor(s / 86400);
  return d === 1 ? 'yesterday' : `${d} days ago`;
}
