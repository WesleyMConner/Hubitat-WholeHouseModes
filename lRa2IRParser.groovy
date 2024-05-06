// ---------------------------------------------------------------------------------
// lRa2IRParser - RA2 Integration Report Parser (Library)
//
// Copyright (C) 2023-Present Wesley M. Conner
//
// LICENSE
//   Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
//   "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
//   not use this file except in compliance with the License. Unless
//   required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
//   implied.
// ---------------------------------------------------------------------------------

library(
  name: 'lRa2IRParser',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Isolate RA2 Methods',
  category: 'general purpose'
)

Map ra2IR_init(String ra2IntegrationReport) {
  // rows - ArrayList of raw rows 'as supplied' (includes empty lines)
  // curr - Index into rows, "+ 1" for user-friendly display
  return [
    rows: ra2IntegrationReport.split('\n'),
    curr: 0
  ]
}

String ra2IR_CurrRow(Map irMap) {
  return (irMap.curr + 1 == irMap.rows.size())
    ? 'EOF'
    : irMap.rows[irMap.curr]
}

ArrayList rowToCols(String rowText) {
  /* groovylint-disable-next-line ReturnsNullInsteadOfEmptyCollection */
  return (rowText == 'EOF') ? null
    : rowText.trim()?.replaceAll(', ', ',').split(',')
}

Boolean ra2IR_hasUnprocessedRows(Map irMap) {
  return (irMap.curr + 1 < irMap.rows.size())
}

ArrayList ra2IR_CurrRowCols(Map irMap) {
  return rowToCols(ra2IR_CurrRow(irMap))
}

String ra2IR_CurrRowUI(Map irMap) {
  String rowText = irMap.rows[irMap.curr]
  return [
    '',
    "<b>${(irMap.curr + 1).toString().padLeft(5, '0')}:</b> ${rowText}",
    "<br/>Cols: <em>${rowToCols(rowText) ?: 'null'}</em>"
  ].join('')
}

String ra2IR_NextNonNullRow(Map irMap) {
  String result = ''
  while (result == '') {
    if (ra2IR_hasUnprocessedRows(irMap)) {
      irMap.curr = irMap.curr + 1
      result = ra2IR_CurrRow(irMap)
    } else {
      result = 'EOF'
    }
  }
  return result
}

ArrayList ra2IR_NextNonNullRowCols(Map irMap) {
  return rowToCols(ra2IR_NextNonNullRow(irMap))
}

void ra2IR_DecrementRow(Map irMap) {
  if (irMap.curr > 0) {
    irMap.curr = irMap.curr - 1
  }
}

void ra2IR_ForceEOF(Map irMap) {
  irMap.curr = irMap.rows.size()
}

Boolean hasExpectedHeaderRow(Map irMap) {
  String expectedHeaderRow = 'RadioRA 2 Integration Report'
  irMap.curr = 0
  Boolean result = (irMap.rows[irMap.curr] == expectedHeaderRow)
  if (!result) {
    logError('hasExpectedHeaderRow() → ', ['Unexpected Header',
      "Found: ${irMap.rows[irMap.curr]}",
      "Expected: ${expectedHeaderRow}"
    ])
    ra2IR_ForceEOF()
  }
  return result
}

