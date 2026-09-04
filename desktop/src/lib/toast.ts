import { notifications } from '@mantine/notifications';
import { errorMessage } from './api';

export function toastOk(message: string, title = 'Succès') {
  notifications.show({ color: 'teal', title, message });
}

export function toastError(err: unknown, title = 'Erreur') {
  notifications.show({ color: 'red', title, message: errorMessage(err), autoClose: 6000 });
}

export function toastInfo(message: string, title?: string) {
  notifications.show({ color: 'blue', title, message });
}
