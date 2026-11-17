import { AreaCode, DirectChannelIdentifier, GroupChannelIdentifier, NCEngine } from '@nexconn/chat';
import { NCChatUIApplication, type ServiceHooks } from '@nexconn/chatui';
import { api, type ChatSession } from '../api';

/**
 * Nexconn must be initialized exactly once per page lifetime (React StrictMode runs
 * effects twice in dev), so the whole startup lives in a module-level promise.
 */
let starting: Promise<NCChatUIApplication> | null = null;

const hooks: ServiceHooks = {
  reqUserProfiles: (userIds) => api.userProfiles(userIds),
  reqGroupProfiles: (groupIds) => api.groupProfiles(groupIds),
  reqSystemProfiles: async (ids) => ids.map((id) => ({ systemId: id, name: 'HackComms' })),
  reqGroupMembers: (groupId) => api.groupMembers(groupId),
};

export function startChat(session: ChatSession): Promise<NCChatUIApplication> {
  if (!starting) {
    starting = (async () => {
      const areaCode = (AreaCode as Record<string, AreaCode>)[session.areaCode] ?? AreaCode.SG;
      NCEngine.initialize({ appKey: session.appKey, areaCode });

      // ChatUI must be created after the engine and before connect, so it sees the connection come up.
      const app = NCChatUIApplication.initialize({ hooks, language: 'en_US' });
      if (!app) throw new Error('Nexconn Chat UI failed to initialize.');
      app.ready();

      const result = await NCEngine.connect({ token: session.token });
      if (!result.isOk) {
        throw new Error(`Couldn't connect to chat (Nexconn code ${result.code}${result.msg ? `: ${result.msg}` : ''}).`);
      }
      return app;
    })();
    // Allow a retry after a failure instead of caching the rejection forever.
    starting.catch(() => {
      starting = null;
    });
  }
  return starting;
}

export async function openGroup(nexconnId: string) {
  const app = await starting;
  await app?.openChannel(new GroupChannelIdentifier(nexconnId), true);
}

export async function openDirect(nexconnUserId: string) {
  const app = await starting;
  await app?.openChannel(new DirectChannelIdentifier(nexconnUserId), true);
}

/** Disconnects; caller should reload the page afterwards to reset the SDK singletons. */
export async function stopChat() {
  if (!starting) return;
  try {
    await NCEngine.disconnect();
  } catch {
    /* already disconnected */
  }
}
