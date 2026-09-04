import { useState } from 'react';
import { Button, Group, Modal, PasswordInput, Select, Stack, Switch, TextInput } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { IconPlus } from '@tabler/icons-react';
import { DataTable } from 'mantine-datatable';
import { useForm } from '@mantine/form';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../lib/api';
import { dateTime } from '../../lib/format';
import { ROLE_LABELS } from '../../lib/session';
import { toastError, toastOk } from '../../lib/toast';
import { PageHeader } from '../../components/PageHeader';
import { StatusBadge } from '../../components/StatusBadge';
import type { Page, Role, User } from '../../lib/types';

const PAGE_SIZE = 15;
const ROLES: Role[] = ['ADMIN', 'MANAGER', 'AGENT', 'AUDITEUR'];

function UserForm({ opened, onClose }: { opened: boolean; onClose: () => void }) {
  const qc = useQueryClient();
  const form = useForm({
    initialValues: { username: '', fullName: '', email: '', role: 'AGENT' as Role, password: '' },
    validate: {
      username: (v) => (v.trim().length >= 3 ? null : '3 caractères min.'),
      fullName: (v) => (v.trim() ? null : 'Requis'),
      password: (v) => (v.length >= 10 ? null : '10 caractères min.'),
    },
  });
  const save = useMutation({
    mutationFn: () =>
      api.post('/api/users', {
        username: form.values.username,
        fullName: form.values.fullName,
        email: form.values.email || null,
        role: form.values.role,
        password: form.values.password,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
      toastOk('Utilisateur créé');
      onClose();
      form.reset();
    },
    onError: (e) => toastError(e),
  });
  return (
    <Modal opened={opened} onClose={onClose} title="Nouvel utilisateur">
      <form onSubmit={form.onSubmit(() => save.mutate())}>
        <Stack>
          <TextInput label="Nom d'utilisateur" required {...form.getInputProps('username')} />
          <TextInput label="Nom complet" required {...form.getInputProps('fullName')} />
          <TextInput label="E-mail" {...form.getInputProps('email')} />
          <Select
            label="Rôle"
            data={ROLES.map((r) => ({ value: r, label: ROLE_LABELS[r] }))}
            allowDeselect={false}
            {...form.getInputProps('role')}
          />
          <PasswordInput label="Mot de passe (≥ 10 caractères)" required {...form.getInputProps('password')} />
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

export function UsersPage() {
  const qc = useQueryClient();
  const [page, setPage] = useState(1);
  const [open, modal] = useDisclosure(false);

  const query = useQuery({
    queryKey: ['users', { page }],
    queryFn: async () =>
      (await api.get<Page<User>>('/api/users', { params: { page: page - 1, size: PAGE_SIZE } })).data,
  });

  const setActive = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) =>
      api.patch(`/api/users/${id}/status`, { active }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
      toastOk('Statut modifié');
    },
    onError: (e) => toastError(e),
  });

  return (
    <>
      <PageHeader
        title="Utilisateurs"
        actions={
          <Button leftSection={<IconPlus size={16} />} onClick={modal.open}>
            Nouvel utilisateur
          </Button>
        }
      />
      <DataTable
        withTableBorder
        borderRadius="sm"
        minHeight={200}
        records={query.data?.content ?? []}
        fetching={query.isFetching}
        noRecordsText="Aucun utilisateur"
        columns={[
          { accessor: 'username', title: "Nom d'utilisateur" },
          { accessor: 'fullName', title: 'Nom complet' },
          { accessor: 'email', title: 'E-mail' },
          { accessor: 'role', title: 'Rôle', render: (r) => ROLE_LABELS[r.role] },
          {
            accessor: 'active',
            title: 'Actif',
            render: (r) => <StatusBadge value={r.active ? 'ACTIF' : 'INACTIF'} />,
          },
          { accessor: 'lastLoginAt', title: 'Dernière connexion', render: (r) => dateTime(r.lastLoginAt) },
          {
            accessor: 'actions',
            title: '',
            textAlign: 'right',
            render: (r) => (
              <Switch
                checked={r.active}
                onChange={(e) => setActive.mutate({ id: r.id, active: e.currentTarget.checked })}
              />
            ),
          },
        ]}
        totalRecords={query.data?.totalElements ?? 0}
        recordsPerPage={PAGE_SIZE}
        page={page}
        onPageChange={setPage}
      />
      <UserForm opened={open} onClose={modal.close} />
    </>
  );
}
