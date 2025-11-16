package com.lekha.utils;

import dev.restate.sdk.InvocationHandle;
import dev.restate.sdk.ObjectContext;
import dev.restate.sdk.common.StateKey;
import dev.restate.sdk.common.TerminalException;
import dev.restate.serde.TypeRef;
import dev.restate.serde.TypeTag;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class Batcher<T> {

  public record State<T>(List<T> items, String expireInvocationId) {}

  private final ObjectContext ctx;
  private final String batcherName;
  private final TypeRef<State<T>> stateTypeRef;

  public Batcher(ObjectContext ctx, String batcherName, TypeRef<State<T>> stateTypeRef) {
    this.ctx = ctx;
    this.batcherName = batcherName;
    this.stateTypeRef = stateTypeRef;
  }

  @FunctionalInterface
  public interface BatchExecutionScheduler {
    InvocationHandle<?> scheduleBatchExecution(
        ObjectContext ctx, String batchName, Optional<Duration> delay);
  }

  public Appender appender(
      int maxBatchSize,
      Duration maxBatchWaitDuration,
      BatchExecutionScheduler batchExecutionScheduler) {
    return new Appender(maxBatchSize, maxBatchWaitDuration, batchExecutionScheduler);
  }

  public Executor executor() {
    return new Executor();
  }

  public class Appender {
    private final int maxBatchSize;
    private final BatchExecutionScheduler batchExecutionScheduler;
    private final State<T> batcherState;

    private Appender(
        int maxBatchSize,
        Duration maxBatchWaitDuration,
        BatchExecutionScheduler batchExecutionScheduler) {
      this.maxBatchSize = maxBatchSize;
      this.batchExecutionScheduler = batchExecutionScheduler;
      Optional<State<T>> existingState = ctx.get(batcherStateKey());
      if (existingState.isPresent()) {
        this.batcherState = existingState.get();
      } else {
        InvocationHandle<?> invocationHandle =
            this.batchExecutionScheduler.scheduleBatchExecution(
                ctx, batcherName, Optional.of(maxBatchWaitDuration));
        this.batcherState = new State<>(new LinkedList<>(), invocationHandle.invocationId());
      }
    }

    public void addToBatch(T value) {
      this.batcherState.items.add(value);
      ctx.set(batcherStateKey(), batcherState);
      if (this.batcherState.items.size() >= maxBatchSize) {
        InvocationHandle<?> invocationHandle =
            this.batchExecutionScheduler.scheduleBatchExecution(ctx, batcherName, Optional.empty());
        invocationHandle.attach().await();
      }
    }
  }

  public class Executor {
    public void executeBatch(Consumer<List<T>> consumer) {
      State<T> existingState =
          ctx.get(batcherStateKey())
              .orElseThrow(
                  () ->
                      new TerminalException(
                          String.format("State of batcher %s not found", batcherName)));
      List<T> items = existingState.items;
      consumer.accept(items);
      ctx.clear(batcherStateKey());
    }
  }

  private StateKey<State<T>> batcherStateKey() {
    return StateKey.of("batcher_" + batcherName, TypeTag.of(stateTypeRef));
  }
}
