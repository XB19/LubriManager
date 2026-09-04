import { AppShell, Burger, Group, NavLink, ScrollArea, Text, Menu, Avatar, UnstyledButton } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import {
  IconLayoutDashboard,
  IconUsers,
  IconCreditCard,
  IconWallet,
  IconArrowsExchange,
  IconShoppingCart,
  IconPackage,
  IconBoxSeam,
  IconFileText,
  IconUserCog,
  IconHistory,
  IconSettings,
  IconLogout,
} from '@tabler/icons-react';
import { NavLink as RouterNavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import { ROLE_LABELS } from '../lib/session';
import type { Role } from '../lib/types';
import { NotificationBell } from './NotificationBell';

interface NavItem {
  to: string;
  label: string;
  icon: typeof IconUsers;
  roles?: Role[];
}

const NAV: NavItem[] = [
  { to: '/', label: 'Tableau de bord', icon: IconLayoutDashboard },
  { to: '/clients', label: 'Clients', icon: IconUsers },
  { to: '/cartes', label: 'Cartes', icon: IconCreditCard },
  { to: '/recharges', label: 'Recharges', icon: IconWallet },
  { to: '/ventes', label: 'Ventes', icon: IconShoppingCart },
  { to: '/transactions', label: 'Transactions', icon: IconArrowsExchange },
  { to: '/produits', label: 'Produits', icon: IconPackage },
  { to: '/stock', label: 'Stock', icon: IconBoxSeam },
  { to: '/rapports', label: 'Rapports', icon: IconFileText, roles: ['ADMIN', 'MANAGER', 'AUDITEUR'] },
  { to: '/audit', label: 'Audit', icon: IconHistory, roles: ['ADMIN', 'MANAGER', 'AUDITEUR'] },
  { to: '/utilisateurs', label: 'Utilisateurs', icon: IconUserCog, roles: ['ADMIN'] },
  { to: '/parametres', label: 'Paramètres', icon: IconSettings },
];

export function AppLayout() {
  const [opened, { toggle }] = useDisclosure();
  const user = useAuth((s) => s.user)!;
  const clear = useAuth((s) => s.clear);
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const items = NAV.filter((i) => !i.roles || i.roles.includes(user.role));

  return (
    <AppShell
      header={{ height: 56 }}
      navbar={{ width: 240, breakpoint: 'sm', collapsed: { mobile: !opened } }}
      padding="lg"
    >
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between">
          <Group gap="sm">
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" />
            <Text fw={700} c="teal">
              LubriManager
            </Text>
          </Group>
          <Group gap="sm">
            <NotificationBell />
            <Menu position="bottom-end" width={200}>
              <Menu.Target>
                <UnstyledButton>
                  <Group gap="xs">
                    <Avatar color="teal" radius="xl" size="sm">
                      {user.fullName.slice(0, 1).toUpperCase()}
                    </Avatar>
                    <div style={{ lineHeight: 1.1 }}>
                      <Text size="sm" fw={600}>
                        {user.fullName}
                      </Text>
                      <Text size="xs" c="dimmed">
                        {ROLE_LABELS[user.role]}
                      </Text>
                    </div>
                  </Group>
                </UnstyledButton>
              </Menu.Target>
              <Menu.Dropdown>
                <Menu.Item onClick={() => navigate('/parametres')}>Mon compte</Menu.Item>
                <Menu.Item
                  color="red"
                  leftSection={<IconLogout size={16} />}
                  onClick={() => {
                    clear();
                    navigate('/login');
                  }}
                >
                  Déconnexion
                </Menu.Item>
              </Menu.Dropdown>
            </Menu>
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Navbar p="xs">
        <ScrollArea>
          {items.map((i) => (
            <NavLink
              key={i.to}
              component={RouterNavLink}
              to={i.to}
              label={i.label}
              leftSection={<i.icon size={18} />}
              active={i.to === '/' ? pathname === '/' : pathname.startsWith(i.to)}
              onClick={() => opened && toggle()}
            />
          ))}
        </ScrollArea>
      </AppShell.Navbar>

      <AppShell.Main>
        <Outlet />
      </AppShell.Main>
    </AppShell>
  );
}
