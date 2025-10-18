package com.lekha.transfer;

import com.lekha.account.Account;
import com.lekha.account.AccountClient;
import com.lekha.saga.Saga;
import dev.restate.sdk.Context;
import java.util.List;

public record Executor(Context ctx) {

  public void executeOperations(Context ctx, List<AccountOperation<?, ?>> operations) {
    Saga saga = new Saga();
    for (AccountOperation<?, ?> operation : operations) {
      executeOperationWithSaga(ctx, saga, operation);
    }
  }

  @SuppressWarnings("unchecked")
  private <R extends AccountOperationResult, S extends AccountOperationResult>
      void executeOperationWithSaga(Context ctx, Saga saga, AccountOperation<R, S> operation) {
    saga.run(
        () -> executeOperation(ctx, operation),
        result -> executeOperation(ctx, operation.reversed((R) result)));
  }

  private AccountOperationResult executeOperation(
      Context ctx, AccountOperation<?, ?> accountOperation) {
    AccountClient.ContextClient account =
        AccountClient.fromContext(ctx, accountOperation.accountId());
    Account.OperationMetadata metadata = new Account.OperationMetadata();
    return switch (accountOperation) {
      case AccountOperation.DebitOperation operation -> {
        Account.DebitResult debitResult =
            account
                .debit(new Account.DebitInstruction(operation.amountToDebit(), metadata))
                .await();
        yield new AccountOperationResult.DebitResult(debitResult.accountSummary());
      }
      case AccountOperation.CreditOperation operation -> {
        Account.CreditResult creditResult =
            account
                .credit(new Account.CreditInstruction(operation.amountToCredit(), metadata))
                .await();
        yield new AccountOperationResult.CreditResult(creditResult.accountSummary());
      }
      case AccountOperation.HoldOperation operation -> {
        Account.HoldResult holdResult =
            account
                .hold(
                    new Account.HoldInstruction(
                        operation.holdId(), operation.amountToHold(), metadata))
                .await();
        yield new AccountOperationResult.HoldResult(
            holdResult.accountSummary(), holdResult.holdSummary());
      }
      case AccountOperation.ReleaseHoldOperation operation -> {
        Account.ReleaseHoldResult releaseHoldResult =
            account
                .releaseHold(new Account.ReleaseHoldInstruction(operation.holdId(), metadata))
                .await();
        yield new AccountOperationResult.ReleaseHoldHoldResult(
            releaseHoldResult.accountSummary(),
            releaseHoldResult.holdSummary(),
            releaseHoldResult.releasedAmount());
      }
      case AccountOperation.DebitHoldIdOperation operation -> {
        Account.DebitHoldResult debitHoldResult =
            account
                .debitHold(
                    new Account.DebitHoldInstruction(
                        operation.holdId(),
                        new Account.DebitInstruction(operation.amountToDebit(), metadata)))
                .await();
        yield new AccountOperationResult.DebitHoldResult(
            debitHoldResult.accountSummary(), debitHoldResult.holdSummary());
      }
    };
  }
}
