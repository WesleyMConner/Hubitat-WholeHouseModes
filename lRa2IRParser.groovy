/* groovylint-disable DuplicateListLiteral, VariableName */
// ---------------------------------------------------------------------------------
// RA2 Integration Report (RA2IR)
//
// Copyright (C) 2023-Present Wesley M. Conner
//
// LICENSE
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
// not use this file except in compliance with the License. Unless
// required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// ---------------------------------------------------------------------------------

library(
  name: 'lRa2IRParser',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Isolate RA2 Methods',
  category: 'general purpose'
)

// Revised Strategy
//   - Keep original data
//   - Supply non-null rows as String or as ArrayList cols

Map ra2IR_init(String ra2IntegrationReport) {
  //    rows - ArrayList of raw rows 'as supplied'
  // currRow - Index into rows, "+ 1" for user-friendly display
  return [
    rowCols: ra2IntegrationReport.split('\n'),
    currRow: 0
  ]
}

ArrayList rowToCols(String rowText) {
  // Preserve null columns.
  return rowText.trim()?.replaceAll(', ', ',').split(',')
}

Boolean ra2IR_hasUnprocessedRows(Map irMap) {
  return (irMap.currRow + 1 < irMap.rowCols.size())
}

String ra2IR_CurrRow(Map irMap) {
  return irMap.rowCols[irMap.currRow]
}

ArrayList ra2IR_CurrRowCols(Map irMap) {
  return rowToCols(ra2IR_CurrRow(irMap))
}

String ra2IR_CurrRowUI(Map irMap) {
  [
    '',
    "<b>${(irMap.currRow + 1).toString().padLeft(5, '0')}:</b> ${irMap.rowCols[irMap.currRow]}",
    "<br/>Cols: <em>${rowToCols(irMap.rowCols[irMap.currRow])}</em>"
  ].join('')
}

String ra2IR_NextNonNullRow(Map irMap) {
  String result = ''
  while (result == '' && ra2IR_hasUnprocessedRows(irMap)) {
    irMap.currRow = irMap.currRow + 1
    result = irMap.rowCols[irMap.currRow]
  }
  return result
}

ArrayList ra2IR_NextNonNullRowCols(Map irMap) {
  return rowToCols(ra2IR_NextNonNullRow(irMap))
}

void ra2IR_DecrementRow(Map irMap) {
  if (irMap.currRow > 0) {
    irMap.currRow = irMap.currRow - 1
  }
}

void ra2IR_ForceEOF(Map irMap) {
  irMap.currRow = irMap.rowCols.size()
}

Boolean hasExpectedHeaderRow(Map irMap) {
  String expectedHeaderRow = 'RadioRA 2 Integration Report'
  irMap.currRow = 0
  Boolean result = (irMap.rowCols[irMap.currRow] == expectedHeaderRow)
  if (!result) {
    logError('hasExpectedHeaderRow', ['Unexpected Header',
      "Found: ${irMap.rowCols[irMap.currRow]}",
      "Expected: ${expectedHeaderRow}"
    ])
    ra2IR_ForceEOF()
  }
  return result
}

