import { useState } from 'react';
import { Button, Group, Modal, Select, Stack, Textarea } from '@mantine/core';
import { IconDownload } from '@tabler/icons-react';
import { DataTable } from 'mantine-datatable';
import { useForm } from '@mantine/form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, getApiBaseUrl } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { dateTime, money } from '../../lib/format';
import { useHasRole } from '../../lib/session';
import { toastError, toastOk } from '../../lib/toast';
import { PageHeader } from '../../components/PageHeader';
import { StatusBadge } from '../../components/StatusBadge';
import type { Page, Transaction } from '../../lib/types';

const PAGE_SIZE = 20;

function ReverseModal({ txn, onClose }: { txn: Transaction | null; onClose: () => void }) {
  const qc = useQueryClient();
  const form = useForm({ initialValues: { reason: '' }, validate: { reason: (v) => (v.trim() ? null : 'Requis') } });
  const submit = useMutation({
    mutationFn: () => api.post(`/api/transactions/${txn!.id}/reversal`, { reason: form.values.reason }),
    onSuccess: () => {
      qc.invalidateQueries();
      toastOk('Transaction annulée');
      onClose();
      form.reset();
    },
    onError: (e) => toastError(e),
  });
  return (
    <Modal opened={!!txn} onClose={onClose} title={`Annuler ${txn?.reference ?? ''}`}>
      <form onSubmit={form.onSubmit(() => submit.mutate())}>
        <Stack>
          <Textarea label="Motif de l'annulation" required autosize minRows={2} {...form.getInputProps('reason')} />
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Annuler
            </Button>
            <Button type="submit" color="red" loading={submit.isPending}>
              Contre-passer
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}

export function TransactionsPage() {
  const canReverse = useHasRole('ADMIN', 'MANAGER');
  const token = useAuth((s) => s.accessToken);
  const [page, setPage] = useState(1);
  const [type, setType] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [reversing, setReversing] = useState<Transaction | null>(null);
  const [downloading, setDownloading] = useState(false);

  const query = useQuery({
    queryKey: ['transactions', { page, type, status }],
    queryFn: async () =>
      (
        await api.get<Page<Transaction>>('/api/transactions', {
          params: { page: page - 1, size: PAGE_SIZE, type: type || undefined, status: status || undefined },
        })
      ).data,
  });

  async function downloadHistory() {
    setDownloading(true);
    try {
      const res = await fetch(`${getApiBaseUrl()}/api/reports/transactions?format=csv`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `historique-transactions-${new Date().toISOString().slice(0, 10)}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      toastError(e);
    } finally {
      setDownloading(false);
    }
  }

  return (
    <>
      <PageHeader
        title="Transactions"
        subtitle="Grand livre (aucune suppression possible)"
        actions={
          <Button
            variant="light"
            leftSection={<IconDownload size={16} />}
            loading={downloading}
            onClick={downloadHistory}
          >
            Télécharger l'historique (CSV)
          </Button>
        }
      />
      <Group mb="sm">
        <Select
          placeholder="Type"
          clearable
          data={['RECHARGE', 'ACHAT', 'ANNULATION', 'AJUSTEMENT']}
          value={type}
          onChange={(v) => {
            setType(v);
            setPage(1);
          }}
        />
        <Select
          placeholder="Statut"
          clearable
          data={['VALIDEE', 'EN_ATTENTE', 'REFUSEE', 'ANNULEE']}
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
        noRecordsText="Aucune transaction"
        columns={[
          { accessor: 'createdAt', title: 'Date', render: (r) => dateTime(r.createdAt) },
          { accessor: 'reference', title: 'Référence', width: 200 },
          { accessor: 'type', title: 'Type', render: (r) => <StatusBadge value={r.type} /> },
          { accessor: 'status', title: 'Statut', render: (r) => <StatusBadge value={r.status} /> },
          {
            accessor: 'amount',
            title: 'Montant',
            render: (r) => (
              <span style={{ color: Number(r.amount) < 0 ? 'var(--mantine-color-red-7)' : 'var(--mantine-color-teal-7)' }}>
                {money(r.amount)}
              </span>
            ),
          },
          { accessor: 'balanceAfter', title: 'Solde après', render: (r) => money(r.balanceAfter) },
          { accessor: 'customerNo', title: 'Client' },
          { accessor: 'cardNo', title: 'Carte' },
          { accessor: 'operator', title: 'Opérateur' },
          {
            accessor: 'actions',
            title: '',
            textAlign: 'right',
            hidden: !canReverse,
            render: (r) =>
              r.status === 'VALIDEE' && ['RECHARGE', 'ACHAT', 'AJUSTEMENT'].includes(r.type) ? (
                <Button size="compact-xs" variant="light" color="red" onClick={() => setReversing(r)}>
                  Annuler
                </Button>
              ) : null,
          },
        ]}
        totalRecords={query.data?.totalElements ?? 0}
        recordsPerPage={PAGE_SIZE}
        page={page}
        onPageChange={setPage}
      />
      <ReverseModal txn={reversing} onClose={() => setReversing(null)} />
    </>
  );
}
