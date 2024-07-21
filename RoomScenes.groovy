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
#include Wmc.WmcUtilsLib_1.0.0
#include Wmc.lPBSG

definition (
  parent: 'Wmc:WHA',
  name: 'RoomScenes',
  namespace: 'Wmc',
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

String getDeviceId(DevW device) {
//==>   return device?.label ? extractDeviceIdFromLabel(device.label) : null
  Map deviceNameToId = [
    // Pro2 Entries
    'pro2': 1,
    'RhsBdrm-TableLamps': 2,
    'RhsBdrm-FloorLamp': 3,
    'Lanai-DownLighting': 4,
    'Lanai-GrillCan': 5,
    'Lanai-OutdoorDining': 7,
    // Ra2 Entries
    'Rep1': 1,
    'Rep2': 83,
    'Kitchen-Soffit': 4,
    'Guest-RhsHallLamp': 5,
    'Yard-FrontPorch': 6,
    'Main-DiningTable': 9,
    'RhsBath-Lamp': 16,
    'Laundry-Cans': 18,
    'Primary-TableLamps': 19,
    'Den-TvWall': 23,
    'Yard-ShopPorch': 26,
    'LhsBath-Shower': 27,
    'Guest-RhsHallCans': 28,
    'Main-FoyerCans': 31,
    'Main-LivRmLamps': 33,
    'Den-KitchenTable': 34,
    'Office-LeftTableLamp': 35,
    'LhsBdrm-TableLamps': 36,
    'Main-ArtNiche': 37,
    'Lanai-Pendants': 39,
    'Den-HallCans': 43,
    'LhsBdrm-FloorLamp': 45,
    'PrimBath-Soffit': 47,
    'PrimBath-Cans': 51,
    'PrimBath-TubAndShower': 52,
    'Hers-CeilingLight': 53,
    'Lanai-Pool': 54,
    'His-CeilingLight': 56,
    'DenLamp-Dimmer': 59,
    'Kitchen-Cans': 61,
    'Den-DenCans': 62,
    'Den-BevStation': 63,
    'Den-UplightLeds': 64,
    'Kitchen-CounterLeds': 65,
    'Lanai-LedUplighting': 68,
    'Yard-Spots': 71,
    'LhsBath-Soffit': 74,
    'Yard-BackPorch': 75,
    'Guest-LhsHallCans': 76,
    'RhsBath-Shower': 78,
    'RhsBath-Vanity': 79
  ]
  return deviceNameToId[device.getName()]
}

void clearManualOverride() {
  state.moDetected = [:]
}

Boolean isManualOverride() {
  return state.moDetected
}

Boolean isRoomOccupied() {
  // If any Motion sensor for a room detects occupancy (i.e., appears in the
  // following state variable array), then the room is occupied.
  // In the absence of a sensor, state.activeMotionSensors = [ true ]
  return state.activeMotionSensors
}

Boolean isSufficientLight() {
  // If any Lux sensor for a room has sufficient light (i.e., appears in the
  // following state variable array), then the room has sufficient light.
  // In the absence of a sensor, state.brightLuxSensors = [ ]
  return state.brightLuxSensors
}

String expectedScene() {
  return (isRoomOccupied() == false || isSufficientLight() == true)
    ? 'OFF' : state.activeScene
}

void pushRepeaterButton(String repeaterId, Long buttonNumber) {
  settings.repeaters.each { repeater ->
    if(getDeviceId(repeater) == repeaterId) {
      repeater.push(buttonNumber)
    }
  }
}

void setDeviceLevel(String deviceId, Long level) {
  settings.indDevices.each { device ->
    if (getDeviceId(device) == deviceId) {
      if (device.hasCommand('setLevel')) {
        logInfo('activateScene', "Setting ${b(deviceId)} to level ${b(level)}")
        // Some devices DO NOT support a level of 100.
        device.setLevel(level == 100 ? 99 : level)
      }
      if (level == 0) {
        logInfo('activateScene', "Setting ${b(deviceId)} to off")
        device.off()
      } else if (level == 100) {
        logInfo('activateScene', "Setting ${b(deviceId)} to on")
        device.on()
      }
    }
  }
}

void activateScene() {
  // expectedScene considers room occupancy and lux constraints
  String expectedScene = expectedScene()
  if (state.currScene != expectedScene) {
    logInfo('activateScene', "${state.currScene} -> ${expectedScene}")
    state.currScene = expectedScene
    // Decode and process the scene's per-device actions
    Map actions = state.scenes.get(state.currScene)
    actions.get('Rep').each { repeaterId, button ->
      logInfo('activateScene', "Pushing repeater (${repeaterId}) button (${button})")
      pushRepeaterButton(repeaterId, button)
    }
    actions.get('Ind').each { deviceId, value ->
      setDeviceLevel(deviceId, value)
    }
  }
}

void updateTargetScene() {
  // Upstream PBSG/Dashboard/Alexa actions should clear Manual Overrides
  if (
    (state.active == 'Automatic' && !state.activeScene)
    || (state.active == 'Automatic' && !isManualOverride())
  ) {
    // Ensure that targetScene is per the latest Hubitat mode.
    logWarn('updateTargetScene', 'Making a call to getLocation().getMode()')
    state.activeScene = getLocation().getMode() //settings["modeToScene^${mode}"]
  } else {
    state.activeScene = state.active
  }
}

void pbsg_ButtonOnCallback(String pbsgName) {
  // PBSG/Dashboard/Alexa actions override Manual Overrides.
  pbsg = atomicState."${pbsgName}"
  if (pbsg) {
    state.active = pbsg?.active ?: 'Automatic'
    logInfo(
      'pbsg_ButtonOnCallback',
      "Button ?${b(button)}? -> state.active: ${b(state.active)}")
    clearManualOverride()
    updateTargetScene()
    activateScene()
  } else {
    logError(
      'pbsg_ButtonOnCallback',
      "Could not find PBSG '${pbsgName}' via atomicState"
    )
  }
}

Boolean isDeviceType(String devTypeCandidate) {
  return ['Rep', 'Ind'].contains(devTypeCandidate)
}

Integer expectedSceneDeviceValue(String devType, String deviceId) {
  Integer retVal = null
  if (isDeviceType(devType)) {
    retVal = state.scenes?.get(state.activeScene)?.get(devType)?.get(deviceId)
  } else {
    logError('expectedSceneDeviceValue', "devType (${devType}) not recognized")
  }
  return retVal
}

void subscribeToIndDeviceHandlerNoDelay() {
  settings.indDevices.each { d ->
    logInfo(
      'subscribeToIndDeviceHandlerNoDelay',
      "${state.ROOM_LABEL} subscribing to independentDevice ${deviceInfo(d)}"
    )
    subscribe(d, indDeviceHandler, ['filterEvents': true])
  }
}

void subscribeIndDevToHandler(Map data) {
  // USAGE:
  //   runIn(1, 'subscribeIndDevToHandler', [data: [device: d]])
  // Independent Devices (especially RA2 and Caséta) are subject to stale
  // Hubitat state data if callbacks occur quickly (within 1/2 second)
  // after a level change. So, briefly unsubscribe/subscribe to avoid
  // this situation.
  logTrace(
    'subscribeIndDevToHandler',
    "${state.ROOM_LABEL} subscribing ${deviceInfo(data.device)}"
  )
  subscribe(device, indDeviceHandler, ['filterEvents': true])
}

void unsubscribeIndDevToHandler(DevW device) {
  // Independent Devices (especially RA2 and Caséta) are subject to stale
  // Hubitat state data if callbacks occur quickly (within 1/2 second)
  // after a level change. So, briefly unsubscribe/subscribe to avoid
  // this situation.
  logTrace(
    '_unsubscribeToIndDeviceHandler',
    "${state.ROOM_LABEL} unsubscribing ${deviceInfo(device)}"
  )
  unsubscribe(device)
}

void subscribeToMotionSensorHandler() {
  if (settings.motionSensors) {
    state.activeMotionSensors = []
    settings.motionSensors.each { d ->
      logInfo(
        'initialize',
        "${state.ROOM_LABEL} subscribing to Motion Sensor ${deviceInfo(d)}"
      )
      subscribe(d, motionSensorHandler, ['filterEvents': true])
      if (d.latestState('motion').value == 'active') {
        state.activeMotionSensors = cleanStrings([*state.activeMotionSensors, displayName])
        activateScene()
      } else {
        state.activeMotionSensors?.removeAll { it == displayName }
        activateScene()
      }
    }
  } else {
    state.activeMotionSensors = [ true ]
  }
}

void subscribeToLuxSensorHandler() {
  if (settings.luxSensors) {
    state.brightLuxSensors = []
    settings.luxSensors.each { d ->
      logInfo(
        'subscribeToLuxSensorHandler',
        "${state.ROOM_LABEL} subscribing to Lux Sensor ${deviceInfo(d)}"
      )
      subscribe(d, luxSensorHandler, ['filterEvents': true])
    }
  } else {
    state.brightLuxSensors = [ ]
  }
}

void indDeviceHandler(Event e) {
  // Devices send various events (e.g., switch, level, pushed, released).
  // Isolate the events that confirm|refute state.activeScene.
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
    state.moDetected = state.moDetected.collect { key, value ->
      if (key != deviceId) { [key, value] }
    }
  } else {
    state.moDetected.put(deviceId, "${reported} (${expected})")
  }
}

