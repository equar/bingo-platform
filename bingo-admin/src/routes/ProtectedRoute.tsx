import { useEffect, useState } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { refreshAdminUserProfile } from "../api/admin";
import { clearSession, hasRequiredRole, readSession, useSession, writeSession } from "../api/session";
import { StatusState } from "../components/StatusState";

interface ProtectedRouteProps {
  allowedRoles?: string[];
  refreshSession?: boolean;
}

export function ProtectedRoute({ allowedRoles = ["ADMIN"], refreshSession = false }: ProtectedRouteProps) {
  const session = useSession();
  const location = useLocation();
  const [verifiedToken, setVerifiedToken] = useState<string | null>(null);

  useEffect(() => {
    if (!refreshSession || !session?.accessToken) {
      setVerifiedToken(session?.accessToken ?? null);
      return;
    }
    let active = true;
    setVerifiedToken(null);
    refreshAdminUserProfile()
      .then((user) => {
        if (!active) return;
        const latestSession = readSession();
        if (latestSession?.accessToken === session.accessToken) {
          writeSession({ ...latestSession, user });
        }
        setVerifiedToken(session.accessToken);
      })
      .catch(() => {
        if (!active) return;
        clearSession();
        setVerifiedToken(session.accessToken);
      });
    return () => { active = false; };
  }, [refreshSession, session?.accessToken]);

  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (refreshSession && verifiedToken !== session.accessToken) {
    return <StatusState title="Checking access" loading message="Refreshing your account permissions" />;
  }

  if (!hasRequiredRole(session.user.roles, allowedRoles)) {
    return (
      <StatusState
        title="Access denied"
        message="Your account is authenticated but does not have a required admin role."
      />
    );
  }

  if (session.user.mustChangePassword && location.pathname !== "/change-password") {
    return <Navigate to="/change-password" replace />;
  }

  return <Outlet />;
}
