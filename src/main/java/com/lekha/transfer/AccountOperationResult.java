package com.lekha.transfer;

import com.lekha.account.Account;
import com.lekha.money.Money;

public sealed interface AccountOperationResult {

  record DebitResult(Account.AccountSummary accountSummary) implements AccountOperationResult {}

  record CreditResult(Account.AccountSummary accountSummary) implements AccountOperationResult {}

  record HoldResult(Account.AccountSummary accountSummary, Account.HoldSummary holdSummary)
      implements AccountOperationResult {}

  record ReleaseHoldHoldResult(
      Account.AccountSummary accountSummary, Account.HoldSummary holdSummary, Money releasedAmount)
      implements AccountOperationResult {}

  record DebitHoldResult(Account.AccountSummary accountSummary, Account.HoldSummary holdSummary)
      implements AccountOperationResult {}

  record CreditHoldResult(Account.AccountSummary accountSummary, Account.HoldSummary holdSummary)
      implements AccountOperationResult {}
}
