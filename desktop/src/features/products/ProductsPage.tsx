import { useState } from 'react';
import {
  Button,
  Group,
  Modal,
  NumberInput,
  Select,
  Stack,
  Switch,
  Textarea,
  TextInput,
} from '@mantine/core';
import { useDebouncedValue, useDisclosure } from '@mantine/hooks';
import { IconPlus, IconSearch } from '@tabler/icons-react';
import { DataTable } from 'mantine-datatable';
import { useForm } from '@mantine/form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { money, qty } from '../../lib/format';
import { useHasRole } from '../../lib/session';
import { toastError, toastOk } from '../../lib/toast';
import { PageHeader } from '../../components/PageHeader';
import { StatusBadge } from '../../components/StatusBadge';
import type { Category, Page, Product } from '../../lib/types';

const PAGE_SIZE = 15;

function ProductForm({
  opened,
  onClose,
  existing,
  categories,
}: {
  opened: boolean;
  onClose: () => void;
  existing?: Product | null;
  categories: Category[];
}) {
  const qc = useQueryClient();
  const form = useForm({
    initialValues: {
      name: existing?.name ?? '',
      brand: existing?.brand ?? '',
      categoryId: existing ? String(existing.categoryId) : '',
      unit: existing?.unit ?? '',
      unitPrice: existing ? Number(existing.unitPrice) : (0 as number | string),
      vatRate: existing ? Number(existing.vatRate) : 18,
      alertThreshold: existing ? Number(existing.alertThreshold) : (0 as number | string),
      description: existing?.description ?? '',
    },
    validate: {
      name: (v) => (v.trim() ? null : 'Requis'),
      categoryId: (v) => (v ? null : 'Requis'),
      unit: (v) => (v.trim() ? null : 'Requis'),
    },
  });

  const save = useMutation({
    mutationFn: () => {
      const body = {
        name: form.values.name,
        brand: form.values.brand || null,
        categoryId: Number(form.values.categoryId),
        unit: form.values.unit,
        unitPrice: Number(form.values.unitPrice),
        vatRate: Number(form.values.vatRate),
        alertThreshold: Number(form.values.alertThreshold),
        description: form.values.description || null,
      };
      return existing ? api.put(`/api/products/${existing.id}`, body) : api.post('/api/products', body);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['products'] });
      toastOk(existing ? 'Produit modifié' : 'Produit créé');
      onClose();
      form.reset();
    },
    onError: (e) => toastError(e),
  });

  return (
    <Modal opened={opened} onClose={onClose} title={existing ? 'Modifier le produit' : 'Nouveau produit'} size="lg">
      <form onSubmit={form.onSubmit(() => save.mutate())}>
        <Stack>
          <TextInput label="Nom" required {...form.getInputProps('name')} />
          <Group grow>
            <TextInput label="Marque" {...form.getInputProps('brand')} />
            <Select
              label="Catégorie"
              required
              data={categories.map((c) => ({ value: String(c.id), label: c.name }))}
              {...form.getInputProps('categoryId')}
            />
          </Group>
          <Group grow>
            <TextInput label="Unité" placeholder="Bidon 5L, Fût 200L…" required {...form.getInputProps('unit')} />
            <NumberInput
              label="Prix TTC (FCFA)"
              required
              min={0}
              thousandSeparator=" "
              {...form.getInputProps('unitPrice')}
            />
          </Group>
          <Group grow>
            <NumberInput label="TVA (%)" min={0} max={100} step={0.5} {...form.getInputProps('vatRate')} />
            <NumberInput label="Seuil d'alerte stock" min={0} {...form.getInputProps('alertThreshold')} />
          </Group>
          <Textarea label="Description" autosize minRows={2} {...form.getInputProps('description')} />
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Annuler
            </Button>
            <Button type="submit" loading={save.isPending}>
              Enregistrer
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}

