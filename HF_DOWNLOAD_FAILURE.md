# Hugging Face Download Failure Analysis

## Reproduction

The public Hugging Face search endpoint returned GGUF repositories and the model metadata endpoint returned repository revisions, GGUF filenames, file sizes, and LFS SHA-256 values. A small public GGUF probe also confirmed that the revision-pinned `resolve/<revision>/<filename>` URL redirects from `huggingface.co` to a Hugging Face CDN/Xet URL, advertises byte ranges, and returns `206 Partial Content` for a range request.

The failing risk was in the Android downloader’s assumptions. It relied on implicit redirect behavior, derived totals only from `Content-Length`, did not reject compressed transfer, did not validate GGUF magic after a remote download, and the foreground path was not durable across process death. These conditions can produce a file that appears downloaded but cannot be loaded, or a partial transfer that cannot resume correctly.

## Fixes applied

Dora now follows HTTPS redirects explicitly with a bounded redirect count, preserves the range request on the redirected connection, rejects non-HTTPS redirect destinations, requests identity encoding, derives total size from `Content-Range` when present, preserves partial files on coroutine cancellation, validates expected byte count and SHA-256, checks the GGUF magic bytes before finalization, and only renames the `.part` file after all checks pass.

The WorkManager worker now carries Hugging Face provenance, reports durable progress and structured failure messages, validates the native llama.cpp load before completion, writes Room model records, and mirrors successful installations into the app-private registry for restart recovery.

## Remaining verification gate

The public API and CDN behavior are verified from the development environment, and the Android project compiles with unit tests. A physical ARM64 device is still required to execute the complete in-app flow against a real model: search, tap download, background/resume behavior, native validation, and chat generation. The app should not be called beta-ready until that device test passes.
