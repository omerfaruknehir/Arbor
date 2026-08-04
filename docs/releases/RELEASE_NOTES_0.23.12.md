# Xylune 0.23.12

## Ubuntu setup: actual `ca-certificates` fix

0.23.11 redirected the outer PRoot process output to files, but APT was still configured with `APT::Status-Fd=1`. That reused standard output as APT's internal progress channel. Under Android/PRoot, package maintainer scripts such as `update-ca-certificates` could then lose their output stream and fail with `echo: I/O error`.

0.23.12 gives APT a separate file descriptor (fd 3) backed by an app-private regular file. Xylune tails that file for live progress, while maintainer scripts retain ordinary stdout and stderr. The same path is used for first setup, dependency repair, and user-requested package installation.
