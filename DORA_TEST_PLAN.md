# Dora Download Center Test Plan

## Automated unit coverage

The JVM suite covers the explicit download state machine and rejects illegal shortcuts such as `DOWNLOADING → COMPLETED`. It covers rolling telemetry, ensuring speed and ETA are null before a second real sample and that Room checkpoint decisions are throttled. Existing tests cover GGUF header validation and the offline demo engine.

## Download engine matrix

| Scenario | Expected result | Current coverage/status |
|---|---|---|
| HTTP 200 full response | Write a new `.part`, verify, atomically finalize | Existing implementation; add failure-injection test |
| HTTP 206 resume | Append from exact partial size | Existing implementation; add failure-injection test |
| Redirect | Follow bounded HTTPS redirects only | Existing implementation; add redirect test |
| HTTP 403/404/429/5xx | Classify and show actionable error; retry only retryable classes | HF client classification implemented; downloader integration test pending |
| Timeout/DNS/reset | Preserve partial file and bounded retry | Worker retry logic implemented; integration test pending |
| Wrong size/checksum | Fail before install | Existing downloader; integration test pending |
| Corrupt/unsupported GGUF | Fail validation and never register | GGUF unit coverage exists; download integration pending |
| Disk full | Fail with storage diagnostic and preserve safe state | Preflight implemented; forced disk-full test pending |
| Cancellation | Stop WorkManager work and clean temporary file | Control store and worker handling implemented; integration test pending |
| Pause/resume | Preserve `.part`, resume with Range, expose paused state | Implemented; device/network integration test pending |
| Duplicate tap | One unique WorkManager job | Guard and unique work implemented; test pending |

## State-transition tests

The required happy path is `QUEUED → STARTING → DOWNLOADING → VERIFYING → VALIDATING → INSTALLING → COMPLETED`. The tested alternate paths are `DOWNLOADING → PAUSED → DOWNLOADING`, `DOWNLOADING → CANCELLED`, and `DOWNLOADING → FAILED → RETRYING → DOWNLOADING`. Terminal states cannot silently return to ready.

## Recovery and concurrency tests

A migration fixture must validate Room v2 to v3. A process-death test must create a persisted active state, cancel or remove the corresponding WorkManager record, recreate the ViewModel, and confirm startup recovery re-enqueues the job. Concurrency tests must cover download plus delete, download plus restart, download plus network loss, duplicate taps, download plus import, and download plus storage exhaustion.

## UI acceptance run

On a physical ARM64 Android device, search a model, inspect recorded compatibility, start a download, confirm the card appears immediately, background the app, observe the notification, return, pause, resume, observe 100% transition through verifying/validating/installing, open chat, stop generation, close and reopen Dora, go offline, confirm the installed model remains available, delete it, and confirm storage state updates.

Every major screen must be checked for loading, empty, error, and success states. Accessibility checks must confirm that progress exposes model name, percentage or calculating state, and lifecycle state through content descriptions.

## Release gate

A release may be called installable only after `assembleDebug`, `assembleRelease`, `testDebugUnitTest`, `lintDebug`, APK signature verification, package/ABI inspection, and remote release-asset checksum verification pass. A beta claim additionally requires the physical-device acceptance run; sandbox builds alone are insufficient.
