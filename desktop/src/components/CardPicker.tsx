import { useState } from 'react';
import { Select } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import type { Card, Page } from '../lib/types';

export function CardPicker({
  onPick,
  label = 'Carte',
  activeOnly = true,
}: {
  onPick: (card: Card | null) => void;
  label?: string;
  activeOnly?: boolean;
}) {
  const [search, setSearch] = useState('');
  const [debounced] = useDebouncedValue(search, 300);
  const [value, setValue] = useState<string | null>(null);
  const q = useQuery({
    queryKey: ['cards', 'picker', debounced, activeOnly],
    queryFn: async () =>
      (
        await api.get<Page<Card>>('/api/cards', {
          params: { q: debounced || undefined, size: 20, status: activeOnly ? 'ACTIVE' : undefined },
        })
      ).data.content,
  });

  return (
    <Select
      label={label}
      placeholder="N° carte ou client…"
      searchable
      searchValue={search}
      onSearchChange={setSearch}
      nothingFoundMessage="Aucune carte"
      value={value}
      onChange={(v) => {
        setValue(v);
        onPick((q.data ?? []).find((c) => String(c.id) === v) ?? null);
      }}
      data={(q.data ?? []).map((c) => ({
        value: String(c.id),
        label: `${c.cardNo} — ${c.customerName}`,
      }))}
    />
  );
}
