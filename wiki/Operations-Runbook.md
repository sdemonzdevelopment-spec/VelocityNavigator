# Operations Runbook

> Structured reference for common operational tasks. Each entry: **symptom → cause → command → expected output**.

---

## Table of Contents

1. [Drain a Server for Maintenance](#drain-a-server-for-maintenance)
2. [Undrain a Server](#undrain-a-server)
3. [Check Circuit Breaker State](#check-circuit-breaker-state)
4. [Verify Routing Distribution](#verify-routing-distribution)
5. [Force a Health Check Refresh](#force-a-health-check-refresh)
6. [Interpret /vn status Output](#interpret-vn-status-output)
7. [Rollback a Config Change](#rollback-a-config-change)
8. [Check for Updates Manually](#check-for-updates-manually)
9. [Handle "No Lobby Found" Errors](#handle-no-lobby-found-errors)
10. [Setup Prometheus & Grafana Monitoring](#setup-prometheus--grafana-monitoring)

---

## Drain a Server for Maintenance

**When**: You need to take a server offline for updates, restarts, or maintenance.

**Steps**:

1. Mark the server as drained — no new players will be routed to it:
   ```
   /vn drain lobby-2
   ```

2. Verify the drain is active:
   ```
   /vn drain status
   ```

3. **Expected output**:
   ```
   Drained servers: lobby-2
   ```
   Or via `/vn status`, the server will show as `[DRAINED]`.

4. Wait for existing players to leave naturally, or kick them to another lobby first.

5. When ready, proceed with your maintenance.

**Notes**:
- Draining is **in-memory only** — it does not persist across proxy restarts
- Drained servers are excluded from ALL routing algorithms
- Existing players on the drained server are **not** kicked automatically

---

## Undrain a Server

**When**: Maintenance is complete and you want the server to receive traffic again.

**Steps**:

1. Remove the drain flag:
   ```
   /vn undrain lobby-2
   ```

2. Verify:
   ```
   /vn drain status
   ```

3. **Expected output**:
   ```
   No servers are currently drained.
   ```

4. The server will immediately be eligible for routing again.

---

## Check Circuit Breaker State

**When**: A server seems to be skipped by routing, or you suspect it's been marked as unhealthy.

**Steps**:

1. Check server health and circuit breaker state:
   ```
   /vn debug server lobby-2
   ```

2. **Expected output** includes:
   ```
   Server: lobby-2
   Registered: true
   Online: true
   Cached: true
   Checked at: 2025-05-17T12:00:00Z
   Sample age: 5s ago
   Players connected: 23
   Circuit breaker: CLOSED
   Drain status: active
   ```

3. Circuit breaker states:
   - **CLOSED** — Normal operation. Server is included in routing.
   - **OPEN** — Server has failed too many health checks. Excluded from routing.
   - **HALF_OPEN** — Server is being tested with limited traffic to see if it recovered.

4. To manually reset a circuit breaker (if you know the server is healthy):
   ```
   /vn reload
   ```
   This resets all circuit breaker states. There is no per-server reset command.

---

## Verify Routing Distribution

**When**: You want to confirm that players are being spread evenly across your lobbies.

**Steps**:

1. Check the status dashboard:
   ```
   /vn status
   ```

2. **Expected output** includes a routing distribution section:
   ```
   Routing Distribution (last 60s):
     lobby-1: 45 connections
     lobby-2: 43 connections
     lobby-3: 42 connections
   ```

3. If distribution is uneven:
   - Check if some servers are drained: `/vn drain status`
   - Check circuit breaker states: `/vn debug server <name>`
   - Verify your selection mode is appropriate (see [Routing Algorithms](Routing-Algorithms))
   - If using `weighted_round_robin`, check that weights are set correctly

4. For programmatic monitoring, use the [Developer API](Developer-API):
   ```java
   Map<String, Long> distribution = api.getRoutingDistribution();
   ```

---

## Force a Health Check Refresh

**When**: You've just restarted a backend server and want the proxy to detect it immediately (instead of waiting for the cache to expire).

**Steps**:

1. The health check cache runs on the interval set by `cache_seconds` (default: 60s). The cache warming task runs at 80% of the TTL to ensure fresh data is always available. To force a refresh:

   ```
   /vn reload
   ```

   This clears the health check cache, forcing fresh pings on the next routing request.

2. Alternatively, reduce `cache_seconds` temporarily:
   ```toml
   [health_checks]
   cache_seconds = 2
   ```
   Then reload and change it back when done.

3. **Expected result**: Within 2–10 seconds, the server should appear as healthy in `/vn debug server <name>`.

---

## Interpret /vn status Output

**When**: You need a quick overview of the plugin's state.

**Command**:
```
/vn status
```

**What each section means**:

| Section | What It Shows |
|---------|--------------|
| **Version** | Installed VelocityNavigator version |
| **Routing Mode** | Current selection algorithm |
| **Default Lobbies** | Configured lobby servers with player counts |
| **Circuit Breaker** | Summary of breaker states (e.g., "2 CLOSED, 1 OPEN") |
| **Drained Servers** | List of servers currently drained (or "None") |
| **Routing Distribution** | Connection counts per server over the last 60 seconds |
| **Health Checks** | Status of the health check system |
| **Contextual Routing** | Whether contextual routing is enabled |

---

## Rollback a Config Change

**When**: A config change caused issues and you need to revert.

**Steps**:

1. VelocityNavigator automatically creates a `.bak` backup when it migrates or overwrites the config. Check for it:
   ```bash
   ls plugins/velocitynavigator/
   ```

2. You should see:
   ```
   navigator.toml        ← current config
   navigator.toml.bak    ← backup from before last write
   ```

3. To rollback:
   ```bash
   cp plugins/velocitynavigator/navigator.toml.bak plugins/velocitynavigator/navigator.toml
   ```

4. Reload the config:
   ```
   /vn reload
   ```

5. **Verify**: Check that the old settings are active:
   ```
   /vn status
   ```

> **Best practice**: Before making manual config changes, always create your own backup:
> ```bash
> cp navigator.toml navigator.toml.manual-backup
> ```

---

## Check for Updates Manually

**When**: You want to see if a new version is available.

**Steps**:

1. Run the update check command:
   ```
   /vn updatecheck
   ```

2. **Expected output** (if update available):
   ```
   A new version of VelocityNavigator is available!
    Current: v4.2.0 | Latest: v4.2.1
   Download: https://modrinth.com/plugin/velocitynavigator
   ```

3. **Expected output** (if up to date):
   ```
    VelocityNavigator is up to date! (v4.2.0)
   ```

> **Note**: v4.1 re-introduced the periodic update checker with scheduled checks and exponential HTTP 429 backoff (up to 4 hours). Use `/vn updatecheck` for manual checks at any time.

---

## Handle "No Lobby Found" Errors

**When**: Players see the "No lobby found" message when using `/lobby`.

**Diagnosis**:

1. Check server health:
   ```
   /vn debug server lobby-1
   /vn debug server lobby-2
   ```

2. Check circuit breaker states:
   ```
   /vn status
   ```

3. Check if servers are drained:
   ```
   /vn drain status
   ```

4. Check the proxy console for errors

**Common causes and fixes**:

| Cause | Fix |
|-------|-----|
| All lobby servers are offline | Start the backend servers |
| All circuit breakers are OPEN | Run `/vn reload` to reset breakers, then investigate why servers are failing |
| Lobby servers don't match `velocity.toml` | Ensure server names in `default_lobbies` exactly match `velocity.toml` |
| Health check timeout too low | Increase `timeout_ms` in `[health_checks]` |
| All servers are drained | Run `/vn undrain <server>` |
| Degradation mode disabled | Enable `[degradation]` to fall back to random when all health checks fail |
| `max_players` cap reached | Increase `max_players` in LobbyEntry or set to `-1` (uncapped) |

5. **Quick fix for emergencies**: Enable degradation mode so the plugin picks a random server even if health checks fail:
   ```toml
   [degradation]
   enabled = true
   mode = "random"
   ```

---

## Setup Prometheus & Grafana Monitoring

**When**: You want to setup real-time metrics monitoring and visualize your proxy's lobby balancing health via a beautiful Grafana dashboard.

**Steps**:

1. **Enable the Prometheus Exporter**:
   Open `plugins/velocitynavigator/navigator.toml` and configure the `[metrics.prometheus]` section:
   ```toml
   [metrics]
   enabled = true # Enable bStats

   [metrics.prometheus]
   enabled = true # Enable Prometheus exporter
   port = 9225    # Port to serve metrics on (default: 9225)
   bind_host = "127.0.0.1" # Address to bind the HTTP server to (localhost for security)
   ```
   Reload the configuration with `/vn reload`.

2. **Verify Metrics Endpoint**:
   Use curl or your web browser to check the metrics page:
   ```bash
   curl http://localhost:9225/metrics
   ```
   **Expected output**:
   ```
   # HELP velocitynavigator_player_joins_total Total player joins to the proxy
   # TYPE velocitynavigator_player_joins_total counter
   velocitynavigator_player_joins_total 12.0
   # HELP velocitynavigator_server_online Online status of the server (1 = online, 0 = offline)
   # TYPE velocitynavigator_server_online gauge
   velocitynavigator_server_online{server="lobby-1"} 1.0
   velocitynavigator_server_players{server="lobby-1"} 4.0
   ...
   ```

3. **Configure Prometheus Scraper**:
   Add the following job to your `prometheus.yml` configuration:
   ```yaml
   scrape_configs:
     - job_name: 'velocity_navigator'
       static_configs:
         - targets: ['<proxy-ip>:9225']
   ```
   Restart Prometheus to apply.

4. **Generate the Grafana Dashboard JSON**:
   Run the setup command from the console or in-game as an administrator:
   ```
   /vn setup grafana
   ```
   **Expected output**:
   ```
   [VelocityNavigator] Grafana dashboard JSON generated and saved to plugins/VelocityNavigator/grafana-dashboard.json
   ```

5. **Import into Grafana**:
   - Open your Grafana dashboard.
   - Click **Dashboards** → **New** → **Import**.
   - Copy the contents of the generated `grafana-dashboard.json` and paste it into the **Import via panel json** box, or upload the file.
   - Select your Prometheus data source and click **Import**.
   - Enjoy a fully interactive, animated telemetry dashboard for your Velocity proxy!

**Metrics Exposed**:
- `velocitynavigator_player_joins_total` - Total player joins
- `velocitynavigator_player_leaves_total` - Total player leaves
- `velocitynavigator_server_online` - Server online status (all registered servers)
- `velocitynavigator_server_players` - Player count per server (all registered servers)
- `velocitynavigator_server_latency_ms` - Health check ping latency in ms (lobby servers only)
- `velocitynavigator_server_circuit_breaker` - Circuit breaker state (0=CLOSED, 1=HALF_OPEN, 2=OPEN)
- `velocitynavigator_server_drained` - Drain status (1=drained, 0=active)
- `velocitynavigator_routed_connections_total` - Total routed connections
- `velocitynavigator_redirects_total` - Total connection routes grouped by reason (affinity, consistent_hash, direct_connect, bedrock_gui, least_players, round_robin, etc.) and target server
- `velocitynavigator_circuit_breaker_trips_total` - Cumulative circuit breaker trips per server
- `velocitynavigator_fallback_events_total` - Fallback events count by type (degradation, retry, contextual)

---

→ See also: [Troubleshooting Guide](Troubleshooting-Guide) | [Configuration Guide](Configuration-Guide) | [FAQ](FAQ)
