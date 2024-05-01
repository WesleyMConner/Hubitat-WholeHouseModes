// ---------------------------------------------------------------------------------
// lPro2IRParser - Pro2 Integration Report Parser (Library)
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

import java.util.regex.Matcher

library(
  name: 'lPro2IRParser',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Isolate RA2 Methods',
  category: 'general purpose'
)

// The Pro2 parser is patterned after the Ra2 parser
//
// +-----------------+------------------+
// | Ra2 Result Keys | Pro2 Result Keys | Comments
// +-----------------+------------------+
// |   ra2Devices    |   pro2Devices    | Per kpads and circuits         |
// |      kpads      |      kpads       | LIPIdList/Devices (w/ buttons) |
// |    ra2Rooms     |    pro2Rooms     | A Component of kpads and zones |
// |     circuits    |     circuits     | Per zones
// |    timeclock    |        n/a       |
// |      green      |        n/a       |
// +-----------------+------------------+

Map pro2IR_init(String pro2IntegrationReport) {
  // rows - ArrayList of raw rows 'as supplied' (includes empty lines)
  // curr - Index into rows, "+ 1" for user-friendly display
  return [
    rows: pro2IntegrationReport.split('\n'),
    curr: 0
  ]
}

String pro2IR_CurrRow(Map irMap) {
  return (irMap.curr + 1 == irMap.rows.size())
    ? 'EOF'
    : irMap.rows[irMap.curr]
}

ArrayList rowToCols(String rowText) {
  return (rowText == 'EOF') ? null
    : rowText.trim()?.replaceAll(', ', ',').split(',')
}

Boolean pro2IR_hasUnprocessedRows(Map irMap) {
  return (irMap.curr + 1 < irMap.rows.size())
}

String pro2IR_CurrRowUI(Map irMap) {
  String rowText = irMap.rows[irMap.curr]
  return "<b>${(irMap.curr + 1).toString().padLeft(5, '0')}:</b> ${rowText}"
}

String pro2IR_NextNonNullRow(Map irMap) {
  String result = ''
  while (result == '') {
    if (pro2IR_hasUnprocessedRows(irMap)) {
      irMap.curr = irMap.curr + 1
      result = pro2IR_CurrRow(irMap)
    } else {
      result = 'EOF'
    }
  }
  return result
}

/*
ArrayList pro2IR_CurrRowCols(Map irMap) {
  return rowToCols(pro2IR_CurrRow(irMap))
}


ArrayList pro2IR_NextNonNullRowCols(Map irMap) {
  return rowToCols(pro2IR_NextNonNullRow(irMap))
}

void pro2IR_DecrementRow(Map irMap) {
  if (irMap.curr > 0) {
    irMap.curr = irMap.curr - 1
  }
}

void pro2IR_ForceEOF(Map irMap) {
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
    pro2IR_ForceEOF()
  }
  return result
}

Map getNextTableType(Map irMap) {
  Map tableType = null
  ArrayList tableTypeCols = pro2IR_NextNonNullRowCols(irMap)
  if (tableTypeCols) {
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
            'regexpCol': 4,  // NOTE: CCI subitems will carry an ID key
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
        'instanceType': 'pro2Rooms',
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
            'regexp': /..^..*../
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
    tableType = tableTypes[(tableTypeCols)]
    if (tableType) {
      tableType.colKeys = tableTypeCols*.replaceAll('\\s', '')
    } else {
      logError('getNextTableType', "No tableType for >${tableTypeCols}<")
      pro2IR_ForceEOF(irMap)
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
  tableType.recurringComponents
  .findAll { recurringItem -> appliesToModel(recurringItem, newEntry.Model) }
  .each { recurringItem ->
    newEntry."${recurringItem.key}" = []  // ArrayList for subitem Maps
  }
  while (pro2IR_hasUnprocessedRows(irMap)) {
    ArrayList recurringCols = pro2IR_NextNonNullRowCols(irMap)
    if (recurringCols != null && !recurringCols[0]) {
      tableType.recurringComponents
      .findAll { recurringItem -> appliesToModel(recurringItem, newEntry.Model) }
      .each { recurringItem ->
        Integer reColIdx = recurringItem.regexpCol
        String reColData = recurringCols[reColIdx]
        if (reColData =~ recurringItem.regexp) {
            Map newSubEntry = recurringCols.withIndex()
            .findAll { colData, i -> i >= recurringItem.regexpCol }
            .collectEntries { colData, i ->
              [ tableType.colKeys[i], colData ]
            }
            newEntry."${recurringItem.key}" << newSubEntry
        }
      }
    } else {
      pro2IR_DecrementRow(irMap)    // Push back the unused recurringCols
      break                        // Stop looping
    }
  }
}

Map getNewEntry(Map tableType, Map irMap, Map results) {
  Map newEntry
  ArrayList dataCols = pro2IR_NextNonNullRowCols(irMap)
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
    }
    results."${tableType.instanceType}" << newEntry
  } else {
    pro2IR_DecrementRow(irMap) // Push back the unused dataCols
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
  // Populates the ArrayList of results.pro2Devices code,id,name strings
  //   code → The device 'Type' (see below)
  //     id → Specifies the Hubitat 'DeviceName' (aka 'deviceID' in Device events)
  //   name → Specifies the Hubitat 'DeviceLabel' (aka 'name' in Device events)
  // Allowed Device Types
  //   results.kpads are mapped per getHubitatCode()
  //     k: Keypad
  //     m: Motion
  //     q: Pico (pushed/released)
  //     v: VCRX
  //     w: Wall mount Keypad
  //   results.circuits are assigned as dimmers by 'brute force'
  //     d: Dimmer
  //   None of the following devices were available for testing
  //     e: Shade ... No te
  //     f: Fan Control ... x
  //     h: HVAC Controller ... x
  //     r: Shade Remote ... x
  //     t: Thermostat ... x
  //   Available options for MANUAL fine-tuning
  //     o: VCRX Output ... Assigned 'd' by default per results.circuits (above)
  //     p: Pico (pushed/held) ... Assigned 'q' by default per getHubitatCode()
  //     s: Switch ... Assigned 'd' by default per results.circuits (above)
  results.pro2Devices = []             // Erase any prior values and rebuild
  results.kpads.each { kpad ->
    // Users cannot edit repeater 'Devicename', but can edit repeater 'DeviceLocation'.
    if (kpad.Devicename == 'Enclosure Device 001') {
      results.pro2Devices << "${getHubitatCode(kpad.Model)},${kpad.DeviceLocation},${kpad.ID}"
    } else {
      results.pro2Devices << "${getHubitatCode(kpad.Model)},${kpad.Devicename},${kpad.ID}"
    }
  }
  results.circuits.each { device ->
    results.pro2Devices << "d,${device.ZoneName},${device.ID}"
  }
}

void logResults(Map results, String key) {
  ArrayList lines = []
  lines << "<b>${key}</b>"
  results."${key}".each { entry -> lines << "${entry}" }
  logInfo('logResults', lines)
}
*/

