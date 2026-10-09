import { SimpleGrid, Card, Text, Group, Loader, Center, Stack } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { money } from '../../lib/format';
import { useIsStaff, useMe } from '../../lib/session';
import { PageHeader } from '../../components/PageHeader';
import type { AgentDaySummary, Dashboard, DashboardPeriod } from '../../lib/types';

function Stat({ label, value, hint }: { label: string; value: string | number; hint?: string }) {
  return (
    <Card withBorder padding="md" radius="md">
      <Text size="xs" c="dimmed" tt="uppercase" fw={600}>
        {label}
      </Text>
      <Text size="xl" fw={700}>
        {value}
      </Text>
      {hint && (
        <Text size="xs" c="dimmed">
          {hint}
        </Text>
      )}
    </Card>
  );
}

function PeriodCard({ title, p }: { title: string; p: DashboardPeriod }) {
  return (
    <Card withBorder padding="md" radius="md">
      <Text fw={600} mb="xs">
        {title}
      </Text>
      <Group justify="space-between">
        <Text c="dimmed" size="sm">
          Recharges
        </Text>
        <Text>
          {money(p.rechargeAmount)}{' '}
          <Text span c="dimmed" size="xs">
            ({p.rechargeCount})
          </Text>
        </Text>
      </Group>
      <Group justify="space-between">
        <Text c="dimmed" size="sm">
          Débits carte
        </Text>
        <Text>
          {money(p.salesAmount)}{' '}
          <Text span c="dimmed" size="xs">
            ({p.salesCount})
          </Text>
        </Text>
      </Group>
    </Card>
  );
}

function StaffDashboard() {
  const q = useQuery({
    queryKey: ['dashboard'],
    queryFn: async () => (await api.get<Dashboard>('/api/dashboard')).data,
  });
  if (q.isLoading)
    return (
      <Center h={200}>
        <Loader />
      </Center>
    );
  if (!q.data) return <Text c="red">Impossible de charger le tableau de bord.</Text>;
  const d = q.data;
  return (
    <Stack>
      <SimpleGrid cols={{ base: 2, sm: 4 }}>
        <Stat label="Clients" value={d.counts.customers} hint={`${d.counts.activeCustomers} non archivés`} />
        <Stat label="Cartes actives" value={d.counts.activeCards} hint={`${d.counts.totalCards} au total`} />
        <Stat label="Cartes bloquées" value={d.counts.blockedCards} />
        <Stat label="Débits carte (mois)" value={money(d.month.salesAmount)} />
      </SimpleGrid>
      <SimpleGrid cols={{ base: 1, md: 3 }}>
        <PeriodCard title="Aujourd'hui" p={d.today} />
        <PeriodCard title="Cette semaine" p={d.week} />
        <PeriodCard title="Ce mois" p={d.month} />
      </SimpleGrid>
    </Stack>
  );
}

function AgentDashboard() {
  const q = useQuery({
    queryKey: ['dashboard', 'me'],
    queryFn: async () => (await api.get<AgentDaySummary>('/api/dashboard/me')).data,
  });
  if (q.isLoading)
    return (
      <Center h={200}>
        <Loader />
      </Center>
    );
  if (!q.data) return null;
  const s = q.data;
  return (
    <SimpleGrid cols={{ base: 2, sm: 4 }}>
      <Stat label="Mes débits carte (jour)" value={s.salesCount} hint={money(s.salesAmount)} />
      <Stat label="Mes recharges (jour)" value={s.rechargeCount} hint={money(s.rechargeAmount)} />
    </SimpleGrid>
  );
}

export function DashboardPage() {
  const me = useMe();
  const isStaff = useIsStaff();
  return (
    <>
      <PageHeader
        title="Tableau de bord"
        subtitle={isStaff ? 'Indicateurs de l’entreprise' : `Votre activité, ${me?.fullName}`}
      />
      {isStaff || me?.role === 'AUDITEUR' ? <StaffDashboard /> : <AgentDashboard />}
    </>
  );
}
