import { useEffect, useState } from 'react';
import { Button, Group, Modal, NumberInput, Select, Stack, Textarea, Text } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, newIdempotencyKey } from '../../lib/api';
import { money } from '../../lib/format';
import { toastError, toastOk } from '../../lib/toast';
import type { Card, PaymentMethod } from '../../lib/types';

export function RechargeModal({
  opened,
  onClose,
  card,
}: {
  opened: boolean;
  onClose: () => void;
  card: Pick<Card, 'id' | 'cardNo' | 'balance'>;
}) {
  const qc = useQueryClient();
  const [idemKey, setIdemKey] = useState(newIdempotencyKey());
  const form = useForm({
    initialValues: { amount: 0 as number | string, paymentMethodId: '', comment: '' },
    validate: {
      amount: (v) => (Number(v) > 0 ? null : 'Montant requis'),
      paymentMethodId: (v) => (v ? null : 'Requis'),
    },
  });

  useEffect(() => {
    if (opened) {
      setIdemKey(newIdempotencyKey());
      form.reset();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [opened]);

  const methods = useQuery({
    queryKey: ['payment-methods'],
    queryFn: async () => (await api.get<PaymentMethod[]>('/api/payment-methods')).data,
  });

  const submit = useMutation({
    mutationFn: () =>
      api.post(
        '/api/recharges',
        {
          cardId: card.id,
          amount: Number(form.values.amount),
          paymentMethodId: Number(form.values.paymentMethodId),
          comment: form.values.comment || null,
        },
        { headers: { 'Idempotency-Key': idemKey } },
      ),
    onSuccess: (res) => {
      qc.invalidateQueries();
      toastOk(
        res.data?.status === 'EN_ATTENTE'
          ? 'Recharge enregistrée, en attente de validation'
          : 'Recharge effectuée',
      );
      onClose();
    },
    onError: (e) => toastError(e),
  });

  return (
    <Modal opened={opened} onClose={onClose} title={`Recharger ${card.cardNo}`}>
      <form onSubmit={form.onSubmit(() => submit.mutate())}>
        <Stack>
          <Text size="sm" c="dimmed">
            Solde actuel : {money(card.balance)}
          </Text>
          <NumberInput
            label="Montant (FCFA)"
            required
            min={0}
            step={1000}
            thousandSeparator=" "
            {...form.getInputProps('amount')}
          />
          <Select
            label="Moyen de paiement"
            required
            data={(methods.data ?? []).map((m) => ({ value: String(m.id), label: m.label }))}
            {...form.getInputProps('paymentMethodId')}
          />
          <Textarea label="Commentaire" autosize minRows={2} {...form.getInputProps('comment')} />
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Annuler
            </Button>
            <Button type="submit" loading={submit.isPending}>
              Valider la recharge
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
