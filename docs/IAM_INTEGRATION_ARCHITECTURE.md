# EOP IAM Integration Architecture

## 1. Context

The Sports Session Platform currently has no authentication or authorization.
All Host screens and APIs operate over one shared data space. Enterprise
Operations Platform (EOP) is the central IAM bounded context and already owns
users, credentials, login, JWT issuance, authentication sessions, refresh-token
rotation, logout, authorization versions, roles, permissions, organization
scopes, and permission overrides.

This document records the audited integration design. It does not mark Sports
authentication as implemented and does not create an EOP roadmap phase.

Audited repository state:

- EOP branch `feature/be-nghia`, HEAD
  `78d22e654153ec715bb271350551ad20245f8796`, Flyway head V018.
- Sports branch `feature/host-live-session-ui-v1`, HEAD
  `ee8e692d447befbc94395d360d1b4a6357409372`, Flyway head V9.

## 2. System Boundaries

EOP owns:

- user identity and credentials;
- login, refresh-token rotation, logout, and authentication sessions;
- JWT signing keys and JWKS publication;
- Auth Clients;
- permission definitions, roles, assignments, organization scopes, hierarchy,
  and ALLOW/DENY overrides;
- authoritative runtime authentication and authorization state.

Sports owns:

- Sports organizations' local configuration and external IAM scope references;
- Venue, Court, Player, Session, SessionParticipant, Buddy, Match, MatchPlan,
  Matchmaking, and Rating business rules;
- mapping Sports endpoints to permission codes;
- resource ownership, tenant-aware queries, and Host operational constraints.

Sports must never read the EOP PostgreSQL database or Redis. It stores IAM UUIDs
as external references without cross-database foreign keys. IAM references are
accepted only from an authenticated, authoritative EOP contract and are checked
again when an ownership/assignment is created or changed.

The Player personal-link route remains a separate, opaque-token, read-only
capability in V1. It does not create an IAM identity or grant Host permissions.

## 3. Authentication Ownership

The audited EOP HTTP contract is:

| Concern | Current implementation | Source | Suitable for Sports? |
| --- | --- | --- | --- |
| Registration | `POST /api/v1/auth/register`; email, username, password | `RegistrationController`, `RegisterRequest` | Technically reusable, but not an Owner-scoped Host invitation workflow |
| Login | `POST /api/v1/auth/login`; `usernameOrEmail`, `password`, `clientCode` | `AuthController`, `LoginRequest` | Yes after provisioning `SPORTS_WEB` |
| Login result | Access token, `Bearer`, seconds-to-expiry, rotating opaque refresh token | `LoginResponse` | Yes; browser storage needs an explicit security decision |
| Refresh | `POST /api/v1/tokens/refresh`; raw opaque refresh token in JSON | `TokenController`, `TokenLifecycleServiceImpl` | Yes; rotation/replay semantics must be preserved |
| Logout | `POST /api/v1/auth/logout`; authenticated Bearer token | `AuthController`, `LogoutServiceImpl` | Yes |
| Current actor endpoint | No general `/me` or authentication-context response | Controller audit | No; required for external resource-server integration |
| Introspection | No token introspection/runtime-validation endpoint | Controller audit | No |
| Service-to-service authorization | No supported external resource-server contract | Controller/security audit | No |

EOP remains the only component that handles passwords and refresh-token family
state. Sports must not create a second user/password/role system.

## 4. Authorization Ownership

EOP owns permission codes and the facts that grant or deny them. Sports owns the
meaning of those codes at each endpoint and the mapping from a Sports resource
to an IAM authorization scope.

The authorization formula is:

```text
AUTHORIZED =
  authenticated and runtime-valid IAM actor
  AND required Sports permission is effective
  AND resource belongs to an IAM scope covered by the actor's effective grants
  AND any Sports-specific assignment rule is satisfied
```

An organization ID, Venue ID, or scope ID supplied by the browser is never
authorization evidence. Sports resolves the requested resource, derives its
scope from persisted ownership, and applies the rule server-side.

## 5. JWT Contract

