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

String ra2IR_UserFriendlyRowCols(Map irMap) {
  return "${irMap.currRow + 1}: ${irMap.rowCols[irMap.currRow]}"
}

Map ra2IR_init(String ra2IntegrationReport) {
  // rowCols - ArrayList of rows (where each row is an ArrayList of cols)
  // currRow - User-friendly index that starts at 1 (instead of 0)
  return [
    rowCols: // ArrayList of rows (where each row is an ArrayList of cols)
      ra2IntegrationReport.split('\n').collect { row ->
        String normalizedCsvRow = row.trim()?.replaceAll(', ', ',')
        normalizedCsvRow.split(',')
      },
    currRow: 0
  ]
}

Boolean ra2IR_hasUnprocessedRows(Map irMap) {
  //-> logInfo('ra2IR_hasUnprocessedRows', "At ${irMap.currRow} of ${irMap.rowCols.size() + 1}")
  return (irMap.currRow < irMap.rowCols.size())
}

ArrayList ra2IR_CurrRow(Map irMap) {
  return irMap.rowCols[irMap.currRow]
}

ArrayList ra2IR_NextNonNullRow(Map irMap) {
  ArrayList result = []
  Integer loop = 0
  logInfo("STRT ${loop}", "${irMap.currRow}")
  while (ra2IR_hasUnprocessedRows(irMap) && result.size() == 0) {
    ++loop
    irMap.currRow = irMap.currRow + 1
    result = irMap.rowCols[irMap.currRow]
    logInfo("LOOP ${loop}", "${irMap.currRow} .. ${result.size()}: >${result}<")
  }
  logInfo("EXIT ${loop}", "${irMap.currRow} .. ${result.size()}: >${result}<")
  return result
}

void ra2IR_DecrementRow(Map irMap) {
  //if (ra2IR_hasUnprocessedRows(irMap)) {
  if (irMap.currRow > 0) {
    irMap.currRow = irMap.currRow - 1
  }
  //}
}

void ra2IR_ForceEOF(Map irMap) {
  irMap.currRow = irMap.rowCols.size()
}

Boolean isExpectedHeaderRow(ArrayList actualHeaderRow) {
  ArrayList expectedHeaderRow = ['RadioRA 2 Integration Report']
  Boolean isExpected = (actualHeaderRow == expectedHeaderRow)
  if (!isExpected) {
    logError('isExpectedHeaderRow', [
      '',
      "expected header: ${expectedHeaderRow}",
      "actual header: ${actualHeaderRow}"
    ])
  }
  return isExpected
}

Map getNextTableType(Map irMap) {
  logInfo('getNextTableType#72', "At #${irMap.currRow}")
  ArrayList tableTypeCols = ra2IR_NextNonNullRow(irMap)
  logInfo('getNextTableType#74', "At #${irMap.currRow} w/ tableTypeCols> ${tableTypeCols}")
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
    logError('getTableType', "No tableType for >${tableTypeCols}<")
    ra2IR_ForceEOF(irMap)
  }
  return tableType
}

String getHubitatCode(String controlModel) {
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
  String hubitatCode = controlModelToHubitatCode[controlModel]
  if (!hubitatCode) {
    logError('getHubitatCode', "No HubitatCode for >${controlModel}<")
  }
  return hubitatCode
}

Map getNewEntry(Map tableType, Map irMap, Map results) {
  Map newEntry = [:]
  logInfo('getNewEntry_IN', "currRow: ${irMap.currRow}")
  ArrayList dataCols = ra2IR_NextNonNullRow(irMap)
  if (
    dataCols
    && dataCols[0]
    && dataCols.size() == tableType.instanceCols
  ) {
    newEntry = tableType.colKeys.withIndex()
    .findAll { colTitle, i -> i < tableType.instanceCols }
    .collectEntries { colTitle, i ->
      String entryKey = colTitle.replaceAll('\\s', '')
      //logInfo('getNewEntry', "${i}: colTitle: ${colTitle}, instanceCols: ${tableType.instanceCols}")
      [ entryKey, dataCols[i] ]
    }
    //-> logInfo('getNewEntry-A', "newEntry: ${newEntry}")
    if (tableType.recurringComponents) {
      tableType.recurringComponents.each { recurringItem ->
        newEntry."${recurringItem.key}" = []  // Possible ArrayList of Maps
      }
      //-> logInfo('getNewEntry-B', "newEntry: ${newEntry}")
      while (ra2IR_hasUnprocessedRows(irMap)) {
        ArrayList recurringCols = ra2IR_NextNonNullRow(irMap)
        //-> logInfo('getNewEntry-C', "recurringCols: ${recurringCols}")
        if (recurringCols != null && !recurringCols[0]) {
          String regexCol = recurringCols[tableType.instanceCols]
          //-> logInfo('getNewEntry-C', "regexCol: ${regexCol}")
          tableType.recurringComponents.each { recurringItem ->
          logInfo('getNewEntry#162', "recurringItem: ${recurringItem}")
            if (regexCol =~ recurringItem.regexp) {
              logInfo('getNewEntry#164', ['',
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
    logInfo('getNewEntry#188', "newEntry: ${newEntry}")
    results."${tableType.instanceType}" += newEntry
  } else {
    logInfo('getNewEntry-NULL', "currRow: ${irMap.currRow}")
  }
  return newEntry
}

Map parseRa2IntegRpt(String lutronIntegrationReport) {
  Map results = [
    ra2Devices: [],
    kpads: [:],
    ra2Rooms: [],
    circuits: [],
    timeclock: [:],
    green: [:]
  ]
  Map irMap = [
    rowCols: // ArrayList of rows (where each row is an ArrayList of cols)
      lutronIntegrationReport.split('\n').collect { row ->
        String normalizedCsvRow = row.trim()?.replaceAll(', ', ',')
        normalizedCsvRow.split(',')
      },
    currRow: 0  // Start at #1 to easier comparisons with the IR CSV file.
  ]
  //logInfo('parseRa2IntegRpt', "irMap: ${irMap}")
  ArrayList actualHeaderRow = ra2IR_CurrRow(irMap)
  isExpectedHeaderRow(actualHeaderRow) || ra2IR_ForceEOF(irMap)
  while (ra2IR_hasUnprocessedRows(irMap)) {
    Map tableType = getNextTableType(irMap)
logInfo('parseRa2IntegRpt--A', "ra2IR_CurrRow: ${ra2IR_CurrRow(irMap)}")
    while (tableType) {
logInfo('parseRa2IntegRpt--B', "ra2IR_CurrRow: ${ra2IR_CurrRow(irMap)}")
      Map newEntry = getNewEntry(tableType, irMap, results)
      if (!newEntry) {
logInfo('parseRa2IntegRep', "No new entry at row ${irMap.currRow}")
        tableType = [:]
      }
      logInfo('parseRa2IntegRpt--C', "ra2IR_CurrRow: ${ra2IR_CurrRow(irMap)}")
    }
      //??-> ra2IR_DecrementRow(irMap)
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
