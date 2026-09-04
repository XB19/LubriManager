import { useState } from 'react';
import { Group, Select } from '@mantine/core';
import { DataTable } from 'mantine-datatable';
import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { dateTime } from '../../lib/format';
import { PageHeader } from '../../components/PageHeader';
import type { AuditEntry, Page } from '../../lib/types';

const PAGE_SIZE = 20;

const ACTIONS = [
  'USER_LOGIN_SUCCESS',
  'USER_LOGIN_FAILURE',
  'USER_CREATED',
  'USER_STATUS_CHANGED',
  'CUSTOMER_STATUS_CHANGED',
  'CARD_STATUS_CHANGED',
  'ACCOUNT_ADJUSTED',
  'RECHARGE_CREATED',
  'RECHARGE_VALIDATED',
  'RECHARGE_REJECTED',
  'SALE_COMPLETED',
  'SALE_REFUSED',
  'TRANSACTION_REVERSED',
  'INVENTORY_MOVEMENT',
];

export function AuditPage() {
  const [page, setPage] = useState(1);
  const [action, setAction] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ['audit', { page, action }],
    queryFn: async () =>
      (
        await api.get<Page<AuditEntry>>('/api/audit', {
          params: { page: page - 1, size: PAGE_SIZE, action: action || undefined },
        })
      ).data,
  });

  return (
    <>
      <PageHeader title="Journal d'audit" subtitle="Qui a fait quoi, quand, depuis où" />
      <Group mb="sm">
        <Select
          placeholder="Action"
          clearable
          searchable
          data={ACTIONS}
          value={action}
          onChange={(v) => {
            setAction(v);
            setPage(1);
          }}
          w={280}
        />
      </Group>
      <DataTable
        withTableBorder
        borderRadius="sm"
        minHeight={200}
        records={query.data?.content ?? []}
        fetching={query.isFetching}
        noRecordsText="Aucune entrée"
        columns={[
          { accessor: 'createdAt', title: 'Date', render: (r) => dateTime(r.createdAt), width: 150 },
          { accessor: 'username', title: 'Utilisateur', width: 130 },
          { accessor: 'action', title: 'Action', width: 200 },
          { accessor: 'reference', title: 'Référence' },
          { accessor: 'summary', title: 'Détail' },
          { accessor: 'ip', title: 'IP', width: 120 },
        ]}
        totalRecords={query.data?.totalElements ?? 0}
        recordsPerPage={PAGE_SIZE}
        page={page}
        onPageChange={setPage}
      />
    </>
  );
}
