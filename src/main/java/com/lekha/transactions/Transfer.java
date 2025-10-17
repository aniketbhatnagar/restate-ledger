package com.lekha.transactions;

import com.lekha.accounts.Account;
import com.lekha.accounts.AccountClient;
import com.lekha.money.Money;
import com.lekha.saga.Saga;
import dev.restate.sdk.Context;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Service;
import java.util.List;
import java.util.Optional;

@Service
public class Transfer {

  public record MoveMoneyInstructionOptions(
      // If the money movement needs to use an existing hold on source account balance.
      Optional<String> sourceAccountHoldId) {}

  public record MoveMoneyInstruction(
      String sourceAccountId,
      String destinationAccountId,
      Money amount,
      MoveMoneyInstructionOptions options) {}

  @Handler
  public void move(Context ctx, MoveMoneyInstruction instruction) {
    Planner planner = new Planner.NonTransactionalPlanner();
    List<AccountOperation<?, ?>> operations = planner.plan(List.of(instruction));
    executeOperations(ctx, operations);
  }

  @Handler
  public void bulkMove(Context ctx, List<MoveMoneyInstruction> instructions) {
    Planner planner = new Planner.NonTransactionalPlanner();
    List<AccountOperation<?, ?>> operations = planner.plan(instructions);
    executeOperations(ctx, operations);
  }

  private void executeOperations(Context ctx, List<AccountOperation<?, ?>> operations) {
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
    Account.OperationMetadata metadata = new Account.OperationMetadata(Optional.empty());
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
      case AccountOperation.DebitFromHoldIdOperation operation -> {
        Account.DebitFromHoldResult debitFromHoldResult =
            account
                .debitFromHold(
                    new Account.DebitFromHoldInstruction(
                        operation.holdId(),
                        new Account.DebitInstruction(operation.amountToDebit(), metadata)))
                .await();
        yield new AccountOperationResult.DebitFromHoldResult(
            debitFromHoldResult.accountSummary(), debitFromHoldResult.holdSummary());
      }
    };
  }
}