Map getNextTableType(Map irMap) {
  Map tableType = null
  ArrayList tableTypeCols = ra2IR_NextNonNullRowCols(irMap)
  if (tableTypeCols) {
    Map tableTypes = [
      [ 'Device Room', 'Device Location', 'Device name',
        'Model', 'ID', 'Component', 'Component Number', 'Name' ] : [
        instanceType: 'kpads',
        keyNames: [
          'ra2Room',              // Device Room
          'physicalLocation',     // Device Location
          'name',                 // Device name
          'model',                // Model
          'id',                   // ID
          null,                   // Component
          'number',               // Component Number
          'label'                 // Name
        ],
        entryCols: 5,
        recurring: [
          [
            key: 'buttons',
            controlModels: [
              'PJ2-3BRL-GWH-L01', 'RR-MAIN-REP-WH', 'RR-T15RL-SW',
              'RR-VCRX-WH', 'RRD-H6BRL-WH', 'RRD-W6BRL-WH', 'RRD-W7B-WH'
            ],
            regexIndex: 5,
            regexp: /^Button /
          ],
          [
            key: 'ccis',
            controlModels: [ 'RR-VCRX-WH' ],
            regexIndex: 4,  // NOTE: CCI subitems will carry an ID key
            regexp: /^CCI /
          ],
          [
            key: 'leds',
            controlModels: [
              'RR-MAIN-REP-WH', 'RR-T15RL-SW', 'RR-VCRX-WH', 'RRD-H6BRL-WH',
              'RRD-W6BRL-WH', 'RRD-W7B-WH'
            ],
            regexIndex: 5,
            regexp: /^Led /
          ]
        ]
      ],
      ['Room', 'ID']: [
        instanceType: 'ra2Rooms',
        keyNames: [
          'name',                 // Room
          'id'                    // ID
        ],
        entryCols: 2
      ],
      ['Zone Room', 'Zone Name', 'ID'] : [
        instanceType: 'circuits',
        keyNames: [
          'ra2Room',              // Zone Room
          'name',                 // Zone Name
          'id'                    // ID
        ],
        entryCols: 3
      ],
      ['Timeclock', 'ID', 'Event', 'Event Index']: [
        instanceType: 'timeclock',
        keyNames: [
          'name',                 // Timeclock
          'id',                   // ID
          'event',                // Event
          'eventIndex'            // Event Index
        ],
        entryCols: 2,
        recurring: [
          [
            key: 'actions',
            regexIndex: 2,
            regexp: /^*/
          ]
        ]
      ],
      ['Green Mode', 'ID', 'Mode Name', 'Step Number'] : [
        instanceType: 'green',
        keyNames: [
          'greenMode',            // Green Mode
          'id',                   // ID
          'name',                 // Mode Name
          'stepNumber'            // Step Number'
        ],
        entryCols: 2,
        recurring: [
          [
            key: 'green',
            regexIndex: 2,
            regexp: /^Green/
          ]
        ]
      ]
    ]
    tableType = tableTypes[(tableTypeCols)]
    //--> if (tableType) {
    //-->   tableType.keyNames = tableTypeCols*.replaceAll('\\s', '')
    //--> } else {
    if (!tableType) {
      logError('getNextTableType', "No tableType for >${tableTypeCols}<")
      ra2IR_ForceEOF(irMap)
    }
    }
  return tableType
  }

Boolean appliesToModel(Map recurringComponent, String controlModel) {
  Boolean applyAll = recurringComponent.controlModels == NULL
  Boolean applySpecific = recurringComponent.controlModels?.contains(controlModel)
  return (applyAll || applySpecific)
}

void addSubitemsToEntry(Map tableType, Map irMap, Map newEntry) {
  tableType.recurring
  .findAll { recurringItem -> appliesToModel(recurringItem, newEntry.model) }
  .each { recurringItem ->
    newEntry."${recurringItem.key}" = []  // ArrayList for subitem Maps
  }
  while (ra2IR_hasUnprocessedRows(irMap)) {
    ArrayList recurringCols = ra2IR_NextNonNullRowCols(irMap)
    if (recurringCols != null && !recurringCols[0]) {
      tableType.recurring
      .findAll { recurringItem -> appliesToModel(recurringItem, newEntry.model) }
      .each { recurringItem ->
        Integer reColIdx = recurringItem.regexIndex
        String reColData = recurringCols[reColIdx]
        if (reColData =~ recurringItem.regexp) {
            Map newSubEntry = recurringCols.withIndex()
            .findAll { colData, i ->
              (tableType.keyNames[i] != null) && (i >= recurringItem.regexIndex)
            }
            .collectEntries { colData, i ->
              String keyName = tableType.keyNames[i]
              //-> if (keyName) { [ keyName, colData ] }
              [ keyName, colData ]
            }
            newEntry."${recurringItem.key}" << newSubEntry
        }
      }
    } else {
      ra2IR_DecrementRow(irMap)    // Push back the unused recurringCols
      break                        // Stop looping
    }
  }
}

Map getNewEntry(Map tableType, Map irMap, Map results) {
  Map newEntry
  ArrayList dataCols = ra2IR_NextNonNullRowCols(irMap)
  if (
    dataCols
    && dataCols[0]
    && dataCols.size() == tableType.entryCols
  ) {
    newEntry = tableType.keyNames.withIndex()
    .findAll { colKey, i ->
      (colKey != null) && (i < tableType.entryCols)
    }
    .collectEntries { colKey, i -> [ colKey, dataCols[i] ] }
    if (tableType.recurring) {
      addSubitemsToEntry(tableType, irMap, newEntry)
    }
    results."${tableType.instanceType}" << newEntry
  } else {
    ra2IR_DecrementRow(irMap) // Push back the unused dataCols
  }
  return newEntry
}

