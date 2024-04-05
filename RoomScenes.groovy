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
#include wesmc.lFifo
#include wesmc.lHExt
#include wesmc.lHUI
#include wesmc.lLut
#include wesmc.lPbsg

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

List<String> getScenes() {
  return state.scenes.collect { it.key == 'INACTIVE' ? 'OFF' : it.key }
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

String getDeviceId(DevW device) {
  return device?.label ? extractDeviceIdFromLabel(device.label) : null
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
  String expectedScene = expectedScene()
  if (state.currScene != expectedScene) {
    logInfo('activateScene', "${state.currScene} -> ${expectedScene}")
    state.currScene = expectedScene
    // Decode and process the scene's per-device actions
    //--xx-> logInfo('activateScene', "state.scenes: ${state.scenes}, state.currScene: ${state.currScene}")
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
  // Upstream Pbsg/Dashboard/Alexa actions should clear Manual Overrides
  if (
    (state.activeButton == 'AUTOMATIC' && !state.activeScene)
    || (state.activeButton == 'AUTOMATIC' && !isManualOverride())
  ) {
    // Ensure that targetScene is per the latest Hubitat mode.
    //String mode = getLocation().getMode()
    state.activeScene = getLocation().getMode() //settings["modeToScene^${mode}"]
  } else {
    state.activeScene = state.activeButton
  }
}

void buttonOnCallback(String button) {
  // Pbsg/Dashboard/Alexa actions override Manual Overrides.
  // Scene activation enforces room occupancy.
  if (!button) {
    logWarn(
      'buttonOnCallback',
      'A null argument was received, using AUTOMATIC as a default'
    )
  }
  state.activeButton = button ?: 'AUTOMATIC'
  logTrace(
    'buttonOnCallback',
    "Button ${b(button)} -> state.activeButton: ${b(state.activeButton)}")
  clearManualOverride()
  updateTargetScene()
  activateScene()
  updateLutronKpadLeds()
}

void updateLutronKpadLeds() {  // old argument was "String currScene"
  settings.sceneButtons.each { d ->
    String buttonDni = d.getDeviceNetworkId()
    String sceneTarget = state.kpadButtonDniToTargetScene[buttonDni]
    if (state.activeScene == sceneTarget) {
      logInfo(
        'updateLutronKpadLeds',
        "Turning on LED ${buttonDni} for ${state.ROOM_LABEL} scene ${sceneTarget}"
      )
      d.on()
    } else {
      logTrace(
        'updateLutronKpadLeds',
        "Turning off LED ${buttonDni} for ${state.ROOM_LABEL} scene ${sceneTarget}"
      )
      d.off()
    }
  }
}

Boolean isDeviceType(String devTypeCandidate) {
  return ['Rep', 'Ind'].contains(devTypeCandidate)
}

Integer expectedSceneDeviceValue(String devType, String deviceId) {
  Integer retVal = null
  if (isDeviceType(devType)) {
    retVal = state.scenes?.get(state.activeScene)?.get(devType)?.get(deviceId)
    //-> logInfo('#328', "${devType}..${deviceId} -> ${retVal}")
  } else {
    logError('expectedSceneDeviceValue', "devType (${devType}) not recognized")
  }
  return retVal
}

InstAppW getOrCreateRSPbsg() {
  // The PBSG is created by createRSPbsgAndPageLink()
  InstAppW pbsgApp = app.getChildAppByLabel(state.RSPBSG_LABEL)
  if (!pbsgApp) {
    List<String> scenes = getScenes()
    if (scenes) {
      logWarn('getOrCreateRSPbsg', "Adding RSPbsg ${state.RSPBSG_LABEL}")
      pbsgApp = addChildApp('wesmc', 'RSPbsg', state.RSPBSG_LABEL)
      ArrayList roomScenes = [ *scenes, 'AUTOMATIC' ]
      roomScenes.removeAll { it == 'OFF' }
      String dfltScene = 'AUTOMATIC'
      String currScene = null
      pbsgApp.pbsgConfigure(
        roomScenes,  // Create a PBSG button per Hubitat Mode name
        dfltScene,   // 'Day' is the default Mode/Button
        currScene,   // Activate the Button for the current Mode
        settings.pbsgLogThresh ?: 'INFO' // 'INFO' for normal operations
                                         // 'DEBUG' to walk key PBSG methods
                                         // 'TRACE' to include PBSG and VSW state
      )
    } else {
      logWarn('getOrCreateRSPbsg', 'RSPbsg creation is pending room scenes')
    }
  }
  return pbsgApp
}

void createRSPbsgAndPageLink() {
  InstAppW pbsgApp = getOrCreateRSPbsg()
  if (pbsgApp) {
    href(
      name: appInfo(pbsgApp),
      width: 2,
      url: "/installedapp/configure/${pbsgApp.id}/RSPbsgPage",
      style: 'internal',
      title: "Edit <b>${appInfo(pbsgApp)}</b>",
      state: null
    )
  } else {
    paragraph "Creation of the MPbsgHref is pending required data."
  }
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

void subscribeToKpadHandler() {
  settings.seeTouchKpads.each { d ->
    logInfo(
      'subscribeToKpadHandler',
      "${state.ROOM_LABEL} subscribing to keypad ${deviceInfo(d)}"
    )
    subscribe(d, kpadHandler, ['filterEvents': true])
  }
}

void subscribeToRepHandler() {
  settings.repeaters.each { d ->
    logInfo(
      'subscribeToRepHandler',
      "${state.ROOM_LABEL} subscribing to Repeater ${deviceInfo(d)}"
    )
    subscribe(d, repeaterHandler, ['filterEvents': true])
  }
}

void subscribeRepToHandler(Map data) {
  // USAGE:
  //   runIn(1, 'subscribeRepToHandler', [data: [device: d]])
  // Unlike some Independent Devices (RA2 and Caséta) RA2 Repeaters
  // are not particularly subject to stale Hubitat state; HOWEVER,
  // callbacks that occur quickly (within 1/2 second) after a buton press
  // subject Hubitat to callback overload (during WHA scene chantes).
  // Briefly unsubscribe/subscribe to avoid this situation.
  logTrace(
    'subscribeRepToHandler',
    "${state.ROOM_LABEL} subscribing ${deviceInfo(data.device)}"
  )
  subscribe(device, repeaterHandler, ['filterEvents': true])
}

void unsubscribeRepToHandler(DevW device) {
  // Unlike some Independent Devices (RA2 and Caséta) RA2 Repeaters
  // are not particularly subject to stale Hubitat state; HOWEVER,
  // callbacks that occur quickly (within 1/2 second) after a buton press
  // subject Hubitat to callback overload (during WHA scene chantes).
  // Briefly unsubscribe/subscribe to avoid this situation.
  logTrace(
    'unsubscribeRepToHandler',
    "${state.ROOM_LABEL} unsubscribing ${deviceInfo(device)}"
  )
  unsubscribe(device)
}

void subscribeToModeHandler() {
  logInfo(
    'subscribeToModeHandler',
    "${state.ROOM_LABEL} subscribing to location 'mode'"
  )
  subscribe(location, "mode", modeHandler)
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
        'initialize',
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

void kpadHandler(Event e) {
  // Design Note
  //   - The field e.deviceId arrives as a number and must be cast toString().
  //   - Hubitat runs Groovy 2.4. Groovy 3 constructs - x?[]?[] - are not available.
  //   - Kpad buttons are matched to state data to activate a scene.
  logTrace('kpadHandler', [
    "state.activeButton: ${state.activeButton}",
    "state.activeScene: ${state.activeScene}",
    eventDetails(e)
  ])
  switch (e.name) {
    case 'pushed':
      // Toggle the corresponding scene for the keypad button.
      String scene = state.sceneButtonMap?.getAt(e.deviceId.toString())
                                         ?.getAt(e.value)
      if (scene) getOrCreateRSPbsg().pbsgToggleButton(scene)
      // The prospective PBSG callback triggers further local processing.
      break
    case 'held':
    case 'released':
      // Ignore without logging
      break
    default:
      logWarn('kpadHandler', [
        "DNI: '${b(e.deviceId)}'",
        "For '${state.ROOM_LABEL}' unexpected event name ${b(e.name)}"
      ])
  }
}

void repeaterHandler(Event e) {
  // Main Repeaters send various events (e.g., pushed, buttonLed-##).
  // Isolate the buttonLed-## events which confirm|refute state.activeScene.
  if (e.name.startsWith('buttonLed-')) {
    Integer eventButton = safeParseInt(e.name.substring(10))
    String deviceId = extractDeviceIdFromLabel(e.displayName)
    // Is there an expected sceneButton for the deviceId?
    Integer sceneButton = expectedSceneDeviceValue('Rep', deviceId)
    // And if so, does it match the eventButton?
    if (sceneButton && sceneButton == eventButton) {
      // This event can be used to confirm or refute the target scene.
      if (e.value == 'on') {
        // Scene compliance confirmed
        logTrace('repeaterHandler', "${deviceId} complies with scene")
        state.moDetected.remove(deviceId)
      } else if (e.value == 'off') {
        // Scene compliance refuted (i.e., Manual Override)
        String summary = "${deviceId} button ${eventButton} off, expected on"
        logInfo('repeaterHandler', [ 'MANUAL OVERRIDE', summary ])
        state.moDetected[deviceId] = summary
      } else {
        // Error condition
        logWarn(
          'repeaterHandler',
          "Main Repeater (${deviceId}) with unexpected value (${e.value}"
        )
      }
    }
  }
}

void modeHandler(Event e) {
  if (state.activeButton == 'AUTOMATIC') {
    // Hubitat Mode changes only apply when the room's button is 'AUTOMATIC'.
    if (e.name == 'mode') {
      // Let buttonOnCallback() handle activeButton == 'AUTOMATIC'!
      logTrace('modeHandler', 'Calling buttonOnCallback("AUTOMATIC")')
      buttonOnCallback('AUTOMATIC')
    } else {
      logWarn('modeHandler', ['UNEXPECTED EVENT', eventDetails(e)])
    }
  } else {
    logTrace(
      'modeHandler', [
        'Ignored: Mode Change',
        "state.activeButton: ${b(state.activeButton)}",
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
          logInfo('picoHandler', [
            "w/ ${e.deviceId.toString()}-${e.value} toggling ${scene}"
          ])
          getOrCreateRSPbsg().pbsgToggleButton(scene)
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
      + "Subscribing to modeHandler."
  )
  state.brightLuxSensors = []
  populateStateScenesAssignValues()
  clearManualOverride()
  settings.indDevices.each { device -> unsubscribe(device) }
  subscribeToRepHandler()
  subscribeToModeHandler()
  subscribeToMotionSensorHandler()
  subscribeToLuxSensorHandler()
  // ACTIVATION
  //   - If AUTOMATIC is already active in the PBSG, buttonOnCallback()
  //     will not be called.
  //   - It is better to include a redundant call here than to miss
  //     proper room activation on initialization.
  InstAppW pbsg = getOrCreateRSPbsg()
  if (pbsg) {
    pbsg.pbsgActivateButton('AUTOMATIC')
    buttonOnCallback('AUTOMATIC')
  } else {
    logWarn(
      'initialize',
      'The RSPbsg is pending additional configuration data.'
    )
  }
}

void idMotionSensors() {
  input(
    name: 'motionSensors',
    title: [
      heading3('Identify Room Motion Sensors'),
      bullet2('The special scene OFF is automatically added'),
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
      heading3('Identify Room Lux Sensors'),
      bullet2('The special scene OFF is automatically added'),
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
      heading3('Identify Low-Light Lux Threshold')
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
    title: heading2('Custom Scene Name (Optional)'),
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
    title: heading3("Identify RA2 Repeaters with Integration Buttons for Room Scenes"),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void idIndDevices() {
  input(
    name: 'indDevices',
    title: heading3("Identify the Room's Non-RA2 Devices (e.g., Lutron Caséta, Z-Wave)"),
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
    state.ROOM_LABEL = app.label  // WHA creates App w/ Label == Room Name
    state.RSPBSG_LABEL = "${state.ROOM_LABEL}Pbsg"
    state.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
    state.remove('sufficientLight')
    state.remove('targetScene')
    state.brightLuxSensors = []
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
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      solicitLogThreshold('pbsgLogThresh', 'INFO') // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      idMotionSensors()
      idLuxSensors()
      if (settings.luxSensors) { idLowLightThreshold() }
      nameCustomScene()
      adjustStateScenesKeys()
      idRa2RepeatersImplementingScenes()
      idIndDevices()
      configureRoomScene()
      createRSPbsgAndPageLink()
    }
  }
}
