import { useState } from 'react';
import { Select } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import type { Customer, Page } from '../lib/types';

export function CustomerPicker({
  value,
  onChange,
  label = 'Client',
  required,
}: {
  value: string | null;
  onChange: (id: string | null) => void;
  label?: string;
  required?: boolean;
}) {
  const [search, setSearch] = useState('');
  const [debounced] = useDebouncedValue(search, 300);
  const q = useQuery({
    queryKey: ['customers', 'picker', debounced],
    queryFn: async () =>
      (
        await api.get<Page<Customer>>('/api/customers', {
          params: { q: debounced || undefined, size: 20, status: 'ACTIF' },
        })
      ).data.content,
  });

  return (
    <Select
      label={label}
      required={required}
      placeholder="Rechercher un client…"
      searchable
      searchValue={search}
      onSearchChange={setSearch}
      nothingFoundMessage="Aucun client"
      value={value}
      onChange={onChange}
      data={(q.data ?? []).map((c) => ({
        value: String(c.id),
        label: `${c.customerNo} — ${c.fullName}`,
      }))}
    />
  );
}
