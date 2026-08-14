import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';

const API = 'http://localhost:8080/api';

export interface Account {
  id: number;
  accountNumber: string;
  balance: number;
}

export interface Transfer {
  id: number;
  fromAccount: string;
  toAccount: string;
  amount: number;
  status: 'PENDING' | 'COMPLETED' | 'FAILED' | 'COMPENSATION_REQUIRED';
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class BankingService {
  constructor(private http: HttpClient) {}

  // The backend scopes accounts to the authenticated user (via the identity
  // headers the gateway derives from the JWT), so no userId is needed here.
  getAccounts() {
    return this.http.get<Account[]>(`${API}/accounts`);
  }

  transfer(fromAccount: string, toAccount: string, amount: number) {
    // Idempotency key generated client-side: retries reuse the same key
    const headers = new HttpHeaders({ 'Idempotency-Key': crypto.randomUUID() });
    return this.http.post<Transfer>(`${API}/transfers`,
      { fromAccount, toAccount, amount }, { headers });
  }

  history(accountNumber: string) {
    return this.http.get<Transfer[]>(`${API}/transfers/history/${accountNumber}`);
  }
}
