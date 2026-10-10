# Direct Control Hub tuning experiments

Goal: identify small, measurable gains from hardware and Android settings, with reversible experiments and evidence suitable for the Control Award.

## Findings

There are real settings to investigate beyond rearranging the Java code. The strongest candidates are CPU frequency policy, the intake sensors' I2C clock, and the Android control thread's scheduling priority. CPU and RAM overclocking are separate from these settings and remain unverified on the installed Hub.

| Candidate | What changes | Evidence | What still needs checking |
| --- | --- | --- | --- |
| CPU `performance` governor | Requests the current policy's maximum CPU frequency instead of waiting for demand-based ramp-up | REV's published kernel config includes `interactive` as the default and includes `performance` | Installed governors, permissions, factory cap, temperature, actual effect |
| I2C 100 → 400 kHz | Speeds up compatible sensors' bus transfers | Public FTC SDK setting; REV Color Sensor V3 explicitly supports both rates | Exact intake sensor models, shared-bus devices, wiring reliability |
| Modestly higher OpMode thread priority | Gives the control worker more scheduling share under contention | Public Android `Process.setThreadPriority()` API | Existing priority, permissions, scheduling delays |
| RAM/DDR frequency | Would change memory clock | REV kernel has platform DDR support | No verified stock user-facing RAM clock setting; do not assume one exists |

These are experiments, not claimed improvements. On 2026-10-09 the Hub was connected: ADB root works, the installed governor is interactive, and the allowed CPU range is 408–1512 MHz. No clock/governor settings have been changed. The measured values and German command guide are in [Control-Hub-Befehle-und-Vergleich.md](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/Control-Hub-Befehle-und-Vergleich.md).

## 1. Direct access through ADB

REV documents Android Debug Bridge access over USB and Wi-Fi. USB-C is the simplest route for the first inspection. Wireless ADB is available on port 5555 when the PC is connected to the Hub's network. [REV ADB guide](https://docs.revrobotics.com/duo-control/managing-the-control-system/android-studio-using-wireless-adb).

ADB is already installed on this PC at:

```text
C:\Users\pmkoe_eque6\AppData\Local\Android\Sdk\platform-tools\adb.exe
```

I prepared [Inspect-ControlHub.ps1](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/tools/control-hub/Inspect-ControlHub.ps1). It only reads device properties, CPU policies, governor tunables, devfreq devices, thermal nodes, and memory/swap information. It does not request root, write a device file, install anything, or modify robot code.

After powering the Hub from its normal battery and connecting its USB-C port, run this from the main project directory:

```powershell
& .\tools\control-hub\Inspect-ControlHub.ps1 -OutputPath "$env:TEMP\control-hub-baseline.txt"
```

If multiple Android devices are connected, select the Hub with `-Serial`. The script rejects missing, unauthorized, or ambiguous devices. Its shell write-access check is only an indication: SELinux or a driver may still refuse a write.

## 2. CPU governor: the first Android hardware experiment

REV publishes the Control Hub kernel source. Its build configuration selects `CONFIG_CPU_FREQ_DEFAULT_GOV_INTERACTIVE=y` and includes `CONFIG_CPU_FREQ_GOV_PERFORMANCE=y`. This establishes a concrete implementation to inspect, but does not prove which configuration is installed on your Hub. [REV kernel configuration](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/arch/arm64/configs/rockchip_smp_nougat_defconfig).

