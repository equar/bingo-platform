import { Navigate, Outlet, useLocation } from "react-router-dom";
import { hasRequiredRole, useSession } from "../api/session";
import { StatusState } from "../components/StatusState";

interface ProtectedRouteProps {
  allowedRoles?: string[];
}

export function ProtectedRoute({ allowedRoles = ["ADMIN"] }: ProtectedRouteProps) {
  const session = useSession();
  const location = useLocation();

  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
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
