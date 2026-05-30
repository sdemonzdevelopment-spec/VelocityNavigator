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

## Command & Permission Issues

### Command `/lobby` is unrecognized or says "No permission"

**Symptoms**:
- Ordinary players type `/lobby` (or configured aliases like `/hub`) and get "You do not have permission to use this command."
- The command doesn't show up in chat autocomplete/tab-completion for players.

**Likely cause**: A custom `commands.permission` is set in the config, or the default `"none"` was overridden in a prior version.

**Debugging steps**:
1. Check if the player has the `velocitynavigator.use` permission.
2. Check the `permission` setting in `navigator.toml`.

**Resolution**:
- Assign players the `velocitynavigator.use` permission node in your permissions manager (e.g. LuckPerms).
- **OR** disable the permission requirement completely by setting it to `"none"` in your `navigator.toml`:
  ```toml
  [commands]
  permission = "none"
  ```
- Run `/vn reload` to apply configuration changes.

---

### Players get blocked by spam cooldowns

**Symptoms**:
- Players see the cooldown message: "Please wait X more second(s)." when trying to run the `/lobby` command.

**Likely cause**: v4.1.0 enforces a default 3-second anti-spam cooldown on command executions.

**Resolution**:
- Grant bypass permission to players/groups who shouldn't have a cooldown: `velocitynavigator.bypass.cooldown` (or legacy `velocitynavigator.bypasscooldown`).
- **OR** reduce/disable the cooldown in your `navigator.toml`:
  ```toml
  [commands]
  cooldown_seconds = 0
  ```
- Run `/vn reload` to apply configuration changes.

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
- Make sure you're running v4.1.0+
- The `<player>` placeholder is supported in all message fields:
  ```toml
  [messages]
  connecting = "<green>Connecting <player> to a lobby...</green>"
  ```

---

## Monitoring & Integration Issues

### Prometheus exporter fails to bind to port (Address already in use)

**Symptoms**:
- Console shows log error: `Failed to start Prometheus exporter on port 9225: Address already in use`

**Likely cause**: Another proxy instance or service is already using port 9225 on the host machine.

**Resolution**:
- Change the Prometheus bind port in your `navigator.toml` under `[metrics.prometheus.port]` to a free port (e.g. `9226` or `9230`).
- Reload the configuration with `/vn reload`.

---

### Prometheus exporter fails with "Cannot assign requested address" (IP Bind Error)

**Symptoms**:
- Console shows log error containing: `java.net.BindException: Cannot assign requested address`
- Prometheus server fails to start on proxy launch

**Likely cause**: The `bind_host` in your `navigator.toml` is configured to a specific public IP (e.g. `45.82.121.194`) which is not directly bound/assigned to the local network interfaces of the host or container (extremely common in containerized environments like Pterodactyl, Docker, or other game panel hosts).

**Resolution**:
- Change the Prometheus bind host in your `navigator.toml` to `0.0.0.0` (which binds to all local network interfaces):
  ```toml
  [metrics.prometheus]
  enabled = true
  bind_host = "0.0.0.0"
  port = 9225
  ```
- If your container system requires binding to a specific internal IP, consult your panel's networking settings or use `127.0.0.1` (if only scraping locally).
- Restart the proxy (or reload the configuration via `/vn reload`).

---

### Bedrock Form GUI does not open for Bedrock players

**Symptoms**:
- Bedrock/Geyser players run `/lobby` but receive a standard text list or connect directly, rather than opening a Form GUI.

**Likely cause**: Geyser/Floodgate is not running on the proxy, or `bedrock.use_gui_for_lobby` is disabled.

**Resolution**:
- Ensure the Floodgate plugin is installed and active on the Velocity proxy.
- Set `bedrock.use_gui_for_lobby = true` in `navigator.toml`.
- Run `/vn reload`.

---

### Grafana dashboard file cannot be generated

**Symptoms**:
- Admin runs `/vn setup grafana` but no `grafana-dashboard.json` is written, or an error is printed in console.

**Likely cause**: File system permission issues in the proxy's directory.

