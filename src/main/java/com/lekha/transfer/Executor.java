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
      case AccountOperation.Debit operation -> {
        Account.DebitResult debitResult =
            account
                .debit(new Account.DebitInstruction(operation.amountToDebit(), metadata))
                .await();
        yield new AccountOperationResult.Debit(debitResult.accountSummary());
      }
      case AccountOperation.Credit operation -> {
        Account.CreditResult creditResult =
            account
                .credit(new Account.CreditInstruction(operation.amountToCredit(), metadata))
                .await();
        yield new AccountOperationResult.Credit(creditResult.accountSummary());
      }
      case AccountOperation.Hold operation -> {
        Account.HoldResult holdResult =
            account
                .hold(
                    new Account.HoldInstruction(
                        operation.holdId(), operation.amountToHold(), metadata))
                .await();
        yield new AccountOperationResult.Hold(
            holdResult.accountSummary(), holdResult.holdSummary());
      }
      case AccountOperation.ReleaseHold operation -> {
        Account.ReleaseHoldResult releaseHoldResult =
            account
                .releaseHold(new Account.ReleaseHoldInstruction(operation.holdId(), metadata))
                .await();
        yield new AccountOperationResult.ReleaseHold(
            releaseHoldResult.accountSummary(),
            releaseHoldResult.holdSummary(),
            releaseHoldResult.releasedAmount());
      }
      case AccountOperation.DebitHold operation -> {
        Account.DebitHoldResult debitHoldResult =
            account
                .debitHold(
                    new Account.DebitHoldInstruction(
                        operation.holdId(),
                        new Account.DebitInstruction(operation.amountToDebit(), metadata)))
                .await();
        yield new AccountOperationResult.DebitHold(
            debitHoldResult.accountSummary(), debitHoldResult.holdSummary());
      }
      case AccountOperation.CreditHold operation -> {
        Account.CreditHoldResult creditHoldResult =
            account
                .creditHold(
                    new Account.CreditHoldInstruction(
                        operation.holdId(),
                        new Account.CreditInstruction(operation.amountToCredit(), metadata)))
                .await();
        yield new AccountOperationResult.CreditHold(
            creditHoldResult.accountSummary(), creditHoldResult.holdSummary());
      }
      case AccountOperation.TransactionalCredit operation -> {
        Account.TransactionalCreditResult creditResult =
            account
                .transactionalCredit(
                    new Account.TransactionalCreditInstruction(
                        operation.transactionId(),
                        new Account.CreditInstruction(operation.amountToCredit(), metadata)))
                .await();
        yield new AccountOperationResult.TransactionalCredit(
            creditResult.accountSummary(), creditResult.transactionHoldSummary());
      }
      case AccountOperation.TransactionalDebit operation -> {
        Account.TransactionalDebitResult debitResult =
            account
                .transactionalDebit(
                    new Account.TransactionalDebitInstruction(
                        operation.transactionId(),
                        new Account.DebitInstruction(operation.amountToDebit(), metadata)))
                .await();
        yield new AccountOperationResult.TransactionalDebit(
            debitResult.accountSummary(), debitResult.transactionHoldSummary());
      }
      case AccountOperation.TransactionalHold operation -> {
        Account.HoldResult holdResult =
            account
                .transactionalHold(
                    new Account.TransactionalHoldInstruction(
                        operation.transactionId(), operation.amountToHold(), metadata))
                .await();
        yield new AccountOperationResult.TransactionalHold(
            holdResult.accountSummary(), holdResult.holdSummary());
      }
      case AccountOperation.TransactionalReleaseHold operation -> {
        Account.TransactionalReleaseHoldResult releaseHoldResult =
            account
                .transactionReleaseHold(
                    new Account.TransactionalReleaseHoldInstruction(
                        operation.transactionId(), metadata))
                .await();
        yield new AccountOperationResult.TransactionalReleaseHold(
            releaseHoldResult.accountSummary(),
            releaseHoldResult.transactionHoldSummary(),
            releaseHoldResult.releasedAmount());
      }
    };
  }
}
