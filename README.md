# Cardbench

Cardbench is a fictional card issuer's disputes and rewards desk demo. It uses made-up cardmembers, cards, merchants, and transactions; it contains no real company names, people, or card numbers. The two services are independent and never call each other.

## Repository layout

- `disputes-service/` — Java 17 and Spring Boot 3.4, Maven, H2 in-memory persistence, server-rendered dispute pages, and a JSON API.
- `rewards-service/` — Kotlin targeting JVM 17 and Spring Boot 3.4, Gradle Kotlin DSL, an in-memory seeded store, server-rendered rewards pages, and a JSON API.

Each service has its own build, tests, embedded fictional data, and standalone run command.

## Build and run

The fetch step for each service downloads its build tool distribution and required dependencies/plugins. Once it succeeds, the listed test and package commands run offline.

### Disputes service

From the repository root:

```sh
cd disputes-service
./mvnw -B package
./mvnw -o test
./mvnw -o package
./mvnw spring-boot:run
```

The first package command is the one-time online fetch and build.

Default port: `8080`. Set `SERVER_PORT` or `server.port` to change it.

### Rewards service

From the repository root:

```sh
cd rewards-service
./gradlew test
./gradlew --offline test
./gradlew --offline bootJar
./gradlew bootRun
```

Default port: `8081`. Set `SERVER_PORT` or `server.port` to change it. The Gradle build targets JVM 17 and does not download or provision a JDK.

### Shared application date

Both services use one injected UTC `Clock` for all date rules. Pin it with `CARDBENCH_TODAY`, for example `CARDBENCH_TODAY=2026-01-15`; if unset, the current UTC date is used.

## Disputes service

The cardmember desk lists seeded cards and transactions and lets a cardmember submit a dispute. Each card links to its account view, and each dispute shows its public timeline. The analyst desk lists filed disputes, allows status changes and case notes, and shows full case history. The JSON API provides the same operations under `/api`, including card accounts at `/api/cards/{cardId}/account`, dispute history at `/api/disputes/{id}/history`, and case notes at `/api/disputes/{id}/notes`.

### Known limits

1. The dispute lifecycle is `OPEN` → `UNDER_REVIEW` → `AWAITING_CARDMEMBER` (then back to `UNDER_REVIEW`) → `RESOLVED_APPROVED` or `RESOLVED_DENIED`; cardmembers may withdraw before a final outcome. Final outcomes cannot be changed, and resolutions require a note.
2. Filing requires a positive amount no greater than the transaction amount, is limited to 120 days after posting, and permits only one active dispute per transaction. Withdrawn or denied disputes may be refiled; approved disputes are permanent. Reasons use a fixed code, with an optional description of at most 500 characters; fraud disputes must cover the full transaction amount.
3. Entering review issues one provisional credit; approval makes it permanent, while denial or withdrawal reverses it. Card accounts show posted transactions, credits in force, the balance owed, and dated dispute adjustments.
4. Case events are append-only and include the application-clock time and supplied actor. Actor names are self-reported, not authenticated; internal notes are hidden from cardmembers.
5. The analyst work queue supports status, reason, cardmember, overdue, and filing-date filters; due-date, filed-date, and amount sorting; and pagination up to 100 cases. Cases are due 30 calendar days after filing, excluding whole calendar days spent awaiting the cardmember. Summary counts cover all cases, independent of the active filters.

## Rewards service

The cardmember home lists each card and its current point balance. Each card page shows its transactions and full points ledger. The JSON API provides member/card lists, card transactions, ledger entries, and balance under `/api`.

### Known limits

1. Every purchase earns a flat 1 point per whole dollar, whatever the merchant category.
2. Refunds are ignored by the ledger. A refunded purchase keeps its points.
3. Points can only be earned. There is no way to spend or redeem them.
4. There is no view by month. Only the full ledger and one running balance exist.
5. There are no promotions or bonus offers of any kind.

## Pull request checks

GitHub Actions builds and tests each service independently on every pull request.