Map getNextTableType(Map irMap) {
  ArrayList tableTypeCols = ra2IR_NextNonNullRowCols(irMap)
  //-> logInfo('getNextTableType', ra2IR_CurrRowUI(irMap))
  Map tableTypes = [
    [ 'Device Room', 'Device Location', 'Device name',
      'Model', 'ID', 'Component', 'Component Number', 'Name' ] : [
      'instanceType': 'kpads',
      'instanceCols': 5,
      'recurringComponents' : [
        [
          'key': 'buttons',
          'controlModels': [
            'PJ2-3BRL-GWH-L01', 'RR-MAIN-REP-WH', 'RR-T15RL-SW',
            'RR-VCRX-WH', 'RRD-H6BRL-WH', 'RRD-W6BRL-WH', 'RRD-W7B-WH'
          ],
          'regexpCol': 5,
          'regexp': /^Button /
        ],
        [
          'key': 'ccis',
          'controlModels': [ 'RR-VCRX-WH' ],
          'regexpCol': 4,  // NOTE: Subitems (outputs) will have their own ID!
          'regexp': /^CCI /
        ],
        [
          'key': 'leds',
          'controlModels': [
            'RR-MAIN-REP-WH', 'RR-T15RL-SW', 'RR-VCRX-WH', 'RRD-H6BRL-WH',
            'RRD-W6BRL-WH', 'RRD-W7B-WH'
          ],
          'regexpCol': 5,
          'regexp': /^Led /
        ]
      ]
    ],
    ['Room', 'ID']: [
      'instanceType': 'ra2Rooms',
      'instanceCols': 2
    ],
    ['Zone Room', 'Zone Name', 'ID'] : [
      'instanceType': 'circuits',
      'instanceCols': 3
    ],
    ['Timeclock', 'ID', 'Event', 'Event Index']: [
      'instanceType': 'timeclock',
      'instanceCols': 2,
      'recurringComponents' : [
        [
          'key': 'actions',
          'regexpCol': 2,
          'regexp': /^*/
        ]
      ]
    ],
    ['Green Mode', 'ID', 'Mode Name', 'Step Number'] : [
      'instanceType': 'green',
      'instanceCols': 2,
      'recurringComponents' : [
        [
          'key': 'green',
          'regexpCol': 2,
          'regexp': /^Green/
        ]
      ]
    ]
  ]
  Map tableType = tableTypes[(tableTypeCols)]
  if (tableType) {
    // Remove whitespace when creating per-column keys.
    tableType.colKeys = tableTypeCols*.replaceAll('\\s', '')
  } else {
    logError('getNextTableType', "No tableType for >${tableTypeCols}<")
    ra2IR_ForceEOF(irMap)
  }
  return tableType
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

Boolean appliesToModel(Map recurringComponent, String controlModel) {
  Boolean applyAll = recurringComponent.controlModels == NULL
  Boolean applySpecific = recurringComponent.controlModels?.contains(controlModel)
  //logInfo('#191', ['',
  //  "parta: ${applyAll}",
  //  "partb: ${applySpecific}",
  //  "parta || partb: ${applyAll || applySpecific}"
  //])
  return (applyAll || applySpecific)
}

void addSubitemsToEntry(Map tableType, Map irMap, Map newEntry) {
  tableType.recurringComponents
  .findAll { recurringItem -> appliesToModel(recurringItem, newEntry.Model) }
  .each { recurringItem ->
    newEntry."${recurringItem.key}" = []  // ArrayList for subitem Maps
  }
  while (ra2IR_hasUnprocessedRows(irMap)) {
    ArrayList recurringCols = ra2IR_NextNonNullRowCols(irMap)
    if (recurringCols != null && !recurringCols[0]) {
      tableType.recurringComponents
      .findAll { recurringItem -> appliesToModel(recurringItem, newEntry.Model) }
      .each { recurringItem ->
        Integer reColIdx = recurringItem.regexpCol
        String reColData = recurringCols[reColIdx]
        //-> logInfo('#213', ['',
        //->   "<b>recurringCols</b>: ${recurringCols}",
        //->   "<b>recurringItem.regexp</b>: ${recurringItem.regexp}",
        //->   "<b>reColIdx</b>: ${reColIdx}",
        //->   "<b>reColData</b>: ${reColData}",
        //->   "<b>reColData =~ recurringItem.regexp</b>: ${reColData =~ recurringItem.regexp}"
        //-> ])
        if (reColData =~ recurringItem.regexp) {
          //-> logInfo('#221', "${reColData} in scope for ${recurringItem.key}")
          Map newSubEntry = recurringCols.withIndex()
          .findAll { colData, i -> i >= recurringItem.regexpCol }
          .collectEntries { colData, i ->
            //-> logInfo('#225', "i: ${i}, colData: ${colData}, colKeys[i]: ${tableType.colKeys[i]}")
            [ tableType.colKeys[i], colData ]
          }
          newEntry."${recurringItem.key}" << newSubEntry
          //logInfo('addSubitemsToEntry', [
          //  '#239',
          //  "<b>${j}</b>: ${colData}",
          //  "<b>newSubEntry</b>: ${newSubEntry}"
          //])
        }
      }
    } else {
      ra2IR_DecrementRow(irMap) // Push back the prospective recurringCols
      break                     // Stop looping
    }
  }
}

Map getNewEntry(Map tableType, Map irMap, Map results) {
  Map newEntry
  ArrayList dataCols = ra2IR_NextNonNullRowCols(irMap)
  if (
    dataCols
    && dataCols[0]
    && dataCols.size() == tableType.instanceCols
  ) {
    newEntry = tableType.colKeys.withIndex()
    .findAll { colKey, i -> i < tableType.instanceCols }
    .collectEntries { colKey, i -> [ colKey, dataCols[i] ] }
    if (tableType.recurringComponents) {
      addSubitemsToEntry(tableType, irMap, newEntry)
      logInfo('getNewEntry', "<b>newEntry</b>: ${newEntry}")
      results."${tableType.instanceType}" += newEntry
    }
    //-> logInfo('getNewEntry', [
    //->   'ADDED NEW ENTRY:',
    //->   "<b>newEntry</b>: ${newEntry}",
    //->   ra2IR_CurrRowUI(irMap)
    //-> ])
    results."${tableType.instanceType}" += newEntry
  }
  if (!newEntry) {
    //-> logInfo('getNewEntry', [ '<b>NO NEW ENTRY</b>:', ra2IR_CurrRowUI(irMap) ])
  //ra2IR_ForceEOF(irMap)
  }
  return newEntry
}

Map parseRa2IntegRpt(String ra2IntegrationReport) {
  Map results = [
    ra2Devices: [],     kpads: [:],         ra2Rooms: [],
    circuits: [],       timeclock: [:],     green: [:]
  ]
  Map irMap = ra2IR_init(ra2IntegrationReport)
  //logInfo('parseRa2IntegRpt', "irMap: ${irMap}")
  hasExpectedHeaderRow(irMap)
  while (ra2IR_hasUnprocessedRows(irMap)) {
    Map tableType = getNextTableType(irMap)
    if (tableType) {
      /* groovylint-disable-next-line EmptyWhileStatement */
      while (getNewEntry(tableType, irMap, results)) { }
    } else {
      logInfo('parseRa2IntegRpt', [ 'No tableType was found',
        ra2IR_CurrRowUI(irMap) ])
      ra2IR_ForceEOF(irMap)
    }
    // O N E   E N T R Y   A N D   S T O P
    break
  }
  return results
}

  //---------------------------------------------------------------------------------
  //  Processes a 'lutronIntegrationReport' (provided as a multi-line String)
  //
  //  Produces a Map with the following keys:
  //      rA2Devices → A (multi-line) String that can be copy-pasted into the
  //                   "Lutron Integrator App" to create a new RA2 integration
  //                   instance, where each line is a "code,id,name" triplet:
  //                          code: Informs the device 'Type'
  //                            id: Specifies the Hubitat 'DeviceName'
  //                                (see 'deviceID' in Device events)
  //                          name: Specifies the Hubitat 'DeviceLabel'
  //                                (see 'name' in Device events)
  //           kpads → A nested Map of RA2 keypad details
  //        ra2Rooms → An ArrayList of RA2 rooms
  //                   ("RA2 rooms" can and often do differ from "Hubitat rooms")
  //        circuits → RA2 circuits which can be switched or dimmed
  //       timeclock → RA2 timeclock data
  //           green → RA2 green mode data
