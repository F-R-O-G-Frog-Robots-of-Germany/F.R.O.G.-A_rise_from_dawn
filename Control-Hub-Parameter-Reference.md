# Control Hub performance parameter reference

**Hub:** `FTC-wP5L` · **Wi-Fi SSID:** `FTC-wP5L` · **ADB serial:** `b7b1bc5d1dc1e74a`

**Inspected:** 16 October 2026, approximately 21:30–21:35, Europe/Berlin.

This is a performance-focused inventory of the connected Hub's controls: CPU/GPU clocks, RAM and swap behavior, program/thread scheduling, storage, network settings, and relevant controls already in this repository. It covers the exposed controls found during this inspection and identifies additional conditional interfaces. It is not a claim to enumerate every Android setting, kernel sysctl, or robot mechanism tuning constant.

**No Hub settings were changed to prepare this document.** Inspection used the ordinary ADB shell and the app's `run-as` access. Root was not enabled, no benchmark or OpMode was started, and no persistence was installed. A node's presence or writable permission bits do not prove that a driver will accept a particular write.

Published REV sources explain the interfaces; their current `main` branch has not been proven identical to this installed firmware build. Live readings take precedence, and source-only behavior is identified separately.

## 1. Earlier work found in the repository

| File | What it already contains |
| --- | --- |
| [Control-Hub-Hardware-Tuning.md](Control-Hub-Hardware-Tuning.md) | CPU governor experiments, compatible I2C bus speeds, Android thread priority, and earlier DDR uncertainty |
| [Control-Hub-Befehle-und-Vergleich.md](Control-Hub-Befehle-und-Vergleich.md) | German command guide, original CPU/governor values, temperature controls, root access and rollback |
| [Control-Hub-Benchmark-2026-10-09.md](Control-Hub-Benchmark-2026-10-09.md) | October 9 comparison of Hub A and Hub B, actual settings and limitations of the SHA-1 benchmark |
| [tools/control-hub/results/](tools/control-hub/results/) | Original observations, configuration records and benchmark results |
| [Inspect-ControlHub.ps1](tools/control-hub/Inspect-ControlHub.ps1) | Existing read-only CPU, thermal, devfreq and memory probe |
| [THREADING-2026-10-16.md](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/docs/THREADING-2026-10-16.md) | Existing native-thread affinity helper, vision/planner workers and CPU diagnostics |
| [Performance-Cleanup-and-Benchmark-Plan.md](Performance-Cleanup-and-Benchmark-Plan.md) | Existing software comparison plan and test OpMode; includes caching, telemetry, affinity and priority experiments |

The connected serial matches **Hub A** in the older records. On October 9 its final recorded setup was `performance`, min/max both `1512000` kHz, temperature target `95` °C, and thermal protection enabled. **Today the governor reads `interactive`.** Today's shell cannot read the active min/max policy limits or thermal target. Consequently, the October 9 final values must not be treated as today's configuration.

## 2. Current device snapshot

| Item | Observed on this Hub |
| --- | --- |
| Model / Control Hub OS | `Control Hub v1.0` / `1.1.6` |
| Android / kernel / build type | `7.1.2` / `3.10.245` / `userdebug` |
| Current ADB identity | `uid=2000(shell)`; root access worked in the October 9 records, not retested today |
| SELinux | `Permissive`, as found |
| Online CPUs / shared CPU policy | CPUs `0–3`; `related_cpus` = `0 1 2 3` |
| CPU driver / governor | `rockchip` / `interactive` |
| Driver-advertised CPU endpoints | `408000–1512000` kHz = **408–1512 MHz**; these are not a readback of the active min/max limits |
| Requested CPU frequency samples | `1008000–1512000` kHz during the sequential probe; not simultaneous independent core clocks |
| CPU temperature sample | `61250` raw = approximately **61.25 °C** using this sensor's documented units |
| Kernel-reported total RAM | `981684` KiB, approximately **958.7 MiB** |
| Swap size / use | `520908` KiB / `0` KiB at the snapshot |
| zRAM logical size / compressor | `533413888` bytes, approximately **508.7 MiB** / `lz4` |
| DDR clock report | `clk_ddr` = `786000000` Hz in `clk_summary`; this alone does not establish effective DDR transfer rate or an adjustable limit |
| GPU clock / automatic scaling | `200000000` Hz = **200 MHz** / `dvfs_enable = 1` |
| Robot Controller main native thread | PID/TID `999`, nice `-4`, affinity mask `f` = CPUs `0–3`; this is the app main thread, not evidence of the OpMode worker's priority |

PIDs, TIDs, temperature, memory usage and instantaneous clocks change. `/sys/class/devfreq` was empty, but vendor GPU and DDR interfaces exist elsewhere; an empty devfreq directory does not mean the entire board lacks clock controls.

