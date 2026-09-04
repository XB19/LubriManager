import { useState } from 'react';
import { Button, Group, Modal, Select, Stack, Text, Textarea } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { DataTable } from 'mantine-datatable';
import { useForm } from '@mantine/form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { dateTime, money } from '../../lib/format';
import { useHasRole } from '../../lib/session';
import { toastError, toastOk } from '../../lib/toast';
import { PageHeader } from '../../components/PageHeader';
import { StatusBadge } from '../../components/StatusBadge';
import { CardPicker } from '../../components/CardPicker';
import { RechargeModal } from './RechargeModal';
import type { Card, Page, Recharge } from '../../lib/types';

const PAGE_SIZE = 15;

function RejectModal({
  recharge,
  onClose,
}: {
  recharge: Recharge | null;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const form = useForm({ initialValues: { reason: '' }, validate: { reason: (v) => (v.trim() ? null : 'Requis') } });
  const submit = useMutation({
    mutationFn: () => api.post(`/api/recharges/${recharge!.id}/reject`, { reason: form.values.reason }),
    onSuccess: () => {
      qc.invalidateQueries();
      toastOk('Recharge refusée');
      onClose();
      form.reset();
    },
    onError: (e) => toastError(e),
  });
  return (
    <Modal opened={!!recharge} onClose={onClose} title="Refuser la recharge">
      <form onSubmit={form.onSubmit(() => submit.mutate())}>
        <Stack>
          <Textarea label="Motif" required autosize minRows={2} {...form.getInputProps('reason')} />
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Annuler
            </Button>
            <Button type="submit" color="red" loading={submit.isPending}>
              Refuser
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}

export function RechargesPage() {
  const qc = useQueryClient();
  const canValidate = useHasRole('ADMIN', 'MANAGER');
  const [page, setPage] = useState(1);
  const [status, setStatus] = useState<string | null>(null);
  const [pickOpen, pick] = useDisclosure(false);
  const [selectedCard, setSelectedCard] = useState<Card | null>(null);
  const [rechargeOpen, recharge] = useDisclosure(false);
  const [rejecting, setRejecting] = useState<Recharge | null>(null);

  const query = useQuery({
    queryKey: ['recharges', { page, status }],
    queryFn: async () =>
      (
        await api.get<Page<Recharge>>('/api/recharges', {
          params: { page: page - 1, size: PAGE_SIZE, status: status || undefined },
        })
      ).data,
  });

  const validate = useMutation({
    mutationFn: (id: number) => api.post(`/api/recharges/${id}/validate`, {}),
    onSuccess: () => {
      qc.invalidateQueries();
      toastOk('Recharge validée');
    },
    onError: (e) => toastError(e),
  });

  return (
    <>
      <PageHeader
        title="Recharges"
        actions={
          <Button leftSection={<IconPlus size={16} />} onClick={pick.open}>
            Nouvelle recharge
          </Button>
        }
      />
      <Group mb="sm">
        <Select
          placeholder="Statut"
          clearable
          data={['EN_ATTENTE', 'VALIDEE', 'REFUSEE']}
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
        records={query.data?.content ?? []}
        fetching={query.isFetching}
        noRecordsText="Aucune recharge"
        columns={[
          { accessor: 'reference', title: 'Référence', width: 200 },
          { accessor: 'customerNo', title: 'Client' },
          { accessor: 'cardNo', title: 'Carte' },
          { accessor: 'paymentMethod', title: 'Paiement' },
          { accessor: 'amount', title: 'Montant', render: (r) => money(r.amount) },
          { accessor: 'status', title: 'Statut', render: (r) => <StatusBadge value={r.status} /> },
          { accessor: 'operator', title: 'Opérateur' },
          { accessor: 'createdAt', title: 'Date', render: (r) => dateTime(r.createdAt) },
          {
            accessor: 'actions',
            title: '',
            textAlign: 'right',
            render: (r) =>
              canValidate && r.status === 'EN_ATTENTE' ? (
                <Group gap={4} justify="flex-end" wrap="nowrap">
                  <Button size="compact-xs" color="teal" onClick={() => validate.mutate(r.id)}>
                    Valider
                  </Button>
                  <Button size="compact-xs" variant="light" color="red" onClick={() => setRejecting(r)}>
                    Refuser
                  </Button>
                </Group>
              ) : null,
          },
        ]}
        totalRecords={query.data?.totalElements ?? 0}
        recordsPerPage={PAGE_SIZE}
        page={page}
        onPageChange={setPage}
      />

      <Modal opened={pickOpen} onClose={pick.close} title="Choisir la carte à recharger">
        <Stack>
          <CardPicker onPick={setSelectedCard} />
          {selectedCard && (
            <Text size="sm" c="dimmed">
              Solde actuel : {money(selectedCard.balance)}
            </Text>
          )}
          <Group justify="flex-end">
            <Button variant="default" onClick={pick.close}>
              Annuler
            </Button>
            <Button
              disabled={!selectedCard}
              onClick={() => {
                pick.close();
                recharge.open();
              }}
            >
              Continuer
            </Button>
          </Group>
        </Stack>
      </Modal>
      {selectedCard && (
        <RechargeModal opened={rechargeOpen} onClose={recharge.close} card={selectedCard} />
      )}
      <RejectModal recharge={rejecting} onClose={() => setRejecting(null)} />
    </>
  );
}
