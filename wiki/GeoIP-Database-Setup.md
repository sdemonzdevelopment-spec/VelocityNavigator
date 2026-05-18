# Database Setup

> Everything you need to know about data storage in VelocityNavigator — including the only external data file you might ever need.

---

## ❓ Do I Need a Database?

**Short answer: Probably not.** VelocityNavigator does **not** require MySQL, PostgreSQL, Redis, or any SQL/NoSQL database.

| Question | Answer |
|----------|--------|
| Does VelocityNavigator need MySQL or PostgreSQL? | **No.** There is no SQL dependency. |
| Where is player data stored? | **In memory.** Player affinity, cooldown timers, and circuit breaker state are all kept in the proxy's JVM memory. |
| Does data persist across restarts? | **No.** All in-memory state resets when the proxy restarts. This is by design — routing decisions are based on real-time conditions. |
| Is there any external data file required? | **Only if you use geo-routing.** The GeoLite2 `.mmdb` file is the one optional data file. |
| What if I don't use geo-routing? | **You don't need any database at all.** Just install and configure the plugin. |

> **TL;DR**: If you're not using `[routing.geo]`, you need zero external data files. If you *are* using geo-routing, you need one free `.mmdb` file — keep reading.

---

## GeoIP Database (GeoLite2)

*This is the only external data file VelocityNavigator uses. It is required **only** if you enable geo-based routing.*

### What Is Geo-Based Routing?

Geo-based routing sends players to lobbies that are geographically closest to them. A player connecting from Europe would be routed to a European lobby, while a player from North America would go to a US lobby.

This requires a **GeoLite2 Country database** from MaxMind, which maps IP addresses to countries.

---

### When You Need It

- You have lobby servers in **multiple geographic regions** (e.g., US, EU, Asia)
- You want to reduce latency by routing players to nearby servers
- You don't need it if all your servers are in one location

---

### Step 1: Create a Free MaxMind Account

1. Go to [https://www.maxmind.com/en/geolite2/signup](https://www.maxmind.com/en/geolite2/signup)
2. Fill in the registration form (name, email, password)
3. Accept the GeoLite2 End User License Agreement
4. Verify your email address

> **Cost**: GeoLite2 is **free**. MaxMind also offers paid GeoIP2 databases with more granularity, but the free Country database is all you need for lobby routing.

---

### Step 2: Download the Database

1. Log in to your MaxMind account
2. Go to **Downloads** → **GeoLite2** → **GeoLite2 Country**
3. Download the **MMDB** format (not CSV)
4. The file will be named something like `GeoLite2-Country_20260325.tar.gz`
5. Extract the archive — you need the `GeoLite2-Country.mmdb` file inside

---

### Step 3: Place the Database File

Copy the `.mmdb` file to your VelocityNavigator plugin directory:

```
plugins/
└── velocitynavigator/
    ├── navigator.toml
    └── GeoLite2-Country.mmdb   ← Place it here
```

The default config looks for this file relative to the plugin directory.

---

### Step 4: Configure the Geo Routing Section

Edit `navigator.toml`:

```toml
[geo_routing]
enabled = true
database_path = "GeoLite2-Country.mmdb"
```

| Setting | Description |
|---------|-------------|
| `enabled` | Set to `true` to activate geo-routing. |
| `database_path` | Path to the `.mmdb` file, relative to the plugin directory. |

Then reload the config:

```
/vn reload
```

---

### Step 5: Verify It Works

Run the debug command to check that the GeoIP database loaded:

```
/vn debug server lobby-eu-1
```

You should see geo-routing information in the output. If the database is loaded correctly, lookups will return country codes for player IPs.

You can also check the proxy console for any GeoIP-related errors on startup or reload.

---

### Troubleshooting

#### "Database not found"

**Symptom**: Console shows a warning that the GeoLite2 database was not found.

**Cause**: The file path in `database_path` doesn't point to a valid `.mmdb` file.

**Fix**:
1. Verify the file exists at the path you specified
2. Make sure the path is relative to `plugins/velocitynavigator/`
3. Check that the file is `.mmdb` format, not the `.tar.gz` archive
4. Re-download and extract if the file might be corrupted

#### "Outdated database"

**Symptom**: Console warns that the GeoLite2 database is outdated.

**Cause**: MaxMind updates GeoLite2 databases weekly. An old database may have inaccurate IP-to-country mappings.

**Fix**:
1. Log in to your MaxMind account
2. Re-download the latest GeoLite2-Country database
3. Replace the old `.mmdb` file
4. Run `/vn reload`

> **Tip**: MaxMind offers a direct download URL you can use in a cron job or update script. Find it in your MaxMind account under **Manage License Keys** → **GeoLite2 Download**.

#### Geo-routing not working despite database being loaded

**Symptom**: Players are not being routed to geographically closer servers.

**Cause**: Geo-routing is **experimental** in v4.0.0. The stub implementation may return empty results.

**Fix**: This feature is under active development. Check for updates in future releases.

#### Database file permissions

**Symptom**: Console shows a permission error when reading the database.

**Fix**: Ensure the Velocity proxy process has read access to the `.mmdb` file:
```bash
chmod 644 plugins/velocitynavigator/GeoLite2-Country.mmdb
```

---

### Keeping the Database Updated

MaxMind updates GeoLite2 databases every **Tuesday**. For production use, set up an automated download:

1. Generate a **license key** in your MaxMind account
2. Use the download URL format: `https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-Country&license_key=YOUR_KEY&suffix=tar.gz`
3. Schedule a weekly download with cron or your preferred task scheduler
4. After downloading, extract and reload: `/vn reload`

---

→ See also: [Configuration Guide](Configuration-Guide) | [Troubleshooting Guide](Troubleshooting-Guide) | [FAQ](FAQ)