export function ProductsPage() {
  const qc = useQueryClient();
  const canEdit = useHasRole('ADMIN', 'MANAGER');
  const [page, setPage] = useState(1);
  const [q, setQ] = useState('');
  const [debouncedQ] = useDebouncedValue(q, 300);
  const [lowStock, setLowStock] = useState(false);
  const [formOpen, form] = useDisclosure(false);
  const [editing, setEditing] = useState<Product | null>(null);

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: async () => (await api.get<Category[]>('/api/categories', { params: { onlyActive: true } })).data,
  });
  const query = useQuery({
    queryKey: ['products', { page, q: debouncedQ, lowStock }],
    queryFn: async () =>
      (
        await api.get<Page<Product>>('/api/products', {
          params: { page: page - 1, size: PAGE_SIZE, q: debouncedQ || undefined, lowStock },
        })
      ).data,
  });

  const toggleStatus = useMutation({
    mutationFn: ({ id, status }: { id: number; status: 'ACTIF' | 'INACTIF' }) =>
      api.patch(`/api/products/${id}/status`, { status }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['products'] });
      toastOk('Statut modifié');
    },
    onError: (e) => toastError(e),
  });

  return (
    <>
      <PageHeader
        title="Produits"
        actions={
          canEdit && (
            <Button
              leftSection={<IconPlus size={16} />}
              onClick={() => {
                setEditing(null);
                form.open();
              }}
            >
              Nouveau produit
            </Button>
          )
        }
      />
      <Group mb="sm">
        <TextInput
          placeholder="Référence, nom, marque…"
          leftSection={<IconSearch size={16} />}
          value={q}
          onChange={(e) => {
            setQ(e.currentTarget.value);
            setPage(1);
          }}
          w={320}
        />
        <Switch
          label="Stock faible uniquement"
          checked={lowStock}
          onChange={(e) => {
            setLowStock(e.currentTarget.checked);
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
        noRecordsText="Aucun produit"
        rowBackgroundColor={(r) => (r.lowStock ? { light: '#fff4e6', dark: '#3a2a10' } : undefined)}
        columns={[
          { accessor: 'reference', title: 'Réf.', width: 120 },
          { accessor: 'name', title: 'Produit' },
          { accessor: 'brand', title: 'Marque' },
          { accessor: 'categoryName', title: 'Catégorie' },
          { accessor: 'unit', title: 'Unité' },
          { accessor: 'unitPrice', title: 'Prix TTC', render: (r) => money(r.unitPrice) },
          {
            accessor: 'stockQuantity',
            title: 'Stock',
            render: (r) => (
              <span style={{ color: r.lowStock ? 'var(--mantine-color-orange-7)' : undefined }}>
                {qty(r.stockQuantity)} {r.lowStock ? `⚠ (seuil ${qty(r.alertThreshold)})` : ''}
              </span>
            ),
          },
          { accessor: 'status', title: 'Statut', render: (r) => <StatusBadge value={r.status} /> },
          {
            accessor: 'actions',
            title: '',
            textAlign: 'right',
            hidden: !canEdit,
            render: (r) => (
              <Group gap={4} justify="flex-end" wrap="nowrap">
                <Button
                  size="compact-xs"
                  variant="subtle"
                  onClick={() => {
                    setEditing(r);
                    form.open();
                  }}
                >
                  Modifier
                </Button>
                <Button
                  size="compact-xs"
                  variant="light"
                  color={r.status === 'ACTIF' ? 'gray' : 'teal'}
                  onClick={() =>
                    toggleStatus.mutate({ id: r.id, status: r.status === 'ACTIF' ? 'INACTIF' : 'ACTIF' })
                  }
                >
                  {r.status === 'ACTIF' ? 'Désactiver' : 'Activer'}
                </Button>
              </Group>
            ),
          },
        ]}
        totalRecords={query.data?.totalElements ?? 0}
        recordsPerPage={PAGE_SIZE}
        page={page}
        onPageChange={setPage}
      />
      <ProductForm
        opened={formOpen}
        onClose={form.close}
        existing={editing}
        categories={categories.data ?? []}
      />
    </>
  );
}
