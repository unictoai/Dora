# Dora Download Center Architecture

## Scope

Dora preserves its existing Kotlin, Jetpack Compose, Room, WorkManager, Hugging Face, filesystem, GGUF, llama.cpp, C++, JNI, and ARM64 foundations. The Download Center is an additive layer over the existing `MainViewModel`, `DoraModelDownloadWorker`, `ModelDownloadManager`, `LocalRegistry`, and `DoraDatabase` components.

> The Download Center never treats a byte count of 100% as model readiness. The terminal path is `VERIFYING → VALIDATING → INSTALLING → COMPLETED`.

## Runtime flow

```text
Hugging Face candidate
        |
        v
MainViewModel -- storage/fit/duplicate checks --> WorkManager unique work
        |                                            |
        |                                            v
        |                                  DoraModelDownloadWorker
        |                                            |
        |                       progress events + checkpoints
        |                                            |
        v                                            v
Compose Download Center <---- Room JobRecord <---- LocalModelStore / GGUF validator / JNI validation
```

The downloader writes to a private `.part` file, supports HTTPS redirects and HTTP Range resume, verifies expected size and SHA-256 when available, validates the GGUF header, and atomically finalizes the artifact. The worker then validates the file with llama.cpp before writing the model record and registry artifact.

## State machine

| State | Meaning | Legal next states |
|---|---|---|
| `QUEUED` | Work has been accepted but not started | `STARTING`, `CANCELLING`, `CANCELLED` |
| `STARTING` | Worker is preparing the secure transfer | `DOWNLOADING`, `RETRYING`, `CANCELLING`, `FAILED` |
| `DOWNLOADING` | Network transfer is active | `PAUSED`, `VERIFYING`, `RETRYING`, `CANCELLING`, `FAILED` |
| `PAUSED` | Work was cancelled by a pause request and the partial file is retained | `QUEUED`, `DOWNLOADING`, `CANCELLING`, `CANCELLED` |
| `VERIFYING` | Size, checksum, and GGUF container checks are running | `VALIDATING`, `RETRYING`, `FAILED`, `CANCELLING` |
| `VALIDATING` | llama.cpp is loading the model for validation | `INSTALLING`, `RETRYING`, `FAILED`, `CANCELLING` |
| `INSTALLING` | Room and LocalRegistry are being updated after validation | `COMPLETED`, `RETRYING`, `FAILED`, `CANCELLING` |
| `RETRYING` | A bounded WorkManager retry is being scheduled | `STARTING`, `DOWNLOADING`, `FAILED`, `CANCELLING` |
| `COMPLETED` | The model is registered and ready | terminal |
| `FAILED` | The job stopped with a user-visible diagnostic | `RETRYING`, `CANCELLING` |
| `CANCELLED` | The user cancelled work; temporary artifacts are cleaned | terminal |

## Persistence and recovery

Room `JobRecord` version 3 stores the download ID, model ID, repository, filename, source revision and license, URL, expected checksum, explicit download state, bytes, total bytes, speed, ETA, elapsed time, timestamps, retry count, error, temporary path, and final path. High-frequency telemetry is kept in WorkManager progress and is checkpointed to Room on a time or byte threshold rather than once per chunk.

On startup, Dora reconciles model records, registry artifacts, and private files. It also scans persisted download records. If a download or install state was interrupted and no corresponding WorkManager job remains active, Dora marks the record as retrying and re-enqueues it through the same unique-work path. Incomplete records are never promoted to `COMPLETED` without repeating verification and native validation.

Pause uses a persistent control marker to distinguish user pause from ordinary WorkManager cancellation. A pause retains the `.part` file. Cancel removes the private partial file after the worker observes the cancellation marker. Resume and retry reconstruct the original HTTPS request from Room metadata and rely on the downloader’s Range/HTTP 206 behavior.

## UI contract

The Models screen contains Download Center sections for active, queued, paused, failed, and completed jobs. Each card exposes accessible model name, percentage or calculating state, bytes, real speed when available, ETA when calculable, lifecycle state, retry count, and actionable pause/resume/retry/cancel/delete controls. An expandable details region exposes repository, filename, elapsed time, retry count, and download ID. Settings shows total storage, available storage, model bytes, temporary download bytes, and orphan count.

## Deliberate limitations

The current release keeps one active model download by default. Queue reordering and configurable concurrency remain follow-up work. The native runtime still loads one model per generation and does not yet expose a persistent context/session manager. Token statistics and generation speed are not shown because the JNI bridge does not currently emit trustworthy token timing data. Physical-device acceptance testing is still required before any beta claim.
