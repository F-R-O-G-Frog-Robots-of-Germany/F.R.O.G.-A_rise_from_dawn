{
  "version": "1.5.0",
  "startPoint": {"x": 20, "y": 20, "headingDeg": 0},
  "lines": [
    {
      "id": "outbound",
      "kind": "atomic",
      "name": "Collect corridor",
      "controlPoints": [{"x": 40, "y": 20}],
      "endPoint": {"x": 50, "y": 40},
      "heading": {"type": "constant", "degrees": 0}
    },
    {
      "id": "return",
      "kind": "atomic",
      "name": "Return corridor",
      "controlPoints": [],
      "endPoint": {"x": 20, "y": 20},
      "heading": {"type": "linear", "startDeg": 0, "endDeg": 90}
    }
  ],
  "sequence": [
    {"kind": "path", "lineId": "outbound"},
    {"kind": "wait", "durationMs": 750},
    {"kind": "path", "lineId": "return"}
  ]
}
