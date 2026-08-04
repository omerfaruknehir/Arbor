# Xylune 0.23.9

## Linux setup progress

- Uses one monotonic progress range for the complete installation instead of restarting or stalling the bar at each internal phase.
- Shows the current step out of eight, elapsed time, current download amount, package-manager activity, and package names while Python tools are installed.
- Keeps indeterminate progress only for work whose exact completion fraction is unavailable, while still updating the visible phase and activity text.

## Correct storage reporting

- Replaces the old logical file-length sum, which counted every path to a hard-linked file and could report several times the real usage.
- Counts allocated filesystem blocks once per unique device/inode pair and does not follow symbolic links while traversing the Linux runtime.
- Labels the value **Linux data on disk**. It is the Linux environment's storage use, not the APK size and not Android's total app-data figure.

## Validation

- Adds regression coverage for apk package-counter progress and structural checks for step reporting and inode-aware storage accounting.
- Runs release unit tests, lint, and release APK assembly before the patch branch is committed.
