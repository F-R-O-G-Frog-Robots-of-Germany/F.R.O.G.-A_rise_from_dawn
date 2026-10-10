# Control-Hub-Vergleich: 9. Oktober 2026

Ziel: beide Control Hubs mit derselben Rechenaufgabe und demselben Score vergleichen.

## Ergebnisse beider Hubs

Hub A: `b7b1bc5d1dc1e74a`. Hub B: `33fd93bd1c45bf95`. Je ein Aufwärmlauf, danach fünf Messläufe mit jeweils 64 MiB Nulldaten durch SHA-1. Zeitmessung direkt auf dem Hub, als normaler ADB-Shell-Benutzer.

| Messwert | Hub A | Hub B |
| --- | --- | --- |
| Governor | `performance` | `performance` |
| CPU-Mindest-/Maximaltakt | Beide `1512000` kHz | Beide `1512000` kHz |
| Lauf 1 | 4,02 s | 4,05 s |
| Lauf 2 | 4,10 s | 4,00 s |
| Lauf 3 | 4,11 s | 4,09 s |
| Lauf 4 | 4,02 s | 3,99 s |
| Lauf 5 | 4,11 s | 4,00 s |
| Median | **4,10 s** | **4,00 s** |
| Durchsatz | **15,6098 MiB/s** | **16,0000 MiB/s** |
| Score | **15.610 Punkte** | **16.000 Punkte** |
| Temperatur beim Start | 62,083 °C | 63,333 °C |
| Temperatur nach letztem Lauf | 65,000 °C | 61,250 °C |
| Höchste protokollierte Temperatur | 65,384 °C | 64,166 °C |

**Score = 1.000 × Datenmenge in MiB / Median in Sekunden.** Höher ist schneller. Das ist unser eigener Vergleichsscore für diesen Test, kein standardisierter CPU-Benchmark. Beide Ergebnisse verwenden dieselbe Datenmenge, Laufanzahl und Formel.

Hub B hatte in dieser Messung **2,5 % mehr Durchsatz** beziehungsweise **2,44 % weniger Laufzeit**. Der Unterschied ist klein, die Einzelzeiten überlappen. Eine einzelne Testserie belegt deshalb keinen dauerhaften Hardwarevorteil. Dieser Test misst die SHA-1-Aufgabe einschließlich Pipe-/Speicherkosten; er misst keine Motor-, Sensor- oder Robotercode-Latenz. Die Wirkung des Performance-Modus gegenüber `interactive` wurde hier nicht gemessen.

Android `7.1.2`, Kernel `3.10.245`, Build-Fingerprint und Toybox-Version waren auf beiden Hubs identisch. Die Starttemperaturen lagen 1,25 °C auseinander. Du hast vor Hub B die Batterie gewechselt; Batteriespannung und Hintergrundlast wurden nicht gemessen. Hub B wurde erst bei `sys.boot_completed=1` getestet. Seine USB-/ADB-Unterbrechungen betrafen die Vorbereitung; die vollständige Testserie lief erfolgreich durch.

Alle zehn Prüfsummen stimmen mit einer unabhängig auf dem Laptop berechneten SHA-1-Prüfsumme von 64 MiB Nulldaten überein:

```text
44fac4bedde4df04b9572ac665d3ac2c5cd00c7d
```

Der angeforderte Takt lag bei allen protokollierten Messpunkten bei `1512000` kHz; vor und nach dem Test wurde auch der Hardwaretakt als `1512000` kHz gelesen. Die Temperatur wurde an den Messpunkten gelesen, nicht kontinuierlich aufgezeichnet.

## Temperaturregelung und aktueller Zustand

Ursprünglich meldete `/sys/dvfs/cpu_temp_target` **`cpu:95`**, bei aktivem Schutz (`cpu_temp_enable` = `cpu:1`). Auf die ursprüngliche Anweisung wurde der Zielwert auf 85 °C gesetzt. Nach deiner Korrektur wurde er wieder auf **95 °C** zurückgestellt und ausgelesen. Die fünf Messläufe fanden noch mit 85 °C statt; alle Temperaturmesspunkte lagen weit darunter. Es gibt deshalb keinen gemessenen Hinweis auf einen Einfluss dieser Änderung auf das Ergebnis.

Dieser Knoten ist der Zielwert der Rockchip-Temperaturregelung, keine Garantie, dass sämtliche möglichen Begrenzungen exakt an dieser Temperatur beginnen. Der veröffentlichte [REV-Kernelcode](https://github.com/REVrobotics/kernel-controlhub-android/blob/main/arch/arm/mach-rockchip/dvfs.c) beschreibt die Zielwert- und Enable-Schnittstellen. Abschaltgrenzen wurden nicht verändert.

Zuletzt geprüfter Zustand beider Hubs: Governor `performance`, Mindest-/Maximaltakt beide 1,512 GHz, CPU-Temperaturziel 95 °C, Temperaturschutz aktiv, ADB wieder ohne Root. Bei Hub B wurde der ursprüngliche Temperaturzielwert während der gesamten Messung beibehalten. Es wurde keine Konfiguration für den nächsten Neustart eingerichtet. 1,512 GHz ist auf beiden Hubs die vorhandene höchste Frequenz.

## Dateien und Wiederherstellung

- [Ausgangswerte](tools/control-hub/results/hub-A-baseline-2026-10-09.txt)
- [Unverändertes Messprotokoll](tools/control-hub/results/hub-A-performance-85C-2026-10-09.txt)
- [Geprüfter Endzustand](tools/control-hub/results/hub-A-final-settings-2026-10-09.txt)
- [Score und Messwerte als JSON](tools/control-hub/results/hub-A-score-2026-10-09.json)
- [Hub B: Ausgangswerte](tools/control-hub/results/hub-B-baseline-2026-10-09.txt)
- [Hub B: Taktänderung mit Vorher-/Nachher-Werten](tools/control-hub/results/hub-B-configuration-2026-10-09.txt)
- [Hub B: unverändertes Messprotokoll](tools/control-hub/results/hub-B-performance-95C-2026-10-09.txt)
- [Hub B: geprüfter Endzustand](tools/control-hub/results/hub-B-final-settings-2026-10-09.txt)
- [Hub B: Score und Messwerte als JSON](tools/control-hub/results/hub-B-score-2026-10-09.json)
- [Befehle und Vergleichsanleitung](Control-Hub-Befehle-und-Vergleich.md)

Ursprüngliche Einstellungen dieses Hubs wiederherstellen, in einer Android-Shell nach `adb root`:

```sh
cpu=/sys/devices/system/cpu/cpu0/cpufreq
echo 408000 > "$cpu/scaling_min_freq"
echo 1512000 > "$cpu/scaling_max_freq"
echo interactive > "$cpu/scaling_governor"
echo 95 > /sys/dvfs/cpu_temp_target
cat "$cpu/scaling_governor"
cat "$cpu/scaling_min_freq"
cat "$cpu/scaling_max_freq"
cat /sys/dvfs/cpu_temp_target
cat /sys/dvfs/cpu_temp_enable
```

Danach `exit` und auf dem Laptop `adb -s b7b1bc5d1dc1e74a unroot` ausführen. Hub B hatte dieselben Ausgangseinstellungen; zum Wiederherstellen dort dieselben Shell-Befehle verwenden und bei ADB die Seriennummer `33fd93bd1c45bf95` einsetzen.

## Änderungen, die du möchtest

- Weitere Vergleichsmessungen:

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
