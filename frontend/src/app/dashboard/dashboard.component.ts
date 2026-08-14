import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { BankingService, Account, Transfer } from '../core/banking.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <h1>My Accounts</h1>
    <div class="accounts">
      @for (acc of accounts; track acc.id) {
        <div class="account-card" (click)="loadHistory(acc.accountNumber)">
          <span class="number">{{ acc.accountNumber }}</span>
          <span class="balance">{{ acc.balance | currency }}</span>
        </div>
      }
    </div>

    <h2>Recent Transactions</h2>
    <table>
      <tr><th>From</th><th>To</th><th>Amount</th><th>Status</th></tr>
      @for (tx of transfers; track tx.id) {
        <tr [class]="tx.status.toLowerCase()">
          <td>{{ tx.fromAccount }}</td>
          <td>{{ tx.toAccount }}</td>
          <td>{{ tx.amount | currency }}</td>
          <td>{{ tx.status }}</td>
        </tr>
      }
    </table>
  `,
})
export class DashboardComponent implements OnInit {
  accounts: Account[] = [];
  transfers: Transfer[] = [];

  constructor(private banking: BankingService) {}

  ngOnInit() {
    this.banking.getAccounts().subscribe(a => {
      this.accounts = a;
      if (a.length) this.loadHistory(a[0].accountNumber);
    });
  }

  loadHistory(accountNumber: string) {
    this.banking.history(accountNumber).subscribe(t => (this.transfers = t));
  }
}
