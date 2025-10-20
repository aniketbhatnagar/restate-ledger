package com.lekha.saga;

import dev.restate.sdk.common.TerminalException;
import java.util.ArrayList;
import java.util.List;

public class Saga implements AutoCloseable {

  @FunctionalInterface
  public interface WorkflowRunnable {
    void run() throws TerminalException;
  }

  @FunctionalInterface
  public interface WorkflowSupplier<T> {
    T supply() throws TerminalException;
  }

  @FunctionalInterface
  public interface WorkflowFunction<T> {
    void apply(T input) throws TerminalException;
  }

  private boolean needsCompensation = false;
  private final List<WorkflowRunnable> compensations = new ArrayList<>();

  public <T> T run(WorkflowSupplier<T> task, WorkflowFunction<T> compensation) {
    try {
      T result = task.supply();
      this.compensations.add(() -> compensation.apply(result));
      return result;
    } catch (TerminalException e) {
      needsCompensation = true;
      throw e;
    }
  }

  public void run(WorkflowRunnable task, WorkflowRunnable compensation) {
    try {
      task.run();
      this.compensations.add(compensation);
    } catch (TerminalException e) {
      needsCompensation = true;
      throw e;
    }
  }

  @Override
  public void close() {
    if (needsCompensation) {
      compensate();
    }
  }

  private void compensate() {
    // run compensations in reverse order
    for (int i = compensations.size() - 1; i >= 0; i--) {
      compensations.get(i).run();
    }
  }
}
