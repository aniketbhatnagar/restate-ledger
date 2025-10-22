package com.lekha.transfer;

import com.lekha.money.Money;
import dev.restate.sdk.Context;
import dev.restate.sdk.annotation.Handler;
import dev.restate.sdk.annotation.Service;
import dev.restate.serde.TypeRef;
import dev.restate.serde.TypeTag;
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
    bulkMove(ctx, List.of(instruction));
  }

  @Handler
  public void bulkMove(Context ctx, List<MoveMoneyInstruction> instructions) {
    Planner.Plan plan =
        ctx.run(
            "plan",
            TypeTag.of(new TypeRef<>() {}),
            () -> {
              Planner planner = new Planner.NonTransactionalPlanner();
              return planner.plan(instructions);
            });
    Executor executor = new Executor(ctx);
    executor.executeOperations(ctx, plan);
  }

  @Handler
  public void transactionalBulkMove(Context ctx, List<MoveMoneyInstruction> instructions) {
    Planner.Plan plan =
        ctx.run(
            "plan",
            TypeTag.of(new TypeRef<>() {}),
            () -> {
              String transactionId = ctx.request().invocationId().toString();
              Planner planner = new Planner.TransactionalPlanner(transactionId);
              return planner.plan(instructions);
            });
    Executor executor = new Executor(ctx);
    executor.executeOperations(ctx, plan);
  }
}