EOP issues RS256 access tokens with JOSE `typ=at+jwt` and a configured `kid`.
The public key is exposed at `GET /.well-known/jwks.json` and the route is public
in every profile.

| Claim | Meaning | Produced/required by EOP | Sports usage |
| --- | --- | --- | --- |
| `iss` | Configured EOP issuer | Produced; decoder validates | Require exact configured issuer |
| `aud` | One globally configured EOP audience | Produced; decoder validates | Require exact audience; currently not client-specific |
| `sub` | IAM User UUID | Produced; converter requires UUID | `iamUserId` |
| `sid` | Persisted Auth Session UUID | Produced; converter requires UUID | `authSessionId` and runtime validation |
| `client_id` | Auth Client UUID, not client code | Produced; converter requires UUID | Bind token to expected `SPORTS_WEB` client/context |
| `jti` | Unique access-token ID | Produced; converter requires nonblank | Revocation/audit/cache identity |
| `token_type` | Literal `access` | Produced; decoder and converter validate | Reject non-access tokens |
| `authz_version` | User authorization version at issuance/refresh | Produced; converter requires positive number | Runtime staleness and cache key |
| `policy_version` | Global policy version at issuance/refresh | Produced; converter requires positive number | Runtime staleness and cache key |
| `amr` | Authentication methods; currently includes `PASSWORD` | Produced; converter requires non-empty supported array | Authentication context/audit |
| `aal` | Assurance level; password login currently `1` | Produced; converter requires 1..3 | Future step-up policy; retain now |
| `iat` | Issued-at instant | Produced | Audit and token age |
| `nbf` | Not-before instant | Produced; timestamp validation applies | Standard validation |
| `exp` | Access-token expiry | Produced; decoder/converter require valid expiry | Hard upper cache/token lifetime |

Roles, permission codes, scoped grants, organization scope IDs, and overrides
are intentionally not embedded in the JWT. EOP loads global authorities from
cache/database after runtime validation. Scoped authorization is evaluated from
database-backed assignments, hierarchy, and overrides.

The signing configuration loads one RSA private/public key pair at startup and
publishes one public JWK. `kid` supports key identification, but there is no
multi-key rotation window or automatic key rotation. A future key rotation must
publish old and new verification keys during overlap before switching signing.

Sports can use Spring Security `oauth2ResourceServer().jwt(...)` with:

- issuer: the exact EOP `EOP_JWT_ISSUER` value;
- JWK set URI: `<EOP_BASE_URL>/.well-known/jwks.json`;
- RS256 only;
- exact audience validation;
- JOSE `typ=at+jwt` validation;
- `token_type=access` validation;
- required UUID/version/method/assurance claims.

Local signature verification is necessary but not sufficient.

```text
Can Sports verify EOP access-token signatures locally using JWKS? YES
```

## 6. Runtime Validation Contract

For each EOP API request, `IamJwtAuthenticationConverter` currently checks:

- the User exists and remains `ACTIVE`;
- token `authz_version` equals the current User version;
- Auth Session exists and belongs to the token User and Auth Client;
- Session is not revoked and is within idle and absolute expiry;
- Session authorization and policy versions equal the token versions;
- password credential is not expired when password expiry is enabled;
- global policy version equals the token version;
- token is an access token.

`JwtBlacklistFilter` additionally checks Redis `jti` blacklist state and falls
back to authoritative PostgreSQL Auth Session state if Redis is unavailable.
Logout revokes the Session and refresh tokens, and blacklists the current JTI.
Refresh-token replay revokes the whole Session/token family.

JWKS-only Sports validation cannot immediately detect logout, administrative
session revocation, user lock/disable/delete, authorization-version changes,
policy-version changes, or refresh-token replay revocation. It detects them
only after access-token expiry. JWKS-only therefore weakens EOP's current
security semantics.

Current conclusion:

```text
EXTERNAL RESOURCE SERVER RUNTIME VALIDATION CONTRACT MISSING
```

Architecture comparison:

