# Sports Session Platform Frontend

React, TypeScript, and Vite foundation for the Host Live Session UI.

## Commands

- `npm run dev` starts the Vite development server.
- `npm run lint` checks the source with Oxlint.
- `npm run test -- --run` runs the frontend tests once.
- `npm run build` type-checks and creates the production build.

During local development, Vite proxies `/api` requests to
`http://localhost:8080` without rewriting the request path.

The read-only Host Live Session Control Room is available at
`/sessions/:sessionId`. It composes the existing backend read APIs and refreshes
only when the Host selects Refresh; automatic polling is not enabled.
