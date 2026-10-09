import { useState } from 'react';
import { Button } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { DataTable } from 'mantine-datatable';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { dateTime, money } from '../../lib/format';
import { PageHeader } from '../../components/PageHeader';
import { StatusBadge } from '../../components/StatusBadge';
import { RechargeModal } from './RechargeModal';
import type { Page, Recharge } from '../../lib/types';

const PAGE_SIZE = 15;

export function RechargesPage() {
  const [page, setPage] = useState(1);
  const [rechargeOpen, recharge] = useDisclosure(false);

  const query = useQuery({
    queryKey: ['recharges', { page }],
    queryFn: async () =>
      (await api.get<Page<Recharge>>('/api/recharges', { params: { page: page - 1, size: PAGE_SIZE } }))
        .data,
  });

  return (
    <>
      <PageHeader
        title="Recharges"
        subtitle="Crédit d'un compte, en espèces"
        actions={
          <Button leftSection={<IconPlus size={16} />} onClick={recharge.open}>
            Nouvelle recharge
          </Button>
        }
      />
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
          { accessor: 'amount', title: 'Montant', render: (r) => money(r.amount) },
          { accessor: 'balanceAfter', title: 'Solde après', render: (r) => money(r.balanceAfter) },
          { accessor: 'status', title: 'Statut', render: (r) => <StatusBadge value={r.status} /> },
          { accessor: 'operator', title: 'Opérateur' },
          { accessor: 'createdAt', title: 'Date', render: (r) => dateTime(r.createdAt) },
        ]}
        totalRecords={query.data?.totalElements ?? 0}
        recordsPerPage={PAGE_SIZE}
        page={page}
        onPageChange={setPage}
      />

      <RechargeModal opened={rechargeOpen} onClose={recharge.close} />
    </>
  );
}
