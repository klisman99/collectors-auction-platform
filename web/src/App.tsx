import { useEffect, useState } from 'react';

import {
  type AuthenticatedSession,
  getAuthenticatedSession,
  revokeAllSessions,
  signOut,
} from './api/client';
import { AuthenticatedHome } from './identity/AuthenticatedHome';
import { hasIdentityAction, IdentityAccess } from './identity/IdentityAccess';
import { OperationalHome, OperationalModeratorHome } from './operations/OperationalHomes';
import { applyApiError } from './shared/forms';
import { PageFrame } from './shared/ui';

export function App() {
  const [session, setSession] = useState<AuthenticatedSession | null | undefined>(undefined);
  const [notice, setNotice] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  useEffect(() => {
    getAuthenticatedSession()
      .then(setSession)
      .catch(() => setSession(null));
  }, []);

  if (session === undefined) {
    return (
      <PageFrame>
        <p className="text-slate-300">Loading your account…</p>
      </PageFrame>
    );
  }

  // Single-use identity links take precedence over a possibly stale session.
  if (session !== null && !hasIdentityAction(window.location.search)) {
    if (session.accountType === 'OPERATIONAL') {
      if (session.role !== 'ADMINISTRATOR') {
        return (
          <OperationalModeratorHome
            onRevokeAllSessions={() => endSession(true)}
            onSignOut={() => endSession(false)}
            session={session}
          />
        );
      }
      return (
        <OperationalHome
          failure={failure}
          notice={notice}
          onRevokeAllSessions={() => endSession(true)}
          onSignOut={() => endSession(false)}
          session={session}
        />
      );
    }
    return (
      <AuthenticatedHome
        failure={failure}
        onRevokeAllSessions={() => endSession(true)}
        onSignOut={() => endSession(false)}
        session={session}
      />
    );
  }

  async function endSession(revokeAll: boolean) {
    setFailure(null);
    try {
      if (revokeAll) {
        await revokeAllSessions();
        setNotice('All sessions have been signed out.');
      } else {
        await signOut();
        setNotice('You have been signed out.');
      }
      setSession(null);
    } catch (error) {
      applyApiError(error, () => {}, setFailure);
    }
  }

  return <IdentityAccess initialNotice={notice} onSessionChange={setSession} />;
}
