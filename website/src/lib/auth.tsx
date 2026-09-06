import * as React from "react";
import { demoLogin, getSession, logout, type PublicSession } from "./auth.functions";

type AuthValue = {
  session: PublicSession;
  ready: boolean;
  refresh: () => Promise<void>;
  signOut: () => Promise<void>;
  signInDemo: (displayName: string) => Promise<void>;
};

const AuthContext = React.createContext<AuthValue | null>(null);

export function AuthProvider({
  children,
  initialSession = null,
}: {
  children: React.ReactNode;
  initialSession?: PublicSession;
}) {
  const [session, setSession] = React.useState<PublicSession>(initialSession);
  const [ready, setReady] = React.useState(false);

  const refresh = React.useCallback(async () => {
    setSession(await getSession());
    setReady(true);
  }, []);

  React.useEffect(() => {
    void refresh().catch(() => setReady(true));
  }, [refresh]);

  const value = React.useMemo<AuthValue>(
    () => ({
      session,
      ready,
      refresh,
      signOut: async () => {
        if (session) await logout({ data: { csrfToken: session.csrfToken } });
        setSession(null);
      },
      signInDemo: async (displayName) => {
        const next = await demoLogin({ data: { displayName } });
        setSession(next);
        setReady(true);
      },
    }),
    [session, ready, refresh],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = React.useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
