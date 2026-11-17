import type { Hackathon } from '../api';

const DAY = 86_400_000;

/** "2026-11-20" -> local midnight (avoids the UTC off-by-one of new Date("2026-11-20")). */
export function parseDay(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y!, m! - 1, d!);
}

export function toIsoDay(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export function today(): Date {
  const n = new Date();
  return new Date(n.getFullYear(), n.getMonth(), n.getDate());
}

export function daysBetween(a: Date, b: Date): number {
  return Math.round((b.getTime() - a.getTime()) / DAY);
}

export type Phase = 'upcoming' | 'ongoing' | 'past';

export function phaseOf(h: Hackathon): Phase {
  const t = today();
  if (parseDay(h.endDate) < t) return 'past';
  if (parseDay(h.startDate) <= t) return 'ongoing';
  return 'upcoming';
}

const fmt = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short' });
const fmtYear = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });

export function formatDay(iso: string, withYear = false): string {
  return (withYear ? fmtYear : fmt).format(parseDay(iso));
}

export function formatRange(start: string, end: string): string {
  if (start === end) return formatDay(start, true);
  return `${formatDay(start)} – ${formatDay(end, true)}`;
}

/** Short, urgent-sounding deadline label, or null if there's no deadline. */
export function deadlineLabel(h: Hackathon): { text: string; urgent: boolean; closed: boolean } | null {
  if (!h.registrationDeadline) return null;
  const left = daysBetween(today(), parseDay(h.registrationDeadline));
  if (left < 0) return { text: 'Registration closed', urgent: false, closed: true };
  if (left === 0) return { text: 'Registration closes today', urgent: true, closed: false };
  if (left === 1) return { text: 'Registration closes tomorrow', urgent: true, closed: false };
  return { text: `Registration closes in ${left} days`, urgent: left <= 3, closed: false };
}

export function startsLabel(h: Hackathon): string {
  const phase = phaseOf(h);
  if (phase === 'past') return 'Ended';
  if (phase === 'ongoing') return 'Happening now';
  const left = daysBetween(today(), parseDay(h.startDate));
  return left === 1 ? 'Starts tomorrow' : `Starts in ${left} days`;
}

// ---------- calendar export ----------

const compact = (iso: string) => iso.replace(/-/g, '');
const plusOneDay = (iso: string) => toIsoDay(new Date(parseDay(iso).getTime() + DAY + 3_600_000));

function details(h: Hackathon): string {
  return [h.organizer && `By ${h.organizer}`, h.description, h.websiteUrl, 'Shared via HackComms']
    .filter(Boolean)
    .join('\n\n');
}

export function googleCalendarUrl(h: Hackathon): string {
  const p = new URLSearchParams({
    action: 'TEMPLATE',
    text: h.title,
    dates: `${compact(h.startDate)}/${compact(plusOneDay(h.endDate))}`,
    details: details(h),
  });
  if (h.location) p.set('location', h.location);
  return `https://calendar.google.com/calendar/render?${p}`;
}

const escapeIcs = (s: string) => s.replace(/\\/g, '\\\\').replace(/\n/g, '\\n').replace(/[,;]/g, (m) => `\\${m}`);

function icsEvent(uid: string, title: string, start: string, endExclusive: string, desc: string, location?: string | null) {
  return [
    'BEGIN:VEVENT',
    `UID:${uid}@hackcomms`,
    `DTSTAMP:${new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')}`,
    `DTSTART;VALUE=DATE:${compact(start)}`,
    `DTEND;VALUE=DATE:${compact(endExclusive)}`,
    `SUMMARY:${escapeIcs(title)}`,
    `DESCRIPTION:${escapeIcs(desc)}`,
    location ? `LOCATION:${escapeIcs(location)}` : '',
    'END:VEVENT',
  ].filter(Boolean);
}

/** One .ics with the hackathon and (if set) its registration deadline as a separate all-day event. */
export function downloadIcs(h: Hackathon) {
  const lines = ['BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//HackComms//EN', 'CALSCALE:GREGORIAN'];
  lines.push(...icsEvent(`h${h.id}`, h.title, h.startDate, plusOneDay(h.endDate), details(h), h.location));
  if (h.registrationDeadline) {
    lines.push(
      ...icsEvent(`h${h.id}-deadline`, `Registration deadline: ${h.title}`, h.registrationDeadline,
        plusOneDay(h.registrationDeadline), details(h)),
    );
  }
  lines.push('END:VCALENDAR');
  const blob = new Blob([lines.join('\r\n')], { type: 'text/calendar' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `${h.title.replace(/[^a-z0-9]+/gi, '-').toLowerCase()}.ics`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}
