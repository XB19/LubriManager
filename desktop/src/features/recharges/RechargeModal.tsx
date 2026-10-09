import { useEffect, useState } from 'react';
import { Button, Group, Modal, NumberInput, Stack, Textarea, Text } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api, newIdempotencyKey } from '../../lib/api';
import { toastError, toastOk } from '../../lib/toast';
import { CustomerPicker } from '../../components/CustomerPicker';

export function RechargeModal({
  opened,
  onClose,
  customer,
}: {
  opened: boolean;
  onClose: () => void;
  customer?: { id: number; fullName: string; customerNo: string };
}) {
  const qc = useQueryClient();
  const [idemKey, setIdemKey] = useState(newIdempotencyKey());
  const [pickedCustomerId, setPickedCustomerId] = useState<string | null>(null);
  const form = useForm({
    initialValues: { amount: 0 as number | string, comment: '' },
    validate: { amount: (v) => (Number(v) > 0 ? null : 'Montant requis') },
  });

  useEffect(() => {
    if (opened) {
      setIdemKey(newIdempotencyKey());
      setPickedCustomerId(null);
      form.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [opened]);

  const customerId = customer ? customer.id : pickedCustomerId ? Number(pickedCustomerId) : null;

  const submit = useMutation({
    mutationFn: () =>
      api.post(
        '/api/recharges',
        {
          customerId,
          amount: Number(form.values.amount),
          comment: form.values.comment || null,
        },
        { headers: { 'Idempotency-Key': idemKey } },
      ),
    onSuccess: () => {
      qc.invalidateQueries();
      toastOk('Recharge effectuée');
      onClose();
    },
    onError: (e) => toastError(e),
  });

  return (
    <Modal
      opened={opened}
      onClose={onClose}
      title={customer ? `Recharger le compte de ${customer.fullName}` : 'Nouvelle recharge (espèces)'}
    >
      <form onSubmit={form.onSubmit(() => submit.mutate())}>
        <Stack>
          {customer ? (
            <Text size="sm" c="dimmed">
              Client {customer.customerNo}
            </Text>
          ) : (
            <CustomerPicker value={pickedCustomerId} onChange={setPickedCustomerId} required />
          )}
          <NumberInput
            label="Montant (FCFA)"
            required
            min={0}
            step={1000}
            thousandSeparator=" "
            {...form.getInputProps('amount')}
          />
          <Textarea label="Commentaire" autosize minRows={2} {...form.getInputProps('comment')} />
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Annuler
            </Button>
            <Button type="submit" disabled={!customerId} loading={submit.isPending}>
              Valider la recharge
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
