
// ---------------------------------------------------------------------------------
// W H O L E   H O U S E   A U T O M A T I O N
//
//   Copyright (C) 2023-Present Wesley M. Conner
//
//   LICENSE
//     Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
//     "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
//     not use this file except in compliance with the License. Unless
//     required by applicable law or agreed to in writing, software
//     distributed under the License is distributed on an "AS IS" BASIS,
//     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
//     implied.
// ---------------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Location as Loc

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include wesmc.lFifo
#include wesmc.lHExt
#include wesmc.lHUI
#include wesmc.lLut
#include wesmc.lPbsg

definition (
  name: 'WHA',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Whole House Automation using Modes, RA2 and Room Overrides',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  singleInstance: true
)

preferences {
  page(name: 'WhaPage')
}

state.hubitatIdToRepeaterData = [
  '6825': [
    type: 'ra2',
    nativeId: '1',
    buttons: [
      [button: 1, room: 'WHA', scene: 'Chill'],
      [button: 2, room: 'WHA', scene: 'Clean'],
      [button: 3, room: 'WHA', rsvdScene: 'Day', altButton: 5],
      [button: 4, room: 'WHA', scene: 'Night'],
      [button: 5, room: 'WHA', scene: 'Off'],
      [button: 6, room: 'WHA', scene: 'Party'],
      [button: 7, room: 'WHA', scene: 'Supp'],
      [button: 8, room: 'WHA', scene: 'TV'],
      [button: 11, room: 'DenLamp', scene: 'Chill'],
      [button: 12, room: 'DenLamp', scene: 'Clean'],
      [button: 13, room: 'DenLamp', rsvdScene: 'Day', altButton: 15],
      [button: 14, room: 'DenLamp', rsvdScene: 'Night', altButton: 15],
      [button: 15, room: 'DenLamp', scene: 'Off'],
      [button: 16, room: 'DenLamp', scene: 'Party'],
      [button: 17, room: 'DenLamp', scene: 'Supp'],
      [button: 18, room: 'DenLamp', rsvdScene: 'TV', altButton: 15],
      [button: 21, room: 'Kitchen', scene: 'Chill'],
      [button: 22, room: 'Kitchen', scene: 'Clean'],
      [button: 23, room: 'Kitchen', rsvdScene: 'Day', altButton: 25],
      [button: 24, room: 'Kitchen', rsvdScene: 'Night', altButton: 25],
      [button: 25, room: 'Kitchen', scene: 'Off'],
      [button: 26, room: 'Kitchen', scene: 'Party'],
      [button: 27, room: 'Kitchen', scene: 'Supp'],
      [button: 28, room: 'Kitchen', scene: 'TV'],
      [button: 29, room: 'Kitchen', scene: '_Cook'],
      [button: 41, room: 'Den', scene: 'Chill'],
      [button: 42, room: 'Den', scene: 'Clean'],
      [button: 43, room: 'Den', rsvdScene: 'Day', altButton: 45],
      [button: 44, room: 'Den', rsvdScene: 'Night', altButton: 45],
      [button: 45, room: 'Den', scene: 'Off'],
      [button: 46, room: 'Den', scene: 'Party'],
      [button: 47, room: 'Den', scene: 'Supp'],
      [button: 48, room: 'Den', scene: 'TV'],
      [button: 51, room: 'Guest', scene: 'Chill'],
      [button: 52, room: 'Guest', scene: 'Clean'],
      [button: 53, room: 'Guest', rsvdScene: 'Day', altButton: 55],
      [button: 54, room: 'Guest', rsvdScene: 'Night', altButton: 55],
      [button: 55, room: 'Guest', scene: 'Off'],
      [button: 56, room: 'Guest', scene: 'Party'],
      [button: 57, room: 'Guest', scene: 'Supp'],
      [button: 58, room: 'Guest', scene: 'TV'],
      [button: 60, room: 'LhsBath', scene: 'Chill'],
      [button: 62, room: 'LhsBath', scene: 'Clean'],
      [button: 63, room: 'LhsBath', scene: 'Day'],
      [button: 64, room: 'LhsBath', scene: 'Night'],
      [button: 65, room: 'LhsBath', scene: 'Off'],
      [button: 66, room: 'LhsBath', scene: 'Party'],
      [button: 67, room: 'LhsBath', scene: 'Supp'],
      [button: 68, room: 'LhsBath', scene: 'TV'],
      [button: 70, room: 'RhsBath', scene: 'Chill'],
      [button: 72, room: 'RhsBath', scene: 'Clean'],
      [button: 73, room: 'RhsBath', scene: 'Day'],
      [button: 74, room: 'RhsBath', scene: 'Night'],
      [button: 75, room: 'RhsBath', scene: 'Off'],
      [button: 76, room: 'RhsBath', scene: 'Party'],
      [button: 77, room: 'RhsBath', scene: 'Supp'],
      [button: 78, room: 'RhsBath', scene: 'TV'],
      [button: 79, room: 'Main', scene: 'Chill'],
      [button: 82, room: 'Main', rsvdScene: 'Clean', altButton: 85],
      [button: 83, room: 'Main', rsvdScene: 'Day', altButton: 85],
      [button: 84, room: 'Main', scene: 'Night'],
      [button: 85, room: 'Main', scene: 'Off'],
      [button: 86, room: 'Main', scene: 'Party'],
      [button: 87, room: 'Main', scene: 'Supp'],
      [button: 88, room: 'Main', scene: 'TV']
    ]
  ],
  '6892': [
    type: 'ra2',
    nativeId: '83',
    buttons: [
      [button: 1, room: 'WHA', scene: '_ALARM'],
      [button: 2, room: 'WHA', scene: '_AUTO'],
      [button: 3, room: 'WHA', scene: '_AWAY'],
      [button: 4, room: 'WHA', scene: '_FLASH'],
      [button: 5, room: 'WHA', scene: '_PANIC'],
      [button: 6, room: 'WHA', scene: '_QUIET'],
      [button: 10, room: 'PrimBath', scene: 'Chill'],
      [button: 12, room: 'PrimBath', scene: 'Clean'],
      [button: 13, room: 'PrimBath', rsvdScene: 'Day', altButton: 15],
      [button: 14, room: 'PrimBath', rsvdScene: 'Night', altButton: 15],
      [button: 15, room: 'PrimBath', scene: 'Off'],
      [button: 16, room: 'PrimBath', scene: 'Party'],
      [button: 17, room: 'PrimBath', scene: 'Supp'],
      [button: 18, room: 'PrimBath', scene: 'TV'],
      [button: 21, room: 'PrimBdrm', scene: 'Chill'],
      [button: 22, room: 'PrimBdrm', scene: 'Clean'],
      [button: 23, room: 'PrimBdrm', rsvdScene: 'Day', altButton: 25],
      [button: 24, room: 'PrimBdrm', rsvdScene: 'Night', altButton: 25],
      [button: 25, room: 'PrimBdrm', scene: 'Off'],
      [button: 26, room: 'PrimBdrm', scene: 'Party'],
      [button: 27, room: 'PrimBdrm', scene: 'Supp'],
      [button: 28, room: 'PrimBdrm', scene: 'TV'],
      [button: 41, room: 'LhsBdrm', scene: 'Chill'],
      [button: 42, room: 'LhsBdrm', scene: 'Clean'],
      [button: 43, room: 'LhsBdrm', rsvdScene: 'Day', altButton: 45],
      [button: 44, room: 'LhsBdrm', rsvdScene: 'Night', altButton: 45],
      [button: 45, room: 'LhsBdrm', scene: 'Off'],
      [button: 46, room: 'LhsBdrm', scene: 'Party'],
      [button: 47, room: 'LhsBdrm', rsvdScene: 'Supp', altButton: 41],
      [button: 48, room: 'LhsBdrm', scene: 'TV'],
      [button: 51, room: 'Office', scene: 'Chill'],
      [button: 52, room: 'Office', scene: 'Clean'],
      [button: 53, room: 'Office', rsvdScene: 'Day', altButton: 55],
      [button: 54, room: 'Office', rsvdScene: 'Night', altButton: 55],
      [button: 55, room: 'Office', scene: 'Off'],
      [button: 56, room: 'Office', scene: 'Party'],
      [button: 57, room: 'Office', scene: 'Supp'],
      [button: 58, room: 'Office', scene: 'TV'],
      [button: 61, room: 'Yard', scene: 'Chill'],
      [button: 62, room: 'Yard', rsvdScene: 'Clean', altButton: 65],
      [button: 63, room: 'Yard', rsvdScene: 'Day', altButton: 65],
      [button: 64, room: 'Yard', scene: 'Night'],
      [button: 65, room: 'Yard', scene: 'Off'],
      [button: 66, room: 'Yard', rsvdScene: 'Party', altButton: 61],
      [button: 67, room: 'Yard', rsvdScene: 'Supp', altButton: 61],
      [button: 68, room: 'Yard', rsvdScene: 'TV', altButton: 61],
      [button: 71, room: 'Lanai', scene: 'Chill'],
      [button: 72, room: 'Lanai', rsvdScene: 'Clean', altButton: 75],
      [button: 73, room: 'Lanai', rsvdScene: 'Day', altButton: 75],
      [button: 74, room: 'Lanai', scene: 'Night'],
      [button: 75, room: 'Lanai', scene: 'Off'],
      [button: 76, room: 'Lanai', scene: 'Party'],
      [button: 77, room: 'Lanai', scene: 'Supp'],
      [button: 78, room: 'Lanai', scene: 'TV'],
      [button: 79, room: 'Lanai', scene: '_Games']
    ],
  ],
  '9129': [
    type: 'pro2',
    nativeId: '1',
    buttons: [
      [button: 1, room: 'Lanai', scene: 'Chill'],
      [button: 2, room: 'Lanai', scene: 'Cleaning'],
      [button: 3, room: 'Lanai', scene: 'Day'],
      [button: 4, room: 'Lanai', scene: 'Games'],
      [button: 5, room: 'Lanai', scene: 'Night'],
      [button: 6, room: 'Lanai', scene: 'Party'],
      [button: 7, room: 'Lanai', scene: 'Supplement'],
      [button: 8, room: 'Lanai', scene: 'TV']
    ]
  ]
]