| Option | Strength | Cost/risk | Decision |
| --- | --- | --- | --- |
| A — JWKS only | Lowest latency; Sports can continue during an EOP outage while cached keys remain valid | Logout, replay revocation, User/session disable, and version changes remain accepted until `exp` | Reject because it weakens current IAM semantics |
| B — JWT + IAM runtime introspection/context | Preserves authoritative User, Session, Client, version, policy, and scoped authorization checks | One EOP dependency per uncached request; needs strict timeout/failure policy | Recommended correctness baseline |
| C — JWT + versioned/cached context | Reduces read latency and IAM load | Bounded stale window; cache key/invalidation errors can extend revoked access | Allow only as a measured optimization for reads; not mutation authority |
| D — direct EOP database/Redis or copied IAM tables | None that justifies boundary violation | Tight coupling, credential exposure, split authority | Prohibited |

Recommended contract: EOP exposes a supported authenticated token-context
endpoint for external resource servers. Over TLS, Sports presents the end-user
Bearer token. EOP applies the same decoder, blacklist/session/user/version
checks as its own requests and returns a minimal context containing:

- User, Session, Auth Client, JTI, expiry, authz version, and policy version;
- client code or an authoritative `SPORTS_WEB` binding;
- effective global permissions;
- effective organization-scoped grants and DENY/ALLOW override results needed
  for tenant filtering and endpoint decisions.

The exact URL and response DTO are intentionally deferred to the first
implementation slice. The existing `/api/v1/authorization/inspect` endpoint is
not this contract: it requires global `iam.authorization.inspect`, can inspect
an arbitrary user/scope, and is an administrative explanation API.

Recommended external resource-server pattern:

1. Sports validates signature, issuer, audience, time, JOSE type, token type,
   and required claim shapes locally with cached JWKS.
2. Sports obtains authoritative runtime token and scoped authorization context
   from EOP.
3. Sports maps that context to `CurrentSportsActor`.
4. Sports resolves the target resource and enforces permission, IAM scope, and
   any local assignment.

Security-sensitive mutations require fresh EOP validation and fail closed.
Read-only requests may later use a bounded context cache, never beyond token
expiry and with a target TTL of 30 seconds or less. Cache keys must include at
least User ID, Session ID, Client ID, JTI, authorization version, and policy
version. A cache is an explicit revocation-delay tradeoff, not an authority of
its own.

## 7. Auth Client Model

The only provisioned first-party client is currently:

| Code | Type | Status | Access TTL | Refresh idle | Refresh absolute | Refresh allowed |
| --- | --- | --- | ---: | ---: | ---: | --- |
| `EOP_WEB` | `WEB` | `ACTIVE` | 600 seconds | 7 days | 30 days | Yes |

A separate `SPORTS_WEB` public client is appropriate because EOP sessions are
bound to an Auth Client UUID and TTL/disable policy is client-specific.

Recommended provisioning mirrors the established web policy:

| Field | Value |
| --- | --- |
| code | `SPORTS_WEB` |
| type | `WEB` |
| status | `ACTIVE` |
| access TTL | 600 seconds |
| refresh idle TTL | 604800 seconds (7 days) |
| refresh absolute TTL | 2592000 seconds (30 days) |
| allow refresh token | `true` |

Provisioning requires a new EOP migration but no Auth Client schema change.
The current JWT audience is globally configured and does not derive from the
Auth Client. Therefore audience alone cannot distinguish `SPORTS_WEB` from
`EOP_WEB`. Until EOP gains per-client audiences, Sports must require the
authoritative `SPORTS_WEB` client binding from the external token-context
contract (or a securely configured expected Auth Client UUID).

## 8. Sports Roles and Permissions

EOP permission codes require exactly `module.resource.action`, so Sports codes
fit naturally. Permissions should be system-provisioned, `ORG_SCOPED`, and
active. Read actions are `NORMAL`; state-changing management actions are
`SENSITIVE`; Host delegation is `CRITICAL`.

```text
Can Sports permissions live naturally in EOP IAM? YES
```

Proposed minimum catalog:

