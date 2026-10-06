# System Design Notes

## Consistent Hashing

Consistent hashing maps both servers and keys onto a ring so that adding or removing a server only moves a small fraction of keys. Virtual nodes give each physical server many positions on the ring, which evens out the load.

## Rate Limiting

The token bucket algorithm refills tokens at a fixed rate and allows bursts up to the bucket capacity. The sliding window log is more precise but stores a timestamp per request, which costs more memory.

## Idempotency

An idempotency key lets a client safely retry a request. The server stores the key with the first response and returns the stored response for any retry with the same key, which prevents duplicate payments or duplicate notifications.

## Outbox Pattern

The transactional outbox writes the business change and the event to publish in the same database transaction. A separate relay later publishes the events, so a message is never lost and never sent for a change that rolled back.
