package com.lekha.transfer;

import dev.restate.sdk.Context;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public interface Planner {

  List<AccountOperation<?, ?>> plan(List<Transfer.MoveMoneyInstruction> instructions);

  record NonTransactionalPlanner() implements Planner {

    @Override
    public List<AccountOperation<?, ?>> plan(List<Transfer.MoveMoneyInstruction> instructions) {
      return instructions.stream().flatMap(this::toAccountOperations).toList();
    }

    private Stream<AccountOperation<?, ?>> toAccountOperations(
        Transfer.MoveMoneyInstruction instruction) {
      return Stream.of(
          getDebitOperation(instruction),
          new AccountOperation.CreditOperation(
              instruction.destinationAccountId(), instruction.amount()));
    }

    private static AccountOperation<?, ?> getDebitOperation(
        Transfer.MoveMoneyInstruction instruction) {
      if (instruction.options().sourceAccountHoldId().isPresent()) {
        return new AccountOperation.DebitHoldOperation(
            instruction.sourceAccountId(),
            instruction.options().sourceAccountHoldId().get(),
            instruction.amount());
      }
      return new AccountOperation.DebitOperation(
          instruction.sourceAccountId(), instruction.amount());
    }
  }

  record TransactionalPlanner(Context ctx) implements Planner {
    @Override
    public List<AccountOperation<?, ?>> plan(List<Transfer.MoveMoneyInstruction> instructions) {
      String transactionId = ctx.request().invocationId().toString();
      List<AccountOperation<?, ?>> operations = new ArrayList<>(instructions.size() * 2);
      for (Transfer.MoveMoneyInstruction instruction : instructions) {}

      return operations;
    }
  }
}
