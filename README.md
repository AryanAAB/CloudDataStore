# CloudDataStore

CloudDataStore is a Java Spring Boot application that acts as a storage worker in a distributed chunk-based file system. The project is designed to receive, validate, and persist chunk uploads from a master service while enforcing reservation leases, integrity checks, and corruption reporting.

This repository is not a monolithic application with a user interface. Instead, it implements the worker-side backend logic responsible for:

- reserving a chunk for upload
- validating that a request is authorized
- verifying uploaded bytes against size and SHA-256 expectations
- storing chunk files on local disk
- persisting metadata in an embedded database
- renewing reservation leases during long uploads
- scanning stored chunks for corruption
- reporting corruption back to the master service

The application is implemented in Java 21 using Spring Boot 4.1.2 and Spring Data JPA.

## Project purpose

This codebase is built around a distributed storage model where a large file is split into fixed or logical chunks. A master node decides which worker should store a chunk and sends a reservation request. The worker validates the request, accepts the chunk data stream, and verifies that the bytes match the declared size and hash before saving them.

This architecture addresses several key concerns:

- authorization through reservation keys
- protection against stale or duplicated uploads
- corruption detection after the fact
- lease-based ownership of chunk assignments
- safe, transactional metadata handling

## High-level architecture

The application is organized around the worker lifecycle:

1. Reservation of a chunk
2. Upload of chunk bytes
3. Validation and checksum verification
4. Persistent metadata storage
5. Lease renewal during long-running transfer
6. Background integrity scanning and corruption reporting
7. Cleanup of expired reservations

The core components are:

- `CloudDataStoreApplication` – boots the Spring application and enables scheduling
- `WorkerReservationController` – exposes reservation endpoints for the master
- `WorkerReservation` – validates and creates/renews reservations
- `WorkerChunkUploadController` – accepts chunk upload requests
- `WorkerChunkUpload` – verifies upload requests and streams data to local storage
- `WorkerChunkFileStorage` – writes verified chunk files and records metadata
- `WorkerReservationLeaseHeartbeat` – renews reservation leases while a chunk is uploading
- `WorkerReservationCleaner` – deletes expired reservations
- `WorkerChunkIntegrityScanner` – checks previously stored chunks for corruption
- `MasterChunkCorruptionReporter` – reports corruption back to the master

## Technology stack

- Java 21
- Spring Boot 4.1.2
- Spring Web MVC
- Spring Data JPA
- H2 database
- Maven
- SLF4J logging
- JUnit 5 + Spring Boot test tools

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
│   │   │           ├── worker/
│   │   │           │   ├── api/
│   │   │           │   ├── data/
│   │   │           │   ├── external/
│   │   │           │   ├── heartbeat/
│   │   │           │   ├── integrity/
│   │   │           │   ├── reservation/
│   │   │           │   ├── upload/
│   │   │           │   └── validation/
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

The repository includes both application code and test coverage under `src/test/java`.

## Configuration

The default configuration is defined in `src/main/resources/application.properties`:

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

### Key configuration properties

#### Database settings

- `spring.datasource.url` – location of the H2 metadata database
- `spring.datasource.username` and `spring.datasource.password` – DB credentials
- `spring.jpa.hibernate.ddl-auto=update` – automatically updates schema on startup

#### Storage settings

- `worker.storage.root-directory` – directory where chunk files are stored locally
- default: `./data/worker/chunks`

#### Reservation settings

- `worker.reservation.ttl-seconds` – how long a reservation remains valid
- default: `60`
- `worker.reservation.key-renewal-policy` – strategy for handling changed keys
- default: `after-expiration`
- `worker.reservation.heartbeat.interval-seconds` – how often the lease is renewed during uploads
- `worker.reservation.cleaner.interval-seconds` – clean-up interval for expired reservations

#### Validation settings

- `worker.validation.max-identifier-length` – maximum allowed length for file and chunk IDs
- `worker.validation.max-reservation-key-length` – maximum allowed length for reservation keys