- `sports.venue.read`, `sports.venue.manage`
- `sports.court.read`, `sports.court.manage`
- `sports.player.read`, `sports.player.manage`
- `sports.session.read`, `sports.session.create`, `sports.session.manage`
- `sports.participant.manage`
- `sports.match.read`, `sports.match.manage`
- `sports.match_plan.manage`
- `sports.matchmaking.manage`
- `sports.host.assign`

| Permission | SPORTS_OWNER | SPORTS_HOST | Scope |
| --- | --- | --- | --- |
| `sports.venue.read` | Allow | Allow | COMPANY/BRANCH |
| `sports.venue.manage` | Allow | Deny | COMPANY/BRANCH |
| `sports.court.read` | Allow | Allow | COMPANY/BRANCH |
| `sports.court.manage` | Allow | Deny | COMPANY/BRANCH |
| `sports.player.read` | Allow | Allow | COMPANY/BRANCH through membership |
| `sports.player.manage` | Allow | Deny | COMPANY |
| `sports.session.read` | Allow | Allow | COMPANY/BRANCH |
| `sports.session.create` | Allow | Allow | COMPANY/BRANCH |
| `sports.session.manage` | Allow | Allow | COMPANY/BRANCH |
| `sports.participant.manage` | Allow | Allow | COMPANY/BRANCH |
| `sports.match.read` | Allow | Allow | COMPANY/BRANCH |
| `sports.match.manage` | Allow | Allow | COMPANY/BRANCH |
| `sports.match_plan.manage` | Allow | Allow | COMPANY/BRANCH |
| `sports.matchmaking.manage` | Allow | Allow | COMPANY/BRANCH |
| `sports.host.assign` | Allow | Deny | COMPANY/BRANCH |

`SPORTS_OWNER` and `SPORTS_HOST` should be assignable system roles supporting
COMPANY and BRANCH scopes. They must not receive `iam.rbac.manage`,
`iam.user.update`, or other global IAM-administration permissions.

EOP already supports role assignment revocation, active/expired assignment
filtering, hierarchy coverage, and DENY > ALLOW > role grant > default-deny.
Although the schema supports assignment expiry, the current assignment request
does not expose `expiresAt`; that is not required for the first Sports slice.

## 9. Organization Mapping

Recommended mapping is:

```text
Sports Club / Business -> EOP COMPANY
Sports Venue           -> EOP BRANCH
Sports Court           -> Sports-only child of Venue
```

This reuses EOP's COMPANY-to-BRANCH hierarchy and closure-based ancestor
coverage without introducing a Sports-specific IAM scope type. A COMPANY Owner
can cover all child Venue/BRANCH scopes, while a Host can be limited to one or
more BRANCH scopes.

Sports should keep its own organization/venue business records and persist the
corresponding EOP authorization-scope UUIDs as external references. The EOP
branch is authorization identity; the Sports Venue remains the scheduling
aggregate. No database foreign key crosses bounded contexts.

## 10. Sports Resource Ownership

| Resource | Scope/ownership strategy |
| --- | --- |
| Sports Organization | References one EOP COMPANY authorization scope |
| Venue | Belongs to Sports Organization and references one EOP BRANCH scope |
| Court | Derives organization/BRANCH through Venue |
| Session | Derives scope through its persisted Venue; never trusts request scope |
| SessionCourt | Derives through Session and physical Court; both must belong to the same tenant |
| SessionParticipant | Derives through Session; Player membership is checked when added |
| Buddy Pair | Derives through both SessionParticipants and Session |
| Match | Derives through Session |
| MatchPlan | Derives through Session |
| Matchmaking recommendation | Ephemeral; authorized through Session/Court scope |
| Player | Global identity plus organization membership, described below |
| Rating | Global per Player/Sport/MatchFormat; access is mediated by Player membership and tenant-aware history presentation |

Current tenancy hotspots:

