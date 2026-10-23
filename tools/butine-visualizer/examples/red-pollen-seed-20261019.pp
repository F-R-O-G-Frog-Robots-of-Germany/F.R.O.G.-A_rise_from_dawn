{
  "version": "1.5.0",
  "startPoint": {
    "x": 59.25,
    "y": 18.0,
    "headingDeg": 90.0,
    "heading": "constant",
    "degrees": 90.0,
    "locked": false
  },
  "lines": [
    {
      "id": "run-01-leg-001",
      "name": "R01 \u00b7 intake OFF \u00b7 turn at depot",
      "kind": "atomic",
      "endPoint": {
        "x": 59.25,
        "y": 18.0,
        "heading": "linear",
        "startDeg": 90.0,
        "endDeg": 180.0,
        "degrees": 180.0
      },
      "controlPoints": [],
      "heading": {
        "type": "linear",
        "reverse": false,
        "startDeg": 90.0,
        "endDeg": 180.0
      },
      "color": "#ffffff",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": false,
        "collectedPollenIds": [],
        "contactFractions": [],
        "startSpeedInS": 0.0,
        "endSpeedInS": 0.0
      }
    },
    {
      "id": "run-01-leg-002",
      "name": "R01 \u00b7 intake ON \u00b7 P08 + P07 + P03 + P12 (4/4)",
      "kind": "atomic",
      "endPoint": {
        "x": 37.816130459845105,
        "y": 26.144389836160954,
        "heading": "constant",
        "startDeg": 180.0,
        "endDeg": 180.0,
        "degrees": 180.0
      },
      "controlPoints": [],
      "heading": {
        "type": "constant",
        "reverse": false,
        "degrees": 180.0
      },
      "color": "#ffffff",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": true,
        "collectedPollenIds": [
          8,
          7,
          3,
          12
        ],
        "contactFractions": [
          0.05296911030801193,
          0.6382025874690076,
          0.7791674745762829,
          0.9563872245091524
        ],
        "startSpeedInS": 0.0,
        "endSpeedInS": 0.0
      }
    },
    {
      "id": "run-01-leg-003",
      "name": "R01 \u00b7 intake OFF \u00b7 depot",
      "kind": "atomic",
      "endPoint": {
        "x": 59.25,
        "y": 18.0,
        "heading": "linear",
        "startDeg": 180.0,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "linear",
        "reverse": false,
        "startDeg": 180.0,
        "endDeg": 90.0
      },
      "color": "#ffffff",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 2000,
      "butine": {
        "intakeOn": false,
        "collectedPollenIds": [],
        "contactFractions": [],
        "startSpeedInS": 0.0,
        "endSpeedInS": 0.0
      }
    },
    {
      "id": "run-02-leg-001",
      "name": "R02 \u00b7 intake ON \u00b7 P01 + P18 (2/4)",
      "kind": "atomic",
      "endPoint": {
        "x": 68.12792206135785,
        "y": 38.27207793864214,
        "heading": "constant",
        "startDeg": 90.0,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "constant",
        "reverse": false,
        "degrees": 90.0
      },
      "color": "#80ff80",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": true,
        "collectedPollenIds": [
          1,
          18
        ],
        "contactFractions": [
          0.4961378912631432,
          0.9995017807906663
        ],
        "startSpeedInS": 0.0,
        "endSpeedInS": 47.008467018944756
      }
    },
    {
      "id": "run-02-leg-002",
      "name": "R02 \u00b7 intake ON \u00b7 P14 + P19 (4/4)",
      "kind": "atomic",
      "endPoint": {
        "x": 68.8900552884525,
        "y": 61.52888848499957,
        "heading": "constant",
        "startDeg": 90.0,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "constant",
        "reverse": false,
        "degrees": 90.0
      },
      "color": "#80ff80",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": true,
        "collectedPollenIds": [
          14,
          19
        ],
        "contactFractions": [
          0.44017777248313333,
          0.9570249117776766
        ],
        "startSpeedInS": 47.008467018944756,
        "endSpeedInS": 0.000653250982141784
      }
    },
    {
      "id": "run-02-leg-003",
      "name": "R02 \u00b7 intake OFF \u00b7 return",
      "kind": "atomic",
      "endPoint": {
        "x": 68.12792206135785,
        "y": 38.27207793864214,
        "heading": "linear",
        "startDeg": 90.0,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "linear",
        "reverse": false,
        "startDeg": 90.0,
        "endDeg": 90.0
      },
      "color": "#80ff80",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": false,
        "collectedPollenIds": [],
        "contactFractions": [],
        "startSpeedInS": 0.000653250982141784,
        "endSpeedInS": 47.008467018944756
      }
    },
    {
      "id": "run-02-leg-004",
      "name": "R02 \u00b7 intake OFF \u00b7 depot",
      "kind": "atomic",
      "endPoint": {
        "x": 59.25,
        "y": 18.0,
        "heading": "linear",
        "startDeg": 90.0,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "linear",
        "reverse": false,
        "startDeg": 90.0,
        "endDeg": 90.0
      },
      "color": "#80ff80",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 2000,
      "butine": {
        "intakeOn": false,
        "collectedPollenIds": [],
        "contactFractions": [],
        "startSpeedInS": 47.008467018944756,
        "endSpeedInS": 0.0
      }
    },
    {
      "id": "run-03-leg-001",
      "name": "R03 \u00b7 intake ON \u00b7 transit",
      "kind": "atomic",
      "endPoint": {
        "x": 32.972077938642144,
        "y": 38.27207793864214,
        "heading": "constant",
        "startDeg": 90.0,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "constant",
        "reverse": false,
        "degrees": 90.0
      },
      "color": "#00ff00",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": true,
        "collectedPollenIds": [],
        "contactFractions": [],
        "startSpeedInS": 0.0,
        "endSpeedInS": 18.702787341175988
      }
    },
    {
      "id": "run-03-leg-002",
      "name": "R03 \u00b7 intake ON \u00b7 P11 + P15 + P10 + P05 (4/4)",
      "kind": "atomic",
      "endPoint": {
        "x": 33.30642816563803,
        "y": 99.26396897483453,
        "heading": "constant",
        "startDeg": 90.0,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "constant",
        "reverse": false,
        "degrees": 90.0
      },
      "color": "#00ff00",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": true,
        "collectedPollenIds": [
          11,
          15,
          10,
          5
        ],
        "contactFractions": [
          0.007428808217948043,
          0.43382444144348165,
          0.5331903554533247,
          0.983604624191089
        ],
        "startSpeedInS": 18.702787341175988,
        "endSpeedInS": 0.0005493164082963631
      }
    },
    {
      "id": "run-03-leg-003",
      "name": "R03 \u00b7 intake OFF \u00b7 return",
      "kind": "atomic",
      "endPoint": {
        "x": 32.972077938642144,
        "y": 38.27207793864214,
        "heading": "linear",
        "startDeg": 90.0,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "linear",
        "reverse": false,
        "startDeg": 90.0,
        "endDeg": 90.0
      },
      "color": "#00ff00",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": false,
        "collectedPollenIds": [],
        "contactFractions": [],
        "startSpeedInS": 0.0005493164082963631,
        "endSpeedInS": 18.702787341175988
      }
    },
    {
      "id": "run-03-leg-004",
      "name": "R03 \u00b7 intake OFF \u00b7 depot",
      "kind": "atomic",
      "endPoint": {
        "x": 59.25,
        "y": 18.0,
        "heading": "linear",
        "startDeg": 90.0,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "linear",
        "reverse": false,
        "startDeg": 90.0,
        "endDeg": 90.0
      },
      "color": "#00ff00",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 2000,
      "butine": {
        "intakeOn": false,
        "collectedPollenIds": [],
        "contactFractions": [],
        "startSpeedInS": 18.702787341175988,
        "endSpeedInS": 0.0
      }
    },
    {
      "id": "run-04-leg-001",
      "name": "R04 \u00b7 intake ON \u00b7 transit",
      "kind": "atomic",
      "endPoint": {
        "x": 68.12792206135785,
        "y": 38.27207793864214,
        "heading": "constant",
        "startDeg": 90.0,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "constant",
        "reverse": false,
        "degrees": 90.0
      },
      "color": "#008000",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": true,
        "collectedPollenIds": [],
        "contactFractions": [],
        "startSpeedInS": 0.0,
        "endSpeedInS": 43.21838950127096
      }
    },
    {
      "id": "run-04-leg-002",
      "name": "R04 \u00b7 intake ON \u00b7 transit",
      "kind": "atomic",
      "endPoint": {
        "x": 68.12792206135785,
        "y": 105.72792206135786,
        "heading": "constant",
        "startDeg": 90.0,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "constant",
        "reverse": false,
        "degrees": 90.0
      },
      "color": "#008000",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": true,
        "collectedPollenIds": [],
        "contactFractions": [],
        "startSpeedInS": 43.21838950127096,
        "endSpeedInS": 12.050893486630324
      }
    },
    {
      "id": "run-04-leg-003",
      "name": "R04 \u00b7 intake ON \u00b7 P09 + P06 (2/4)",
      "kind": "atomic",
      "endPoint": {
        "x": 52.30156566935235,
        "y": 109.34568919696736,
        "heading": "constant",
        "startDeg": 90.0,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "constant",
        "reverse": false,
        "degrees": 90.0
      },
      "color": "#008000",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": true,
        "collectedPollenIds": [
          9,
          6
        ],
        "contactFractions": [
          0.14566961492142702,
          0.9384031120262214
        ],
        "startSpeedInS": 12.050893486630324,
        "endSpeedInS": 43.5432799483003
      }
    },
    {
      "id": "run-04-leg-004",
      "name": "R04 \u00b7 intake ON \u00b7 P17 (3/4)",
      "kind": "atomic",
      "endPoint": {
        "x": 32.972077938642144,
        "y": 105.72792206135786,
        "heading": "constant",
        "startDeg": 90.0,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "constant",
        "reverse": false,
        "degrees": 90.0
      },
      "color": "#008000",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": true,
        "collectedPollenIds": [
          17
        ],
        "contactFractions": [
          0.6624921590638959
        ],
        "startSpeedInS": 43.5432799483003,
        "endSpeedInS": 18.443706668194928
      }
    },
    {
      "id": "run-04-leg-005",
      "name": "R04 \u00b7 intake ON \u00b7 P20 (4/4)",
      "kind": "atomic",
      "endPoint": {
        "x": 19.723823740461818,
        "y": 78.63648008240864,
        "heading": "constant",
        "startDeg": 90.0,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "constant",
        "reverse": false,
        "degrees": 90.0
      },
      "color": "#008000",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": true,
        "collectedPollenIds": [
          20
        ],
        "contactFractions": [
          0.9668405277840364
        ],
        "startSpeedInS": 18.443706668194928,
        "endSpeedInS": 22.582984941246696
      }
    },
    {
      "id": "run-04-leg-006",
      "name": "R04 \u00b7 intake OFF \u00b7 return",
      "kind": "atomic",
      "endPoint": {
        "x": 32.972077938642144,
        "y": 38.27207793864214,
        "heading": "linear",
        "startDeg": 90.0,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "linear",
        "reverse": false,
        "startDeg": 90.0,
        "endDeg": 90.0
      },
      "color": "#008000",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": false,
        "collectedPollenIds": [],
        "contactFractions": [],
        "startSpeedInS": 22.582984941246696,
        "endSpeedInS": 29.60840860035243
      }
    },
    {
      "id": "run-04-leg-007",
      "name": "R04 \u00b7 intake OFF \u00b7 depot",
      "kind": "atomic",
      "endPoint": {
        "x": 59.25,
        "y": 18.0,
        "heading": "linear",
        "startDeg": 90.0,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "linear",
        "reverse": false,
        "startDeg": 90.0,
        "endDeg": 90.0
      },
      "color": "#008000",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 2000,
      "butine": {
        "intakeOn": false,
        "collectedPollenIds": [],
        "contactFractions": [],
        "startSpeedInS": 29.60840860035243,
        "endSpeedInS": 0.0
      }
    },
    {
      "id": "run-05-leg-001",
      "name": "R05 \u00b7 intake OFF \u00b7 turn at depot",
      "kind": "atomic",
      "endPoint": {
        "x": 59.25,
        "y": 18.0,
        "heading": "linear",
        "startDeg": 90.0,
        "endDeg": 180.0,
        "degrees": 180.0
      },
      "controlPoints": [],
      "heading": {
        "type": "linear",
        "reverse": false,
        "startDeg": 90.0,
        "endDeg": 180.0
      },
      "color": "#000000",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": false,
        "collectedPollenIds": [],
        "contactFractions": [],
        "startSpeedInS": 0.0,
        "endSpeedInS": 0.0
      }
    },
    {
      "id": "run-05-leg-002",
      "name": "R05 \u00b7 intake ON \u00b7 P13 + P02 (2/4)",
      "kind": "atomic",
      "endPoint": {
        "x": 24.841620781466283,
        "y": 29.66835700763898,
        "heading": "constant",
        "startDeg": 180.0,
        "endDeg": 180.0,
        "degrees": 180.0
      },
      "controlPoints": [],
      "heading": {
        "type": "constant",
        "reverse": false,
        "degrees": 180.0
      },
      "color": "#000000",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": true,
        "collectedPollenIds": [
          13,
          2
        ],
        "contactFractions": [
          0.6568206498906137,
          0.9724768140772057
        ],
        "startSpeedInS": 0.0,
        "endSpeedInS": 0.9711517579481316
      }
    },
    {
      "id": "run-05-leg-003",
      "name": "R05 \u00b7 intake ON \u00b7 P04 (3/4)",
      "kind": "atomic",
      "endPoint": {
        "x": 71.5001045663407,
        "y": 16.179476212326364,
        "heading": "constant",
        "startDeg": 180.0,
        "endDeg": 180.0,
        "degrees": 180.0
      },
      "controlPoints": [],
      "heading": {
        "type": "constant",
        "reverse": false,
        "degrees": 180.0
      },
      "color": "#000000",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": true,
        "collectedPollenIds": [
          4
        ],
        "contactFractions": [
          0.9794108061725718
        ],
        "startSpeedInS": 0.9711517579481316,
        "endSpeedInS": 7.568263746164472
      }
    },
    {
      "id": "run-05-leg-004",
      "name": "R05 \u00b7 intake ON \u00b7 transit",
      "kind": "atomic",
      "endPoint": {
        "x": 68.12792206135785,
        "y": 105.72792206135786,
        "heading": "constant",
        "startDeg": 180.0,
        "endDeg": 180.0,
        "degrees": 180.0
      },
      "controlPoints": [],
      "heading": {
        "type": "constant",
        "reverse": false,
        "degrees": 180.0
      },
      "color": "#000000",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": true,
        "collectedPollenIds": [],
        "contactFractions": [],
        "startSpeedInS": 7.568263746164472,
        "endSpeedInS": 13.106370320019375
      }
    },
    {
      "id": "run-05-leg-005",
      "name": "R05 \u00b7 intake ON \u00b7 P16 (4/4)",
      "kind": "atomic",
      "endPoint": {
        "x": 61.72688244062659,
        "y": 107.54905055430609,
        "heading": "constant",
        "startDeg": 180.0,
        "endDeg": 180.0,
        "degrees": 180.0
      },
      "controlPoints": [],
      "heading": {
        "type": "constant",
        "reverse": false,
        "degrees": 180.0
      },
      "color": "#000000",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": true,
        "collectedPollenIds": [
          16
        ],
        "contactFractions": [
          0.8497383837059375
        ],
        "startSpeedInS": 13.106370320019375,
        "endSpeedInS": 0.0
      }
    },
    {
      "id": "run-05-leg-006",
      "name": "R05 \u00b7 intake OFF \u00b7 return",
      "kind": "atomic",
      "endPoint": {
        "x": 68.12792206135785,
        "y": 105.72792206135786,
        "heading": "linear",
        "startDeg": 180.0,
        "endDeg": 173.7765541781897,
        "degrees": 173.7765541781897
      },
      "controlPoints": [],
      "heading": {
        "type": "linear",
        "reverse": false,
        "startDeg": 180.0,
        "endDeg": 173.7765541781897
      },
      "color": "#000000",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": false,
        "collectedPollenIds": [],
        "contactFractions": [],
        "startSpeedInS": 0.0,
        "endSpeedInS": 12.649378492840839
      }
    },
    {
      "id": "run-05-leg-007",
      "name": "R05 \u00b7 intake OFF \u00b7 return",
      "kind": "atomic",
      "endPoint": {
        "x": 68.12792206135785,
        "y": 38.27207793864214,
        "heading": "linear",
        "startDeg": 173.7765541781897,
        "endDeg": 110.69555693029046,
        "degrees": 110.69555693029046
      },
      "controlPoints": [],
      "heading": {
        "type": "linear",
        "reverse": false,
        "startDeg": 173.7765541781897,
        "endDeg": 110.69555693029046
      },
      "color": "#000000",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 0,
      "butine": {
        "intakeOn": false,
        "collectedPollenIds": [],
        "contactFractions": [],
        "startSpeedInS": 12.649378492840839,
        "endSpeedInS": 43.21838950127096
      }
    },
    {
      "id": "run-05-leg-008",
      "name": "R05 \u00b7 intake OFF \u00b7 depot",
      "kind": "atomic",
      "endPoint": {
        "x": 59.25,
        "y": 18.0,
        "heading": "linear",
        "startDeg": 110.69555693029046,
        "endDeg": 90.0,
        "degrees": 90.0
      },
      "controlPoints": [],
      "heading": {
        "type": "linear",
        "reverse": false,
        "startDeg": 110.69555693029046,
        "endDeg": 90.0
      },
      "color": "#000000",
      "locked": false,
      "waitBeforeMs": 0,
      "waitAfterMs": 2000,
      "butine": {
        "intakeOn": false,
        "collectedPollenIds": [],
        "contactFractions": [],
        "startSpeedInS": 43.21838950127096,
        "endSpeedInS": 0.0
      }
    }
  ],
  "sequence": [
    {
      "kind": "path",
      "lineId": "run-01-leg-001"
    },
    {
      "kind": "path",
      "lineId": "run-01-leg-002"
    },
    {
      "kind": "path",
      "lineId": "run-01-leg-003"
    },
    {
      "kind": "path",
      "lineId": "run-02-leg-001"
    },
    {
      "kind": "path",
      "lineId": "run-02-leg-002"
    },
    {
      "kind": "path",
      "lineId": "run-02-leg-003"
    },
    {
      "kind": "path",
      "lineId": "run-02-leg-004"
    },
    {
      "kind": "path",
      "lineId": "run-03-leg-001"
    },
    {
      "kind": "path",
      "lineId": "run-03-leg-002"
    },
    {
      "kind": "path",
      "lineId": "run-03-leg-003"
    },
    {
      "kind": "path",
      "lineId": "run-03-leg-004"
    },
    {
      "kind": "path",
      "lineId": "run-04-leg-001"
    },
    {
      "kind": "path",
      "lineId": "run-04-leg-002"
    },
    {
      "kind": "path",
      "lineId": "run-04-leg-003"
    },
    {
      "kind": "path",
      "lineId": "run-04-leg-004"
    },
    {
      "kind": "path",
      "lineId": "run-04-leg-005"
    },
    {
      "kind": "path",
      "lineId": "run-04-leg-006"
    },
    {
      "kind": "path",
      "lineId": "run-04-leg-007"
    },
    {
      "kind": "path",
      "lineId": "run-05-leg-001"
    },
    {
      "kind": "path",
      "lineId": "run-05-leg-002"
    },
    {
      "kind": "path",
      "lineId": "run-05-leg-003"
    },
    {
      "kind": "path",
      "lineId": "run-05-leg-004"
    },
    {
      "kind": "path",
      "lineId": "run-05-leg-005"
    },
    {
      "kind": "path",
      "lineId": "run-05-leg-006"
    },
    {
      "kind": "path",
      "lineId": "run-05-leg-007"
    },
    {
      "kind": "path",
      "lineId": "run-05-leg-008"
    }
  ],
  "shapes": [
    {
      "id": "pollen-01",
      "name": "P01 RED pollen (display marker)",
      "vertices": [
        {
          "x": 70.621853,
          "y": 38.457746
        },
        {
          "x": 70.5152843455158,
          "y": 38.993502805311124
        },
        {
          "x": 70.21180249366117,
          "y": 39.447695493661165
        },
        {
          "x": 69.75760980531112,
          "y": 39.7511773455158
        },
        {
          "x": 69.221853,
          "y": 39.857746
        },
        {
          "x": 68.68609619468887,
          "y": 39.7511773455158
        },
        {
          "x": 68.23190350633882,
          "y": 39.447695493661165
        },
        {
          "x": 67.9284216544842,
          "y": 38.993502805311124
        },
        {
          "x": 67.82185299999999,
          "y": 38.457746
        },
        {
          "x": 67.9284216544842,
          "y": 37.92198919468888
        },
        {
          "x": 68.23190350633882,
          "y": 37.467796506338836
        },
        {
          "x": 68.68609619468887,
          "y": 37.1643146544842
        },
        {
          "x": 69.221853,
          "y": 37.057746
        },
        {
          "x": 69.75760980531112,
          "y": 37.1643146544842
        },
        {
          "x": 70.21180249366117,
          "y": 37.467796506338836
        },
        {
          "x": 70.5152843455158,
          "y": 37.92198919468888
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-02",
      "name": "P02 RED pollen (display marker)",
      "vertices": [
        {
          "x": 16.788649,
          "y": 29.821966
        },
        {
          "x": 16.6820803455158,
          "y": 30.357722805311127
        },
        {
          "x": 16.378598493661165,
          "y": 30.811915493661168
        },
        {
          "x": 15.924405805311125,
          "y": 31.115397345515802
        },
        {
          "x": 15.388649,
          "y": 31.221966
        },
        {
          "x": 14.852892194688874,
          "y": 31.115397345515802
        },
        {
          "x": 14.398699506338833,
          "y": 30.811915493661168
        },
        {
          "x": 14.095217654484198,
          "y": 30.357722805311127
        },
        {
          "x": 13.988648999999999,
          "y": 29.821966
        },
        {
          "x": 14.095217654484198,
          "y": 29.286209194688873
        },
        {
          "x": 14.398699506338833,
          "y": 28.83201650633883
        },
        {
          "x": 14.852892194688872,
          "y": 28.528534654484197
        },
        {
          "x": 15.388649,
          "y": 28.421966
        },
        {
          "x": 15.924405805311125,
          "y": 28.528534654484197
        },
        {
          "x": 16.378598493661165,
          "y": 28.83201650633883
        },
        {
          "x": 16.6820803455158,
          "y": 29.286209194688873
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-03",
      "name": "P03 RED pollen (display marker)",
      "vertices": [
        {
          "x": 33.549426,
          "y": 22.956563
        },
        {
          "x": 33.4428573455158,
          "y": 23.492319805311126
        },
        {
          "x": 33.13937549366116,
          "y": 23.946512493661167
        },
        {
          "x": 32.68518280531112,
          "y": 24.249994345515802
        },
        {
          "x": 32.149426,
          "y": 24.356562999999998
        },
        {
          "x": 31.61366919468887,
          "y": 24.249994345515802
        },
        {
          "x": 31.159476506338834,
          "y": 23.946512493661167
        },
        {
          "x": 30.855994654484196,
          "y": 23.492319805311126
        },
        {
          "x": 30.749426,
          "y": 22.956563
        },
        {
          "x": 30.855994654484196,
          "y": 22.420806194688872
        },
        {
          "x": 31.15947650633883,
          "y": 21.96661350633883
        },
        {
          "x": 31.61366919468887,
          "y": 21.663131654484197
        },
        {
          "x": 32.149426,
          "y": 21.556563
        },
        {
          "x": 32.68518280531112,
          "y": 21.663131654484197
        },
        {
          "x": 33.13937549366116,
          "y": 21.96661350633883
        },
        {
          "x": 33.4428573455158,
          "y": 22.420806194688872
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-04",
      "name": "P04 RED pollen (display marker)",
      "vertices": [
        {
          "x": 64.339444,
          "y": 16.052464
        },
        {
          "x": 64.23287534551581,
          "y": 16.588220805311128
        },
        {
          "x": 63.929393493661166,
          "y": 17.04241349366117
        },
        {
          "x": 63.475200805311125,
          "y": 17.345895345515803
        },
        {
          "x": 62.939444,
          "y": 17.452464
        },
        {
          "x": 62.40368719468888,
          "y": 17.345895345515803
        },
        {
          "x": 61.94949450633884,
          "y": 17.04241349366117
        },
        {
          "x": 61.6460126544842,
          "y": 16.588220805311128
        },
        {
          "x": 61.539444,
          "y": 16.052464
        },
        {
          "x": 61.6460126544842,
          "y": 15.516707194688875
        },
        {
          "x": 61.94949450633884,
          "y": 15.062514506338834
        },
        {
          "x": 62.40368719468888,
          "y": 14.7590326544842
        },
        {
          "x": 62.939444,
          "y": 14.652464
        },
        {
          "x": 63.475200805311125,
          "y": 14.7590326544842
        },
        {
          "x": 63.929393493661166,
          "y": 15.062514506338834
        },
        {
          "x": 64.23287534551581,
          "y": 15.516707194688873
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-05",
      "name": "P05 RED pollen (display marker)",
      "vertices": [
        {
          "x": 34.708621,
          "y": 108.663984
        },
        {
          "x": 34.6020523455158,
          "y": 109.19974080531112
        },
        {
          "x": 34.29857049366117,
          "y": 109.65393349366117
        },
        {
          "x": 33.844377805311126,
          "y": 109.9574153455158
        },
        {
          "x": 33.308621,
          "y": 110.063984
        },
        {
          "x": 32.77286419468888,
          "y": 109.9574153455158
        },
        {
          "x": 32.31867150633884,
          "y": 109.65393349366117
        },
        {
          "x": 32.0151896544842,
          "y": 109.19974080531112
        },
        {
          "x": 31.908621000000004,
          "y": 108.663984
        },
        {
          "x": 32.0151896544842,
          "y": 108.12822719468888
        },
        {
          "x": 32.31867150633884,
          "y": 107.67403450633883
        },
        {
          "x": 32.77286419468888,
          "y": 107.3705526544842
        },
        {
          "x": 33.308621,
          "y": 107.263984
        },
        {
          "x": 33.844377805311126,
          "y": 107.3705526544842
        },
        {
          "x": 34.29857049366117,
          "y": 107.67403450633883
        },
        {
          "x": 34.6020523455158,
          "y": 108.12822719468888
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-06",
      "name": "P06 RED pollen (display marker)",
      "vertices": [
        {
          "x": 48.551952,
          "y": 119.522846
        },
        {
          "x": 48.4453833455158,
          "y": 120.05860280531112
        },
        {
          "x": 48.141901493661166,
          "y": 120.51279549366117
        },
        {
          "x": 47.687708805311125,
          "y": 120.8162773455158
        },
        {
          "x": 47.151952,
          "y": 120.922846
        },
        {
          "x": 46.61619519468888,
          "y": 120.8162773455158
        },
        {
          "x": 46.16200250633884,
          "y": 120.51279549366117
        },
        {
          "x": 45.8585206544842,
          "y": 120.05860280531112
        },
        {
          "x": 45.751952,
          "y": 119.522846
        },
        {
          "x": 45.8585206544842,
          "y": 118.98708919468888
        },
        {
          "x": 46.16200250633884,
          "y": 118.53289650633883
        },
        {
          "x": 46.61619519468888,
          "y": 118.2294146544842
        },
        {
          "x": 47.151952,
          "y": 118.122846
        },
        {
          "x": 47.687708805311125,
          "y": 118.2294146544842
        },
        {
          "x": 48.141901493661166,
          "y": 118.53289650633883
        },
        {
          "x": 48.4453833455158,
          "y": 118.98708919468888
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-07",
      "name": "P07 RED pollen (display marker)",
      "vertices": [
        {
          "x": 36.570848999999995,
          "y": 18.186538
        },
        {
          "x": 36.464280345515796,
          "y": 18.722294805311126
        },
        {
          "x": 36.16079849366116,
          "y": 19.176487493661167
        },
        {
          "x": 35.70660580531112,
          "y": 19.4799693455158
        },
        {
          "x": 35.170849,
          "y": 19.586537999999997
        },
        {
          "x": 34.63509219468887,
          "y": 19.4799693455158
        },
        {
          "x": 34.18089950633883,
          "y": 19.176487493661167
        },
        {
          "x": 33.8774176544842,
          "y": 18.722294805311126
        },
        {
          "x": 33.770849,
          "y": 18.186538
        },
        {
          "x": 33.8774176544842,
          "y": 17.650781194688875
        },
        {
          "x": 34.18089950633883,
          "y": 17.196588506338834
        },
        {
          "x": 34.63509219468887,
          "y": 16.893106654484196
        },
        {
          "x": 35.170849,
          "y": 16.786538
        },
        {
          "x": 35.70660580531112,
          "y": 16.893106654484196
        },
        {
          "x": 36.16079849366116,
          "y": 17.19658850633883
        },
        {
          "x": 36.464280345515796,
          "y": 17.65078119468887
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-08",
      "name": "P08 RED pollen (display marker)",
      "vertices": [
        {
          "x": 49.114667,
          "y": 13.778312
        },
        {
          "x": 49.0080983455158,
          "y": 14.314068805311125
        },
        {
          "x": 48.70461649366116,
          "y": 14.768261493661166
        },
        {
          "x": 48.25042380531112,
          "y": 15.0717433455158
        },
        {
          "x": 47.714667,
          "y": 15.178312
        },
        {
          "x": 47.178910194688875,
          "y": 15.0717433455158
        },
        {
          "x": 46.724717506338834,
          "y": 14.768261493661166
        },
        {
          "x": 46.4212356544842,
          "y": 14.314068805311125
        },
        {
          "x": 46.314667,
          "y": 13.778312
        },
        {
          "x": 46.4212356544842,
          "y": 13.242555194688874
        },
        {
          "x": 46.724717506338834,
          "y": 12.788362506338833
        },
        {
          "x": 47.178910194688875,
          "y": 12.484880654484199
        },
        {
          "x": 47.714667,
          "y": 12.378312
        },
        {
          "x": 48.25042380531112,
          "y": 12.484880654484199
        },
        {
          "x": 48.70461649366116,
          "y": 12.788362506338833
        },
        {
          "x": 49.0080983455158,
          "y": 13.242555194688872
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-09",
      "name": "P09 RED pollen (display marker)",
      "vertices": [
        {
          "x": 56.969879999999996,
          "y": 114.629672
        },
        {
          "x": 56.8633113455158,
          "y": 115.16542880531112
        },
        {
          "x": 56.55982949366116,
          "y": 115.61962149366117
        },
        {
          "x": 56.10563680531112,
          "y": 115.9231033455158
        },
        {
          "x": 55.56988,
          "y": 116.029672
        },
        {
          "x": 55.034123194688874,
          "y": 115.9231033455158
        },
        {
          "x": 54.57993050633883,
          "y": 115.61962149366117
        },
        {
          "x": 54.2764486544842,
          "y": 115.16542880531112
        },
        {
          "x": 54.16988,
          "y": 114.629672
        },
        {
          "x": 54.2764486544842,
          "y": 114.09391519468888
        },
        {
          "x": 54.57993050633883,
          "y": 113.63972250633883
        },
        {
          "x": 55.034123194688874,
          "y": 113.3362406544842
        },
        {
          "x": 55.56988,
          "y": 113.229672
        },
        {
          "x": 56.10563680531112,
          "y": 113.3362406544842
        },
        {
          "x": 56.55982949366116,
          "y": 113.63972250633883
        },
        {
          "x": 56.8633113455158,
          "y": 114.09391519468888
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-10",
      "name": "P10 RED pollen (display marker)",
      "vertices": [
        {
          "x": 34.188719,
          "y": 81.192366
        },
        {
          "x": 34.0821503455158,
          "y": 81.72812280531113
        },
        {
          "x": 33.778668493661165,
          "y": 82.18231549366118
        },
        {
          "x": 33.324475805311124,
          "y": 82.4857973455158
        },
        {
          "x": 32.788719,
          "y": 82.59236600000001
        },
        {
          "x": 32.25296219468888,
          "y": 82.4857973455158
        },
        {
          "x": 31.798769506338836,
          "y": 82.18231549366118
        },
        {
          "x": 31.495287654484198,
          "y": 81.72812280531113
        },
        {
          "x": 31.388719000000002,
          "y": 81.192366
        },
        {
          "x": 31.495287654484198,
          "y": 80.65660919468888
        },
        {
          "x": 31.798769506338832,
          "y": 80.20241650633884
        },
        {
          "x": 32.25296219468888,
          "y": 79.89893465448421
        },
        {
          "x": 32.788719,
          "y": 79.792366
        },
        {
          "x": 33.324475805311124,
          "y": 79.89893465448421
        },
        {
          "x": 33.778668493661165,
          "y": 80.20241650633884
        },
        {
          "x": 34.0821503455158,
          "y": 80.65660919468888
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-11",
      "name": "P11 RED pollen (display marker)",
      "vertices": [
        {
          "x": 30.684943999999998,
          "y": 49.125175
        },
        {
          "x": 30.578375345515802,
          "y": 49.66093180531112
        },
        {
          "x": 30.274893493661168,
          "y": 50.11512449366116
        },
        {
          "x": 29.820700805311127,
          "y": 50.4186063455158
        },
        {
          "x": 29.284944,
          "y": 50.525175
        },
        {
          "x": 28.749187194688872,
          "y": 50.4186063455158
        },
        {
          "x": 28.29499450633883,
          "y": 50.11512449366116
        },
        {
          "x": 27.991512654484197,
          "y": 49.66093180531112
        },
        {
          "x": 27.884944,
          "y": 49.125175
        },
        {
          "x": 27.991512654484197,
          "y": 48.589418194688875
        },
        {
          "x": 28.29499450633883,
          "y": 48.135225506338834
        },
        {
          "x": 28.749187194688872,
          "y": 47.8317436544842
        },
        {
          "x": 29.284944,
          "y": 47.725175
        },
        {
          "x": 29.820700805311127,
          "y": 47.8317436544842
        },
        {
          "x": 30.274893493661164,
          "y": 48.135225506338834
        },
        {
          "x": 30.578375345515802,
          "y": 48.589418194688875
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-12",
      "name": "P12 RED pollen (display marker)",
      "vertices": [
        {
          "x": 29.750920999999998,
          "y": 26.321159
        },
        {
          "x": 29.644352345515802,
          "y": 26.85691580531113
        },
        {
          "x": 29.340870493661168,
          "y": 27.31110849366117
        },
        {
          "x": 28.886677805311127,
          "y": 27.614590345515804
        },
        {
          "x": 28.350921,
          "y": 27.721159
        },
        {
          "x": 27.815164194688872,
          "y": 27.614590345515804
        },
        {
          "x": 27.360971506338835,
          "y": 27.31110849366117
        },
        {
          "x": 27.057489654484197,
          "y": 26.85691580531113
        },
        {
          "x": 26.950921,
          "y": 26.321159
        },
        {
          "x": 27.057489654484197,
          "y": 25.785402194688878
        },
        {
          "x": 27.36097150633883,
          "y": 25.331209506338837
        },
        {
          "x": 27.815164194688872,
          "y": 25.0277276544842
        },
        {
          "x": 28.350921,
          "y": 24.921159000000003
        },
        {
          "x": 28.886677805311127,
          "y": 25.0277276544842
        },
        {
          "x": 29.340870493661164,
          "y": 25.331209506338833
        },
        {
          "x": 29.644352345515802,
          "y": 25.785402194688874
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-13",
      "name": "P13 RED pollen (display marker)",
      "vertices": [
        {
          "x": 27.649866,
          "y": 19.44938
        },
        {
          "x": 27.543297345515803,
          "y": 19.98513680531113
        },
        {
          "x": 27.23981549366117,
          "y": 20.43932949366117
        },
        {
          "x": 26.785622805311128,
          "y": 20.742811345515804
        },
        {
          "x": 26.249866,
          "y": 20.84938
        },
        {
          "x": 25.714109194688874,
          "y": 20.742811345515804
        },
        {
          "x": 25.259916506338833,
          "y": 20.43932949366117
        },
        {
          "x": 24.956434654484198,
          "y": 19.98513680531113
        },
        {
          "x": 24.849866000000002,
          "y": 19.44938
        },
        {
          "x": 24.956434654484198,
          "y": 18.913623194688874
        },
        {
          "x": 25.259916506338833,
          "y": 18.459430506338833
        },
        {
          "x": 25.714109194688874,
          "y": 18.1559486544842
        },
        {
          "x": 26.249866,
          "y": 18.049380000000003
        },
        {
          "x": 26.785622805311128,
          "y": 18.1559486544842
        },
        {
          "x": 27.239815493661165,
          "y": 18.459430506338833
        },
        {
          "x": 27.543297345515803,
          "y": 18.913623194688874
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-14",
      "name": "P14 RED pollen (display marker)",
      "vertices": [
        {
          "x": 70.23379200000001,
          "y": 58.909209
        },
        {
          "x": 70.1272233455158,
          "y": 59.44496580531112
        },
        {
          "x": 69.82374149366117,
          "y": 59.89915849366116
        },
        {
          "x": 69.36954880531113,
          "y": 60.202640345515796
        },
        {
          "x": 68.833792,
          "y": 60.309208999999996
        },
        {
          "x": 68.29803519468888,
          "y": 60.202640345515796
        },
        {
          "x": 67.84384250633883,
          "y": 59.89915849366116
        },
        {
          "x": 67.5403606544842,
          "y": 59.44496580531112
        },
        {
          "x": 67.433792,
          "y": 58.909209
        },
        {
          "x": 67.5403606544842,
          "y": 58.37345219468887
        },
        {
          "x": 67.84384250633883,
          "y": 57.91925950633883
        },
        {
          "x": 68.29803519468888,
          "y": 57.6157776544842
        },
        {
          "x": 68.833792,
          "y": 57.509209
        },
        {
          "x": 69.36954880531113,
          "y": 57.6157776544842
        },
        {
          "x": 69.82374149366117,
          "y": 57.91925950633883
        },
        {
          "x": 70.1272233455158,
          "y": 58.37345219468887
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-15",
      "name": "P15 RED pollen (display marker)",
      "vertices": [
        {
          "x": 33.614732,
          "y": 75.131851
        },
        {
          "x": 33.5081633455158,
          "y": 75.66760780531112
        },
        {
          "x": 33.20468149366116,
          "y": 76.12180049366117
        },
        {
          "x": 32.75048880531112,
          "y": 76.4252823455158
        },
        {
          "x": 32.214732,
          "y": 76.531851
        },
        {
          "x": 31.67897519468887,
          "y": 76.4252823455158
        },
        {
          "x": 31.224782506338833,
          "y": 76.12180049366117
        },
        {
          "x": 30.921300654484195,
          "y": 75.66760780531112
        },
        {
          "x": 30.814732,
          "y": 75.131851
        },
        {
          "x": 30.921300654484195,
          "y": 74.59609419468887
        },
        {
          "x": 31.22478250633883,
          "y": 74.14190150633883
        },
        {
          "x": 31.67897519468887,
          "y": 73.8384196544842
        },
        {
          "x": 32.214732,
          "y": 73.73185099999999
        },
        {
          "x": 32.75048880531112,
          "y": 73.8384196544842
        },
        {
          "x": 33.20468149366116,
          "y": 74.14190150633883
        },
        {
          "x": 33.5081633455158,
          "y": 74.59609419468887
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-16",
      "name": "P16 RED pollen (display marker)",
      "vertices": [
        {
          "x": 53.688713,
          "y": 107.673712
        },
        {
          "x": 53.5821443455158,
          "y": 108.20946880531112
        },
        {
          "x": 53.278662493661166,
          "y": 108.66366149366117
        },
        {
          "x": 52.824469805311125,
          "y": 108.9671433455158
        },
        {
          "x": 52.288713,
          "y": 109.073712
        },
        {
          "x": 51.75295619468888,
          "y": 108.9671433455158
        },
        {
          "x": 51.29876350633884,
          "y": 108.66366149366117
        },
        {
          "x": 50.9952816544842,
          "y": 108.20946880531112
        },
        {
          "x": 50.888713,
          "y": 107.673712
        },
        {
          "x": 50.9952816544842,
          "y": 107.13795519468887
        },
        {
          "x": 51.29876350633884,
          "y": 106.68376250633882
        },
        {
          "x": 51.75295619468888,
          "y": 106.3802806544842
        },
        {
          "x": 52.288713,
          "y": 106.27371199999999
        },
        {
          "x": 52.824469805311125,
          "y": 106.3802806544842
        },
        {
          "x": 53.278662493661166,
          "y": 106.68376250633882
        },
        {
          "x": 53.5821443455158,
          "y": 107.13795519468887
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-17",
      "name": "P17 RED pollen (display marker)",
      "vertices": [
        {
          "x": 30.881535,
          "y": 116.91383
        },
        {
          "x": 30.774966345515804,
          "y": 117.44958680531113
        },
        {
          "x": 30.47148449366117,
          "y": 117.90377949366118
        },
        {
          "x": 30.017291805311128,
          "y": 118.2072613455158
        },
        {
          "x": 29.481535,
          "y": 118.31383000000001
        },
        {
          "x": 28.945778194688874,
          "y": 118.2072613455158
        },
        {
          "x": 28.491585506338836,
          "y": 117.90377949366118
        },
        {
          "x": 28.1881036544842,
          "y": 117.44958680531113
        },
        {
          "x": 28.081535000000002,
          "y": 116.91383
        },
        {
          "x": 28.1881036544842,
          "y": 116.37807319468888
        },
        {
          "x": 28.491585506338833,
          "y": 115.92388050633883
        },
        {
          "x": 28.945778194688874,
          "y": 115.6203986544842
        },
        {
          "x": 29.481535,
          "y": 115.51383
        },
        {
          "x": 30.017291805311128,
          "y": 115.6203986544842
        },
        {
          "x": 30.471484493661166,
          "y": 115.92388050633883
        },
        {
          "x": 30.774966345515804,
          "y": 116.37807319468888
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-18",
      "name": "P18 RED pollen (display marker)",
      "vertices": [
        {
          "x": 69.638506,
          "y": 48.661978
        },
        {
          "x": 69.5319373455158,
          "y": 49.19773480531112
        },
        {
          "x": 69.22845549366117,
          "y": 49.65192749366116
        },
        {
          "x": 68.77426280531112,
          "y": 49.9554093455158
        },
        {
          "x": 68.238506,
          "y": 50.061977999999996
        },
        {
          "x": 67.70274919468888,
          "y": 49.9554093455158
        },
        {
          "x": 67.24855650633883,
          "y": 49.65192749366116
        },
        {
          "x": 66.9450746544842,
          "y": 49.19773480531112
        },
        {
          "x": 66.838506,
          "y": 48.661978
        },
        {
          "x": 66.9450746544842,
          "y": 48.126221194688874
        },
        {
          "x": 67.24855650633883,
          "y": 47.67202850633883
        },
        {
          "x": 67.70274919468888,
          "y": 47.3685466544842
        },
        {
          "x": 68.238506,
          "y": 47.261978
        },
        {
          "x": 68.77426280531112,
          "y": 47.3685466544842
        },
        {
          "x": 69.22845549366117,
          "y": 47.67202850633883
        },
        {
          "x": 69.5319373455158,
          "y": 48.126221194688874
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-19",
      "name": "P19 RED pollen (display marker)",
      "vertices": [
        {
          "x": 70.30318100000001,
          "y": 70.929425
        },
        {
          "x": 70.1966123455158,
          "y": 71.46518180531112
        },
        {
          "x": 69.89313049366118,
          "y": 71.91937449366117
        },
        {
          "x": 69.43893780531113,
          "y": 72.2228563455158
        },
        {
          "x": 68.903181,
          "y": 72.329425
        },
        {
          "x": 68.36742419468888,
          "y": 72.2228563455158
        },
        {
          "x": 67.91323150633883,
          "y": 71.91937449366117
        },
        {
          "x": 67.6097496544842,
          "y": 71.46518180531112
        },
        {
          "x": 67.503181,
          "y": 70.929425
        },
        {
          "x": 67.6097496544842,
          "y": 70.39366819468887
        },
        {
          "x": 67.91323150633883,
          "y": 69.93947550633882
        },
        {
          "x": 68.36742419468888,
          "y": 69.6359936544842
        },
        {
          "x": 68.903181,
          "y": 69.52942499999999
        },
        {
          "x": 69.43893780531113,
          "y": 69.6359936544842
        },
        {
          "x": 69.89313049366118,
          "y": 69.93947550633882
        },
        {
          "x": 70.1966123455158,
          "y": 70.39366819468887
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "pollen-20",
      "name": "P20 RED pollen (display marker)",
      "vertices": [
        {
          "x": 20.878501,
          "y": 87.134818
        },
        {
          "x": 20.771932345515804,
          "y": 87.67057480531112
        },
        {
          "x": 20.46845049366117,
          "y": 88.12476749366117
        },
        {
          "x": 20.01425780531113,
          "y": 88.4282493455158
        },
        {
          "x": 19.478501,
          "y": 88.534818
        },
        {
          "x": 18.942744194688874,
          "y": 88.4282493455158
        },
        {
          "x": 18.488551506338837,
          "y": 88.12476749366117
        },
        {
          "x": 18.1850696544842,
          "y": 87.67057480531112
        },
        {
          "x": 18.078501000000003,
          "y": 87.134818
        },
        {
          "x": 18.1850696544842,
          "y": 86.59906119468887
        },
        {
          "x": 18.488551506338833,
          "y": 86.14486850633882
        },
        {
          "x": 18.942744194688874,
          "y": 85.8413866544842
        },
        {
          "x": 19.478501,
          "y": 85.73481799999999
        },
        {
          "x": 20.01425780531113,
          "y": 85.8413866544842
        },
        {
          "x": 20.468450493661166,
          "y": 86.14486850633882
        },
        {
          "x": 20.771932345515804,
          "y": 86.59906119468887
        }
      ],
      "color": "#eab308",
      "fillColor": "#eab308",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "field-obstacle-0",
      "name": "Physical field obstacle 1",
      "vertices": [
        {
          "x": 47.2,
          "y": 52.5
        },
        {
          "x": 53.9,
          "y": 52.5
        },
        {
          "x": 53.9,
          "y": 91.5
        },
        {
          "x": 47.2,
          "y": 91.5
        }
      ],
      "color": "#64748b",
      "fillColor": "#64748b",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "field-obstacle-1",
      "name": "Physical field obstacle 2",
      "vertices": [
        {
          "x": 90.1,
          "y": 52.5
        },
        {
          "x": 96.8,
          "y": 52.5
        },
        {
          "x": 96.8,
          "y": 91.5
        },
        {
          "x": 90.1,
          "y": 91.5
        }
      ],
      "color": "#64748b",
      "fillColor": "#64748b",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "field-obstacle-2",
      "name": "Physical field obstacle 3",
      "vertices": [
        {
          "x": 45.2,
          "y": 136.4
        },
        {
          "x": 52.0,
          "y": 136.4
        },
        {
          "x": 52.0,
          "y": 143.8
        },
        {
          "x": 45.2,
          "y": 143.8
        }
      ],
      "color": "#64748b",
      "fillColor": "#64748b",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "field-obstacle-3",
      "name": "Physical field obstacle 4",
      "vertices": [
        {
          "x": 136.4,
          "y": 92.0
        },
        {
          "x": 143.8,
          "y": 92.0
        },
        {
          "x": 143.8,
          "y": 98.8
        },
        {
          "x": 136.4,
          "y": 98.8
        }
      ],
      "color": "#64748b",
      "fillColor": "#64748b",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "field-obstacle-4",
      "name": "Physical field obstacle 5",
      "vertices": [
        {
          "x": 92.0,
          "y": 0.2
        },
        {
          "x": 98.8,
          "y": 0.2
        },
        {
          "x": 98.8,
          "y": 7.6
        },
        {
          "x": 92.0,
          "y": 7.6
        }
      ],
      "color": "#64748b",
      "fillColor": "#64748b",
      "type": "obstacle",
      "visible": true,
      "locked": true
    },
    {
      "id": "field-obstacle-5",
      "name": "Physical field obstacle 6",
      "vertices": [
        {
          "x": 0.2,
          "y": 45.2
        },
        {
          "x": 7.6,
          "y": 45.2
        },
        {
          "x": 7.6,
          "y": 52.0
        },
        {
          "x": 0.2,
          "y": 52.0
        }
      ],
      "color": "#64748b",
      "fillColor": "#64748b",
      "type": "obstacle",
      "visible": true,
      "locked": true
    }
  ],
  "fieldPoints": [],
  "activePaths": [],
  "settings": {
    "fieldWidth": 144,
    "fieldHeight": 144,
    "fieldMap": "biobuzz.webp",
    "rLength": 18,
    "rWidth": 18,
    "rHeight": 18,
    "safetyMargin": 1,
    "pathOpacity": 1,
    "showGhostPaths": false,
    "showOnionLayers": false,
    "experimentalFeatures": {
      "obstacles": true
    }
  },
  "butineVisualization": {
    "seed": 20261019,
    "alliance": "RED",
    "sideCondition": "x < 72",
    "generatedPollen": 40,
    "displayedPollen": 20,
    "possessionLimit": 4,
    "pollenShapesAreDisplayMarkersOnly": true,
    "routeGradient": "white-green-black",
    "routeCount": 5,
    "virtualBudgetSeconds": 150.0,
    "intakeWidthIn": 18.0,
    "intakeForwardOffsetIn": 9.0,
    "intakeOvershootIn": 1.0,
    "pollenRadiusIn": 1.4,
    "collectionModel": "front intake sweep at constant robot heading",
    "continuousCollection": true
  }
}
