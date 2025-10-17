package com.lekha.accounts;

import java.util.Optional;
import java.util.UUID;

public class OperationMetadataFactory {

  public static Account.OperationMetadata createOperationMetadata() {
    return new Account.OperationMetadata(Optional.of(UUID.randomUUID().toString()));
  }
}
