# Walkthrough: Mini Distributed Cloud Object Store (Controller & Cluster Engine)

We have designed, built, tested, and containerized the **Controller Node** (`controller.jar`) for the Mini Distributed Cloud Object Store, transforming the repository into a production-grade multi-module distributed system alongside the existing storage worker (`worker.jar`).

---

## 1. System Architecture Overview

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

## 2. Milestone Deliverables

### Milestone 1: Multi-Module Setup & Core Data Plane
* **Multi-Module Maven Structure:**
  * `cloudstore-common`: Shared DTOs (`MasterWorkerRegistration`, `MasterWorkerHeartbeat`, `MasterChunkCorruptionReport`, `WorkerChunkInventoryResponse`), constants (`WorkerApiConstants`), and hashing utilities (`ChunkHashUtils`).
  * `cloudstore-worker`: Existing storage worker backend; compiles to `worker.jar`.
  * `cloudstore-controller`: Master control plane and u-Proxy data plane; compiles to `controller.jar`.
* **Master Control Plane:**
  * `WorkerRegistryService`: Ingests registration and periodic heartbeats; 10s timeout watchdog evicts inactive workers.
  * `StorageAccountingService`: Persistent tracking of `unassignedCapacityBytes` per worker with atomic reservations and rollbacks.
  * `PlacementEngine`: Configurable $N$-copy replication (default: $N=2$); selects distinct, live (`UP`) workers based on capacity.
  * `MasterInternalController`: Endpoints for worker registration, heartbeats, and corruption reports.
* **u-Proxy Data Plane (Java 21 Virtual Threads):**
  * `ChunkStreamPipeline`: Streams data in 1 MB chunks with on-the-fly SHA-256; strictly enforces hard byte and chunk budgets (`QuotaExceededException` $\implies$ HTTP 413).
  * `WorkerChunkClient`: HTTP client interacting with worker reservation, upload, download, and delete endpoints.
  * `UProxyService`:
    * Step 1 (`initiateSession`): Pre-flight quota and worker check.
    * Step 2 (`processStreamUpload`): Pipelined dual-write with mid-stream failover to replacement workers.
    * Streaming Download (`streamDownload`): Streams verified chunks; falls back to replica 2 if primary is corrupted or DOWN.
* **Public REST API:**
  * `POST /api/v1/objects/sessions` (2-step upload initiate)
  * `PUT /api/v1/objects/sessions/{id}/data` (2-step binary stream)
  * `GET /api/v1/objects/sessions/{id}` (resumability check)
  * `POST /api/v1/objects` (1-step backward-compatible multipart facade)
  * `GET /api/v1/objects/{id}` (streaming download)
  * `GET /api/v1/objects` (list objects)
  * `GET /api/v1/admin/cluster` (cluster diagnostics)

### Milestone 2: Object Lifecycle & Metadata Operations
* **Metadata Endpoints:**
  * `GET /api/v1/objects/{id}/metadata`: Returns system and custom metadata without touching worker bytes.
  * `PATCH /api/v1/objects/{id}/metadata`: Merges custom key-values in-place with zero worker interaction.
* **Resilient Distributed Deletion:**
  * `DELETE /api/v1/objects/{id}`:
    1. Synchronous phase: Soft-deletes object record and **immediately refunds storage quota** back to workers. Responds with `204 No Content`.
    2. Asynchronous phase: Purges chunks across workers in parallel.
    3. Offline Worker Resilience: If a worker is `DOWN`, stores a persistent tombstone (`PendingDeletionRepository`). When the worker reconnects (`WorkerReconnectedEvent`), stale chunks are purged automatically.

### Milestone 3: Self-Healing, Reconciler & Docker Deployment
* **Self-Healing Engine:**
  * `HealProxy`: Reads healthy chunks from live replicas, finds new eligible target workers, reserves storage, and restores replication factor to $N$.
  * `ReconcilerService`: Periodic watchdog (every 5s) discovering under-replicated chunks and dispatching `HealProxy` tasks.
  * `GET /api/v1/admin/objects/{id}/topology`: Returns a real-time map of chunk replica locations and worker statuses.
* **Deployment & Containerization:**
  * `Dockerfile.controller` & `Dockerfile.worker`: Multi-stage Alpine-based container images.
  * `docker-compose.yml`: Deploys 1 Controller (port 8080) and 4 Storage Workers (ports 8081–8084) on a shared bridge network with dedicated persistent storage volumes.
* **Documentation & Git:**
  * Updated comprehensive [README.md](file:///c:/Abhirath/iiitb/Course/Sem7/CloudComputing/Project/CloudDataStore/README.md) with architecture, API docs, and step-by-step evaluation demonstration instructions.
  * All markdown plans ignored via `.gitignore` and kept local.
  * Pushed to GitHub: [`feature/controller-milestone-1`](https://github.com/AryanAAB/CloudDataStore/tree/feature/controller-milestone-1).

---

## 3. Verification & Automated Test Results

The codebase includes comprehensive unit and integration test suites:

| Test Suite | Components Tested | Scenarios Covered |
|---|---|---|
| `StorageAccountingServiceTest` | `StorageAccountingService` | Atomic capacity reservation, capacity exhaustion rejection, capacity release / refund |
| `PlacementEngineTest` | `PlacementEngine` | $N$-way distinct worker selection, capacity sorting, replacement worker selection with exclusions |
| `ChunkStreamPipelineTest` | `ChunkStreamPipeline` | 1 MB chunk partitioning, SHA-256 calculation, byte quota overflow (HTTP 413), chunk limit overflow |
| `UProxyServiceTest` | `UProxyService` | 2-step upload handshake, dual-worker write dispatch, download replica fallback upon silent corruption, metadata PATCH merge, deletion |
| `ObjectDeletionServiceTest` | `ObjectDeletionService` | 404 handling, synchronous quota refund across workers, offline tombstone persistence for DOWN workers |
| `HealProxyTest` | `HealProxy` | Re-replication from live source to target worker, handling missing/corrupted source copies |
| `ReconcilerServiceTest` | `ReconcilerService` | Detection of under-replicated chunks, skipping fully-replicated chunks, spawning heal tasks |

---

## 4. How to Run the Evaluation Demonstration

```bash
# 1. Start the cluster
docker compose up --build -d

# 2. Verify all 4 workers are UP
curl http://localhost:8080/api/v1/admin/cluster

# 3. Upload a file larger than 1 MB (e.g. 5 MB)
curl -X POST -F "file=@large_video.mp4" http://localhost:8080/api/v1/objects

# 4. View chunk topology & replica placement (shows 2 copies per chunk)
curl http://localhost:8080/api/v1/admin/objects/<OBJECT_ID>/topology

# 5. Stop worker2 to simulate failure
docker stop worker2

# 6. Retrieve object — succeeds immediately from remaining replica!
curl -o retrieved.mp4 http://localhost:8080/api/v1/objects/<OBJECT_ID>

# 7. Observe Reconciler self-healing: copy re-replicated to worker3 or worker4
curl http://localhost:8080/api/v1/admin/objects/<OBJECT_ID>/topology

# 8. Test corruption handling: intentionally corrupt worker1 chunk file
docker exec -it worker1 sh -c 'echo "CORRUPT" > /data/worker/chunks/<CHUNK_FILE>'
curl -o retrieved.mp4 http://localhost:8080/api/v1/objects/<OBJECT_ID> # Succeeds via fallback!
```
