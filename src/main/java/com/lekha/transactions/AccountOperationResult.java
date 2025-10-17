package com.lekha.transactions;

import com.lekha.accounts.Account;
import com.lekha.money.Money;

public sealed interface AccountOperationResult {

  record DebitResult(Account.AccountSummary accountSummary) implements AccountOperationResult {}

  record CreditResult(Account.AccountSummary accountSummary) implements AccountOperationResult {}

  record HoldResult(Account.AccountSummary accountSummary, Account.HoldSummary holdSummary)
      implements AccountOperationResult {}

  record ReleaseHoldHoldResult(
      Account.AccountSummary accountSummary, Account.HoldSummary holdSummary, Money releasedAmount)
      implements AccountOperationResult {}

  record DebitFromHoldResult(Account.AccountSummary accountSummary, Account.HoldSummary holdSummary)
      implements AccountOperationResult {}
}
