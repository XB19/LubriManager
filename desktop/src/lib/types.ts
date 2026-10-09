// Types TS reflétant les DTO du backend Spring Boot (§39). Écrits à la main pour
// l'instant ; pourront être générés depuis openapi.json plus tard.

export type Role = 'ADMIN' | 'MANAGER' | 'AGENT' | 'AUDITEUR';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path?: string;
  requestId?: string;
  errors?: { field: string; message: string }[];
}

export interface MeResponse {
  id: number;
  username: string;
  fullName: string;
  email: string | null;
  role: Role;
  branchId: number;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken: string;
  user: MeResponse;
}

// ---- Clients -------------------------------------------------------------
export type CustomerType = 'PARTICULIER' | 'ENTREPRISE' | 'ADMINISTRATION';
export type CustomerStatus = 'ACTIF' | 'SUSPENDU' | 'BLOQUE' | 'ARCHIVE';

export interface Customer {
  id: number;
  customerNo: string;
  fullName: string;
  companyName: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  type: CustomerType;
  status: CustomerStatus;
  notes: string | null;
  branchId: number;
  createdAt: string;
  updatedAt: string;
}

// ---- Comptes ----------------------------------------------------------------
export interface Account {
  id: number;
  customerId: number;
  balance: string;
  currency: string;
  status: 'ACTIF' | 'GELE' | 'CLOS';
  createdAt: string;
  updatedAt: string;
}

// ---- Cartes ---------------------------------------------------------------
export type CardStatus =
  | 'EN_ATTENTE'
  | 'ACTIVE'
  | 'SUSPENDUE'
  | 'BLOQUEE'
  | 'PERDUE'
  | 'EXPIREE'
  | 'DESACTIVEE';

export interface Card {
  id: number;
  cardNo: string;
  externalNo: string | null;
  customerId: number;
  customerNo: string;
  customerName: string;
  accountId: number;
  balance: string;
  status: CardStatus;
  issuedAt: string;
  activatedAt: string | null;
  expiresAt: string | null;
  createdAt: string;
}

// ---- Produits -------------------------------------------------
export interface Product {
  id: number;
  reference: string;
  name: string;
  brand: string;
  unit: string;
  unitPrice: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

// ---- Transactions ------------------------------------------------------------
export type TransactionType =
  | 'RECHARGE'
  | 'ACHAT'
  | 'REMBOURSEMENT'
  | 'ANNULATION'
  | 'AJUSTEMENT'
  | 'CORRECTION';
export type TransactionStatus = 'EN_ATTENTE' | 'VALIDEE' | 'REFUSEE' | 'ANNULEE';

export interface Transaction {
  id: number;
  reference: string;
  type: TransactionType;
  status: TransactionStatus;
  amount: string;
  balanceBefore: string;
  balanceAfter: string;
  accountId: number;
  customerId: number;
  customerNo: string;
  customerName: string;
  cardNo: string | null;
  operator: string;
  description: string | null;
  createdAt: string;
}

// ---- Recharges -------------------------------------------------------------
export interface Recharge {
  id: number;
  reference: string;
  transactionId: number;
  status: TransactionStatus;
  accountId: number;
  customerId: number;
  customerNo: string;
  cardNo: string | null;
  amount: string;
  balanceBefore: string;
  balanceAfter: string;
  operator: string;
  comment: string | null;
  createdAt: string;
}

// ---- Débits carte -------------------------------------------------------------
export interface PurchaseItem {
  productId: number;
  productReference: string;
  productName: string;
  quantity: string;
  unitPrice: string;
  lineTotal: string;
}

export interface Purchase {
  id: number;
  reference: string;
  transactionId: number;
  status: TransactionStatus;
  accountId: number;
  customerId: number;
  customerNo: string;
  customerName: string;
  cardNo: string | null;
  total: string;
  balanceBefore: string;
  balanceAfter: string;
  operator: string;
  description: string | null;
  createdAt: string;
  items: PurchaseItem[];
}

export interface PurchaseSummary {
  id: number;
  reference: string;
  status: TransactionStatus;
  customerId: number;
  customerNo: string;
  cardNo: string | null;
  total: string;
  operator: string;
  createdAt: string;
}

// ---- Tableau de bord ------------------------------------------------------
export interface DashboardPeriod {
  rechargeAmount: string;
  rechargeCount: number;
  salesAmount: string;
  salesCount: number;
}
export interface Dashboard {
  counts: {
    customers: number;
    activeCustomers: number;
    totalCards: number;
    activeCards: number;
    blockedCards: number;
  };
  today: DashboardPeriod;
  week: DashboardPeriod;
  month: DashboardPeriod;
}
export interface AgentDaySummary {
  operator: string;
  salesCount: number;
  salesAmount: string;
  rechargeCount: number;
  rechargeAmount: string;
}

// ---- Audit -------------------------------------------------------------
export interface AuditEntry {
  id: number;
  userId: number | null;
  username: string | null;
  action: string;
  entityType: string | null;
  entityId: number | null;
  reference: string | null;
  summary: string;
  oldValue: string | null;
  newValue: string | null;
  ip: string | null;
  createdAt: string;
}

// ---- Notifications ------------------------------------------------------
export interface Notification {
  id: number;
  type: string;
  severity: 'INFO' | 'ALERTE' | 'CRITIQUE';
  title: string;
  body: string | null;
  entityRef: string | null;
  read: boolean;
  createdAt: string;
}

// ---- Utilisateurs ------------------------------------------------------
export interface User {
  id: number;
  username: string;
  email: string | null;
  fullName: string;
  role: Role;
  active: boolean;
  branchId: number;
  lastLoginAt: string | null;
  createdAt: string;
}

// ---- Reversal -------------------------------------------------------------
export interface Reversal {
  reversalId: number;
  reversalReference: string;
  reversalAmount: string;
  originalId: number;
  originalReference: string;
  originalType: TransactionType;
  originalStatus: TransactionStatus;
  accountId: number;
  balanceBefore: string;
  balanceAfter: string;
  reason: string;
  operator: string;
  createdAt: string;
}
