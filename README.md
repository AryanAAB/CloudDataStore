# CloudDataStore

CloudDataStore is a Java Spring Boot application that acts as a storage worker in a distributed, chunk-based file system. It is responsible for accepting reservations, validating uploaded chunk data, persisting metadata, and later verifying stored chunk integrity.

This repository is intentionally focused on the worker-side backend logic rather than any user interface. In practice, the application handles:

- reserving a chunk for upload
- validating authorization via reservation keys
- checking file size and SHA-256 hashes
- writing chunk data to local storage
- storing metadata in an embedded database
- renewing reservation leases during long uploads
- scanning for silent corruption and reporting it upstream

## Why this project exists

The system is designed around a distributed storage model in which a master node decides which worker should host a file chunk. The worker then receives a reservation, accepts the chunk body only if it matches expected metadata, and stores the result safely.

This design provides:

- lease-based ownership for file chunks
- protection against stale or duplicate uploads
- verification of uploaded content before persistence
- consistent tracking of chunk metadata
- proactive integrity scanning for data corruption

## Tech stack

- Java 21
- Spring Boot 3.x / 4.x style application structure
- Spring Web MVC
- Spring Data JPA
- H2 embedded database
- Maven
- JUnit 5 + Spring Boot test support

## Repository layout

```text
CloudDataStore/
├── pom.xml
├── README.md
├── .gitignore
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/
│   │   │       └── cloudstore/
│   │   │           ├── CloudDataStoreApplication.java
│   │   │           └── worker/
│   │   │               ├── api/
│   │   │               ├── data/
│   │   │               ├── external/
│   │   │               ├── heartbeat/
│   │   │               ├── integrity/
│   │   │               ├── reservation/
│   │   │               ├── upload/
│   │   │               └── validation/
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/
│           └── org/
│               └── cloudstore/
├── data/
│   └── worker/
│       ├── chunks/
│       └── metadata/
└── target/
```

## Core responsibilities

The application is organized around the chunk lifecycle:

1. Reservation of a chunk
2. Upload of chunk bytes
3. Integrity verification and checksum validation
4. Storage of metadata and files
5. Lease renewal during long operations
6. Background integrity scans
7. Cleanup of expired reservations

### Main application components

- `CloudDataStoreApplication` – starts the Spring application and enables scheduling
- `WorkerReservationController` – exposes reservation endpoints for the master node
- `WorkerReservation` – manages reservation creation, renewal, and conflict handling
- `WorkerChunkUploadController` – accepts upload requests
- `WorkerChunkUpload` – validates and processes chunk bodies
- `WorkerChunkFileStorage` – writes verified file chunks and persists metadata
- `WorkerReservationLeaseHeartbeat` – keeps reservations alive during upload
- `WorkerReservationCleaner` – removes expired reservations
- `WorkerChunkIntegrityScanner` – checks stored chunks for corruption
- `MasterChunkCorruptionReporter` – reports corruption back to the master service

## Configuration

The default runtime settings are defined in `src/main/resources/application.properties`.

```properties
spring.datasource.url=${DB_URL:jdbc:h2:file:./data/worker/metadata;DB_CLOSE_ON_EXIT=FALSE}
spring.datasource.username=${DB_USERNAME:sa}
spring.datasource.password=${DB_PASSWORD:}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.open-in-view=false
worker.storage.root-directory=${WORKER_STORAGE_DIRECTORY:./data/worker/chunks}
worker.integrity.buffer-size-bytes=${WORKER_INTEGRITY_BUFFER_SIZE_BYTES:8192}
worker.reservation.heartbeat.interval-seconds=${RESERVATION_HEARTBEAT_INTERVAL_SECONDS:10}
worker.heartbeat.scheduler-pool-size=${WORKER_HEARTBEAT_POOL_SIZE:4}
worker.heartbeat.thread-name-prefix=${WORKER_HEARTBEAT_THREAD_NAME_PREFIX:worker-heartbeat-}
worker.reservation.key-renewal-policy=${RESERVATION_KEY_RENEWAL_POLICY:after-expiration}
worker.reservation.cleaner.interval-seconds=${RESERVATION_CLEANER_INTERVAL_SECONDS:10}
worker.reservation.ttl-seconds=${RESERVATION_TTL_SECONDS:60}
worker.validation.max-identifier-length=${WORKER_MAX_IDENTIFIER_LENGTH:255}
worker.validation.max-reservation-key-length=${WORKER_MAX_RESERVATION_KEY_LENGTH:128}
worker.integrity.scan.interval-seconds=${WORKER_INTEGRITY_SCAN_INTERVAL_SECONDS:300}
worker.integrity.scan.batch-size=${WORKER_INTEGRITY_SCAN_BATCH_SIZE:50}
worker.master.worker-id=${WORKER_ID:worker-1}
worker.master.corruption-report-url=${MASTER_CORRUPTION_REPORT_URL:http://master:8080/api/v1/workers/{workerId}/chunks/corruption-reports}
```

