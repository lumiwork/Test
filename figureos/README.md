# Figure OS (Netlify)

Browser OS with draggable/resizable windows, customizable desktop (add/remove/move icons), App Store, themes, particles, Library and Game Login.

## Deploy
Set Netlify **base directory** to `figureos` (publish dir `.`, functions `netlify/functions` — already in `netlify.toml`), or drag-drop this folder via `netlify deploy`.

`/api/*` is routed to `netlify/functions/proxy.js`, which forwards to the Figure Cloud API (the API only allows its own origin via CORS, so a direct browser call from Netlify would fail). Override upstream with env var `FIGURE_API`.

Login: `POST /api/login {email,password}`. The session token is read from the upstream `as_user_token` cookie (returned to the browser as `x-fig-token`) and used to build the game `/play?gid=…&token=…` iframe URL.

## Layout
- Floating taskbar (bottom): start menu, show desktop, pinned apps, running windows, games-panel toggle, user, clock.
- Right panel: all 225 library games with search and sort (Top / Newest / A-Z); click to play, + to pin to the desktop.
- Errors and API responses are rendered as UI cards/tables, never raw JSON (see the API Inspector app).
- Game launches run a preflight through the proxy (`/api/__play`) so a "Please log in first" JSON reply becomes a sign-in prompt.
