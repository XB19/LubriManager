import { Button, Group, Modal, Select, Stack, Textarea, TextInput } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { toastError, toastOk } from '../../lib/toast';
import type { Customer, CustomerType } from '../../lib/types';

const TYPES: CustomerType[] = ['PARTICULIER', 'ENTREPRISE', 'ADMINISTRATION'];

export function CustomerForm({
  opened,
  onClose,
  existing,
}: {
  opened: boolean;
  onClose: () => void;
  existing?: Customer | null;
}) {
  const qc = useQueryClient();
  const form = useForm({
    initialValues: {
      fullName: existing?.fullName ?? '',
      companyName: existing?.companyName ?? '',
      phone: existing?.phone ?? '',
      email: existing?.email ?? '',
      address: existing?.address ?? '',
      type: (existing?.type ?? 'PARTICULIER') as CustomerType,
      notes: existing?.notes ?? '',
    },
    validate: { fullName: (v) => (v.trim() ? null : 'Requis') },
  });

  const save = useMutation({
    mutationFn: async (values: typeof form.values) => {
      const payload = { ...values, companyName: values.companyName || null, email: values.email || null };
      if (existing) return api.put(`/api/customers/${existing.id}`, payload);
      return api.post('/api/customers', payload);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['customers'] });
      qc.invalidateQueries({ queryKey: ['customer'] });
      toastOk(existing ? 'Client modifié' : 'Client créé');
      onClose();
      form.reset();
    },
    onError: (e) => toastError(e),
  });

  return (
    <Modal opened={opened} onClose={onClose} title={existing ? 'Modifier le client' : 'Nouveau client'} size="lg">
      <form onSubmit={form.onSubmit((v) => save.mutate(v))}>
        <Stack>
          <TextInput label="Nom complet" required {...form.getInputProps('fullName')} />
          <Group grow>
            <TextInput label="Raison sociale" {...form.getInputProps('companyName')} />
            <Select label="Type" data={TYPES} allowDeselect={false} {...form.getInputProps('type')} />
          </Group>
          <Group grow>
            <TextInput label="Téléphone" {...form.getInputProps('phone')} />
            <TextInput label="E-mail" {...form.getInputProps('email')} />
          </Group>
          <TextInput label="Adresse" {...form.getInputProps('address')} />
          <Textarea label="Observations" autosize minRows={2} {...form.getInputProps('notes')} />
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
