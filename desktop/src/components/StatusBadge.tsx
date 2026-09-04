import { Badge } from '@mantine/core';

const COLORS: Record<string, string> = {
  // transactions
  VALIDEE: 'teal',
  EN_ATTENTE: 'yellow',
  REFUSEE: 'red',
  ANNULEE: 'gray',
  // clients
  ACTIF: 'teal',
  SUSPENDU: 'yellow',
  BLOQUE: 'red',
  ARCHIVE: 'gray',
  // cartes
  ACTIVE: 'teal',
  SUSPENDUE: 'yellow',
  BLOQUEE: 'red',
  PERDUE: 'red',
  EXPIREE: 'gray',
  DESACTIVEE: 'gray',
  // produits
  INACTIF: 'gray',
  // sévérité
  INFO: 'blue',
  ALERTE: 'yellow',
  CRITIQUE: 'red',
};

export function StatusBadge({ value }: { value: string | null | undefined }) {
  if (!value) return <span>—</span>;
  return (
    <Badge color={COLORS[value] ?? 'blue'} variant="light" radius="sm">
      {value}
    </Badge>
  );
}
