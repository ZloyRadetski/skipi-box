---
title: "Tunnel memory tile counts Go held memory plus hev; Linux and Android hev is counted by a patch inside hev, macOS is not"
author: Radetski
date: 2026-11-25
branch: main
cost: { minutes: 11, tokens: 50966 }
id: l-e8226713
scope: repo
status: active
replaces: l-9b5ab6e3
src: agent
---

## Decided
The memory figure is Go runtime total minus released heap, plus hev memory the core reports as externalBytes. Windows: private bytes of the hev child process. Android and Linux (hev in process): a counter inside hev's allocators, added by third_party/patches/hev-socks5-tunnel/0002-memory-usage.patch, because the heap is shared with the JVM and Go and cannot be split from outside.

## Tried and ruled out
- Process RSS or mallinfo for in-process hev: on Android it includes the JVM and other native code, so it is not hev.

## Watch out
- The hev counter covers allocations only. Task stacks are separate mmap regions and are not counted.
- The Android counter was only built (all four ABIs, AAR links), never run on a device or compared with dumpsys meminfo. The Linux build uses hev's slice allocator and is patched the same way but was not built here.
- macOS hev runs as a root process and is not counted.

In the code: skipi-core internal/memstats SetExternalBytes, internal/hevtun MemoryBytes per target, hevtun_cgo.c skipi_hev_memory_bytes, shared/core CoreMemoryStats.kt coreMemoryKilobytes.
