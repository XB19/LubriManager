import { Button, Checkbox, Group, Modal, Stack, TextInput } from '@mantine/core';
import { DateInput } from '@mantine/dates';
import { useForm } from '@mantine/form';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { api } from '../../lib/api';
import { toastError, toastOk } from '../../lib/toast';

export function CardForm({
  opened,
  onClose,
  customerId,
}: {
  opened: boolean;
  onClose: () => void;
  customerId: number;
}) {
  const qc = useQueryClient();
  const form = useForm({
    initialValues: { externalNo: '', expiresAt: null as Date | null, activateImmediately: true },
  });

  const save = useMutation({
    mutationFn: () =>
      api.post('/api/cards', {
        customerId,
        externalNo: form.values.externalNo || null,
        expiresAt: form.values.expiresAt ? dayjs(form.values.expiresAt).format('YYYY-MM-DD') : null,
        activateImmediately: form.values.activateImmediately,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['cards'] });
      qc.invalidateQueries({ queryKey: ['customer'] });
      toastOk('Carte créée');
      onClose();
      form.reset();
    },
    onError: (e) => toastError(e),
  });

  return (
    <Modal opened={opened} onClose={onClose} title="Nouvelle carte">
      <form onSubmit={form.onSubmit(() => save.mutate())}>
        <Stack>
          <TextInput
            label="N° externe (badge / QR)"
            placeholder="optionnel"
            {...form.getInputProps('externalNo')}
          />
          <DateInput
            label="Date d'expiration"
            placeholder="optionnel"
            valueFormat="DD/MM/YYYY"
            minDate={new Date()}
            clearable
            {...form.getInputProps('expiresAt')}
          />
          <Checkbox
            label="Activer immédiatement"
            {...form.getInputProps('activateImmediately', { type: 'checkbox' })}
          />
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Annuler
            </Button>
            <Button type="submit" loading={save.isPending}>
              Créer
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
