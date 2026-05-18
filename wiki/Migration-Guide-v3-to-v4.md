# Migration Guide: v3 → v4

> Step-by-step instructions for upgrading from VelocityNavigator v3 to v4.

---

## Before You Begin

⚠️ **Backup your config!**

```bash
cp plugins/velocitynavigator/navigator.toml plugins/velocitynavigator/navigator.toml.v3-backup
```

Also back up your entire `plugins/velocitynavigator/` directory if you want to be safe.

---

## Step 1: Replace the JAR

1. Download `VelocityNavigator-4.0.0.jar` from [Releases](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/releases)
2. Remove the old JAR from `plugins/`
3. Place the new JAR in `plugins/`

```
plugins/
├── VelocityNavigator-4.0.0.jar   ← new
└── velocitynavigator/
    ├── navigator.toml              ← will be auto-migrated
    └── ...
```

---

## Step 2: Start the Proxy

Start (or restart) your Velocity proxy. VelocityNavigator v4 will:

1. Detect the v3 config format
2. Automatically migrate it to v4 format
3. Create a backup at `navigator.toml.bak`
4. Log any migration warnings to the console

You should see something like:

```
[VelocityNavigator] Config migrated from v3 to v4 format. Backup saved as navigator.toml.bak
```

---

## Step 3: Verify the Migration

### Compare the backup with the new config

```bash
diff plugins/velocitynavigator/navigator.toml.bak plugins/velocitynavigator/navigator.toml
```

### Check for logged warnings

Review the proxy console for any warnings like:

```
[VelocityNavigator] WARNING: Config field 'update_checker.enabled' is no longer supported in v4. The update checker now always runs on startup.
```

These warnings indicate fields that were removed or renamed. See the [Config Changes Table](#config-changes-table) below.

### Verify routing works

1. Run `/vn status` to confirm the plugin loaded correctly
2. Type `/lobby` in-game to test routing
3. Run `/vn debug player <name>` to verify routing decisions

---

## Step 4: Update Scripts and Automation

If you have any scripts or external tools that depend on v3 config fields, update them:

- Scripts reading `update_checker.enabled` → removed (checker always runs on startup)
- Scripts reading `update_checker.notifyConsole` → removed (always logs to console)
- Scripts reading `update_checker.startupDelaySeconds` → removed (fixed at 5 seconds)
- Scripts checking `velocitynavigator.bypasscooldown` permission → update to `velocitynavigator.bypass.cooldown` (legacy name still works)

---

## Config Changes Table

### Removed Fields

| Field | Was (v3) | Notes |
|-------|----------|-------|
| `update_checker.enabled` | `true`/`false` | Checker now always runs once on startup. Use `notifyOnStartup` to suppress notification. |
| `update_checker.notifyConsole` | `true`/`false` | Always logs to console now. |
| `update_checker.startupDelaySeconds` | `5` | Fixed at 5 seconds. |

### Added Fields

| Field | Default | Description |
|-------|---------|-------------|
| `routing.max_retries` | `2` | Connection retry attempts on failure. |
| `circuit_breaker.enabled` | `true` | Enable/disable circuit breaker. |
| `circuit_breaker.failure_threshold` | `3` | Failures before circuit opens. |
| `circuit_breaker.cooldown_seconds` | `30` | Seconds before HALF_OPEN transition. |
| `circuit_breaker.half_open_max_tests` | `1` | Test requests in HALF_OPEN state. |
| `degradation.enabled` | `true` | Enable graceful degradation. |
| `degradation.mode` | `"random"` | Degradation selection mode. |
| `messages.retrying` | `"<yellow>Connection failed, retrying... (<attempt>/<max>)</yellow>"` | Retry notification message. |
| `notify_on_startup` | `true` | Show update notification on proxy start. |
| `notify_admins_on_join` | `true` | Notify admins with `velocitynavigator.admin` permission about available updates when they join. |
| Contextual group `mode` | (inherits global) | Per-group selection mode override. |
| Contextual `fallback_chain` | `{}` | Fallback group ordering. |

### Changed Fields

| Field | v3 | v4 | Migration |
|-------|----|----|-----------|
| `routing.default_lobbies` | `["lobby-1", "lobby-2"]` | Same format + inline tables | Backward compatible — plain strings still work |
| `routing.selection_mode` | 3 modes | 7 modes | Old modes still work; new: `power_of_two`, `weighted_round_robin`, `least_connections`, `consistent_hash` |
| `routing.contextual.groups` | `Map<String, List<LobbyEntry>>` | `Map<String, GroupConfig>` | Auto-migrated; GroupConfig adds optional `mode` field |

---

## Permission Node Change

| v3 | v4 | Notes |
|----|----|-------|
| `velocitynavigator.bypasscooldown` | `velocitynavigator.bypass.cooldown` | **Both still work.** The old name is checked as a fallback. Update your permission plugins when convenient. |

---

## New Commands

| Command | Description |
|---------|-------------|
| `/vn drain <server>` | Mark a server as drained (no new players routed) |
| `/vn undrain <server>` | Remove the drain flag |
| `/vn drain status` | List all drained servers |
| `/vn updatecheck` | Manually check for updates |

---

## New API Methods

If you use the Developer API, note these new methods on `NavigatorAPI`:

```java
// Routing distribution over last 60 seconds
Map<String, Long> distribution = api.getRoutingDistribution();

// Health check latency per server (ms)
Map<String, Long> latencies = api.getHealthCheckLatencies();

// Circuit breaker state per server
Map<String, CircuitBreaker.State> breakers = api.getCircuitBreakerStatuses();
```

See [Developer API](Developer-API) for full documentation.

---

## Rollback

If v4 causes issues, you can rollback:

1. Stop the proxy
2. Replace the v4 JAR with the v3 JAR
3. Restore the v3 config:
   ```bash
   cp plugins/velocitynavigator/navigator.toml.v3-backup plugins/velocitynavigator/navigator.toml
   ```
4. Start the proxy

---

→ See also: [Configuration Guide](Configuration-Guide) | [Changelog](https://github.com/sdemonzdevelopment-spec/VelocityNavigator/blob/main/CHANGELOG.md)
