package com.lekha.transfer;

import com.lekha.account.Account;
import com.lekha.money.Money;

public sealed interface AccountOperationResult {

  record Debit(Account.AccountSummary accountSummary) implements AccountOperationResult {}

  record Credit(Account.AccountSummary accountSummary) implements AccountOperationResult {}

  record Hold(Account.AccountSummary accountSummary, Account.HoldSummary holdSummary)
      implements AccountOperationResult {}

  record ReleaseHold(
      Account.AccountSummary accountSummary, Account.HoldSummary holdSummary, Money releasedAmount)
      implements AccountOperationResult {}

  record DebitHold(Account.AccountSummary accountSummary, Account.HoldSummary holdSummary)
      implements AccountOperationResult {}

  record CreditHold(Account.AccountSummary accountSummary, Account.HoldSummary holdSummary)
      implements AccountOperationResult {}

  record TransactionalDebit(
      Account.AccountSummary accountSummary, Account.HoldSummary transactionHoldSummary)
      implements AccountOperationResult {}

  record TransactionalCredit(
      Account.AccountSummary accountSummary, Account.HoldSummary transactionHoldSummary)
      implements AccountOperationResult {}

  record TransactionalReleaseHold(
      Account.AccountSummary accountSummary, Account.HoldSummary holdSummary, Money releasedAmount)
      implements AccountOperationResult {}

  record TransactionalHold(Account.AccountSummary accountSummary, Account.HoldSummary holdSummary)
      implements AccountOperationResult {}
}