void toggleButton(String button) {
  // Toggle the button's device and let activate and deactivate react.
  // This will result in delivery of the scene change via a callback.
  String dni = "${state.ROOM_LABEL}_${button}"
  DevW device = getChildDevice(dni)
  if (switchState(device) == 'on') {
    device.off()
  } else {
    devive.on()
  }
}

void room_ModeChange(String newMode) {
  // If the PBSG active is 'Automatic', adjust the state.activeScene
  // per the newMode.
  if (state.active == 'Automatic') {
    logInfo('room_ModeChange', "Adjusting activeScene to '${newMode}'")
    state.activeScene = newMode
    activateScene()
  } else {
    logInfo(
      'room_ModeChange', [
        'Ignored: Mode Change',
        "newMode: ${newMode}",
        "state.active: ${b(state.active)}",
        "state.activeScene: ${b(state.activeScene)}"
      ]
    )
  }
}

void motionSensorHandler(Event e) {
  // It IS POSSIBLE to have multiple motion sensors per room.
  logDebug('motionSensorHandler', eventDetails(e))
  if (e.name == 'motion') {
    if (e.value == 'active') {
      logInfo('motionSensorHandler', "${e.displayName} is active")
      state.activeMotionSensors = cleanStrings([*state.activeMotionSensors, e.displayName])
      activateScene()
    } else if (e.value == 'inactive') {
      logInfo('motionSensorHandler', "${e.displayName} is inactive")
      state.activeMotionSensors?.removeAll { it == e.displayName }
      activateScene()
    } else {
      logWarn('motionSensorHandler', "Unexpected event value (${e.value})")
    }
  }
}

