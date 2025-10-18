package com.lekha.transfer;

import java.util.*;
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
          new AccountOperation.Credit(instruction.destinationAccountId(), instruction.amount()));
    }
  }

  private static AccountOperation<?, ?> getDebitOperation(
      Transfer.MoveMoneyInstruction instruction) {
    if (containsHold(instruction)) {
      return new AccountOperation.DebitHold(
          instruction.sourceAccountId(),
          instruction.options().sourceAccountHoldId().get(),
          instruction.amount());
    }
    return new AccountOperation.Debit(instruction.sourceAccountId(), instruction.amount());
  }

  private static boolean containsHold(Transfer.MoveMoneyInstruction instruction) {
    return instruction.options().sourceAccountHoldId().isPresent();
  }

  record TransactionalPlanner(String transactionId) implements Planner {
    @Override
    public List<AccountOperation<?, ?>> plan(List<Transfer.MoveMoneyInstruction> instructions) {
      List<AccountOperation<?, ?>> operations = new ArrayList<>(instructions.size() * 2);
      Set<String> transactionalHoldAccountIds = new LinkedHashSet<>();
      for (Transfer.MoveMoneyInstruction instruction : instructions) {
        if (!containsHold(instruction)
            && transactionalHoldAccountIds.contains(instruction.sourceAccountId())) {
          operations.add(
              new AccountOperation.TransactionalDebit(
                  instruction.sourceAccountId(), transactionId, instruction.amount()));
        } else {
          operations.add(getDebitOperation(instruction));
        }
        operations.add(
            new AccountOperation.TransactionalCredit(
                instruction.destinationAccountId(), transactionId, instruction.amount()));
        transactionalHoldAccountIds.add(instruction.destinationAccountId());
      }

      for (String transactionalHoldAccountId : transactionalHoldAccountIds) {
        operations.add(
            new AccountOperation.TransactionalReleaseHold(
                transactionalHoldAccountId, transactionId));
      }

      return operations;
    }
  }
}
