
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

/* groovylint-disable-next-line LineLength */
// state.repToRS → [pro2-1:[1:[Lanai, Chill], 2:[Lanai, Cleaning], 3:[Lanai, Day], 4:[Lanai, Games], 5:[Lanai, Night], 6:[Lanai, Party], 7:[Lanai, Supplement], 8:[Lanai, TV]], ra2-83:[22:[Primary, Clean], 23:[Primary, Day], 24:[Primary, Night], 25:[Primary, Off], 26:[Primary, Party], 27:[Primary, Supp], 28:[Primary, TV], 71:[Lanai, Chill], 72:[Lanai, Clean], 51:[Office, Chill], 73:[Lanai, Day], 52:[Office, Clean], 74:[Lanai, Night], 53:[Office, Day], 75:[Lanai, Off], 10:[PrimBath, Chill], 54:[Office, Night], 76:[Lanai, Party], 55:[Office, Off], 77:[Lanai, Supp], 12:[PrimBath, Clean], 56:[Office, Party], 78:[Lanai, TV], 13:[PrimBath, Day], 57:[Office, Supp], 79:[Lanai, _Games], 14:[PrimBath, Night], 58:[Office, TV], 15:[PrimBath, Off], 16:[PrimBath, Party], 17:[PrimBath, Supp], 18:[PrimBath, TV], 61:[Yard, High], 62:[Yard, Low], 63:[Yard, Off], 21:[Primary, Chill]], ra2-1:[44:[Den, Night], 88:[Main, TV], 45:[Den, Off], 46:[Den, Party], 47:[Den, Supp], 48:[Den, TV], 51:[Guest_Wing, Chill], 52:[Guest_Wing, Clean], 53:[Guest_Wing, Day], 54:[Guest_Wing, Night], 11:[Den_Lamp, Chill], 55:[Guest_Wing, Off], 12:[Den_Lamp, Clean], 56:[Guest_Wing, Party], 13:[Den_Lamp, Day], 57:[Guest_Wing, Supp], 14:[Den_Lamp, Night], 58:[Guest_Wing, TV], 15:[Den_Lamp, Off], 16:[Den_Lamp, Party], 17:[Den_Lamp, Supp], 18:[Den_Lamp, TV], 60:[LHS_Bath, Chill], 62:[LHS_Bath, Clean], 63:[LHS_Bath, Day], 64:[LHS_Bath, Night], 21:[Kitchen, Chill], 65:[LHS_Bath, Off], 22:[Kitchen, Clean], 66:[LHS_Bath, Party], 23:[Kitchen, Day], 67:[LHS_Bath, Supp], 24:[Kitchen, Night], 68:[LHS_Bath, TV], 25:[Kitchen, Off], 26:[Kitchen, Party], 27:[Kitchen, Supp], 28:[Kitchen, TV], 29:[Kitchen, _Cook], 70:[RHS_Bath, Chill], 72:[RHS_Bath, Clean], 73:[RHS_Bath, Day], 74:[RHS_Bath, Night], 75:[RHS_Bath, Off], 76:[RHS_Bath, Party], 77:[RHS_Bath, Supp], 78:[RHS_Bath, TV], 79:[Main, Chill], 82:[Main, Clean], 83:[Main, Day], 84:[Main, Night], 41:[Den, Chill], 85:[Main, Off], 42:[Den, Clean], 86:[Main, Party], 43:[Den, Day], 87:[Main, Supp]]]

// hubitatDeviceId: 6825, displayName: 'RA2 Repeater 1 (ra2-1)'
// hubitatDeviceId: 6892, displayName: 'RA2 Repeater 2 (ra2-83)'
//   -> button <value> (##) was <name> (pushed/released)
//   -> Integer eventButton = safeParseInt(e.name.substring(10))
//
// hubitatDeviceId: 9129, displayName: 'Caséta Repeater (pro2-1)'
//   - button # was pushed
//   - button # was released

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
      [button: 1, room: 'ALARM', scene: 'ALARM'],
      [button: 2, room: 'AUTO', scene: 'AUTO'],
      [button: 3, room: 'AWAY', scene: 'AWAY'],
      [button: 4, room: 'FLASH', scene: 'FLASH'],
      [button: 5, room: 'PANIC', scene: 'PANIC'],
      [button: 6, room: 'QUIET', scene: 'QUIET'],
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

