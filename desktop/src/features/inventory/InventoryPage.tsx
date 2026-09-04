import { useState } from 'react';
import { Button, Group, Modal, NumberInput, Select, Stack, Textarea } from '@mantine/core';
import { useDebouncedValue, useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { DataTable } from 'mantine-datatable';
import { useForm } from '@mantine/form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { dateTime, qty } from '../../lib/format';
import { useHasRole } from '../../lib/session';
import { toastError, toastOk } from '../../lib/toast';
import { PageHeader } from '../../components/PageHeader';
import { StatusBadge } from '../../components/StatusBadge';
import type { InventoryMovement, InventoryMovementType, Page, Product } from '../../lib/types';

const PAGE_SIZE = 15;
const MOVE_TYPES: InventoryMovementType[] = ['ENTREE', 'AJUSTEMENT', 'INVENTAIRE'];

function MovementModal({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const qc = useQueryClient();
  const [search, setSearch] = useState('');
  const [debounced] = useDebouncedValue(search, 300);
  const form = useForm({
    initialValues: { type: 'ENTREE' as InventoryMovementType, productId: '', quantity: '' as number | string, reference: '', reason: '' },
    validate: {
      productId: (v) => (v ? null : 'Requis'),
      quantity: (v) => (v === '' ? 'Requis' : null),
      reason: (v, values) => (values.type !== 'ENTREE' && !v.trim() ? 'Motif obligatoire' : null),
    },
  });
  const products = useQuery({
    queryKey: ['products', 'picker', debounced],
    queryFn: async () =>
      (await api.get<Page<Product>>('/api/products', { params: { q: debounced || undefined, size: 20 } })).data.content,
  });
  const submit = useMutation({
    mutationFn: () =>
      api.post('/api/inventory/movements', {
        type: form.values.type,
        productId: Number(form.values.productId),
        quantity: Number(form.values.quantity),
        reference: form.values.reference || null,
        reason: form.values.reason || null,
      }),
    onSuccess: () => {
      qc.invalidateQueries();
      toastOk('Mouvement enregistré');
      onClose();
      form.reset();
    },
    onError: (e) => toastError(e),
  });

  const qtyLabel =
    form.values.type === 'INVENTAIRE'
      ? 'Quantité comptée (nouveau total)'
      : form.values.type === 'AJUSTEMENT'
        ? 'Ajustement (+ ou −)'
        : 'Quantité reçue';

  return (
    <Modal opened={opened} onClose={onClose} title="Nouveau mouvement de stock">
      <form onSubmit={form.onSubmit(() => submit.mutate())}>
        <Stack>
          <Select label="Type" data={MOVE_TYPES} allowDeselect={false} {...form.getInputProps('type')} />
          <Select
            label="Produit"
            required
            searchable
            searchValue={search}
            onSearchChange={setSearch}
            data={(products.data ?? []).map((p) => ({
              value: String(p.id),
              label: `${p.reference} — ${p.name} (stock ${qty(p.stockQuantity)})`,
            }))}
            {...form.getInputProps('productId')}
          />
          <NumberInput label={qtyLabel} required {...form.getInputProps('quantity')} />
          <Textarea
            label={form.values.type === 'ENTREE' ? 'Référence / motif (optionnel)' : 'Motif (obligatoire)'}
            autosize
            minRows={2}
            {...form.getInputProps('reason')}
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Annuler
            </Button>
            <Button type="submit" loading={submit.isPending}>
              Enregistrer
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}

export function InventoryPage() {
  const canMove = useHasRole('ADMIN', 'MANAGER');
  const [page, setPage] = useState(1);
  const [type, setType] = useState<string | null>(null);
  const [open, modal] = useDisclosure(false);

  const query = useQuery({
    queryKey: ['inventory-movements', { page, type }],
    queryFn: async () =>
      (
        await api.get<Page<InventoryMovement>>('/api/inventory/movements', {
          params: { page: page - 1, size: PAGE_SIZE, type: type || undefined },
        })
      ).data,
  });

  return (
    <>
      <PageHeader
        title="Stock"
        subtitle="Mouvements de stock (entrées, ajustements, inventaires)"
        actions={
          canMove && (
            <Button leftSection={<IconPlus size={16} />} onClick={modal.open}>
              Nouveau mouvement
            </Button>
          )
        }
      />
      <Group mb="sm">
        <Select
          placeholder="Type"
          clearable
          data={['ENTREE', 'SORTIE', 'AJUSTEMENT', 'INVENTAIRE']}
          value={type}
          onChange={(v) => {
            setType(v);
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
        noRecordsText="Aucun mouvement"
        columns={[
          { accessor: 'createdAt', title: 'Date', render: (r) => dateTime(r.createdAt) },
          { accessor: 'productReference', title: 'Produit', render: (r) => `${r.productReference} — ${r.productName}` },
          { accessor: 'type', title: 'Type', render: (r) => <StatusBadge value={r.type} /> },
          {
            accessor: 'quantity',
            title: 'Delta',
            render: (r) => (
              <span style={{ color: Number(r.quantity) < 0 ? 'var(--mantine-color-red-7)' : 'var(--mantine-color-teal-7)' }}>
                {Number(r.quantity) > 0 ? '+' : ''}
                {qty(r.quantity)}
              </span>
            ),
          },
          { accessor: 'stockBefore', title: 'Avant', render: (r) => qty(r.stockBefore) },
          { accessor: 'stockAfter', title: 'Après', render: (r) => qty(r.stockAfter) },
          { accessor: 'reference', title: 'Réf.' },
          { accessor: 'reason', title: 'Motif' },
          { accessor: 'operator', title: 'Opérateur' },
        ]}
        totalRecords={query.data?.totalElements ?? 0}
        recordsPerPage={PAGE_SIZE}
        page={page}
        onPageChange={setPage}
      />
      <MovementModal opened={open} onClose={modal.close} />
    </>
  );
}
