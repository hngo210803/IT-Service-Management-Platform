import {DatePipe} from '@angular/common';
import {HttpErrorResponse} from '@angular/common/http';
import {Component, inject, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {firstValueFrom} from 'rxjs';
import {Api, AssistantAnswer, Status, Ticket} from './api';

@Component({
  selector: 'itsm-root',
  imports: [FormsModule, DatePipe],
  templateUrl: './service-desk.html',
})
export class ServiceDesk {
  private readonly api = inject(Api);
  readonly statuses: {value: Status; label: string}[] = [
    {value: 'OPEN', label: 'Ouvert'},
    {value: 'IN_PROGRESS', label: 'En cours'},
    {value: 'RESOLVED', label: 'Résolu'},
  ];
  readonly tickets = signal<Ticket[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly updating = signal<number | null>(null);
  readonly asking = signal(false);
  readonly ticketError = signal('');
  readonly assistantError = signal('');
  readonly answer = signal<AssistantAnswer | null>(null);
  title = '';
  description = '';
  question = '';

  constructor() {
    void this.loadTickets();
  }

  async loadTickets() {
    this.loading.set(true);
    this.ticketError.set('');
    try {
      this.tickets.set(await firstValueFrom(this.api.list()));
    } catch {
      this.ticketError.set('Impossible de charger les tickets. Vérifie que le backend est lancé.');
    } finally {
      this.loading.set(false);
    }
  }

  async createTicket() {
    if (!this.title.trim() || !this.description.trim() || this.saving() || this.loading()) return;

    this.saving.set(true);
    this.ticketError.set('');
    try {
      const ticket = await firstValueFrom(this.api.create(this.title.trim(), this.description.trim()));
      this.tickets.update(tickets => [ticket, ...tickets]);
      this.title = '';
      this.description = '';
    } catch {
      this.ticketError.set('Impossible de créer le ticket.');
    } finally {
      this.saving.set(false);
    }
  }

  async changeStatus(ticket: Ticket, status: Status, select: HTMLSelectElement) {
    if (this.updating() !== null || status === ticket.status) return;

    this.updating.set(ticket.id);
    this.ticketError.set('');
    try {
      const updated = await firstValueFrom(this.api.update(ticket.id, status));
      this.tickets.update(tickets => tickets.map(item => item.id === updated.id ? updated : item));
    } catch {
      // Le navigateur a déjà changé la sélection, donc on rétablit le dernier statut serveur.
      select.value = ticket.status;
      this.ticketError.set('Impossible de mettre à jour le statut.');
    } finally {
      this.updating.set(null);
    }
  }

  async askAssistant() {
    if (!this.question.trim() || this.asking()) return;

    this.asking.set(true);
    this.answer.set(null);
    this.assistantError.set('');
    try {
      this.answer.set(await firstValueFrom(this.api.ask(this.question.trim())));
    } catch (error) {
      this.assistantError.set(error instanceof HttpErrorResponse && error.status === 503
        ? 'Assistant indisponible. Vérifie qu’Ollama tourne et que les modèles sont téléchargés.'
        : 'Impossible d’envoyer la question.');
    } finally {
      this.asking.set(false);
    }
  }
}