| Current Sports operation | Current behavior | Required future scoping |
| --- | --- | --- |
| `GET /api/venues` | Returns every Venue | Filter by visible COMPANY/BRANCH scopes |
| Venue/Court create/get/list | Loads by raw ID or lists all children | Require permission and derive/verify Venue scope; hide foreign-tenant IDs |
| `GET /api/sessions` | `findAllForDiscovery()` returns every Session | Query only Sessions under visible Venue scopes |
| Session get/lifecycle | Loads Session by ID only | Resolve Session plus Venue scope and Host grant before use |
| Participants/Buddy/personal-access administration | Checks Session relationships, not actor scope | Require Session scope; personal-access issuance is Host-only |
| Public `/api/player-session-access/{token}` | Opaque token resolves read-only Player view | Keep public, minimize returned data, rate-limit, retain unguessable token |
| Player list/search/get/history | Global search/read | Restrict to organization memberships; avoid cross-tenant history leakage |
| Player create/update | Global mutation | Owner-scoped membership rules and explicit global-identity policy |
| Match list/create/start/complete/cancel | Session or Match ID only | Derive Session scope before read/mutation |
| MatchPlan list/update/move/reorder/cancel/start | Session or plan ID only | Derive Session scope and return 404 across tenants |
| Matchmaking generate/accept/queue | Session/Court IDs only | Require matchmaking/manage permission in derived Session/Venue scope |
| Rating reconciliation | Internal scheduler, no actor | Remains system-internal; tenant-aware reads are enforced at API boundary |

Adding authentication without changing these repository/service entry points
would still leak or mutate cross-organization data.

## 11. Host Assignment

V1 recommendation is H2, Venue-level Host membership, implemented through an
EOP `SPORTS_HOST` role assignment at the Venue's BRANCH scope. Sports should
not duplicate the same assignment in a `venue_host_assignments` table unless a
future Sports-only assignment attribute is proven necessary.

A Host can operate Sessions under assigned Venue scopes. COMPANY-scoped Owners
cover all Venue branches beneath the Company. H1 (`sessions.host_user_id`) is
too restrictive for shift/backup Hosts. H3 can later add an optional primary
Host for accountability or scheduling, but it is not needed for authorization
V1.

Current EOP administration is not safe for Owner self-service delegation:

- role assignment/revocation requires global `iam.rbac.manage`;
- user listing requires `iam.user.read` and is not scope-filtered;
- public registration is not an invitation or scoped account-provisioning API;
- no API constrains an administrator to roles/scopes below their own grant.

Therefore a delegated Host administration contract is missing. It must enforce
that an Owner can invite/find a user, assign only `SPORTS_HOST`, target only
covered COMPANY/BRANCH scopes, revoke only covered Sports assignments, and list
only Hosts in scope. Granting global IAM administration is prohibited.

```text
Can SPORTS_OWNER safely administer Hosts using existing IAM APIs? NO
```

## 12. Player Ownership Decision

Options considered:

- P1, organization-owned Player: simplest isolation but duplicates one human
  across clubs and fragments the existing global Player code/rating identity.
- P2, global Player plus organization membership: preserves global identity
  while allowing strict tenant visibility and membership lifecycle.
- P3, globally shared Player directory: simplest reuse but leaks identity and
  activity between unrelated organizations.

V1 recommendation is P2:

```text
players (global identity, global playerCode)
organization_players (organization, player, membership status/metadata)
```

Organizations may search/read only their members plus an explicit, privacy-safe
join/invite flow. `playerCode` must not become an unrestricted global directory
lookup. Rating can remain global because the existing model is keyed to Player,
Sport, and MatchFormat, but detailed Rating history must not reveal another
organization's Session/Match data. Cross-organization profile linking,
consent, duplicate resolution, and history redaction require product decisions
before migration design.

## 13. CurrentSportsActor Model

Conceptual immutable request actor:

```text
CurrentSportsActor
- iamUserId
- authSessionId
- authClientId
- tokenId
- tokenExpiresAt
- authorizationVersion
- policyVersion
- authenticationMethods
- assuranceLevel
- globalPermissions
- scopedPermissionGrants
- scopedPermissionDenies
- coveredOrganizationScopeIds
```

Sources:

- JWT: User, Session, Client, JTI, expiry, versions, AMR, and AAL.
- EOP runtime context: current validity, authoritative Client binding,
  permissions, scoped grants/denies, and organization coverage.
- Sports database: Organization/Venue external scope mapping, Player
  membership, resource parentage, and future Sports-only assignment metadata.

