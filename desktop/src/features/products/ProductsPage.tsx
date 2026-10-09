import { useState } from 'react';
import { Button, Group, Modal, NumberInput, Stack, Textarea, TextInput } from '@mantine/core';
import { useDebouncedValue, useDisclosure } from '@mantine/hooks';
import { IconPlus, IconSearch } from '@tabler/icons-react';
import { DataTable } from 'mantine-datatable';
import { useForm } from '@mantine/form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { money } from '../../lib/format';
import { useHasRole } from '../../lib/session';
import { toastError, toastOk } from '../../lib/toast';
import { PageHeader } from '../../components/PageHeader';
import type { Page, Product } from '../../lib/types';

const PAGE_SIZE = 15;

function ProductForm({
  opened,
  onClose,
  existing,
}: {
  opened: boolean;
  onClose: () => void;
  existing?: Product | null;
}) {
  const qc = useQueryClient();
  const form = useForm({
    initialValues: {
      name: existing?.name ?? '',
      brand: existing?.brand ?? '',
      unit: existing?.unit ?? '',
      unitPrice: existing ? Number(existing.unitPrice) : (0 as number | string),
      description: existing?.description ?? '',
    },
    validate: {
      name: (v) => (v.trim() ? null : 'Requis'),
      brand: (v) => (v.trim() ? null : 'Requis'),
      unit: (v) => (v.trim() ? null : 'Requis'),
    },
  });

  const save = useMutation({
    mutationFn: () => {
      const body = {
        name: form.values.name,
        brand: form.values.brand,
        unit: form.values.unit,
        unitPrice: Number(form.values.unitPrice),
        description: form.values.description || null,
      };
      return existing
        ? api.put(`/api/products/${existing.id}`, body)
        : api.post('/api/products', body);
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
    <Modal
      opened={opened}
      onClose={onClose}
      title={existing ? 'Modifier le produit' : 'Nouveau produit'}
      size="lg"
    >
      <form onSubmit={form.onSubmit(() => save.mutate())}>
        <Stack>
          <TextInput label="Nom" required {...form.getInputProps('name')} />
          <Group grow>
            <TextInput label="Marque" required {...form.getInputProps('brand')} />
            <TextInput
              label="Unité"
              placeholder="Bidon 5L, Fût 200L…"
              required
              {...form.getInputProps('unit')}
            />
          </Group>
          <NumberInput
            label="Prix (FCFA)"
            required
            min={0}
            thousandSeparator=" "
            {...form.getInputProps('unitPrice')}
          />
          <Textarea
            label="Description (facultatif)"
            autosize
            minRows={2}
            {...form.getInputProps('description')}
          />
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
  const canEdit = useHasRole('ADMIN', 'MANAGER');
  const [page, setPage] = useState(1);
  const [q, setQ] = useState('');
  const [debouncedQ] = useDebouncedValue(q, 300);
  const [formOpen, form] = useDisclosure(false);
  const [editing, setEditing] = useState<Product | null>(null);

  const query = useQuery({
    queryKey: ['products', { page, q: debouncedQ }],
    queryFn: async () =>
      (
        await api.get<Page<Product>>('/api/products', {
          params: { page: page - 1, size: PAGE_SIZE, q: debouncedQ || undefined },
        })
      ).data,
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
      </Group>
      <DataTable
        withTableBorder
        borderRadius="sm"
        minHeight={200}
        records={query.data?.content ?? []}
        fetching={query.isFetching}
        noRecordsText="Aucun produit"
        columns={[
          { accessor: 'reference', title: 'Réf.', width: 120 },
          { accessor: 'name', title: 'Produit' },
          { accessor: 'brand', title: 'Marque' },
          { accessor: 'unit', title: 'Unité' },
          { accessor: 'unitPrice', title: 'Prix', render: (r) => money(r.unitPrice) },
          {
            accessor: 'actions',
            title: '',
            textAlign: 'right',
            hidden: !canEdit,
            render: (r) => (
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
            ),
          },
        ]}
        totalRecords={query.data?.totalElements ?? 0}
        recordsPerPage={PAGE_SIZE}
        page={page}
        onPageChange={setPage}
      />
      <ProductForm opened={formOpen} onClose={form.close} existing={editing} />
    </>
  );
}
