# Pocket Arcade

A phone-first page with 20 small games: 5 in real 3D and 15 in 2D.
Open `games/index.html` on a phone (or any browser) and tap a card to play.

Everything is static HTML, CSS and JavaScript. There is no build step and no
server: the folder works from a local file, from GitHub Pages, or any static host.

## Games

| 3D (Three.js)     | Controls | 2D (canvas / DOM) | Controls   |
|-------------------|----------|-------------------|------------|
| Cube Runner       | swipe    | Flappy Box        | tap        |
| Stack             | tap      | Snake             | swipe      |
| Tunnel Rush       | drag     | 2048              | swipe      |
| Ball Balance      | drag     | Breakout          | drag       |
| ZigZag            | tap      | Tetris            | buttons    |
|                   |          | Memory Match      | tap        |
|                   |          | Tic-Tac-Toe       | tap        |
|                   |          | Minesweeper       | tap / hold |
|                   |          | Whack-a-Mole      | tap        |
|                   |          | Pong              | drag       |
|                   |          | Star Shooter      | drag       |
|                   |          | Sky Jumper        | hold       |
|                   |          | Simon             | tap        |
|                   |          | Fruit Catch       | drag       |
|                   |          | Dino Run          | tap        |

Every game also works with a keyboard (arrows, space) for desktop play.

## Layout

```
games/
  index.html         hub page: cards, 2D/3D filter, personal bests
  shared/arcade.css  shared look for every game page
  shared/arcade.js   shared runtime: top bar, HUD, overlays, touch input,
                     frame loop, best scores (localStorage), sound effects
  2d/*.html          the fifteen 2D games
  3d/*.html          the five 3D games
  lib/three.min.js   Three.js r128 (MIT), vendored so nothing loads from a CDN
```

Best scores are stored per game under the `arcade:best:<id>` key in
`localStorage`, so they stay on the device that played them.
