import { Routes, Route, Navigate } from 'react-router-dom';
import { RequireAuth } from './components/RequireAuth';
import { AppLayout } from './components/AppLayout';
import { LoginPage } from './features/auth/LoginPage';
import { DashboardPage } from './features/dashboard/DashboardPage';
import { CustomersPage } from './features/customers/CustomersPage';
import { CustomerDetailPage } from './features/customers/CustomerDetailPage';
import { CardsPage } from './features/cards/CardsPage';
import { RechargesPage } from './features/recharges/RechargesPage';
import { PurchasesPage } from './features/purchases/PurchasesPage';
import { NewPurchasePage } from './features/purchases/NewPurchasePage';
import { TransactionsPage } from './features/transactions/TransactionsPage';
import { ProductsPage } from './features/products/ProductsPage';
import { ReportsPage } from './features/reports/ReportsPage';
import { AuditPage } from './features/audit/AuditPage';
import { UsersPage } from './features/users/UsersPage';
import { SettingsPage } from './features/settings/SettingsPage';

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<DashboardPage />} />
        <Route path="/clients" element={<CustomersPage />} />
        <Route path="/clients/:id" element={<CustomerDetailPage />} />
        <Route path="/cartes" element={<CardsPage />} />
        <Route path="/recharges" element={<RechargesPage />} />
        <Route path="/achats" element={<PurchasesPage />} />
        <Route path="/achats/nouveau" element={<NewPurchasePage />} />
        <Route path="/transactions" element={<TransactionsPage />} />
        <Route path="/produits" element={<ProductsPage />} />
        <Route
          path="/rapports"
          element={
            <RequireAuth roles={['ADMIN', 'MANAGER', 'AUDITEUR']}>
              <ReportsPage />
            </RequireAuth>
          }
        />
        <Route
          path="/audit"
          element={
            <RequireAuth roles={['ADMIN', 'MANAGER', 'AUDITEUR']}>
              <AuditPage />
            </RequireAuth>
          }
        />
        <Route
          path="/utilisateurs"
          element={
            <RequireAuth roles={['ADMIN']}>
              <UsersPage />
            </RequireAuth>
          }
        />
        <Route path="/parametres" element={<SettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
