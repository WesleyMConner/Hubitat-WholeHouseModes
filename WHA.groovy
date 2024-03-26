
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
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(name: 'WhaPage')
}

void refreshRepButtonToRS() {
  // GOAL: [repHubId: [
  //         repNativeId: ra2-1 .. ra2-83 .. pro2-1
  //         button: [room: scene]]] (one room.scene per button)
  state.repButtonToRS = [:]
  state.hubIdToRa2RepData.each{ repHubId, map1 ->
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

void pbsgButtonOnCallback(String mode) {
  // - The WHA MPbsg instance calls this method to reflect a state change.
  // - WHA sets the Hubitat Mode on certain RA2 Repeater button presses.
  // - WHA ignores Mode change events, allowing the Room Scenes to react.
  logTrace('pbsgButtonOnCallback (WHA)', "WHA ignoring reported Hubitat mode: ${b(mode)}")
}

//xxxx void activateScene(String room, String scene) {
  // PURPOSE
  //   Designed for invocation by a child Room Scene
  // CONTEXT
  //   [room: [scene: [repHubId: [unique buttons]]]]
//xxxx   logError('activateScene', 'N O T   I M P L E M E N T E D')
  /*
  Map repTargetButtons = state.rsToRepButton."${room}"?."${scene}"
  settings.ra2Repeaters.each{ rep ->
    if (rep.label)
    repPTargetButtons."${rep.deviceId}"
  }
  settings.pro2Repeaters.each{ rep ->
  }
  each{ repHubId, buttonList ->
  }
  */
//xxxx }

void refreshRStoRepButton() {
  // GOAL: [room: [scene: [repHubId: [unique buttons]]]]
  state.rsToRepButton = [:]
  state.hubIdToRa2RepData.each{ repHubId, map1 ->
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

InstAppW createModePbsg() {
  // --------------------------------------------------------------------
  // W A R N I N G
  //   - As of Q1'24, the 'properties' (4th) parameter of addChildApp()
  //     is still not implemented.
  //   - As an alternative, call the 'MPbsg' method pbsgConfigure() just
  //     after Mpbsg instance creation to provide initial data.
  // --------------------------------------------------------------------
  InstAppW mPbsg = addChildApp('wesmc', 'MPbsg', state.MPBSG_LABEL)
  ArrayList<String> modeNames = getLocation().getModes().collect{ it.name }
  String currModeName = getLocation().currentMode.name
  mPbsg.pbsgConfigure(
    modeNames,     // Create a PBSG button per Hubitat Mode name
    'Day',         // 'Day' is the default Mode/Button
    currModeName,  // Activate the Button for the current Mode
    settings.pbsgLogThresh ?: 'INFO' // 'INFO' for normal operations
                                     // 'DEBUG' to walk key PBSG methods
                                     // 'TRACE' to include PBSG and VSW state
  )
  return mPbsg
}

InstAppW getModePbsg() {
  // Mpbsg depends on Hubitat Mode properties AND NOT local data.
  Boolean createdNewInstance = false
  InstAppW modePbsg = app.getChildAppByLabel(state.MPBSG_LABEL)
  if (! modePbsg) {
    modePbsg = createModePbsg()
    createdNewInstance = true
  }
  logInfo('getModePbsg', [
    '',
    "Created New Instance: ${createdNewInstance}",
    "Id: ${modePbsg.getId()}",
    "Label: ${modePbsg.getLabel()}",
    "Install State: ${modePbsg.getInstallationState()}<"
  ])
  return modePbsg
}

void writeMPbsgHref() {
  InstAppW mPbsg = getModePbsg()
  if (mPbsg) {
    href(
      name: appInfo(mPbsg),
      width: 2,
      url: "/installedapp/configure/${mPbsg.id}/MPbsgPage",
      style: 'internal',
      title: "Review ${appInfo(mPbsg)}",
      state: null
    )
  } else {
    paragraph "Creation of the MPbsgHref is pending required data."
  }
}

//---- EVENT HANDLERS

void pro2RepHandler (Event e) {
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
    String repDeviceId = extractNativeIdFromLabel(e.displayName)
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

void ra2RepHandler (Event e) {
  // Example Event
  //   descriptionText  RA2 Repeater 2 (ra2-83) button 73 was pushed
  //   displayName  RA2 Repeater 2 (ra2-83)
  //   deviceId  6892
  //   name  pushed
  //   value  73
  //   isStateChange  true
  if (e.name.startsWith('buttonLed-')) {
    String eventButton = e.name.substring(10)
    String repDeviceId = extractNativeIdFromLabel(e.displayName)
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
              // Room's react to mode when in their AUTO mode.
              logInfo('ra2RepHandler', "Activating mode ${scene} (via PBSG)")
              getModePbsg()?.pbsgActivateButton(scene)
            }
            break;
          case '_AUTO':
            if (e.value == 'on') {
              // Force room Pbsg's into AUTO (to follow the Hubitat mode)
              settings.rooms.each{ roomName ->
                logInfo('ra2RepHandler', "Force ${roomName} to AUTO")
                app.getChildAppByLabel(roomName)
                   .getRSPbsg()
                   .pbsgActivateButton('AUTO')
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

void idRa2Repeaters () {
  input(
    name: 'ra2Repeaters',
    title: heading2('Identify Lutron RA2 Repeater(s)'),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void idPro2Repeaters () {
  input(
    name: 'pro2Repeaters',
    title: heading2('Identify Lutron Caseta (Pro2) Repeater(s)'),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void displayInstantiatedRoomHrefs () {
  paragraph heading1('Room Scene Configuration')
  //x--> settings.rooms.each{ roomName ->
  getRoomNames().each{ room ->
    InstAppW roomApp = app.getChildAppByLabel(room)
    if (!roomApp) {
      logWarn('displayInstantiatedRoomHrefs', "Adding room ${room}")
      // --------------------------------------------------------------------
      // W A R N I N G
      //   - As of Q1'24, the 'properties' (4th) parameter of addChildApp()
      //     is still not implemented.
      //   - As an alternative, call the 'RoomScenes' method rsConfigure()
      //     just after RoomScenes instance creation to provide initial data.
      // --------------------------------------------------------------------
logInfo('#607', "room: ${room}")
      roomApp = addChildApp('wesmc', 'RoomScenes', room)
logInfo('#609', "roomApp: ${roomApp}")
      roomApp.rsConfigure(getSceneNames(room))
logInfo('#611', "roomApp: ${roomApp}")
    }
    href (
      name: room,
      width: 2,
      url: "/installedapp/configure/${roomApp?.id}",
      style: 'internal',
      title: "${appInfo(roomApp)} Scenes",
      state: null, //'complete'
    )
  }
}

void stateAndSessionCleanup () {
  settingsRemoveAndLog('rooms')
  settingsRemoveAndLog('hubitatQueryString')
  settingsRemoveAndLog('mainRepeaters')
  settingsRemoveAndLog('modeButton_Chill')
  settingsRemoveAndLog('modeButton_Cleaning')
  settingsRemoveAndLog('modeButton_Day')
  settingsRemoveAndLog('modeButton_Night')
  settingsRemoveAndLog('modeButton_Party')
  settingsRemoveAndLog('modeButton_TV')
  stateRemoveAndLog('kpadButtonDniToSpecialtyFn')
  stateRemoveAndLog('kpadButtonDniToTargetMode')
  stateRemoveAndLog('MODE_PBSG_LABEL')
  stateRemoveAndLog('modeButtonMap')
  stateRemoveAndLog('specialFnButtonMap')
  stateRemoveAndLog('SPECIALTY_BUTTONS')
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
    stateAndSessionCleanup()
    app.updateLabel('WHA')
    state.MPBSG_LABEL = '_MPbsg'
    state.MODES = getLocation().getModes().collect{ it.name }
    getGlobalVar('defaultMode').value
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      solicitLogThreshold('pbsgLogThresh', 'INFO') // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      state.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
      idRa2Repeaters()
      idPro2Repeaters()
      writeMPbsgHref()
      // if (!settings.rooms) {
      //   // Don't be too aggressive deleting child apps and their config data.
      //   paragraph('Management of child apps is pending selection of Room Names.')
      // } else {
      //   pruneAppDups(
      //     [*settings.rooms, state.MPBSG_LABEL],
      //     app      // The object (parent) pruning dup children
      //   )
      // }
      displayInstantiatedRoomHrefs()
      paragraph([
        heading1('Debug<br/>'),
        *appStateAsBullets(true),
        *appSettingsAsBullets(true)
      ].join('<br/>'))
    }
  }
}
