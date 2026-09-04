import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Center, Text } from '@mantine/core';
import { useAuth } from '../lib/auth';
import type { Role } from '../lib/types';

export function RequireAuth({ children, roles }: { children: ReactNode; roles?: Role[] }) {
  const user = useAuth((s) => s.user);
  const refreshToken = useAuth((s) => s.refreshToken);
  const location = useLocation();

  if (!user || !refreshToken) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  if (roles && !roles.includes(user.role)) {
    return (
      <Center h="60vh">
        <Text c="dimmed">Vous n'avez pas accès à cette section.</Text>
      </Center>
    );
  }
  return <>{children}</>;
}
