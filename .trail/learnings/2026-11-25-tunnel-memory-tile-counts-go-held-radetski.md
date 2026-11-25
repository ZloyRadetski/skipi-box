---
title: Tunnel memory tile counts Go held memory plus hev; in-process hev on Android and Linux is still not counted
author: Radetski
date: 2026-11-25
branch: main
cost: { minutes: 18, tokens: 162368 }
id: l-9b5ab6e3
scope: repo
status: active
src: agent
---

## Decided
The memory figure is Go runtime total minus released heap, plus hev memory the core reports as externalBytes. On Windows that is the hev child process private bytes. Checked on Windows: the Go part tracks Working Set within about 6 MB.

## Tried and ruled out
- Process RSS or mallinfo for in-process hev: on Android it includes the JVM and other native code, so it is not hev.

## Watch out
Android and Linux run hev in process on the shared malloc heap, so externalBytes is 0 there and the tile still understates. Fixing it means counting inside hev's allocator (hev_malloc and hev_free in hev-task-system) through a patch in third_party/patches, which needs an NDK build to verify. macOS hev runs as a root process and is also not counted.

In the code: skipi-core internal/memstats SetExternalBytes, internal/hevtun MemoryBytes per target, shared/core CoreMemoryStats.kt coreMemoryKilobytes.
