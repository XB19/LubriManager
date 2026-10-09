import { useState } from 'react';
import { Button, Card, Group, Select, Stack, Table, Text, ScrollArea } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { IconDownload, IconTable } from '@tabler/icons-react';
import { useMutation } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { api, getApiBaseUrl } from '../../lib/api';
import { toastError } from '../../lib/toast';
import { useAuth } from '../../lib/auth';
import { PageHeader } from '../../components/PageHeader';

interface ReportData {
  title: string;
  columns: string[];
  rows: unknown[][];
}

const TYPES = [
  { value: 'purchases', label: 'Débits carte' },
  { value: 'recharges', label: 'Recharges' },
  { value: 'transactions', label: 'Transactions' },
  { value: 'cancellations', label: 'Annulations' },
  { value: 'audit', label: "Journal d'audit" },
];

export function ReportsPage() {
  const token = useAuth((s) => s.accessToken);
  const [type, setType] = useState('purchases');
  const [from, setFrom] = useState<Date | null>(dayjs().startOf('month').toDate());
  const [to, setTo] = useState<Date | null>(new Date());
  const [data, setData] = useState<ReportData | null>(null);

  const params = () => ({
    from: from ? dayjs(from).format('YYYY-MM-DD') : undefined,
    to: to ? dayjs(to).format('YYYY-MM-DD') : undefined,
  });

  const preview = useMutation({
    mutationFn: async () =>
      (await api.get<ReportData>(`/api/reports/${type}`, { params: { ...params(), format: 'json' } })).data,
    onSuccess: setData,
    onError: (e) => toastError(e),
  });

  async function downloadCsv() {
    try {
      const usp = new URLSearchParams({ format: 'csv' });
      const p = params();
      if (p.from) usp.set('from', p.from);
      if (p.to) usp.set('to', p.to);
      const res = await fetch(`${getApiBaseUrl()}/api/reports/${type}?${usp}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) throw new Error(`Erreur ${res.status}`);
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `rapport-${type}-${dayjs().format('YYYY-MM-DD')}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      toastError(e);
    }
  }

  return (
    <>
      <PageHeader title="Rapports" subtitle="Aperçu à l'écran ou export CSV (compatible Excel)" />
      <Card withBorder padding="md" radius="md" mb="md">
        <Group align="flex-end">
          <Select label="Type de rapport" data={TYPES} value={type} onChange={(v) => setType(v ?? 'purchases')} w={200} />
          <DateInput label="Du" valueFormat="DD/MM/YYYY" value={from} onChange={setFrom} clearable />
          <DateInput label="Au" valueFormat="DD/MM/YYYY" value={to} onChange={setTo} clearable />
          <Button leftSection={<IconTable size={16} />} loading={preview.isPending} onClick={() => preview.mutate()}>
            Aperçu
          </Button>
          <Button variant="light" leftSection={<IconDownload size={16} />} onClick={downloadCsv}>
            Télécharger CSV
          </Button>
        </Group>
      </Card>

      {data && (
        <Stack>
          <Text fw={600}>
            {data.title} — {data.rows.length} ligne(s)
          </Text>
          <ScrollArea>
            <Table striped withTableBorder>
              <Table.Thead>
                <Table.Tr>
                  {data.columns.map((c) => (
                    <Table.Th key={c}>{c}</Table.Th>
                  ))}
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {data.rows.slice(0, 200).map((row, i) => (
                  <Table.Tr key={i}>
                    {row.map((cell, j) => (
                      <Table.Td key={j}>{cell === null ? '' : String(cell)}</Table.Td>
                    ))}
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </ScrollArea>
          {data.rows.length > 200 && (
            <Text size="sm" c="dimmed">
              Aperçu limité à 200 lignes — l'export CSV contient tout.
            </Text>
          )}
        </Stack>
      )}
    </>
  );
}
