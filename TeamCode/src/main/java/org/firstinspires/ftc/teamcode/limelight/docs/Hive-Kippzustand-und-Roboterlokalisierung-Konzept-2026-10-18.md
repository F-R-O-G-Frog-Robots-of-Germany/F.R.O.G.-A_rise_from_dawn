# Hive-Kippzustand und Roboterlokalisierung mit Limelight

**Ziel:** Den Zustand einer bestimmten Hive-Cell, ihren kontinuierlichen Kippwinkel und eine daraus geometrisch begründete Roboterpose schätzen, ohne die Messung mit der Eigenbewegung des Roboters zu verwechseln.

Stand: 18. Oktober 2026. **Geometrisches Konzept, keine Implementierung.** Dieses Dokument liegt ausschließlich im Limelight-Ordner. Es führt keine Pipeline, Kamerakonfiguration, Posekorrektur, Competition-Anbindung oder Hardwareaktion aus. Der Roboter ist noch nicht gebaut; Montagewerte und reale Messgüte bleiben offen.

**Ergänzung nach Rückmeldung:** Gesucht ist vor allem eine Korrektur kleiner Odometriefehler, keine vollständige Neulokalisierung ohne Vorwissen. Eine ursprüngliche Pose mit wenigen Inches/Grad Unsicherheit ist deshalb eine wertvolle Eingabe. Auf-/Abwärtsklassifikation und Winkelgeschwindigkeit gehören nicht zum erforderlichen Ergebnis. Die vorgeschlagene abwechselnde Positions-/Heading-Korrektur wurde separat [synthetisch untersucht](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/docs/Hive-Pose-Heading-Alternation-Synthetischer-Test-2026-10-18.md).

**Klarstellung des Besitzers:** Die im Code als Hive-Positionen bezeichneten Punkte sind gewünschte Schusspositionen. Sie sind weder Hive-Umrisse noch vermessene Drehachsen, Tagpositionen oder Öffnungsmittelpunkte. Aus diesen Punkten wird hier keine physische Geometrie abgeleitet. Auch eine zufällige Übereinstimmung mit später berechneten Koordinaten wäre kein unabhängiger Beleg.

**Neuere Untersuchung:** Für das inzwischen präzisierte Ziel X/Y/Heading ist ein expliziter Hive-Winkel keine notwendige Ausgabe und kein zwingender Zwischenschritt. Aus vollständiger relativer Tagpose, Tag-zu-Achse-Geometrie und Schwerkraft lassen sich direkt ein virtueller fester Achsenpunkt und die vorzeichenbehaftete Achsenrichtung bestimmen. Das funktioniert geometrisch auch in Zwischenstellungen und ohne Roboterbewegung zwischen Bildern. Der [Single-Frame-/MegaTag-Bericht](Hive-Single-Frame-Localization-and-MegaTag2-2026-10-18.md) enthält die Herleitung, den Vergleich mit MegaTag2, einen möglichen MegaTag1-Objektkartenansatz und synthetische Pixelversuche. Die unten erhaltene Winkelrechnung ist eine alternative Herleitung, keine zusätzliche Pflicht vor jeder Posekorrektur.

## 1. Ergebnis der Untersuchung

Die bekannte Kippbahn ist nutzbar. Ein identifizierter Punkt am Hive bewegt sich auf einer durch die Mechanik bestimmten Kreisbahn um dessen Drehachse. Seine Position und seine Orientierung hängen von einem gemeinsamen Winkel ab.

**Kamerahöhe, Sichtfeld und ein einzelner Zielmittelpunkt im Bild reichen aber nicht aus, um gleichzeitig den Kippwinkel und die Roboterposition eindeutig zu bestimmen.** Ein höherer Zielpunkt in größerer Entfernung kann unter demselben Bildwinkel erscheinen wie ein niedrigerer Punkt in geringerer Entfernung.

Für den vollständigen Ansatz ist deshalb die **relative 3D-Pose eines bekannten Tags** besonders geeignet: Sie enthält metrische Translation und Orientierung. Zusammen mit einem Modell der Drehachse, der Kameramontage und der Schwerkraftrichtung des Roboters lässt sich daraus entweder der Hive-Winkel mit anschließender Roboterpose bestimmen oder der Winkel direkt über feste Achsenmerkmale eliminieren. Die unten erhaltene Winkelherleitung wird ausdrücklich von den fertigen Limelight-Funktionen unterschieden; die direkte Variante steht im neueren Bericht.

Eine zweite Möglichkeit ist eine unabhängige Roboterpose zur Aufnahmezeit: Dann kann man den Hive-Winkel an dessen projizierte Kippbahn anpassen. Dieser Weg liefert jedoch keine unabhängige Roboterlokalisierung aus derselben einzelnen Winkelmessung.