InstAppW _getOrCreateMPbsg () {
  // Mpbsg depends on Hubitat Mode properties AND NOT local data.
  InstAppW pbsgApp = app.getChildAppByLabel(state.MPBSG_LABEL)
  if (!pbsgApp) {
    logWarn('_getOrCreateMPbsg', "Adding Mode PBSG ${state.MPBSG_LABEL}")
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
  InstAppW pbsgApp = _getOrCreateMPbsg()
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

void AllAuto () {
  settings.rooms.each{ roomName ->
    InstAppW roomApp = app.getChildAppByLabel(roomName)
    String manualOverrideSwitchDNI = "pbsg_${roomApp.label}_AUTOMATIC"
    logInfo('AllAuto', "Turning on ${b(manualOverrideSwitchDNI)}")
    roomApp.getRSPbsg().turnOnSwitch(manualOverrideSwitchDNI)
  }
}

void updateLutronKpadLeds (String currMode) {
  settings.lutronModeButtons.each{ ledObj ->
    String modeTarget = state.kpadButtonDniToTargetMode[ledObj.getDeviceNetworkId()]
    if (currMode == modeTarget) {
      ledObj.on()
    } else {
      ledObj.off()
    }
  }
}

void buttonOnCallback (String mode) {
  // - The MPbsg instance calls this method to reflect a state change.
  logInfo('buttonOnCallback', "Received mode: ${b(mode)}")
  getLocation().setMode(mode)
  updateLutronKpadLeds(mode)
}

//---- EVENT HANDLERS

void seeTouchSpecialFnButtonHandler (Event e) {
  switch (e.name) {
    case 'pushed':
      String specialtyFunction = state.specialFnButtonMap?.getAt(e.deviceId.toString())
                                                         ?.getAt(e.value)
      if (specialtyFunction == null) return
      switch(specialtyFunction) {
        case 'ALL_AUTO':
          logInfo('seeTouchSpecialFnButtonHandler', 'executing ALL_AUTO')
          AllAuto()
          //--TBD--> Update of Kpad LEDs
          break;
        case 'ALARM':
        case 'AWAY':
        case 'FLASH':
        case 'PANIC':
        case 'QUIET':
          logWarn(
            'seeTouchSpecialFnButtonHandler',
            "${b(specialtyFunction)} function execution is <b>TBD</b>"
          )
          break
        default:
          // Silently
          logError(
            'seeTouchSpecialFnButtonHandler',
            "Unknown specialty function ${b(specialtyFunction)}"
          )
      }
      break;
    case 'held':
    case 'released':
    default:
      logWarn(
        'seeTouchSpecialFnButtonHandler',
        "ignoring ${e.name} ${e.deviceId}-${e.value}"
      )
  }
}

void seeTouchModeButtonHandler (Event e) {
  // Design Note
  //   - Process Lutron SeeTouch Kpad events.
  //   - The field e.deviceId arrives as a number and must be cast toString().
  //   - Hubitat runs Groovy 2.4. Groovy 3 constructs - x?[]?[] - are not available.
  //   - Kpad buttons are matched to state data to activate a PBSG button.
  switch (e.name) {
    case 'pushed':
      String targetButton = state.modeButtonMap?.getAt(e.deviceId.toString())
                                               ?.getAt(e.value)
      if (targetButton) {
        logInfo('seeTouchModeButtonHandler', "turning on ${targetButton}")
        _getOrCreateMPbsg().pbsgToggleButton(targetButton)
      }
      if (targetButton == 'Day') {
        logInfo('seeTouchModeButtonHandler', 'executing ALL_AUTO')
        AllAuto()
      }
      // Silently ignore buttons that DO NOT impact Hubitat mode.
      break;
    case 'held':
    case 'released':
    default:
      logWarn(
        'seeTouchModeButtonHandler',
        "Ignoring ${e.name} ${e.deviceId}-${e.value}"
      )
  }
}

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
  settings.seeTouchKpads.each{ device ->
    logInfo('initialize', "subscribing ${deviceInfo(device)} to mode handler."
    )
    subscribe(device, seeTouchModeButtonHandler, ['filterEvents': true])
  }
  settings.seeTouchKpads.each{ device ->
    logInfo('initialize', "subscribing ${deviceInfo(device)} to Keypad Handler")
    subscribe(device, seeTouchSpecialFnButtonHandler, ['filterEvents': true])
  }
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

void _idSpecialFnButtons () {
  input(
    name: 'specialFnButtons',
    title: [
      heading2('Identify Special Function Buttons'),
      bullet2("Examples: ${state.SPECIALTY_BUTTONS}")
    ].join('<br/>'),
    type: 'device.LutronComponentSwitch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _populateSpecialFnButtonMap () {
  Map<String, String> result = [:]
  state.specialFnButtonMap.collect{ kpadDni, buttonMap ->
    buttonMap.each{ buttonNumber, specialtyFn ->
      result["${kpadDni}-${buttonNumber}"] = specialtyFn
    }
  }
  state.kpadButtonDniToSpecialtyFn = result
}

void _wireSpecialFnButtons () {
  if (settings?.specialFnButtons == null) {
    paragraph bullet2('No specialty activation buttons are selected.')
  } else {
    identifyLedButtonsForListItems(         // From UtilsLibrary.groovy
      state.SPECIALTY_BUTTONS,              //   - list
      settings.specialFnButtons,            //   - ledDevices
      'specialFnButton'                     //   - prefix
    )
    populateStateKpadButtons('specialFnButton')
    _populateSpecialFnButtonMap()
  }
}

void _idKpadsWithModeButtons () {
  input(
    name: 'seeTouchKpads',
    title: [
      heading2('Identify Kpad(s) with Mode Selection Buttons'),
      bullet2('The identified buttons are used to set the Hubitat mode')
    ].join('<br/>'),
    type: 'device.LutronSeeTouchKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _idKpadModeButtons () {
  input(
    name: 'lutronModeButtons',
    title: heading2('Identify Kpad Mode Selection Buttons'),
    type: 'device.LutronComponentSwitch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _populateStateKpadButtonDniToTargetMode () {
  Map<String, String> result = [:]
  state.modeButtonMap.collect{ kpadDni, buttonMap ->
    buttonMap.each{ buttonNumber, targetMode ->
      result["${kpadDni}-${buttonNumber}"] = targetMode
    }
  }
  state.kpadButtonDniToTargetMode = result
}

void _wireModeButtons () {
  if (state.MODES == null || settings?.lutronModeButtons == null) {
    paragraph('Mode activation buttons are pending pre-requisites.')
  } else {
    identifyLedButtonsForListItems(         // From UtilsLibrary.groovy
      state.MODES,                          //   - list
      settings.lutronModeButtons,           //   - ledDevices
      'modeButton'                          //   - prefix
    )
    populateStateKpadButtons('modeButton')
    _populateStateKpadButtonDniToTargetMode()
  }
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
    //--NO LONGER REQUIRED-> stateRemoveAndLog('MODE_PBSG_APP_LABEL')
    //--NO LONGER REQUIRED-> stateRemoveAndLog('MODE_PBSG_APP_NAME')
    //--NO LONGER REQUIRED-> stateRemoveAndLog('repToRS')
    //--NO LONGER REQUIRED-> app.removeSetting('hubitatQueryString')
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
      _idSpecialFnButtons()
      _idKpadsWithModeButtons()
      _wireSpecialFnButtons()
      _idKpadModeButtons()
      _wireModeButtons()
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
