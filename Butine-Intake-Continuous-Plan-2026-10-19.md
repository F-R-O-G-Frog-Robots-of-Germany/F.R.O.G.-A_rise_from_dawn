# Butine: Intake-Kontakt und durchgehende Sammelfahrt

Ziel: Eine direkt einstellbare Intake-Breite sammelt berührte Pollen gemeinsam; der Roboter fährt zwischen Sammelpunkten weiter und hält erst am Depot präzise an.

## Umsetzung der gewünschten Änderungen

- `Butine.Params.intakeWidthIn = 18.0` ist die direkt veränderbare Breite in Zoll. Es gibt keinen nullable Ersatzwert. Die Öffnung liegt zunächst neun Zoll vor der Roboterreferenz; ein Pollenradius von 1,4 Zoll gehört zur Kontaktrechnung.
- Die Planung berechnet die entlang des Weges überstrichene vordere Öffnung. Pollenpositionen bleiben von Roboter-Zielpositionen getrennt. Während einer Sammelfahrt bleibt der Roboterwinkel konstant; das holonome Fahrwerk darf dabei seitlich fahren. So entspricht dieselbe Kontaktgeometrie Planung und Ausführung ohne fiktive Berührungen durch Heck oder Chassis.
- Sämtliche sichtbaren Pollen werden gegen einen vorgeschlagenen Sammelabschnitt geprüft, auch außerhalb der Auswahlgrenze. Ein Abschnitt mit mehr Kontakten als freien Plätzen wird verworfen. Maximal vier Pollen pro Ladung; Rückfahrt mit ausgeschaltetem Intake.
- Ein zusammenhängender Pedro-3-CompoundPath führt alle Abschnitte einer Ladung aus. Zwischenpunkte bekommen keinen geplanten Stillstand und keine Intake-Wartezeit. Geometrische Ecken dürfen weiterhin Geschwindigkeitsreduktion erfordern. Abschneiden von Hindernisecken durch vorzeitigen Pfadwechsel wird deaktiviert.
- Der Runner entfernt Pollen erst nach Kontakt der tatsächlich gefahrenen Intake-Fläche. Er veröffentlicht den Intake-Zustand an die autonome Mechaniksteuerung und prüft Position, Winkel und Geschwindigkeit am Depot.
- Ergänzung vom 23. Oktober: `intakeOvershootIn = 1.0` verlängert den Sammelabschnitt nach dem letzten Kontakt um einen Zoll in exakt derselben Fahrtrichtung. Der Intake bleibt bis zum Ende dieser Verlängerung eingeschaltet. Neue Kontakte innerhalb der Verlängerung werden mitgerechnet; eine weitere Verlängerung darf weder ein Hindernis noch ein fünftes Pollen treffen.
- Ergänzung vom 23. Oktober: `Intake.loaded()` beziehungsweise die bestehende Voll-Ladungs-Speicherung beendet den Sammelpfad sofort und schaltet den Intake aus. Der Runner verwirft offene Planung und fährt auf einem neu geprüften Weg von der aktuellen Pose zum Depot. Wiederholte Sensor-Meldungen verändern diese Rückfahrt nicht; unbesuchte Pollen bleiben in der Karte. Position, Winkel und Geschwindigkeit am Depot werden auch bei dieser sensorbedingten Rückfahrt geprüft.
- Das Zeitmodell verwendet gemeinsame Abschnittsgeschwindigkeiten statt einer separaten Beschleunigung aus dem Stillstand an jedem Pollen. Java-Desktop-Bridge und Python-Export verwenden denselben Fahrweg und dieselben Zeiten; die `.pp`-Datei erhält konstante Sammelwinkel, gruppierte Kontaktangaben und Wartezeit nur am Depot.
- Bestehende mathematische Freiflächen-Kalibrierungen behalten die bisherige Punktplanung. Hardwarefreie Tests prüfen benachbarte Pollen, verschiedene Breiten, das Viererlimit, durchgehenden Dispatch, tatsächliche Kontakte und Depotgenauigkeit. Anschließend beide vorhandenen Audit-Skripte ausführen.

## Quellen und Grenzen

Die installierte Pedro-Version ist 3.0.1. Ihre tatsächlichen Quellen bestätigen `Paths.path(Path...)` und einmaliges `Follower.follow(...)` für zusammenhängende Fahrten. `pathSkip` kann Ecken vorzeitig verlassen und wird für diese hindernisgeprüften Polylinien deaktiviert. Die Endbedingung allein kann durch Timeout erfüllt werden; deshalb prüft der Runner die Depotpose zusätzlich.

- https://pedropathing.com/docs/pathing/reference/pathconstraints
- https://pedropathing.com/docs/pathing/reference/endconstraints
- https://pedropathing.com/docs/pathing/reference/api

Der Roboter ist noch nicht gebaut. Die Prüfung erfolgt mit echter Java/Pedro-Implementierung und simulierter Bewegung; mechanischer Einzug und reale Nachlaufstrecke sind damit nicht gemessen.

## Gewünschte Änderungen

- Bestätigt: Intake-Breite zunächst 18 Zoll, als direkt veränderbare Variable.
- Bestätigt: Ein Zoll über den bloßen Intake-Kontakt hinausfahren, ohne den Anfahrwinkel zu ändern.
- Bestätigt: Bei einem geladenen REV-Sensorsignal den Sammelpfad abbrechen und zum Depot zurückfahren; anschließend committen und pushen.
- Weitere Wünsche: …

Written by GPT-6, I had access to this repo and thus know the context of F.R.O.G.-A_rise_from_dawn
