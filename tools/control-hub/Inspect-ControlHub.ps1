[CmdletBinding()]
param(
    [string]$AdbPath,
    [string]$Serial,
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'

if (-not $AdbPath) {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCommand) {
        $AdbPath = $adbCommand.Source
    } else {
        $AdbPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    }
}
if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
    throw 'ADB was not found. Supply -AdbPath with the Android SDK adb.exe path.'
}

$deviceLines = @(& $AdbPath devices)
if ($LASTEXITCODE -ne 0) { throw 'ADB could not list devices.' }
$devices = @($deviceLines | ForEach-Object {
    if ($_ -match '^([^\s]+)\s+device$') { $Matches[1] }
})
if (-not $Serial) {
    if ($devices.Count -eq 0) {
        throw 'No authorized ADB device is connected. Power the Control Hub and connect its USB-C port.'
    }
    if ($devices.Count -ne 1) { throw 'Multiple devices are connected. Select the Control Hub with -Serial.' }
    $Serial = $devices[0]
}
if ($Serial -notmatch '^[A-Za-z0-9_.:-]+$' -or $Serial -notin $devices) {
    throw 'The selected serial is not an authorized connected ADB device.'
}

# This probe only reads properties and kernel interfaces. It does not request root or change settings.
$probeScript = @'
show_node() {
    [ -e "$1" ] || return 0
    printf '%s = ' "$1"
    cat "$1" 2>/dev/null || printf '<unreadable>\n'
    if [ -w "$1" ]; then
        printf '  shell access check: writable (SELinux/driver may still deny a write)\n'
    else
        printf '  shell access check: not writable\n'
    fi
}
printf '\nDEVICE / ACCESS\n'
id
uname -r
for property in ro.product.model ro.product.device ro.build.version.release ro.build.type ro.debuggable ro.secure; do
    printf '%s = ' "$property"
    getprop "$property"
done
getenforce 2>/dev/null
printf '\nCPU POLICIES (frequency values in kHz)\n'
show_node /sys/devices/system/cpu/online
for policy in /sys/devices/system/cpu/cpufreq/policy* /sys/devices/system/cpu/cpu[0-9]*/cpufreq; do
    [ -d "$policy" ] || continue
    for name in related_cpus scaling_driver scaling_governor scaling_available_governors scaling_available_frequencies scaling_min_freq scaling_max_freq scaling_cur_freq cpuinfo_min_freq cpuinfo_max_freq cpuinfo_cur_freq; do
        show_node "$policy/$name"
    done
done
printf '\nINTERACTIVE GOVERNOR TUNABLES\n'
for directory in /sys/devices/system/cpu/cpufreq/interactive /sys/devices/system/cpu/cpufreq/policy*/interactive /sys/devices/system/cpu/cpu[0-9]*/cpufreq/interactive; do
    [ -d "$directory" ] || continue
    for name in hispeed_freq go_hispeed_load min_sample_time timer_rate above_hispeed_delay target_loads io_is_busy boost; do
        show_node "$directory/$name"
    done
done
printf '\nDEVFREQ (device names must identify DDR versus GPU; units are driver-specific)\n'
for directory in /sys/class/devfreq/*; do
    [ -d "$directory" ] || continue
    for name in name governor available_governors available_frequencies cur_freq min_freq max_freq; do
        show_node "$directory/$name"
    done
done
printf '\nTHERMAL (raw values; units must be verified for each driver)\n'
for directory in /sys/class/thermal/thermal_zone*; do
    [ -d "$directory" ] || continue
    show_node "$directory/type"
    show_node "$directory/temp"
done
for directory in /sys/class/thermal/cooling_device*; do
    [ -d "$directory" ] || continue
    show_node "$directory/type"
    show_node "$directory/cur_state"
done
printf '\nMEMORY / SWAP\n'
cat /proc/meminfo
cat /proc/swaps
show_node /proc/sys/vm/swappiness
show_node /sys/block/zram0/disksize
printf '\nREAD-ONLY PROBE COMPLETE\n'
'@

$startInfo = New-Object System.Diagnostics.ProcessStartInfo
$startInfo.FileName = (Resolve-Path -LiteralPath $AdbPath).Path
$startInfo.Arguments = "-s $Serial shell sh -s"
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
$startInfo.RedirectStandardInput = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true
$startInfo.StandardInputEncoding = New-Object System.Text.UTF8Encoding($false)
$process = New-Object System.Diagnostics.Process
$process.StartInfo = $startInfo
try {
    if (-not $process.Start()) { throw 'Could not start the ADB probe.' }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $process.StandardInput.Write($probeScript.Replace("`r`n", "`n") + "`n")
    $process.StandardInput.Close()
    if (-not $process.WaitForExit(30000)) {
        $process.Kill()
        throw 'The read-only ADB probe timed out.'
    }
    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    if ($process.ExitCode -ne 0) { throw "ADB probe failed: $stderr" }
    $report = "Captured $(Get-Date -Format o)`n$stdout"
    if ($stderr) { $report += "`nDIAGNOSTIC STDERR`n$stderr" }
    if ($OutputPath) {
        $absoluteOutput = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)
        [System.IO.File]::WriteAllText($absoluteOutput, $report, (New-Object System.Text.UTF8Encoding($false)))
        Write-Output "Saved probe to $absoluteOutput"
    }
    Write-Output $report
} finally {
    $process.Dispose()
}
