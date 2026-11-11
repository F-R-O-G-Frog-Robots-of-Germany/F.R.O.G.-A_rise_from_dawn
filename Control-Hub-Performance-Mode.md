# Control Hub performance batch file

The script was found in the sibling project at `C:\Users\kmkoe\StudioProjects\Ma-Douce\tools\performance_mode.bat`. It is not currently part of this repository. This explanation is based on reading that file; the script was not executed and no Control Hub settings were changed.

## What it changes

Despite being described as an overclock, the script does not raise a CPU frequency limit, change voltage, or install a modified kernel. It requests the `performance` CPU governor on CPUs 0 through 3 and, by default, attempts to stop Android's `thermal-engine` service.

The performance governor requests the highest frequency allowed by the CPU frequency policy. This favors sustained CPU speed over load-dependent frequency selection, but does not establish a higher supported maximum. CPUs may share a frequency policy, so four writes do not necessarily configure four independent clocks. See the [Linux CPU frequency documentation](https://www.kernel.org/doc/html/latest/admin-guide/pm/cpufreq.html).

| Stage | Commands or behavior | Purpose |
|---|---|---|
| Request privileged ADB | `adb root`, then a three-second Windows timeout | Ask the device's ADB daemon to restart with root privileges. This is not a rooting exploit; it depends on device support. |
| Set governor | Write `performance` to `/sys/devices/system/cpu/cpuN/cpufreq/scaling_governor` for N = 0..3 | Request performance policy on the expected four CPUs. |
| Optionally stop thermal service | `adb shell stop thermal-engine` | Attempt to stop the named thermal management service. This is enabled by default. |
| Inspect settings | Read each governor and `scaling_cur_freq`; optionally run `ps` filtered for `thermal` | Print pass, warning, or failure messages, then pause for a key press. |

Stopping a thermal management service can remove part of the device's response to rising temperature. The script does not establish which protections remain active, monitor temperature, or demonstrate that sustained operation is safe. A governor change and disabling thermal management should therefore be evaluated as separate changes.

## Arguments

The script checks only its first argument, ignoring case:

| Invocation | Thermal behavior |
|---|---|
| `performance_mode.bat` | Attempts to stop `thermal-engine`. |
| `performance_mode.bat -nt` | Skips stopping the service. |
| `performance_mode.bat --no-thermal` | Also skips stopping the service. |

The flag name `--no-thermal` means “do not perform the thermal-service stop” in this script. It does not disable thermal management. Neither flag restarts a service stopped by a previous invocation.

The script requires `adb` on the Windows PATH, an authorized device connection, support for `adb root`, and writable CPU frequency paths. It does not specify a device serial with `adb -s`, so it cannot reliably select the intended hub when multiple ADB devices are connected.

## What its verification actually proves

- Governor read-back is compared with the literal string `performance`. A mismatch sets the script's `FAIL` variable.
- Frequency read-back is compared with `1512000`. The printed `Hz` label is incorrect: CPUFreq exposes this value in **kHz**, so the expected value represents **1.512 GHz**. A mismatch prints a warning but does not set `FAIL`. Depending on the driver, this file may describe a requested frequency rather than a direct physical clock measurement. See the [CPUFreq attribute documentation](https://www.kernel.org/doc/html/latest/admin-guide/pm/cpufreq.html).
- When thermal stopping is enabled, any line returned by `ps | grep thermal` is treated as a failure. This is a broad text match, not an exact service-state check, and may include unrelated matching processes or the filter itself.
- The script does not check ADB command exit codes or require each read to return a value. A failed command or empty output can leave the inner verification loop unexecuted and `FAIL` unchanged. Consequently, “ALL CHECKS PASSED” is not sufficient evidence that the requested changes succeeded.
- There is no explicit success/failure exit code for automation, temperature test, or before/after robot benchmark.

## Persistence and restoration

The script saves neither the original governors nor the previous service state, and provides no undo mode. It makes runtime writes and does not install a boot-time configuration. Persistence across reboot is not verified here; inspect the device after restarting rather than assuming its state. Exact restoration requires knowing the original governor values and whether the thermal service was running beforehand.

## Relationship to our thread layout

Core pinning controls where a thread may run; the governor controls requested CPU frequency within policy limits. This batch file does not pin threads, change the TaskManager layout, or measure control-loop performance. It is a device-level configuration tool from the sibling project, not an optimization automatically applied by this robot software.

To evaluate its intended benefit, compare the same robot workload with the original settings and with governor-only changes while preserving thermal management. Measure control-cycle variation, planner duration, vision overruns, and temperature over sustained runs. The file's existence and a frequency read-back do not establish a measured performance improvement.
