// ---------------------------------------------------------------------------------
// ( L U T R O N )   R A 2
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

import com.hubitat.hub.domain.Event as Event
#include wesmc.lHExt
#include wesmc.lHUI

definition (
  name: 'Test-lRa2',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Preview lRa2 functionality',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(name: 'Ra2Page')
}

String ra2ModelToCode(String model) {
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

//----
//---- USER INTERFACE SUPPORT
//----

void solicitRa2IntegrationReport() {
  input(
    name: 'ra2IntegReport',
    title: 'Paste in the Lutron RA2 Integration Report',
    type: 'textarea',
    rows: 5,
    submitOnChange: true,
    required: true,
    multiple: false
  )
}

Map Ra2Page() {
  return dynamicPage(
    name: 'Ra2Page',
    title: [
      heading1("Ra2 Test Page - ${app.id}"),
      bullet1('Click <b>Done</b> to enable subscriptions.')
    ].join('<br/>'),
    install: true,
    uninstall: true
  ) {
    //---------------------------------------------------------------------------------
    // REMOVE NO LONGER USED SETTINGS AND STATE
    //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    //-> Prefer settingsRemoveAndLog() over app.removeSetting('..')
    //-> Prefer stateRemoveAndLog() over state.remove('..')
    //---------------------------------------------------------------------------------
    //-> stateAndSessionCleanup()
    app.updateLabel('Ra2TestPage')
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      state.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
      //-> idRa2Repeaters()
      solicitRa2IntegrationReport()
      paragraph([
        heading1('Debug<br/>'),
        *appStateAsBullets(true),
        //*appSettingsAsBullets(true)
      ].join('<br/>'))
    }
  }
}

//----
//---- SYSTEM CALLBACKS
//----

