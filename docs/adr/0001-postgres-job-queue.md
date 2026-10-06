# ADR 0001 — Postgres job queue before Kafka

**Status:** accepted · 2026-10-02

## Context
Document processing (parse → chunk → embed) and, from week 8, reminders need durable background work with retries. The PRD diagram shows Kafka.

## Decision
Use a `jobs` table in Postgres. Workers claim jobs with one `UPDATE … WHERE id IN (SELECT … FOR UPDATE SKIP LOCKED)` statement and hold a lease (`locked_until`). Jobs are enqueued **in the same transaction** as the business change that needs them, which gives the transactional-outbox guarantee for free. Retries use bounded exponential backoff (5s, 10s, 20s, …, capped at 10 min). `PermanentJobFailure` or exhausted attempts mark the job FAILED and call the handler's `onGiveUp`. The `idempotency_key` column prevents duplicate jobs.

## Consequences
- One less system to run, monitor and pay for. Throughput is far beyond what a personal or family product needs.
- Handlers must be idempotent, because a job can run twice after a lease expires.
- Revisit in week 8 or later if we need fan-out to many consumers, event replay or cross-service streaming. `JobQueue` is the seam for swapping implementations.
