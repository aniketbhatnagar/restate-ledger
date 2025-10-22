# Repository Guidelines

## Project overview
The project is a proof of concept implementation of transactional ledger that supports following features:
1. Various account types - ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE. For ASSET & EXPENSE accounts, a debit increases their balance and for all other accounts, a debit decreases their balance.
2. Each account must be initially initialized by calling Account's init API to specify its account type and other options.
3. Funds in an account can be put on hold using Account's hold API. They can be later released using Account's releaseHold API.  
4. Once accounts are initialized, the following is possible using Transfer API:
   1. Transfer.move: Move money between source and destination account. A hold ID can be optionally provided to debit a previously held balance from source account.
   2. Transfer.bulkMove: Move money between a series of source and destination accounts. Each money movement can optionally specify hold ID.
   3. Transfer.transactionalBulkMove: A move like Transfer.bulkMove but meant to be "transactional" so that any credit in the movement is not readily available to spend until all movements are complete. Internally, this is managed by creating a transactional hold to which all credits are made and once all money movement happens, the hold is released.

With this proof of concept implementation, we want to run a load test to understand performance of ledger built using restate. 
Scenarios for load tests:
1. Move between accounts: Initialize an asset and N liability accounts. Each scenario runs moves funds from asset to a randomly picked liability account.
2. Move between accounts with hold: Initialize an asset and N liability accounts. Credit a large amount to each liability account by moving funds from asset to liability account and hold those funds. Each scenario runs moves randomly picked liability account to asset account and passes in the respective hold ID.
3. Transaction move: Initialize an asset and N liability accounts. Credit a large amount to each liability account by moving funds from asset to liability account. Each scenario run 3 liability accounts and does the following transactional bulk move: 
   1. X funds from Liability account1 -> Liability account2. 
   2. X/2 funds from Liability account2 -> Liability account3.
   3. X/2 funds Liability account1 -> asset account.
   4. X/2 funds Liability account3 -> asset account.  

## Project Structure & Module Organization
Restate ledger sample built on the Gradle Java application plugin. Core services live in `src/main/java/com/lekha/...`; `AppMain` wires Restate handlers. Shared configs, JSON schemas, and static assets belong in `src/main/resources`. Integration and unit tests reside in `src/test/java`, mirroring package layout to keep fixtures close to code. Build logic stays in `build.gradle.kts`, while helper scripts live under `gradle/`.

## Build, Test, and Development Commands
- `./gradlew clean build` compiles the Java 21 sources, runs tests, and produces artifacts in `build/`.
- `./gradlew spotlessApply` formats Java sources with Google Java Format and fixes imports.

## Coding Style & Naming Conventions
Spotless enforces Google Java Format with 2-space indentation and import sorting—run it before submitting. Use the `com.lekha.*` package prefix; name classes in PascalCase, enums in singular PascalCase, methods and variables in camelCase, and constants in UPPER_SNAKE. Favor Jackson annotations over manual parsing, and prefer constructor injection for handler dependencies.

## Testing Guidelines
Tests use JUnit Jupiter and AssertJ. Place unit suites in `src/test/java/.../<ClassName>Test.java`, reusing the production package structure. Leverage Restate’s `TestWorkflowEnvironment` utilities for ledger flows and assert ledger balances with AssertJ’s fluent assertions. Run `./gradlew test` locally before pushing; add integration coverage whenever you modify workflow steps or state transitions.

## Commit & Pull Request Guidelines
Follow the existing log style: short, lower-case, action-oriented summaries (e.g., `add balance guard`). Group related changes per commit and avoid mixing formatting with behavior. Pull requests should include the problem statement, high-level behavior changes, linked Restate issues or tasks, and screenshots or sample JSON payloads when APIs change. Request review from a ledger maintainer and ensure the Gradle build and tests pass before merge.
