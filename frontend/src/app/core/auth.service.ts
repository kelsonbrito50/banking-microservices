import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';

const API = 'http://localhost:8080/api';

@Injectable({ providedIn: 'root' })
export class AuthService {
  token = signal<string | null>(sessionStorage.getItem('token'));

  constructor(private http: HttpClient) {}

  login(email: string, password: string) {
    return this.http.post<{ accessToken: string; role: string }>(
      `${API}/auth/login`, { email, password }
    ).pipe(tap(res => {
      sessionStorage.setItem('token', res.accessToken);
      this.token.set(res.accessToken);
    }));
  }

  register(email: string, password: string) {
    return this.http.post(`${API}/auth/register`, { email, password });
  }

  logout() {
    sessionStorage.removeItem('token');
    this.token.set(null);
  }

  isLoggedIn(): boolean {
    return !!this.token();
  }
}