## 14. Request Authorization Flow

Example: check in a SessionParticipant.

1. Require a Bearer access token.
2. Validate EOP JWT signature and fixed claims locally.
3. Obtain fresh EOP runtime context for this mutation.
4. Require `sports.participant.manage` (or the finalized equivalent).
5. Load SessionParticipant by participant ID and Session ID.
6. Load the Session and its persisted Venue ownership mapping.
7. Derive the EOP BRANCH scope; never accept scope from the request.
8. Confirm the effective grant/override covers that BRANCH.
9. Apply any future Sports-local assignment constraint.
10. Execute the existing lifecycle command.

For an inaccessible cross-tenant object, return 404 to avoid confirming its
existence. For collection endpoints, return only visible rows.

HTTP policy:

- 401: missing, malformed, expired, wrong issuer/audience/type, or
  authoritatively stale/revoked token.
- 403: runtime-valid actor can see the tenant/resource but lacks the required
  permission or assignment.
- 404: resource does not exist or is outside the actor's visible scope.
- 503: EOP runtime validation is unavailable and no permitted fresh context is
  usable; dependency outage is not falsely reported as invalid credentials.

## 15. Deployment Topology

```text
Browser
  -> Vercel Sports SPA
  -> EOP login/refresh/logout
  -> Render Sports API with Bearer access token
       -> EOP JWKS + runtime token-context API
       -> Supabase Sports PostgreSQL

EOP service
  -> EOP PostgreSQL and Redis (never accessed by Sports)
```

Conceptual future environment variables:

Sports frontend (public):

- `VITE_API_BASE_URL`
- `VITE_EOP_BASE_URL`
- `VITE_EOP_CLIENT_CODE=SPORTS_WEB`

Sports backend:

- `EOP_ISSUER`
- `EOP_JWK_SET_URI`
- `EOP_EXPECTED_AUDIENCE`
- `EOP_RUNTIME_CONTEXT_URI`
- expected `SPORTS_WEB` binding/configuration if not returned by context
- bounded timeout/cache settings

EOP:

- exact allowed Sports Vercel origin if the SPA calls EOP directly;
- any future resource-server contract authentication/configuration.

EOP currently enables Spring Security CORS but defines no audited application
`CorsConfigurationSource`; direct Vercel-to-EOP browser calls therefore require
an explicit, exact-origin CORS configuration or an approved same-origin/BFF
topology.

## 16. Failure Policy

| Failure | Required policy |
| --- | --- |
| JWKS temporarily unreachable | Use already cached, unexpired verification keys; unknown `kid`/cold cache fails closed with 503 |
| Runtime-context endpoint unavailable | Mutations fail closed with 503; reads may use only an explicitly fresh bounded cache entry |
| IAM database unavailable | EOP cannot establish runtime truth; Sports fails closed/503 |
| IAM Redis unavailable | EOP's existing PostgreSQL Auth Session fallback remains authoritative; Sports consumes the EOP result |
| Sports has cached context | Never use after token expiry/cache TTL; never blind fail-open; mutation path requires fresh validation |

Calling EOP on every Sports request adds network/database latency and makes EOP
a synchronous availability dependency. Start with correctness: fresh validation
for mutations and a small read cache only after measurements. Use short connect
and response timeouts, circuit-break dependency failures as 503, and observe
latency/error rates. Do not cache by User ID alone.

## 17. Security Rules

- EOP is the sole credential, session, token, role, permission, and IAM scope
  authority.
- Sports never reads EOP database/Redis and never stores passwords or raw
  refresh-token families in its domain database.
- Never use JWKS-only validation as proof of current session/user/policy state.
- Require exact issuer, audience, algorithm, JOSE type, token type, and
  `SPORTS_WEB` client binding.
- Never accept tenant/scope claims from request parameters as authority.
- Never create cross-database foreign keys.
- Never grant `iam.rbac.manage` globally to a Sports Owner.
- Apply EOP DENY overrides before ALLOW/role grants.
- Mutations fail closed when current authorization cannot be established.
- Cross-tenant IDs return 404; collection queries are tenant-scoped at the
  repository boundary, not filtered only in the UI.
