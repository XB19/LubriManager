import { useState } from 'react';
import { Button, Group, Select, TextInput } from '@mantine/core';
import { useDebouncedValue, useDisclosure } from '@mantine/hooks';
import { IconPlus, IconSearch } from '@tabler/icons-react';
import { DataTable } from 'mantine-datatable';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api } from '../../lib/api';
import { dateTime } from '../../lib/format';
import { PageHeader } from '../../components/PageHeader';
import { StatusBadge } from '../../components/StatusBadge';
import { CustomerForm } from './CustomerForm';
import type { Customer, Page } from '../../lib/types';

const PAGE_SIZE = 15;

export function CustomersPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(1);
  const [q, setQ] = useState('');
  const [type, setType] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [debouncedQ] = useDebouncedValue(q, 300);
  const [formOpen, form] = useDisclosure(false);

  const query = useQuery({
    queryKey: ['customers', { page, q: debouncedQ, type, status }],
    queryFn: async () => {
      const { data } = await api.get<Page<Customer>>('/api/customers', {
        params: {
          page: page - 1,
          size: PAGE_SIZE,
          q: debouncedQ || undefined,
          type: type || undefined,
          status: status || undefined,
        },
      });
      return data;
    },
  });

  return (
    <>
      <PageHeader
        title="Clients"
        actions={
          <Button leftSection={<IconPlus size={16} />} onClick={form.open}>
            Nouveau client
          </Button>
        }
      />
      <Group mb="sm">
        <TextInput
          placeholder="Nom, n° client, téléphone, e-mail…"
          leftSection={<IconSearch size={16} />}
          value={q}
          onChange={(e) => {
            setQ(e.currentTarget.value);
            setPage(1);
          }}
          w={320}
        />
        <Select
          placeholder="Type"
          clearable
          data={['PARTICULIER', 'ENTREPRISE', 'ADMINISTRATION']}
          value={type}
          onChange={(v) => {
            setType(v);
            setPage(1);
          }}
        />
        <Select
          placeholder="Statut"
          clearable
          data={['ACTIF', 'SUSPENDU', 'BLOQUE', 'ARCHIVE']}
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
        noRecordsText="Aucun client"
        onRowClick={({ record }) => navigate(`/clients/${record.id}`)}
        columns={[
          { accessor: 'customerNo', title: 'N° client', width: 130 },
          { accessor: 'fullName', title: 'Nom' },
          { accessor: 'companyName', title: 'Raison sociale' },
          { accessor: 'phone', title: 'Téléphone' },
          { accessor: 'type', title: 'Type' },
          { accessor: 'status', title: 'Statut', render: (c) => <StatusBadge value={c.status} /> },
          { accessor: 'createdAt', title: 'Créé le', render: (c) => dateTime(c.createdAt) },
        ]}
        totalRecords={query.data?.totalElements ?? 0}
        recordsPerPage={PAGE_SIZE}
        page={page}
        onPageChange={setPage}
      />
      <CustomerForm opened={formOpen} onClose={form.close} />
    </>
  );
}
