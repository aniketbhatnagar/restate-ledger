package com.lekha.transfer;

import com.lekha.money.Money;

public sealed interface AccountOperation<
    R extends AccountOperationResult, ReverseR extends AccountOperationResult> {

  String accountId();

  AccountOperation<ReverseR, R> reversed(R result);

  record Debit(String accountId, Money amountToDebit)
      implements AccountOperation<AccountOperationResult.Debit, AccountOperationResult.Credit> {
    @Override
    public Credit reversed(AccountOperationResult.Debit result) {
      return new Credit(accountId, amountToDebit);
    }
  }

  record Credit(String accountId, Money amountToCredit)
      implements AccountOperation<AccountOperationResult.Credit, AccountOperationResult.Debit> {

    @Override
    public Debit reversed(AccountOperationResult.Credit result) {
      return new Debit(accountId, amountToCredit);
    }
  }

  record Hold(String accountId, String holdId, Money amountToHold)
      implements AccountOperation<AccountOperationResult.Hold, AccountOperationResult.ReleaseHold> {
    @Override
    public ReleaseHold reversed(AccountOperationResult.Hold result) {
      return new ReleaseHold(accountId, holdId);
    }
  }

  record ReleaseHold(String accountId, String holdId)
      implements AccountOperation<AccountOperationResult.ReleaseHold, AccountOperationResult.Hold> {
    @Override
    public Hold reversed(AccountOperationResult.ReleaseHold result) {
      return new Hold(accountId, holdId, result.releasedAmount());
    }
  }

  record DebitHold(String accountId, String holdId, Money amountToDebit)
      implements AccountOperation<
          AccountOperationResult.DebitHold, AccountOperationResult.CreditHold> {
    @Override
    public CreditHold reversed(AccountOperationResult.DebitHold result) {
      return new CreditHold(accountId, holdId, amountToDebit);
    }
  }

  record CreditHold(String accountId, String holdId, Money amountToCredit)
      implements AccountOperation<
          AccountOperationResult.CreditHold, AccountOperationResult.DebitHold> {

    @Override
    public DebitHold reversed(AccountOperationResult.CreditHold result) {
      return new DebitHold(accountId, holdId, amountToCredit);
    }
  }

  record TransactionalDebit(String accountId, String transactionId, Money amountToDebit)
      implements AccountOperation<
          AccountOperationResult.TransactionalDebit, AccountOperationResult.TransactionalCredit> {

    @Override
    public TransactionalCredit reversed(AccountOperationResult.TransactionalDebit result) {
      return new TransactionalCredit(accountId, transactionId, amountToDebit);
    }
  }

  record TransactionalCredit(String accountId, String transactionId, Money amountToCredit)
      implements AccountOperation<
          AccountOperationResult.TransactionalCredit, AccountOperationResult.TransactionalDebit> {

    @Override
    public TransactionalDebit reversed(AccountOperationResult.TransactionalCredit result) {
      return new TransactionalDebit(accountId, transactionId, amountToCredit);
    }
  }

  record TransactionalReleaseHold(String accountId, String transactionId)
      implements AccountOperation<
          AccountOperationResult.TransactionalReleaseHold,
          AccountOperationResult.TransactionalHold> {

    @Override
    public TransactionalHold reversed(AccountOperationResult.TransactionalReleaseHold result) {
      return new TransactionalHold(accountId, transactionId, result.releasedAmount());
    }
  }

  record TransactionalHold(String accountId, String transactionId, Money amountToHold)
      implements AccountOperation<
          AccountOperationResult.TransactionalHold,
          AccountOperationResult.TransactionalReleaseHold> {
    @Override
    public TransactionalReleaseHold reversed(AccountOperationResult.TransactionalHold result) {
      return new TransactionalReleaseHold(accountId, transactionId);
    }
  }
}
