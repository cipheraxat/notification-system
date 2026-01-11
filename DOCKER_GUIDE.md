# Docker Guide - Notification System

A comprehensive guide to managing Docker containers for the Notification System project, covering both **Terminal commands** and **Docker Desktop GUI**.

---

## Table of Contents

1. [Overview](#overview)
2. [Quick Start](#quick-start)
3. [Terminal Commands](#terminal-commands)
   - [Basic Docker Commands](#basic-docker-commands)
   - [Docker Compose Commands](#docker-compose-commands)
   - [Container Management](#container-management)
   - [Viewing Logs](#viewing-logs)
   - [Executing Commands Inside Containers](#executing-commands-inside-containers)
4. [Container-Specific Commands](#container-specific-commands)
   - [PostgreSQL](#1-postgresql-notification-postgres)
   - [Redis](#2-redis-notification-redis)
   - [Zookeeper](#3-zookeeper-notification-zookeeper)
   - [Kafka](#4-kafka-notification-kafka)
   - [Kafka UI](#5-kafka-ui-notification-kafka-ui)
5. [Docker Desktop GUI](#docker-desktop-gui)
   - [Opening Docker Desktop](#opening-docker-desktop)
   - [Containers View](#containers-view)
   - [Images View](#images-view)
   - [Volumes View](#volumes-view)
   - [Container-Specific GUI Operations](#container-specific-gui-operations)
6. [Troubleshooting](#troubleshooting)
7. [Cleanup Commands](#cleanup-commands)

---

## Overview

This project uses **7 Docker containers**:

| Container | Image | Port | Purpose |
|-----------|-------|------|---------|
| `notification-postgres` | `postgres:15` | 5432 | Main database |
| `notification-redis` | `redis:7-alpine` | 6379 | Caching & rate limiting |
| `notification-zookeeper` | `confluentinc/cp-zookeeper:7.4.0` | 2181 | Kafka coordination |
| `notification-kafka-1` | `confluentinc/cp-kafka:7.4.0` | 9092 | Message queue (Broker 1) |
| `notification-kafka-2` | `confluentinc/cp-kafka:7.4.0` | 9093 | Message queue (Broker 2) |
| `notification-kafka-3` | `confluentinc/cp-kafka:7.4.0` | 9094 | Message queue (Broker 3) |
| `notification-kafka-ui` | `provectuslabs/kafka-ui:latest` | 8090 | Kafka web interface |

---

## Quick Start

```bash
# Navigate to project directory
cd /Users/Admin/Developer/Projects/notification-system

# Start all containers
docker-compose up -d

# Verify all containers are running
docker ps

# Stop all containers
docker-compose down
```

---

## Terminal Commands

### Basic Docker Commands

#### Check Docker Installation
```bash
# Check Docker version
docker --version

# Check Docker Compose version
docker-compose --version

# Check if Docker daemon is running
docker info
```

#### List Containers
```bash
# List running containers
docker ps

# List ALL containers (including stopped)
docker ps -a

# List containers with specific format
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

**Example Output:**
```
NAMES                    STATUS          PORTS
notification-kafka-ui    Up 2 hours      0.0.0.0:8090->8080/tcp
notification-kafka-1     Up 2 hours      0.0.0.0:9092->9092/tcp
notification-kafka-2     Up 2 hours      0.0.0.0:9093->9093/tcp
notification-kafka-3     Up 2 hours      0.0.0.0:9094->9094/tcp
notification-zookeeper   Up 2 hours      0.0.0.0:2181->2181/tcp
notification-redis       Up 2 hours      0.0.0.0:6379->6379/tcp
notification-postgres    Up 2 hours      0.0.0.0:5432->5432/tcp
```

#### List Images
```bash
# List all images
docker images

# List images with size
docker images --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"
```

#### List Volumes
```bash
# List all volumes
docker volume ls

# Inspect a volume
docker volume inspect notification-system_postgres_data
```

---

### Docker Compose Commands

All these commands should be run from the project directory where `docker-compose.yml` is located.

#### Starting Containers
```bash
# Start all services in background (detached mode)
docker-compose up -d

# Start all services with logs visible
docker-compose up

# Start specific service only
docker-compose up -d postgres

# Start and rebuild images
docker-compose up -d --build

# Start and force recreate containers
docker-compose up -d --force-recreate
```

#### Stopping Containers
```bash
# Stop all services (keeps data in volumes)
docker-compose down

# Stop and remove volumes (⚠️ DELETES ALL DATA)
docker-compose down -v

# Stop and remove images too
docker-compose down --rmi all

# Stop specific service
docker-compose stop postgres
```

#### Restarting Containers
```bash
# Restart all services
docker-compose restart

# Restart specific service
docker-compose restart postgres

# Restart with zero downtime (recreate)
docker-compose up -d --force-recreate postgres
```

#### Viewing Status
```bash
# Show status of all services
docker-compose ps

# Show resource usage
docker-compose top
```

---

### Container Management

#### Start/Stop Individual Containers
```bash
# Stop a container
docker stop notification-postgres

# Start a container
docker start notification-postgres

# Restart a container
docker restart notification-postgres

# Pause a container (freeze processes)
docker pause notification-postgres

# Unpause a container
docker unpause notification-postgres
```

#### Remove Containers
```bash
# Remove a stopped container
docker rm notification-postgres

# Force remove a running container
docker rm -f notification-postgres

# Remove all stopped containers
docker container prune
```

#### Inspect Containers
```bash
# Get detailed container info
docker inspect notification-postgres

# Get container IP address
docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' notification-postgres

# Get container health status
docker inspect --format='{{.State.Health.Status}}' notification-postgres
```

---

### Viewing Logs

#### Basic Log Commands
```bash
# View logs for a container
docker logs notification-postgres

# Follow logs in real-time (like tail -f)
docker logs -f notification-postgres

# Show last N lines
docker logs --tail 50 notification-postgres

# Show logs with timestamps
docker logs -t notification-postgres

# Show logs since a specific time
docker logs --since 1h notification-postgres
docker logs --since "2026-01-04T10:00:00" notification-postgres

# Combine options: last 100 lines, follow, with timestamps
docker logs -f --tail 100 -t notification-postgres
```

#### Docker Compose Logs
```bash
# View logs for all services
docker-compose logs

# Follow logs for all services
docker-compose logs -f

# View logs for specific service
docker-compose logs postgres

# Follow logs for specific service
docker-compose logs -f kafka
```

---

### Executing Commands Inside Containers

#### Basic Syntax
```bash
docker exec [OPTIONS] CONTAINER COMMAND [ARG...]
```

| Option | Description |
|--------|-------------|
| `-i` | Interactive (keep STDIN open) |
| `-t` | Allocate a pseudo-TTY (terminal) |
| `-it` | Combine both (interactive terminal) |
| `-u` | Run as specific user |
| `-w` | Set working directory |

#### Examples
```bash
# Run a single command
docker exec notification-postgres ls -la

# Open interactive shell
docker exec -it notification-postgres bash

# Run command as specific user
docker exec -u postgres notification-postgres whoami

# Run command in specific directory
docker exec -w /var/lib/postgresql notification-postgres pwd
```

---

## Container-Specific Commands

---

### 1. PostgreSQL (`notification-postgres`)

#### Connection Details
| Property | Value |
|----------|-------|
| **Host** | localhost |
| **Port** | 5432 |
| **Database** | notification_db |
| **Username** | postgres |
| **Password** | postgres |

#### Connect to Database
```bash
# Connect to psql (PostgreSQL CLI)
docker exec -it notification-postgres psql -U postgres -d notification_db
```

**Command Breakdown:**
| Part | Meaning |
|------|---------|
| `docker exec` | Run command in container |
| `-it` | Interactive terminal |
| `notification-postgres` | Container name |
| `psql` | PostgreSQL command-line tool |
| `-U postgres` | Connect as user "postgres" |
| `-d notification_db` | Connect to database "notification_db" |

#### Useful psql Commands (Inside psql)
```sql
-- List all databases
\l

-- List all tables
\dt

-- Describe a table structure
\d notifications
\d users
\d notification_templates

-- View table data
SELECT * FROM users;
SELECT * FROM notifications ORDER BY created_at DESC LIMIT 10;
SELECT * FROM notification_templates;

-- Count records
SELECT COUNT(*) FROM notifications;

-- View column names only
SELECT column_name, data_type FROM information_schema.columns 
WHERE table_name = 'notifications';

-- Quit psql
\q
```

#### Run SQL Without Entering psql
```bash
# Run a single query
docker exec -it notification-postgres psql -U postgres -d notification_db \
  -c "SELECT id, email, phone FROM users;"

# Run multiple queries
docker exec -it notification-postgres psql -U postgres -d notification_db \
  -c "SELECT COUNT(*) as user_count FROM users;" \
  -c "SELECT COUNT(*) as notification_count FROM notifications;"

# Export query results to file
docker exec -it notification-postgres psql -U postgres -d notification_db \
  -c "SELECT * FROM notifications;" > notifications_export.txt
```

#### Database Backup & Restore
```bash
# Backup database to local file
docker exec notification-postgres pg_dump -U postgres notification_db > backup.sql

# Restore database from file
cat backup.sql | docker exec -i notification-postgres psql -U postgres -d notification_db

# Backup with compression
docker exec notification-postgres pg_dump -U postgres notification_db | gzip > backup.sql.gz
```

---

### 2. Redis (`notification-redis`)

#### Connection Details
| Property | Value |
|----------|-------|
| **Host** | localhost |
| **Port** | 6379 |
| **Password** | (none) |

#### Connect to Redis CLI
```bash
docker exec -it notification-redis redis-cli
```

#### Useful Redis Commands (Inside redis-cli)
```bash
# Test connection
PING
# Returns: PONG

# List all keys
KEYS *

# Get number of keys
DBSIZE

# Get a specific key value
GET rate_limit:550e8400-e29b-41d4-a716-446655440001

# Check if key exists
EXISTS keyname

# Get key type
TYPE keyname

# Get time-to-live (seconds until expiry)
TTL keyname

# Get server info
INFO

# Get memory usage
INFO memory

# Get statistics
INFO stats

# Monitor all commands in real-time (Ctrl+C to stop)
MONITOR

# Delete a specific key
DEL keyname

# Delete all keys (⚠️ DANGEROUS)
FLUSHALL

# Exit redis-cli
exit
```

#### Run Redis Commands Without Entering CLI
```bash
# Ping Redis
docker exec -it notification-redis redis-cli PING

# Get all keys
docker exec -it notification-redis redis-cli KEYS '*'

# Get database size
docker exec -it notification-redis redis-cli DBSIZE

# Get specific key
docker exec -it notification-redis redis-cli GET "rate_limit:550e8400-e29b-41d4-a716-446655440001"
```

---

### 3. Zookeeper (`notification-zookeeper`)

#### Connection Details
| Property | Value |
|----------|-------|
| **Host** | localhost |
| **Port** | 2181 |

> **Note:** You typically don't interact with Zookeeper directly. Kafka manages it.

#### Check Zookeeper Status
```bash
# Check if Zookeeper is running
docker exec -it notification-zookeeper zkServer.sh status
```

**Expected Output:**
```
Mode: standalone
```

#### Connect to Zookeeper CLI
```bash
docker exec -it notification-zookeeper zkCli.sh
```

#### Useful Zookeeper Commands (Inside zkCli.sh)
```bash
# List root nodes
ls /

# List Kafka broker IDs
ls /brokers/ids

# Get broker info
get /brokers/ids/1

# List topics
ls /brokers/topics

# Quit
quit
```

---

### 4. Kafka Brokers (`notification-kafka-1`, `notification-kafka-2`, `notification-kafka-3`)

#### Connection Details
| Property | Value |
|----------|-------|
| **Hosts** | localhost:9092, localhost:9093, localhost:9094 |
| **Ports** | 9092 (Broker 1), 9093 (Broker 2), 9094 (Broker 3) |
| **Internal Ports** | kafka-1:29092, kafka-2:29093, kafka-3:29094 (for containers) |

#### Topic Management
```bash
# List all topics
docker exec -it notification-kafka-1 kafka-topics \
  --bootstrap-server localhost:9092,localhost:9093,localhost:9094 --list

# Create a topic
docker exec -it notification-kafka-1 kafka-topics \
  --bootstrap-server localhost:9092,localhost:9093,localhost:9094 \
  --create --topic my-topic \
  --partitions 3 --replication-factor 3

# Describe a topic
docker exec -it notification-kafka-1 kafka-topics \
  --bootstrap-server localhost:9092,localhost:9093,localhost:9094 \
  --describe --topic notifications

# Delete a topic
docker exec -it notification-kafka-1 kafka-topics \
  --bootstrap-server localhost:9092,localhost:9093,localhost:9094 \
  --delete --topic my-topic
```

#### Produce Messages (Send)
```bash
# Start producer (type messages, press Enter to send)
docker exec -it notification-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic notifications

# Type your message and press Enter
# Press Ctrl+C to exit
```

#### Consume Messages (Receive)
```bash
# Read all messages from beginning
docker exec -it notification-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic notifications \
  --from-beginning

# Read only new messages (real-time)
docker exec -it notification-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic notifications

# Read with message metadata
docker exec -it notification-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic notifications \
  --from-beginning \
  --property print.key=true \
  --property print.timestamp=true

# Press Ctrl+C to exit
```

#### Consumer Groups
```bash
# List consumer groups
docker exec -it notification-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 --list

# Describe a consumer group
docker exec -it notification-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group notification-group

# Check consumer lag
docker exec -it notification-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group notification-group \
  --members --verbose
```

---

### 5. Kafka UI (`notification-kafka-ui`)

#### Access
Open in browser: **http://localhost:8090**

#### No CLI Needed
Kafka UI is a web-only interface. All interactions happen in the browser.

#### Features Available in UI
- View all topics
- Browse messages in topics
- Create/delete topics
- View consumer groups
- Monitor consumer lag
- View broker configuration

---

## Docker Desktop GUI

### Opening Docker Desktop

1. **macOS:** 
   - Click the Docker icon in the menu bar (top-right)
   - Or open from Applications folder

2. **Keyboard Shortcut:** Press `Cmd + Space`, type "Docker", press Enter

---

### Containers View

Navigate to: **Docker Desktop → Containers**

#### Container List
You'll see all 5 containers:
```
▼ notification-system (compose project)
  ├── notification-postgres     Running    5432:5432
  ├── notification-redis        Running    6379:6379
  ├── notification-zookeeper    Running    2181:2181
  ├── notification-kafka        Running    9092:9092
  └── notification-kafka-ui     Running    8090:8080
```

#### Container Actions (Click on Container Name)

| Tab | Description |
|-----|-------------|
| **Logs** | View real-time container logs |
| **Inspect** | View container configuration (JSON) |
| **Terminal** | Open interactive shell inside container |
| **Files** | Browse container filesystem |
| **Stats** | View CPU, Memory, Network usage |

#### Quick Actions (Icons on Right)

| Icon | Action |
|------|--------|
| ▶️ | Start container |
| ⏸️ | Pause container |
| ⏹️ | Stop container |
| 🔄 | Restart container |
| 🗑️ | Delete container |
| 📋 | Copy container ID |

---

### Images View

Navigate to: **Docker Desktop → Images**

You'll see all downloaded images:
```
postgres                    15          287MB
redis                       7-alpine    41MB
confluentinc/cp-zookeeper   7.4.0       789MB
confluentinc/cp-kafka       7.4.0       789MB
provectuslabs/kafka-ui      latest      412MB
```

#### Image Actions

| Action | Description |
|--------|-------------|
| **Run** | Create a new container from image |
| **Pull** | Download latest version |
| **Push** | Upload to registry (if logged in) |
| **Delete** | Remove image from local storage |

---

### Volumes View

Navigate to: **Docker Desktop → Volumes**

You'll see:
```
notification-system_postgres_data    1.2 GB
notification-system_redis_data       512 KB
```

#### Volume Actions

| Action | Description |
|--------|-------------|
| **Browse** | View files stored in volume |
| **Delete** | Remove volume (⚠️ deletes data) |
| **Clone** | Create a copy of the volume |
| **Export** | Export volume data as tar file |

---

### Container-Specific GUI Operations

#### PostgreSQL via Docker Desktop

1. Click on `notification-postgres`
2. Go to **Terminal** tab
3. You're now inside the container
4. Run:
   ```bash
   psql -U postgres -d notification_db
   ```
5. Execute SQL commands:
   ```sql
   SELECT * FROM users;
   \q
   ```

#### Redis via Docker Desktop

1. Click on `notification-redis`
2. Go to **Terminal** tab
3. Run:
   ```bash
   redis-cli
   ```
4. Execute Redis commands:
   ```bash
   KEYS *
   DBSIZE
   exit
   ```

#### Kafka via Docker Desktop

1. Click on `notification-kafka`
2. Go to **Terminal** tab
3. Run:
   ```bash
   kafka-topics --bootstrap-server localhost:9092 --list
   ```

#### View Logs in Docker Desktop

1. Click on any container
2. Go to **Logs** tab
3. Use the search box to filter logs
4. Toggle "Wrap lines" for better readability
5. Click "Copy" to copy logs to clipboard

#### Monitor Resources in Docker Desktop

1. Click on any container
2. Go to **Stats** tab
3. View real-time:
   - CPU usage (%)
   - Memory usage (MB)
   - Network I/O
   - Disk I/O

---

## Troubleshooting

### Container Won't Start

```bash
# Check container logs for errors
docker logs notification-postgres

# Check if port is already in use
lsof -i :5432

# Kill process using the port
kill -9 <PID>

# Remove and recreate container
docker-compose down
docker-compose up -d
```

### Container Keeps Restarting

```bash
# Check restart count
docker inspect notification-postgres | grep RestartCount

# View recent logs
docker logs --tail 100 notification-postgres

# Check health status
docker inspect --format='{{.State.Health.Status}}' notification-postgres
```

### Cannot Connect to Container

```bash
# Verify container is running
docker ps | grep notification-postgres

# Check container IP
docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' notification-postgres

# Check port mapping
docker port notification-postgres

# Test connection
nc -zv localhost 5432
```

### Out of Disk Space

```bash
# Check Docker disk usage
docker system df

# View detailed usage
docker system df -v

# Clean up unused resources
docker system prune

# Clean up everything including volumes (⚠️ DANGEROUS)
docker system prune -a --volumes
```

### View All Container Resource Usage

```bash
# Real-time resource monitoring
docker stats

# Formatted output
docker stats --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}"
```

---

## Cleanup Commands

### Safe Cleanup (Keeps Data)
```bash
# Stop all containers
docker-compose down

# Remove unused containers
docker container prune

# Remove unused images
docker image prune

# Remove unused networks
docker network prune
```

### Full Cleanup (⚠️ Deletes Data)
```bash
# Stop and remove containers + volumes
docker-compose down -v

# Remove all unused data
docker system prune -a --volumes

# Remove specific volume
docker volume rm notification-system_postgres_data
```

### Reset Everything
```bash
# Nuclear option: Remove all containers, images, volumes
docker-compose down -v
docker system prune -a --volumes -f

# Rebuild from scratch
docker-compose up -d --build
```

---

## Quick Reference Card

### Most Used Commands

| Task | Command |
|------|---------|
| Start all | `docker-compose up -d` |
| Stop all | `docker-compose down` |
| View containers | `docker ps` |
| View logs | `docker logs -f <container>` |
| Enter container | `docker exec -it <container> bash` |
| PostgreSQL CLI | `docker exec -it notification-postgres psql -U postgres -d notification_db` |
| Redis CLI | `docker exec -it notification-redis redis-cli` |
| Kafka topics | `docker exec -it notification-kafka kafka-topics --bootstrap-server localhost:9092 --list` |
| Restart container | `docker restart <container>` |
| Resource usage | `docker stats` |
| Clean up | `docker system prune` |

### Container Names

| Service | Container Name |
|---------|---------------|
| PostgreSQL | `notification-postgres` |
| Redis | `notification-redis` |
| Zookeeper | `notification-zookeeper` |
| Kafka | `notification-kafka` |
| Kafka UI | `notification-kafka-ui` |

### Web Interfaces

| Service | URL |
|---------|-----|
| Kafka UI | http://localhost:8090 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Actuator Health | http://localhost:8080/actuator/health |

---

*Happy Dockering! 🐳*