### Access and lifetime

- **App:** an API or source setting inside your Robot Controller app; usually no root for its own thread, but requests can still be rejected.
- **Privileged runtime:** a system/root-owned sysfs, procfs or cgroup control. Usually needs root/system access; driver constraints still apply. Writes were not tested today.
- **Setup/restart:** requires reinitializing a subsystem, restarting the app, or rebuilding/reinstalling it.
- **Conditional:** supported by an API/source implementation, but its live interface or effect was not verified here.

Most kernel runtime changes last until reboot or until a service rewrites them. Thread settings last for that thread and may be inherited by newly created threads. Android can reassign process groups. App constants require a build/deploy. Network settings normally persist. **`adb unroot` only changes ADB privileges; it does not undo tuning.**

## 3. CPU clock and frequency policy

Actual CPU directory on this Hub: `/sys/devices/system/cpu/cpu0/cpufreq/`. The four CPU directories share a policy; they are not four separately tunable frequency domains.

| Parameter, relative to that directory | Current / choices | What it changes | Access |
| --- | --- | --- | --- |
| `scaling_governor` | Current `interactive`; six choices below | CPU frequency selection strategy | Privileged runtime |
| `scaling_min_freq` | Present, current value permission-denied; historical baseline `408000` | Policy floor, **kHz**; higher floor can reduce ramp-up delay and increase heat | Privileged runtime |
| `scaling_max_freq` | Present, current value permission-denied; historical baseline `1512000` | Policy ceiling, **kHz**; lowering can reduce heat and throughput | Privileged runtime |
| `scaling_setspeed` | Present; reads `<unsupported>` under `interactive` | Requested **kHz** when the `userspace` governor is active | Privileged runtime; conditional on governor |
| `scaling_available_frequencies` | `408000 600000 816000 1008000 1200000 1296000 1392000 1512000` | Read-only list to use when selecting supported frequencies | Read-only |
| `cpuinfo_min_freq`, `cpuinfo_max_freq` | `408000`, `1512000` | Read-only driver endpoints, not editable policy limits | Read-only |
| `scaling_cur_freq`, `cpuinfo_cur_freq` | First readable; second permission-denied today | Observations, not settings; requested and hardware frequency need not match | Read-only |