Map getRoomSceneForRepButton (Long repHubId, String button) {
  return state.repButtonToRS."${repHubId}"?."${button}"
}

Map getRoomSceneMapForRep (Long repHubId) {
  return state.repButtonToRS."${repHubId}"
}

void refreshRepButtonToRS() {
  // GOAL: [repHubId: [
  //         repNativeId: ra2-1 .. ra2-83 .. pro2-1
  //         button: [room: scene]]] (one room.scene per button)
  state.repButtonToRS = [:]
  state.hubitatIdToRepeaterData.each{ repHubId, map1 ->
    String repNativeId = "${map1.type}-${map1.nativeId}"
    logTrace('refreshRepButtonToRS', [
      "repHubId: ${repHubId}, repNativeId: ${repNativeId}",
      "map1.buttons: ${map1.buttons}"
    ])
    map1.buttons.each{ map2 ->
      String button = map2.button ?: map2.altButton
      String room = map2.room
      String scene = map2.scene ?: map2.rsvdScene
      if (room && scene && button) {
        logTrace(
          'refreshRepButtonToRS',
          "repHubId: ${repHubId}, button: ${button}, room: ${room}, scene: ${scene}"
        )
        // If missing, init button Map for this repHubId
        if (! state.repButtonToRS."${repHubId}") {
          state.repButtonToRS.put(repHubId, ['repNativeId': repNativeId])
        }
        // If missing, init room-to-scene Map for this button
        if (! state.repButtonToRS."${repHubId}"."${button}") {
          state.repButtonToRS."${repHubId}".put(button, [:])
        }
        // Install the single-entry [room: scene] Map for the button
        if (room && scene) {
          state.repButtonToRS."${repHubId}"."${button}".put(room, scene)
        }
      } else {
        logError('refreshRepButtonToRS', [
          "repHubId: ${repHubId}, repNativeId: ${repNativeId}",
          "map2: ${map2}",
          "button: ${button}, room: ${room}, scene: ${scene}"
        ])      }
    }
  }
}