String getHubitatCode(String controlModel) {
  String hubitatCode
  Map controlModelToHubitatCode = [
    'LRF2-OCR2B-P-WH': 'm',
    'PJ2-3BRL-GWH-L01': 'q',
    'RR-MAIN-REP-WH': 'k',
    'RR-T15RL-SW': 'k',
    'RR-VCRX-WH': 'v',
    'RRD-H6BRL-WH': 'w',
    'RRD-W6BRL-WH' : 'w',
    'RRD-W7B-WH': 'w'
  ]
  hubitatCode = controlModelToHubitatCode[controlModel]
  if (!hubitatCode) {
    logError('getHubitatCode', "No HubitatCode for >${controlModel}<")
  }
  return hubitatCode
}

void populateRa2Devices(Map results) {
  // Populates the ArrayList of results.ra2Devices code,id,name strings
  //   code → The device 'Type' (see below)
  //     id → Specifies the Hubitat 'DeviceName' (aka 'deviceID' in Device events)
  //   name → Specifies the Hubitat 'DeviceLabel' (aka 'name' in Device events)
  // Allowed Device Types
  //   From getHubitatCode(kpad.model)
  //     k: Keypad
  //     m: Motion
  //     q: Pico (pushed/released)     // See 'q' below
  //     v: VCRX
  //     w: Wall mount Keypad
  //   From circuits
  //     d: Dimmer                     // See 's' and 'o' below
  //   Some manual adjustments may be appropriate
  //     o: VCRX Output                // See 'd' above
  //     p: Pico (pushed/held)         // See 'q' above
  //     s: Switch .                   // See 'd' above
  //   Unused / Not Tested
  //     e: Shade
  //     f: Fan Control
  //     h: HVAC Controller
  //     r: Shade Remote
  //     t: Thermostat
  results.ra2Devices = []             // Erase any prior values and rebuild
  results.kpads.each { kpad ->
    // Users cannot edit the name of some device types (e.g., main repeaters,
    // motion sensors). For these devices the (editable) "Device Location"
    // (aka 'physicalLocation' is used as the device name.
    // Code,Id,Name
    if (['Enclosure Device 001', 'Device 001'].contains(kpad.name)) {
      results.ra2Devices << "${getHubitatCode(kpad.model)},${kpad.id},${kpad.physicalLocation}"
    } else {
      results.ra2Devices << "${getHubitatCode(kpad.model)},${kpad.id},${kpad.name}"
    }
  }
  results.circuits.each { device ->
    results.ra2Devices << "d,${device.id},${device.name}"
  }
}

void logRa2Results(Map results, String key) {
  ArrayList lines = []
  lines << "<b>${key}</b>"
  results."${key}".each { e -> lines << "${e}" }
  logInfo('logRa2Results', lines)
}

Map parseRa2IntegRpt(String ra2IntegrationReport, Boolean sortResults = false) {
  //---------------------------------------------------------------------------------
  //  Processes a 'ra2IntegrationReport' (provided as a multi-line String)
  //  Produces a 'results' Map with the following keys:
  //      rA2Devices → An ArrayList of strings that can be copy-pasted into the
  //                   "Lutron Integrator App" to create a new RA2 integration
  //                   instance
  //           kpads → An ArrayList of RA2 keypad Map(s)
  //        ra2Rooms → An ArrayList of RA2 room Map(s_
  //                   "RA2 rooms" can and often do differ from "Hubitat rooms" !!!
  //        circuits → An ArrayList of RA2 circuit Map(s) - switched or dimmed
  //       timeclock → An ArrayList of RA2 timeclock Map(s)
  //           green → An ArrayList of RA2 green mode Map(s)
  Map results = [
    ra2Devices: [],     kpads: [],         ra2Rooms: [],
    circuits: [],       timeclock: [],     green: []
  ]
  Map irMap = ra2IR_init(ra2IntegrationReport)
  hasExpectedHeaderRow(irMap)
  while (ra2IR_hasUnprocessedRows(irMap)) {
    Map tableType = getNextTableType(irMap)
    if (tableType) {
      /* groovylint-disable-next-line EmptyWhileStatement */
      while (getNewEntry(tableType, irMap, results)) { }
    }
  }
  populateRa2Devices(results)
  if (sortResults) {
    results.ra2Devices = results.ra2Devices.sort { triplet ->
      ArrayList elements = triplet.split(',')
      elements[1]
    }
    results.kpads = results.kpads.sort { kpad ->
      (kpad.name == 'Enclosure Device 001') ? kpad.physicalLocation : kpad.name
    }
    results.ra2Rooms = results.ra2Rooms.sort { room -> room.name }
    results.circuits = results.circuits.sort { circuit -> circuit.name }
  }

  return results
}
