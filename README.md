# Mini Distributed Cloud Object Store

A resilient, chunk-based distributed cloud object storage system built with Java 21, Spring Boot, and Docker Compose. The system divides arbitrary binary objects into 1 MB chunks and stores them across multiple storage workers with configurable $N$-copy replication (default: 2 copies), proactive data integrity verification, and automatic self-healing.

---

## 1. System Architecture

```
                             +----------------------------------------+
                             |              Client REST               |
                             |      (/api/v1/objects, /sessions)      |
                             +-------------------+--------------------+
                                                 |
                                                 v
+-----------------------------------------------------------------------------------------+
| CONTROLLER NODE (controller.jar - Port 8080)                                            |
|                                                                                         |
|   +---------------------------------------------------------------------------------+   |
|   | Control Plane (Master)                                                          |   |
|   |  • Worker Registry & Heartbeat Watchdog (10s failure detection)                 |   |
|   |  • Persistent Worker Storage Accounting (Tracks total & unassigned bytes)       |   |
|   |  • Placement Engine (N-way distinct UP worker selection, load balancing)        |   |
|   |  • Durable Metadata Store (StoredObject, ChunkRecord, ChunkReplica, Sessions)   |   |
|   |  • Background Reconciler Loop (Under-replication & corruption discovery)        |   |
|   +---------------------------------------------------------------------------------+   |
|                                        |                                                |
|                                        v                                                |
|   +---------------------------------------------------------------------------------+   |
|   | Data Plane (u-Proxy Pool & HealProxy - Java 21 Virtual Threads)                 |   |
|   |  • Streaming Chunker (1 MB boundary partitioning)                               |   |
|   |  • Hard Byte & Chunk Quota Enforcement (Prevents unauthorized allocation)       |   |
|   |  • Parallel Dual-Write Pipeline (X-Reservation-Key, SHA-256 verification)       |   |
|   |  • Mid-Upload Worker Failover (Zero client re-transmits on worker death)        |   |
|   |  • Streaming Download with Verified Replica Fallback                            |   |
|   |  • Headless HealProxy (Re-replicates under-replicated or corrupted chunks)       |   |
|   +------------------------------------+--------------------------------------------+   |
+----------------------------------------|------------------------------------------------+
                                         | HTTP REST
                     +-------------------+-------------------+
                     |                   |                   |
                     v                   v                   v
             +---------------+   +---------------+   +---------------+
             | Storage Worker|   | Storage Worker|   | Storage Worker|  ... (4 Workers)
             |   (worker-1)  |   |   (worker-2)  |   |   (worker-3)  |
             +---------------+   +---------------+   +---------------+
```

---

## 2. Multi-Module Repository Layout

```text
CloudDataStore/
├── pom.xml                     <-- Parent multi-module aggregator POM
├── docker-compose.yml          <-- Deploys 1 Controller + 4 Workers
├── Dockerfile.controller       <-- Multi-stage build producing controller.jar
├── Dockerfile.worker           <-- Multi-stage build producing worker.jar
├── cloudstore-common/          <-- Shared DTOs, constants, and ChunkHashUtils
│   └── src/main/java/org/cloudstore/common/
├── cloudstore-worker/          <-- Storage Worker node backend (worker.jar)
│   └── src/main/java/org/cloudstore/worker/
└── cloudstore-controller/      <-- Master Control Plane + u-Proxy Data Plane (controller.jar)
    └── src/main/java/org/cloudstore/controller/
        ├── api/                <-- Public Client REST APIs (/api/v1/objects)
        ├── master/             <-- Registry, Placement, Storage Accounting, Reconciler
        ├── model/              <-- JPA Entities (StoredObject, ChunkRecord, ChunkReplica, WorkerNode)
        ├── proxy/              <-- u-Proxy streaming engine, Bounded Pipeline, HealProxy
        └── repository/         <-- Spring Data JPA repositories
```

---

## 3. Getting Started & Deployment

### Prerequisites
* Java 21+
* Maven 3.9+
* Docker & Docker Compose

### Option A: Local Build & Package
```bash
# Clean and package both standalone binaries (worker.jar and controller.jar)
mvn clean package -DskipTests

# Binaries generated:
# - cloudstore-worker/target/worker.jar
# - cloudstore-controller/target/controller.jar
```

### Option B: Run with Docker Compose (Recommended)
```bash
# Starts 1 Controller (port 8080) and 4 Storage Workers (ports 8081-8084)
docker compose up --build -d

# Check cluster status and confirm all 4 workers are UP:
curl http://localhost:8080/api/v1/admin/cluster
```