void buttonOnCallback (String mode) {
  // - The WHA MPbsg instance calls this method to reflect a state change.
  // - WHA sets the Hubitat Mode on certain RA2 Repeater button presses.
  // - WHA ignores Mode change events, allowing the Room Scenes to react.
  logTrace('buttonOnCallback', "WHA ignoring new mode: ${b(mode)}")
}

/*
void pressButtonsForRoomScene (String room, String scene) {
  state.rsToRepButton."${room}"?."${scene}"?.each{ repHubId, buttonList ->
    buttonList.each{ button ->
      settings.ra2Repeaters{ rep ->
        //if (rep.label)
      }
      // Filter for the repeater instance (from settings)
    }
  }
  Map repButtonsMap = state.rsToRepButton.collectEntries{ k1, map1 ->
    if (k1 == room) {
      map1.each{ k2, map2 ->
        if (k2 = scene) {
          [map2.repHub]
        }
      }
    }
  }
}
*/

void refreshRStoRepButton() {
  // GOAL: [room: [scene: [repHubId: [unique buttons]]]]
  state.rsToRepButton = [:]
  state.hubitatIdToRepeaterData.each{ repHubId, map1 ->
    String repNativeId = "${map1.type}-${map1.nativeId}"
    logTrace('refreshRStoRepButton', "repHubId: ${repHubId}, repNativeId: ${repNativeId}")
    map1.buttons.each{ map2 ->
      String room = map2.room
      String scene = map2.scene ?: map2.rsvdScene
      String button = map2.button ?: map2.altButton
      if (room && scene && button) {
        logTrace(
          'refreshRStoRepButton',
          "room: ${room}, scene: ${scene}, repHubId: ${repHubId}, button: ${button}"
        )
        // If missing, init room Map
        if (! state.rsToRepButton."${room}") {
          state.rsToRepButton.put(room, [:])
        }
        // If missing, init room.scene Map
        if (! state.rsToRepButton."${room}"."${scene}") {
          state.rsToRepButton."${room}".put(scene, [:])
        }
        // If missing, init room.scene.repHub List
        if (! state.rsToRepButton."${room}"."${scene}"."${repHubId}") {
          state.rsToRepButton."${room}"."${scene}".put(repHubId, [])
        }
        // Add repeater button if not already present
        //if (! state.rsToRepButton."${room}"."${scene}"."${repHubId}".findAll{ it == button }) {
        if (! state.rsToRepButton."${room}"."${scene}"."${repHubId}".contains(button)) {
          state.rsToRepButton."${room}"."${scene}"."${repHubId}" << button
        }
      } else {
        logError('refreshRStoRepButton', "repHubId: ${repHubId}, room: ${room}, scene: ${scene}, button: ${button}")
      }
    }
  }
}

