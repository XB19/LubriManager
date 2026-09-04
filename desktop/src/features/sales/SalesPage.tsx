import { useState } from 'react';
import { Button, Group, Modal, Stack, Table, Text } from '@mantine/core';
import { IconPlus } from '@tabler/icons-react';
import { DataTable } from 'mantine-datatable';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api } from '../../lib/api';
import { dateTime, money, qty } from '../../lib/format';
import { PageHeader } from '../../components/PageHeader';
import { StatusBadge } from '../../components/StatusBadge';
import type { Page, Sale, SaleSummary } from '../../lib/types';

const PAGE_SIZE = 15;

function SaleDetail({ id, onClose }: { id: number | null; onClose: () => void }) {
  const q = useQuery({
    queryKey: ['sale', id],
    queryFn: async () => (await api.get<Sale>(`/api/sales/${id}`)).data,
    enabled: !!id,
  });
  return (
    <Modal opened={!!id} onClose={onClose} title={q.data ? `Vente ${q.data.reference}` : 'Vente'} size="lg">
      {q.data && (
        <Stack>
          <Group justify="space-between">
            <Text>
              {q.data.customerName} — {q.data.cardNo}
            </Text>
            <StatusBadge value={q.data.status} />
          </Group>
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Produit</Table.Th>
                <Table.Th>Qté</Table.Th>
                <Table.Th ta="right">P.U.</Table.Th>
                <Table.Th ta="right">Total</Table.Th>
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {q.data.items.map((it, i) => (
                <Table.Tr key={i}>
                  <Table.Td>
                    {it.productReference} — {it.productName}
                  </Table.Td>
                  <Table.Td>{qty(it.quantity)}</Table.Td>
                  <Table.Td ta="right">{money(it.unitPrice)}</Table.Td>
                  <Table.Td ta="right">{money(it.lineTotal)}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
          <Group justify="space-between">
            <Text c="dimmed">Dont TVA : {money(q.data.tax)}</Text>
            <Text fw={700}>Total : {money(q.data.total)}</Text>
          </Group>
          <Text size="sm" c="dimmed">
            Solde {money(q.data.balanceBefore)} → {money(q.data.balanceAfter)} · par {q.data.operator} ·{' '}
            {dateTime(q.data.createdAt)}
          </Text>
        </Stack>
      )}
    </Modal>
  );
}

export function SalesPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(1);
  const [detailId, setDetailId] = useState<number | null>(null);

  const query = useQuery({
    queryKey: ['sales', { page }],
    queryFn: async () =>
      (await api.get<Page<SaleSummary>>('/api/sales', { params: { page: page - 1, size: PAGE_SIZE } })).data,
  });

  return (
    <>
      <PageHeader
        title="Ventes"
        actions={
          <Button leftSection={<IconPlus size={16} />} onClick={() => navigate('/ventes/nouvelle')}>
            Nouvelle vente
          </Button>
        }
      />
      <DataTable
        withTableBorder
        borderRadius="sm"
        minHeight={200}
        highlightOnHover
        records={query.data?.content ?? []}
        fetching={query.isFetching}
        noRecordsText="Aucune vente"
        onRowClick={({ record }) => setDetailId(record.id)}
        columns={[
          { accessor: 'reference', title: 'Référence', width: 200 },
          { accessor: 'customerNo', title: 'Client' },
          { accessor: 'cardNo', title: 'Carte' },
          { accessor: 'total', title: 'Total', render: (r) => money(r.total) },
          { accessor: 'status', title: 'Statut', render: (r) => <StatusBadge value={r.status} /> },
          { accessor: 'operator', title: 'Opérateur' },
          { accessor: 'createdAt', title: 'Date', render: (r) => dateTime(r.createdAt) },
        ]}
        totalRecords={query.data?.totalElements ?? 0}
        recordsPerPage={PAGE_SIZE}
        page={page}
        onPageChange={setPage}
      />
      <SaleDetail id={detailId} onClose={() => setDetailId(null)} />
    </>
  );
}
