# Kleine Drift: Position und Heading abwechselnd korrigieren

**Ziel:** Prüfen, ob abwechselnde Positions- und Heading-Updates eine bereits ungefähr richtige Roboterpose verbessern können, und welche Informationen beziehungsweise Annahmen dafür erforderlich sind.

18. Oktober 2026. Ergänzung zum [Hive-Konzept](C:/Users/pmkoe_eque6/StudioProjects/F.R.O.G.-A_rise_from_dawn/TeamCode/src/main/java/org/firstinspires/ftc/teamcode/limelight/docs/Hive-Kippzustand-und-Roboterlokalisierung-Konzept-2026-10-18.md). Ausschließlich Untersuchung und Markdown; keine Competition-Anbindung oder Änderung an Produktionscode. Sämtliche Versuche sind synthetisch. Schusspositionen aus dem Code werden nicht als physische Hive-Geometrie verwendet.

## 1. Antwort auf die vorgeschlagene Idee

**Ja, die Idee ist als iterative Korrektur mit Odometrie-Vorwissen brauchbar.** Die frühere Erklärung war zu stark auf eine vollständige Neulokalisierung ohne ausreichende Information ausgerichtet. Bei wenigen Inches und Grad Drift liegt bereits eine wertvolle Ausgangsschätzung vor. Diese muss man nicht wegwerfen.

