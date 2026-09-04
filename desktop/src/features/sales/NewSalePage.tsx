import { useMemo, useState } from 'react';
import {
  ActionIcon,
  Alert,
  Button,
  Card,
  Group,
  NumberInput,
  Select,
  Stack,
  Table,
  Text,
  Title,
} from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { IconTrash } from '@tabler/icons-react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { api, newIdempotencyKey } from '../../lib/api';
import { money, qty } from '../../lib/format';
import { toastError, toastOk } from '../../lib/toast';
import { PageHeader } from '../../components/PageHeader';
import { CardPicker } from '../../components/CardPicker';
import type { Card as CardT, Page, Product } from '../../lib/types';

interface Line {
  product: Product;
  quantity: number;
}

export function NewSalePage() {
  const navigate = useNavigate();
  const [card, setCard] = useState<CardT | null>(null);
  const [lines, setLines] = useState<Line[]>([]);
  const [search, setSearch] = useState('');
  const [debounced] = useDebouncedValue(search, 300);
  const [idemKey] = useState(newIdempotencyKey());

  const products = useQuery({
    queryKey: ['products', 'sale-picker', debounced],
    queryFn: async () =>
      (
        await api.get<Page<Product>>('/api/products', {
          params: { q: debounced || undefined, size: 20, status: 'ACTIF' },
        })
      ).data.content,
  });

  const total = useMemo(
    () => lines.reduce((sum, l) => sum + Number(l.product.unitPrice) * l.quantity, 0),
    [lines],
  );
  const balance = card ? Number(card.balance) : 0;
  const insufficient = !!card && total > balance;

  const submit = useMutation({
    mutationFn: () =>
      api.post(
        '/api/sales',
        {
          cardId: card!.id,
          items: lines.map((l) => ({ productId: l.product.id, quantity: l.quantity })),
        },
        { headers: { 'Idempotency-Key': idemKey } },
      ),
    onSuccess: (res) => {
      toastOk(`Vente ${res.data.reference} enregistrée`);
      navigate('/ventes');
    },
    onError: (e) => toastError(e),
  });

  function addProduct(id: string | null) {
    const p = (products.data ?? []).find((x) => String(x.id) === id);
    if (!p) return;
    setLines((prev) => {
      const existing = prev.find((l) => l.product.id === p.id);
      if (existing) return prev.map((l) => (l.product.id === p.id ? { ...l, quantity: l.quantity + 1 } : l));
      return [...prev, { product: p, quantity: 1 }];
    });
  }

  return (
    <>
      <PageHeader title="Nouvelle vente" subtitle="Point de vente" />
      <Stack maw={900}>
        <Card withBorder padding="md" radius="md">
          <CardPicker onPick={setCard} label="Carte du client" />
          {card && (
            <Group mt="sm" justify="space-between">
              <Text>
                {card.customerName} — {card.cardNo}
              </Text>
              <Text fw={700}>Solde : {money(card.balance)}</Text>
            </Group>
          )}
        </Card>

        <Card withBorder padding="md" radius="md">
          <Title order={5} mb="xs">
            Panier
          </Title>
          <Select
            placeholder="Ajouter un produit…"
            searchable
            searchValue={search}
            onSearchChange={setSearch}
            value={null}
            onChange={addProduct}
            data={(products.data ?? []).map((p) => ({
              value: String(p.id),
              label: `${p.reference} — ${p.name} · ${money(p.unitPrice)} · stock ${qty(p.stockQuantity)}`,
            }))}
            mb="sm"
          />
          <Table>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Produit</Table.Th>
                <Table.Th w={120}>Qté</Table.Th>
                <Table.Th ta="right">P.U.</Table.Th>
                <Table.Th ta="right">Total</Table.Th>
                <Table.Th w={40} />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {lines.map((l) => (
                <Table.Tr key={l.product.id}>
                  <Table.Td>{l.product.name}</Table.Td>
                  <Table.Td>
                    <NumberInput
                      size="xs"
                      min={1}
                      value={l.quantity}
                      onChange={(v) =>
                        setLines((prev) =>
                          prev.map((x) => (x.product.id === l.product.id ? { ...x, quantity: Number(v) || 1 } : x)),
                        )
                      }
                    />
                  </Table.Td>
                  <Table.Td ta="right">{money(l.product.unitPrice)}</Table.Td>
                  <Table.Td ta="right">{money(Number(l.product.unitPrice) * l.quantity)}</Table.Td>
                  <Table.Td>
                    <ActionIcon
                      variant="subtle"
                      color="red"
                      onClick={() => setLines((prev) => prev.filter((x) => x.product.id !== l.product.id))}
                    >
                      <IconTrash size={16} />
                    </ActionIcon>
                  </Table.Td>
                </Table.Tr>
              ))}
              {lines.length === 0 && (
                <Table.Tr>
                  <Table.Td colSpan={5}>
                    <Text c="dimmed">Panier vide.</Text>
                  </Table.Td>
                </Table.Tr>
              )}
            </Table.Tbody>
          </Table>

          <Group justify="space-between" mt="md">
            <Text fw={700} size="lg">
              Total : {money(total)}
            </Text>
            {card && (
              <Text c={insufficient ? 'red' : 'dimmed'}>
                Solde après : {money(balance - total)}
              </Text>
            )}
          </Group>
          {insufficient && (
            <Alert color="red" mt="sm">
              Solde insuffisant pour cette vente.
            </Alert>
          )}

          <Group justify="flex-end" mt="md">
            <Button variant="default" onClick={() => navigate('/ventes')}>
              Annuler
            </Button>
            <Button
              disabled={!card || lines.length === 0 || insufficient}
              loading={submit.isPending}
              onClick={() => submit.mutate()}
            >
              Enregistrer la vente
            </Button>
          </Group>
        </Card>
      </Stack>
    </>
  );
}