void luxSensorHandler(Event e) {
  // It IS POSSIBLE to have multiple lux sensors impacting a room. The lux
  // sensor(s) change values frequently. The activateScene() method is only
  // invoked if the aggregate light level changes materially (i.e., from
  // no sensor detecting sufficient light to one or more sensors detecting
  // sufficient light).
  if (e.name == 'illuminance') {
    if (e.value.toInteger() >= settings.lowLuxThreshold) {
      // Add sensor to list of sensors with sufficient light.
      state.brightLuxSensors = cleanStrings([*state.brightLuxSensors, e.displayName])
    } else {
      // Remove sensor from list of sensors with sufficient light.
      state.brightLuxSensors?.removeAll { it == e.displayName }
    }
    logTrace('luxSensorHandler', [
      "sensor name: ${e.displayName}",
      "illuminance level: ${e.value}",
      "sufficient light threshold: ${settings.lowLuxThreshold}",
      "sufficient light: ${state.brightLuxSensors}"
    ])
    activateScene()
  }
}

void picoHandler(Event e) {
  Integer changePercentage = 10
  if (e.isStateChange == true) {
    switch (e.name) {
      case 'pushed':
        // Check to see if the received button is assigned to a scene.
        String scene = state.picoButtonToTargetScene?.getAt(e.deviceId.toString())
                                                    ?.getAt(e.value.toString())
        if (scene) {
          logInfo(
            'picoHandler',
            "w/ ${e.deviceId.toString()}-${e.value} toggling ${scene}"
          )
          toggleButton(scene)
        } else if (e.value == '2') {  // Default "Raise" behavior
          logTrace('picoHandler', "Raising ${settings.indDevices}")
          settings.indDevices.each { d ->
            if (switchState(d) == 'off') {
              d.setLevel(5)
              //d.on()
            } else {
              d.setLevel(Math.min(
                (d.currentValue('level') as Integer) + changePercentage,
                100
              ))
            }
          }
        } else if (e.value == '4') {  // Default "Lower" behavior
          logTrace('picoHandler', "Lowering ${settings.indDevices}")
          settings.indDevices.each { d ->
            d.setLevel(Math.max(
              (d.currentValue('level') as Integer) - changePercentage,
              0
            ))
          }
        } else {
          logTrace(
            'picoHandler',
            "${state.ROOM_LABEL} picoHandler() w/ ${e.deviceId}-${e.value} no action."
          )
        }
        break
    }
  }
}

void installed() {
  logTrace('installed', 'At Entry')
  initialize()
}