void installed() {
  logWarn('installed', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled() {
  logWarn('uninstalled', 'Entered')
  removeAllChildApps()
}

void updated() {
  logWarn('updated', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void initialize() {
  // - The same keypad may be associated with two different, specialized handlers
  //   (e.g., mode changing buttons vs special functionalily buttons).
  logWarn('initialize', 'Entered')
  settings.ra2Repeaters.each{ device ->
  logInfo('initialize', "Subscribing >${device}< to ra2RepHandler")
    subscribe(device, ra2RepHandler, ['filterEvents': true])
  }
  parseRa2IntegRpt()
}

String normalizeCsv(String raw) {
  raw?.trim()?.replaceAll(', ', ',')
}

Boolean kpadSupportsLedEvents(String ra2Model) {
  return [
    'RR-MAIN-REP-WH',
    'RR-T15RL-SW',
    'RRD-H6BRL-WH',
    'RRD-W7B-WH'
  ].findAll{ it == ra2Model } ? true : false
}

void parseRa2IntegRpt() {
  ArrayList ra2IntegrationConfig = []
  Map ra2Data = [:]
  ra2Data.kpads = [:]
  ra2Data.rooms = [:]      // NOTE: Ra2 Rooms != Hubitat Rooms
  ra2Data.circuits = [:]
  ra2Data.timeclock = [:]
  ra2Data.green = []
  // Create an array of report rows, perserving empty rows.
  String[] rawIntegRpt = settings.ra2IntegReport.split('\n')
  //ArrayList rawIntegRpt = settings.ra2IntegReport.split('\n').toList()
  // Confirm that the expected Integration Report header is present.
  Integer row = 0
  String expectHeader = 'RadioRA 2 Integration Report'
  String rowData = rawIntegRpt[row++]
logInfo(">A<", "row: ${row}, rowData: >${rowData}<")
  if (expectHeader.equals(actualHeader)) {
    logError('parseRa2IntegRpt', [
      'At initial row ...',
      ">${expectHeader}<, Expected Header",
      ">${rowData}<, Actual Header"
    ])
  } else {
    // The Outerloop Identifies the Extraction Mode
    String kpadCols = 'Device Room,Device Location,Device name,Model,ID,Component,Component Number,Name'
    String roomCols = 'Room,ID'
    String deviceCols = 'Zone Room,Zone Name,ID'
    String timeclockCols = 'Timeclock,ID,Event,Event Index'
    String greenModeCols = 'Green Mode,ID,Mode Name,Step Number'
    while (row < rawIntegRpt.size()) {
      rowData = normalizeCsv(rawIntegRpt[row++])
logInfo(">B<", "row: ${row}, rowData: >${rowData}<")
      switch(rowData) {
        case kpadCols:
          //---- Extract Keypads (repeaters, wall kpads, picos, motion sensors)
          //---- to be separated by blank lines.
          Boolean kpadFound = true
          while (kpadFound) {
            // BEGIN EXTRACTION OF A SINGLE KEYPAD
            String ra2Name = null
            // Consume leading empty rows.
            while ({
              rowData = normalizeCsv(rawIntegRpt[++row])
logInfo(">C<", "row: ${row}, rowData: >${rowData}<")
              (row < rawIntegRpt.size() && rowData == '')
            }()) continue
            String[] cols = rowData.split(',')
            // Proceed only if the expected number of columns exist
            if (cols.size() != 5) {
              kpadFound = false
            } else {
              String[] colsNoLeadingNulls = cols.dropWhile{ it == '' }
              String ra2Model
              if (cols.size() == colsNoLeadingNulls.size()) {
                // Construct a 'Hubitat Label' for the keypad.
                String hubType = ra2ModelToCode(cols[3])
                String ra2Id = cols[4]
                ra2Name = cols[1]
                // Apply special handling for repeater Hubitat Labels.
                if (ra2Name == 'Enclosure Device 001') { ra2Name = cols[1] }
                hubDeviceLabel = "${ra2Name} (ra2-${ra2Id.trim()})"
                // Create a Repeater entry for the Lutron Integrator instance.
                ra2IntegrationConfig << "'${hubType},${ra2Id},${hubDeviceLabel}'"
                // Create a Repeater entry in the Add keypad top-level data.
                ra2Model = "${cols[3]}"
                ra2Data.kpads."${hubDeviceLabel}" = [
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
                ra2Data.kpads."${hubDeviceLabel}".buttonEvents = [:]
                while ({
                  rowData = normalizeCsv(rawIntegRpt[++row])
logInfo(">D<", "row: ${row}, rowData: >${rowData}<")
                  (row < rawIntegRpt.size() && rowData != '')
                }()) {
                  String[] buttonCols = rowData.split(',')?.dropWhile{ it == '' }
                  if (buttonCols.size() == 3) {
                    String eventName = buttonCols[0].find(/Button/) ? "buttonLed-${buttonCols[1]}" : ''
                    ArrayList sceneData = buttonCols[2].tokenize(' ')
                    if (sceneData.size() == 2) {
                      String hubitatRoom = sceneData[0]
                      String scene = sceneData[1]
                      ra2Data.kpads?."${hubDeviceLabel}".buttonEvents."${eventName}" = [
                        'hubitatRoom': hubitatRoom,
                        'scene': scene
                      ]
                    }
                  }
                }
              } else {
                // Toss additional device rows.
                while ({
                  rowData = normalizeCsv(rawIntegRpt[++row])
logInfo(">E<", "row: ${row}, rowData: >${rowData}<")
                  (row < rawIntegRpt.size() && rowData != '')
                }()) {}
              }
            }
            // END EXTRACTION OF A SINGLE KEYPAD
          }
          break
        case roomCols:
          logInfo('parseRa2IntegRpt', "At #${row}: Parsing Rooms")
          Boolean roomFound = true
          while (roomFound) {
            // Consume leading empty rows.
            while ({
              rowData = normalizeCsv(rawIntegRpt[++row])
logInfo(">F<", "row: ${row}, rowData: >${rowData}<")
              (row < rawIntegRpt.size() && rowData == '')
            }()) continue
            // BEGIN EXTRACTION OF ROOM DATA
            String[] cols = rowData.split(',')      // RhsBath, 2
            // Proceed only if the expected number of columns exist
            if (cols.size() != 2) {
              logInfo('BAILING ROOM DATA', "row: ${row} cols: >${cols}<")
              roomFound = false
            } else {
              logInfo('parseRa2IntegRpt', "At #${row}, Id: ${cols[1]} Room: ${cols[0]}")
              ra2Data.rooms."${cols[1]}" = cols[0]  // ID -> ROOM
            }
            // END EXTRACTION OF ROOM DATA
          }
          break

        case deviceCols:
          logInfo('parseRa2IntegRpt', "At #${row}: Parsing Circuits")
          // Find next non-null data row.
          while ({
            rowData = normalizeCsv(rawIntegRpt[++row])
logInfo(">G<", "row: ${row}, rowData: >${rowData}<")
            (row < rawIntegRpt.size() && rowData == '')
          }()) continue
          String[] cols = rowData.split(',')      // Den, Kitchen-Soffit, 4
          if (cols.size() == 3) {
            ArrayList zoneData = cols[1].tokenize('-')
            if(zoneData.size() == 2) {
              ra2Data.circuits."${cols[2]}" = [
                'room': zoneData[0],
                'scene': zoneData[1]
              ]
            } else {
              logWarn('parseRa2IntegRpt', "Expected <room>-<scene>, got >${cols[1]}<")
            }
            ra2Data.rooms."${cols[1]}" = cols[0]  // ID -> ROOM
          }
          break
        case timeclockCols:
          logInfo('parseRa2IntegRpt', "At #${row}: Parsing Timeclocks")
          // String timeclockCols = 'Timeclock,ID,Event,Event Index'
          // Project Timeclock, 25
          // , , New Event 002,1
          break
        case greenModeCols:
          logInfo('parseRa2IntegRpt', "At #${row}: Parsing Greenmodes")
          // String greenModeCols = 'Green Mode,ID,Mode Name,Step Number'
          // , , Off, 1
          // , , Green Mode, 2
          break
        default:
          if (rowData) {
            logWarn('parseRa2IntegRpt', "No action at #${row} for >${rowData}<")
          } else {
            logWarn('parseRa2IntegRpt', "Nothing at #${row}")
          }
      }
      rowData = normalizeCsv(rawIntegRpt[++row])
logInfo(">H<", "row: ${row}, rowData: >${rowData}<")
    }
    // REVIEW EXTRACTED KPADS
    ra2Data.kpads.each{ k, v -> logInfo('KPAD-DATA', "${k} => ${v}") }
    // REVIEW EXTRACTED ROOMS
    ra2Data.rooms.each{ k, v -> logInfo('ROOM-DATA', "${k} => ${v}") }
  }
}