//---- CORE METHODS (External)

//---- CORE METHODS (Internal)

InstAppW getOrCreateMPbsg () {
  // Mpbsg depends on Hubitat Mode properties AND NOT local data.
  InstAppW pbsgApp = app.getChildAppByLabel(state.MPBSG_LABEL)
  if (!pbsgApp) {
    logWarn('getOrCreateMPbsg', "Adding Mode PBSG ${state.MPBSG_LABEL}")
    pbsgApp = addChildApp('wesmc', 'MPbsg', state.MPBSG_LABEL)
    ArrayList<String> modeNames = getLocation().getModes().collect{ it.name }
    String currModeName = getLocation().currentMode.name
    pbsgApp.pbsgConfigure(
      modeNames,     // Create a PBSG button per Hubitat Mode name
      'Day',         // 'Day' is the default Mode/Button
      currModeName,  // Activate the Button for the current Mode
      settings.pbsgLogThresh ?: 'INFO' // 'INFO' for normal operations
                                       // 'DEBUG' to walk key PBSG methods
                                       // 'TRACE' to include PBSG and VSW state
    )
  }
  return pbsgApp
}

void _writeMPbsgHref () {
  InstAppW pbsgApp = getOrCreateMPbsg()
  if (pbsgApp) {
    href(
      name: appInfo(pbsgApp),
      width: 2,
      url: "/installedapp/configure/${pbsgApp.id}/MPbsgPage",
      style: 'internal',
      title: "Review ${appInfo(pbsgApp)}",
      state: null
    )
  } else {
    paragraph "Creation of the MPbsgHref is pending required data."
  }
}