#### Integrity settings

- `worker.integrity.buffer-size-bytes` – read/write buffer size during checksum validation
- `worker.integrity.scan.interval-seconds` – how often integrity validation runs
- `worker.integrity.scan.batch-size` – how many chunk metadata rows are checked per batch

#### Master reporting

- `worker.master.worker-id` – identity of this worker to the master
- `worker.master.corruption-report-url` – endpoint used to report corrupt chunks

## Runtime model

The worker service runs as a Spring Boot application and persists:

- active reservations in a database table
- verified chunk metadata in a second database table
- chunk files in a filesystem directory

This gives the worker a local state that can be recovered after restart without requiring a central metadata service.

## API contract

The worker exposes the following REST endpoints.

### Base path

```text
/worker/files
```

### Reservation endpoint

```http
PUT /worker/files/{fileId}/chunks/{chunkId}/reservation
```

Required header:

```http
X-Reservation-Key: <reservation-key>
```

This endpoint is used by the master to create or renew a lease for a chunk.

#### Behavior

- Returns `200 OK` on success
- Returns `400 Bad Request` for invalid identifiers or key format
- Returns `409 Conflict` when the lease key conflicts with an active reservation

### Upload endpoint

```http
PUT /worker/files/{fileId}/chunks/{chunkId}/upload
Content-Type: application/octet-stream
```

Required headers:

```http
X-Reservation-Key: <reservation-key>
X-Chunk-Size: <expected-size-in-bytes>
X-Chunk-SHA256: <hex-digest>
```

Body:

- raw byte stream of the chunk

This endpoint receives the bytes of the chunk, validates them, and writes them to disk if all checks pass.

#### Upload security checks

- fileId and chunkId must be valid identifiers
- reservation key must be valid
- chunk size must be non-negative
- checksum must be valid hexadecimal SHA-256
- the reservation must still be active
- during streaming, the size must not exceed expected size
- the final streaming digest must match the expected value

If any check fails, the upload is rejected.

## Main classes and responsibilities

### `CloudDataStoreApplication`

The entry point for the Spring Boot app. It starts the application and enables scheduling so background tasks can run.

### `WorkerReservationController`

REST controller for creating and renewing reservations. Delegates the request to `WorkerReservation`.

### `WorkerReservation`

This is the core reservation orchestrator.

It:

- validates IDs and reservation keys
- checks whether a reservation already exists
- creates a new reservation if the chunk is unknown
- renews an active reservation when the same key is used
- rejects conflicting keys while the lease is active
- allows key renewal after expiration depending on policy

Important logic is preserved in `renewExisting(...)` and `resolveConcurrentInsert(...)` to handle race conditions.

### `WorkerReservationLeaseHeartbeat`

This service keeps the reservation alive during a long upload.

It runs in its own transaction and only renews the lease when:

- the reservation exists
- it has not expired
- the same key is still present

This prevents uploads from silently failing when a master takes too long to send data.

### `HeartbeatService`

Schedules recurring heartbeat tasks using Spring’s `TaskScheduler`. This allows periodic renewals without blocking the main upload thread.

### `WorkerChunkUploadController`

Receives chunk upload requests and delegates to `WorkerChunkUpload`.

### `WorkerChunkUpload`

This is the heart of the upload process.

It does the following:

- validates file and chunk ids
- validates the reservation key
- validates expected size and checksum metadata
- ensures the chunk is still reserved
- starts a heartbeat thread while the upload is streaming
- creates a temporary file
- copies incoming bytes while computing the digest
- aborts if the upload exceeds the expected size
- aborts if the lease is lost during transfer
- moves the final file into the storage directory
- stores metadata in the database

### `ChunkContentVerifier`

This class performs integrity checks.

It includes:

- `validateExpectedValues(...)` – validates size/checksum before upload begins
- `copyAndVerify(...)` – streams bytes to disk while verifying size and SHA-256
- `verifyStoredFile(...)` – checks the stored file later against expected metadata

