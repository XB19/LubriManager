import { ActionIcon, Indicator, Menu, ScrollArea, Text, Stack, Group, Button } from '@mantine/core';
import { IconBell } from '@tabler/icons-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import { fromNow } from '../lib/format';
import type { Notification, Page } from '../lib/types';
import { StatusBadge } from './StatusBadge';

export function NotificationBell() {
  const qc = useQueryClient();

  const count = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: async () => (await api.get<{ unread: number }>('/api/notifications/unread-count')).data.unread,
    refetchInterval: 30_000,
  });

  const list = useQuery({
    queryKey: ['notifications', 'recent'],
    queryFn: async () =>
      (await api.get<Page<Notification>>('/api/notifications', { params: { size: 8 } })).data.content,
  });

  const markAll = useMutation({
    mutationFn: () => api.post('/api/notifications/read-all'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const unread = count.data ?? 0;

  return (
    <Menu shadow="md" width={360} position="bottom-end" onOpen={() => list.refetch()}>
      <Menu.Target>
        <Indicator label={unread} size={16} disabled={unread === 0} color="red">
          <ActionIcon variant="subtle" size="lg" aria-label="Notifications">
            <IconBell size={20} />
          </ActionIcon>
        </Indicator>
      </Menu.Target>
      <Menu.Dropdown>
        <Group justify="space-between" px="xs" py={4}>
          <Text fw={600} size="sm">
            Notifications
          </Text>
          <Button size="compact-xs" variant="subtle" onClick={() => markAll.mutate()} disabled={unread === 0}>
            Tout lire
          </Button>
        </Group>
        <Menu.Divider />
        <ScrollArea.Autosize mah={360}>
          {(list.data ?? []).length === 0 && (
            <Text c="dimmed" size="sm" ta="center" py="md">
              Aucune notification
            </Text>
          )}
          <Stack gap={0}>
            {(list.data ?? []).map((n) => (
              <div key={n.id} style={{ padding: '8px 12px', opacity: n.read ? 0.55 : 1 }}>
                <Group gap="xs" wrap="nowrap">
                  <StatusBadge value={n.severity} />
                  <Text size="sm" fw={600}>
                    {n.title}
                  </Text>
                </Group>
                {n.body && (
                  <Text size="xs" c="dimmed">
                    {n.body}
                  </Text>
                )}
                <Text size="xs" c="dimmed">
                  {fromNow(n.createdAt)}
                </Text>
              </div>
            ))}
          </Stack>
        </ScrollArea.Autosize>
      </Menu.Dropdown>
    </Menu>
  );
}
