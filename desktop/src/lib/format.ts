import dayjs from 'dayjs';
import 'dayjs/locale/fr';
import relativeTime from 'dayjs/plugin/relativeTime';

dayjs.extend(relativeTime);
dayjs.locale('fr');

const moneyFmt = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 0 });

/** Affiche un montant FCFA (le backend envoie des chaînes comme "50000.00"). */
export function money(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—';
  const n = typeof value === 'string' ? Number(value) : value;
  if (Number.isNaN(n)) return String(value);
  return `${moneyFmt.format(n)} FCFA`;
}

export function qty(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—';
  const n = typeof value === 'string' ? Number(value) : value;
  if (Number.isNaN(n)) return String(value);
  return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 }).format(n);
}

export function dateTime(value: string | null | undefined): string {
  return value ? dayjs(value).format('DD/MM/YYYY HH:mm') : '—';
}

export function date(value: string | null | undefined): string {
  return value ? dayjs(value).format('DD/MM/YYYY') : '—';
}

export function fromNow(value: string | null | undefined): string {
  return value ? dayjs(value).fromNow() : '';
}

export function todayIso(): string {
  return dayjs().format('YYYY-MM-DD');
}