Keep `min <= max` and use advertised frequencies. Writing a larger number does not create an operating point above **1.512 GHz**. Thermal and other driver limits can still restrict the actual clock. See [Linux CPUFreq interfaces](https://www.kernel.org/doc/html/latest/admin-guide/pm/cpufreq.html).

| Governor available here | Behavior |
| --- | --- |
| `interactive` | Responds to workload using the tunables in the next section |
| `performance` | Requests the policy maximum; does not itself raise the maximum |
| `powersave` | Requests the policy minimum |
| `userspace` | Lets a privileged controller request a frequency through `scaling_setspeed` |
| `ondemand` | Periodically estimates load and selects frequency |
| `conservative` | Changes frequency more gradually with load |

Governor semantics: [REV kernel governor documentation](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/Documentation/cpu-freq/governors.txt). `schedutil` is **not** in this Hub's advertised governor list.

### Interactive governor parameters

Directory: `/sys/devices/system/cpu/cpufreq/interactive/`. All entries below were found on this Hub. They affect `interactive`, not a CPU policy running `performance`. These are privileged runtime controls.

| Parameter | Current value | Meaning / tradeoff |
| --- | --- | --- |
| `hispeed_freq` | `600000` kHz | Initial high-load frequency target |
| `go_hispeed_load` | `99` percent | Load threshold for requesting hispeed; lowering reacts earlier |
| `min_sample_time` | `40000` µs = 40 ms | Minimum dwell before downscaling |
| `timer_rate` | `20000` µs = 20 ms | Load reevaluation interval while active |
| `timer_slack` | `20000` µs = 20 ms | Extra timer deferral at above-minimum frequency; `-1` disables the slack timer in this implementation |
| `above_hispeed_delay` | `20000 1000000:80000 1200000:100000 1700000:20000` | Frequency-dependent wait before further upscaling; delays in µs, thresholds in kHz |
| `target_loads` | `70 600000:70 800000:75 1500000:80 1700000:90` | Frequency-dependent target load percentages; lower targets generally request more frequency |
| `io_is_busy` | `0` | Whether I/O-wait time counts as busy |
| `boost` | `0` | Sustained boost floor at hispeed while enabled |
| `boostpulse` | Write-only trigger | Temporary boost request; no meaningful current scalar value |
| `boostpulse_duration` | `40000` µs = 40 ms | Duration of pulse boost |

The `1700000` thresholds in tables do **not** make a 1.7 GHz CPU clock available. Table frequency thresholds must remain ordered. Definitions and parsing: [REV interactive governor implementation](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/drivers/cpufreq/cpufreq_interactive.c).

### Other governor settings: conditional

The governors are advertised, but their tunable directories were not activated or inspected. If selected, inspect `/sys/devices/system/cpu/cpufreq/ondemand/` or `conservative/` before relying on these source-defined names. Current values and accepted ranges are unverified here.

| Governor | Parameters to check | Purpose |
| --- | --- | --- |
| `ondemand` | `sampling_rate`, `sampling_rate_min` | Poll interval in µs and its read-only lower bound |
| `ondemand` | `up_threshold`, `sampling_down_factor` | Upscaling threshold and delayed downscaling |
| `ondemand` | `ignore_nice_load`, `io_is_busy`, `powersave_bias` | Load accounting and frequency bias |
| `conservative` | `sampling_rate`, `sampling_down_factor`, `up_threshold`, `down_threshold`, `freq_step`, `ignore_nice_load` | Sampling, thresholds and frequency step size |

These interfaces are described in the [REV kernel CPU governor sources](https://github.com/REVrobotics/kernel-controlhub-android/tree/main/drivers/cpufreq). A source-supported setting is not proof of a live writable node.

### Core availability and idle behavior

| Interface | Current evidence | Effect / access |
| --- | --- | --- |
| `/sys/devices/system/cpu/cpuN/online` | CPUs `0–3` online; CPU1–3 node reads denied | Core hotplug, where driver supports it; privileged, can reduce available processing capacity |
| `/sys/devices/system/cpu/cpu0/cpuidle/stateN/disable` | State 0 `WFI`, state 1 `cpu-sleep`; both `disable=0` | Per-state idle control; privileged setter not tested; disabling idle can increase power and heat |

## 4. RAM / DDR clock: what is actually established

**A DDR clock is observable, but an ordinary writable RAM-frequency setting has not been verified.** Memory capacity, zRAM size and CPU frequency are separate quantities.

| Interface / parameter | Observed | Interpretation |
| --- | --- | --- |
| `/sys/kernel/debug/clk/clk_summary` → `clk_ddr` | `786000000` Hz | Clock-tree observation; not a frequency-setting command |
| `/sys/class/devfreq/` | Empty | No standard devfreq RAM governor/min/max interface found |
| `/sys/devices/ddr.25/` | Bound to `rockchip_ddr`; no frequency attribute found | Driver presence does not establish a user clock setter |
| `/sys/module/ddr_freq/parameters/mode` | `normal` | Read-only driver mode report |
| `/sys/module/ddr_freq/parameters/sys_status` | `0` | Read-only system-status bitmask |
| `/sys/module/ddr_freq/parameters/auto_self_refresh` | `N` | Read-only state report, not a writable power-saving switch here |
| `/sys/module/ddr_freq/parameters/debug_mask` | `0` | Source exposes a privileged logging mask; not a speed control |
| `/dev/ddr_freq` | Character device exists | Vendor ioctl interface; **not** a text file accepting an MHz value |
| `/dev/video_state` | Character device exists, writable permission bits | Source-defined DDR profile requests: `p`/`n` set/clear performance, `l`/`L` set/clear low-power status. No requests were made or effects verified |

The vendor driver uses predefined board frequency tables and system-status requests. The existence of performance/low-power flags does not establish the target rates configured on this Hub. Its ioctl ABI is not a general RAM-clock API. These findings extend the earlier documents without establishing a RAM overclock procedure. See [REV DDR driver](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/arch/arm/mach-rockchip/ddr_freq.c).

RAM timings, DDR voltage, arbitrary DDR MHz and CPU clocks beyond the advertised table require separate board/kernel investigation; no working setting for them is established in this repository or today's inspection.

## 5. GPU clock and automatic scaling

Vendor directory: `/sys/devices/ff300000.gpu/`. Unlike CPU settings, these frequency values are in **Hz**.

| Parameter | Current / choices | Behavior / access |
| --- | --- | --- |
| `clock` | Current `200000000`; advertised `200000000`, `300000000`, `400000000`, `492000000` | Requests a supported GPU clock level; privileged runtime |
| `dvfs_enable` | `1`; boolean `0`/`1` | Automatic GPU frequency scaling; privileged runtime; can supersede a manual clock request |
| `available_frequencies` | Four rates above | Read-only list |
| `utilisation` | `0` at the sample | Read-only activity report |
| `error_count` | Node exists | Diagnostic counter; source provides no store handler, despite writable-looking mode bits |

GPU tuning addresses GPU workloads; it does not increase CPU execution speed or the remote Limelight camera's processing speed. The setter selects from existing frequency levels rather than creating a new overclock. See [REV Mali platform implementation](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/drivers/gpu/arm/mali400/mali/platform/rk30/mali_platform.c).

## 6. Program and thread priority / CPU placement

Linux schedules native **threads**. Changing a process leader is not equivalent to changing every SDK, OpMode, vision and planner thread.

| Parameter | Values / interface | Scope and effect |
| --- | --- | --- |
| Native nice priority | `android.os.Process.getThreadPriority(tid)` / `setThreadPriority(tid, value)`; Linux range `-20…19` | Lower number means more favorable CPU share under contention; permissions constrain raises |
| Current-thread priority | `Process.setThreadPriority(value)` | Applies to the calling native thread; use from inside the intended worker |
| Java priority | `Thread.setPriority(1…10)` | Java scale, not Linux nice; verify actual native result rather than equating numbers |
| CPU affinity | `taskset -p MASK TID`, or existing `ThreadAffinity` | Restricts eligible CPUs; does not reserve a core |
| CPU scheduling policy | `SCHED_OTHER`, `SCHED_BATCH`, `SCHED_IDLE`, `SCHED_FIFO`, `SCHED_RR` at kernel level | Conditional native/privileged API work; normal app threads observed with policy `0` (`SCHED_OTHER`); `chrt` was not on PATH |
| Real-time priority | Separate from nice; normally `1…99` for FIFO/RR | Conditional privileged native scheduling; can starve Android and communication workers |
| OOM adjustment | `/proc/PID/oom_score_adj`, `-1000…1000`; app leader currently `0` via `run-as` | Memory-pressure kill preference, **not CPU priority**; Android manages it and may overwrite manual changes |

Use `Process.myTid()` inside the worker. Java `Thread.getId()` is not the native TID. The app main thread was already at nice `-4`; setting it to `-1` would make it **less** favorable. The OpMode worker must be inspected independently. Capture and restore original values, and handle denied requests. API semantics: [Android Process](https://developer.android.com/reference/android/os/Process), [Java Thread priority](https://developer.android.com/reference/java/lang/Thread#setPriority(int)), [kernel process/OOM interfaces](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/Documentation/filesystems/proc.txt).

`taskset` and `renice` are installed. Check their on-device `--help`: the old Android Toybox command syntax must not be assumed identical to a current desktop Linux utility. Prefer the Android API for precise per-thread nice changes.

### Affinity choices already implemented in this repo

| Setting | Repository default | How to change |
| --- | --- | --- |
| `LimelightWorker.preferredCore` | `1` | Configure before `start()`; use `-1` to leave inherited affinity alone |
| `ButineWorker.preferredCore` | `2` | Configure before `start()`; same `-1` behavior |
| `ThreadAffinity.bind_current_thread(core)` | Caller selects | Returns application/readback evidence and saved original mask; restore in the same thread |
| `CpuThreadTest` control-loop placement | Requests CPU `0` | Existing stationary test toggles binding/restoration with X |

For this four-core Hub: CPU0 = hex `1`, CPU1 = `2`, CPU2 = `4`, CPU3 = `8`, all four = `f`. Masks combine bits; `3` permits CPUs0–1. Start workers before pinning their parent thread, because new threads can inherit affinity. Pinning does not exclude Android from that core. Existing details and cleanup requirements: [threading guide](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/docs/THREADING-2026-10-16.md). Android command implementation: [Toybox taskset](https://android.googlesource.com/platform/external/toybox/+/refs/tags/android-7.1.2_r39/toys/other/taskset.c).

### CPU groups: privileged global controls

| Interface | Current value | Meaning |
| --- | --- | --- |
| `/dev/cpuctl/cpu.shares` | `1024` | Relative CPU-group weight under contention |
| `/dev/cpuctl/bg_non_interactive/cpu.shares` | `52` | Background group weight; applies to the group, not a named program |
| `/dev/cpuctl/cpu.cfs_period_us` | `100000` µs | CPU quota accounting period |
| `/dev/cpuctl/cpu.cfs_quota_us` | `-1` | No quota at this group; positive quota limits CPU time per period |
| `/dev/cpuset/background/cpus` | `0` | Background group eligible CPUs |
| `/dev/cpuset/foreground/cpus` | `0-3` | Foreground group eligible CPUs |
| `/dev/cpuset/system-background/cpus` | `0-3` | System background eligible CPUs |
| `/dev/cpuset/top-app/cpus` | `0-3` | Top app eligible CPUs; Robot Controller was in this cpuset |
| Group `tasks` / `cgroup.procs` files | Membership, not a scalar | Assign threads/processes to groups; Android can reassign them |

CPU weight is relative, not a guaranteed percentage. Affinity is constrained by group eligibility and online CPUs. The memory-node `mems` values were all `0`; this is placement metadata, not RAM speed. See [REV CFS bandwidth documentation](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/Documentation/scheduler/sched-bwc.txt) and [cpusets](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/Documentation/cgroups/cpusets.txt).

## 7. Global scheduler parameters

Directory: `/proc/sys/kernel/`. All of these nodes were observed and read. They are privileged **whole-system** settings; a faster local loop is not proof of better overall scheduling. Accepted write ranges were not tested.

| Parameter | Current | Purpose |
| --- | --- | --- |
| `sched_latency_ns` | `10000000` = 10 ms | Target scheduling period at modest runnable load |
| `sched_min_granularity_ns` | `2250000` = 2.25 ms | Minimum execution granularity |
| `sched_wakeup_granularity_ns` | `2000000` = 2 ms | Wakeup preemption tolerance |
| `sched_migration_cost_ns` | `500000` = 0.5 ms | Cache-hot migration threshold |
| `sched_nr_migrate` | `32` | Tasks considered in balancing |
| `sched_child_runs_first` | `0` | New-child scheduling preference |
| `sched_tunable_scaling` | `0` | CPU-count scaling mode; source modes 0 none, 1 logarithmic, 2 linear |
| `sched_shares_window_ns` | `10000000` = 10 ms | Group load/share averaging window |
| `sched_time_avg_ms` | `1000` ms | Scheduler averaging period |
| `sched_cfs_bandwidth_slice_us` | `5000` = 5 ms | Quota distribution slice |
| `sched_rr_timeslice_ms` | `30` ms | Round-robin real-time timeslice |
| `sched_rt_period_us` | `1000000` = 1 s | Real-time bandwidth period |
| `sched_rt_runtime_us` | `950000` = 0.95 s | Real-time runtime budget per period |

Smaller timing values can increase preemption/context-switch costs. Real-time settings do not raise ordinary Java thread priority. Implementation references: [REV fair scheduler](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/kernel/sched/fair.c), [CFS design](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/Documentation/scheduler/sched-design-CFS.txt), [real-time bandwidth](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/Documentation/scheduler/sched-rt-group.txt).

## 8. RAM allocation, reclaim and swap behavior

These controls change memory management, **not physical RAM capacity or clock speed**. All following `/proc/sys/vm/` entries were read today; modifications require privileged access.

| Parameter | Current | Meaning / relevant consequence |
| --- | --- | --- |
| `swappiness` | `100` | Balance anonymous-page swapping versus file reclaim; lower is not automatically faster with zRAM |
| `vfs_cache_pressure` | `100` | Dentry/inode reclaim pressure |
| `dirty_background_ratio` | `5` percent | Background writeback threshold |
| `dirty_ratio` | `20` percent | Writer throttling/writeback threshold |
| `dirty_background_bytes` | `0` | Byte alternative to background ratio |
| `dirty_bytes` | `0` | Byte alternative to dirty ratio |
| `dirty_expire_centisecs` | `200` = 2 s | Dirty-data age for writeback eligibility |
| `dirty_writeback_centisecs` | `100` = 1 s | Periodic writeback interval |
| `min_free_kbytes` | `3858` KiB | Allocation reserve watermark |
| `extra_free_kbytes` | `10800` KiB | Extra headroom before direct reclaim |
| `overcommit_memory` | `1` | Virtual allocation policy: 0 heuristic, 1 permissive, 2 strict |
| `overcommit_ratio` | `50` percent | Strict-mode accounting factor, not active as a strict limit in mode 1 |
| `page-cluster` | `0` | Swap readahead exponent; 0 = one page |
| `laptop_mode` | `0` | Writeback batching policy |
| `oom_kill_allocating_task` | `0` | Kernel OOM victim-selection behavior |
| `drop_caches` | Reads `0` | One-shot cache eviction trigger, not a persistent RAM optimization |

Ratio/byte alternatives interact; changing one can reset its counterpart. Cache eviction changes the benchmark's cache state and can cause extra I/O afterward. Definitions: [REV kernel VM documentation](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/Documentation/sysctl/vm.txt).

### zRAM: compressed swap in physical memory

Directory: `/sys/block/zram0/`. The bracketed compressor in `lzo [lz4]` is the active one.

| Parameter | Current | Change requirements / effect |
| --- | --- | --- |
| `disksize` | `533413888` bytes | Logical capacity; resizing requires subsystem reinitialization, not an ordinary live overwrite |
| `comp_algorithm` | `lz4`; available `lzo`, `lz4` | Compression speed/ratio choice; rejected on an initialized device |
| `max_comp_streams` | `1` | Compression concurrency; positive integer, backend may reject live changes |
| `mem_limit` | `0` | Physical compressed-memory cap in bytes; 0 means no explicit cap; source permits runtime changes |
| `reset` | Write-only node | Destructive subsystem reset; not a scalar tuning parameter |
| Swap activation / priority | `/proc/swaps` priority `-1` | Setup-time `swapon` policy; additional swap devices were not configured |

Changing size/compressor involves stopping swap and rebuilding its state. Do that only as a separate restart/setup experiment: reclaiming used swap can create memory pressure. More zRAM is not more physical RAM. Interfaces: [REV zRAM documentation](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/Documentation/blockdev/zram.txt) and [installed-family driver source](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/drivers/block/zram/zram_drv.c).

### Android low-memory killer

Directory: `/sys/module/lowmemorykiller/parameters/`. These are privileged runtime controls, potentially rewritten by Android.

| Parameter | Current | Meaning |
| --- | --- | --- |
| `adj` | `0,100,200,300,900,906` | Victim eligibility thresholds |
| `minfree` | `11702,14628,17554,20480,35108,51200` | Corresponding pressure thresholds in pages, not bytes |
| `cost` | `32` | Shrinker seek-cost estimate |
| `debug_level` | `1` | Diagnostic logging verbosity |

Changing thresholds can keep background apps longer or cause earlier process kills. This is independent of CPU nice priority. Source: [REV lowmemorykiller](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/drivers/staging/android/lowmemorykiller.c).

### Java heap policy: configuration rather than a live RAM-speed knob

| Configuration | Current property / repo value | Scope |
| --- | --- | --- |
| `dalvik.vm.heapstartsize` | `16m` | Initial ART heap policy |
| `dalvik.vm.heapgrowthlimit` | `192m` | Normal application growth limit |
| `dalvik.vm.heapsize` | `512m` | Large-heap policy ceiling; not guaranteed usable free memory |
| `dalvik.vm.heaptargetutilization` | `0.75` | GC heap utilization target |
| `dalvik.vm.heapminfree`, `heapmaxfree` | `512k`, `8m` | GC free-space policy |
| Manifest `android:largeHeap` | `true` in both app manifests | App build/start setting already enabled in this repo |

These property readings are not verified runtime setters. They do not establish this running app's actual maximum: use `Runtime.getRuntime().maxMemory()` in the app. A bigger Java heap leaves less room for native SDK/camera allocations and the rest of Android. Manifest behavior: [Android largeHeap](https://developer.android.com/guide/topics/manifest/application-element#largeHeap).

## 9. Storage I/O scheduling

Directory: `/sys/block/mmcblk0/queue/`. These privileged runtime controls affect internal eMMC traffic, including logs and application files.

| Parameter | Current / choices | Purpose |
| --- | --- | --- |
| `scheduler` | `noop [cfq]` | Choose `noop` or current `cfq` |
| `read_ahead_kb` | `2048` KiB | Sequential read-ahead window |
| `nr_requests` | `128` | Request queue allowance |
| `nomerges` | `0` | Request merge policy: 0 normal, 1 reduced, 2 disabled |
| `rq_affinity` | `1` | Completion CPU-placement policy |

General definitions: [REV block queue sysfs](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/Documentation/block/queue-sysfs.txt).

CFQ-specific directory: `/sys/block/mmcblk0/queue/iosched/`. These settings depend on CFQ remaining selected.

| Parameter | Current | Purpose / units |
| --- | --- | --- |
| `back_seek_max` | `16384` | Backward-seek distance, KiB |
| `back_seek_penalty` | `2` | Backward-seek weighting |
| `fifo_expire_async` | `250` | Async request expiry, ms |
| `fifo_expire_sync` | `123` | Sync request expiry, ms |
| `group_idle` | `0` | Group idle window, ms |
| `low_latency` | `1` | Low-latency mode |
| `quantum` | `8` | Dispatch batch limit |
| `slice_async` | `40` | Async slice, ms |
| `slice_async_rq` | `2` | Async request allowance |
| `slice_idle` | `6` | Queue idle window, ms |
| `slice_sync` | `100` | Sync slice, ms |
| `target_latency` | `300` | Group scheduling latency target, ms |

Source: [REV CFQ documentation](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/Documentation/block/cfq-iosched.txt). Storage tuning only helps when storage work contributes to the delay being measured. The `ionice` utility was not found on this Hub's PATH; do not assume a desktop recipe is available.

## 10. Temperature and power behavior

| Parameter | Current evidence | Interpretation |
| --- | --- | --- |
| `/sys/dvfs/cpu_temp_target` | Present; read denied today; historical `cpu:95` | Rockchip thermal target in whole °C; privileged runtime |
| `/sys/dvfs/cpu_temp_enable` | Present; read denied today; historical `cpu:1` | Vendor thermal-control enable; disabling changes thermal protection behavior |
| `/sys/class/thermal/thermal_zone0/temp` | Sample `61250` | Read-only temperature, milli-°C on this sensor |
| `/sys/class/thermal/thermal_zone0/policy` | `step_wise` | Thermal policy interface present; alternative policies/setter not verified |
| `/sys/class/thermal/thermal_zone0/mode` | `enabled` | Thermal-zone mode; setter not tested |
| Android `stay_on_while_plugged_in` | `1` | Plugged-in stay-awake setting; bitmask, not CPU clock control |

The earlier **95 °C** target is a historical device value, not a recommended operating temperature. Changing it is not a general speed setting, and the target is not necessarily the only throttling/shutdown threshold. Keep thermal protection enabled for comparisons. Vendor semantics: [REV Rockchip DVFS implementation](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/arch/arm/mach-rockchip/dvfs.c).

## 11. Network settings and repo-level workload controls

### Hub networking

| Parameter | Current evidence | Where / consequence |
| --- | --- | --- |
| Hub device name / AP SSID | Both `FTC-wP5L` | Robot Controller Console → Manage; renaming changes the network clients select |
| Wi-Fi password | Not collected for this document | Console → Manage; changing it requires reconnecting clients |
| Wi-Fi band | Manage supports 2.4/5 GHz; not freshly read here | Select supported band for the environment |
| Wi-Fi channel | Configurable; not freshly read here | Console → Manage; supported choices depend on band/region/OS |

Use the Console to apply network settings rather than editing private preferences. The generic Android device-name setting separately reads `Control Hub v1.0`; it is not the FTC Hub name/SSID. See [REV managing Wi-Fi](https://docs.revrobotics.com/duo-control/managing-the-control-system).

### Controls already present in the checked-out robot code

These are source defaults in the current working tree, not a claim that this exact APK is installed or these workers are running. Source edits require rebuild/deploy; individual API choices may be made during INIT.

| Setting / location | Current value or behavior | Performance effect / limitation |
| --- | --- | --- |
| `CompConfig.COMP_MODE` | `true` | Disables Panels/general telemetry through its accessors; video flag also false, but the plan identifies the video hook as currently empty |
| `CompConfig.LOOP_TIME_TELEMETRY` | `true` | Keeps loop-time output enabled in competition mode |
| `CompConfig.INIT_TELEMETRY` | `true` | Allows INIT selection telemetry in competition mode |
| `setMsTransmissionInterval(...)` | TeleOp 100 ms; auto 200 ms; CPU test 200 ms | Limits sending; does not by itself prevent per-loop telemetry construction |
| Lynx `setBulkCachingMode(...)` | Competition INIT `AUTO`, after START `MANUAL` | Reduces repeated supported hardware transactions; clear MANUAL cache once per control iteration |
| Limelight SDK `setPollRateHz(...)` | `100` in `Limelight.java` | Camera-result polling cadence; not the remote camera's processing FPS |
| `LimelightWorker.LOOP_NANOS` | `10_000_000` ns = 10 ms | Java worker target period; source constant |
| `LimelightWorker.HEALTH_NANOS` | `250_000_000` ns = 250 ms | Status/health-read cadence; source constant |
| Vision/planner `preferredCore` | `1` / `2` | Existing placement controls, section 6 |
| CPU test sampler sleep | `500` ms | Filesystem/CPU diagnostics cadence; source constant |
| Range-read refresh policy | Earlier/current plan identifies 300 ms cache | Changes sensor freshness as well as workload; inspect the owning mechanism before changing |
| Motor/servo command deadbands | Existing mechanism-specific values in the comparison plan | Reduce forwarded changes at the cost of command resolution; preserve exact stop and validity behavior |
| Planner candidate/iteration/local-search limits | Existing planner parameters | Trade solution quality against work; current threading guide says no wall-clock compute cap |
| Sensor I2C bus rate | Earlier SDK audit: `STANDARD_100K` / `FAST_400K` | Conditional on actual sensor/client and every shared-bus device supporting the rate; not a CPU/RAM clock |

Relevant files: [CompConfig.java](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/core/control/CompConfig.java), [AbstractTeleOp.java](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/teleop/AbstractTeleOp.java), [AbstractAuto.java](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/opmodes/auto/structure/AbstractAuto.java), [Limelight.java](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/Limelight.java), [LimelightWorker.java](TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/LimelightWorker.java), [comparison plan](Performance-Cleanup-and-Benchmark-Plan.md).

The older hardware guide contains the SDK I2C investigation and proposed sensor-specific snippet. Actual intake sensor models were not established by today's inspection. REV's Color Sensor V3 supports both rates: [sensor specification](https://docs.revrobotics.com/rev-crossover-products/sensors/color-sensor).

## 12. Commands for inspecting and making controlled changes

These are **instructions for later use**, not commands applied while writing this document.

### Read-only inspection from PowerShell

Run from the repository root:

```powershell
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$hub = 'b7b1bc5d1dc1e74a'
& $adb devices -l
& .\tools\control-hub\Inspect-ControlHub.ps1 -Serial $hub `
    -OutputPath "$env:TEMP\control-hub-baseline.txt"
& $adb -s $hub shell iw dev wlan0 info
& $adb -s $hub shell cat /sys/devices/ff300000.gpu/available_frequencies
& $adb -s $hub shell cat /sys/devices/ff300000.gpu/clock
& $adb -s $hub shell cat /proc/sys/vm/swappiness
& $adb -s $hub shell ps -t -p 999  # Replace 999 with the current PID.
```

Find the current PID with `& $adb -s $hub shell pidof com.qualcomm.ftcrobotcontroller`. Permission-denied readings are expected with the ordinary shell. The existing inspector does not enumerate the vendor GPU/DDR interfaces or every scheduler/storage setting listed above.

### Privileged shell for a later kernel-setting experiment

Only when ready to run a tuning experiment, use PowerShell:

```powershell
& $adb -s $hub root
& $adb -s $hub wait-for-device
& $adb -s $hub shell
```

Check `id` inside the Hub shell. If root is refused, these kernel recipes are unavailable through that route. `userdebug` plus the older successful test is evidence to investigate, not today's successful root readback. Root access documentation: [AOSP ADB privileges](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/dev/root.md).

### Example: governor-only comparison with rollback

The following is **Android shell**, after verifying root and supported governors. It leaves the clock bounds at their saved values, and verifies them during cleanup. Save the baseline output on the PC as well: shell variables/traps cannot guarantee recovery from a lost connection or crash.

```sh
cpu=/sys/devices/system/cpu/cpu0/cpufreq
old_governor=$(cat "$cpu/scaling_governor") || exit 1
old_min=$(cat "$cpu/scaling_min_freq") || exit 1
old_max=$(cat "$cpu/scaling_max_freq") || exit 1
case " $(cat "$cpu/scaling_available_governors") " in
  *" performance "*) ;;
  *) echo 'performance unavailable'; exit 1 ;;
esac
printf 'Baseline: governor=%s min=%s max=%s\n' "$old_governor" "$old_min" "$old_max"
restore_governor() {
  printf '%s\n' "$old_governor" > "$cpu/scaling_governor"
  cat "$cpu/scaling_governor" "$cpu/scaling_min_freq" "$cpu/scaling_max_freq"
}
trap restore_governor EXIT
trap 'exit 1' HUP INT TERM
printf '%s\n' performance > "$cpu/scaling_governor" || exit 1
cat "$cpu/scaling_governor"
# Run the selected measurement, then leave this shell with exit.
# Normal exit triggers governor restoration. Verify all three values afterward.
```

If separately changing CPU limits, preserve both originals and maintain `min <= max` during every write and rollback. Restoring only the governor does not undo a fixed high minimum. No temperature change is needed for this governor comparison.

### Example: native priority from inside the intended Java worker

This is a usage sketch, not a patch to the robot app. A rejected priority request must be handled by the caller; verify the native readback before describing it as applied.

```java
int tid = android.os.Process.myTid();
int original = android.os.Process.getThreadPriority(tid);
int requested = Math.min(original, -1); // Preserve an already more favorable value.
try {
    android.os.Process.setThreadPriority(tid, requested);
    int actual = android.os.Process.getThreadPriority(tid);
    // Record original/requested/actual, then run the bounded measured workload.
} finally {
    android.os.Process.setThreadPriority(tid, original);
}
```

For affinity use the existing helper and its restoration API, rather than changing every app thread with `taskset -a`. Never put subprocess setup or kernel writes in the per-tick control loop.

When finished, leave the device shell, then in PowerShell:

```powershell
& $adb -s $hub unroot
& $adb -s $hub wait-for-device
& $adb -s $hub shell id
```

Restore and verify the actual settings **before** dropping root. No boot scripts, persistent properties, kernel flashing or overclock procedure are included here.

## 13. Choosing the next experiment

For the first comparison, change just the governor while preserving the live min/max values and thermal behavior. Separately compare existing affinity settings or a modest native-priority request only after identifying the actual worker TID. The CPU/thread diagnostics already provide a useful starting point.

Record the original value, requested value, effective readback, workload, battery condition, starting/ending temperature, median loop time, high-percentile/worst loop time, sample age, failed reads and stop responsiveness. Restore the baseline and repeat it to distinguish a settings effect from warm-up or temperature drift.

The October 9 SHA-1 test compares one computational workload; it did not measure governor improvement, motor/sensor latency or DDR speed. Memory, global scheduler, GPU and storage adjustments should follow evidence that the corresponding subsystem is causing delay. No measured speedup for the settings in this reference is claimed.
