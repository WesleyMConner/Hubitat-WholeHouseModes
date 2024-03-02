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

//---- Repeater Integration Buttons

/*
Map repButtons = [
  'RA2 Repeater 1 (ra2-1)': [
    'DenLamp Chill': 11,
    'DenLamp Clean': 12,
    'DenLamp Day': 13,
    'DenLamp Night': 14,
    'DenLamp Off': 15,
    'DenLamp Party': 16,
    'DenLamp Supp': 17,
    'DenLamp TV': 18,
    'Kitchen Chill': 21,
    'Kitchen Clean': 22,
    'Kitchen Day': 23,
    'Kitchen Night': 24,
    'Kitchen Off': 25,
    'Kitchen Party': 26,
    'Kitchen Supp': 27,
    'Kitchen TV': 28,
    'Kitchen _Cook': 29,
    'Den Chill': 41,
    'Den Clean': 42,
    'Den Day': 43,
    'Den Night': 44,
    'Den Off': 45,
    'Den Party': 46,
    'Den Supp': 47,
    'Den TV': 48,
    'Guest Chill': 51,
    'Guest Clean': 52,
    'Guest Day': 53,
    'Guest Night': 54,
    'Guest Off': 55,
    'Guest Party': 56,
    'Guest Supp': 57,
    'Guest TV': 58,
    'BathLHS Chill': 61,
    'BathLHS Clean': 62,
    'BathLHS Day': 63,
    'BathLHS Night': 64,
    'BathLHS Off': 65,
    'BathLHS Party': 66,
    'BathLHS Supp': 67,
    'BathLHS TV': 68,
    'BathRHS Chill': 71,
    'BathRHS Clean': 72,
    'BathRHS Day': 73,
    'BathRHS Night': 74,
    'BathRHS Off': 75,
    'BathRHS Party': 76,
    'BathRHS Supp': 77,
    'BathRHS TV': 78,
    'Main Chill': 81,
    'Main Clean': 82,
    'Main Day': 83,
    'Main Night': 84,
    'Main Off': 85,
    'Main Party': 86,
    'Main Supp': 87,
    'Main TV': 88
  ],
  'RA2 Repeater 2 (ra2-83)': [
    'PrimBath Chill': 11,
    'PrimBath Clean': 12,
    'PrimBath Day': 13,
    'PrimBath Night': 14,
    'PrimBath Off': 15,
    'PrimBath Party': 16,
    'PrimBath Supp': 17,
    'PrimBath TV': 18,
    'Primary Chill': 21,
    'Primary Clean': 22,
    'Primary Day': 23,
    'Primary Night': 24,
    'Primary Off': 25,
    'Primary Party': 26,
    'Primary Supp': 27,
    'Primary TV': 28,
    'Office Chill': 51,
    'Office Clean': 52,
    'Office Day': 53,
    'Office Night': 54,
    'Office Off': 55,
    'Office Party': 56,
    'Office Supp': 57,
    'Office TV': 58,
    'Yard High': 61,
    'Yard Low': 62,
    'Yard Off': 63,
    'Lanai Chill': 71,
    'Lanai Clean': 72,
    'Lanai Day': 73,
    'Lanai Night': 74,
    'Lanai Off': 75,
    'Lanai Party': 76,
    'Lanai Supp': 77,
    'Lanai TV': 78,
    'Lanai _Games': 79
  ],
  'Caséta Repeater': [
    'Lanai Chill' : 1,
    'Lanai Cleaning' : 2,
    'Lanai Day' : 3,
    'Lanai Games' : 4,
    'Lanai Night' : 5,
    'Lanai Party' : 6,
    'Lanai Supp' : 7,
    'Lanai TV' : 8
  ]
]
*/

//---- MANAGE STATE SCENES

//---- CORE METHODS (Internal)

List<String> getScenes() {
  return state.scenes.collect{ it.key }
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
    ? 'INACTIVE' : state.activeScene
}

void pushRepeaterButton (String repeaterId, Long buttonNumber) {
  //---> logInfo('#208', "${repeaterId}..${buttonNumber}")
  settings.repeaters.each{ repeater ->
    if (getDeviceId(repeater) == repeaterId) {
      //---> logInfo('#211', "${getDeviceId(repeater)}, ${repeaterId}, ${buttonNumber}")
      repeater.push(buttonNumber)
    }
  }
}

