package com.lekha.transfer;

import com.lekha.money.Money;

public sealed interface AccountOperation<
    R extends AccountOperationResult, ReverseR extends AccountOperationResult> {

  String accountId();

  AccountOperation<ReverseR, R> reversed(R result);

  record DebitOperation(String accountId, Money amountToDebit)
      implements AccountOperation<
          AccountOperationResult.DebitResult, AccountOperationResult.CreditResult> {
    @Override
    public CreditOperation reversed(AccountOperationResult.DebitResult result) {
      return new CreditOperation(accountId, amountToDebit);
    }
  }

  record CreditOperation(String accountId, Money amountToCredit)
      implements AccountOperation<
          AccountOperationResult.CreditResult, AccountOperationResult.DebitResult> {

    @Override
    public DebitOperation reversed(AccountOperationResult.CreditResult result) {
      return new DebitOperation(accountId, amountToCredit);
    }
  }

  record HoldOperation(String accountId, String holdId, Money amountToHold)
      implements AccountOperation<
          AccountOperationResult.HoldResult, AccountOperationResult.ReleaseHoldHoldResult> {
    @Override
    public ReleaseHoldOperation reversed(AccountOperationResult.HoldResult result) {
      return new ReleaseHoldOperation(accountId, holdId);
    }
  }

  record ReleaseHoldOperation(String accountId, String holdId)
      implements AccountOperation<
          AccountOperationResult.ReleaseHoldHoldResult, AccountOperationResult.HoldResult> {
    @Override
    public HoldOperation reversed(AccountOperationResult.ReleaseHoldHoldResult result) {
      return new HoldOperation(accountId, holdId, result.releasedAmount());
    }
  }

  record DebitHoldOperation(String accountId, String holdId, Money amountToDebit)
      implements AccountOperation<
          AccountOperationResult.DebitHoldResult, AccountOperationResult.CreditHoldResult> {
    @Override
    public CreditHoldOperation reversed(AccountOperationResult.DebitHoldResult result) {
      return new CreditHoldOperation(accountId, holdId, amountToDebit);
    }
  }

  record CreditHoldOperation(String accountId, String holdId, Money amountToCredit)
      implements AccountOperation<
          AccountOperationResult.CreditHoldResult, AccountOperationResult.DebitHoldResult> {

    @Override
    public DebitHoldOperation reversed(AccountOperationResult.CreditHoldResult result) {
      return new DebitHoldOperation(accountId, holdId, amountToCredit);
    }
  }
}