Abwechselnd einen Teil der Variablen festzuhalten und den anderen zu verbessern ist eine bekannte Optimierungsstrategie: Block-Koordinatenabstieg. Sie ist im [Überblick von Stephen Wright](https://arxiv.org/abs/1502.04759) beschrieben. Die wichtige Bedingung für den hier getesteten Ansatz ist, dass beide Updates dieselbe feste gewichtete Fehlerfunktion bearbeiten. Aus einer bedingt festgehaltenen Größe wird dadurch keine exakt bekannte Größe.

Die Versuche zeigen beides: Der gewichtete Ansatz verbessert die durchschnittliche Genauigkeit und kann zur gleichen Lösung wie eine gemeinsame Anpassung konvergieren. Eine ungewichtete Variante kann dagegen einen Headingfehler als Positionsfehler erklären und danach trotz falscher Pose keinen Kamerafehler mehr erkennen.

## 2. Auf-/Abwärtsbewegung ist hier nicht erforderlich

Die vorherige Aufnahme von `RISING`/`FALLING` war eine zusätzliche Idee für spätere Prognosen oder Schusszeitplanung. Für das aktuelle Ziel, die Pose geometrisch zu korrigieren, ist sie nicht nötig und wurde aus dem Kernkonzept entfernt.

Für die ursprüngliche winkelbasierte Transformationskette wird der **aktuelle Hive-Winkel zur Bildaufnahmezeit** benötigt. Das ist keine allgemeine Voraussetzung der Roboterlokalisierung: Der neuere [Single-Frame-/MegaTag-Bericht](Hive-Single-Frame-Localization-and-MegaTag2-2026-10-18.md) eliminiert ihn über den festen Achsenpunkt und die vorzeichenbehaftete Achsenrichtung aus der vollständigen relativen Tagpose. Dort werden X/Y/Heading direkt bestimmt; eine gemeinsame Pixelanpassung kann den Winkel alternativ als interne Hilfsvariable führen. Weder Bewegungsrichtung noch Winkelableitung noch das Warten auf eine Endlage sind erforderlich.

## 3. Was „abwechselnd“ hier genau bedeutet

Wir unterscheiden die **aktualisierte** Variable ausdrücklich von der festgehaltenen:

| Bezeichnung | Reihenfolge |
|---|---|
| `Heading → XY` | Aktuelles XY festhalten, Heading anpassen; dann neues Heading festhalten und XY anpassen. Das entspricht der zuerst beschriebenen Idee. |
| `XY → Heading` | Aktuelles Heading festhalten, XY anpassen; dann neues XY festhalten und Heading anpassen. |
| Gewichtet, ursprünglicher Prior fest | Gleiche bedingte Updates, aber beide enthalten weiterhin das ursprüngliche Odometrie-Vorwissen mit dessen Unsicherheit. |
| Gemeinsame Anpassung | XY und Heading als Variablen derselben Fehlerfunktion gemeinsam bestimmen. |

Der Prior ist die vor dieser Kameramessung vorhandene Pose. Er bleibt während der Rechenzyklen über dieses Bild unverändert. Das aktuelle numerische Zwischenergebnis darf sich ändern; die ursprüngliche Referenz und ihre Unsicherheit dürfen dabei nicht stillschweigend ersetzt werden.

## 4. Bereits wenige Grad und Inches reichen für Mehrdeutigkeit

Hier wird zunächst ein günstigeres Problem angenommen als das vollständige Hive-Problem: Ein Zielpunkt liegt exakt bekannt bei `L = (72, 0) in`. Seine relative metrische Position zum Roboter wird exakt als `v = (72, 0) in` gemessen. Der wahre Roboter steht bei `(0, 0)` mit Heading `0°`. Der Hive-Winkel ist bereits bekannt.

Für jede Headingannahme gilt:

$$
p=L-R(\psi)v.
$$

Bei `ψ = 3°` ergibt sich:

```text
x = 72 - 72*cos(3°) =  0.0986735 in
y =    -72*sin(3°) = -3.7681888 in
```

Diese falsche Pose hat einen Positionsfehler von **3,76948 in** und einen Headingfehler von **3°**, erzeugt aber exakt dieselbe relative Zielmessung. Der Fehler liegt damit genau in der vom Besitzer beschriebenen Größenordnung und benötigt keinen Sprung über das halbe Feld.

Mit der Reihenfolge `XY → Heading` kann zuerst diese passende falsche Position entstehen. Der folgende Heading-Schritt sieht dann bereits eine exakt passende Beobachtung und ändert nichts mehr. Wiederholungen erzeugen keine fehlende Information.

`Heading → XY` löst gerade dieses Beispiel, wenn das anfängliche XY tatsächlich exakt stimmt. Bei seitlichem Positionsfehler kann dieselbe Reihenfolge stattdessen einen Headingfehler erzeugen. Die Reihenfolge entscheidet dann, welcher Eingangsschätzung die ungeklärte Abweichung zugeschrieben wird.

Die metrische Zielmessung in diesem Beispiel ist bereits stärker als ein einzelnes `tx`/`ty`-Paar bei unbekanntem Hive-Winkel. Die Mehrdeutigkeit ist deshalb kein Nachteil, der erst aus einer besonders schwachen künstlichen Messung entsteht.

## 5. Getestete gewichtete Fehlerfunktion

Das vereinfachte ebene Modell verwendet bekannte Punkte `L_i`, beobachtete relative Vektoren `z_i`, Position `p = (x,y)` und Heading `ψ`. `R(ψ)` ist die zweidimensionale Rotationsmatrix.

Minimiert wird:

$$
J(p,\psi)=
\sum_i\frac{\|L_i-p-R(\psi)z_i\|^2}{\sigma_v^2}
+\frac{\|p-p_0\|^2}{\sigma_p^2}
+\frac{(\psi-\psi_0)^2}{\sigma_\psi^2}.
$$

`p_0, ψ_0` bleiben der ursprüngliche Prior. Die Sigmas sind Standardabweichungen, hier ausdrücklich angenommene Versuchsparameter. Inches und Radiant werden durch ihre jeweils passenden Unsicherheiten gewichtet; rohe Inches- und Winkelabweichungen einfach zu addieren würde willkürliche Einheiten vermischen.

Bei einer zusätzlichen unabhängigen Orientierungsmessung `ψ_obs` kommt ein weiterer Term hinzu:

$$
J_{\mathrm{Orientierung}}=\frac{(\psi-\psi_{\mathrm{obs}})^2}{\sigma_{\mathrm{obs}}^2}.
$$

Dies ist eine kleine Maximum-a-posteriori-Schätzung: Messung plus Vorwissen. Als Hintergrund beschreiben die [GTSAM-Einführung](https://gtsam.org/tutorials/intro.html) Odometrie, Priors und externe Messungen in einer gemeinsamen Schätzung und [Ceres](https://ceres-solver.readthedocs.io/latest/nnls_modeling.html#normalprior) einen festen gewichteten Prior. Es wird dadurch keine dieser Bibliotheken für FROG vorgeschrieben.

Der Versuch verwendet zur Vereinfachung getrennte skalare Unsicherheiten. Eine tatsächliche Odometrie kann korrelierte Positions- und Headingfehler besitzen; ein allgemeines Messmodell sollte deshalb auch diese Zusammenhänge über die gemeinsame Kovarianz berücksichtigen.

Für festes Heading besitzt das getestete XY-Update die geschlossene Form:

$$
p^*(\psi)=
\frac{\sum_i(L_i-R(\psi)z_i)/\sigma_v^2+p_0/\sigma_p^2}
{N/\sigma_v^2+1/\sigma_p^2}.
$$

Das Heading wird anschließend bei festem XY angepasst, oder umgekehrt. Die gemeinsame Referenzlösung eliminiert XY analytisch über diese Formel und minimiert die resultierende skalare Funktion des Headings. Beide Verfahren bearbeiten exakt dieselben Daten und dieselbe Zielfunktion.

## 6. Versuchsaufbau und Grenzen

Es wurden **384 Rausch-/Prior-Realisierungen je fester Geometrie** getestet, insgesamt vier Geometrie-/Informationsfälle. Das sind keine 384 unterschiedlichen Blickwinkel. Der wahre Roboter steht jeweils am Ursprung mit Heading null; die Messungen und Odometriefehler werden zufällig erzeugt. Zufallsseed: `20261018`.

| Größe | Angenommene Standardabweichung |
|---|---:|
| Ursprünglicher X- und Y-Fehler, je Komponente | 2 in |
| Ursprünglicher Headingfehler | 2° |
| Relative metrische Zielmessung, je Komponente und Punkt | 0,25 in |
| Zusätzliche unabhängige Orientierung, nur im entsprechenden Fall | 0,5° |

Die Fehler sind normalverteilt und unverzerrt. Alle Verfahren erhalten innerhalb eines Falls dieselbe Messung und denselben Prior. Zwischen den Geometriefällen werden neue Messrauschwerte gezogen; die Odometrie-Realisierungen bleiben gleich.

Bewusst günstige Annahmen: bekannter Hive-Winkel, exakt bekannte Punktgeometrie, bereits verfügbare metrische Zielvektoren, isotropes unabhängiges Messrauschen und gegebenenfalls unabhängige Orientierung. Es werden keine Bildecken, PnP-Mehrdeutigkeiten, Montagefehler, Latenzen oder Bewegungen simuliert. Bei echten Tags können Fehler korreliert und blickwinkelabhängig sein. Die Zahlen sind deshalb **keine Limelight-Genauigkeitsprognose**.

20 beziehungsweise 1000 Zyklen sind numerische Rechenzyklen über **dieselbe einzelne Bildmessung**, keine Folge neuer Kamerabilder. 1000 Zyklen wurden nur benutzt, um das Konvergenzverhalten zu prüfen. Sie sind keine Empfehlung für den Roboterloop.

## 7. Ergebnis mit nur einem bekannten Zielmittelpunkt

Der synthetische Zielpunkt liegt 72 in vor dem wahren Roboter. Positionsfehler werden als zweidimensionale euklidische Abweichung ausgewertet. RMSE bedeutet die Wurzel aus dem Mittel der quadrierten Fehler.

| Verfahren | Positions-RMSE | Heading-RMSE |
|---|---:|---:|
| Ursprüngliche Odometrie | 2,812 in | 2,027° |
| Ungewichtet, `XY → Heading`, 20 Zyklen | 2,597 in | 2,027° |
| Ungewichtet, `Heading → XY`, 20 Zyklen | 1,933 in | 1,525° |
| Fester gewichteter Prior, `Heading → XY`, 20 Zyklen | 1,678 in | 1,311° |
| Fester gewichteter Prior, `Heading → XY`, 1000 Zyklen | 1,542 in | 1,192° |
| Gemeinsame Anpassung desselben gewichteten Problems | 1,542 in | 1,192° |

Damit ist die Idee nicht nur grundsätzlich plausibel: In diesem definierten kleinen Driftmodell verbessert die gewichtete Variante beide durchschnittlichen Fehler. Ihre konvergierte Lösung stimmt mit der gemeinsamen Schätzung überein. Die fehlende geometrische Information wird dabei durch den statistischen Prior ergänzt, nicht durch die Wiederholung neu erzeugt.

Die Verbesserung gilt nicht für jeden einzelnen Versuch: Beim konvergierten gewichteten Einpunktfall wurde der tatsächliche Positionsfehler in **16,7 %** und der tatsächliche Headingfehler in **29,2 %** der Realisierungen größer als vorher. Das sind nachträglich mit der synthetischen Wahrheit bestimmte Werte, keine online nutzbaren Ablehnungsraten. Eine verrauschte Messung kann im Einzelfall ungünstig ausfallen, auch wenn die mittlere Genauigkeit steigt.

## 8. Zusätzliche unabhängige Information verändert das Ergebnis

Hier stehen ausschließlich die gewichteten gemeinsamen Lösungen; die konvergierte Alternation stimmt jeweils numerisch damit überein.

| Synthetische Informationslage | Positions-RMSE | Heading-RMSE |
|---|---:|---:|
| Ein Punkt | 1,542 in | 1,192° |
| Zwei Punkte mit 13-in-Querbasis | 1,161 in | 0,908° |
| Zwei Punkte mit 48-in-Querbasis | 0,545 in | 0,386° |
| Ein Punkt plus unabhängige Orientierung mit angenommenen 0,5° Rauschen | 0,676 in | 0,483° |

Die 48-in-Basis ist eine **hypothetische Vergleichsgeometrie**, kein behaupteter Abstand am Hive. Auch die 13-in-Basis wird hier nur als ideale Punktgeometrie geprüft. Zwei echte Tagmessungen liefern nicht automatisch unabhängige metrische Vektoren dieser Güte.

Die Verbesserung stammt hier aus zusätzlicher beziehungsweise besser verteilter Messinformation. Eine längere geometrische Basis kann Heading und Translation leichter auseinanderhalten. Die vollständige Tagorientierung verfolgt denselben Zweck über weitere Bildinformation. Beim beweglichen Hive muss dabei dessen eigene Orientierung berücksichtigt werden.

Über alle vier Fälle betrug der größte Unterschied zwischen 1000 gewichteten Alternationszyklen und der profilierten gemeinsamen Referenzlösung ungefähr **3,52e−7 in** beziehungsweise **2,83e−7°**. Das prüft die numerische Übereinstimmung der beiden Verfahren in diesen Fällen, nicht globale Konvergenz für beliebige Ansichten oder Anfangsfehler.

## 9. Rechnen über dasselbe Bild ist keine neue Beobachtung

Innerhalb einer Optimierung darf dieselbe Messung beliebig oft zur Berechnung der festen Fehlerfunktion verwendet werden. Falsch wäre, jede Iteration als weitere unabhängige Kameramessung in einen Filter einzuspeisen und dessen Unsicherheit erneut zu verringern.

Ein eindimensionales Beispiel zeigt die Scheingenauigkeit: Bei einem Heading-Prior mit 2° Standardabweichung und einer unabhängigen Bildmessung mit angenommenen 0,5° ergibt die einmalige Fusion eine formale Unsicherheit von etwa **0,485°**. Wird dasselbe Bild irrtümlich hundertmal als unabhängig gezählt, meldet die Rechnung etwa **0,050°**. Es wurde aber keine zusätzliche Information gewonnen.

Für einen neuen Frame wird der Zustand mit Odometrie zur neuen Aufnahmezeit fortgeschrieben. Das ist ein anderes Problem als hundert Rechenschritte über den alten Frame. Auch wirklich neue Bilder können gemeinsame Kalibrierfehler besitzen und sind nicht in allen Fehleranteilen unabhängig.

## 10. Übertragung auf den Hive und die zukünftigen Berechnungen

Im vollständigen Problem kommen mindestens `x, y, ψ` und der Hive-Winkel `α` vor. Mit einem einzelnen Zielmittelpunkt gibt es dann noch weniger unabhängige Information als im günstigen Test. Kleine Drift erlaubt sinnvolles Vorwissen und lokale Anpassung; sie macht das Problem nicht von selbst eindeutig.

Die bereits entwickelte Kombination aus relativer 3D-Tagpose, bekannter Mechanik und Schwerkraftrichtung liefert weitere Information. Falls diese Messung bereits eine belastbare unabhängige Orientierung trägt, wird die Alternation zu einer möglichen numerischen Umsetzung der gemeinsamen Schätzung. Eine große neue Lokalisierungsarchitektur ist dafür nicht grundsätzlich erforderlich.

Wichtig ist die Unabhängigkeit des Heading-Hinweises: [Limelight beschreibt MegaTag2](https://docs.limelightvision.io/docs/docs-limelight/pipeline-apriltag/apriltag-robot-localization-megatag2) ausdrücklich als Verfahren, dem die Roboterorientierung zugeführt wird. Ein Resultat, das bereits unser Heading als Eingang benutzt, darf nicht anschließend als unabhängiger Beweis für genau dieses Heading verwendet werden. Das ist eine Schlussfolgerung für unsere Messmodellierung, keine Behauptung über eine neue Limelight-API.

Für den vom Besitzer genannten Schutz zukünftiger Rechnungen sollte eine spätere Korrektur außerdem als **zusammenhängender Satz `(x, y, heading, Aufnahmezeit, Qualität)`** bereitgestellt werden. Zwischendurch korrigiertes XY mit noch unpassendem altem Heading an Shooter- oder Pfadrechnungen weiterzugeben wäre unnötig inkonsistent. Die internen bedingten Rechenschritte müssen keine sichtbaren Zwischenzustände des Roboters werden.

Die passende Schlussfolgerung lautet daher: Odometrie als unsicheres Vorwissen behalten, Kamera auf kleine begründete Korrekturen anwenden, und Heading/XY gemeinsam konsistent veröffentlichen. Ob die kleine innere Anpassung alternierend oder gemeinsam gerechnet wird, ist eine nachgeordnete numerische Entscheidung. Reale Unsicherheiten, Ausreißer und Aufnahmezeiten bleiben für die Messgüte entscheidend.

## Gewünschte Änderungen / Ergänzungen des Besitzers

- Gewünschte Gewichtung beziehungsweise angenommene Größenordnung von XY- und Headingdrift: …
- Welche unabhängigen Messdaten tatsächlich verfügbar sein sollen: …
- Weitere synthetische Fälle, die vor einer Umsetzung untersucht werden sollen: …
- Weitere Änderungen: …

## Reproduzierbarer Versuchscode

Der folgende Python-Code benötigt NumPy. Er ist hier ausschließlich als Text dokumentiert und gehört zu keiner Android-/Java-Quelle. Beim separaten Ausführen schreibt er die Rohresultate als JSON neben die kopierte Skriptdatei. Seine Punktkoordinaten sind synthetische Kontrollfälle und keine Feld- oder Schusskoordinaten.

Testumgebung: NumPy 2.3.5.

```python
import json
from pathlib import Path
import numpy as np

SEED = 20261018
SAMPLES = 384
SIGMA_POSITION = 2.0
SIGMA_HEADING = np.deg2rad(2.0)
SIGMA_VECTOR = 0.25
SIGMA_ORIENTATION = np.deg2rad(0.5)
rng = np.random.default_rng(SEED)
prior_position = rng.normal(0, SIGMA_POSITION, (SAMPLES, 2))
prior_heading = rng.normal(0, SIGMA_HEADING, SAMPLES)


def minimize_profile(function, lower, upper):
    ratio = (np.sqrt(5) - 1) / 2
    left = upper - ratio * (upper - lower)
    right = lower + ratio * (upper - lower)
    f_left, f_right = function(left), function(right)
    for _ in range(100):
        if upper - lower < 1e-12:
            break
        if f_left < f_right:
            upper, right, f_right = right, left, f_left
            left = upper - ratio * (upper - lower)
            f_left = function(left)
        else:
            lower, left, f_left = left, right, f_right
            right = lower + ratio * (upper - lower)
            f_right = function(right)
    return (lower + upper) / 2


def rotate(vectors, headings):
    c = np.cos(headings)[:, None]
    s = np.sin(headings)[:, None]
    return np.stack((c * vectors[:, :, 0] - s * vectors[:, :, 1],
                     s * vectors[:, :, 0] + c * vectors[:, :, 1]), axis=2)


def summary(position, heading):
    pos_error = np.linalg.norm(position, axis=1)
    return {
        'position_rmse_in': float(np.sqrt(np.mean(pos_error ** 2))),
        'heading_rmse_deg': float(np.rad2deg(np.sqrt(np.mean(heading ** 2)))),
        'position_worse_percent': float(100 * np.mean(pos_error > np.linalg.norm(prior_position, axis=1) + 1e-9)),
        'heading_worse_percent': float(100 * np.mean(np.abs(heading) > np.abs(prior_heading) + 1e-9)),
    }


def solve(points, orientation_enabled=False):
    n = len(points)
    observed = points[None, :, :] + rng.normal(0, SIGMA_VECTOR, (SAMPLES, n, 2))
    orientation = rng.normal(0, SIGMA_ORIENTATION, SAMPLES) if orientation_enabled else None

    def position_step(heading, regularized):
        values = points[None, :, :] - rotate(observed, heading)
        if not regularized:
            return np.mean(values, axis=1)
        return (np.sum(values, axis=1) / SIGMA_VECTOR ** 2 + prior_position / SIGMA_POSITION ** 2) / (
                n / SIGMA_VECTOR ** 2 + 1 / SIGMA_POSITION ** 2)

    def heading_step(position, heading, regularized):
        delta = points[None, :, :] - position[:, None, :]
        a = np.sum(delta * observed, axis=(1, 2))
        b = np.sum(observed[:, :, 0] * delta[:, :, 1] - observed[:, :, 1] * delta[:, :, 0], axis=1)
        if not regularized:
            return np.arctan2(b, a)
        heading = heading.copy()
        for _ in range(8):
            gradient = (a * np.sin(heading) - b * np.cos(heading)) / SIGMA_VECTOR ** 2
            hessian = (a * np.cos(heading) + b * np.sin(heading)) / SIGMA_VECTOR ** 2
            gradient += (heading - prior_heading) / SIGMA_HEADING ** 2
            hessian += 1 / SIGMA_HEADING ** 2
            if orientation is not None:
                gradient += (heading - orientation) / SIGMA_ORIENTATION ** 2
                hessian += 1 / SIGMA_ORIENTATION ** 2
            if np.any(hessian <= 0):
                raise AssertionError('small-error convex branch assumption failed')
            heading -= gradient / hessian
        return heading

    def alternate(regularized, heading_first, cycles):
        position = prior_position.copy()
        heading = prior_heading.copy()
        for _ in range(cycles):
            if heading_first:
                heading = heading_step(position, heading, regularized)
                position = position_step(heading, regularized)
            else:
                position = position_step(heading, regularized)
                heading = heading_step(position, heading, regularized)
        return position, heading

    methods = {
        'hard_xy_then_heading_20': alternate(False, False, 20),
        'hard_heading_then_xy_20': alternate(False, True, 20),
        'fixed_prior_heading_then_xy_20': alternate(True, True, 20),
        'fixed_prior_heading_then_xy_1000': alternate(True, True, 1000),
    }
    # A joint reference solution of the SAME objective: analytically eliminate xy,
    # then minimize the resulting one-dimensional profile over the small-drift branch.
    joint_position = np.empty_like(prior_position)
    joint_heading = np.empty_like(prior_heading)
    for i in range(SAMPLES):
        def profile(heading, return_position=False):
            c, s = np.cos(heading), np.sin(heading)
            rotated = observed[i] @ np.array([[c, s], [-s, c]])
            estimate = (np.sum(points - rotated, axis=0) / SIGMA_VECTOR ** 2
                        + prior_position[i] / SIGMA_POSITION ** 2) / (n / SIGMA_VECTOR ** 2 + 1 / SIGMA_POSITION ** 2)
            residual = points - estimate - rotated
            cost = np.sum(residual ** 2) / SIGMA_VECTOR ** 2
            cost += np.sum((estimate - prior_position[i]) ** 2) / SIGMA_POSITION ** 2
            cost += ((heading - prior_heading[i]) / SIGMA_HEADING) ** 2
            if orientation is not None:
                cost += ((heading - orientation[i]) / SIGMA_ORIENTATION) ** 2
            return estimate if return_position else cost
        lower, upper = prior_heading[i] - np.deg2rad(20), prior_heading[i] + np.deg2rad(20)
        heading = minimize_profile(profile, lower, upper)
        if profile(heading) > min(profile(value) for value in np.linspace(lower, upper, 41)) + 1e-8:
            raise AssertionError('profile optimizer failed coarse independent grid check')
        joint_heading[i] = heading
        joint_position[i] = profile(heading, True)
    methods['joint_fixed_prior'] = joint_position, joint_heading
    alternating_position, alternating_heading = methods['fixed_prior_heading_then_xy_1000']
    return {
        'points_in': points.tolist(),
        'independent_orientation': orientation_enabled,
        'methods': {name: summary(*values) for name, values in methods.items()},
        'alternating_joint_max_position_difference_in': float(np.max(np.linalg.norm(alternating_position - joint_position, axis=1))),
        'alternating_joint_max_heading_difference_deg': float(np.rad2deg(np.max(np.abs(alternating_heading - joint_heading)))),
    }


output = {
    'seed': SEED, 'samples_per_case': SAMPLES,
    'assumptions': {'sigma_prior_xy_in': SIGMA_POSITION, 'sigma_prior_heading_deg': float(np.rad2deg(SIGMA_HEADING)),
                    'sigma_relative_vector_component_in': SIGMA_VECTOR,
                    'sigma_independent_orientation_deg': float(np.rad2deg(SIGMA_ORIENTATION))},
    'initial_odometry': summary(prior_position, prior_heading),
    'cases': {},
}
for name, points, orientation in [
    ('one_center', [[72, 0]], False),
    ('two_centers_13in_baseline', [[72, -6.5], [72, 6.5]], False),
    ('two_centers_48in_baseline', [[72, -24], [72, 24]], False),
    ('one_center_plus_independent_orientation', [[72, 0]], True),
]:
    output['cases'][name] = solve(np.array(points, dtype=float), orientation)

# Exact zero-noise ambiguity; neither point is a real field/Hive coordinate.
angle = np.deg2rad(3)
wrong_position = np.array([72., 0.]) - np.array([72 * np.cos(angle), 72 * np.sin(angle)])
c, s = np.cos(angle), np.sin(angle)
reconstructed = (np.array([72., 0.]) - wrong_position) @ np.array([[c, -s], [s, c]])
output['exact_counterexample'] = {'wrong_position_in': wrong_position.tolist(),
                                  'wrong_heading_deg': 3,
                                  'same_observation_in': reconstructed.tolist(),
                                  'position_error_in': float(np.linalg.norm(wrong_position))}
output['reused_heading_frame_sigma_deg'] = {
    str(count): float(np.rad2deg(1 / np.sqrt(1 / SIGMA_HEADING ** 2 + count / SIGMA_ORIENTATION ** 2)))
    for count in [1, 100]
}
output_path = Path(__file__).with_suffix('.json')
output_path.write_text(json.dumps(output, indent=2), encoding='utf-8')
print(json.dumps(output, indent=2))

```

---

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
