import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BankingService } from '../core/banking.service';

@Component({
  selector: 'app-transfer',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="transfer-card">
      <h2>Send money</h2>
      <input [(ngModel)]="from" placeholder="From account" />
      <input [(ngModel)]="to" placeholder="To account" />
      <input [(ngModel)]="amount" type="number" placeholder="Amount" />
      <button (click)="send()" [disabled]="sending">
        {{ sending ? 'Sending…' : 'Send' }}
      </button>
      @if (message) { <p>{{ message }}</p> }
    </div>
  `,
})
export class TransferComponent {
  from = ''; to = ''; amount = 0;
  sending = false; message = '';

  constructor(private banking: BankingService) {}

  send() {
    this.sending = true;
    this.banking.transfer(this.from, this.to, this.amount).subscribe({
      next: (tx) => { this.message = `Transfer ${tx.status}`; this.sending = false; },
      error: (e) => { this.message = e.error?.message ?? 'Transfer failed'; this.sending = false; },
    });
  }
}
