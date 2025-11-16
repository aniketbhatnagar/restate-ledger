package com.lekha.transfer;

import com.lekha.account.Account;
import com.lekha.account.AccountClient;
import com.lekha.utils.Saga;
import dev.restate.sdk.Awakeable;
import dev.restate.sdk.Context;
import dev.restate.sdk.DurableFuture;
import java.util.ArrayList;
import java.util.List;

public record Executor(Context ctx) {

  public void executeOperations(Context ctx, Planner.Plan plan) {
    try (Saga saga = new Saga()) {
      for (AccountOperation<?, ?> operation : plan.serialOperations()) {
        executeOperationWithSaga(ctx, saga, operation);
      }
    }

    List<AccountOperation<?, ?>> cleanupOperations = plan.parallelCleanupOperations();
    if (!cleanupOperations.isEmpty()) {
      List<DurableFuture<?>> cleanupResults = new ArrayList<>(cleanupOperations.size());
      for (AccountOperation<?, ?> operation : cleanupOperations) {
        cleanupResults.add(this.executeOperationAsync(ctx, operation));
      }
      DurableFuture.all(cleanupResults).await();
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
    return executeOperationAsync(ctx, accountOperation).await();
  }

  private DurableFuture<AccountOperationResult> executeOperationAsync(
      Context ctx, AccountOperation<?, ?> accountOperation) {
    AccountClient.ContextClient account =
        AccountClient.fromContext(ctx, accountOperation.accountId());
    Account.OperationMetadata metadata = new Account.OperationMetadata();
    return switch (accountOperation) {
      case AccountOperation.Debit operation -> {
        DurableFuture<Account.DebitResult> debitResultFuture =
            account.debit(new Account.DebitInstruction(operation.amountToDebit(), metadata));
        yield debitResultFuture.map(
            debitResult -> new AccountOperationResult.Debit(debitResult.accountSummary()));
      }
      case AccountOperation.AsyncDebit operation -> {
        Awakeable<Account.AsyncDebitResult> debitResultAwakeable =
            ctx.awakeable(Account.AsyncDebitResult.class);
        Account.DebitInstruction debitInstruction =
            new Account.DebitInstruction(operation.amountToDebit(), metadata);
        Account.SignalInstruction signalInstruction =
            new Account.SignalInstruction(debitResultAwakeable.id());
        account.asyncDebit(new Account.AsyncDebitInstruction(debitInstruction, signalInstruction));
        yield debitResultAwakeable.map(
            asyncDebitResult ->
                new AccountOperationResult.Debit(asyncDebitResult.debitResult().accountSummary()));
      }
      case AccountOperation.Credit operation -> {
        DurableFuture<Account.CreditResult> creditResultFuture =
            account.credit(new Account.CreditInstruction(operation.amountToCredit(), metadata));
        yield creditResultFuture.map(
            creditResult -> new AccountOperationResult.Credit(creditResult.accountSummary()));
      }
      case AccountOperation.Hold operation -> {
        DurableFuture<Account.HoldResult> holdResultFuture =
            account.hold(
                new Account.HoldInstruction(
                    operation.holdId(), operation.amountToHold(), metadata));
        yield holdResultFuture.map(
            holdResult ->
                new AccountOperationResult.Hold(
                    holdResult.accountSummary(), holdResult.holdSummary()));
      }
      case AccountOperation.ReleaseHold operation -> {
        DurableFuture<Account.ReleaseHoldResult> releaseHoldResultFuture =
            account.releaseHold(new Account.ReleaseHoldInstruction(operation.holdId(), metadata));
        yield releaseHoldResultFuture.map(
            releaseHoldResult ->
                new AccountOperationResult.ReleaseHold(
                    releaseHoldResult.accountSummary(),
                    releaseHoldResult.holdSummary(),
                    releaseHoldResult.releasedAmount()));
      }
      case AccountOperation.DebitHold operation -> {
        DurableFuture<Account.DebitHoldResult> debitHoldResultFuture =
            account.debitHold(
                new Account.DebitHoldInstruction(
                    operation.holdId(),
                    new Account.DebitInstruction(operation.amountToDebit(), metadata)));
        yield debitHoldResultFuture.map(
            debitHoldResult ->
                new AccountOperationResult.DebitHold(
                    debitHoldResult.accountSummary(), debitHoldResult.holdSummary()));
      }
      case AccountOperation.CreditHold operation -> {
        DurableFuture<Account.CreditHoldResult> creditHoldResultFuture =
            account.creditHold(
                new Account.CreditHoldInstruction(
                    operation.holdId(),
                    new Account.CreditInstruction(operation.amountToCredit(), metadata)));
        yield creditHoldResultFuture.map(
            creditHoldResult ->
                new AccountOperationResult.CreditHold(
                    creditHoldResult.accountSummary(), creditHoldResult.holdSummary()));
      }
      case AccountOperation.TransactionalCredit operation -> {
        DurableFuture<Account.TransactionalCreditResult> creditResultFuture =
            account.transactionalCredit(
                new Account.TransactionalCreditInstruction(
                    operation.transactionId(),
                    new Account.CreditInstruction(operation.amountToCredit(), metadata)));
        yield creditResultFuture.map(
            creditResult ->
                new AccountOperationResult.TransactionalCredit(
                    creditResult.accountSummary(), creditResult.transactionHoldSummary()));
      }
      case AccountOperation.TransactionalDebit operation -> {
        DurableFuture<Account.TransactionalDebitResult> debitResultFuture =
            account.transactionalDebit(
                new Account.TransactionalDebitInstruction(
                    operation.transactionId(),
                    new Account.DebitInstruction(operation.amountToDebit(), metadata)));
        yield debitResultFuture.map(
            debitResult ->
                new AccountOperationResult.TransactionalDebit(
                    debitResult.accountSummary(), debitResult.transactionHoldSummary()));
      }
      case AccountOperation.TransactionalHold operation -> {
        DurableFuture<Account.HoldResult> holdResultFuture =
            account.transactionalHold(
                new Account.TransactionalHoldInstruction(
                    operation.transactionId(), operation.amountToHold(), metadata));
        yield holdResultFuture.map(
            holdResult ->
                new AccountOperationResult.TransactionalHold(
                    holdResult.accountSummary(), holdResult.holdSummary()));
      }
      case AccountOperation.TransactionalReleaseHold operation -> {
        DurableFuture<Account.TransactionalReleaseHoldResult> releaseHoldResultFuture =
            account.transactionReleaseHold(
                new Account.TransactionalReleaseHoldInstruction(
                    operation.transactionId(), metadata));
        yield releaseHoldResultFuture.map(
            releaseHoldResult ->
                new AccountOperationResult.TransactionalReleaseHold(
                    releaseHoldResult.accountSummary(),
                    releaseHoldResult.transactionHoldSummary(),
                    releaseHoldResult.releasedAmount()));
      }
    };
  }
}
