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

The cardmember desk lists seeded cards and transactions and lets a cardmember submit a dispute. The analyst desk lists filed disputes and allows status changes. The JSON API provides the same operations under `/api`.

### Known limits

1. A dispute has only two statuses, `OPEN` and `CLOSED`, and anyone can flip between them in either direction any number of times.
2. Filing checks almost nothing. The disputed amount can be larger than the transaction, the same transaction can be disputed many times, a transaction of any age can be disputed, and the reason is any text.
3. A dispute never changes the money on the card. There is no credit of any kind.
4. Nothing records who changed a dispute or when. Only the current state is stored.
5. The analyst list is one plain list in insertion order, with no filters, no sorting, no due dates and no paging.

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
