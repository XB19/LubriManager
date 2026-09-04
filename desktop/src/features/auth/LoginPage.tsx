import { Button, Card, Center, PasswordInput, Stack, Text, TextInput, Title, Alert } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { getApiBaseUrl, errorMessage } from '../../lib/api';
import { useAuth } from '../../lib/auth';
import type { TokenResponse } from '../../lib/types';

export function LoginPage() {
  const navigate = useNavigate();
  const setSession = useAuth((s) => s.setSession);
  const form = useForm({ initialValues: { username: '', password: '' } });

  const login = useMutation({
    mutationFn: async (values: { username: string; password: string }) => {
      const { data } = await axios.post<TokenResponse>(`${getApiBaseUrl()}/api/auth/login`, values);
      return data;
    },
    onSuccess: (data) => {
      setSession({ accessToken: data.accessToken, refreshToken: data.refreshToken, user: data.user });
      navigate('/', { replace: true });
    },
  });

  return (
    <Center mih="100vh" bg="gray.0">
      <Card shadow="md" radius="md" p="xl" w={380}>
        <Stack>
          <div>
            <Title order={2} c="teal">
              LubriManager
            </Title>
            <Text c="dimmed" size="sm">
              Connectez-vous pour continuer
            </Text>
          </div>
          {login.isError && <Alert color="red">{errorMessage(login.error)}</Alert>}
          <form onSubmit={form.onSubmit((v) => login.mutate(v))}>
            <Stack>
              <TextInput
                label="Nom d'utilisateur ou e-mail"
                required
                autoFocus
                {...form.getInputProps('username')}
              />
              <PasswordInput label="Mot de passe" required {...form.getInputProps('password')} />
              <Button type="submit" loading={login.isPending} fullWidth mt="sm">
                Se connecter
              </Button>
            </Stack>
          </form>
          <Text size="xs" c="dimmed" ta="center">
            API : {getApiBaseUrl()}
          </Text>
        </Stack>
      </Card>
    </Center>
  );
}