**Resolution**:
- Ensure the user running the Velocity proxy has write permissions to the `plugins/VelocityNavigator` folder.

---

### Example Grafana Dashboard JSON

If you want to manually import the Grafana dashboard for VelocityNavigator without running the `/vn setup grafana` command (or if the command fails due to permission/container issues), copy the JSON configuration below and paste it in Grafana (**Dashboard -> New -> Import -> Paste JSON**):

<details>
<summary>Click to expand Example Grafana Dashboard JSON</summary>

```json
{
  "annotations": {
    "list": []
  },
  "editable": true,
  "fiscalYearStartMonth": 0,
  "graphTooltip": 1,
  "id": null,
  "links": [],
  "liveNow": false,
  "panels": [
    {
      "collapsed": false,
      "gridPos": {
        "h": 1,
        "w": 24,
        "x": 0,
        "y": 0
      },
      "id": 1,
      "title": "Global Metrics",
      "type": "row"
    },
    {
      "datasource": {
        "type": "prometheus",
        "uid": "prometheus"
      },
      "fieldConfig": {
        "defaults": {
          "color": {
            "mode": "thresholds"
          },
          "mappings": [],
          "thresholds": {
            "mode": "absolute",
            "steps": [
              {
                "color": "green",
                "value": null
              }
            ]
          }
        },
        "overrides": []
      },
      "gridPos": {
        "h": 4,
        "w": 8,
        "x": 0,
        "y": 1
      },
      "id": 2,
      "options": {
        "colorMode": "value",
        "graphMode": "area",
        "justifyMode": "auto",
        "orientation": "auto",
        "reduceOptions": {
          "calcs": [
            "lastNotNull"
          ],
          "fields": "",
          "values": false
        },
        "textMode": "auto"
      },
      "targets": [
        {
          "datasource": {
            "type": "prometheus",
            "uid": "prometheus"
          },
          "editorMode": "code",
          "expr": "sum(velocitynavigator_server_players{server=~\"^$server$\"})",
          "legendFormat": "Total Players",
          "range": true,
          "refId": "A"
        }
      ],
      "title": "Total Lobby Players",
      "type": "stat"
    },
    {
      "datasource": {
        "type": "prometheus",
        "uid": "prometheus"
      },
      "fieldConfig": {
        "defaults": {
          "color": {
            "mode": "thresholds"
          },
          "mappings": [],
          "thresholds": {
            "mode": "absolute",
            "steps": [
              {
                "color": "red",
                "value": null
              },
              {
                "color": "green",
                "value": 1
              }
            ]
          }
        },
        "overrides": []
      },
      "gridPos": {
        "h": 4,
        "w": 8,
        "x": 8,
        "y": 1
      },
      "id": 3,
      "options": {
        "colorMode": "value",
        "graphMode": "none",
        "justifyMode": "auto",
        "orientation": "auto",
        "reduceOptions": {
          "calcs": [
            "lastNotNull"
          ],
          "fields": "",
          "values": false
        },
        "textMode": "auto"
      },
      "targets": [
        {
          "datasource": {
            "type": "prometheus",
            "uid": "prometheus"
          },
          "editorMode": "code",
          "expr": "sum(velocitynavigator_server_online{server=~\"^$server$\"})",
          "legendFormat": "Online Lobbies",
          "range": true,
          "refId": "A"
        }
      ],
      "title": "Online Lobbies Count",
      "type": "stat"
    },
    {
      "datasource": {
        "type": "prometheus",
        "uid": "prometheus"
      },
      "fieldConfig": {
        "defaults": {
          "color": {
            "mode": "thresholds"
          },
          "mappings": [],
          "thresholds": {
            "mode": "absolute",
            "steps": [
              {
                "color": "green",
                "value": null
              },
              {
                "color": "yellow",
                "value": 1
              }
            ]
          }
        },
        "overrides": []
      },
      "gridPos": {
        "h": 4,
        "w": 8,
        "x": 16,
        "y": 1
      },
      "id": 4,
      "options": {
        "colorMode": "value",
        "graphMode": "none",
        "justifyMode": "auto",
        "orientation": "auto",
        "reduceOptions": {
          "calcs": [
            "lastNotNull"
          ],
          "fields": "",
          "values": false
        },
        "textMode": "auto"
      },
      "targets": [
        {
          "datasource": {
            "type": "prometheus",
            "uid": "prometheus"
          },
          "editorMode": "code",
          "expr": "sum(velocitynavigator_server_drained{server=~\"^$server$\"})",
          "legendFormat": "Drained Lobbies",
          "range": true,
          "refId": "A"
        }
      ],
      "title": "Drained Lobbies",
      "type": "stat"
    },
    {
      "collapsed": false,
      "gridPos": {
        "h": 1,
        "w": 24,
        "x": 0,
        "y": 5
      },
      "id": 5,
      "title": "Server Telemetry & Performance",
      "type": "row"
    },
    {
      "datasource": {
        "type": "prometheus",
        "uid": "prometheus"
      },
      "fieldConfig": {
        "defaults": {
          "custom": {
            "drawStyle": "line",
            "lineInterpolation": "smooth"
          },
          "unit": "ms"
        },
        "overrides": []
      },
      "gridPos": {
        "h": 8,
        "w": 12,
        "x": 0,
        "y": 6
      },
      "id": 6,
      "options": {
        "legend": {
          "calcs": [
            "mean",
            "max"
          ],
          "displayMode": "table",
          "placement": "bottom",
          "showLegend": true
        },
        "tooltip": {
          "mode": "multi",
          "sort": "none"
        }
      },
      "targets": [
        {
          "datasource": {
            "type": "prometheus",
            "uid": "prometheus"
          },
          "editorMode": "code",
          "expr": "velocitynavigator_server_latency_ms{server=~\"^$server$\"} >= 0",
          "legendFormat": "{{server}}",
          "range": true,
          "refId": "A"
        }
      ],
      "title": "Health Check Latency",
      "type": "timeseries"
    },
    {
      "datasource": {
        "type": "prometheus",
        "uid": "prometheus"
      },
      "fieldConfig": {
        "defaults": {
          "custom": {
            "drawStyle": "line",
            "lineInterpolation": "smooth"
          }
        },
        "overrides": []
      },
      "gridPos": {
        "h": 8,
        "w": 12,
        "x": 12,
        "y": 6
      },
      "id": 7,
      "options": {
        "legend": {
          "calcs": [
            "lastNotNull",
            "max"
          ],
          "displayMode": "table",
          "placement": "bottom",
          "showLegend": true
        },
        "tooltip": {
          "mode": "multi",
          "sort": "none"
        }
      },
      "targets": [
        {
          "datasource": {
            "type": "prometheus",
            "uid": "prometheus"
          },
          "editorMode": "code",
          "expr": "velocitynavigator_server_players{server=~\"^$server$\"}",
          "legendFormat": "{{server}}",
          "range": true,
          "refId": "A"
        }
      ],
      "title": "Player Count Distribution",
      "type": "timeseries"
    },
    {
      "datasource": {
        "type": "prometheus",
        "uid": "prometheus"
      },
      "fieldConfig": {
        "defaults": {
          "color": {
            "mode": "thresholds"
          },
          "custom": {
            "fillOpacity": 70,
            "lineWidth": 1
          },
          "mappings": [
            {
              "options": {
                "from": 0,
                "result": {
                  "color": "green",
                  "text": "CLOSED (Healthy)"
                },
                "to": 0
              },
              "type": "range"
            },
            {
              "options": {
                "from": 1,
                "result": {
                  "color": "yellow",
                  "text": "HALF_OPEN (Testing)"
                },
                "to": 1
              },
              "type": "range"
            },
            {
              "options": {
                "from": 2,
                "result": {
                  "color": "red",
                  "text": "OPEN (Tripped)"
                },
                "to": 2
              },
              "type": "range"
            }
          ],
          "thresholds": {
            "mode": "absolute",
            "steps": [
              {
                "color": "green",
                "value": null
              },
              {
                "color": "yellow",
                "value": 1
              },
              {
                "color": "red",
                "value": 2
              }
            ]
          }
        },
        "overrides": []
      },
      "gridPos": {
        "h": 6,
        "w": 24,
        "x": 0,
        "y": 14
      },
      "id": 8,
      "options": {
        "rowHeight": 0.9,
        "showValue": "always",
        "tooltip": {
          "mode": "single",
          "sort": "none"
        }
      },
      "targets": [
        {
          "datasource": {
            "type": "prometheus",
            "uid": "prometheus"
          },
          "editorMode": "code",
          "expr": "velocitynavigator_server_circuit_breaker{server=~\"^$server$\"}",
          "legendFormat": "{{server}}",
          "range": true,
          "refId": "A"
        }
      ],
      "title": "Circuit Breaker State history",
      "type": "state-timeline"
    },
    {
      "datasource": {
        "type": "prometheus",
        "uid": "prometheus"
      },
      "fieldConfig": {
        "defaults": {
          "custom": {
            "drawStyle": "line",
            "lineInterpolation": "linear"
          }
        },
        "overrides": []
      },
      "gridPos": {
        "h": 6,
        "w": 24,
        "x": 0,
        "y": 20
      },
      "id": 9,
      "options": {
        "legend": {
          "calcs": [
            "sum"
          ],
          "displayMode": "table",
          "placement": "right",
          "showLegend": true
        },
        "tooltip": {
          "mode": "multi",
          "sort": "none"
        }
      },
      "targets": [
        {
          "datasource": {
            "type": "prometheus",
            "uid": "prometheus"
          },
          "editorMode": "code",
          "expr": "rate(velocitynavigator_routed_connections_total{server=~\"^$server$\"}[5m])",
          "legendFormat": "{{server}}",
          "range": true,
          "refId": "A"
        }
      ],
      "title": "Routed Connections Rate (per second)",
      "type": "timeseries"
    },
    {
      "collapsed": false,
      "gridPos": {
        "h": 1,
        "w": 24,
        "x": 0,
        "y": 26
      },
      "id": 14,
      "title": "Traffic & Event Diagnostics",
      "type": "row"
    },
    {
      "datasource": {
        "type": "prometheus",
        "uid": "prometheus"
      },
      "fieldConfig": {
        "defaults": {
          "custom": {
            "drawStyle": "line",
            "lineInterpolation": "smooth"
          },
          "unit": "pps"
        },
        "overrides": []
      },
      "gridPos": {
        "h": 8,
        "w": 12,
        "x": 0,
        "y": 27
      },
      "id": 10,
      "options": {
        "legend": {
          "calcs": [
            "mean",
            "max"
          ],
          "displayMode": "table",
          "placement": "bottom",
          "showLegend": true
        },
        "tooltip": {
          "mode": "multi",
          "sort": "none"
        }
      },
      "targets": [
        {
          "datasource": {
            "type": "prometheus",
            "uid": "prometheus"
          },
          "editorMode": "code",
          "expr": "rate(velocitynavigator_player_joins_total[5m])",
          "legendFormat": "Joins",
          "range": true,
          "refId": "A"
        },
        {
          "datasource": {
            "type": "prometheus",
            "uid": "prometheus"
          },
          "editorMode": "code",
          "expr": "rate(velocitynavigator_player_leaves_total[5m])",
          "legendFormat": "Leaves",
          "range": true,
          "refId": "B"
        }
      ],
      "title": "Join/Leave Rates (per second)",
      "type": "timeseries"
    },
    {
      "datasource": {
        "type": "prometheus",
        "uid": "prometheus"
      },
      "fieldConfig": {
        "defaults": {
          "color": {
            "mode": "palette-classic"
          },
          "mappings": [],
          "thresholds": {
            "mode": "absolute",
            "steps": [
              {
                "color": "green",
                "value": null
              }
            ]
          }
        },
        "overrides": []
      },
      "gridPos": {
        "h": 8,
        "w": 12,
        "x": 12,
        "y": 27
      },
      "id": 11,
      "options": {
        "displayMode": "basic",
        "orientation": "horizontal",
        "reduceOptions": {
          "calcs": [
            "lastNotNull"
          ],
          "fields": "",
          "values": false
        },
        "showUnfilled": true
      },
      "targets": [
        {
          "datasource": {
            "type": "prometheus",
            "uid": "prometheus"
          },
          "editorMode": "code",
          "expr": "sum(velocitynavigator_redirects_total) by (reason)",
          "legendFormat": "{{reason}}",
          "range": true,
          "refId": "A"
        }
      ],
      "title": "Redirect Reasons (Why Players Moved)",
      "type": "bargauge"
    },
    {
      "datasource": {
        "type": "prometheus",
        "uid": "prometheus"
      },
      "fieldConfig": {
        "defaults": {
          "color": {
            "mode": "thresholds"
          },
          "mappings": [],
          "thresholds": {
            "mode": "absolute",
            "steps": [
              {
                "color": "green",
                "value": null
              },
              {
                "color": "orange",
                "value": 1
              },
              {
                "color": "red",
                "value": 5
              }
            ]
          }
        },
        "overrides": []
      },
      "gridPos": {
        "h": 8,
        "w": 12,
        "x": 0,
        "y": 35
      },
      "id": 12,
      "options": {
        "displayMode": "basic",
        "orientation": "horizontal",
        "reduceOptions": {
          "calcs": [
            "lastNotNull"
          ],
          "fields": "",
          "values": false
        },
        "showUnfilled": true
      },
      "targets": [
        {
          "datasource": {
            "type": "prometheus",
            "uid": "prometheus"
          },
          "editorMode": "code",
          "expr": "velocitynavigator_circuit_breaker_trips_total{server=~\"^$server$\"}",
          "legendFormat": "{{server}}",
          "range": true,
          "refId": "A"
        }
      ],
      "title": "Circuit Breaker Trips by Server",
      "type": "bargauge"
    },
    {
      "datasource": {
        "type": "prometheus",
        "uid": "prometheus"
      },
      "fieldConfig": {
        "defaults": {
          "color": {
            "mode": "palette-classic"
          },
          "mappings": [],
          "thresholds": {
            "mode": "absolute",
            "steps": [
              {
                "color": "green",
                "value": null
              }
            ]
          }
        },
        "overrides": []
      },
      "gridPos": {
        "h": 8,
        "w": 12,
        "x": 12,
        "y": 35
      },
      "id": 13,
      "options": {
        "displayMode": "basic",
        "orientation": "horizontal",
        "reduceOptions": {
          "calcs": [
            "lastNotNull"
          ],
          "fields": "",
          "values": false
        },
        "showUnfilled": true
      },
      "targets": [
        {
          "datasource": {
            "type": "prometheus",
            "uid": "prometheus"
          },
          "editorMode": "code",
          "expr": "velocitynavigator_fallback_events_total",
          "legendFormat": "{{type}}",
          "range": true,
          "refId": "A"
        }
      ],
      "title": "Fallback Events by Type",
      "type": "bargauge"
    }
  ],
  "refresh": "5s",
  "schemaVersion": 38,
  "style": "dark",
  "tags": [
    "minecraft",
    "velocitynavigator",
    "loadbalancer"
  ],
  "templating": {
    "list": [
      {
        "current": {},
        "datasource": {
          "type": "prometheus",
          "uid": "prometheus"
        },
        "definition": "label_values(velocitynavigator_server_online, server)",
        "hide": 0,
        "includeAll": true,
        "multi": true,
        "name": "server",
        "options": [],
        "query": {
          "query": "label_values(velocitynavigator_server_online, server)",
          "refId": "StandardVariableQuery"
        },
        "refresh": 1,
        "regex": "",
        "skipUrlSync": false,
        "sort": 1,
        "type": "query"
      }
    ]
  },
  "time": {
    "from": "now-1h",
    "to": "now"
  },
  "timepicker": {},
  "timezone": "",
  "title": "VelocityNavigator Lobby Diagnostics",
  "uid": "vn_lobby_diagnostics",
  "version": 1
}
```

</details>

---

→ See also: [Operations Runbook](Operations-Runbook) | [FAQ](FAQ) | [Configuration Guide](Configuration-Guide)