### `WorkerChunkFileStorage`

Writes a chunk to disk and commits the metadata-only transaction.

This class is designed to be resilient:

- uses an atomic move when supported
- falls back to a normal move when needed
- removes the current file if database persistence fails
- deletes replaced files when they are no longer referenced

### `WorkerReservationCleaner`

Deletes expired reservation records from the database at a fixed schedule.

The cleanup query is based on `expiresAt <= now`.

### `WorkerChunkIntegrityScanner`

This background process checks a batch of chunk files and compares them with their recorded metadata.

If an uploaded file is missing or modified, the scanner reports the reason and updates the last checked time for that metadata row.

### `MasterChunkCorruptionReporter`

Sends corruption reports to the master using HTTP over `RestClient`.

This is how the worker tells higher-level components that a chunk did not match its expected integrity.

## Persistence model

The application uses JPA entities with H2 storage.

### Reservation entity

`ChunkRecord`

This stores the active reservation for a file/chunk pair.

Fields:

- `fileId`
- `chunkId`
- `reservationKey`
- `expiresAt`

This is keyed by a composite identifier:

- `ChunkRecordId`

### Chunk metadata entity

`ChunkFileMetadata`

This stores the metadata for a successfully uploaded and verified chunk.

Fields:

- `fileId`
- `chunkId`
- `fileLocation`
- `fileSize`
- `checksum`
- `lastIntegrityCheckedAt`

The metadata is keyed by the same composite ID pattern and is intended for integrity scanning and cleanup.

## Integrity model

A core design goal of the project is to detect silent corruption.

A chunk upload includes two pieces of metadata from the master:

- expected size in bytes
- expected SHA-256 checksum

During upload, the worker streams the bytes and computes the digest while writing them to a temporary file. If the file exceeds the expected size or the digest does not match, the upload fails.

Later, the integrity scanner reads stored files and compares them to the metadata stored in the database. If a file is missing, modified, or has the wrong size/checksum, the worker reports the problem to the master.

## Validation strategy

Validation is separated into small components.

### Identifier validation

Ensures file and chunk identifiers are valid and within configured length bounds.

### Reservation key validation

Ensures the key is present and not too long.

### Key comparison strategy

The worker can compare keys using different strategies such as exact matching or custom logic.

### Renewal strategy

Controls whether a different reservation key may replace an active one.

For example, the `AfterExpirationKeyRenewalStrategy` allows a different key only after the existing reservation has expired.

## Safety and concurrency patterns

This project emphasizes correctness under concurrency and failure conditions.

### Lease races

The project handles a common race condition: two requests arriving simultaneously for the same file/chunk pair.

The code uses transactional repository lookups and state comparisons to detect and resolve conflicts cleanly.

### Lease renewal during transfer

Because upload may take a significant amount of time, the system starts a background heartbeat while bytes are still being streamed. This keeps the reservation active and prevents another upload from taking over unexpectedly.

### Transaction safety

The storage layer uses a transaction template when saving chunk metadata. If the database write fails, the file is deleted to keep the state consistent.

### File replacement cleanup

When a file is updated, the old file may remain on disk. The system checks whether any metadata row still references the previous path and removes it only if safe to do so.

## Scheduling and background tasks

The app schedules recurring jobs using `@EnableScheduling`.

The main scheduled concerns include:

- reservation cleanup
- integrity scanning
- recurring heartbeat tasks during active uploads

This ensures that stale reservations and latent data corruption do not go unnoticed.

## Testing

The project contains a meaningful set of tests in `src/test/java/org/cloudstore/worker`:

- `WorkerReservationControllerTest`
- `WorkerReservationTest`
- `WorkerReservationCleanerTest`
- `WorkerChunkUploadControllerTest`
- `WorkerChunkUploadTest`
- `MasterChunkCorruptionReporterTest`
- `ChunkContentVerifierTest`
- `ReservationKeyComparisonStrategyTest`
- `ChunkFileMetadataStorageTest`

