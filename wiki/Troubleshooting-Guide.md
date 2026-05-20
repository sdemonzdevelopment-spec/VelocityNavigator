# Troubleshooting Guide

> Symptom-based guide organized by category. Each entry: **symptoms → likely cause → debugging steps → resolution**.

---

## Connection Issues

### Players can't connect to any lobby

**Symptoms**:
- Players see "No lobby found! (all servers unavailable)" when typing `/lobby`
- New players can't join the network at all

**Likely cause**: All lobby servers are offline or unreachable from the proxy.

**Debugging steps**:
1. Check if backend servers are running: `vn debug server lobby-1`
2. Check circuit breaker states: `/vn status`
3. Check if all servers are drained: `/vn drain status`
4. Check the proxy console for connection errors

**Resolution**:
- Start the backend servers
- If circuit breakers are all OPEN, run `/vn reload` to reset them
- If servers are accidentally drained, run `/vn undrain <server>`
- Enable degradation mode as a safety net:
  ```toml
  [degradation]
  enabled = true
  mode = "random"
  ```

---

### Players get "No lobby found" intermittently

**Symptoms**:
- Sometimes `/lobby` works, sometimes it doesn't
- Error appears during peak times

**Likely cause**: Health check timeouts causing servers to be temporarily excluded.

**Debugging steps**:
1. Check health check timeout: is `timeout_ms` too low for your servers?
2. Check if servers are hitting the `max_players` cap
3. Run `/vn debug server <name>` during peak time

**Resolution**:
- Increase health check timeout:
  ```toml
  [health_checks]
  timeout_ms = 1000  # Increase from 500
  ```
- Increase `max_players` caps or set to `-1` (uncapped)
- Reduce `cache_seconds` for more responsive health checks

---

### Connection fails, then player is stuck

**Symptoms**:
- Player uses `/lobby`, connection starts but fails
- Player stays on their current server
- No retry attempt is made

**Likely cause**: `max_retries` is set to 0, or retry message is empty.

**Debugging steps**:
1. Check `max_retries` in config:
   ```toml
   [routing]
   max_retries = 2  # Should be > 0
   ```
2. Check the console for connection failure logs

**Resolution**:
- Set `max_retries` to at least 2
- Verify the `messages.retrying` message is configured

---

## Routing Issues

### Uneven player distribution

**Symptoms**:
- One lobby server has many more players than others
- `/vn status` shows unbalanced distribution

**Likely causes and fixes**:

| Cause | How to Check | Fix |
|-------|-------------|-----|
| Using `random` with few players | Check `selection_mode` | Switch to `least_players` or `power_of_two` |
| Server is drained | `/vn drain status` | `/vn undrain <server>` |
| Circuit breaker is OPEN | `/vn debug server <name>` | Fix the server or `/vn reload` to reset breakers |
| Unequal weights in WRR | Check config `default_lobbies` | Adjust `weight` values or switch modes |
| `max_players` too low | Check LobbyEntry config | Increase `max_players` or set to `-1` |
| Bursty traffic | Check connection timestamps | Switch to `least_connections` for EMA smoothing |

---

### Players always go to the same server

**Symptoms**:
- All players end up on one lobby regardless of load
- Other lobbies stay empty

**Likely cause**: Using `consistent_hash` mode with few players, or only one healthy server.

**Debugging steps**:
1. Check selection mode: `/vn status`
2. Check how many servers are healthy: `/vn debug server <name>` for each lobby
3. Check circuit breaker states

**Resolution**:
- If using `consistent_hash`: this is expected behavior — players with the same UUID always go to the same server. Switch to `power_of_two` or `least_players` for even distribution.
- If only one server is healthy: fix the other servers or reduce `failure_threshold` if they're being incorrectly marked as unhealthy.

---

### Player affinity keeps sending players back to a bad server

**Symptoms**:
- Player keeps getting routed to a server they don't want
- Even after a server was temporarily down, players return to it immediately when it recovers

**Likely cause**: Player affinity (sticky sessions) is sending players back to their previously connected lobby due to the `0.7` stickiness factor.

**Resolution**:
- You can tune or disable player affinity entirely in your `navigator.toml` under the `[routing.affinity]` block:
  ```toml
  [routing.affinity]
  enabled = true       # Set to false to completely disable sticky sessions
  stickiness = 0.4     # Decrease from 0.7 to reduce stickiness chance (40% chance)
  ```
- If a server is completely down, the affinity system will naturally skip it because it is missing from the healthy candidates list.
- If a server is running but in an unhealthy state, ensure health checks and circuit breakers are enabled so the proxy excludes it from the selection pool automatically.

---

## Health Check Issues

### Servers showing as offline when they're running

**Symptoms**:
- `/vn debug server` shows a server as unhealthy
- Server is clearly running and players can connect directly

**Likely cause**: Health check timeout too low, or network latency between proxy and backend.

**Debugging steps**:
1. Check the latency in debug output: `/vn debug server <name>`
2. Compare the latency with your `timeout_ms` setting

**Resolution**:
- Increase timeout:
  ```toml
  [health_checks]
  timeout_ms = 1000  # or higher for high-latency setups
  ```

---

### Circuit breaker keeps opening

**Symptoms**:
- Servers repeatedly get excluded from routing
- Circuit breaker cycles between OPEN and HALF_OPEN

**Likely cause**: Intermittent health check failures (network blips, server GC pauses).

**Debugging steps**:
1. Check the failure threshold — is it too low?
2. Monitor server TPS and GC behavior
3. Check network stability between proxy and backend

**Resolution**:
- Increase `failure_threshold`:
  ```toml
  [circuit_breaker]
  failure_threshold = 5  # Increase from 3
  ```
- Increase `cooldown_seconds` to give servers more time to recover
- Increase `half_open_max_tests` to require more successful tests before closing

---

## Config Issues

### Changes aren't taking effect

**Symptoms**:
- Edited `navigator.toml` but behavior doesn't change

**Likely cause**: Forgot to reload the config.

**Resolution**:
```
/vn reload
```

---

### Config migration warnings in console

**Symptoms**:
- Warnings like "Config field 'update_checker.enabled' is no longer supported"

**Likely cause**: v3 config fields that are no longer used in v4.

**Resolution**:
- These are harmless warnings — v4 ignores unknown fields
- To clean up your config, remove the deprecated fields listed in the [Migration Guide](Migration-Guide-v3-to-v4)

---

### Server names not matching

**Symptoms**:
- "No lobby found" error even though servers exist
- Servers show as offline in debug

**Likely cause**: Server names in `default_lobbies` don't exactly match the names in `velocity.toml`.

**Debugging steps**:
1. Check `velocity.toml` for exact server names
2. Compare with `navigator.toml` `default_lobbies`
3. Names are case-sensitive

**Resolution**:
- Ensure exact match:
  ```toml
  # velocity.toml
  [servers]
  lobby-1 = "..."   # Note: lowercase, hyphen

  # navigator.toml
  default_lobbies = ["lobby-1"]  # Must match exactly
  ```

---

### "Player" placeholder not working in messages

**Symptoms**:
- Messages show literal `<player>` instead of the player's name

**Likely cause**: Using v3 message format without the v4 placeholder.

**Resolution**:
- Make sure you're running v4.0.0+
- The `<player>` placeholder is supported in all message fields:
  ```toml
  [messages]
  connecting = "<green>Connecting <player> to a lobby...</green>"
  ```

---

→ See also: [Operations Runbook](Operations-Runbook) | [FAQ](FAQ) | [Configuration Guide](Configuration-Guide)
