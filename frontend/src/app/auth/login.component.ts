import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="login-card">
      <h2>Sign in</h2>
      <input [(ngModel)]="email" placeholder="Email" type="email" />
      <input [(ngModel)]="password" placeholder="Password (min 8 chars)" type="password" />
      <button (click)="submit()">Sign in</button>
      <button class="secondary" (click)="registerAndLogin()">Create account</button>
      @if (error) { <p class="error">{{ error }}</p> }
    </div>
  `,
  styles: [`.secondary { margin-left: 0.5rem; background: #486581; }`],
})
export class LoginComponent {
  email = '';
  password = '';
  error = '';

  constructor(private auth: AuthService, private router: Router) {}

  submit() {
    this.auth.login(this.email, this.password).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: (e) => (this.error = e.status === 423
        ? 'Account locked after too many attempts.'
        : 'Invalid email or password.'),
    });
  }

  registerAndLogin() {
    this.auth.register(this.email, this.password).subscribe({
      next: () => this.submit(),
      error: (e) => (this.error = e.status === 409
        ? 'Email already registered — try signing in.'
        : e.error?.message ?? 'Registration failed (password must be 8+ chars).'),
    });
  }
}