These tests cover:

- reservation lifecycle
- validation behavior
- upload acceptance and rejection
- checksum verification
- corruption reporting
- metadata storage
- cleaner behavior

## Running the project

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

The application will boot with the default configuration in `application.properties`.

### Custom runtime settings

You can override defaults by setting environment variables before launch:

```bash
export DB_URL="jdbc:h2:file:./data/worker/metadata;DB_CLOSE_ON_EXIT=FALSE"
export WORKER_STORAGE_DIRECTORY="./data/worker/chunks"
export RESERVATION_TTL_SECONDS="60"
export MASTER_CORRUPTION_REPORT_URL="http://master:8080/api/v1/workers/{workerId}/chunks/corruption-reports"

mvn spring-boot:run
```

## Data directories

By default, the app writes:

- metadata DB: `./data/worker/metadata`
- chunks: `./data/worker/chunks`

These directories are created automatically as needed.

## Typical workflow

A common sequence for a master worker interaction looks like this:

1. Master reserves a chunk
   - `PUT /worker/files/{fileId}/chunks/{chunkId}/reservation`
   - Includes `X-Reservation-Key`
2. Worker validates the request and creates an expiring reservation record
3. Master uploads the raw bytes
   - `PUT /worker/files/{fileId}/chunks/{chunkId}/upload`
   - Includes expected size and SHA-256
4. Worker verifies data integrity and stores it locally
5. Worker persists metadata in the database
6. A background scanner later verifies stored files again
7. If corruption is found, the worker reports it upstream

## Operational concerns

### Data safety

The project is designed to reject unauthorized or corrupt writes. It does not trust the client blindly and instead validates the chunk before committing it.

### Availability

The app is resilient to partial upload failures and uses temporary files to avoid writing bad data into the final location.

### Correctness under load

The combination of reservation leases, heartbeat renewal, and concurrency-safe database operations keeps multiple requests from clobbering one another.

## Design summary

CloudDataStore is best understood as a worker node for a distributed storage protocol. Its responsibilities are narrow but critical:

- grant temporary ownership of a chunk
- accept a verified chunk body
- store it safely
- track its metadata
- validate its integrity later
- notify the master when corruption is detected

This is a robust worker implementation for a storage system that values correctness, lease semantics, and integrity verification more than end-user UX or UI polish.

## Future extension opportunities

This codebase is already structured for extension. Possible additions include:

- download endpoints for retrieving stored chunks
- chunk replication between workers
- stronger auth beyond reservation keys
- metrics and tracing
- support for larger storage backends such as NFS or cloud object storage
- explicit deletion APIs
- richer job scheduling and health checks

## License

This project does not appear to ship with an explicit license declaration in the repository metadata currently visible here. If you plan to distribute or reuse this code in a public or production setting, check the repository for the exact licensing terms before use.

## Conclusion

CloudDataStore is a compact but engineering-heavy worker application built around the principles of verified uploads, leases, integrity scanning, and maintenance of chunk metadata. It is a strong example of a backend service designed for data correctness in a distributed storage architecture.

The project is intentionally focused on correctness and operational resilience rather than user-facing functionality. It is particularly well suited to a storage system where chunks must be verified before acceptance and where stale or conflicting uploads must be rejected.

If you want to explore the code further, the most important files to start with are:

- `src/main/java/org/cloudstore/CloudDataStoreApplication.java`
- `src/main/java/org/cloudstore/worker/reservation/WorkerReservation.java`
- `src/main/java/org/cloudstore/worker/upload/WorkerChunkUpload.java`
- `src/main/java/org/cloudstore/worker/upload/WorkerChunkFileStorage.java`
- `src/main/java/org/cloudstore/worker/integrity/ChunkContentVerifier.java`
- `src/main/java/org/cloudstore/worker/integrity/WorkerChunkIntegrityScanner.java`
- `src/main/resources/application.properties`

These files form the core of the application and reveal the key behaviors of the system.
