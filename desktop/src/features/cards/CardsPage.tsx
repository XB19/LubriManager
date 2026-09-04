import { useState } from 'react';
import {
  Button,
  Checkbox,
  Group,
  Menu,
  Modal,
  Select,
  Stack,
  TextInput,
} from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useDebouncedValue, useDisclosure } from '@mantine/hooks';
import { IconDotsVertical, IconPlus, IconSearch } from '@tabler/icons-react';
import { DataTable } from 'mantine-datatable';
import { useForm } from '@mantine/form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { api } from '../../lib/api';
import { dateTime, money } from '../../lib/format';
import { useHasRole } from '../../lib/session';
import { toastError, toastOk } from '../../lib/toast';
import { PageHeader } from '../../components/PageHeader';
import { StatusBadge } from '../../components/StatusBadge';
import { CustomerPicker } from '../../components/CustomerPicker';
import { RechargeModal } from '../recharges/RechargeModal';
import type { Card, CardStatus, Page } from '../../lib/types';

const PAGE_SIZE = 15;
const OPERATOR_STATUSES: CardStatus[] = ['ACTIVE', 'SUSPENDUE', 'BLOQUEE', 'PERDUE'];

function NewCardModal({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const qc = useQueryClient();
  const form = useForm({
    initialValues: {
      customerId: null as string | null,
      externalNo: '',
      expiresAt: null as Date | null,
      activateImmediately: true,
    },
    validate: { customerId: (v) => (v ? null : 'Requis') },
  });
  const save = useMutation({
    mutationFn: () =>
      api.post('/api/cards', {
        customerId: Number(form.values.customerId),
        externalNo: form.values.externalNo || null,
        expiresAt: form.values.expiresAt ? dayjs(form.values.expiresAt).format('YYYY-MM-DD') : null,
        activateImmediately: form.values.activateImmediately,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cards'] });
      toastOk('Carte créée');
      onClose();
      form.reset();
    },
    onError: (e) => toastError(e),
  });
  return (
    <Modal opened={opened} onClose={onClose} title="Nouvelle carte">
      <form onSubmit={form.onSubmit(() => save.mutate())}>
        <Stack>
          <CustomerPicker
            required
            value={form.values.customerId}
            onChange={(v) => form.setFieldValue('customerId', v)}
          />
          <TextInput label="N° externe (badge / QR)" {...form.getInputProps('externalNo')} />
          <DateInput
            label="Expiration"
            clearable
            valueFormat="DD/MM/YYYY"
            minDate={new Date()}
            {...form.getInputProps('expiresAt')}
          />
          <Checkbox
            label="Activer immédiatement"
            {...form.getInputProps('activateImmediately', { type: 'checkbox' })}
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Annuler
            </Button>
            <Button type="submit" loading={save.isPending}>
              Créer
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}

export function CardsPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const canDeactivate = useHasRole('ADMIN', 'MANAGER');
  const [page, setPage] = useState(1);
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<string | null>(null);
  const [debouncedQ] = useDebouncedValue(q, 300);
  const [newOpen, newModal] = useDisclosure(false);
  const [rechargeCard, setRechargeCard] = useState<Card | null>(null);

  const query = useQuery({
    queryKey: ['cards', { page, q: debouncedQ, status }],
    queryFn: async () =>
      (
        await api.get<Page<Card>>('/api/cards', {
          params: { page: page - 1, size: PAGE_SIZE, q: debouncedQ || undefined, status: status || undefined },
        })
      ).data,
  });

  const setCardStatus = useMutation({
    mutationFn: ({ id, status }: { id: number; status: CardStatus }) =>
      api.patch(`/api/cards/${id}/status`, { status }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cards'] });
      toastOk('Statut modifié');
    },
    onError: (e) => toastError(e),
  });
  const deactivate = useMutation({
    mutationFn: (id: number) => api.post(`/api/cards/${id}/deactivate`, {}),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cards'] });
      toastOk('Carte désactivée');
    },
    onError: (e) => toastError(e),
  });

  return (
    <>
      <PageHeader
        title="Cartes"
        actions={
          <Button leftSection={<IconPlus size={16} />} onClick={newModal.open}>
            Nouvelle carte
          </Button>
        }
      />
      <Group mb="sm">
        <TextInput
          placeholder="N° carte, n° externe, client…"
          leftSection={<IconSearch size={16} />}
          value={q}
          onChange={(e) => {
            setQ(e.currentTarget.value);
            setPage(1);
          }}
          w={320}
        />
        <Select
          placeholder="Statut"
          clearable
          data={['EN_ATTENTE', 'ACTIVE', 'SUSPENDUE', 'BLOQUEE', 'PERDUE', 'EXPIREE', 'DESACTIVEE']}
          value={status}
          onChange={(v) => {
            setStatus(v);
            setPage(1);
          }}
        />
      </Group>
      <DataTable
        withTableBorder
        borderRadius="sm"
        minHeight={200}
        highlightOnHover
        records={query.data?.content ?? []}
        fetching={query.isFetching}
        noRecordsText="Aucune carte"
        columns={[
          { accessor: 'cardNo', title: 'N° carte', width: 140 },
          {
            accessor: 'customerName',
            title: 'Client',
            render: (r) => (
              <a
                style={{ cursor: 'pointer', color: 'var(--mantine-color-teal-7)' }}
                onClick={() => navigate(`/clients/${r.customerId}`)}
              >
                {r.customerNo} — {r.customerName}
              </a>
            ),
          },
          { accessor: 'status', title: 'Statut', render: (r) => <StatusBadge value={r.status} /> },
          { accessor: 'balance', title: 'Solde', render: (r) => money(r.balance) },
          { accessor: 'issuedAt', title: 'Émise le', render: (r) => dateTime(r.issuedAt) },
          {
            accessor: 'actions',
            title: '',
            textAlign: 'right',
            render: (r) => (
              <Group gap={4} justify="flex-end" wrap="nowrap">
                <Button
                  size="compact-xs"
                  variant="light"
                  disabled={r.status !== 'ACTIVE'}
                  onClick={() => setRechargeCard(r)}
                >
                  Recharger
                </Button>
                <Menu position="bottom-end">
                  <Menu.Target>
                    <Button size="compact-xs" variant="subtle" px={6}>
                      <IconDotsVertical size={14} />
                    </Button>
                  </Menu.Target>
                  <Menu.Dropdown>
                    {OPERATOR_STATUSES.filter((s) => s !== r.status).map((s) => (
                      <Menu.Item key={s} onClick={() => setCardStatus.mutate({ id: r.id, status: s })}>
                        Passer à {s}
                      </Menu.Item>
                    ))}
                    {canDeactivate && r.status !== 'DESACTIVEE' && (
                      <>
                        <Menu.Divider />
                        <Menu.Item color="red" onClick={() => deactivate.mutate(r.id)}>
                          Désactiver définitivement
                        </Menu.Item>
                      </>
                    )}
                  </Menu.Dropdown>
                </Menu>
              </Group>
            ),
          },
        ]}
        totalRecords={query.data?.totalElements ?? 0}
        recordsPerPage={PAGE_SIZE}
        page={page}
        onPageChange={setPage}
      />
      <NewCardModal opened={newOpen} onClose={newModal.close} />
      {rechargeCard && <RechargeModal opened onClose={() => setRechargeCard(null)} card={rechargeCard} />}
    </>
  );
}
