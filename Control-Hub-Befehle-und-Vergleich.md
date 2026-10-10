# Control Hub: Befehle, aktuelle Werte und Vergleichstest

Ziel: die verfügbaren Einstellungen selbst auslesen und zurücksetzbar ändern sowie zwei Control Hubs mit demselben einfachen Rechentest vergleichen.

**Aktueller Stand nach beiden vollständigen Tests:** Beide Hubs wurden mit `performance` und Mindest-/Maximaltakt 1,512 GHz gemessen. Zuletzt geprüft wurde jeweils der ursprüngliche CPU-Temperaturzielwert **95 °C** bei aktivem Temperaturschutz. Score: Hub A **15.610**, Hub B **16.000 Punkte**; B hatte in dieser Messung 2,5 % mehr Durchsatz. Messwerte und Ausgangskonfiguration stehen in [Control-Hub-Benchmark-2026-10-09.md](Control-Hub-Benchmark-2026-10-09.md). Die Tabelle unten beschreibt weiterhin die frühere Ausgangskonfiguration.

## 1. Tatsächlich ausgelesene Werte

Momentaufnahme vom **9. Oktober 2026, 14:07 Uhr, Europe/Berlin**. Takt, Temperatur und Speicherbelegung ändern sich laufend. Die Werte stammen direkt vom angeschlossenen Hub, nicht aus angenommenen Herstellerdaten.

| Eigenschaft | Ausgelesener Wert |
| --- | --- |
| ADB-Seriennummer | `b7b1bc5d1dc1e74a` |
| Modell | `Control Hub v1.0` |
| Android | `7.1.2` |
| Kernel | `3.10.245` |
| Build-Typ | `userdebug` |
| `ro.debuggable` / `ro.secure` | `1` / `1` |
| ADB-Root | Funktioniert; nach dem Auslesen wieder deaktiviert |
| SELinux | `Permissive` – ausgelesen, nicht von uns geändert |
| CPU-Treiber | `rockchip` |
| Gemeinsame CPU-Taktregelung | Kerne `0 1 2 3` |
| Governor | `interactive` |
| Verfügbare Governors | `conservative ondemand userspace powersave interactive performance` |
| Mindesttakt | `408000` kHz = **408 MHz** |
| Maximaltakt | `1512000` kHz = **1512 MHz / 1,512 GHz** |
| Momentaner Hardwaretakt | `1296000` kHz = **1296 MHz** |
| CPU-Temperatur | `55454` = ungefähr **55,454 °C** |
| Vom Kernel gemeldeter RAM | `981684` KiB, ungefähr **959 MiB** |
| Freier RAM bei der Aufnahme | `67760` KiB |
| Cache bei der Aufnahme | `536716` KiB; freier RAM allein beschreibt nicht den verfügbaren Speicher |
| Swap gesamt / frei | `520908` / `520908` KiB; zu diesem Zeitpunkt ungenutzt |
| zRAM-Größe | `533413888` Bytes = ungefähr **509 MiB** |
| `swappiness` | `100` |

Auswählbare CPU-Frequenzen in kHz:

```text
408000 600000 816000 1008000 1200000 1296000 1392000 1512000
```

Ausgelesene Werte des `interactive`-Governors:

| Parameter | Wert | Bedeutung |
| --- | --- | --- |
| `hispeed_freq` | `600000` | Bevorzugter höherer Takt, in kHz |
| `go_hispeed_load` | `99` | Lastschwelle für den Sprung zum hispeed-Takt |
| `min_sample_time` | `40000` | Zeitparameter in Mikrosekunden, hier 40 ms |
| `timer_rate` | `20000` | Abtastzeitparameter, hier 20 ms |
| `above_hispeed_delay` | `20000 1000000:80000 1200000:100000 1700000:20000` | Taktabhängige Verzögerungstabelle |
| `target_loads` | `70 600000:70 800000:75 1500000:80 1700000:90` | Taktabhängige Ziel-Lasttabelle |
| `io_is_busy` | `0` | I/O-Wartezeit wird nicht als ausgelastete CPU behandelt |
| `boost` | `0` | Governor-Boost ist aus |