void updated() {
  logTrace('updated', 'At Entry')
  unsubscribe()  // Suspend all events (e.g., child devices, mode changes).
  initialize()
}

void uninstalled() {
  logWarn('uninstalled', 'At Entry')
}

void initialize() {
  logInfo(
    'initialize',
    "${state.ROOM_LABEL} initialize() of '${state.ROOM_LABEL}'. "
      //+ "Subscribing to modeHandler."
  )
  state.brightLuxSensors = []
  populateStateScenesAssignValues()
  clearManualOverride()
  settings.indDevices.each { device -> unsubscribe(device) }
  subscribeToMotionSensorHandler()
  subscribeToLuxSensorHandler()
  // ACTIVATION
  //   - If Automatic is already active in the PBSG, pbsg_ButtonOnCallback()
  //     will not be called.
  //   - It is better to include a redundant call here than to miss
  //     proper room activation on initialization.
  Map pbsg = atomicState."${state.ROOM_LABEL}"
  if (pbsg) {
    pbsg_ActivateButton(pbsg.name, 'Automatic')
    pbsg_ButtonOnCallback(pbsg.name)
  } else {
    logWarn(
      'initialize',
      'The RsPBSG is pending additional configuration data.'
    )
  }
}

void idMotionSensors() {
  input(
    name: 'motionSensors',
    title: [
      h3('Identify Room Motion Sensors'),
      bullet2('The special scene OFF is Automatically added'),
      bullet2('OFF is invoked when the room is unoccupied')
    ].join('<br/>'),
    type: 'device.LutronMotionSensor',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void idLuxSensors() {
  input(
    name: 'luxSensors',
    title: [
      h3('Identify Room Lux Sensors'),
      bullet2('The special scene OFF is Automatically added'),
      bullet2('OFF is invoked when no Lux Sensor is above threshold')
    ].join('<br/>'),
    type: 'capability.illuminanceMeasurement',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void idLowLightThreshold() {
  input(
    name: 'lowLuxThreshold',
    title: [
      h3('Identify Low-Light Lux Threshold')
    ].join('<br/>'),
    type: 'number',
    submitOnChange: true,
    required: false,
    multiple: false
  )
}

void nameCustomScene() {
  input(
    name: customScene,
    type: 'text',
    title: h2('Custom Scene Name (Optional)'),
    width: 4,
    submitOnChange: true,
    required: false
  )
}

void adjustStateScenesKeys() {
  ArrayList assembleScenes = modeNames()
  if (settings.customScene) {
    assembleScenes << settings.customScene
  }
  if (settings.motionSensors || settings.luxSensors) {
    assembleScenes << 'OFF'
  }
  // The following is a work around after issues with map.retainAll {}
  state.scenes = state.scenes?.collectEntries { k, v ->
    assembleScenes.contains(k) ? [k, v] : [:]
  }
}

Map getDeviceValues (String scene) {
  String keyPrefix = "scene^${scene}^"
  List<DevW> allowedDevices = allowedDevices = [ *settings.get('indDevices'), *settings.get('repeaters')]
  List<String> allowedDeviceIds = allowedDevices.collect { getDeviceId(it) }
  Map results = ['Rep': [:], 'Ind': [:]]
  settings.findAll { k1, v1 ->
    k1.startsWith(keyPrefix)
  }.each { k2, v2 ->
    ArrayList typeAndId = k2.substring(keyPrefix.size()).tokenize('^')
    if (typeAndId[0] == 'RA2') {
      logWarn('getDeviceValues', "Removing stale RA2 setting? >${k2}<")
      app.removeSetting(k2)
    } else if (allowedDeviceIds.contains(typeAndId[1]) == false) {
      logWarn('getDeviceValues', "Removing stale Device setting? >${k2}<")
    } else {
      // Enforce min/max constraints on Independent (Ind) device value.
      if (typeAndId[1] == 'Ind') {
        if (v2 > 100) {
          results.put(typeAndId[0], [*:results.get(typeAndId[0]), (typeAndId[1]):100])
        } else if (v2 < 0) {
          results.put(typeAndId[0], [*:results.get(typeAndId[0]), (typeAndId[1]):0])
        }
      } else {
        results.put(typeAndId[0], [*:results.get(typeAndId[0]), (typeAndId[1]):v2])
      }
    }
  }
  return results
}

void populateStateScenesAssignValues() {
  state.scenes = state.scenes.collectEntries { scene, map ->
    if (scene == 'INACTIVE') {
      Map M = getDeviceValues(scene)
      if (M) ['OFF', M]
    } else {
      Map M = getDeviceValues(scene)
      if (M) [scene, M]
    }
  }
}

void idRa2RepeatersImplementingScenes() {
  input(
    name: 'repeaters',
    title: h3("Identify RA2 Repeaters with Integration Buttons for Room Scenes"),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void idIndDevices() {
  input(
    name: 'indDevices',
    title: h3("Identify the Room's Non-RA2 Devices (e.g., Lutron Caséta, Z-Wave)"),
    type: 'capability.switch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void configureRoomScene() {
  // DESIGN NOTES
  //   There are three steps to populate "state.scenes" map.
  //   (1) adjustStateScenesKeys() creates a per-scene key and
  //       sets the value to [].
  //   (2) This method populates Settings keys "scene^SCENENAME^Ind|Rep^DNI".
  //       with Integer values:
  //         - 'light level' for Independent devices (Ind)
  //         - 'virtual button number' for RA2 Repeaters (RA2)
  //   (3) populateStateScenesAssignValues() harvests the settings from
  //       Step 2 to complete the "state.scenes" map.
  // VIRTUAL TABLE
  //   Hubitat page display logic simulates table cells.
  //     - Full-sized displays (computer monitors) are 12 cells wide.
  //     - Phone-sized displays are 4 cells wide.
  //   To ensure that each scene starts on a new row, this method adds
  //   empty cells (modulo 12) to ensure each scene begins in column 1.
  if (state.scenes) {
    ArrayList currSettingsKeys = []
    state.scenes?.sort().each { sceneName, ignoredValue ->
      if (sceneName == 'INACTIVE') { sceneName = 'OFF' }
      // Ignore the current componentList. Rebuilt it from scratch.
      Integer tableCol = 3
      paragraph("<br/><b>${sceneName} →</b>", width: 2)
      settings.indDevices?.each { d ->
        String inputName = "scene^${sceneName}^Ind^${getDeviceId(d)}"
        currSettingsKeys += inputName
        tableCol += 3
        input(
          name: inputName,
          type: 'number',
          title: "${b(d.label)}<br/>Level 0..100",
          width: 3,
          submitOnChange: true,
          required: false,
          multiple: false,
          defaultValue: 0
        )
      }
      settings.repeaters?.each {d ->
        String inputName = "scene^${sceneName}^Rep^${getDeviceId(d)}"
        currSettingsKeys += inputName
        tableCol += 3
        input(
          name: inputName,
          type: 'number',
          title: "${b(d.label)}<br/>Button #",
          width: 3,
          submitOnChange: true,
          required: false,
          multiple: false,
          defaultValue: 0
        )
      }
      // Pad the remainder of the table row with empty cells.
      while (tableCol++ % 12) {
        paragraph('', width: 1)
      }
    }
  }
}

Map RoomScenesPage() {
  // The parent application (Whole House Automation) assigns a unique label
  // to each WHA Rooms instance. Capture app.label as state.ROOM_LABEL.
  return dynamicPage(
    name: 'RoomScenesPage',
    title: [
      h1("${app.label} Scenes - ${app.id}"),
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
    state.ROOM_LABEL = app.label  // WHA creates App w/ Label == Room Name
    state.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
    state.remove('sufficientLight')
    state.remove('targetScene')
    state.brightLuxSensors = []
    state.remove('RsPBSG_LABEL')
    state.remove('pbsgStore')
    app.removeSetting('pbsgLogThresh')
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
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // ERROR, WARN, INFO, DEBUG, TRACE
      idMotionSensors()
      idLuxSensors()
      if (settings.luxSensors) { idLowLightThreshold() }
      nameCustomScene()
      adjustStateScenesKeys()
      //*********************************************************************
      if (state.scenes) {
        ArrayList scenes = state.scenes.collect{ k, v -> return k }
        // For now, state.ROOM_LABEL is redundant given atomicState.name.
        atomicState."${state.ROOM_LABEL}" = [
          'name': state.ROOM_LABEL,
          'all': [ *scenes, 'Automatic' ].minus([ 'INACTIVE', 'OFF' ]),
          'dflt': 'Automatic',
          'instType': 'pbsg'  // Just a pbsg until elevated to 'room'
        ]
        Map rsPBSG = pbsg_BuildToConfig(state.ROOM_LABEL)
      } else {
        paragraph "Creation of the room's PBSG is pending identification of room scenes"
      }
      //*********************************************************************
      idRa2RepeatersImplementingScenes()
      idIndDevices()
      configureRoomScene()
    }
  }
}
