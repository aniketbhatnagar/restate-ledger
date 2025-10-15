package com.lekha.testsetup;

import com.lekha.transactions.TransactionMetadata;
import java.util.UUID;

public class TransactionMetadataFactory {
  public static TransactionMetadata transactionMetadata() {
    return new TransactionMetadata(UUID.randomUUID().toString());
  }
}