//---- EVENT HANDLERS

String extractDeviceIdFromLabel(String deviceLabel) {
  //->x = (deviceLabel =~ /\((.*)\)/)
  //->logDebug('extractDeviceIdFromLabel', [
  //->  "deviceLabel: ${deviceLabel}",
  //->  "x: ${x}",
  //->  "x[0]: ${x[0]}",
  //->  "x[0]: ${x[0][1]}",
  //->])
  return (deviceLabel =~ /\((.*)\)/)[0][1]
}

void pro2RepHandler(Event e) {
  // CASETA EXAMPLE
  //   descriptionText  Caséta Repeater (pro2-1) button 4 was pushed
  //   displayName  Caséta Repeater (pro2-1)
  //   deviceId  9129
  //   name  pushed
  //   value  4
  //   isStateChange  true
  // NOTES:
  //   - A Caseta scene button press implies 'on'.
  //   - There is no event equivalent to scene 'off'.
  //   - Detection of 'scene off' requires device monitoring.
  if (e.name == 'pushed') {
    String eventButton = e.value
    String repDeviceId = extractDeviceIdFromLabel(e.displayName)
    Map repMap = getRoomSceneMapForRep(e.deviceId)
    Map rsMap = repMap."${eventButton}"
    rsMap.each{ room, scene ->
      logInfo(
        'pro2RepHandler',
        "${e.deviceId} (${repMap.repNativeId}), ${room} ${scene} ${e.value} on"
      )
    }
  }
}




void ra2RepHandler(Event e) {
  // Example Event
  //   descriptionText  RA2 Repeater 2 (ra2-83) button 73 was pushed
  //   displayName  RA2 Repeater 2 (ra2-83)
  //   deviceId  6892
  //   name  pushed
  //   value  73
  //   isStateChange  true
  if (e.name.startsWith('buttonLed-')) {
    String eventButton = e.name.substring(10)
    String repDeviceId = extractDeviceIdFromLabel(e.displayName)
    Map repMap = getRoomSceneMapForRep(e.deviceId)
    Map rsMap = repMap."${eventButton}"
    rsMap.each{ room, scene ->
      logInfo(
        'ra2RepHandler',
        "${e.deviceId} (${repMap.repNativeId}), ${room} ${scene} ${e.value}"
      )
      if (room == 'WHA') {
        switch(scene) {
          case 'Off':
            // The button that turns RA2 lights off DOES NOT adjust mode.
            // Room Scene apps may individually react.
            logTrace('ra2RepHandler', "All ${scene} w/ nothing to do.")
            break
          case 'Chill':
          case 'Clean':
          case 'Day':
          case 'Night':
          case 'Party':
          case 'Supp':
          case 'TV':
            if (e.value == 'on') {
              // Activation sets mode. Deactivation does nothing.
              logInfo('ra2RepHandler', "Activating mode ${scene} (via PBSG)")
              getOrCreateMPbsg().pbsgActivateButton(scene)
            }
            break;
          case '_AUTO':
            if (e.value == 'on') {
              // Force room Pbsg's into AUTO (to follow the Hubitat mode)
              settings.rooms.each{ roomName ->
                logInfo('ra2RepHandler', "Force ${roomName} to AUTOMATIC")
                app.getChildAppByLabel(roomName)
                   .getOrCreateRSPbsg()
                   .pbsgActivateButton('AUTOMATIC')
              }
            }
            break
          case '_ALARM':
          case '_AWAY':
          case '_FLASH':
          case '_PANIC':
          case '_QUIET':
            logWarn('ra2RepHandler', "WHA scene '${scene}' is not implemented!")
            break
          default:
            logWarn('ra2RepHandler', "WHA scene '${scene}' is not implemented.")
        }
      }
    }
  }
}

//---- SYSTEM CALLBACKS

