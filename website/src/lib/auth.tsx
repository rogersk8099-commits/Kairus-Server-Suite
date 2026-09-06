/**
 * Front-end only session stub so the portal can be demoed end to end.
 * Replace `signIn` / `signOut` with real backend calls when auth is wired up.
 */
import * as React from "react";

const STORAGE_KEY = "smp.session";

export type Session = { username: string; email: string } | null;

type AuthValue = {
  session: Session;
  ready: boolean;
  signIn: (username: string, email?: string) => void;
  signOut: () => void;
};

const AuthContext = React.createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = React.useState<Session>(null);
  const [ready, setReady] = React.useState(false);

  React.useEffect(() => {
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY);
      if (raw) setSession(JSON.parse(raw) as Session);
    } catch {
      /* ignore */
    }
    setReady(true);
  }, []);

  const value = React.useMemo<AuthValue>(
    () => ({
      session,
      ready,
      signIn: (username, email) => {
        const next = { username, email: email ?? `${username.toLowerCase()}@example.com` };
        setSession(next);
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      },
      signOut: () => {
        setSession(null);
        window.localStorage.removeItem(STORAGE_KEY);
      },
    }),
    [session, ready],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = React.useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
