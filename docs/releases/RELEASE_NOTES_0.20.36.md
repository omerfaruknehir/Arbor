# Xylune 0.20.36

This release repairs the widget compiler network layer falsely reporting `EOFException` for every normal HTTPS JSON response, including Open-Meteo.

## Fixed

- Replaces the exact-length `readUtf8(1 MB + 1)` call which required every response to contain more than 1 MB and therefore threw `EOFException` for ordinary short API bodies.
- Reads response bodies incrementally until real EOF while retaining the strict 1 MB safety ceiling.
- Probes one byte beyond the ceiling so chunked and unknown-length oversized responses are still rejected without unbounded buffering.
- Treats genuine I/O failures, including premature EOF and TLS truncation, as transient during compiler preflight when complete offline fallbacks exist.
- Adds regression tests for short JSON, exact-limit, and oversized response bodies.

## Verification

Release unit tests, release lint, the optimized release APK build, and the widget HTTP body regression suite passed before publication.