void setDeviceLevel (String deviceId, Long level) {
  settings.indDevices.each{ device ->
    if (getDeviceId(repeater) == repeaterId) {
      if (device.hasCommand('setLevel')) {
        logTrace('activateScene', "Setting ${b(deviceId)} to level ${b(level)}")
        // Some devices do not support a level of 100.
        device.setLevel(level == 100 ? 99 : level)
        //-> device.on() // NOT PART OF ORIGINAL, MAY BE IMPLIED
      }
      if (value == 0) {
        logTrace('activateScene', "Setting ${b(deviceId)} to off")
        device.off()
      } else if (value == 100) {
        logTrace('activateScene', "Setting ${b(deviceId)} to on")
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
//---> logInfo('#242', "${state.currScene}..${expectedScene}")
    // Decode and process the scene's per-device actions
    Map actions = state.scenes.get(state.currScene)
//---> logInfo('#245', "${actions}")
    actions.get('Rep').each{ repeaterId, button ->
//---> logInfo('#247', "${repeaterId}..${button}")
      logInfo('activateScene', "Pushing repeater (${repeaterId}) button (${button})")
      pushRepeaterButton(repeaterId, button)
    }
//---> logInfo('#251', "-")
    actions.get('Ind').each{ deviceId, value ->
//---> logInfo('#253', "${deviceId}..${value}")
      logTrace('activateScene', "Setting device (${deviceId} to level (${value})")
      setDeviceLevel(deviceId, value)
    }
//---> logInfo('#257', "-")
  }
}

void updateTargetScene() {
  // Upstream Pbsg/Dashboard/Alexa actions should clear Manual Overrides
  //---> logTrace('updateTargetScene', [
  //--->   'At entry',
  //--->   "state.activeButton: ${b(state.activeButton)}",
  //--->   "state.activeScene: ${b(state.activeScene)}",
  //--->   "isManualOverride(): ${b(isManualOverride())}"
  //---> ])
  if (
    (state.activeButton == 'AUTOMATIC' && !state.activeScene)
    || (state.activeButton == 'AUTOMATIC' && !isManualOverride())
  ) {
    // Ensure that targetScene is per the latest Hubitat mode.
    String mode = getLocation().getMode()
    state.activeScene = settings["modeToScene^${mode}"]
  } else {
    state.activeScene = state.activeButton
  }
  //---> logTrace('updateTargetScene', [
  //--->   'At exit',
  //--->   "state.activeScene: ${b(state.activeScene)}"
  //---> ])
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
  settings.sceneButtons.each{ d ->
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
      ArrayList<String> roomScenes = [ *scenes, 'AUTOMATIC' ]
      roomScenes.removeAll{ it == 'INACTIVE' }
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
  settings.indDevices.each{ d ->
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
  settings.seeTouchKpads.each{ d ->
    logInfo(
      'subscribeToKpadHandler',
      "${state.ROOM_LABEL} subscribing to keypad ${deviceInfo(d)}"
    )
    subscribe(d, kpadHandler, ['filterEvents': true])
  }
}

void subscribeToRepHandler() {
  settings.repeaters.each{ d ->
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
    settings.motionSensors.each{ d ->
      logInfo(
        'initialize',
        "${state.ROOM_LABEL} subscribing to Motion Sensor ${deviceInfo(d)}"
      )
      subscribe(d, motionSensorHandler, ['filterEvents': true])
      if (d.latestState('motion').value == 'active') {
        state.activeMotionSensors = cleanStrings([*state.activeMotionSensors, displayName])
        activateScene()
      } else {
        state.activeMotionSensors?.removeAll{ it == displayName }
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
    settings.luxSensors.each{ d ->
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

void subscribeToPicoHandler() {
  settings.picos.each{ d ->
    logInfo(
      'initialize',
      "${state.ROOM_LABEL} subscribing to Pico ${deviceInfo(d)}"
    )
    subscribe(d, picoHandler, ['filterEvents': true])
  }
}

//---- EVENT HANDLERS

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
  expected = (expected == 100) ? 99 : expected
  //---> logInfo('indDeviceHandler #532', "${deviceId}: ${reported} (${expected})")
  if (reported == expected) {
    state.moDetected = state.moDetected.collect{ key, value ->
      if (key != deviceId) { [key, value] }
    }
  } else {
    state.moDetected.put(deviceId, "${reported} (${expected})")
  }
  //---> logInfo('indDeviceHandler #540', "${state.moDetected}")
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
      state.activeMotionSensors?.removeAll{ it == e.displayName }
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
      state.brightLuxSensors?.removeAll{ it == e.displayName }
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
          settings.indDevices.each{ d ->
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
          settings.indDevices.each{ d ->
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

//---- SYSTEM CALLBACKS

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
  //-> subscribeToIndDeviceHandlerNoDelay()
  settings.indDevices.each{ device -> unsubscribe(device) }
  subscribeToKpadHandler()
  subscribeToRepHandler()
  subscribeToModeHandler()
  subscribeToMotionSensorHandler()
  subscribeToLuxSensorHandler()
  subscribeToPicoHandler()
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

//---- GUI / PAGE RENDERING

void idMotionSensors() {
  input(
    name: 'motionSensors',
    title: [
      heading3('Identify Room Motion Sensors'),
      bullet2('The special scene INACTIVE is automatically added'),
      bullet2('INACTIVE is invoked when the room is unoccupied')
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
      bullet2('The special scene INACTIVE is automatically added'),
      bullet2('INACTIVE is invoked when no Lux Sensor is above threshold')
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

void selectModesAsScenes() {
  ArrayList<String> scenes = modeNames()
  input(
    name: 'modesAsScenes',
    type: 'enum',
    title: heading2('Select Mode Names to use as Scenes Names'),
    submitOnChange: true,
    required: false,
    multiple: true,
    options: scenes.sort()
  )
}

void nameCustomScenes() {
  String prefix = 'customScene'
  LinkedHashMap<String, String> slots = [
    "${prefix}1": settings["${prefix}1"],
    "${prefix}2": settings["${prefix}2"],
    "${prefix}3": settings["${prefix}3"],
    "${prefix}4": settings["${prefix}4"],
    "${prefix}5": settings["${prefix}5"],
    "${prefix}6": settings["${prefix}6"],
    "${prefix}7": settings["${prefix}7"],
    "${prefix}8": settings["${prefix}8"],
    "${prefix}9": settings["${prefix}9"]
  ]
  LinkedHashMap<String, String> filled = slots?.findAll{it.value}
  // Only present 1 empty sceen "slot" at a time.
  LinkedHashMap<String, String> firstOpen = slots?.findAll{!it.value}?.take(1)
  LinkedHashMap<String, String> custom = \
    firstOpen + filled.sort{ a, b -> a.value <=> b.value }
  custom.each{ key, value ->
    input(
      name: key,
      type: 'text',
      title: heading2('Custom Scene Name (Optional)'),
      width: 4,
      submitOnChange: true,
      required: false,
      defaultValue: value
    )
  }
}

void adjustStateScenesKeys() {
  ArrayList<String> assembleScenes = settings.modesAsScenes ?: []
  assembleScenes = assembleScenes.flatten()
  if (settings.motionSensors || settings.luxSensors) {
    assembleScenes += 'INACTIVE'
  }
  String prefix = 'customScene'
  ArrayList<String> customScenes = []
  customScenes += settings["${prefix}1"]
  customScenes += settings["${prefix}2"]
  customScenes += settings["${prefix}3"]
  customScenes += settings["${prefix}4"]
  customScenes += settings["${prefix}5"]
  customScenes += settings["${prefix}6"]
  customScenes += settings["${prefix}7"]
  customScenes += settings["${prefix}8"]
  customScenes += settings["${prefix}9"]
  customScenes.removeAll{it == null}
  if (customScenes) {
    assembleScenes << customScenes
    assembleScenes = assembleScenes.flatten().toUnique()
  }
  // Keep existing scenes keys that are present in assembleScenes.
  state.scenes = state.scenes.collectEntries{ key, value ->
    if (assembleScenes.contains(key)) {
      [key, value]
    } else {
      []
    }
  }
  // Add any new scene keys from assembleScenes.
  assembleScenes.removeAll{ scene -> getScenes() }
  state.scenes << assembleScenes?.collectEntries{ [(it): []] }
  //-> logInfo('#871', "state.scenes: ${state.scenes}")
}

void idSceneForMode() {
  if (state.scenes) {
    paragraph heading2('Identify a Scene for each Hubitat Mode')
    getLocation().getModes().collect{mode -> mode.name}.sort().each{ modeName ->
      String inputName = "modeToScene^${modeName}"
      String defaultValue = settings[inputName]
        ?: state.scenes.containsKey(modeName) ? modeName : null
      input(
        name: inputName,
        type: 'enum',
        title: heading3(modeName),
        width: 2,
        submitOnChange: true,
        required: true,
        multiple: false,
        options: getScenes(),
        defaultValue: defaultValue
      )
    }
  } else {
    paragraph 'Mode-to-Scene selection will proceed once scene names exist.'
  }
}

void authSeeTouchKpads() {
  input(
    name: 'seeTouchKpads',
    title: heading3("Identify Keypads used to Activate Room Scenes"),
    type: 'device.LutronSeeTouchKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void idRoomSceneButtons() {
  input(
    name: 'sceneButtons',
    title: heading3("Identify Keypad Buttons that Activate a Room Scene"),
    type: 'device.LutronComponentSwitch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void mapKpadButtonDniToScene() {
  Map<String, String> result = [:]
  state.sceneButtonMap.collect{ kpadDni, buttonMap ->
    buttonMap.each{ buttonNumber, targetScene ->
      result["${kpadDni}-${buttonNumber}"] = targetScene
    }
  }
  state.kpadButtonDniToTargetScene = result
}

void wireKpadButtonsToScenes() {
  List<String> scenes = getScenes()
  if (scenes == null || settings?.sceneButtons == null) {
    //paragraph('No Room Scene Kpad buttons have been identified.')
  } else {
    identifyLedButtonsForListItems(
      scenes,
      settings.sceneButtons,
      'sceneButton'
    )
    populateStateKpadButtons('sceneButton')
    mapKpadButtonDniToScene()
  }
}

Map getDeviceValues (String scene) {
  String keyPrefix = "scene^${scene}^"
  List<DevW> allowedDevices = allowedDevices = [ *settings.get('indDevices'), *settings.get('repeaters')]
  List<String> allowedDeviceIds = allowedDevices.collect{ getDeviceId(it) }
  Map results = ['Rep': [:], 'Ind': [:]]
  //---> logInfo('#966', [
  //--->   "scene: ${scene}",
  //--->   "keyPrefix: ${keyPrefix}",
  //--->   "allowedDevices: ${allowedDevices}",
  //--->   "allowedDeviceIds: ${allowedDeviceIds}",
  //--->   "results: ${results}"
  //---> ])
  settings.findAll{ k1, v1 ->
    //---> logInfo('#974', "k1: ${k1}, v1: ${v1}")
    k1.startsWith(keyPrefix)
  }.each { k2, v2 ->
    //---> logInfo('#977', "k2: ${k2}, v2: ${v2}")
    ArrayList<String> typeAndId = k2.substring(keyPrefix.size()).tokenize('^')
    if (typeAndId[0] == 'RA2') {
      logWarn('getDeviceValues', "Removing stale RA2 setting? >${k2}<")
      app.removeSetting(k2)
    } else if (allowedDeviceIds.contains(typeAndId[1]) == false) {
      logWarn('getDeviceValues', "Removing stale Device setting? >${k2}<")
    } else {
      //---> logInfo('#985', "${typeAndId[0]}..${typeAndId[1]}..${v2}")
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
    //---> logInfo('#997', "results: ${results}")
  }
  return results
}

void populateStateScenesAssignValues() {
  state.scenes = state.scenes.collectEntries{ scene, map ->
    //---> logInfo('#991', "scene: ${scene}, map: ${map}")
    Map M = getDeviceValues(scene)
    if (M) [scene, M]
  }
}

void authRoomScenesPicos() {
  input(
    name: 'picos',
    title: heading3("Identify Picos that Trigger or Adjust Room Scenes"),
    type: 'device.LutronFastPico',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

Map<String,String> namePicoButtons(DevW pico) {
  String label = pico.label
  String id = pico.id
  return [
    "${id}^1": "${label}^1",
    "${id}^2": "${label}^2",
    "${id}^3": "${label}^3",
    "${id}^4": "${label}^4",
    "${id}^5": "${label}^5"
  ]
}

Map<String, String> picoButtonPicklist(List<DevW> picos) {
  Map<String, String> results = [:]
  picos.each{ pico -> results << namePicoButtons(pico) }
  return results
}

void selectPicoButtonsForScene(List<DevW> picos) {
  List<String> scenes = getScenes()
  if (scenes) {
    ArrayList<String> picoScenes = ['AUTOMATIC'] << scenes
    picoScenes.flatten().each{ sceneName ->
      input(
        name: "picoButtons_${sceneName}",
        type: 'enum',
        title: heading2("Pico Buttons -> ${b(sceneName)}"),
        width: 4,
        submitOnChange: true,
        required: false,
        multiple: true,
        options: picoButtonPicklist(picos)
      )
    }
  }
}

void populateStatePicoButtonToTargetScene() {
  state.picoButtonToTargetScene = [:]
  settings.findAll{ key, value -> key.contains('picoButtons_') }
          .each{ key, value ->
            String scene = key.tokenize('_')[1]
            value.each{ idAndButton ->
              ArrayList<String> valTok = idAndButton.tokenize('^')
              String deviceId = valTok[0]
              String buttonNumber = valTok[1]
              if (state.picoButtonToTargetScene[deviceId] == null) {
                state.picoButtonToTargetScene[deviceId] = [:]
              }
              state.picoButtonToTargetScene[deviceId][buttonNumber] = scene
            }
          }
}

void wirePicoButtonsToScenes() {
  if (settings.picos) {
    selectPicoButtonsForScene(settings.picos)
    populateStatePicoButtonToTargetScene()
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
    ArrayList<String> currSettingsKeys = []
    state.scenes?.each{ sceneName, ignoredValue ->
      // Ignore the current componentList. Rebuilt it from scratch.
      Integer tableCol = 3
      paragraph("<br/><b>${sceneName} →</b>", width: 2)
      settings.indDevices?.each{ d ->
        String inputName = "scene^${sceneName}^Ind^${getDeviceId(d)}"
        currSettingsKeys += inputName
        tableCol += 3
        input(
          name: inputName,
          type: 'number',
          title: "${b(d.label)}<br/>Level 0..99",
          width: 3,
          submitOnChange: true,
          required: false,
          multiple: false,
          defaultValue: 0
        )
      }
      settings.repeaters?.each{d ->
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
    //-> state.scenes = [ : ]
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
    /*
    app.removeSetting('ra2Repeaters')
    app.removeSetting('mainRepeaters')
    app.removeSetting('hubitatQueryString')
    app.removeSetting('mainRepeaters')
    app.removeSetting('ra2Repeaters')
    app.removeSetting('scene^Chill^RA2^pro2-1')
    app.removeSetting('scene^Chill^RA2^ra2-1')
    app.removeSetting('scene^Chill^RA2^ra2-83')
    app.removeSetting('scene^Cleaning^RA2^pro2-1')
    app.removeSetting('scene^Cleaning^RA2^ra2-1')
    app.removeSetting('scene^Cleaning^RA2^ra2-83')
    app.removeSetting('scene^Cook^Rep^ra2-1')
    app.removeSetting('scene^Day^RA2^pro2-1')
    app.removeSetting('scene^Day^RA2^ra2-1')
    app.removeSetting('scene^Day^RA2^ra2-83')
    app.removeSetting('scene^Games^RA2^pro2-1')
    app.removeSetting('scene^Games^RA2^ra2-83')
    app.removeSetting('scene^Games^Rep^ra2-1')
    app.removeSetting('scene^Games^Rep^ra2-83')
    app.removeSetting('scene^INACTIVE^RA2^ra2-1')
    app.removeSetting('scene^INACTIVE^RA2^ra2-83')
    app.removeSetting('scene^Night^RA2^pro2-1')
    app.removeSetting('scene^Night^RA2^ra2-1')
    app.removeSetting('scene^Night^RA2^ra2-83')
    app.removeSetting('scene^Off^RA2^ra2-1')
    app.removeSetting('scene^Off^RA2^ra2-83')
    app.removeSetting('scene^Party^RA2^pro2-1')
    app.removeSetting('scene^Party^RA2^ra2-1')
    app.removeSetting('scene^Party^RA2^ra2-83')
    app.removeSetting('scene^Supplement^RA2^pro2-1')
    app.removeSetting('scene^Supplement^RA2^ra2-1')
    app.removeSetting('scene^Supplement^RA2^ra2-83')
    app.removeSetting('scene^TV^RA2^pro2-1')
    app.removeSetting('scene^TV^RA2^ra2-1')
    app.removeSetting('scene^TV^RA2^ra2-83')
    app.removeSetting('scene^TV^Rep^ra2-1')
    app.removeSetting('scene^TV^Rep^ra2-83')
    */
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      solicitLogThreshold('pbsgLogThresh', 'INFO') // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      idMotionSensors()
      idLuxSensors()
      if (settings.luxSensors) { idLowLightThreshold() }
      selectModesAsScenes()
      nameCustomScenes()
      adjustStateScenesKeys()
      idSceneForMode()
      authSeeTouchKpads()
      idRoomSceneButtons()
      wireKpadButtonsToScenes()
      authRoomScenesPicos()
      wirePicoButtonsToScenes()
      idRa2RepeatersImplementingScenes()
      idIndDevices()
      configureRoomScene()
      createRSPbsgAndPageLink()
      paragraph([
        heading1('Debug'),
        *appStateAsBullets(true),
        *appSettingsAsBullets(true)
      ].join('<br/>'))
    }
  }
}
