import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';

export type Status = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED';

export interface Ticket {
  id: number;
  title: string;
  description: string;
  status: Status;
  createdAt: string;
}

export interface AssistantAnswer {
  answer: string;
  tools: string[];
  sources: {id: string; title: string; file: string; excerpt: string}[];
}

@Injectable({providedIn: 'root'})
export class Api {
  private readonly http = inject(HttpClient);

  list() {
    return this.http.get<Ticket[]>('/api/tickets');
  }

  create(title: string, description: string) {
    return this.http.post<Ticket>('/api/tickets', {title, description});
  }

  update(id: number, status: Status) {
    return this.http.patch<Ticket>('/api/tickets/' + id + '/status', {status});
  }

  ask(question: string) {
    return this.http.post<AssistantAnswer>('/api/assistant', {question});
  }
}