- Keep the Player personal-token flow read-only, opaque, rate-limited, and
  isolated from Host APIs.
- Frontend hides controls for usability only; backend authorization is final.

Frontend token storage recommendation for the current JSON-token EOP contract:

- keep the access token in memory;
- for an MVP direct-SPA flow, keep the rotating refresh token in
  `sessionStorage`, never `localStorage`, acknowledging that XSS can still read
  it and page/tab lifecycle affects login persistence;
- apply a strict CSP and clear all token state on logout/refresh replay;
- prefer a later same-origin BFF/HttpOnly Secure cookie design before broader
  production exposure, but do not add a second password/role system in Sports.

## 18. API Contract Gaps

Already available in EOP:

- registration, login, refresh rotation/replay detection, and logout;
- RS256 access tokens and public JWKS;
- runtime User/Session/Client/version/policy validation inside EOP;
- global and scoped RBAC with hierarchy and ALLOW/DENY overrides;
- user/session/RBAC administration for global IAM administrators.

Reusable with no change:

- login/refresh/logout payloads and token semantics;
- JWKS signature verification;
- User UUID, Session UUID, Client UUID, JTI, version claims;
- COMPANY/BRANCH hierarchy and scoped authorization engine.

Requires provisioning only:

- `SPORTS_WEB` Auth Client;
- Sports permission catalog;
- `SPORTS_OWNER` and `SPORTS_HOST` roles and mappings.

Requires a new EOP public/service contract:

- external resource-server runtime token/context validation;
- authoritative `SPORTS_WEB` client-code binding or per-client audience policy;
- scoped grants/denies/covered scopes suitable for Sports list filtering;
- delegated Owner Host invitation/list/assign/revoke constrained by scope;
- exact-origin CORS if the SPA calls EOP directly.

Requires Sports-side implementation:

- Spring Security resource-server foundation and `CurrentSportsActor`;
- public-route policy for health and Player personal access;
- organization/Venue external IAM scope mapping;
- Player organization membership;
- tenant-aware repositories/services and endpoint permissions;
- Host/Owner frontend authentication and role-aware navigation.

Requires product decisions:

- Player membership invitation/linking/privacy and Rating-history exposure;
- direct SPA token storage versus BFF hardening;
- whether EOP adopts per-client audiences or Sports validates Client UUID/context;
- whether Owner can create IAM users or only invite existing/self-registered
  users;
- whether Session gains an optional primary Host after Venue-scoped V1.

## 19. Migration Plan

Current heads:

- EOP: V018 (`V018__create_password_recovery_request_queue.sql`).
- Sports: V9 (`V9__add_player_code.sql`).

Likely future EOP migration work:

- provision `SPORTS_WEB`;
- provision Sports system permissions, roles, supported scope types, and role
  permission mappings;
- schema only if the chosen external contract/client-auth model proves it is
  required.

Likely future Sports migration work:

- Sports Organization with external EOP COMPANY scope UUID;
- Venue organization ownership and external EOP BRANCH scope UUID;
- `organization_players` membership and constraints;
- indexes supporting organization-scoped Venue, Session, and Player queries;
- optional local audit/assignment fields only when required by an approved
  slice.

At implementation time use the next available migration version after
rechecking each repository. With the audited heads those would currently be
EOP V019 and Sports V10, but neither file is created by this design task.

## 20. Implementation Slices

### Slice 1 — External IAM Resource Server Contract

- Repo: EOP
- Goal: expose runtime-valid token context with client binding and scoped
  authorization data while preserving current validation semantics.
- Migration: no, unless service authentication design requires persistence.
- API: yes, new supported external context contract.
- Acceptance: logout, replay, user disable, authz/policy changes, Session expiry,
  scoped DENY, and Redis-to-PostgreSQL fallback are covered.
- Risk: contract leakage, latency, and accidentally weakening runtime checks.

### Slice 2 — SPORTS_WEB and Sports Authorization Catalog

