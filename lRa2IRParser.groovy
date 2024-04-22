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

Boolean kpadSupportsLedEvents(String ra2Model) {
  return [
    'RR-MAIN-REP-WH',
    'RR-T15RL-SW',
    'RRD-H6BRL-WH',
    'RRD-W7B-WH'
  ].findAll{ it == ra2Model } ? true : false
}

String normalizeCsv(String rawRow) {
  return rawRow?.trim()?.replaceAll(', ', ',')
}

Boolean ra2IR_hasUnprocessedRows(Map irMap) {
  return (irMap.row + 1 < irMap.rowCols.size())
}

ArrayList ra2IR_CurrRow(Map irMap) {
  return irMap.rowCols[irMap.row]
}

ArrayList ra2IR_NextRow(Map irMap) {
  ArrayList result
  if (ra2IR_hasUnprocessedRows(irMap)) {
    irMap.row = irMap.row + 1
    result = irMap.rowCols[irMap.row]
  } else {
    result = ['EOF']
  }
  return result
}

Map parseKeypads(Map irMap) {
  logInfo('parseKeypads', "KPAD_COLS: ${ra2IR_CurrRow(irMap)}")
  ArrayList cols = []
  // This inner while loop expects a Keypad sticky row OR Keypad body row.
  while (
    (cols = ra2IR_NextRow(irMap)) != EOF
    && (
      (cols[0] && cols.size() == 5)
      || !cols[0]                             // NOTE: cols.size() can vary
    )
  ) {
    if (cols[0]) {
      logInfo('parseKeypads', "HEADER: ${cols} (${cols.size()})")
    } else {
      logInfo('parseKeypads', "BODY: ${cols} (${cols.size()})")
    }
  }
  return [:]
}

Map parseRooms(Map irMap) {
  logInfo('parseRooms', "ROOM_COLS: ${ra2IR_CurrRow(irMap)}")
  return [:]
}

Map parseDevices(Map irMap) {
  logInfo('parseDevices', "DEVICE_COLS: ${ra2IR_CurrRow(irMap)}")
  return [:]
}

Map parseTimeClocks(Map irMap) {
  logInfo('parseTimeClocks', "TIME_CLOCKCOLS: ${ra2IR_CurrRow(irMap)}")
  return [:]
}

Map parseGreenModes(Map irMap) {
  logInfo('parseGreenModes', "GREEN_MODE_COLS: ${ra2IR_CurrRow(irMap)}")
  return [:]
}

Map parseRa2IntegRpt(String lutronIntegrationReport) {
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
  ArrayList RA2_EXPECTED_HEADER_ROW = ['RadioRA 2 Integration Report']
  ArrayList KPAD_COLS = ['Device Room','Device Location','Device name',
    'Model','ID','Component','Component Number','Name']
  ArrayList ROOM_COLS = ['Room','ID']
  ArrayList DEVICE_COLS = ['Zone Room','Zone Name','ID']
  ArrayList TIME_CLOCK_COLS = ['Timeclock','ID','Event','Event Index']
  ArrayList GREEN_MODE_COLS = ['Green Mode','ID','Mode Name','Step Number']
  ArrayList EOF = ['EOF']
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
  // The following irMap facilitates navigation of an Integration Report (IR)
  //   rowCols: The original IR string is first tokenized into an ArrayList
  //            of rows (with empty rows removed). Each row is subsequently
  //            CSV normalized and split into columns (retaining empty columns).
  //            Thus 'rowCols' is an ArrayList of rows where each row is an
  //            ArrayList of (possibly empty) columns.
  //       row: The 'current' row (rowCols index) which advances as as IR
  //            data is parsed.
  Map irMap = [
    rowCols: lutronIntegrationReport.tokenize('\n').collect { row ->
      normalizeCsv(row).split(',')
    },
    row: 0
  ]
  // Confirm that the expected Integration Report header is present.
  ArrayList actualHeaderRow = ra2IR_CurrRow(irMap)
  if (actualHeaderRow == RA2_EXPECTED_HEADER_ROW) {
    ArrayList cols = []
    // The outer while loop identifies the table data that is being extracted.
    while ((cols = ra2IR_NextRow(irMap)) != EOF) {
      switch (cols) {
        case { it == KPAD_COLS }:
          parseKeypads(irMap)
          break
        case { it == ROOM_COLS }:
          parseRooms(irMap)
          break
        case { it == DEVICE_COLS }:
          parseDevices(irMap)
          break
        case { it == TIME_CLOCK_COLS }:
          parseTimeClocks(irMap)
          break
        case { it == GREEN_MODE_COLS }:
          parseGreenModes(irMap)
          break
        default:
          if (cols[0]) {
            logInfo('#140', "STRANDED STICKY ROW: ${cols}")
          } else {
            logInfo('#142', "STRANDED BODY ROW: ${cols}")
          }
      }
    }
    /*
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
        logInfo('#140', "STRANDED STICKY ROW: ${cols}")
      } else {
        logInfo('#142', "STRANDED BODY ROW: ${cols}")
      }
    }
    */

    /*****
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
  return results
}
