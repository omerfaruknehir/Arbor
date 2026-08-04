# Xylune 0.23.10

## Linux setup progress

The installer now uses a thicker rounded progress bar with smooth value changes, eight compact stage segments, the current step, percentage, current package activity, and human-readable elapsed time.

## Ubuntu installation reliability

Xylune previously stopped reading a child process pipe as soon as one megabyte of output had been retained. Closing that pipe early could make verbose Debian package scripts fail with an `I/O error`; the screenshot from `update-ca-certificates` is consistent with that failure mode. Output retention remains capped, but the complete pipe is now drained until the process exits.
