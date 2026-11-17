export interface Workspace {
  id: number;
  name: string;
  slug: string;
  domain: string;
}

export interface User {
  id: number;
  email: string;
  displayName: string;
  role: 'MEMBER' | 'ADMIN';
  workspace: Workspace;
}

export interface OtpRequested {
  email: string;
  workspaceName: string;
  workspaceDomain: string;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

const TOKEN_KEY = 'hackcomms.token';

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string | null) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
  } catch {
    /* storage blocked: session just won't persist */
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body) headers.set('Content-Type', 'application/json');
  const token = getToken();
  if (token) headers.set('Authorization', `Bearer ${token}`);

  let res: Response;
  try {
    res = await fetch(`/api${path}`, { ...init, headers });
  } catch {
    throw new ApiError(0, "Can't reach the server. Is the backend running?");
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = await res.json();
      if (body?.message) message = body.message;
    } catch {
      /* non-JSON error */
    }
    throw new ApiError(res.status, message);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export interface ChatSession {
  appKey: string;
  areaCode: string;
  userId: string;
  token: string;
}

export interface Channel {
  id: number;
  name: string;
  description: string | null;
  kind: 'GENERAL' | 'TOPIC' | 'HACKATHON';
  nexconnId: string;
  memberCount: number;
  joined: boolean;
  hackathonId: number | null;
  hackathonSource: 'OFFICIAL' | 'COMMUNITY' | null;
}

export type HackathonMode = 'ONLINE' | 'OFFLINE' | 'HYBRID';

export interface HackathonInput {
  title: string;
  organizer: string;
  description: string;
  websiteUrl: string;
  mode: HackathonMode;
  location: string;
  startDate: string; // YYYY-MM-DD
  endDate: string;
  registrationDeadline: string | null;
  prizePool: string;
}

export interface Hackathon {
  id: number;
  title: string;
  organizer: string | null;
  description: string | null;
  websiteUrl: string | null;
  mode: HackathonMode;
  location: string | null;
  startDate: string;
  endDate: string;
  registrationDeadline: string | null;
  prizePool: string | null;
  source: 'OFFICIAL' | 'COMMUNITY';
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  rejectionReason: string | null;
  postedBy: { id: number; name: string };
  channel: { id: number; name: string; nexconnId: string } | null;
  interestedCount: number;
  interested: boolean;
  mine: boolean;
  createdAt: string;
  openTeamPosts: number;
}

export type TeamPostType = 'LOOKING_FOR_TEAM' | 'LOOKING_FOR_MEMBERS';

export interface TeamPostInput {
  hackathonId: number;
  type: TeamPostType;
  skills: string[];
  message: string;
  spotsOpen: number | null;
}

export interface TeamPost {
  id: number;
  type: TeamPostType;
  skills: string[];
  message: string | null;
  spotsOpen: number | null;
  open: boolean;
  author: { id: number; name: string; nexconnId: string; email: string; leetcodeRating: number | null; featured: boolean };
  hackathon: { id: number; title: string; source: 'OFFICIAL' | 'COMMUNITY'; startDate: string };
  mine: boolean;
  createdAt: string;
}

export interface Person {
  id: number;
  nexconnId: string;
  displayName: string;
  email: string;
}

export interface PersonSummary {
  userId: number;
  nexconnId: string;
  displayName: string;
  email: string;
  headline: string | null;
  year: string | null;
  branch: string | null;
  skills: string[];
  leetcodeRating: number | null;
  leetcodeBadge: string | null;
  featured: boolean;
}

export interface LeetCodeInfo {
  username: string;
  profileUrl: string;
  verified: boolean;
  verifyCode: string | null;
  rating: number | null;
  badge: string | null;
  topPercent: number | null;
  contests: number | null;
  solved: number | null;
  easy: number | null;
  medium: number | null;
  hard: number | null;
  syncedAt: string | null;
}

export interface Profile {
  userId: number;
  nexconnId: string;
  displayName: string;
  email: string;
  role: 'MEMBER' | 'ADMIN';
  headline: string | null;
  bio: string | null;
  year: string | null;
  branch: string | null;
  skills: string[];
  githubUrl: string | null;
  linkedinUrl: string | null;
  leetcode: LeetCodeInfo | null;
  featured: boolean;
  featuredMinRating: number;
  mine: boolean;
}

export interface Team {
  id: number;
  name: string;
  hackathon: { id: number; title: string; endDate: string };
  channel: { id: number; name: string; nexconnId: string };
  leaderId: number;
  members: PersonSummary[];
  maxSize: number;
  archived: boolean;
  archivedAt: string | null;
  archivesOn: string;
  pendingRequest: { id: number; requestedBy: string; reason: string | null; createdAt: string } | null;
  amLeader: boolean;
}

export interface UnarchiveRequest {
  id: number;
  teamId: number;
  teamName: string;
  hackathonTitle: string;
  memberNames: string[];
  requestedBy: string;
  reason: string | null;
  status: 'PENDING' | 'APPROVED' | 'DENIED';
  reviewedBy: string | null;
  reviewNote: string | null;
  createdAt: string;
  archivedAt: string | null;
}

export type NotificationLink = 'HACKATHON' | 'TEAM' | 'TEAMMATES' | 'ADMIN' | 'PROFILE' | 'NONE';

export interface AppNotification {
  id: number;
  icon: string | null;
  title: string;
  body: string | null;
  link: NotificationLink;
  linkId: number | null;
  read: boolean;
  createdAt: string;
}

export interface ProfileUpdate {
  displayName: string;
  headline: string;
  bio: string;
  year: string;
  branch: string;
  skills: string[];
  githubUrl: string;
  linkedinUrl: string;
}

