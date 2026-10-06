# Figure OS (Netlify)

Browser OS with draggable/resizable windows, customizable desktop (add/remove/move icons), App Store, themes, particles, Library and Game Login.

## Deploy
Set Netlify **base directory** to `figureos` (publish dir `.`, functions `netlify/functions` — already in `netlify.toml`), or drag-drop this folder via `netlify deploy`.

`/api/*` is routed to `netlify/functions/proxy.js`, which forwards to the Figure Cloud API (the API only allows its own origin via CORS, so a direct browser call from Netlify would fail). Override upstream with env var `FIGURE_API`.

Login: `POST /api/login {email,password}`. The session token is read from the upstream `as_user_token` cookie (returned to the browser as `x-fig-token`) and used to build the game `/play?gid=…&token=…` iframe URL.
