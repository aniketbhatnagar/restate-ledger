package com.lekha.transfer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.lekha.money.Money;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = AccountOperation.Debit.class, name = AccountOperation.Debit.TYPE_NAME),
  @JsonSubTypes.Type(
      value = AccountOperation.Credit.class,
      name = AccountOperation.Credit.TYPE_NAME),
  @JsonSubTypes.Type(value = AccountOperation.Hold.class, name = AccountOperation.Hold.TYPE_NAME),
  @JsonSubTypes.Type(
      value = AccountOperation.ReleaseHold.class,
      name = AccountOperation.ReleaseHold.TYPE_NAME),
  @JsonSubTypes.Type(
      value = AccountOperation.DebitHold.class,
      name = AccountOperation.DebitHold.TYPE_NAME),
  @JsonSubTypes.Type(
      value = AccountOperation.CreditHold.class,
      name = AccountOperation.CreditHold.TYPE_NAME),
  @JsonSubTypes.Type(
      value = AccountOperation.TransactionalDebit.class,
      name = AccountOperation.TransactionalDebit.TYPE_NAME),
  @JsonSubTypes.Type(
      value = AccountOperation.TransactionalCredit.class,
      name = AccountOperation.TransactionalCredit.TYPE_NAME),
  @JsonSubTypes.Type(
      value = AccountOperation.TransactionalHold.class,
      name = AccountOperation.TransactionalHold.TYPE_NAME),
  @JsonSubTypes.Type(
      value = AccountOperation.TransactionalReleaseHold.class,
      name = AccountOperation.TransactionalReleaseHold.TYPE_NAME)
})
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface AccountOperation<
    R extends AccountOperationResult, ReverseR extends AccountOperationResult> {

  String accountId();

  AccountOperation<ReverseR, R> reversed(R result);

  String getType();

  @JsonTypeName(Debit.TYPE_NAME)
  record Debit(String accountId, Money amountToDebit)
      implements AccountOperation<AccountOperationResult.Debit, AccountOperationResult.Credit> {
    public static final String TYPE_NAME = "debit";

    @Override
    public Credit reversed(AccountOperationResult.Debit result) {
      return new Credit(accountId, amountToDebit);
    }

    @Override
    public String getType() {
      return TYPE_NAME;
    }
  }

  @JsonTypeName(Credit.TYPE_NAME)
  record Credit(String accountId, Money amountToCredit)
      implements AccountOperation<AccountOperationResult.Credit, AccountOperationResult.Debit> {

    public static final String TYPE_NAME = "credit";

    @Override
    public Debit reversed(AccountOperationResult.Credit result) {
      return new Debit(accountId, amountToCredit);
    }

    @Override
    public String getType() {
      return TYPE_NAME;
    }
  }

  @JsonTypeName(Hold.TYPE_NAME)
  record Hold(String accountId, String holdId, Money amountToHold)
      implements AccountOperation<AccountOperationResult.Hold, AccountOperationResult.ReleaseHold> {

    public static final String TYPE_NAME = "hold";

    @Override
    public ReleaseHold reversed(AccountOperationResult.Hold result) {
      return new ReleaseHold(accountId, holdId);
    }

    @Override
    public String getType() {
      return TYPE_NAME;
    }
  }

  @JsonTypeName(ReleaseHold.TYPE_NAME)
  record ReleaseHold(String accountId, String holdId)
      implements AccountOperation<AccountOperationResult.ReleaseHold, AccountOperationResult.Hold> {

    public static final String TYPE_NAME = "releaseHold";

    @Override
    public Hold reversed(AccountOperationResult.ReleaseHold result) {
      return new Hold(accountId, holdId, result.releasedAmount());
    }

    @Override
    public String getType() {
      return TYPE_NAME;
    }
  }

  @JsonTypeName(DebitHold.TYPE_NAME)
  record DebitHold(String accountId, String holdId, Money amountToDebit)
      implements AccountOperation<
          AccountOperationResult.DebitHold, AccountOperationResult.CreditHold> {

    public static final String TYPE_NAME = "debitHold";

    @Override
    public CreditHold reversed(AccountOperationResult.DebitHold result) {
      return new CreditHold(accountId, holdId, amountToDebit);
    }

    @Override
    public String getType() {
      return TYPE_NAME;
    }
  }

  @JsonTypeName(CreditHold.TYPE_NAME)
  record CreditHold(String accountId, String holdId, Money amountToCredit)
      implements AccountOperation<
          AccountOperationResult.CreditHold, AccountOperationResult.DebitHold> {

    public static final String TYPE_NAME = "creditHold";

    @Override
    public DebitHold reversed(AccountOperationResult.CreditHold result) {
      return new DebitHold(accountId, holdId, amountToCredit);
    }

    @Override
    public String getType() {
      return TYPE_NAME;
    }
  }

  @JsonTypeName(TransactionalDebit.TYPE_NAME)
  record TransactionalDebit(String accountId, String transactionId, Money amountToDebit)
      implements AccountOperation<
          AccountOperationResult.TransactionalDebit, AccountOperationResult.TransactionalCredit> {

    public static final String TYPE_NAME = "transactionalDebit";

    @Override
    public TransactionalCredit reversed(AccountOperationResult.TransactionalDebit result) {
      return new TransactionalCredit(accountId, transactionId, amountToDebit);
    }

    @Override
    public String getType() {
      return TYPE_NAME;
    }
  }

  @JsonTypeName(TransactionalCredit.TYPE_NAME)
  record TransactionalCredit(String accountId, String transactionId, Money amountToCredit)
      implements AccountOperation<
          AccountOperationResult.TransactionalCredit, AccountOperationResult.TransactionalDebit> {

    public static final String TYPE_NAME = "transactionalCredit";

    @Override
    public TransactionalDebit reversed(AccountOperationResult.TransactionalCredit result) {
      return new TransactionalDebit(accountId, transactionId, amountToCredit);
    }

    @Override
    public String getType() {
      return TYPE_NAME;
    }
  }

  @JsonTypeName(TransactionalReleaseHold.TYPE_NAME)
  record TransactionalReleaseHold(String accountId, String transactionId)
      implements AccountOperation<
          AccountOperationResult.TransactionalReleaseHold,
          AccountOperationResult.TransactionalHold> {

    public static final String TYPE_NAME = "transactionalReleaseHold";

    @Override
    public TransactionalHold reversed(AccountOperationResult.TransactionalReleaseHold result) {
      return new TransactionalHold(accountId, transactionId, result.releasedAmount());
    }

    @Override
    public String getType() {
      return TYPE_NAME;
    }
  }

  @JsonTypeName(TransactionalHold.TYPE_NAME)
  record TransactionalHold(String accountId, String transactionId, Money amountToHold)
      implements AccountOperation<
          AccountOperationResult.TransactionalHold,
          AccountOperationResult.TransactionalReleaseHold> {

    public static final String TYPE_NAME = "transactionalHold";

    @Override
    public TransactionalReleaseHold reversed(AccountOperationResult.TransactionalHold result) {
      return new TransactionalReleaseHold(accountId, transactionId);
    }

    @Override
    public String getType() {
      return TYPE_NAME;
    }
  }
}