void installed () {
  logWarn('installed', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled () {
  logWarn('uninstalled', 'Entered')
  removeAllChildApps()
}

void updated () {
  logWarn('updated', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void initialize () {
  // - The same keypad may be associated with two different, specialized handlers
  //   (e.g., mode changing buttons vs special functionalily buttons).
  logTrace('initialize', 'Entered')
  refreshRepButtonToRS()
  refreshRStoRepButton()
  //x--> settings.seeTouchKpads.each{ device ->
  //x-->   logInfo('initialize', "subscribing ${deviceInfo(device)} to mode handler."
  //x-->   )
  //x-->   subscribe(device, seeTouchModeButtonHandler, ['filterEvents': true])
  //x--> }
  //x--> settings.seeTouchKpads.each{ device ->
  //x-->   logInfo('initialize', "subscribing ${deviceInfo(device)} to Keypad Handler")
  //x-->   subscribe(device, seeTouchSpecialFnButtonHandler, ['filterEvents': true])
  //x--> }
  settings.ra2Repeaters.each{ device ->
    logInfo('initialize', "subscribing ${deviceInfo(device)} to Repeater Handler")
    subscribe(device, ra2RepHandler, ['filterEvents': true])
  }
  settings.pro2Repeaters.each{ device ->
    logInfo('initialize', "subscribing ${deviceInfo(device)} to Repeater Handler")
    subscribe(device, pro2RepHandler, ['filterEvents': true])
  }
}

//---- GUI / PAGE RENDERING

void _idRa2Repeaters () {
  input(
    name: 'ra2Repeaters',
    title: heading2('Identify Lutron RA2 Repeater(s)'),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _idPro2Repeaters () {
  input(
    name: 'pro2Repeaters',
    title: heading2('Identify Lutron Caseta (Pro2) Repeater(s)'),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _idParticipatingRooms () {
  roomPicklist = app.getRooms().collect{it.name.replace(' ', '_')}.sort()
  input(
    name: 'rooms',
    type: 'enum',
    title: heading2('Identify Participating Rooms'),
    options: roomPicklist,
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _displayInstantiatedRoomHrefs () {
  paragraph heading1('Room Scene Configuration')
  settings.rooms.each{ roomName ->
    InstAppW roomApp = app.getChildAppByLabel(roomName)
    if (!roomApp) {
      logWarn(
        'addRoomAppsIfMissing',
        "Adding room ${roomName}"
      )
      roomApp = addChildApp('wesmc', 'RoomScenes', roomName)
    }
    href (
      name: roomName,
      width: 2,
      url: "/installedapp/configure/${roomApp?.id}",
      style: 'internal',
      title: "${appInfo(roomApp)} Scenes",
      state: null, //'complete'
    )
  }
}

Map WhaPage () {
  return dynamicPage(
    name: 'WhaPage',
    title: [
      heading1("Whole House Automation (WHA) - ${app.id}"),
      bullet1('Tab to register changes.'),
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
    app.updateLabel('WHA')
    state.MPBSG_LABEL = '_MPbsg'
    state.MODES = getLocation().getModes().collect{ it.name }
    getGlobalVar('defaultMode').value
    state.SPECIALTY_BUTTONS = ['ALARM', 'ALL_AUTO', 'ALL_OFF', 'AWAY',
      'FLASH', 'PANIC', 'QUIET']
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      solicitLogThreshold('pbsgLogThresh', 'INFO') // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      state.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
      _idRa2Repeaters()
      _idPro2Repeaters()
      //x--> _idSpecialFnButtons()
      //x--> _idKpadsWithModeButtons()
      //x--> _wireSpecialFnButtons()
      //x--> _idKpadModeButtons()
      //x--> _wireModeButtons()
      _idParticipatingRooms()
      _writeMPbsgHref()
      if (!settings.rooms) {
        // Don't be too aggressive deleting child apps and their config data.
        paragraph('Management of child apps is pending selection of Room Names.')
      } else {
        //TBD-> pruneAppDups(
        //TBD->   [*settings.rooms, state.MPBSG_LABEL],
        //TBD->   app      // The object (parent) pruning dup children
        //TBD-> )
        _displayInstantiatedRoomHrefs()
      }
      paragraph([
        heading1('Debug<br/>'),
        *appStateAsBullets(true),
        *appSettingsAsBullets(true)
      ].join('<br/>'))
    }
  }
}