The Linux `performance` governor requests the highest frequency within the existing `scaling_max_freq` limit. It can remove governor ramp-up delay in bursty work. It does not create a higher maximum clock or disable thermal limits. If the robot already remains at its maximum frequency, there may be no gain. [Linux CPUFreq documentation](https://www.kernel.org/doc/html/latest/admin-guide/pm/cpufreq.html).

Relevant nodes, using the path actually reported by the inspector, are:

```text
/sys/devices/system/cpu/cpufreq/policy0/scaling_available_governors
/sys/devices/system/cpu/cpufreq/policy0/scaling_governor
/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq
/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq
/sys/devices/system/cpu/cpufreq/policy0/cpuinfo_cur_freq
```

Older layouts may expose the same policy through `/sys/devices/system/cpu/cpu0/cpufreq`. Multiple CPU paths can point to the same policy; do not change each as though it were an independent clock.

An initial practice-bench trial would preserve the factory min/max limits, save the original governor, select `performance`, measure the same workload, and restore the original governor. The following is a recipe for an ADB shell **after** the actual policy path and write permission have been verified. It has not been run:

```sh
p=/sys/devices/system/cpu/cpufreq/policy0
old=$(cat "$p/scaling_governor") || exit 1
available=$(cat "$p/scaling_available_governors") || exit 1
case " $available " in
    *" performance "*) ;;
    *) echo "performance is unavailable"; exit 1 ;;
esac
[ -w "$p/scaling_governor" ] || { echo "No write access"; exit 1; }
printf 'Original governor: %s\n' "$old"
trap 'printf "%s\n" "$old" > "$p/scaling_governor"' EXIT
trap 'exit 1' HUP INT TERM
printf '%s\n' performance > "$p/scaling_governor" || exit 1
cat "$p/scaling_governor"
sleep 30
# Normal exit restores the saved governor; verify it afterward.
```

The shell trap covers normal exit and handled signals; it is not a guarantee after a crash or lost device connection. Save the baseline on the PC and verify restoration. No boot/startup persistence is part of this experiment.

CPU frequency nodes are normally owner/root writable. ADB access alone does not imply root. `ro.build.type`, `ro.debuggable`, `ro.secure`, and `id` distinguish the cases. AOSP documents that production `user` builds refuse `adb root`, while debug builds may allow it. The inspector does not invoke it. [AOSP ADB privilege documentation](https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/main/docs/dev/root.md).

If this experiment helps but sustained maximum frequency increases temperature, inspect the existing interactive tunables such as `hispeed_freq`, `go_hispeed_load`, `min_sample_time`, and `timer_rate`. REV's kernel implements these. Change one at a time only after the governor comparison; there is no justified universal number to prescribe offline. [REV interactive governor implementation](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/drivers/cpufreq/cpufreq_interactive.c).

`scaling_cur_freq` can describe the requested frequency rather than a precise measurement of the hardware clock. Prefer `cpuinfo_cur_freq` when exposed and correlate with thermal/cooling state. Raw temperature units also depend on the driver.

## 3. A supported hardware setting: faster I2C

REV Color Sensor V3 supports Standard 100 kHz and 400 kHz I2C. [REV sensor specification](https://docs.revrobotics.com/rev-crossover-products/sensors/color-sensor).

I inspected the installed FTC Hardware 12.0.0 source, rather than assuming the current driver already selects the faster rate:

- `LynxI2cDeviceSynch.resetDeviceConfigurationForOpMode()` selects `STANDARD_100K`.
- `LynxI2cDeviceSynch.setBusSpeed()` exposes `FAST_400K` and sends the corresponding hub configuration command.
- `BroadcomColorSensorImpl`, used by `RevColorSensorV3`, does not select a faster bus rate during its initialization.
- `GoBildaPinpointDriver.doInitialize()` already selects `FAST_400K`. Leave Pinpoint/Pedro's behavior alone.

The intake retrieves only the generic `ColorRangeSensor` interface, so the repo does not establish the actual sensor model. If both devices are confirmed REV Color Sensor V3 on compatible buses, the existing INIT code can set their bus clocks once:

```java
import com.qualcomm.hardware.lynx.LynxI2cDeviceSynch;
import com.qualcomm.hardware.rev.RevColorSensorV3;

((LynxI2cDeviceSynch) ((RevColorSensorV3) colorSensorLow).getDeviceClient())
        .setBusSpeed(LynxI2cDeviceSynch.BusSpeed.FAST_400K);
((LynxI2cDeviceSynch) ((RevColorSensorV3) colorSensorHigh).getDeviceClient())
        .setBusSpeed(LynxI2cDeviceSynch.BusSpeed.FAST_400K);
```

This is a proposed snippet, not a committed change. Apply after hardware retrieval in each OpMode's INIT, because SDK resets can restore defaults. Every device sharing the affected bus must support the selected speed. Actual sensor/client types must match these casts.

The wire clock becomes four times faster, but an entire `getDistance()` call will not become four times faster: hub command latency, driver caching and sensor measurement time remain. The experiment should measure API-call duration and successful readings at both rates. This is the most straightforward supported hardware tweak found so far, and it uses neither a custom kernel nor a per-update guard.

## 4. Direct Android scheduling control

Android exposes the native worker-thread priority through `Process.getThreadPriority(Process.myTid())` and `Process.setThreadPriority(...)`. Lower Linux niceness values receive more scheduling share when threads compete. A modest trial could use `THREAD_PRIORITY_MORE_FAVORABLE` (-1), or -2 after inspecting the current priority. An already higher-priority worker should not be accidentally lowered to these values. [Android Process API](https://developer.android.com/reference/android/os/Process#setThreadPriority(int)).

Set this in the actual LinearOpMode worker, once before the measured loop, and restore the saved priority in `finally`. The API can reject the request if permissions do not allow it. This changes scheduling under contention; it does not raise clock speed. SDK communication and Driver Station workers must still get CPU time. No real-time or audio priority is proposed.

This is a smaller app-level experiment than a kernel change. It remains a candidate until a trace or measurements demonstrate scheduling delay.

## 5. RAM and overclocking

REV's kernel configuration includes Rockchip DDR support, but that does not establish a writable RAM governor or frequency node. The inspector enumerates devfreq devices without assuming a GPU node is RAM. No verified stock RAM overclock command was found.

Likewise, a frequency listed in a generic Rockchip/REV source tree is not proof that the installed Hub should run there. The first CPU experiment leaves the existing factory cap and voltage policy intact. Raising clocks above that cap or replacing the kernel is a different research project, with no gain or competition eligibility established here.

The current 2026–2027 manual's R706 restricts modifications to core control devices. I have not established an exception permitting kernel/clock modifications. Treat the governor investigation as a bench experiment until the current rule interpretation is resolved; public SDK sensor configuration is the clearer route. [FIRST competition manual, section 12.7](https://ftc-resources.firstinspires.org/ftc/archive/2027/game/cm-html/BIOBUZZ%20Competition%20Manual%20-%20V1.htm).

## Tomorrow's experiment and Control Award evidence

1. Capture the stock read-only snapshot and confirm both intake sensor models.
2. Select one change: compatible sensor bus speed first; CPU governor if available and accessible.
3. Compare baseline → changed → baseline with the same workload and comparable battery/temperature. Use repeated runs so warm-up is not mistaken for a gain.
4. Record the setting, actual clock/access evidence, loop or sensor-call timing, failed-read count, temperature, and relevant behavior such as RPM recovery or repeatable endpoint error.
5. Keep a setting only if the effect is repeatable, and document the rollback.

A small measured gain can make a useful engineering story. A settings change alone is weak Control Award evidence: the published criteria emphasize using external feedback to improve robot performance. Connect the experiment to a demonstrated control result, explain why it helped, and include its limitations. [FIRST Control Award criteria, section 6.3.7](https://ftc-resources.firstinspires.org/ftc/archive/2027/game/cm-html/BIOBUZZ%20Competition%20Manual%20-%20V1.htm).

## Changes you would like

- Control Hub OS version / sensor models:
- First experiment to run:
- Measurements or constraints:

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
