import { SimpleGrid, Card, Text, Group, Loader, Center, Stack, Table, Title } from '@mantine/core';
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
          {money(p.rechargeAmount)} <Text span c="dimmed" size="xs">({p.rechargeCount})</Text>
        </Text>
      </Group>
      <Group justify="space-between">
        <Text c="dimmed" size="sm">
          Ventes (CA)
        </Text>
        <Text>
          {money(p.salesAmount)} <Text span c="dimmed" size="xs">({p.salesCount})</Text>
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
      <SimpleGrid cols={{ base: 2, sm: 3, lg: 6 }}>
        <Stat label="Clients" value={d.counts.customers} hint={`${d.counts.activeCustomers} non archivés`} />
        <Stat label="Cartes actives" value={d.counts.activeCards} hint={`${d.counts.totalCards} au total`} />
        <Stat label="Cartes bloquées" value={d.counts.blockedCards} />
        <Stat label="Stock faible" value={d.counts.lowStockProducts} />
        <Stat label="Recharges en attente" value={d.counts.pendingRecharges} />
        <Stat label="CA du mois" value={money(d.month.salesAmount)} />
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
    <Stack>
      <SimpleGrid cols={{ base: 2, sm: 4 }}>
        <Stat label="Mes ventes (jour)" value={s.salesCount} hint={money(s.salesAmount)} />
        <Stat label="Mes recharges (jour)" value={s.rechargeCount} hint={money(s.rechargeAmount)} />
      </SimpleGrid>
      <Card withBorder padding="md" radius="md">
        <Title order={5} mb="sm">
          Recharges encaissées par moyen de paiement
        </Title>
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Moyen</Table.Th>
              <Table.Th>Nombre</Table.Th>
              <Table.Th ta="right">Montant</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {s.rechargesByPaymentMethod.map((r) => (
              <Table.Tr key={r.paymentMethod}>
                <Table.Td>{r.paymentMethod}</Table.Td>
                <Table.Td>{r.count}</Table.Td>
                <Table.Td ta="right">{money(r.amount)}</Table.Td>
              </Table.Tr>
            ))}
            {s.rechargesByPaymentMethod.length === 0 && (
              <Table.Tr>
                <Table.Td colSpan={3}>
                  <Text c="dimmed">Aucune recharge aujourd'hui.</Text>
                </Table.Td>
              </Table.Tr>
            )}
          </Table.Tbody>
        </Table>
      </Card>
    </Stack>
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
