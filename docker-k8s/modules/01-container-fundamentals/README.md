# Module 01 — Container Fundamentals

## Overview

What containers _really_ are under the hood. This module demystifies the Linux primitives — namespaces, cgroups, union filesystems — that make containers possible. You'll understand OCI specifications, how container runtimes work, and why "a container is just a process" is both true and misleading.

By the end of this module, you'll be able to create a container using nothing but Linux syscalls. That understanding is the foundation everything else in this track builds on.

---

## Learning Objectives

After completing this module, you should be able to:

- [ ] Explain how Linux namespaces provide process isolation
- [ ] Describe how cgroups limit resource usage (CPU, memory, I/O)
- [ ] Differentiate between OCI Image Spec and OCI Runtime Spec
- [ ] Compare container runtimes: runc, containerd, CRI-O
- [ ] Build a minimal container from scratch using Linux primitives
- [ ] Explain the difference between containers and VMs at the kernel level
- [ ] Read and interpret `/proc` filesystem entries related to namespaces and cgroups
- [ ] Inspect OCI image layers and manifests

---

## Topics

| # | File | Topic | Est. Time |
|---|------|-------|-----------|
| 1 | [01-linux-namespaces-and-cgroups.md](01-linux-namespaces-and-cgroups.md) | Linux Namespaces & Cgroups | 60 min |
| 2 | [02-oci-images-and-runtimes.md](02-oci-images-and-runtimes.md) | OCI Images & Container Runtimes | 45 min |
| 3 | [03-container-vs-vm.md](03-container-vs-vm.md) | Containers vs VMs: Architecture Deep Dive | 30 min |
| 4 | [exercises/README.md](exercises/README.md) | Hands-on Exercises | 60 min |

**Total estimated time: 3–4 hours**

---

## Prerequisites

- Linux command line basics (`ls`, `ps`, `mount`, `cat /proc/...`)
- Understanding of processes and filesystems (what PID 1 is, what `/proc` exposes)
- Comfort running commands as root / with `sudo`
- A Linux environment (native, VM, or WSL2) — macOS Docker Desktop won't work for the namespace exercises

---

## Capstone Milestone

> **Goal:** Understand the Linux primitives that Docker abstracts away. Be able to explain to a colleague why containers are not VMs and what actually happens when you run `docker run`.

At the end of this module you won't write DeployForge code yet, but you'll have the mental model that makes everything in Modules 02–13 click. When you later debug a container that's OOM-killed, can't reach the network, or has filesystem permission issues — you'll know _exactly_ which primitive is involved.

---

## How to Work Through This Module

1. Read each concept file in order (01 → 02 → 03).
2. Run every code example — don't just read them.
3. Complete the exercises in `exercises/README.md`.
4. Check off the learning objectives above as you master each one.
5. Move to [Module 02 — Docker Mastery](../02-docker-mastery/) when ready.
