import { Button, Card, Group, Loader, Menu, SimpleGrid, Stack, Text, Title, Center } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconArrowLeft, IconChevronDown, IconCreditCardPay, IconPlus } from '@tabler/icons-react';
import { DataTable } from 'mantine-datatable';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../../lib/api';
import { dateTime, money } from '../../lib/format';
import { useIsStaff } from '../../lib/session';
import { toastError, toastOk } from '../../lib/toast';
import { PageHeader } from '../../components/PageHeader';
import { StatusBadge } from '../../components/StatusBadge';
import { CardForm } from '../cards/CardForm';
import { RechargeModal } from '../recharges/RechargeModal';
import type { Account, Card as CardT, Customer } from '../../lib/types';

const CUSTOMER_STATUSES = ['ACTIF', 'SUSPENDU', 'BLOQUE', 'ARCHIVE'];

export function CustomerDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const isStaff = useIsStaff();
  const [cardFormOpen, cardForm] = useDisclosure(false);
  const [rechargeOpen, recharge] = useDisclosure(false);

  const customer = useQuery({
    queryKey: ['customer', id],
    queryFn: async () => (await api.get<Customer>(`/api/customers/${id}`)).data,
  });
  const account = useQuery({
    queryKey: ['customer', id, 'account'],
    queryFn: async () => (await api.get<Account>(`/api/customers/${id}/account`)).data,
    enabled: !!id,
  });
  const cards = useQuery({
    queryKey: ['customer', id, 'cards'],
    queryFn: async () => (await api.get<CardT[]>(`/api/customers/${id}/cards`)).data,
    enabled: !!id,
  });

  const changeStatus = useMutation({
    mutationFn: (status: string) => api.patch(`/api/customers/${id}/status`, { status }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['customer', id] });
      toastOk('Statut modifié');
    },
    onError: (e) => toastError(e),
  });

  if (customer.isLoading)
    return (
      <Center h={200}>
        <Loader />
      </Center>
    );
  if (!customer.data) return <Text c="red">Client introuvable.</Text>;
  const c = customer.data;

  return (
    <>
      <Button
        variant="subtle"
        size="compact-sm"
        leftSection={<IconArrowLeft size={16} />}
        mb="xs"
        onClick={() => navigate('/clients')}
      >
        Retour
      </Button>
      <PageHeader
        title={`${c.fullName}`}
        subtitle={`${c.customerNo} · ${c.type}`}
        actions={
          isStaff && (
            <Menu>
              <Menu.Target>
                <Button variant="default" rightSection={<IconChevronDown size={14} />}>
                  Statut : {c.status}
                </Button>
              </Menu.Target>
              <Menu.Dropdown>
                {CUSTOMER_STATUSES.filter((s) => s !== c.status).map((s) => (
                  <Menu.Item key={s} onClick={() => changeStatus.mutate(s)}>
                    Passer à {s}
                  </Menu.Item>
                ))}
              </Menu.Dropdown>
            </Menu>
          )
        }
      />

      <SimpleGrid cols={{ base: 1, md: 2 }} mb="md">
        <Card withBorder padding="md" radius="md">
          <Title order={5} mb="xs">
            Coordonnées
          </Title>
          <Stack gap={4}>
            <Text size="sm">Raison sociale : {c.companyName ?? '—'}</Text>
            <Text size="sm">Téléphone : {c.phone ?? '—'}</Text>
            <Text size="sm">E-mail : {c.email ?? '—'}</Text>
            <Text size="sm">Adresse : {c.address ?? '—'}</Text>
            <Text size="sm">Observations : {c.notes ?? '—'}</Text>
          </Stack>
        </Card>
        <Card withBorder padding="md" radius="md">
          <Group justify="space-between" mb="xs">
            <Title order={5}>Compte</Title>
            {isStaff && c && (
              <Button
                size="compact-sm"
                leftSection={<IconCreditCardPay size={14} />}
                onClick={recharge.open}
              >
                Recharger
              </Button>
            )}
          </Group>
          <Text size="xl" fw={700}>
            {account.data ? money(account.data.balance) : '…'}
          </Text>
          <Text size="sm" c="dimmed">
            Compte n°{account.data?.id} · {account.data?.status}
          </Text>
        </Card>
      </SimpleGrid>

      <Group justify="space-between" mb="xs">
        <Title order={5}>Cartes</Title>
        <Button size="compact-sm" leftSection={<IconPlus size={14} />} onClick={cardForm.open}>
          Nouvelle carte
        </Button>
      </Group>
      <DataTable
        withTableBorder
        borderRadius="sm"
        minHeight={120}
        records={cards.data ?? []}
        fetching={cards.isFetching}
        noRecordsText="Aucune carte"
        columns={[
          { accessor: 'cardNo', title: 'N° carte' },
          { accessor: 'externalNo', title: 'N° externe' },
          { accessor: 'status', title: 'Statut', render: (r) => <StatusBadge value={r.status} /> },
          { accessor: 'balance', title: 'Solde', render: (r) => money(r.balance) },
          { accessor: 'activatedAt', title: 'Activée le', render: (r) => dateTime(r.activatedAt) },
        ]}
      />

      <CardForm opened={cardFormOpen} onClose={cardForm.close} customerId={Number(id)} />
      {c && (
        <RechargeModal
          opened={rechargeOpen}
          onClose={recharge.close}
          customer={{ id: c.id, fullName: c.fullName, customerNo: c.customerNo }}
        />
      )}
    </>
  );
}
