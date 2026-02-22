# Dashboard Notes: `notification-system-overview.json`

Because JSON does not support native comments, this file explains the dashboard panels and queries.

## Dashboard identity

- Title: `Notification System - Stress & Services`
- UID: `notification-stress-overview`
- Refresh: `10s`
- Time range: last `30m`

## Top stat panels

1. **App Up**
   - Query: `up{job="notification-app"}`
   - Meaning: app scrape target availability (`1` is healthy).

2. **Request Rate (RPS)**
   - Query: `sum(rate(http_server_requests_seconds_count[1m]))`
   - Meaning: current requests per second.

3. **p95 Latency**
   - Query: `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[1m])) by (le)) * 1000`
   - Meaning: 95th percentile response latency in milliseconds.

4. **5xx Error Rate**
   - Query: `sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m])) / clamp_min(sum(rate(http_server_requests_seconds_count[1m])), 1)`
   - Meaning: fraction of server errors among all responses.

## Time series panels

- **HTTP Throughput by Endpoint**
  - Groups request rate by `method` and `uri` labels.

- **HTTP Latency Percentiles**
  - Displays p50, p95, p99 over time.

- **JVM Heap**
  - Compares `jvm_memory_used_bytes` vs `jvm_memory_max_bytes`.

- **CPU Usage**
  - Shows `system_cpu_usage` and `process_cpu_usage`.

- **HikariCP Connections**
  - Tracks active/idle/max DB pool connections.

- **PostgreSQL Health & Connections**
  - Uses exporter metrics like `pg_up`, `pg_stat_database_numbackends`.

- **Redis Memory & Clients**
  - Uses `redis_memory_used_bytes`, `redis_connected_clients`.

- **Kafka Broker & Topic Partitions**
  - Uses `kafka_brokers`, `kafka_topic_partitions`.

## Customization tips

- Keep panel IDs unique.
- If query returns no data, verify metric names in Prometheus Explore.
- For noisy dashboards during spikes, increase refresh to `15s` or `30s`.
