// ---------------------------------------------------------------------------------
// R O O M   S C E N E S →
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
// The Groovy Linter generates false positives on Hubitat #include !!!
#include wesmc.lHExt
#include wesmc.lHUI
#include wesmc.lPbsgv2
#include wesmc.lRoom

definition (
  parent: 'wesmc:WHA',
  name: 'RoomScenes',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Manage WHA Rooms for Whole House Automation',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  singleInstance: false
)

preferences {
  page(name: 'RoomScenesPage')
}

/*
void indDeviceHandler(Event e) {
  // Devices send various events (e.g., switch, level, pushed, released).
  // Isolate the events that confirm|refute roomMap.activeScene.
  Map roomMap = xxx
  Integer reported
  // Only select events are considered for MANUAL OVERRIDE detection.
  if (e.name == 'level') {
    reported = safeParseInt(e.value)
  } else if (e.name == 'switch' && e.value == 'off') {
    reported = 0
  }
  String deviceId = extractDeviceIdFromLabel(e.displayName)
  Integer expected = expectedSceneDeviceValue('Ind', deviceId)
  if (reported == expected) {
    roomMap.moDetected = roomMap.moDetected.collect { key, value ->
      if (key != deviceId) { [key, value] }
    }
  } else {
    roomMap.moDetected.put(deviceId, "${reported} (${expected})")
  }
}
*/

void toggleButton(Map roomMap, String button) {
  // Toggle the button's device and let activate and deactivate react.
  // This will result in delivery of the scene change via a callback.
  String dni = "${roomMap.name}_${button}"
  DevW device = getChildDevice(dni)
  if (switchState(device) == 'on') {
    device.off()
  } else {
    devive.on()
  }
}

void room_ModeChange(Map roomMap, String newMode) {
  // If the PBSG activeButton is 'Automatic', adjust the roomMap.activeScene
  // per the newMode.
  if (roomMap.activeButton == 'Automatic') {
    logInfo('room_ModeChange', "Adjusting activeScene to '${newMode}'")
    roomMap.activeScene = newMode
    room_ActivateScene()
  } else {
    logInfo(
      'room_ModeChange', [
        'Ignored: Mode Change',
        "newMode: ${newMode}",
        "roomMap.activeButton: ${b(roomMap.activeButton)}",
        "roomMap.activeScene: ${b(roomMap.activeScene)}"
      ]
    )
  }
}

void installed() {
  //-> logTrace('installed', 'At Entry')
  initialize()
}

void updated() {
  //-> logTrace('updated', 'At Entry')
  unsubscribe()  // Suspend all events (e.g., child devices, mode changes).
  initialize()
}

void uninstalled() {
  logWarn('uninstalled', 'At Entry')
}

void initialize() {
  Map roomMap = xxx
  logInfo(
    'initialize',
    "${roomMap.name} initialize() of '${roomMap.name}'. "
      //+ "Subscribing to modeHandler."
  )
  roomMap.luxSensors = []
  populateStateScenesAssignValues()
  roomMap.moDetected = [:]()
  settings.indDevices.each { device -> unsubscribe(device) }
  //-> subscribeToRepHandler()
  subscribeToMotionSensorHandler()
  subscribeToLuxSensorHandler()
  // ACTIVATION
  //   - If Automatic is already active in the PBSG, pbsg_ButtonOnCallback(...)
  //     will not be called.
  //   - It is better to include a redundant call here than to miss
  //     proper room activation on initialization.
  Map pbsg = pbsgStore_Retrieve(roomMap.name)
  if (pbsg) {
    pbsg_ActivateButton(pbsg, 'Automatic')
    pbsg_ButtonOnCallback(pbsg)
  } else {
    logWarn(
      'initialize',
      'The RSPbsg is pending additional configuration data.'
    )
  }
}

Map getRoomMap(String roomName) {
  return getParent().roomStore_Retrieve(roomName)
}

Map RoomScenesPage() {
  // The parent application (Whole House Automation) assigns a unique label
  // to each WHA Rooms instance. Capture app.label as roomMap.name.
  return dynamicPage(
    name: 'RoomScenesPage',
    title: [
      heading1("${app.label} Scenes - ${app.id}"),
      bullet1('Tab to register changes.'),
      bullet1('Click <b>Done</b> to enable subscriptions.')
    ].join('<br/>'),
    install: true,
    uninstall: true,
  ) {
    //---------------------------------------------------------------------------------
    // REMOVE NO LONGER USED SETTINGS AND STATE
    //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    //-> app.removeSetting('..')
    //-> state.remove('..')
    //---------------------------------------------------------------------------------
    roomMap.name = app.label  // WHA creates App w/ Label == Room Name
    state.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
    state.remove('sufficientLight')
    state.remove('targetScene')
    roomMap.luxSensors = []
    state.remove('RSPBSG_LABEL')
    app.removeSetting('modeToScene^Chill')
    app.removeSetting('modeToScene^Cleaning')
    app.removeSetting('modeToScene^Day')
    app.removeSetting('modeToScene^Night')
    app.removeSetting('modeToScene^Party')
    app.removeSetting('modeToScene^Supplement')
    app.removeSetting('modeToScene^TV')
    app.removeSetting('modesAsScenes')
    app.removeSetting('scene^INACTIVE^Rep^ra2-1')
    app.removeSetting('scene^INACTIVE^Rep^ra2-83')
    app.removeSetting('customScene1')
    //----
    //---- RETRIEVE ROOM MAP
    //----
    Map roomMap = getRoomMap(roomMap.name)
    state.roomMap = roomMap
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      idMotionSensors()
      idLuxSensors()
      //-> if (roomMap.luxSensors) { idLowLightThreshold() }
//->       nameCustomScene()
//->       adjustStateScenesKeys()
      //*********************************************************************
      if (roomMap.scenes) {
        ArrayList scenes = roomMap.scenes.collect{ k, v -> return k }
        Map rsPbsgConfig = [
          'name': roomMap.name,
          'allButtons': [ *scenes, 'Automatic' ].minus([ 'INACTIVE', 'Off' ]),
          'defaultButton': 'Automatic'
        ]
        Map rsPbsg = pbsg_CreateInstance(rsPbsgConfig, 'room')
      } else {
        paragraph "Creation of the room's PBSG is pending identification of room scenes"
      }
      //*********************************************************************
      //idRa2RepeatersImplementingScenes()
      //idIndDevices()
//->       configureRoomScene()
    }
  }
}
