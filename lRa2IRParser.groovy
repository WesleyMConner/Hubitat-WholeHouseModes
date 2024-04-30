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
  // rows - ArrayList of original rows ('as supplied', preserving nulls)
  // currRow - Index into rows ["+ 1" for user-friendly display]
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
  return (irMap.currRow < irMap.rowCols.size())
}

String ra2IR_CurrRow(Map irMap) {
  return irMap.rowCols[irMap.currRow]
}

ArrayList ra2IR_CurrRowCols(Map irMap) {
  return rowToCols(ra2IR_CurrRow(irMap))
}

String ra2IR_CurrRowUI(Map irMap) {
  [
    "${(irMap.currRow + 1).padLeft(5, "0")}: ${irMap.rowCols[irMap.currRow]}",
    "       ${rowToCols(irMap.rowCols[irMap.currRow])}"
  ].join('\n')
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
  //logInfo('getNextTableType', "At #${irMap.currRow}")
  ArrayList tableTypeCols = ra2IR_NextNonNullRowCols(irMap)
  logInfo('getNextTableType', "At #${irMap.currRow} w/ tableTypeCols> ${tableTypeCols}")
  Map tableTypes = [
    [ 'Device Room', 'Device Location', 'Device name',
      'Model', 'ID', 'Component', 'Component Number', 'Name' ] : [
      'instanceType': 'kpads',
      'instanceCols': 5,
      'recurringComponents' : [
        [ 'key': 'buttons', 'regexp': /^Button / ],
        [ 'key': 'leds', 'regexp': /^Led / ]
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
        [ 'key': 'actions', 'regexp': /^*/ ]
      ]
    ],
    ['Green Mode', 'ID', 'Mode Name', 'Step Number'] : [
      'instanceType': 'green',
      'instanceCols': 2,
      'recurringComponents' : [
        [ 'key': 'green', 'regexp': /^Green/ ]
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

Map getNewEntry(Map tableType, Map irMap, Map results) {
  Map newEntry = [:]
  logInfo('getNewEntry_IN', "currRow #: ${irMap.currRow}")
  ArrayList dataCols = ra2IR_NextNonNullRowCols(irMap)
  if (
    dataCols
    && dataCols[0]
    && dataCols.size() == tableType.instanceCols
  ) {
    newEntry = tableType.colKeys.withIndex()
    .findAll { colKey, i -> i < tableType.instanceCols }
    .collectEntries { colKey, i -> [ colKey, dataCols[i] ] }
    logInfo('getNewEntry-A', "newEntry: ${newEntry}")
    if (tableType.recurringComponents) {
      tableType.recurringComponents.each { recurringItem ->
        newEntry."${recurringItem.key}" = []  // ArrayList of (prospective) Maps
      }
      logInfo('getNewEntry-B', "newEntry: ${newEntry}")
      while (ra2IR_hasUnprocessedRows(irMap)) {
        ArrayList recurringCols = ra2IR_NextNonNullRowCols(irMap)
        logInfo('getNewEntry-C', "recurringCols: ${recurringCols}")
        if (recurringCols != null && !recurringCols[0]) {
          String regexCol = recurringCols[tableType.instanceCols]
          //-> logInfo('getNewEntry-C', "regexCol: ${regexCol}")
          tableType.recurringComponents.each { recurringItem ->
          logInfo('getNewEntry-D', "recurringItem: ${recurringItem}")
          // Sample recurringItem: [ 'key': 'buttons', 'regexp': /^Button / ],
            if (regexCol =~ recurringItem.regexp) {
              logInfo('getNewEntry-E', ['',
                "regexCol: ${regexCol}",
                "recurringItem.regexp: ${recurringItem.regexp}",
                "regexCol =~ recurringItem.regexp: ${regexCol =~ recurringItem.regexp}"
              ])
              Map newSubEntry = recurringCols.withIndex()
              .findAll { colData, i -> tableType.instanceCols <= i }
              .collectEntries { colData, i -> [ tableType.colKeys[i], colData ] }
              newEntry."${recurringItem.key}" << newSubEntry
/*
              ArrayList subEntries = newEntry."${recurringItem.key}"
              logInfo('getNewEntry-D', "priorSubEntries (before): ${subEntries}")
              subEntries << newSubEntry
              logInfo('getNewEntry-E', " priorSubEntries (after): ${subEntries}")
              newEntry."${recurringItem.key}" = subEntries
              logInfo('getNewEntry-F', "newEntry: ${newEntry}")
*/
            }
          }
        }
      }
    }
    ////  'recurringComponents' : [
    ////    [ 'key': 'buttons', 'regexp': /^Button / ],
    ////    [ 'key': 'leds', 'regexp': /^Button / ]
    ////  ]
    // Processes recurringComponent rows for the current newEntry.
  }
  if (newEntry) {
    logInfo('getNewEntry-EXIT', "newEntry: ${newEntry}")
    results."${tableType.instanceType}" += newEntry
  } else {
    logInfo('getNewEntry-NULL', "No new entry at row ${irMap.currRow}")
    ra2IR_ForceEOF(irMap)
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
      while( getNewEntry(tableType, irMap, results) ) { }
    }
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
