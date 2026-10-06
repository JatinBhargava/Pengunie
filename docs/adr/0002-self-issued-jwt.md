# ADR 0002 — Self-issued JWTs, validated as an OAuth2 resource server

**Status:** accepted · 2026-10-02

## Context
We need email/password auth for a solo-built MVP. Options considered: our own JWT auth, Keycloak, or AWS Cognito.

## Decision
The API issues its own **RS256** access tokens (15 min, in the response body, kept in memory by the SPA) and **opaque refresh tokens** (30 days, httpOnly `SameSite=Strict` cookie scoped to `/api/v1/auth`). Refresh tokens are stored hashed and rotated on every use. Presenting an already-rotated token revokes the whole token family, which detects stolen tokens.

Validation goes through `spring-boot-starter-oauth2-resource-server` with the public key and issuer, which is exactly how it would validate Keycloak or Cognito tokens.

## Consequences
- No identity container, realm configuration or redirect flows. This keeps the learning focus on RAG, agents and events.
- **Migration path:** point the resource server at the IdP's `issuer-uri`, delete `AuthController`, `TokenService` and `JwtConfig`, and map the IdP subject to `users.id`. Controllers only see `AccessScope`, so they don't change.
- Our responsibilities: password hashing (BCrypt), login rate limiting (in memory, single instance), and audit of auth events. Reconsider an IdP when we need SSO, social login, MFA or multiple client apps.