---

## 4. Client REST API Reference

Structured payloads use JSON. Binary streams use `application/octet-stream` or the original media content type.

| Method | Endpoint | Purpose | Success Code |
|---|---|---|---|
| **POST** | `/api/v1/objects` | 1-Step Upload (Multipart form-data: `file`, optional `metadata` JSON) | `201 Created` |
| **POST** | `/api/v1/objects/sessions` | Step 1: Resumable Upload Initiation (pre-flight quota check) | `201 Created` |
| **PUT** | `/api/v1/objects/sessions/{id}/data` | Step 2: Resumable Binary Streaming (`X-Session-Key`) | `201 Created` |
| **GET** | `/api/v1/objects/sessions/{id}` | Query Upload Session Status (resumability check) | `200 OK` |
| **GET** | `/api/v1/objects/{id}` | Download Object (Streaming binary with replica fallback) | `200 OK` |
| **GET** | `/api/v1/objects/{id}/metadata` | Retrieve System & Custom Metadata | `200 OK` |
| **PATCH** | `/api/v1/objects/{id}/metadata` | Update/Merge Custom Metadata | `200 OK` |
| **DELETE** | `/api/v1/objects/{id}` | Delete Object (Immediate quota refund + worker chunk purge) | `204 No Content` |
| **GET** | `/api/v1/objects` | List Stored Objects | `200 OK` |
| **GET** | `/api/v1/admin/cluster` | Admin Cluster Diagnostics (worker health & capacity) | `200 OK` |
| **GET** | `/api/v1/admin/objects/{id}/topology` | Chunk Topology Map (replica locations and health) | `200 OK` |

---

## 5. Evaluation Demonstration Runbook

The system satisfies all 6 required evaluation demonstrations:

### Demo 1: Object Storage & Retrieval
```bash
# 1. Upload a file
curl -X POST -F "file=@sample.pdf" http://localhost:8080/api/v1/objects

# 2. Download the file
curl -o retrieved.pdf http://localhost:8080/api/v1/objects/<OBJECT_ID>

# 3. Verify identical checksum
sha256sum sample.pdf retrieved.pdf
```

### Demo 2 & 3: Chunking, Distribution & Replication
```bash
# 1. Upload a file larger than 1 MB (e.g. 5 MB)
curl -X POST -F "file=@large_video.mp4" http://localhost:8080/api/v1/objects

# 2. View chunk distribution and replica locations across workers
curl http://localhost:8080/api/v1/admin/objects/<OBJECT_ID>/topology
```
*Output demonstrates that the file is partitioned into 1 MB chunks and each chunk has exactly 2 copies distributed across distinct workers.*

### Demo 4: Metadata Management
```bash
# 1. Fetch metadata without downloading chunk data
curl http://localhost:8080/api/v1/objects/<OBJECT_ID>/metadata

# 2. Modify/add custom metadata without re-uploading bytes
curl -X PATCH -H "Content-Type: application/json" \
  -d '{"project": "CloudStore", "classification": "confidential"}' \
  http://localhost:8080/api/v1/objects/<OBJECT_ID>/metadata
```

### Demo 5: Worker Failure & Self-Healing
```bash
# 1. Stop worker-2
docker stop worker2

# 2. Verify controller marks worker-2 DOWN within 10 seconds:
curl http://localhost:8080/api/v1/admin/cluster

# 3. Download the object — succeeds immediately by falling back to the remaining replica!
curl -o file.bin http://localhost:8080/api/v1/objects/<OBJECT_ID>

# 4. Observe self-healing: Reconciler spawns HealProxy to copy the chunk to worker-3 or worker-4:
curl http://localhost:8080/api/v1/admin/objects/<OBJECT_ID>/topology

# 5. Restart worker-2:
docker start worker2
```

### Demo 6: Data Integrity & Corruption Handling
```bash
# 1. Intentionally corrupt a chunk file on worker-1
docker exec -it worker1 sh -c 'echo "CORRUPTED_DATA" > /data/worker/chunks/<CHUNK_FILE>'

# 2. Download object — Controller detects hash mismatch, flags replica CORRUPT,
#    and returns pristine bytes from replica 2!
curl -o file.bin http://localhost:8080/api/v1/objects/<OBJECT_ID>

# 3. HealProxy automatically re-replicates healthy bytes to maintain 2 copies:
curl http://localhost:8080/api/v1/admin/objects/<OBJECT_ID>/topology
```
