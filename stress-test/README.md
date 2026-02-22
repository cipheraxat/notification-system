# Stress Testing (k6)

This folder contains repeatable load profiles for the notification system.

> Goal: help you run load tests and understand what each script is proving.

## Prerequisites

- Install k6: https://k6.io/docs/get-started/installation/
- Start app and infra:
  - `docker-compose up -d`
  - `mvn spring-boot:run`

Quick pre-checks:

```bash
curl -s http://localhost:8080/api/v1/health
curl -s http://localhost:3000/api/health
```

## Scripts

- `k6/stress-test.js` -> steady high load with gradual ramp-up.
  - Use this to measure stable throughput and baseline latency.
  - Better for tuning DB pool, JVM, and cache under predictable pressure.

- `k6/spike-test.js` -> explicit traffic spikes and rapid drop/recovery phases.
  - Use this to validate burst handling and recovery behavior.
  - Better for queue backpressure, retry behavior, and tail latency checks.

- `k6/heap-test.js` -> invoke the debug endpoint repeatedly to allocate heap.
  - Useful when you want to watch JVM heap growth and GC under pressure.
  - Pairs with Grafana's "JVM Heap" panel.

## Run commands

### 1) Baseline steady load (GET /)

```bash
k6 run stress-test/k6/stress-test.js
```

What this tells you:
- Sustained request capacity over time.
- Whether p95/p99 latency stays stable as load increases.

### 2) Spike test (GET /)

```bash
k6 run stress-test/k6/spike-test.js
```

What this tells you:
- How quickly latency/error rate rises during a burst.
- How fast the system recovers after traffic drops.

### 3) Target notification API with POST

```bash
BASE_URL=http://localhost:8080 \
TARGET_PATH=/api/v1/notifications \
METHOD=POST \
k6 run stress-test/k6/spike-test.js
```

### 4) Custom payload for POST tests

```bash
PAYLOAD='{"userId":"550e8400-e29b-41d4-a716-446655440001","channel":"IN_APP","content":"hello"}' \
BASE_URL=http://localhost:8080 \
TARGET_PATH=/api/v1/notifications \
METHOD=POST \
k6 run stress-test/k6/stress-test.js
```

## Environment variables

| Variable | Default | Meaning |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | Target host |
| `TARGET_PATH` | `/` | Endpoint path |
| `METHOD` | `GET` | HTTP method (`GET`/`POST`) |
| `PAYLOAD` | Built-in JSON | Request body used only for `POST` |

## Tips

- For strict endpoint validation, keep `METHOD=POST` only if payload matches endpoint requirements.
- During tests, open Grafana dashboard to observe p95 latency, throughput, JVM heap, DB/Redis/Kafka behavior.

## Debug & Heap Stress

A special controller exists at `/api/v1/debug` that lets you allocate or release heap memory on demand. This is helpful for observing garbage collection and heap pressure during load.

- **Allocate**: `POST /api/v1/debug/alloc?mb=50` will append 50 MB of garbage to JVM heap.
- **Clear**: `POST /api/v1/debug/clear` releases previous allocations and hints GC.

You can drive these endpoints manually or via `heap-test.js` script:

```bash
# run 20 virtual users for 1 minute, each allocating 5MB every 0.5s
k6 run stress-test/k6/heap-test.js
```

Watch the `JVM Heap` panel in Grafana to see the memory curve and trigger GC events.
- `http_req_duration p(95), p(99)`: tail latency; most users feel this.
- `vus` and `vus_max`: active virtual users and test pressure level.
- `iterations` / `http_reqs`: total work completed.

If p95 climbs sharply during spike stages while CPU and DB connections are saturated in Grafana, you've found a bottleneck worth tuning.
