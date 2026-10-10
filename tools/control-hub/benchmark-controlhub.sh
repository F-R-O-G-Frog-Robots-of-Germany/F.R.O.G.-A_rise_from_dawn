#!/system/bin/sh
set -eu
set -o pipefail

sizeMiB=${1:-64}
runs=${2:-5}
for value in "$sizeMiB" "$runs"; do
    case "$value" in
        ''|*[!0-9]*) echo 'Usage: sh benchmark-controlhub.sh [MiB: 1-512] [runs: 1-20]' >&2; exit 1 ;;
    esac
done
if [ "$sizeMiB" -lt 1 ] || [ "$sizeMiB" -gt 512 ] || [ "$runs" -lt 1 ] || [ "$runs" -gt 20 ]; then
    echo 'Allowed ranges: 1-512 MiB and 1-20 runs.' >&2
    exit 1
fi
command -v dd >/dev/null
command -v sha1sum >/dev/null

show_value() {
    printf '%s=' "$1"
    if [ -r "$2" ]; then cat "$2"; else printf 'unreadable/unavailable\n'; fi
}
hash_stream() {
    dd if=/dev/zero bs=1048576 count="$sizeMiB" 2>/dev/null | sha1sum
}

printf 'test=SHA1 zero stream\nsizeMiB=%s\nruns=%s\n' "$sizeMiB" "$runs"
printf 'model='; getprop ro.product.model
printf 'android='; getprop ro.build.version.release
printf 'fingerprint='; getprop ro.build.fingerprint
printf 'kernel='; uname -r
printf 'identity='; id
printf 'toybox='; toybox --version
cpu=/sys/devices/system/cpu/cpu0/cpufreq
show_value governor "$cpu/scaling_governor"
show_value requestedFrequencyBeforeKHz "$cpu/scaling_cur_freq"
show_value temperatureBeforeRaw /sys/class/thermal/thermal_zone0/temp

printf 'warmup=1\n'
hash_stream >/dev/null
run=1
while [ "$run" -le "$runs" ]; do
    printf '\nRUN %s\n' "$run"
    time -p hash_stream
    show_value requestedFrequencyAfterRunKHz "$cpu/scaling_cur_freq"
    show_value temperatureAfterRunRaw /sys/class/thermal/thermal_zone0/temp
    run=$((run + 1))
done
printf '\nBENCHMARK COMPLETE\n'
