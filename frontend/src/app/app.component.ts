import { Component } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink],
  template: `
    <header>
      <span class="brand">🏦 MicroBank</span>
      @if (auth.isLoggedIn()) {
        <nav>
          <a routerLink="/dashboard">Dashboard</a>
          <a routerLink="/transfer">Transfer</a>
          <button class="link" (click)="logout()">Log out</button>
        </nav>
      }
    </header>
    <main>
      <router-outlet />
    </main>
  `,
})
export class AppComponent {
  constructor(public auth: AuthService, private router: Router) {}

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