Die Schwelle `1700000` in einer Governor-Tabelle bedeutet nicht, dass 1,7 GHz auswählbar sind. Maßgeblich ist die tatsächliche Frequenzliste des Treibers. [REV-Governor-Implementierung](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/drivers/cpufreq/cpufreq_interactive.c).

Build-Fingerprint und Toybox-Version wurden zusätzlich beim Funktionstest gelesen:

```text
FIRST/ch_v1_box/rk3328_box:7.1.2/NHG47K/rev05081324:userdebug/release-keys
Toybox: -a777abe3f668-android
```

## 2. PowerShell vorbereiten

Alle Befehle in diesem Abschnitt gehören ins **PowerShell-Terminal auf dem Laptop**. Der Hub braucht seine normale Stromversorgung; USB-C verbindet ihn mit dem PC.

```powershell
$adb = "C:\Users\pmkoe_eque6\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$hub = "b7b1bc5d1dc1e74a"
$project = "C:\Users\pmkoe_eque6\StudioProjects\F.R.O.G.-A_rise_from_dawn"

& $adb devices -l
```

Bei einem anderen Hub ersetzt du `$hub` durch dessen Seriennummer aus dieser Liste. Auch bei zwei gleichzeitig angeschlossenen Hubs wählt `-s $hub` das gewünschte Gerät eindeutig aus. [ADB-Dokumentation](https://developer.android.com/tools/adb).

Die vollständige, nur lesende Bestandsaufnahme ist ebenfalls vorbereitet:

```powershell
& "$project\tools\control-hub\Inspect-ControlHub.ps1" `
    -Serial $hub `
    -OutputPath "$env:TEMP\control-hub-snapshot.txt"
```

Einige Dateien sind ohne Root nicht lesbar. Der Inspector selbst fordert kein Root an.

## 3. Root-Zugang und Android-Terminal öffnen

```powershell
& $adb -s $hub root
& $adb -s $hub wait-for-device
& $adb -s $hub shell
```

Die folgenden Abschnitte 4 bis 6 werden **in dieser Android-Shell** ausgeführt. Root gibt dem Debug-Terminal die nötigen Rechte; dafür wurde kein Kernel geflasht und keine zusätzliche App installiert.

## 4. CPU, Temperatur und Speicher selbst auslesen

```sh
id
cpu=/sys/devices/system/cpu/cpu0/cpufreq

cat "$cpu/scaling_governor"
cat "$cpu/scaling_available_governors"
cat "$cpu/scaling_available_frequencies"
cat "$cpu/scaling_min_freq"
cat "$cpu/scaling_max_freq"
cat "$cpu/cpuinfo_cur_freq"
cat "$cpu/related_cpus"

cat /sys/class/thermal/thermal_zone0/type
cat /sys/class/thermal/thermal_zone0/temp

cat /proc/meminfo
cat /proc/swaps
cat /proc/sys/vm/swappiness
cat /sys/block/zram0/disksize
```

CPU-Werte sind in **kHz**. Der gemessene CPU-Temperaturknoten liefert Tausendstel Grad Celsius. zRAM-Größe ist in Bytes. Die `kB`-Werte aus `/proc/meminfo` entsprechen hier 1024-Byte-Einheiten.

`cpuinfo_cur_freq` liefert den vom Treiber gelesenen Hardwaretakt. `scaling_cur_freq` kann stattdessen den angeforderten Takt beschreiben. [Linux CPUFreq-Dokumentation](https://www.kernel.org/doc/html/latest/admin-guide/pm/cpufreq.html).

Governor-Parameter lesen:

```sh
gov=/sys/devices/system/cpu/cpufreq/interactive

cat "$gov/hispeed_freq"
cat "$gov/go_hispeed_load"
cat "$gov/min_sample_time"
cat "$gov/timer_rate"
cat "$gov/above_hispeed_delay"
cat "$gov/target_loads"
cat "$gov/io_is_busy"
cat "$gov/boost"
```

## 5. Performance-Modus einschalten und zurücksetzen

Zuerst den ursprünglichen Governor im selben Terminal sichern:

```sh
oldGovernor=$(cat "$cpu/scaling_governor")
echo "$oldGovernor"
```

Performance-Modus einschalten und prüfen:

```sh
echo performance > "$cpu/scaling_governor"
cat "$cpu/scaling_governor"
cat "$cpu/cpuinfo_cur_freq"
```

`performance` fordert den aktuellen erlaubten Maximaltakt an. Thermische Begrenzungen bleiben wirksam. Ein höherer Takt als die vorhandene Grenze wird dadurch nicht eingeführt. [Bedeutung des Governors](https://www.kernel.org/doc/html/latest/admin-guide/pm/cpufreq.html#performance).

Zurücksetzen, solange du noch in derselben Shell bist:

```sh
echo "$oldGovernor" > "$cpu/scaling_governor"
cat "$cpu/scaling_governor"
```

Wenn die Shell geschlossen wurde und die Variable fehlt, war der ausgelesene Ausgangswert dieses Hubs `interactive`:

```sh
cpu=/sys/devices/system/cpu/cpu0/cpufreq
echo interactive > "$cpu/scaling_governor"
```

## 6. Optional: Taktgrenzen innerhalb der vorhandenen Liste

Für einen kontrollierten Vergleich kannst du einen vorhandenen niedrigeren Maximaltakt verwenden. Beispiel **1,2 GHz** auf diesem Hub:

```sh
oldMin=$(cat "$cpu/scaling_min_freq")
oldMax=$(cat "$cpu/scaling_max_freq")

echo 1200000 > "$cpu/scaling_max_freq"
cat "$cpu/scaling_max_freq"
```

Das Beispiel setzt voraus, dass der Mindesttakt nicht über 1,2 GHz liegt; bei der dokumentierten Aufnahme liegt er bei 408 MHz. Es verändert nur die obere Grenze. Mit `performance` fordert der Governor dann diese Grenze an.

Zurücksetzen:

```sh
echo "$oldMax" > "$cpu/scaling_max_freq"
echo "$oldMin" > "$cpu/scaling_min_freq"
```

Die ausgelesenen Ausgangsgrenzen dieses Hubs sind `408000` und `1512000`. Wenn du zwischenzeitlich andere Grenzen gesetzt hast, muss beim Wiederherstellen immer `min <= max` gelten.

Falls die Shell-Variablen nach dem Schließen verloren sind, kannst du die dokumentierten Ausgangsgrenzen dieses Hubs so wiederherstellen:

```sh
cpu=/sys/devices/system/cpu/cpu0/cpufreq
echo 1512000 > "$cpu/scaling_max_freq"
echo 408000 > "$cpu/scaling_min_freq"
```

Für echtes Übertakten oberhalb **1,512 GHz** reicht ein größerer Wert in dieser Datei nicht aus: der Treiber bietet keine höhere Frequenz an. Dafür müsste die Kernel-/Frequenzkonfiguration untersucht und angepasst werden. Hier sind keine Befehle zum Flashen, Verändern von Spannungen oder Abschalten von Temperaturgrenzen enthalten.

### Fester Maximaltakt und Temperaturziel dieses Hubs

Diese Befehle gehören in die Android-Shell mit Root. So wurde Hub A auf einen festen angeforderten Takt von 1,512 GHz gestellt:

```sh
cpu=/sys/devices/system/cpu/cpu0/cpufreq
echo 1512000 > "$cpu/scaling_max_freq"
echo 1512000 > "$cpu/scaling_min_freq"
echo performance > "$cpu/scaling_governor"
cat "$cpu/cpuinfo_cur_freq"
```

Die tatsächlich vorhandene Rockchip-Temperaturschnittstelle auslesen:

```sh
cat /sys/dvfs/cpu_temp_target
cat /sys/dvfs/cpu_temp_enable
```

Auf Hub A waren die ursprünglichen und zuletzt wiederhergestellten Werte `cpu:95` und `cpu:1`. Der Zielwert verwendet ganze **Grad Celsius**, anders als der Temperaturmessknoten. Wiederherstellung des ursprünglichen Zielwerts:

```sh
echo 95 > /sys/dvfs/cpu_temp_target
cat /sys/dvfs/cpu_temp_target
cat /sys/dvfs/cpu_temp_enable
```

Der Schutz bleibt aktiviert; der Zielwert ist keine universelle Schwelle aller möglichen Taktbegrenzungen. Die Schnittstelle ist im [REV-Kernelcode](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/arch/arm/mach-rockchip/dvfs.c) beschrieben. Für einen anderen Hub erst dessen Werte auslesen. Beim anschließenden Wiederherstellen von `interactive` auch den Mindesttakt wieder auf den gesicherten Ausgangswert setzen: bei Hub A `408000` kHz. Ein Governor-Wechsel allein hebt den festen Mindesttakt nicht auf.

## 7. Android-Shell verlassen und ADB-Root deaktivieren

In der Android-Shell:

```sh
exit
```

Danach wieder in PowerShell:

```powershell
& $adb -s $hub unroot
& $adb -s $hub wait-for-device
& $adb -s $hub shell id
```

Die Ausgabe sollte wieder `uid=2000(shell)` enthalten. **`unroot` setzt den Governor nicht zurück.** Diesen vorher ausdrücklich wiederherstellen. Es wird keine Einstellung für den nächsten Boot gespeichert.

## 8. Einfacher Vergleich von zwei Control Hubs

Ja: Derselbe feste Rechentest kann auf beiden Hubs laufen, einzeln oder mit beiden am PC. Ein Expansion Hub ist dafür kein zweiter Control Hub; dieser Test benötigt Android und ADB.

Ich habe [benchmark-controlhub.sh](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/tools/control-hub/benchmark-controlhub.sh) vorbereitet. Er berechnet SHA-1 über einen Datenstrom aus `/dev/zero`:

- Standardmäßig **64 MiB pro Lauf**, ein Aufwärmlauf und **fünf Messläufe**.
- Die Zeitmessung findet direkt im Hub statt; USB/WLAN-Übertragungszeit ist nicht Teil des Ergebnisses.
- Keine große Testdatei wird geschrieben: der Datenstrom läuft über eine Pipe zum Hash-Programm.
- Das Skript ändert keine Governors, Taktgrenzen oder Motorbefehle und benötigt kein Root.
- SHA-1 wird hier nur als feste Rechenaufgabe verwendet.

Die nötigen Werkzeuge sind auf beiden Hubs tatsächlich vorhanden. Ein kurzer Funktionstest mit 2 MiB und einem Messlauf war erfolgreich; die gemessenen **0,14 s** betreffen nur diesen Funktionstest. Anschließend wurden beide Hubs mit je 64 MiB und fünf Messläufen vollständig getestet; die Ergebnisse stehen in der oben verlinkten Vergleichsdatei.

### Faire Bedingungen

Auf beiden Hubs dieselbe Datenmenge und Laufanzahl verwenden. Kein OpMode sollte während des Rechentests laufen. Gleiche CPU-Einstellung, ähnliche Starttemperatur und vergleichbare Hintergrundlast wählen. Android-/Toybox-Versionen mitprotokollieren: unterschiedliche Hash-Implementierungen können Unterschiede verursachen. Für einen Vergleich der Hardware sollten die Softwarestände übereinstimmen.

Für den ersten Vergleich beide Hubs in ihrer dokumentierten Ausgangskonfiguration testen. Wenn du die CPU-Wirkung isolieren willst, kannst du anschließend beide unter `performance` mit einer gemeinsamen, auf beiden zulässigen Taktgrenze vergleichen. Nicht voraussetzen, dass Hub B dieselben Grenzen oder Root-Rechte hat: vorher auslesen.

### Hub A: Skript übertragen und Test ausführen

**In PowerShell**, außerhalb der Android-Shell:

```powershell
$hubA = "b7b1bc5d1dc1e74a"

& $adb -s $hubA push "$project\tools\control-hub\benchmark-controlhub.sh" `
    /data/local/tmp/frog-cpu-benchmark.sh

& $adb -s $hubA shell sh /data/local/tmp/frog-cpu-benchmark.sh 64 5 2>&1 |
    Tee-Object -FilePath "$env:TEMP\hub-A-interactive.txt"
```

### Hub B: derselbe Test

Die Seriennummer von Hub B zuerst mit `devices -l` feststellen und den Platzhalter ersetzen:

```powershell
& $adb devices -l
$hubB = "SERIENNUMMER_VON_HUB_B"

& $adb -s $hubB push "$project\tools\control-hub\benchmark-controlhub.sh" `
    /data/local/tmp/frog-cpu-benchmark.sh

& $adb -s $hubB shell sh /data/local/tmp/frog-cpu-benchmark.sh 64 5 2>&1 |
    Tee-Object -FilePath "$env:TEMP\hub-B-interactive.txt"
```

Du kannst die Hubs auch nacheinander anschließen. Beide müssen nicht gleichzeitig am Laptop hängen.

### Ergebnisse lesen

Pro Messlauf erscheinen unter anderem:

```text
RUN 1
<Prüfsumme>  -
real     <Sekunden>
user     <Sekunden>
sys      <Sekunden>
```

- **`real`** ist die verstrichene Zeit der vollständigen Rechenaufgabe. Kleiner ist schneller.
- `user` und `sys` beschreiben CPU-Zeit im Programm bzw. Kernel.
- Die Prüfsummen müssen bei derselben Datenmenge auf beiden Hubs übereinstimmen.
- Nutze den **Median der fünf `real`-Werte**: sortieren und den dritten Wert nehmen. Ein auffälliger einzelner Lauf darf nicht die Aussage bestimmen.

| Messung | Hub A | Hub B |
| --- | --- | --- |
| Android / Toybox | | |
| Governor / Taktgrenze | | |
| Starttemperatur | | |
| Lauf 1, `real` in s | | |
| Lauf 2 | | |
| Lauf 3 | | |
| Lauf 4 | | |
| Lauf 5 | | |
| Median in s | | |
| Temperatur nach letztem Lauf | | |

Bei 64 MiB pro Lauf ist der Durchsatz `64 / Median` MiB/s. Falls Hub A 4,0 s und Hub B 3,6 s benötigen würde, hätte B 10 % weniger Laufzeit beziehungsweise rund 11,1 % mehr Durchsatz. Diese Zahlen sind ein Rechenbeispiel, keine gemessenen Hub-Ergebnisse.

Wenn die Unterschiede klein sind, die komplette Messung wiederholen und die Reihenfolge A/B tauschen. Unterschiedliche Temperatur, Hintergrundaktivität und Governor-Verhalten können einen kleinen Effekt überdecken.

### Derselbe Hub: interactive gegen performance

Zuerst die fünf Läufe mit `interactive` protokollieren. Anschließend Abschnitt 5 anwenden, die Android-Shell mit `exit` verlassen und `unroot` ausführen. **Der Governor bleibt dabei auf `performance`**, sodass der Benchmark in beiden Fällen als normaler ADB-Shell-Benutzer läuft.

Dann:

```powershell
& $adb -s $hubA shell sh /data/local/tmp/frog-cpu-benchmark.sh 64 5 2>&1 |
    Tee-Object -FilePath "$env:TEMP\hub-A-performance.txt"
```

Danach über `root` und die Android-Shell wieder `interactive` herstellen, mit `cat` prüfen und ADB-Root deaktivieren. Der Testlauf kann den Hub erwärmen; vor der Gegenmessung eine vergleichbare Starttemperatur abwarten.

### Was dieser einfache Test aussagt

Er vergleicht die Leistung der SHA-1-Aufgabe einschließlich Speicher-Kopier- und Pipe-Kosten unter dem jeweiligen Android-System. Er misst nicht die Motor-/Sensorlatenz, Java-Regelung, Pathing-Leistung oder RAM-Taktrate. Ein anhaltender Rechentest kann beide Governors schnell zum Maximaltakt bringen; ein gleiches Ergebnis schließt Unterschiede bei kurzen CPU-Lastspitzen nicht aus.

Für einen zusätzlichen Praxisvergleich kannst du dasselbe Robot-Controller-APK mit derselben Routine und identischer Hardware auf beiden Hubs verwenden und die vorhandenen `Work ms` / `Cycle ms` / `Hz` vergleichen. Dafür ist kein neuer OpMode nötig. Dieses Ergebnis beantwortet die Frage nach der Geschwindigkeit deines Robotercodes besser als der isolierte Rechentest.

## 9. Testskript optional wieder vom Hub entfernen

Nur die von uns übertragene Datei wird entfernt:

```powershell
& $adb -s $hubA shell rm /data/local/tmp/frog-cpu-benchmark.sh
& $adb -s $hubB shell rm /data/local/tmp/frog-cpu-benchmark.sh
```

## Änderungen, die du möchtest

- Hub-B-Seriennummer / Softwarestand:
- Gewünschte Testeinstellung:
- Ergebnisse / weitere Fragen:

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
