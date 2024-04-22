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
  name: 'lRa2',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Isolate RA2 Methods',
  category: 'general purpose'
)

String ra2ModelToHubitatCode(String model) {
  switch (model) {
    case 'LRF2-OCR2B-P-WH':
      return 'm'
    case 'PJ2-3BRL-GWH-L01':
      return 'q'
    case 'RR-MAIN-REP-WH':
    case 'RR-T15RL-SW':
    case 'RR-VCRX-WH':
      return 'k'
    case 'RRD-H6BRL-WH':
    case 'RRD-W7B-WH':
      return 'w'
    default:
      return 'unknown'
  }
}

String normalizeCsv(String rawRow) {
  return rawRow?.trim()?.replaceAll(', ', ',')
}

Boolean ra2IR_hasUnprocessedRows(Map irMap) {
  return (irMap.row + 1 < irMap.rows.size())
}

ArrayList ra2IR_CurrRow(Map irMap) {
  return irMap.rows[irMap.row]
}

ArrayList ra2IR_NextRow(Map irMap) {
  ArrayList result
  if (ra2IR_hasUnprocessedRows(irMap)) {
    irMap.row = irMap.row + 1
    result = irMap.rows[irMap.row]
  } else {
    result = [ 'EOF' ]
  }
  return result
}

Boolean colsMatch(ArrayList left, ArrayList right) {
  Boolean result = (left == right)
  logInfo('colsMatch', ['',
    "left: ${left}",
    "right: ${right}",
    "result: ${result}",
  ])
  return result
}

