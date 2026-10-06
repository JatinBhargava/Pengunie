# ADR 0004 — Filesystem storage for development, S3 for deployment

**Status:** accepted · 2026-10-02

## Context
The plan assumed MinIO for local S3. The `minio/minio` image is no longer published on Docker Hub, and other S3-compatible images add another container to a solo dev setup.

## Decision
`ObjectStorage` has two implementations, selected by `app.storage.type`:
- `filesystem` (default): files under `app.storage.filesystem.root`. "Presigned" links are HMAC-signed, expiring tokens served by `GET /api/v1/files/{token}`. The token is the credential, as with an S3 presigned URL.
- `s3`: AWS SDK v2 against AWS S3 (default credential chain / IAM role) or any S3-compatible endpoint (`S3_ENDPOINT`, path-style, static keys).

## Consequences
- Local dev and integration tests need only Postgres.
- The S3 implementation has no automated test yet. Add one against an S3-compatible container, or an AWS sandbox bucket, before the week 12 deployment.
- The filesystem backend is single-node only, so it is not for production.