String paddedRowNumber(Map irMap) {
  return (irMap.curr + 1).toString().padLeft(5, '0')
}

Map parsePro2IntegRpt(String pro2IntegrationReport) {
  logInfo('parsePro2IntegRpt', 'AT ENTRY')
  //---------------------------------------------------------------------------------
  //  Processes a 'lutronIntegrationReport' (provided as a multi-line String)
  //  Produces a 'results' Map with the following keys:
  //      rA2Devices → An ArrayList of strings that can be copy-pasted into the
  //                   "Lutron Integrator App" to create a new RA2 integration
  //                   instance
  //           kpads → An ArrayList of RA2 keypad Map(s)
  //        pro2Rooms → An ArrayList of RA2 room Map(s_
  //                   "RA2 rooms" can and often do differ from "Hubitat rooms" !!!
  //        circuits → An ArrayList of RA2 circuit Map(s) - switched or dimmed
  //       timeclock → An ArrayList of RA2 timeclock Map(s)
  //           green → An ArrayList of RA2 green mode Map(s)
  // RexEx Notes
  //       {  }  "  [  ]  - Escape once:  \{  \}  \"  \[  \]
  //   In a string: [  ]  - Escape twice:  "\\[  \\]"
  //    switch statements - The whole expression must be matched
  Map results = [ pro2Devices: [], kpads: [], pro2Rooms: [], circuits: [] ]
  Map irMap = pro2IR_init(pro2IntegrationReport)
  Integer indentLevel = 0
  ArrayList rowInfo = ['']
  /*
  ArrayList data = [
    'This is quoted "text" in a string',
    'This is a number 58 in a string',
    'This is a [ in a string',
    'This is a ] in a string',
    'This is a { in a string',
    'This is a } in a string'
  ]
  data.each{ d ->
    if (d =~ /\"(.*)\"/) {
      logInfo('A', "Found quoted text for ${d}")
    }
    switch (d) {
      case ~/\"(.*)\"/:
        logInfo('B', "Found quoted text for ${d}")
        break
    }
  }
  */
  while (pro2IR_hasUnprocessedRows(irMap)) {
    String rowN = paddedRowNumber(irMap)
    String row = pro2IR_NextNonNullRow(irMap)
    // matcher[0] is the first match object.
    // matcher[0][0] is everything that matched in this match.
    // matcher[0][1] is the first capture in this match.
    // matcher[0][n] is the n capture in this match.
    switch (row) {
      case ~/.*(Buttons).*/:
        rowInfo << "${rowN}: [${indentLevel}] B U T T O N S .. ${Matcher.lastMatcher.group(1)}"
        break
      case ~/\"([^"]*)\":\s+(d+)"/ :
        String label = Matcher.lastMatcher.group(1)
        String iVal = Matcher.lastMatcher.group(1)
        rowInfo << "${rowN}: [${indentLevel}] ${label} = ${iVal}"
        break
      case ~/\"([^"]*)\":\s+\"([^"]*)\""/ :
        String label = Matcher.lastMatcher.group(1)
        String sVal = Matcher.lastMatcher.group(1)
        rowInfo << "${rowN}: [${indentLevel}] ${label} = ${sVal}"
        break
      case ~/\"([^"]*)\":\s+\{$/ :
        String label = Matcher.lastMatcher.group(1)
        rowInfo << "${rowN}: [${indentLevel}] ${label} START MAP"
        ++indentLevel
        break
      case ~/\}/ :
        rowInfo << "${rowN}: [${indentLevel}] END MAP"
        --indentLevel
        break
      case ~/\"([^"]*)\":\s+\[$/ :
        String label = Matcher.lastMatcher.group(1)
        rowInfo << "${rowN}: [${indentLevel}] ${label} START LIST"
        ++indentLevel
        break
      case ~/\]/ :
        rowInfo << "${rowN}: [${indentLevel}] END LIST"
        --indentLevel
        break
      default:
        rowInfo << "${rowN}: [${indentLevel}] DEFAULT ${row}"
    }
    if (row.contains('}')) { ++indentLevel }
  }
  logInfo('parsePro2IntegRpt', rowInfo)
  //----> }
  //----> populateRa2Devices(results)
  return results
}