Für kleine Drift muss nicht alles neu bestimmt werden: Eine gewichtete Schätzung kann Kameradaten mit der vorhandenen Odometrie kombinieren. Dabei werden Position und Heading als unsichere Größen behandelt. Abwechselnde bedingte Updates können dieselbe feste Zielfunktion minimieren wie eine gemeinsame Anpassung. Die Odometrie-Eingabe bleibt während dieser inneren Iterationen unverändert; ein daraus bereits berechnetes Zwischenergebnis ist keine zusätzliche unabhängige Messung. Vorwissen ermöglicht eine brauchbare Schätzung, auch wenn die Kamera allein keine eindeutige Lösung liefert.

## 2. Offizielle Ausgangsdaten

Geprüft wurden das [vollständige Competition Manual TU05](https://ftc-resources.firstinspires.org/ftc/game/manual), insbesondere §9.6 und §9.9 sowie Abbildungen 9-9, 9-10 und 9-15. Die [offizielle Materialseite](https://ftc-resources.firstinspires.org/ftc/game) nennt TU05 vom 15. Oktober 2026. Zeichnungen wurden visuell geprüft. Die nominalen Drehachsenmittelpunkte und Achsenrichtungen wurden zusätzlich aus der offiziellen STEP-Baugruppe nachvollzogen; vollständige Achse-zu-Tag-Transformationen sind noch nicht extrahiert.

| Bestätigte Geometrie | Bedeutung für dieses Konzept |
|---|---|
| Pro Allianz ein Hive mit zwei Cells und zwei stabilen Stellungen | Zustand an einer eindeutig benannten Cell festmachen. |
| Vier Tags je Cell-Unterseite; IDs 30–45 insgesamt | Diese Tags bewegen sich mit. Keine statischen Feldanker voraussetzen. |
| Drehachsenhöhe nominal 43,95 in | Höhe über Tiles, keine Taghöhe. |
| Abbildung 9-10 zeigt 30° | Bezug zur Mechanik definieren; kein direkt übernehmbarer SDK-Eulerwinkel. |
| Untere/obere Kante der oberen Öffnung: 53,5 / 65,6 in | Öffnungskanten, keine Tagmittelpunkte. |
| Tag-Schwarzquadrat: 3,25 in, Familie 36h11 | Metrischer Maßstab der Tagmessung. |

Quelle dieser Tabelle: [Manual, §9.6/§9.9](https://ftc-resources.firstinspires.org/ftc/game/manual#page=69). Die [Field Acceptance Checklist, Revision 26-27.2, Punkt 1.13](https://ftc-resources.firstinspires.org/ftc/field/field-check) erlaubt für die untere Kante der aufgerichteten Öffnung 53,5 ± 0,50 in. Nominalmaße sind daher kein Beleg für eine exakt identische Geometrie auf jedem Feld.

Eine Ebene durch einen Tag, die Cell-Öffnung und die Drehachse sind unterschiedliche geometrische Objekte. Zwischen ihnen gehören vollständige, mitrotierende Abstände und Orientierungen ins Modell.

## 3. Unabhängige Teilberechnungen aus der Zeichnung

Die folgenden Rechnungen verwenden ausschließlich die gezeichneten Maße und geometrische Definitionen, keine Schussposition aus dem Code.

### 3.1 Die physischen Drehachsen im eigenen Feldrahmen

Zunächst wird ein eigener rechtshändiger Feldrahmen definiert: Ursprung am Feldmittelpunkt auf den Tiles, `+x` aus Publikumssicht nach rechts, `+y` vom Publikum weg, `+z` nach oben. Dieser Rahmen ist unabhängig von den Bezeichnungen der Schusspositionen.

Im [offiziellen STEP-Feldmodell](https://ftc-resources.firstinspires.org/ftc/field/field-cad-step) wurden die beiden mittig um ihre lokale Achse aufgebauten `am-5881`-Distanzstücke und deren Baugruppenplatzierungen geprüft. Die Achsen sind parallel zur Feld-`x`-Richtung. Der Abstand ihrer Mittelpunkte beträgt 0,6477 m, also 25,50 in. Bei einer zentrierten Aufteilung folgt unabhängig vom Code:

```text
halber Abstand = 25.50 / 2 = 12.75 in

roter Achsenbezugspunkt:  H_red  = (-12.75, 0, 43.95) in
blauer Achsenbezugspunkt: H_blue = (+12.75, 0, 43.95) in
normierte Achsenrichtung: a     = (1, 0, 0)
```

Das sind **eigene abgeleitete Nominalkoordinaten**, keine direkt tabellierten offiziellen Feldkoordinaten und keine Feldkalibrierung. Winzige Exportversätze wurden auf die nominale Symmetrie und Höhe gerundet. Die Winkelfreiheit beider Hives bleibt getrennt, auch wenn die geometrischen Drehachsen kollinear sind.

Nur falls man einen gleich ausgerichteten Eckursprung mit nominell 72 in Abstand zum Feldzentrum wählt, werden daraus beispielsweise `(59.25, 72, 43.95)` und `(84.75, 72, 43.95)`. Diese Rechnung beschreibt die physischen Achsenbezugspunkte. Sie macht aus einem im Code auf derselben `x`-Linie gelegenen Schusspunkt keinen Tag- oder Öffnungspunkt. Für eine spätere Nutzung in FROG muss die Rahmenausrichtung ausdrücklich bestätigt werden.

Zur Nachvollziehbarkeit: Im STEP-Export ist `y` vertikal. Die ausgelesenen Platzierungen in Metern waren ungefähr Blau `(0.324038319, 1.116322380, 0)` und Rot `(-0.323661681, 1.116322380, 0)`; die übergeordneten Pivot-Platzierungen waren identisch ausgerichtet. Diese Exportachsen dürfen nicht unverändert als die oben definierten Feldachsen gelesen werden. SHA-256 der untersuchten STEP-Datei: `05B35961C7DF847741031F00FDA73DDD068537F809E92A11B1CD59A94BCC8331`.

### 3.2 Der Tagstreifen als eigenes Messobjekt

Für die in Abbildung 9-15 bemaßte Anordnung wird ein lokales Sticker-Koordinatensystem gewählt: `u` nach rechts entlang des Streifens, `v` nach oben im aufrecht betrachteten Sticker und `n = u × v`. Der Ursprung liegt auf dessen gemeinsamer Tagmittellinie zwischen den beiden inneren Tags.

Damit ergeben sich die Tagmittelpunkte in Inches:

```text
äußerer linker Tag:  (-6.50, 0, 0)
innerer linker Tag:  (-2.75, 0, 0)
innerer rechter Tag: (+2.75, 0, 0)
äußerer rechter Tag: (+6.50, 0, 0)
```

Eigene Folgerungen aus den Abständen: Die äußere Tagbasis beträgt 13,00 in, die innere 5,50 in. Die Ecken eines einzelnen Schwarzquadrats liegen jeweils 1,625 in vom eigenen Mittelpunkt entfernt. Diese metrische Struktur kann die gemeinsame Pose des Streifens bestimmen; bloße Bildmittelpunkte würden einen großen Teil dieser Information verwerfen. Die konkrete Tag-ID und Druckorientierung müssen jeweils dem aktuellen Layout zugeordnet werden. Die kleine Beispielbeschriftung der Abbildung ist dafür keine ID-Konfiguration.

### 3.3 Öffnungshöhe ist noch kein Hebelarm

Der geometrische Mittelpunkt der dargestellten oberen Öffnung liegt vertikal bei:

```text
(53.5 + 65.6) / 2 = 59.55 in
59.55 - 43.95 = 15.60 in über der Drehachsenhöhe
```

Das bestimmt **keinen vollständigen Vektor von der Achse zur Öffnung**. In einem vereinfachten Schnittmodell gilt bei 30° beispielsweise:

```text
0.5 * r_y + 0.8660254 * r_z = 15.60 in
```

Dabei sind `r_y` und `r_z` die Komponenten dieses Vektors in der definierten Referenzstellung. Eine Gleichung bestimmt diese beiden Unbekannten nicht. Insbesondere wäre `15.60 / sin(30°)` nur dann ein Hebelarm, wenn `r_z = 0` unabhängig bestätigt wäre. Diese Annahme wird hier nicht gemacht.

Die fehlende vollständige Achse-zu-Tag-/Öffnungsgeometrie muss aus CAD oder einer geometrischen Vermessung kommen. Ein Foto des Manuals ist keine maßstäbliche Ersatzmessung. Neben dem STEP-Modell ist das [offiziell verlinkte Onshape-Feld-CAD](https://cad.onshape.com/documents/a355e772e3d24813de7852ee/w/f106353168f1f92100b81259/e/95d1e1e442b4138cccaf2d73) ein Einstieg. Die Pivotprüfung aus Abschnitt 3.1 ersetzt die noch fehlenden Tag- und Öffnungstransformationen nicht.

## 4. Was das vorhandene Projekt bereits liefert

| Vorhandener Baustein | Einordnung |
|---|---|
| [VisionPose](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/VisionPose.java) | Zweidimensionale Feldpose in Inches, Heading gegen den Uhrzeigersinn in Radiant. Für die Zwischenrechnung werden zusätzlich 3D-Transformationen benötigt. |
| [LimelightCalibration](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/LimelightCalibration.java) | Bestehende Projektion auf Bodenobjekte. `project()` darf nicht für erhöhte Hive-Tags wiederverwendet werden. |
| [LimelightGeometry](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/LimelightGeometry.java) | Montage-Defaults und Sichtfeldwerte; keine vermessene zukünftige Kameramontage. |
| [Limelight.Result](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/Limelight.java) | Bewahrt derzeit unter anderem aggregierte Winkel und eine Feldpose auf. Vollständige einzelne Fiducial-Transformationen gehören noch nicht zu diesem Snapshot. |
| [LimelightWorker](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/LimelightWorker.java) | Vorhandener Kameraeigentümer mit zeitgestempelten Veröffentlichungen; ein späterer isolierter Schätzer könnte solche Daten konsumieren. |

Die geometrischen Hive-Parameter werden unabhängig davon definiert. Weder Schuss- noch Fahrpunkte liefern die benötigten CAD-Abstände.

## 5. Warum die Höhenrechnung allein mehrdeutig bleibt

In einem senkrechten Schnitt, ohne seitlichen Zielversatz, lautet die bekannte Entfernungsgleichung:

$$
d = \frac{h_{\mathrm{Ziel}}-h_{\mathrm{Kamera}}}{\tan(\beta)}.
$$

`d` ist hier die horizontale Entfernung im Schnitt; `β` ist der tatsächliche Höhenwinkel des Sehstrahls gegenüber dem Boden. Die [Limelight-Anleitung zur Entfernungsschätzung](https://docs.limelightvision.io/docs/docs-limelight/tutorials/tutorial-estimating-distance) setzt eine bekannte Zielhöhe und eine bekannte Montage voraus. Beim kippenden Ziel ist die Zielhöhe selbst eine Funktion des gesuchten Winkels.

**Reines Rechenbeispiel, keine tatsächliche Hive-Geometrie:** Eine horizontale Achse liegt auf 43,95 in, ein beobachteter Modellpunkt besitzt einen frei angenommenen Radius von 20 in. Bei Winkeln von ±14,4775° erhält er Höhen von 38,95 beziehungsweise 48,95 in. Bei einer ebenfalls nur angenommenen Kamerahöhe von 10 in und einem Höhenwinkel von 20° gilt:

| Modellstellung | Zielhöhe | Passende Entfernung | Beobachteter Höhenwinkel |
|---|---:|---:|---:|
| Erste mögliche Stellung | 38,95 in | 79,539 in | 20° |
| Zweite mögliche Stellung | 48,95 in | 107,014 in | 20° |

Beide Möglichkeiten erfüllen dieselbe Höhenmessung. Das Sichtfeld entscheidet nur, ob der Strahl sichtbar ist. Es liefert keine zusätzliche Entfernungsmessung.

Auch `tx` löst das Problem im Allgemeinen nicht vollständig: Bei bekannter Kamerahöhe und bekanntem Heading bleiben Roboter-`x`, Roboter-`y` und Hive-Winkel als drei Unbekannte. Ein Zielmittelpunkt liefert zwei Winkel. Eine zeitliche Spur hilft nur zusammen mit zusätzlicher unabhängiger Bewegung/Orientierung oder weiteren Bildmerkmalen; das wiederholte Beobachten derselben Mehrdeutigkeit macht sie nicht automatisch eindeutig.

## 6. Die mechanische Kippbahn als Modell

Die folgenden Gleichungen sind eine eigene geometrische Herleitung.

Wir definieren einen Punkt `H_W` auf der Drehachse und deren normierten Richtungsvektor `a`. Der Index `W` bezeichnet ein festes Feldkoordinatensystem. `R_a(α)` ist eine Rotation um diese Achsenrichtung um den Kippwinkel `α`.

Für einen Punkt mit bekanntem Referenzabstand `r_0` gilt:

$$
q_W(\alpha)=H_W+R_a(\alpha)r_0.
$$

Für die Orientierung eines starr befestigten Tags gilt mit der Referenzorientierung `B`:

$$
R_{WT}(\alpha)=R_a(\alpha)B.
$$

`α = 0` ist eine ausdrücklich definierte geometrische Referenz, nicht automatisch eine stabile Stellung. Erst wenn die gewählte Referenz die symmetrische Mittelstellung beschreibt, ist eine nominelle Interpretation der Endlagen als ungefähr `−30°` und `+30°` sinnvoll. Die tatsächlich erlaubten Grenzen und die Seitennamen sind Modellparameter.

Für Rot und Blau existieren getrennte Achsmodelle und getrennte Winkel. Die Bewegung einer Allianz sagt nichts über den aktuellen Winkel des anderen Hives aus.

## 7. Bevorzugter Ansatz: relative Tag-Pose und Schwerkraft

### 7.1 Benötigte Pose statt eines einzelnen Bildpunkts

Die Limelight bietet pro Fiducial kamera-relative 3D-Transformationen, unter anderem über `getTargetPoseCameraSpace()`. Sie sind im [offiziellen FTC-Programmierleitfaden](https://docs.limelightvision.io/docs/docs-limelight/apis/ftc-programming) beschrieben. Ob die installierte Pipeline/Firmware die benötigten Daten in brauchbarer Qualität liefert, muss später separat geprüft werden.

Hier wird eine gültige vollständige Messung `T_CT` angenommen. Sie enthält die Tagposition und die Tagorientierung relativ zur Kamera. Die metrische Entfernung stammt aus bekannten Tagabmessungen und den abgebildeten Ecken, nicht allein aus dem Sichtfeld.

Die Notation lautet durchgehend: **`T_AB` transformiert Koordinaten aus Rahmen B nach A.**

- `W`: festes Feldkoordinatensystem.
- `R`: Roboterrahmen mit festgelegtem Bezug zum Boden.
- `C`: Kamerarahmen.
- `T`: gewählter Tag- oder gemeinsam kalibrierter Clusterrahmen.

Alle folgenden Rotationsmatrizen werden erst nach der Umrechnung in konsistente rechtshändige Rahmen verwendet.

### 7.2 Den Kippwinkel aus der Orientierung ermitteln

Die IMU-Lageschätzung liefert die normierte Schwerkraftrichtung `g_R` im Roboterrahmen zur Bildaufnahmezeit. Gemeint ist die aus der Orientierung bestimmte Richtung, nicht ein ungeprüfter roher Beschleunigungsvektor während einer Fahrt: Dieser enthält zusätzlich die Eigenbeschleunigung. Eine angenommene exakt waagerechte Roboterlage kann die Messung im Rechenmodell ersetzen, muss dann aber ausdrücklich als Annahme gekennzeichnet werden.

Die Kameramontage liefert `R_RC`, die beobachtete Tagorientierung `R_CT`. Damit wird die Schwerkraft in den Tagrahmen umgerechnet:

$$
g_C=R_{RC}^{T}g_R,\qquad g_T=R_{CT}^{T}g_C.
$$

Das Achsmodell sagt voraus, wie die Feld-Schwerkraft `g_W = (0,0,−1)` im Tagrahmen aussehen müsste. Der gesuchte Winkel minimiert:

$$
\alpha^*=\arg\min_{\alpha\in[\alpha_{\min},\alpha_{\max}]}
\left\|R_{WT}(\alpha)^Tg_W-g_T\right\|^2.
$$

Bei einer horizontalen Drehachse verändert das Kippen die Orientierung zur Schwerkraft deutlich. Eine vertikale Achse wäre durch Schwerkraft allein nicht beobachtbar. IMU-Yaw ist für diese Winkelschätzung nicht erforderlich; ein unabhängig bekanntes Heading ist dennoch eine gute Kontrolle der späteren Roboterpose.

Für das ideale starre Modell gibt es eine geschlossene Lösung. Mit `B = R_WT(0)` und normierter Achse `a`:

$$
v=Bg_T,\qquad
p=g_W-a(a\cdot g_W),\qquad
q=v-a(a\cdot v),
$$

$$
\alpha=\operatorname{atan2}\big(a\cdot(q\times p),\ p\cdot q\big).
$$

Das Vorzeichen folgt der definierten Achsenrichtung und Referenz. Die Lösung wird auf den zulässigen Winkelbereich abgebildet. Bei kleinen Längen von `p` oder `q`, widersprüchlichen Messungen oder mehreren plausiblen Posekandidaten gibt es keine akzeptierte Winkelschätzung. Werte außerhalb der Mechanik werden nicht einfach an einen Anschlag geklemmt.

### 7.3 Aus dem Winkel die Roboterpose berechnen

Das mechanische Modell liefert nun die vollständige Tagpose im Feld `T_WT(α)`. Die Kameramontage `T_RC` enthält auch den Abstand zum Roboterbezugspunkt. Dann gilt:

$$
\boxed{T_{WR}=T_{WT}(\alpha)\;T_{CT}^{-1}\;T_{RC}^{-1}.}
$$

Die Bewegung wird dadurch dem geometrischen Tagmodell zugeordnet. Aus dem verbleibenden Verhältnis ergibt sich die Roboterpose. Eine statische Feldkarte mit einer unveränderlichen Tagposition würde diese Unterscheidung gerade verlieren.

Vor einer Veröffentlichung werden insbesondere Roboterhöhe, Roll/Pitch, zulässiger Feldbereich und gegebenenfalls unabhängiges Heading/Odometrie geprüft. Eine aus denselben Eingangsdaten berechnete Größe darf dabei nicht als zusätzliche unabhängige Messung gewertet werden.

**Diese Kette ist ein vorgeschlagener eigener Schätzer. Sie ist keine Behauptung, dass eine vorhandene `botpose` den kippenden Hive bereits so behandelt.** Eine kamera-relative Tagpose benötigt die unbewegte Feldposition des Tags nicht; eine fertige Feldpose aus einer statischen Karte dagegen schon.

## 8. Mehrere Tags, Cluster und die Öffnung

Die [offizielle FTC-Clusterbeschreibung](https://ftc-docs.firstinspires.org/en/latest/tech_tips/tech-tips/tech-tip-apriltag-clusters/tech-tip-apriltag-clusters.html) beschreibt SDK-12-Cluster mit einem virtuellen Bezugspunkt an der Cell-Öffnung. Sie erläutert außerdem eine Roll-Heuristik zur Zielauswahl. Das betrifft den FTC-`AprilTagProcessor`; daraus folgt keine automatische identische Clusterunterstützung der Limelight.

Wenn einzelne Tags geliefert werden, wird deren bekannter starrer Abstand zum eigenen gemeinsamen Rahmen `K` verwendet:

$$
T_{CK}=T_{CT_i}\;T_{KT_i}^{-1}.
$$

Mehrere Tags derselben Cell liefern dadurch Messungen desselben Rahmens. Für die spätere Schätzung sind eine gemeinsame Anpassung an die gemessenen Bildecken oder eine robuste Konsistenzprüfung sinnvoll. Positionen blind mitteln und Eulerwinkel arithmetisch mitteln wäre keine geeignete gemeinsame Posebestimmung. Alle Tags eines solchen flachen Streifens bleiben koplanar: Mehr Tags helfen gegen Verdeckung und Rauschen, garantieren aber keine eindeutige Lösung in jeder Ansicht.

Die Öffnung bekommt einen eigenen festen Transformationsabstand zum Cell-/Tagrahmen. Ihre Position muss nicht mit einem sichtbaren Tagzentrum übereinstimmen. Das [Limelight-Konzept eines 3D-Point-of-Interest](https://docs.limelightvision.io/docs/docs-limelight/pipeline-apriltag/apriltag-3d) zeigt, dass ein Zielpunkt relativ zum Tag definiert werden kann; das ersetzt weder die Achsgeometrie noch eine eigene Winkelschätzung.

Die genannte FTC-Heuristik ist kein kontinuierlicher physischer Hive-Winkel. Ein Limelight-Eulerfeld einfach als `ftcPose.roll` auszulegen, würde unterschiedliche Koordinatenkonventionen vermischen.

## 9. Koordinaten, Kamerawinkel und Sichtfeld

Für die eigene Rechnung werden rechtshändige Rahmen mit den lokalen Achsen **vorwärts, links, oben** verwendet. Die [Limelight-Dokumentation](https://docs.limelightvision.io/docs/docs-limelight/pipeline-apriltag/apriltag-coordinate-systems) beschreibt die optischen Kameraachsen als **rechts, unten, vorwärts**. Für einen Kameravektor ist die Umrechnung deshalb:

```text
vorwärts =  optisches Z
links    = -optisches X
oben     = -optisches Y
```

Orientierungen benötigen ebenfalls einen korrekten Basiswechsel auf beiden Seiten einer Transformation. Eulerreihenfolgen und Zielrahmen sind Teil des API-Adapters, keine austauschbaren Zahlenfelder.

Für Winkelstrahlen gilt im vorhandenen Projekt `tx` nach rechts und `ty` nach oben. Ein zum optischen Hauptpunkt bezogener Strahl hat im eigenen Kamerarahmen proportional die Komponenten:

```text
(vorwärts, links, oben) = (1, -tan(tx), tan(ty))
```

Der Vektor wird normalisiert und durch die vollständige Kameramontage gedreht. Das bestehende Projekt definiert positiven Kamerapitch nach unten. In der zweidimensionalen Schnittrechnung wäre der Höhenwinkel daher `β = ty − cameraPitchDown`, nicht unbesehen die Summe zweier positiver Winkel. Seitlicher Versatz, Roll und beliebige Montage werden über 3D-Rotationen berücksichtigt.

Die [Limelight-API](https://docs.limelightvision.io/docs/docs-limelight/apis/complete-networktables-api) unterscheidet Crosshair-Winkel und Winkel relativ zum optischen Hauptpunkt. Für geometrische Strahlen gehören der kalibrierte Hauptpunkt beziehungsweise entzerrte Bildkoordinaten zur Messung. Eine verschobene Zielmarke im UI ist keine physische Kameradrehung.

Sichtfeldwerte helfen bei der Planung der Montage und bei Sichtbarkeitsprüfungen. Für eine mittige Ansicht ohne Roll muss der Höhenwinkel zur optischen Achse innerhalb des halben vertikalen Sichtfelds liegen. Die komplette Tagfläche muss ausreichend im Bild liegen, nicht nur ihr Mittelpunkt. Ein zu steiler oder verdeckter Tag kann daher `UNKNOWN` ergeben, obwohl der geometrische Hive-Winkel existiert.

Die Umrechnung vom eigenen Feldrahmen nach `VisionPose` wird ausdrücklich festgelegt. Ein Ursprungstransfer allein reicht nur dann, wenn auch die Achsenrichtungen bereits übereinstimmen. Schusspositionen werden zur Festlegung dieses Basiswechsels nicht benutzt.

## 10. Optionales Hive-Ergebnis; erforderliche Roboterpose

Für das aktuelle Lokalisierungsziel genügt eine zusammenhängende X/Y/Heading-Schätzung mit Aufnahmezeit und Qualität. Die folgende Zustandsanzeige beschreibt die ursprüngliche zusätzliche Hive-Diagnose. Sie ist weder Voraussetzung für die neuere direkte Poseberechnung noch ein Grund, auf Endlagen zu warten.

„Oben“ bezieht sich immer auf eine benannte Cell. Ein bloßes `hiveIsUp` ist mehrdeutig. Das Ergebnis identifiziert Allianz, Hive und die konkrete Cell/Seite.

| Information | Vorgesehene Bedeutung |
|---|---|
| `UPPER` | Gewählte Cell liegt nahe ihrer definierten oberen Endstellung. |
| `LOWER` | Gewählte Cell liegt nahe ihrer unteren Endstellung. |
| `INTERMEDIATE` | Gültig gemessener Winkel zwischen den Endstellungen. |
| `UNKNOWN` | Keine ausreichende gültige Messung. Das ist keine dritte physische Lage. |
| Kippwinkel und gegebenenfalls Roboterpose | Kontinuierliches geometrisches Ergebnis mit Bezug auf dieselbe Bildaufnahme. |
| `AMBIGUOUS`, `STALE`, `INVALID`, `VALID` | Messqualität zusätzlich zum physischen Zustand. |

Eine eigene Klassifikation als steigend/fallend wird hier nicht benötigt. Sie wäre erst für eine spätere Bewegungsprognose oder Schusszeitplanung interessant. Nur die hier beschriebene winkelbasierte Variante benötigt den Winkel zur Aufnahmezeit; die direkte Achsenvariante eliminiert ihn. Beide benötigen keine Winkelableitung. Eine geometrische Endlage allein behauptet dabei keinen mechanischen Stillstand.

Für die Anzeige der Endlagen sind getrennte Eintritts-/Austrittsschwellen sinnvoll, damit Rauschen den Zustand nicht ständig umschaltet. Konkrete Schwellen bleiben Kalibrierparameter.

Bei Tagverlust bleibt die letzte bestätigte Messung mit ihrem alten Zeitstempel sichtbar, wird aber als veraltet markiert. Eine fortgeschriebene Prognose ist ein separates Ergebnis. Eine feste Animationsdauer darf keine neue Messung vortäuschen.

## 11. Zeitstempel, Latenz und Eigenbewegung

Ein Verlauf zeitgestempelter Winkel und Öffnungspositionen kann später eine Diagnose ergänzen. Für die aktuelle Posekorrektur werden weder Bewegungsrichtung noch eine nachverfolgte Kippspur benötigt.

Wichtig ist der **Aufnahmezeitpunkt** des Bildes. Die IMU-Neigung und eine optionale Odometrieprüfung müssen zu diesem Zeitpunkt passen. Kamera-, SDK- und Java-Zeitbasen werden dafür eindeutig umgerechnet; Latenzen werden nicht doppelt abgezogen. Das wiederholte Lesen desselben Frames ist keine weitere Beobachtung und darf keine vermeintliche Bewegungsbestätigung liefern.

Mit Eigenbewegung wird die beobachtete Tagpose über `T_RC` und die zeitlich passende Roboterorientierung verarbeitet. Eine spätere aktuelle Neigung in eine ältere Bildmessung einzusetzen würde einen Winkelversatz erzeugen.

Smoothing und Ausreißerbehandlung dürfen die echte Kippbewegung nicht wegfiltern. Während schneller Rotation steigen Bewegungsunschärfe und zeitlicher Fehler. Das Ergebnis benötigt deshalb Zeitstempel, Herkunft und eine begründete Unsicherheit. Ein frei erfundener Confidence-Prozentwert wäre hier nicht hilfreich.

## 12. Benötigte Geometrie und Kalibrierwerte

| Eingabe | Wie sie festgelegt wird |
|---|---|
| Drehachsenposition und Richtung für beide Hives | Nominalmodell aus Abschnitt 3.1; Feldabweichungen und Basiswechsel bleiben zu berücksichtigen. |
| Winkelreferenz und zulässige Endlagen | Mechanikmodell und klare Zuordnung der beiden Cells. |
| Achse → Cell → Tagrahmen → Öffnung | Vollständige starre Transformationen aus Geometrie; keine Schusspunkte. |
| Tagidentität, metrische Größe und Druckorientierung | Aktuelles Taglayout und verwendete Erkennungskonfiguration. |
| Kamera-Intrinsik und Verzerrung | Kalibrierung des konkreten Kameramodus. |
| Roboter → Kamera, Position und Orientierung | Spätere tatsächliche Montage. Die derzeitigen Defaults sind keine Messung. |
| Schwerkraftrichtung zur Aufnahmezeit | IMU beziehungsweise ausdrücklich angenommene Bodenparallelität. |
| Bildzeit und Datenalter | Definierte Zeitbasis und Framezuordnung. |

Besonders wichtig: Ein Codepunkt `SHOOT_IN_...`, ein Punkt zum Anfahren und ein Zielpunkt des Shooters sind keine Quelle für die erste oder dritte Tabellenzeile.

## 13. Qualitätsprüfung und Mehrdeutigkeiten

Ein planarer Tag kann in ungünstigen Ansichten mehr als eine plausible Posehypothese liefern. Die Winkelrechnung löst solche Hypothesen nicht automatisch auf. Kandidaten werden gegen Mechanik, Schwerkraft, Roboterhöhe, Sichtbarkeit und zeitliche Kontinuität geprüft. Bleiben mehrere plausible Lösungen, lautet das Ergebnis `AMBIGUOUS`.

Mögliche Ablehnungsgründe sind unter anderem:

- Falsche oder unbekannte Tag-/Cell-Zuordnung.
- Zu kleine, abgeschnittene oder stark verzerrte Tagfläche.
- Zu großer Reprojektionsfehler, soweit Bildecken verfügbar sind.
- Widerspruch zwischen Tags desselben starren Streifens.
- Unplausible Roboterhöhe oder Orientierung nach der Transformationskette.
- Winkel außerhalb des mechanischen Bereichs oder sprunghafter Wechsel ohne passende Bewegung.
- Veraltete Frames oder fehlende zeitlich passende Neigung.

Ein Tag einer anderen Cell oder Allianz wird seinem eigenen Modell zugeordnet. Die Auswahl des größten sichtbaren Tags allein ist keine geometrische Identifizierung. Bei wechselnder Sicht auf zwei Cells desselben Hives müssen beide Messungen denselben gemeinsamen Kippwinkel ergeben, nachdem ihre Referenztransformationen berücksichtigt wurden.

## 14. Gedachte isolierte Datenverarbeitung

Die folgende Skizze beschreibt die ursprüngliche winkelbasierte Variante und ist kein kompilierbarer Implementierungsauftrag. Für die inzwischen bevorzugte direkte X/Y/Heading-Berechnung ersetzt der neuere Bericht die beiden Winkelschritte durch die Rekonstruktion des festen Achsenpunkts und der Achsenrichtung; Zustand und Winkel sind dann keine erforderlichen Ausgaben:

```text
frischer Kamera-Snapshot mit einzelner relativer Tagpose
  -> Tag-ID einem physischen Cell-/Achsenmodell zuordnen
  -> API-Koordinaten in die eigenen 3D-Rahmen umrechnen
  -> Neigung/Schwerkraft zur Bildaufnahmezeit zuordnen
  -> Winkelkandidaten aus Tagorientierung und Achsmodell bestimmen
  -> für jeden Kandidaten T_WR und Öffnungsposition berechnen
  -> Hypothesen geometrisch prüfen und Mehrdeutigkeit erhalten
  -> Zustand, Kippwinkel, gegebenenfalls Roboterpose, Zeitstempel und Qualität veröffentlichen
```

Ein späterer reiner Schätzer könnte innerhalb des Limelight-Pakets Eingabedaten konsumieren und immutable Ergebnisse liefern. Er müsste selbst weder Kameraeigentümer sein noch einen zusätzlichen Worker starten. Er dürfte auch nicht eigenständig Motoren, Shooter oder Pedro ansprechen. Die bereits gewünschte Architektur des gemeinsamen wichtigen Kontrollloops bleibt damit vereinbar.

Eine spätere Anbindung wäre eine gesonderte Entscheidung. Dieses Konzept führt insbesondere keine automatische Posekorrektur oder Schussfreigabe ein.

## 15. Bereits ohne Roboter überprüft und weiter offen

Die geschlossene Winkelgleichung und die Posekette wurden für dieses Dokument numerisch mit **100 synthetischen, rauschfreien Konfigurationen** geprüft. Der Aufbau variierte Hive-Winkel, Roboterposition und Heading und enthielt eine schräg montierte Kamera sowie kleinen Roboter-Roll/Pitch. Die daraus erzeugte relative Tagmessung wurde wieder zurückgerechnet.

Größte beobachtete Rückrechnungsabweichungen:

```text
Hive-Winkel:               1.67e-16 rad
Roboterpositionskomponente: 3.55e-14 in
Rotationsmatrixeintrag:     4.44e-16
```

Das bestätigt Rechenrichtung und Vorzeichen der idealen Gleichungen bis auf Rundungsfehler. Es ist weder ein Kameratest noch eine Genauigkeitsprognose. Das mehrdeutige Höhenbeispiel aus Abschnitt 5 wurde ebenfalls numerisch nachgerechnet. Es wurden dafür keine Produktionsdateien oder Tests im Repo ergänzt.

Der neuere Single-Frame-Bericht ergänzt inzwischen 1.000 ideale Achsenrekonstruktionen und 576 gepaarte Pixelversuche je Ein-/Viertagvariante. Montagefehler, verzögerte IMU-Daten, Ansichten nahe der optischen Mehrdeutigkeit und widersprüchliche Cell-Messungen bleiben weitere Untersuchungsfelder. Für geometrische Eindeutigkeit darf ein idealer Einzeldurchlauf nicht mit einem Nachweis über alle Blickwinkel verwechselt werden.

Offen bleiben die vollständigen Achse-zu-Tag-/Öffnungstransformationen, die tatsächliche Kameramontage und die Verfügbarkeit/Qualität der benötigten relativen Posen im konkreten Limelight-Modus. Reale Kalibrierung und Sichtbarkeits-/Genauigkeitsmessungen sind erst mit passender Hardware möglich. Diese Abhängigkeiten werden nicht durch geschätzte Produktionskonstanten ersetzt.

## Gewünschte Änderungen / Ergänzungen des Besitzers

- Bezeichnung der beiden Cells/Seiten: …
- Gewünschter geometrischer Referenzrahmen und Winkelnullpunkt: …
- Vorhandene CAD-Geometrie oder gewünschter Bezugspunkt der Öffnung: …
- Geplante Kameraposition und ob die Kamera starr am Chassis sitzt: …
- Priorität: Zustand erkennen, relativer Zielpunkt oder unabhängige Feldlokalisierung: …
- Weitere Änderungen am Konzept: …

---

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
