# Builder Server Design

> ## ⚠️ TEILWEISE ÜBERHOLT — 2026-09-03
>
> **Das Zugangstor dieses Entwurfs gilt nicht mehr.** Wer hier `builder:members`,
> `/builder add|remove|list` oder „whitelist" liest, liest den Stand bis zum 2026-09-02.
>
> | Damals | Seit 2026-09-03 (Commit `23a0bc7`) |
> |---|---|
> | Redis-Namensliste `builder:members` entscheidet | Berechtigung **`zanoria.builder`** entscheidet |
> | `/builder add\|remove\|list` pflegen die Liste | **weg** — wer freischalten will, vergibt den Rang |
> | kein `permission:`-Feld am Befehl | `permission: zanoria.builder` in `plugin.yml` |
> | — | zusätzlich: Abweisung, wenn der Zielserver **kein FAWE meldet** |
>
> **Warum:** der Schlüssel war der **Spielername**. Eine Namensänderung verschob oder verlor den
> Zugang, lautlos. Und die Liste war ein zweiter Ort neben dem Rangsystem.
>
> ⚠️ **Der Rest dieses Dokuments gilt weiter** — Pelican-Start auf Zuruf, `builder:status`,
> Leerlauf-Abschaltung, der Velocity-Eintrag. Nur das Tor ist ein anderes.
>
> Der aktuelle Stand steht in `2026-09-03-lobby-spielbar-design.md`, Abschnitt 5.


**Date:** 2026-06-05

## Overview

Builders can join a dedicated builder server from the lobby via `/builder`. The server starts on-demand via Pelican and shuts down after 30 minutes of inactivity. A Redis-backed whitelist controls who can join.

---

## Components

### 1. ZanoriaLobby — `/builder` Command

**File:** `BuilderCommand.java`

Sub-commands:
- `/builder` — (no args) join the builder server
- `/builder add <name>` — add player to whitelist (`zanoria.admin`)
- `/builder remove <name>` — remove player from whitelist (`zanoria.admin`)
- `/builder list` — list all whitelisted builders (`zanoria.admin`)

**Join flow:**
1. Check if sender is in Redis Set `builder:members` (by name, case-insensitive)
2. If not → send deny message, abort
3. Check `builder:status` key in Redis:
   - `running` → read `builder:address`, call `player.transfer(host, port)` via Velocity API
   - `starting` → tell player "Server startet gerade, bitte warte..." and schedule a retry check every 2s (max 60s)
   - missing/stopped → call Pelican API to start server, set `builder:status = starting`, tell player "Server wird gestartet...", schedule retry check

**Pelican API call:** Uses `PelicanServerStarter` (new small class in ZanoriaLobby, reads config for panel URL, API key, server UUID).

**Config (`config.yml`):**
```yaml
builder:
  pelican-url: "http://..."
  api-key: "..."
  server-uuid: "..."
  server-address: "ip:port"  # fallback if Redis address missing
```

---

### 2. Builders Plugin — Idle Shutdown + Redis Reporter

**`BuilderRedisReporter`** (registered in `Builders.onEnable`):
- On enable: connects to Redis, writes `builder:status = running` and `builder:address = <ip:port>`
- On disable: deletes `builder:status` and `builder:address`
- Redis config read from `config.yml` (same host/port as lobby)

**`BuilderIdleShutdown`** (BukkitRunnable, 1min interval):
- Tracks time since last player was online
- If `Bukkit.getOnlinePlayers().isEmpty()` for 30 consecutive minutes → calls `Bukkit.shutdown()`
- Resets timer whenever a player joins

**Config (`config.yml` additions):**
```yaml
redis:
  host: "172.18.0.1"
  port: 6379
  password: ""

idle-shutdown-minutes: 30
server-address: "ip:port"
```

---

## Redis Keys

| Key | Type | Value |
|-----|------|-------|
| `builder:status` | String | `starting` / `running` |
| `builder:address` | String | `"172.x.x.x:25570"` |
| `builder:members` | Set | Player names (lowercase) |

---

## Velocity

`velocity.toml` on the Hetzner node gets a static entry:
```toml
builder-1 = "172.21.0.x:25570"
```

The builder server is registered in the Pelican Panel and managed by Wings. Port `25570` reserved for the builder server.

---

## What Is NOT Changed

- Nexus — untouched
- Existing Builders plugin logic (MapManager, activity, WorldEdit etc.) — untouched
- ZanoriaLobby queue NPC system — untouched

---

## Success Criteria

- Builder types `/builder` → transferred to running builder server within ~30s of cold start
- Non-builder types `/builder` → denied message
- Admin `/builder add corwis` → corwis can now join
- Builder server empty for 30min → shuts down cleanly, Redis keys removed
- Next `/builder` after shutdown → server starts again