export const api = {
  requestOtp: (email: string) =>
    request<OtpRequested>('/auth/otp/request', { method: 'POST', body: JSON.stringify({ email }) }),
  verifyOtp: (email: string, code: string) =>
    request<{ token: string; user: User }>('/auth/otp/verify', {
      method: 'POST',
      body: JSON.stringify({ email, code }),
    }),
  me: () => request<User>('/me'),

  chatSession: () => request<ChatSession>('/chat/session'),
  channels: () => request<Channel[]>('/channels'),
  createChannel: (name: string, description: string) =>
    request<Channel>('/channels', { method: 'POST', body: JSON.stringify({ name, description }) }),
  joinChannel: (id: number) => request<Channel>(`/channels/${id}/join`, { method: 'POST' }),
  people: () => request<Person[]>('/people'),

  hackathons: () => request<Hackathon[]>('/hackathons'),
  createHackathon: (input: HackathonInput) =>
    request<Hackathon>('/hackathons', { method: 'POST', body: JSON.stringify(input) }),
  toggleInterest: (id: number) => request<Hackathon>(`/hackathons/${id}/interest`, { method: 'POST' }),

  profiles: () => request<PersonSummary[]>('/profiles'),
  featured: () => request<PersonSummary[]>('/profiles/featured'),
  profile: (userId: number) => request<Profile>(`/profiles/${userId}`),
  updateProfile: (u: ProfileUpdate) => request<Profile>('/profiles/me', { method: 'PUT', body: JSON.stringify(u) }),
  linkLeetcode: (username: string) =>
    request<Profile>('/profiles/me/leetcode', { method: 'POST', body: JSON.stringify({ username }) }),
  verifyLeetcode: () => request<Profile>('/profiles/me/leetcode/verify', { method: 'POST' }),
  refreshLeetcode: () => request<Profile>('/profiles/me/leetcode/refresh', { method: 'POST' }),
  unlinkLeetcode: () => request<Profile>('/profiles/me/leetcode', { method: 'DELETE' }),

  notifications: () => request<{ unread: number; items: AppNotification[] }>('/notifications'),
  unreadCount: () => request<{ unread: number }>('/notifications/unread-count'),
  markNotificationRead: (id: number) => request<void>(`/notifications/${id}/read`, { method: 'POST' }),
  markAllNotificationsRead: () => request<void>('/notifications/read-all', { method: 'POST' }),

  teams: () => request<Team[]>('/teams'),
  createTeam: (hackathonId: number, name: string, maxSize: number) =>
    request<Team>('/teams', { method: 'POST', body: JSON.stringify({ hackathonId, name, maxSize }) }),
  addTeamMember: (teamId: number, userId: number) =>
    request<Team>(`/teams/${teamId}/members`, { method: 'POST', body: JSON.stringify({ userId }) }),
  removeTeamMember: (teamId: number, userId: number) =>
    request<Team | undefined>(`/teams/${teamId}/members/${userId}`, { method: 'DELETE' }),
  archiveTeam: (teamId: number) => request<Team>(`/teams/${teamId}/archive`, { method: 'POST' }),
  requestUnarchive: (teamId: number, reason: string) =>
    request<Team>(`/teams/${teamId}/unarchive-request`, { method: 'POST', body: JSON.stringify({ reason }) }),

  teamPosts: () => request<TeamPost[]>('/team-posts'),
  createTeamPost: (input: TeamPostInput) =>
    request<TeamPost>('/team-posts', { method: 'POST', body: JSON.stringify(input) }),
  setTeamPostOpen: (id: number, open: boolean) =>
    request<TeamPost>(`/team-posts/${id}/open`, { method: 'POST', body: JSON.stringify({ open }) }),
  deleteTeamPost: (id: number) => request<void>(`/team-posts/${id}`, { method: 'DELETE' }),

  admin: {
    hackathons: () => request<Hackathon[]>('/admin/hackathons'),
    approve: (id: number, official: boolean) =>
      request<Hackathon>(`/admin/hackathons/${id}/approve`, { method: 'POST', body: JSON.stringify({ official }) }),
    reject: (id: number, reason: string) =>
      request<Hackathon>(`/admin/hackathons/${id}/reject`, { method: 'POST', body: JSON.stringify({ reason }) }),
    update: (id: number, input: HackathonInput) =>
      request<Hackathon>(`/admin/hackathons/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
    remove: (id: number) => request<void>(`/admin/hackathons/${id}`, { method: 'DELETE' }),
    unarchiveRequests: () => request<UnarchiveRequest[]>('/admin/unarchive-requests'),
    approveUnarchive: (id: number, note: string) =>
      request<UnarchiveRequest>(`/admin/unarchive-requests/${id}/approve`, { method: 'POST', body: JSON.stringify({ note }) }),
    denyUnarchive: (id: number, note: string) =>
      request<UnarchiveRequest>(`/admin/unarchive-requests/${id}/deny`, { method: 'POST', body: JSON.stringify({ note }) }),
  },

  // Used by the Nexconn Chat UI hooks to resolve IDs to names.
  userProfiles: (ids: string[]) =>
    request<{ userId: string; name: string }[]>('/chat/profiles/users', {
      method: 'POST',
      body: JSON.stringify({ ids }),
    }),
  groupProfiles: (ids: string[]) =>
    request<{ groupId: string; name: string; memberCount: number }[]>('/chat/profiles/groups', {
      method: 'POST',
      body: JSON.stringify({ ids }),
    }),
  groupMembers: (groupId: string) =>
    request<{ userId: string; nickname: string }[]>(`/chat/groups/${encodeURIComponent(groupId)}/members`),
};
