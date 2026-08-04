# Xylune 0.23.11

## Ubuntu certificate installation repair

The previous repair still depended on Android process pipes. On affected devices, `update-ca-certificates` could still inherit a stream that became unusable and abort with `echo: I/O error`.

Xylune now gives Linux processes app-private regular files for stdout and stderr. The UI tails those files for live progress, retains at most one megabyte in the final result, and removes the temporary logs after each command. This removes the broken-pipe failure mode without sacrificing progress reporting.