### Key configuration entries

- `spring.datasource.url` – H2 metadata database location
- `worker.storage.root-directory` – directory where chunks are stored
- `worker.reservation.ttl-seconds` – expiration time for a reservation
- `worker.reservation.heartbeat.interval-seconds` – renewal frequency during upload
- `worker.integrity.scan.interval-seconds` – how often integrity checks run
- `worker.master.corruption-report-url` – endpoint to notify the master when corruption is detected

## Getting started

### Prerequisites

- Java 21+
- Maven 3.9+
- Optional: Git

### Build

```bash
mvn clean package
```

### Run the application

```bash
mvn spring-boot:run
```

### Custom environment variables

```bash
export DB_URL="jdbc:h2:file:./data/worker/metadata;DB_CLOSE_ON_EXIT=FALSE"
export WORKER_STORAGE_DIRECTORY="./data/worker/chunks"
export RESERVATION_TTL_SECONDS="60"
export MASTER_CORRUPTION_REPORT_URL="http://master:8080/api/v1/workers/{workerId}/chunks/corruption-reports"

mvn spring-boot:run
```

## API contract

The worker exposes chunk-related REST endpoints under the `/worker/files` base path.

### Reserve a chunk

```http
PUT /worker/files/{fileId}/chunks/{chunkId}/reservation
```

Headers:

```http
X-Reservation-Key: <reservation-key>
```

This request creates or renews a reservation for a specific chunk.

### Upload a chunk

```http
PUT /worker/files/{fileId}/chunks/{chunkId}/upload
Content-Type: application/octet-stream
```

Headers:

```http
X-Reservation-Key: <reservation-key>
X-Chunk-Size: <expected-size-in-bytes>
X-Chunk-SHA256: <hex-digest>
```

Body:

- Raw bytes of the chunk

During upload, the worker validates:

- file id and chunk id format
- reservation key validity
- expected size is non-negative
- checksum is a valid SHA-256 hex string
- reservation is active and not expired
- total uploaded size stays within the expected limit
- final digest matches the expected value

If any validation fails, the chunk is rejected.

## Integrity and safety model

A core design goal of CloudDataStore is to detect silent corruption.

Before accepting upload data, the worker compares the uploaded bytes against expected metadata:

- expected size in bytes
- expected SHA-256 checksum

While streaming the file, it computes the digest and writes to a temporary file. If the size exceeds the expected limit or the checksum mismatches, the upload is aborted.

Later, the integrity scanner checks stored chunk files again against the metadata in the database. If files are missing, modified, or corrupted, the worker reports the issue to the master service.

## Persistence model

CloudDataStore uses JPA entities and H2 storage. It stores:

- active reservations for each file/chunk pair
- verified chunk metadata
- file locations on disk

This allows the worker to recover state after restart without requiring a centralized metadata service.

## Testing

The repo includes tests under `src/test/java` covering:

- reservation lifecycle behavior
- validation and rejection scenarios
- upload success and failure paths
- checksum verification
- corruption reporting
- metadata persistence
- cleaner task behavior

## Operational notes

CloudDataStore focuses on correctness and resilience more than end-user features. It is meant to be a reliable worker node in a storage system where chunk-level safety matters.

Typical workflow:

1. Master reserves a chunk
2. Worker validates and creates an expiring reservation
3. Master uploads the raw bytes
4. Worker verifies size and hash
5. Worker stores data and metadata
6. Background jobs scan for corruption and report issues upstream

## License

This repository does not currently declare an explicit license in the metadata visible here. If you plan to distribute or reuse this project publicly, you should confirm the intended licensing terms before publication.

## Summary

CloudDataStore is a compact but engineering-heavy Java worker application for distributed chunk storage. Its responsibilities are focused and critical:

- grant temporary chunk ownership
- accept verified upload data
- persist chunk metadata and files
- maintain lease health during transfers
- detect integrity problems later
- notify the master node when corruption is found

It is a strong example of a storage worker designed around safety, correctness, lease semantics, and data integrity.

## Important files to explore

- `src/main/java/org/cloudstore/CloudDataStoreApplication.java`
- `src/main/java/org/cloudstore/worker/reservation/WorkerReservation.java`
- `src/main/java/org/cloudstore/worker/upload/WorkerChunkUpload.java`
- `src/main/java/org/cloudstore/worker/upload/WorkerChunkFileStorage.java`
- `src/main/java/org/cloudstore/worker/integrity/ChunkContentVerifier.java`
- `src/main/java/org/cloudstore/worker/integrity/WorkerChunkIntegrityScanner.java`
- `src/main/resources/application.properties`

These files form the core of the project and show how the worker manages reservations, uploads, storage, and integrity validation.
