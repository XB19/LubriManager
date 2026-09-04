import { useAuth } from './auth';
import type { Role } from './types';

export function useMe() {
  return useAuth((s) => s.user);
}

export function useHasRole(...roles: Role[]) {
  const user = useAuth((s) => s.user);
  return !!user && roles.includes(user.role);
}

export function useIsStaff() {
  return useHasRole('ADMIN', 'MANAGER');
}

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrateur',
  MANAGER: 'Responsable',
  AGENT: 'Agent',
  AUDITEUR: 'Auditeur',
};
