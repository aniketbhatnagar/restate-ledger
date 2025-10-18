package com.lekha.transfer;

import com.lekha.money.Money;
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
    Executor executor = new Executor(ctx);
    executor.executeOperations(ctx, operations);
  }

  @Handler
  public void bulkMove(Context ctx, List<MoveMoneyInstruction> instructions) {
    Planner planner = new Planner.NonTransactionalPlanner();
    List<AccountOperation<?, ?>> operations = planner.plan(instructions);
    Executor executor = new Executor(ctx);
    executor.executeOperations(ctx, operations);
  }
}