- Repo: EOP
- Goal: provision Auth Client, permissions, roles, scope types, and mappings.
- Migration: yes, next verified EOP version.
- API: no behavior change expected.
- Acceptance: deterministic provisioning; `SPORTS_WEB` issues 10-minute access
  tokens; roles grant only Sports permissions at COMPANY/BRANCH scopes.
- Risk: wrong scope/risk classification or client-binding ambiguity.

### Slice 3 — Sports Authentication Foundation

- Repo: Sports backend
- Goal: local JWT validation, EOP runtime context adapter,
  `CurrentSportsActor`, public route allow-list, consistent 401/503 handling.
- Migration: no.
- API: Host APIs become authenticated; health and personal-token read remain
  explicitly public.
- Acceptance: wrong issuer/audience/type/client and revoked/stale contexts fail;
  no business authorization is inferred from browser input.
- Risk: regressions to public Player personal access or outage semantics.

### Slice 4 — Organization/Venue Ownership

- Repo: Sports backend
- Goal: add Sports Organization external COMPANY scope and Venue external BRANCH
  scope; scope Venue/Court reads and writes.
- Migration: yes, next verified Sports version.
- API: organization-aware creation/read contracts.
- Acceptance: Owner sees own hierarchy; foreign-tenant IDs return 404; no
  cross-database foreign keys.
- Risk: backfilling existing shared Venue data.

### Slice 5 — Player Organization Membership

- Repo: Sports backend
- Goal: implement P2 membership and tenant-safe Player search/read/write/history.
- Migration: yes.
- API: membership/invitation or linking contract required.
- Acceptance: global UUID/playerCode retained; no unrestricted cross-tenant
  directory or Match-history leakage.
- Risk: identity linking, privacy, duplicate resolution, and Rating visibility.

### Slice 6 — Session-Derived Tenancy and Host Enforcement

- Repo: Sports backend
- Goal: scope Session, Participant, Buddy, Court allocation, Match, MatchPlan,
  Matchmaking, and Rating-history operations through persisted Venue scope.
- Migration: normally no beyond Slice 4; optional audit fields may require one.
- API: authorization behavior/404 semantics change, domain contracts remain.
- Acceptance: every listed tenancy hotspot has positive and cross-tenant tests;
  Venue-scoped Host can operate only covered Sessions.
- Risk: one unscoped ID lookup can become a tenant escape.

### Slice 7 — Delegated Host Administration

- Repo: EOP, then Sports frontend
- Goal: Owner safely lists/invites and assigns/revokes `SPORTS_HOST` only in
  covered COMPANY/BRANCH scopes.
- Migration: possibly no; recheck after contract design.
- API: yes, constrained delegated-administration endpoints.
- Acceptance: Owner cannot assign IAM/admin roles, escape owned hierarchy, or
  enumerate unrelated users; authz versions increment on change.
- Risk: privilege escalation; requires adversarial integration tests.

### Slice 8 — Frontend Authentication and Role-Aware UX

- Repo: Sports frontend, plus deployment configuration
- Goal: login/refresh/logout, Bearer attachment, 401 recovery, route guards, and
  Owner/Host navigation using the approved token-storage topology.
- Migration: no.
- API: consumes EOP and authenticated Sports APIs.
- Acceptance: `SPORTS_WEB` login, refresh rotation, logout, expired/revoked
  handling, and direct-route reload work on Vercel; QR Player flow still works.
- Risk: browser token exposure, concurrent refresh races, and CORS.

## 21. Deferred Decisions

- Per-client JWT audience versus authoritative Client UUID/context binding.
- Exact external runtime-context URL, DTO, caller authentication, cache TTL,
  and rate limits.
- Direct SPA/sessionStorage MVP versus same-origin BFF/HttpOnly-cookie hardening.
- Owner invitation versus existing-user assignment workflow.
- Player linking/consent and cross-organization Rating-history redaction.
- Optional Session primary Host and operational audit attribution.
- Multi-key JWKS rotation overlap.
- Any IAM scope type beyond existing COMPANY/BRANCH; `VENUE` is not proposed.
- Player account authentication; the V1 QR read-only flow remains accountless.
