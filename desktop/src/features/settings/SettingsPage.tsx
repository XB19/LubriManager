import { Button, Card, Group, PasswordInput, Stack, Text, TextInput, Title } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { api, getApiBaseUrl, setApiBaseUrl } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import { useMe, ROLE_LABELS } from '../../lib/session';
import { toastError, toastOk } from '../../lib/toast';
import { PageHeader } from '../../components/PageHeader';

export function SettingsPage() {
  const me = useMe();
  const clear = useAuth((s) => s.clear);
  const [baseUrl, setBaseUrl] = useState(getApiBaseUrl());

  const pwd = useForm({
    initialValues: { currentPassword: '', newPassword: '', confirm: '' },
    validate: {
      newPassword: (v) => (v.length >= 10 ? null : '10 caractères min.'),
      confirm: (v, values) => (v === values.newPassword ? null : 'Ne correspond pas'),
    },
  });

  const changePwd = useMutation({
    mutationFn: () =>
      api.post('/api/users/me/password', {
        currentPassword: pwd.values.currentPassword,
        newPassword: pwd.values.newPassword,
      }),
    onSuccess: () => {
      toastOk('Mot de passe modifié — reconnexion nécessaire');
      clear();
    },
    onError: (e) => toastError(e),
  });

  return (
    <>
      <PageHeader title="Paramètres" />
      <Stack maw={520}>
        <Card withBorder padding="md" radius="md">
          <Title order={5} mb="xs">
            Mon compte
          </Title>
          <Text size="sm">
            {me?.fullName} — {me && ROLE_LABELS[me.role]}
          </Text>
          <Text size="sm" c="dimmed">
            {me?.username} · {me?.email ?? 'pas d’e-mail'}
          </Text>
        </Card>

        <Card withBorder padding="md" radius="md">
          <Title order={5} mb="xs">
            Changer mon mot de passe
          </Title>
          <form onSubmit={pwd.onSubmit(() => changePwd.mutate())}>
            <Stack>
              <PasswordInput label="Mot de passe actuel" required {...pwd.getInputProps('currentPassword')} />
              <PasswordInput label="Nouveau mot de passe" required {...pwd.getInputProps('newPassword')} />
              <PasswordInput label="Confirmer" required {...pwd.getInputProps('confirm')} />
              <Group justify="flex-end">
                <Button type="submit" loading={changePwd.isPending}>
                  Modifier
                </Button>
              </Group>
            </Stack>
          </form>
        </Card>

        <Card withBorder padding="md" radius="md">
          <Title order={5} mb="xs">
            Serveur
          </Title>
          <TextInput
            label="URL de l'API"
            value={baseUrl}
            onChange={(e) => setBaseUrl(e.currentTarget.value)}
            description="Modifie l'adresse du backend. Rechargez la page après changement."
          />
          <Group justify="flex-end" mt="sm">
            <Button
              variant="light"
              onClick={() => {
                setApiBaseUrl(baseUrl);
                toastOk('URL enregistrée — rechargez la page');
              }}
            >
              Enregistrer
            </Button>
          </Group>
        </Card>
      </Stack>
    </>
  );
}