Map parseRa2IntegRpt() {
  // This method processes "settings.ra2IntegReport" and returns a Map with
  // the following keys:
  //      rA2Devices - A multi-row String that can be used in the "Lutron Integrator"
  //                   App to make Hubitat aware of RA2 devices. Said App expects
  //                   triplets in the form "code,id,name", where:
  //                     'code' informs the devices Type
  //                     'id' is used as the DeviceName (aka event.deviceId)
  //                     'name' is used as the DeviceLabel (aka event.name)
  //   buttonToScene - A nested Map in the form:
  //                   repDevId -> buttonNumber -> room -> scene
  //   sceneToButton - A nested Map in the form:
  //                   room -> scene -> repDevId -> buttonNumber
  //           kpads - RA2 keypad details
  //        ra2Rooms - RA2 rooms (which ARE NOT the same as Hubitat rooms)
  //        circuits - RA2 circuits that can be switched or dimmed
  //       timeclock - RA2 timeclock data
  //           green - RA2 green mode data
  // Internally, this method and supporting methods leverage the temporary
  // Map "irMap", which has two keys:
  //            rows - An ArrayList of ra2IntegReport rows, but which excludes
  //                   empty rows and brings rows into proper CSV conformance.
  //             row - The current 'rows' index, which advances as rows are
  //                   processed.
  ArrayList RA2_EXPECTED_HEADER_ROW = ['RadioRA 2 Integration Report']
  ArrayList KPAD_COLS = ['Device Room','Device Location','Device name','Model','ID','Component','Component Number','Name']
  ArrayList ROOM_COLS = ['Room','ID']
  ArrayList DEVICE_COLS = ['Zone Room','Zone Name','ID']
  ArrayList TIME_CLOCK_COLS = ['Timeclock','ID','Event','Event Index']
  ArrayList GREEN_MODE_COLS = ['Green Mode','ID','Mode Name','Step Number']
  Map results = [
    rA2Devices: '',
    buttonToScene: [:],
    sceneToButton: [:],
    kpads: [:],
    ra2Rooms: [],
    circuits: [],
    timeclock: [],
    green: []
  ]
  Map irMap = [
    rows: settings.ra2IntegReport
          .tokenize('\n')                   // Tokenize removes empty rows.
          .collect { row ->
            normalizeCsv(row).split(',')    // Split preserves empty cols.
          },
    row: 0
  ]
  //-> logInfo('parseRa2IntegRpt', "ra2IR_CurrRow()[0]: ${ra2IR_CurrRow(irMap)[0]}")
  // Confirm that the expected Integration Report header is present.
  ArrayList actualHeaderRow = ra2IR_CurrRow(irMap)
  if (colsMatch(actualHeaderRow, RA2_EXPECTED_HEADER_ROW)) {
    ArrayList cols = []
    while ((cols = ra2IR_NextRow(irMap)) != [ 'EOF' ]) {
      if (cols == KPAD_COLS) {
        logInfo('#130', "KPAD_COLS: ${cols}")
      } else if (cols == ROOM_COLS) {
        logInfo('#132', "ROOM_COLS: ${cols}")
      } else if (cols == DEVICE_COLS) {
        logInfo('#134', "DEVICE_COLS: ${cols}")
      } else if (cols == TIME_CLOCK_COLS) {
        logInfo('#136', "TIME_CLOCKCOLS: ${cols}")
      } else if (cols == GREEN_MODE_COLS) {
        logInfo('#138', "GREEN_MODE_COLS: ${cols}")
      } else if (cols[0]) {
        logInfo('#140', "STICKY: ${cols}")
      } else {
        logInfo('#142', "BODY: ${cols}")
      }
    }
    /*****
    // The Outerloop Identifies the Extraction Mode
    // DO NOT DEPEND ON EMPTY ROWS. INSTEAD WATCH FOR COL COUNT CHANGES, ETC.
    //--> rowData = ra2IR_nextNonEmptyRow()
    while (rowData != 'EOF') {
      logInfo('parseRa2IntegRpt', "Processing >${rowData}<")
      switch (rowData) {
        case KPAD_COLS:
          //---- Extract Keypads (repeaters, wall kpads, picos, motion sensors)
          //---- which are separated by blank lines.
          logInfo('parseRa2IntegRpt', 'Parsing Keypads')
          Boolean kpadFound = true
          while (kpadFound) {
            // BEGIN EXTRACTION OF A SINGLE KEYPAD
            String ra2Name = null
            // DO NOT DEPEND ON EMPTY ROWS. INSTEAD WATCH FOR COL COUNT CHANGES, ETC.
            //--> String[] cols = ra2IR_nextNonEmptyRowAsCols()
            // Proceed only if the expected number of columns exist
            if (cols.size() != 5) {
              kpadFound = false
            } else {
              String[] colsNoLeadingNulls = cols.dropWhile { col -> col == '' }
              String ra2Model
              if (cols.size() == colsNoLeadingNulls.size()) {
                // Construct a 'Hubitat Label' for the keypad.
                String hubType = ra2ModelToHubitatCode(cols[3])
                String ra2Id = cols[4]
                ra2Name = cols[1]
                // Apply special handling for repeater Hubitat Labels.
                if (ra2Name == 'Enclosure Device 001') { ra2Name = cols[1] }
                hubDeviceLabel = "${ra2Name} (ra2-${ra2Id.trim()})"
                // Create a Repeater entry for the Lutron Integrator instance.
                results.ra2Devices << "'${hubType},${ra2Id},${hubDeviceLabel}'"
                // Create a Repeater entry in the Add keypad top-level data.
                ra2Model = "${cols[3]}"
                results.kpads."${hubDeviceLabel}" = [
                  'ra2Room': "${cols[0]}",
                  'ra2Location': "${cols[1]}",
                  'ra2DeviceName': "${cols[2]}",
                  'ra2Model': ra2Model,
                  'ra2Id': "${cols[4]}"
                ]
              }
              // Add buttonEvents w/ "buttonLed-#"" keys when:
              //   - The keypad device supports LEDs (real or virtual).
              //   - The keypad's button is labeled and format:
              //     |---------+-------------------------------+-------------------|
              //     | INCLUDE |            FORMAT             |      EXAMPLE      |
              //     |---------+-------------------------------+-------------------|
              //     |  YES    |       "<room> <scene>"        |  "DenLamp Chill"  |
              //     |  NO     | "<alt-button> <room> <scene>" | "#15 DenLamp Day" |
              //     |---------+-------------------------------+-------------------|
              if (kpadSupportsLedEvents(ra2Model)) {
                results.kpads."${hubDeviceLabel}".buttonEvents = [:]
                while (rowData = ra2IR_NextRow()) {
                  String[] buttonCols = rowData.split(',')?.dropWhile{ it == '' }
                  if (buttonCols.size() == 3) {
                    String eventName = buttonCols[0].find(/Button/) ? "buttonLed-${buttonCols[1]}" : ''
                    ArrayList sceneData = buttonCols[2].tokenize(' ')
                    if (sceneData.size() == 2) {
                      String hubitatRoom = sceneData[0]
                      String scene = sceneData[1]
                      results.kpads?."${hubDeviceLabel}".buttonEvents."${eventName}" = [
                        'hubitatRoom': hubitatRoom,
                        'scene': scene
                      ]
                    }
                  }
                }
              } else {
                while (rowData = ra2IR_NextRow()) {
                  // Tossing an unused device row.
                  logInfo('parseRa2IntegRpt', "Tossing >${rowData}<")
                }
              }
            }
            // END EXTRACTION OF A SINGLE KEYPAD
          }
          break
        case ROOM_COLS:
          logInfo('parseRa2IntegRpt', 'Parsing Rooms')
          Boolean roomFound = true
          while (roomFound) {
            // BEGIN EXTRACTION OF ROOM DATA
            // DO NOT DEPEND ON EMPTY ROWS. INSTEAD WATCH FOR COL COUNT CHANGES, ETC.
            //--> String[] cols = ra2IR_nextNonEmptyRowAsCols()  // RhsBath, 2
            // Proceed only if the expected number of columns exist
            if (cols.size() == 2) {
              logInfo('parseRa2IntegRpt', "At #${row}, Id: ${cols[1]} Room: ${cols[0]}")
              results.room."${cols[1]}" = cols[0]  // ID -> ROOM
            } else {
              logInfo('BAILING ROOM DATA', "row: ${row} cols: >${cols}<")
              roomFound = false
            }
            // END EXTRACTION OF ROOM DATA
          }
          break
        case DEVICE_COLS:
          logInfo('parseRa2IntegRpt', 'Parsing Circuits')
          // DO NOT DEPEND ON EMPTY ROWS. INSTEAD WATCH FOR COL COUNT CHANGES, ETC.
          //--> String[] cols = ra2IR_nextNonEmptyRowAsCols()  // Den, Kitchen-Soffit, 4
          if (cols.size() == 3) {
            ArrayList zoneData = cols[1].tokenize('-')
            if(zoneData.size() == 2) {
              results.circuits."${cols[2]}" = [
                'room': zoneData[0],
                'scene': zoneData[1]
              ]
            } else {
              logWarn('parseRa2IntegRpt', "Expected <room>-<scene>, got >${cols[1]}<")
            }
          }
          break
        case TIME_CLOCK_COLS:
          logInfo('parseRa2IntegRpt', 'Parsing Timeclocks')
          // String TIME_CLOCK_COLS = 'Timeclock,ID,Event,Event Index'
          // Project Timeclock, 25
          // , , New Event 002,1
          break
        case GREEN_MODE_COLS:
          logInfo('parseRa2IntegRpt', 'Parsing Greenmodes')
          // String GREEN_MODE_COLS = 'Green Mode,ID,Mode Name,Step Number'
          // , , Off, 1
          // , , Green Mode, 2
          break
        default:
          logWarn('parseRa2IntegRpt', "At Default, ignoring ${rowData}")
          // DO NOT DEPEND ON EMPTY ROWS. INSTEAD WATCH FOR COL COUNT CHANGES, ETC.
          //--> rowData = ra2IR_nextNonEmptyRow()
      }
      logInfo('parseRa2IntegRpt', "At end of while >${ra2IR_CurrRow()}<")
      // O L D -> rowData = normalizeCsv(rawIntegRpt[++row])
    }
    // REVIEW EXTRACTED KPADS
    results.kpads.each{ k, v -> logInfo('KPAD-DATA', "${k} => ${v}") }
    // REVIEW EXTRACTED ROOMS
    results.room.each{ k, v -> logInfo('ROOM-DATA', "${k} => ${v}") }
    *****/
  } else {
    logError('parseRa2IntegRpt', [
      '',
      "expected header: ${RA2_EXPECTED_HEADER_ROW}",
      "actual header: ${actualHeaderRow}"
    ])
  }
}