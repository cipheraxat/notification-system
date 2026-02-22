# Monitoring Folder Guide

This folder contains all monitoring assets for Prometheus + Grafana.

## Folder Layout

```text
monitoring/
├── README.md
├── prometheus/
│   └── prometheus.yml
└── grafana/
    ├── dashboards/
    │   ├── notification-system-overview.json
    │   └── README.md
    └── provisioning/
        ├── datasources/datasource.yml
        └── dashboards/dashboard-provider.yml
```

## What each file does

### `prometheus/prometheus.yml`

- Defines scrape intervals.
- Configures scrape jobs for:
  - Spring Boot app (`/actuator/prometheus`)
  - PostgreSQL exporter
  - Redis exporter
  - Kafka exporter
  - Prometheus self-metrics

### `grafana/provisioning/datasources/datasource.yml`

- Auto-creates Prometheus datasource on Grafana startup.
- Sets it as default so dashboard imports work immediately.

### `grafana/provisioning/dashboards/dashboard-provider.yml`

- Tells Grafana to auto-load dashboard JSON files from `/var/lib/grafana/dashboards`.
- Keeps file-based provisioning as source of truth.

### `grafana/dashboards/notification-system-overview.json`

- Main dashboard definition used by Grafana.
- JSON does not support comments; see `grafana/dashboards/README.md` for panel-level explanations.

## Runtime Notes

- Prometheus target for app metrics uses `host.docker.internal:8080`.
  - Works on macOS/Windows Docker Desktop.
  - On Linux, replace with host IP or run app in the same Docker network.

## Useful checks

```bash
# Check Grafana health
curl -s http://localhost:3000/api/health

# Check Prometheus targets
curl -s http://localhost:9090/api/v1/targets

# Check app metrics endpoint
curl -s http://localhost:8080/actuator/prometheus | head
```
