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
      Optional<String> sourceAccountHoldId, TransactionMetadata metadata) {}

  public record MoveMoneyInstruction(
      String sourceAccountId,
      String destinationAccountId,
      Money amount,
      MoveMoneyInstructionOptions options) {
    public List<AccountOperation> toAccountOperations() {
      return List.of(
          new AccountOperation(
              sourceAccountId,
              AccountOperation.Type.DEBIT,
              amount,
              options.sourceAccountHoldId(),
              options.metadata()),
          new AccountOperation(
              destinationAccountId,
              AccountOperation.Type.CREDIT,
              amount,
              Optional.empty(),
              options.metadata()));
    }
  }

  public record AccountOperation(
      String accountId,
      Type type,
      Money amount,
      Optional<String> holdId,
      TransactionMetadata metadata) {
    public enum Type {
      DEBIT,
      CREDIT;

      public Type reverse() {
        return switch (this) {
          case DEBIT -> Type.CREDIT;
          case CREDIT -> Type.DEBIT;
        };
      }
    }

    public AccountOperation reversed() {
      return new AccountOperation(accountId, type.reverse(), amount, holdId, metadata);
    }
  }

  @Handler
  public void bulkMove(Context ctx, List<MoveMoneyInstruction> instructions) {
    List<AccountOperation> accountOperations =
        instructions.stream()
            .flatMap(instruction -> instruction.toAccountOperations().stream())
            .toList();
    executeOperations(ctx, accountOperations);
  }

  @Handler
  public void move(Context ctx, MoveMoneyInstruction instruction) {
    executeOperations(ctx, instruction.toAccountOperations());
  }

  private void executeOperations(Context ctx, List<AccountOperation> operations) {
    Saga saga = new Saga();
    for (AccountOperation operation : operations) {
      saga.run(
          () -> executeOperation(ctx, operation),
          () -> executeOperation(ctx, operation.reversed()));
    }
  }

  private Account.AccountSummary executeOperation(Context ctx, AccountOperation operation) {
    AccountClient.ContextClient account = AccountClient.fromContext(ctx, operation.accountId());
    return switch (operation.type()) {
      case DEBIT -> {
        Account.DebitOptions options =
            new Account.DebitOptions(operation.holdId(), operation.metadata());
        yield account.debit(new Account.DebitInstruction(operation.amount(), options)).await();
      }
      case CREDIT -> {
        Account.CreditOptions options =
            new Account.CreditOptions(operation.holdId(), operation.metadata());
        yield account.credit(new Account.CreditInstruction(operation.amount(), options)).await();
      }
    };
  }
}
