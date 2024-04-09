// ---------------------------------------------------------------------------------
// R O O M
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
// Referenced types below
//   - import com.hubitat.app.DeviceWrapper as DevW
//   - import com.hubitat.hub.domain.Event as Event
// The following are required when using this library.
//   - #include wesmc.lHExt
//   - #include wesmc.lHUI

library(
  name: 'lRoom',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Room Implementation',
  category: 'general purpose'
)

// The psuedo-class "roomStore" facilitates concurrent storage of multiple
// "room" psuedo-class instances.

Map roomStore_Retrieve(String roomName) {
  // Retrieve a "room" psuedo-class instance.
  Map roomStore = state.roomStore ?: [:]
  return roomStore."${roomName}"
}

void roomStore_Save(Map room) {
  // Add/update a "room" psuedo-class instance.
  Map roomStore = state.roomStore ?: [:]
  roomStore."${room.name}" = room
  state.roomStore = roomStore
}

// = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = = =

//import com.hubitat.app.DeviceWrapper as DevW
//import com.hubitat.app.InstalledAppWrapper as InstAppW
//import com.hubitat.hub.domain.Event as Event
//import com.hubitat.hub.domain.Location as Loc
// The Groovy Linter generates false positives on Hubitat #include !!!
//#include wesmc.lHExt
//#include wesmc.lHUI
//#include wesmc.lLut
//#include wesmc.lPbsgv2

//-> String extractDeviceIdFromLabel(String deviceLabel) {
//->   //->x = (deviceLabel =~ /\((.*)\)/)
//->   //->logDebug('extractDeviceIdFromLabel', [
//->   //->  "deviceLabel: ${deviceLabel}",
//->   //->  "x: ${x}",
//->   //->  "x[0]: ${x[0]}",
//->   //->  "x[0]: ${x[0][1]}",
//->   //->])
//->   return (deviceLabel =~ /\((.*)\)/)[0][1]
//-> }

//-> String getDeviceId(DevW device) {
//->   return device?.label ? extractDeviceIdFromLabel(device.label) : null
//-> }

void room_ActivateScene(Map room) {
  String expectedScene = (
    room.activeMotionSensors == false || room.brightLuxSensors == true
  ) ? 'OFF' : room.activeScene
  if (room.currScene != expectedScene) {
    logInfo('activateScene', "${room.currScene} -> ${expectedScene}")
    room.currScene = expectedScene
    // Decode and process the scene's per-device actions
    Map actions = room.scenes.get(room.currScene)
    actions.get('Rep').each { repeaterId, button ->
      logInfo('activateScene', "Pushing repeater (${repeaterId}) button (${button})")
      pushRa2RepButton(repeaterId, button)
    }
    actions.get('Ind').each { deviceLabel, value ->
      setDeviceLevel(deviceLabel, value)
    }
  }
}

void pbsgButtonOnCallback(Map pbsg) {
  // Pbsg/Dashboard/Alexa actions override Manual Overrides.
  // Scene activation enforces room occupancy.
  if (!button) {
    logWarn(
      'pbsgButtonOnCallback',
      'A null argument was received, using AUTOMATIC as a default'
    )
  }
  room.activeButton = button ?: 'AUTOMATIC'
  logInfo(
    'pbsgButtonOnCallback',
    "Button ${b(button)} -> room.activeButton: ${b(room.activeButton)}")
  room.moDetected = [:] // clears Manual Override
  // UPDATE THE TARGET SCENE
  // Upstream Pbsg/Dashboard/Alexa actions should clear Manual Overrides
  if (
    (room.activeButton == 'AUTOMATIC' && !room.activeScene)
    || (room.activeButton == 'AUTOMATIC' && !room.moDetected)
  ) {
    // Ensure that targetScene is per the latest Hubitat mode.
    /* groovylint-disable-next-line UnnecessaryGetter */
    room.activeScene = getLocation().getMode() //settings["modeToScene^${mode}"]
  } else {
    room.activeScene = room.activeButton
  }
  room_ActivateScene(room)
}

Boolean isDeviceType(String devTypeCandidate) {
  return ['Rep', 'Ind'].contains(devTypeCandidate)
}

//??tbd Integer scene_GetDeviceValue(Map )

//-> Integer expectedSceneDeviceValue(Map room, String devType, String deviceLabel) {
//->   Integer retVal = null
//->   if (isDeviceType(devType)) {
//->     // 'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 48 ], 'Ind': [ 'Den - Fireplace (02)': 100 ] ],
//->     retVal = room.scenes?."${room.activeScene}"?."${devType}"?."${deviceLabel}"
//->   } else {
//->     logError('expectedSceneDeviceValue', "devType (${devType}) not recognized")
//->   }
//->   return retVal
//-> }

//-> void subscribeToIndDeviceHandlerNoDelay() {
//->   settings.zWaveDevices.each { d ->
//->     logInfo(
//->       'subscribeToIndDeviceHandlerNoDelay',
//->       "${room.name} subscribing to independentDevice ${deviceInfo(d)}"
//->     )
//->     subscribe(d, indDeviceHandler, ['filterEvents': true])
//->   }
//-> }

//-> void subscribeToRepHandler() {
//->   settings.ra2Repeaters.each { d ->
//->     logInfo(
//->       'subscribeToRepHandler',
//->       "${room.name} subscribing to Repeater ${deviceInfo(d)}"
//->     )
//->     subscribe(d, repeaterHandler, ['filterEvents': true])
//->   }
//-> }

void subscribeToModeHandler() { // MOVE TO WHA SCOPE
  logInfo(
    'subscribeToModeHandler',
    "${room.name} subscribing to location 'mode'"
  )
  subscribe(location, 'mode', modeHandler)
}

void subscribeToMotionSensorHandler() { // RETAIN AT ROOM SCOPE
  if (settings.motionSensors) {
    room.activeMotionSensors = []
    settings.motionSensors.each { d ->
      logInfo(
        'initialize',
        "${room.name} subscribing to Motion Sensor ${deviceInfo(d)}"
      )
      subscribe(d, motionSensorHandler, ['filterEvents': true])
      if (d.latestState('motion').value == 'active') {
        room.activeMotionSensors = cleanStrings([*room.activeMotionSensors, d.displayName])
        room_ActivateScene(room)
      } else {
        room.activeMotionSensors?.removeAll { activeSensor -> activeSensor == d.displayName }
        room_ActivateScene(room)
      }
    }
  } else {
    room.activeMotionSensors = true
  }
}

void subscribeToLuxSensorHandler() {
  if (settings.luxSensors) {
    room.brightLuxSensors = []
    settings.luxSensors.each { d ->
      logInfo(
        'subscribeToLuxSensorHandler',
        "${room.name} subscribing to Lux Sensor ${deviceInfo(d)}"
      )
      subscribe(d, luxSensorHandler, ['filterEvents': true])
    }
  } else {
    room.brightLuxSensors = [ ]
  }
}

void indDeviceHandler(Event e) {
  // Devices send various events (e.g., switch, level, pushed, released).
  // Isolate the events that confirm|refute room.activeScene.
  Integer reported
  // Only select events are considered for MANUAL OVERRIDE detection.
  if (e.name == 'level') {
    reported = safeParseInt(e.value)
  } else if (e.name == 'switch' && e.value == 'off') {
    reported = 0
  }
  String deviceLabel = e.displayName
  Integer expected = room.scenes?."${room.activeScene}"?."${devType}"?."${deviceLabel}"
  if (reported == expected) {
    room.moDetected = room.moDetected.collect { key, value ->
      if (key != deviceLabel) { [key, value] }
    }
  } else {
    room.moDetected.put(deviceLabel, "${reported} (${expected})")
  }
}

void toggleButton(String button) {
  // Toggle the button's device and let activate and deactivate react.
  // This will result in delivery of the scene change via a callback.
  String dni = "${room.name}_${button}"
  DevW device = getChildDevice(dni)
  if (switchState(device) == 'on') {
    device.off()
  } else {
    devive.on()
  }
}

//-> void repeaterHandler(Event e) {
//->   // Main Repeaters send various events (e.g., pushed, buttonLed-##).
//->   // Isolate the buttonLed-## events which confirm|refute room.activeScene.
//->     logInfo('repeaterHandler', e.descriptionText)
//->   if (e.name.startsWith('buttonLed-')) {
//->     Integer eventButton = safeParseInt(e.name.substring(10))
//->     String deviceId = extractDeviceIdFromLabel(e.displayName)
//->     // Is there an expected sceneButton for the deviceId?
//->     Integer sceneButton = expectedSceneDeviceValue('Rep', deviceId)
//->     // And if so, does it match the eventButton?
//->     if (sceneButton && sceneButton == eventButton) {
//->       // This event can be used to confirm or refute the target scene.
//->       if (e.value == 'on') {
//->         // Scene compliance confirmed
//->         logTrace('repeaterHandler', "${deviceId} complies with scene")
//->         room.moDetected.remove(deviceId)
//->       } else if (e.value == 'off') {
//->         // Scene compliance refuted (i.e., Manual Override)
//->         String summary = "${deviceId} button ${eventButton} off, expected on"
//->         logInfo('repeaterHandler', [ 'MANUAL OVERRIDE', summary ])
//->         room.moDetected[deviceId] = summary
//->       } else {
//->         // Error condition
//->         logWarn(
//->           'repeaterHandler',
//->           "Main Repeater (${deviceId}) with unexpected value (${e.value}"
//->         )
//->       }
//->     }
//->   }
//-> }

void modeHandler(Event e) {
  if (room.activeButton == 'AUTOMATIC') {
    // Hubitat Mode changes only apply when the room's button is 'AUTOMATIC'.
    if (e.name == 'mode') {
      // Let pbsgButtonOnCallback() handle activeButton == 'AUTOMATIC'!
      logTrace('modeHandler', 'Calling pbsgButtonOnCallback()')

      logError('modeHandler', 'TBD FIND PBSG AND SET ACTIVE TO "AUTOMATIC"')
      pbsg.activeButton = 'AUTOMATIC'
      pbsgButtonOnCallback(pbsg)

    } else {
      logWarn('modeHandler', ['UNEXPECTED EVENT', eventDetails(e)])
    }
  } else {
    logTrace(
      'modeHandler', [
        'Ignored: Mode Change',
        "room.activeButton: ${b(room.activeButton)}",
        "room.activeScene: ${b(room.activeScene)}"
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
      room.activeMotionSensors = cleanStrings([*room.activeMotionSensors, e.displayName])
      room_ActivateScene(room)
    } else if (e.value == 'inactive') {
      logInfo('motionSensorHandler', "${e.displayName} is inactive")
      room.activeMotionSensors?.removeAll { activeSensor -> activeSensor == e.displayName }
      room_ActivateScene(room)
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
      room.brightLuxSensors = cleanStrings([*room.brightLuxSensors, e.displayName])
    } else {
      // Remove sensor from list of sensors with sufficient light.
      room.brightLuxSensors?.removeAll { brightSensor -> brightSensor == e.displayName }
    }
    logTrace('luxSensorHandler', [
      "sensor name: ${e.displayName}",
      "illuminance level: ${e.value}",
      "sufficient light threshold: ${settings.lowLuxThreshold}",
      "sufficient light: ${room.brightLuxSensors}"
    ])
    room_ActivateScene(room)
  }
}

void picoHandler(Event e) {
  Integer changePercentage = 10
  if (e.isStateChange == true) {
    switch (e.name) {
      case 'pushed':
        // Check to see if the received button is assigned to a scene.
        String scene = room.picoButtonToTargetScene?.getAt(e.deviceId.toString())
                                                   ?.getAt(e.value.toString())
        if (scene) {
          logInfo(
            'picoHandler',
            "w/ ${e.deviceId}-${e.value} toggling ${scene}"
          )
          toggleButton(scene)
        } else if (e.value == '2') {  // Default "Raise" behavior
          logTrace('picoHandler', "Raising ${settings.zWaveDevices}")
          settings.zWaveDevices.each { d ->
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
          logTrace('picoHandler', "Lowering ${settings.zWaveDevices}")
          settings.zWaveDevices.each { d ->
            d.setLevel(Math.max(
              (d.currentValue('level') as Integer) - changePercentage,
              0
            ))
          }
        } else {
          logTrace(
            'picoHandler',
            "${room.name} picoHandler() w/ ${e.deviceId}-${e.value} no action."
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
    "${room.name} initialize() of '${room.name}'. "
      + 'Subscribing to modeHandler.'
  )
  room.brightLuxSensors = []
  populateStateScenesAssignValues()
  room.moDetected = [:] // clears Manual Override
  settings.zWaveDevices.each { device -> unsubscribe(device) }
  //-> subscribeToRepHandler()
  subscribeToModeHandler()
  subscribeToMotionSensorHandler()
  subscribeToLuxSensorHandler()
  // ACTIVATION
  //   - If AUTOMATIC is already active in the PBSG, pbsgButtonOnCallback()
  //     will not be called.
  //   - It is better to include a redundant call here than to miss
  //     proper room activation on initialization.
  Map pbsg = pbsgStore_Retrieve(room.name)
  if (pbsg) {
    pbsg_ActivateButton(pbsg, 'AUTOMATIC')

      logError('modeHandler', 'TBD FIND PBSG AND SET ACTIVE TO "AUTOMATIC"')
      pbsg.activeButton = 'AUTOMATIC'
      pbsgButtonOnCallback(pbsg)

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
  room.scenes = room.scenes?.collectEntries { k, v ->
    assembleScenes.contains(k) ? [k, v] : [:]
  }
}

//-> Map getDeviceValues(String scene) {
//->   String keyPrefix = "scene^${scene}^"
//->   List<DevW> allowedDevices = allowedDevices = [ *settings.get('zWaveDevices'), *settings.get('repeaters')]
//->   List<String> allowedDeviceIds = allowedDevices.collect { device -> getDeviceId(device) }
//->   Map results = ['Rep': [:], 'Ind': [:]]
//->   settings.findAll { k1, v1 ->
//->     k1.startsWith(keyPrefix)
//->   }.each { k2, v2 ->
//->     ArrayList typeAndId = k2.substring(keyPrefix.size()).tokenize('^')
//->     if (typeAndId[0] == 'RA2') {
//->       logWarn('getDeviceValues', "Removing stale RA2 setting? >${k2}<")
//->       app.removeSetting(k2)
//->     } else if (allowedDeviceIds.contains(typeAndId[1]) == false) {
//->       logWarn('getDeviceValues', "Removing stale Device setting? >${k2}<")
//->     } else {
//->       // Enforce min/max constraints on Independent (Ind) device value.
//->       if (typeAndId[1] == 'Ind') {
//->         if (v2 > 100) {
//->           results.put(typeAndId[0], [*:results.get(typeAndId[0]), (typeAndId[1]):100])
//->         } else if (v2 < 0) {
//->           results.put(typeAndId[0], [*:results.get(typeAndId[0]), (typeAndId[1]):0])
//->         }
//->       } else {
//->         results.put(typeAndId[0], [*:results.get(typeAndId[0]), (typeAndId[1]):v2])
//->       }
//->     }
//->   }
//->   return results
//-> }

//-> void populateStateScenesAssignValues() {
//->   room.scenes = room.scenes.collectEntries { scene, map ->
//->     Map m = getDeviceValues(scene)
//->     if (m) { [scene, m] }
//->   }
//-> }

//-> void configureRoomScene() {
//->   // DESIGN NOTES
//->   //   There are three steps to populate "room.scenes" map.
//->   //   (1) adjustStateScenesKeys() creates a per-scene key and
//->   //       sets the value to [].
//->   //   (2) This method populates Settings keys "scene^SCENENAME^Ind|Rep^DNI".
//->   //       with Integer values:
//->   //         - 'light level' for Independent devices (Ind)
//->   //         - 'virtual button number' for RA2 Repeaters (RA2)
//->   //   (3) populateStateScenesAssignValues() harvests the settings from
//->   //       Step 2 to complete the "room.scenes" map.
//->   // VIRTUAL TABLE
//->   //   Hubitat page display logic simulates table cells.
//->   //     - Full-sized displays (computer monitors) are 12 cells wide.
//->   //     - Phone-sized displays are 4 cells wide.
//->   //   To ensure that each scene starts on a new row, this method adds
//->   //   empty cells (modulo 12) to ensure each scene begins in column 1.
//->   if (room.scenes) {
//->     ArrayList currSettingsKeys = []
//->     room.scenes?.sort().each { sceneName, ignoredValue ->
//->       // Ignore the current componentList. Rebuilt it from scratch.
//->       Integer tableCol = 3
//->       paragraph("<br/><b>${sceneName} →</b>", width: 2)
//->       settings.zWaveDevices?.each { d ->
//->         String inputName = "scene^${sceneName}^Ind^${getDeviceId(d)}"
//->         currSettingsKeys += inputName
//->         tableCol += 3
//->         input(
//->           name: inputName,
//->           type: 'number',
//->           title: "${b(d.label)}<br/>Level 0..100",
//->           width: 3,
//->           submitOnChange: true,
//->           required: false,
//->           multiple: false,
//->           defaultValue: 0
//->         )
//->       }
//->       settings.ra2Repeaters?.each { d ->
//->         String inputName = "scene^${sceneName}^Rep^${getDeviceId(d)}"
//->         currSettingsKeys += inputName
//->         tableCol += 3
//->         input(
//->           name: inputName,
//->           type: 'number',
//->           title: "${b(d.label)}<br/>Button #",
//->           width: 3,
//->           submitOnChange: true,
//->           required: false,
//->           multiple: false,
//->           defaultValue: 0
//->         )
//->       }
//->       // Pad the remainder of the table row with empty cells.
//->       while (tableCol++ % 12) {
//->         paragraph('', width: 1)
//->       }
//->     }
//->   }
//-> }

Map roomScenesPage() {
  // The parent application (Whole House Automation) assigns a unique label
  // to each WHA Rooms instance. Capture app.label as room.name.
  return dynamicPage(
    name: 'roomScenesPage',
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
    room.name = app.label  // WHA creates App w/ Label == Room Name
    room.brightLuxSensors = []
    section {
      idMotionSensors()
      idLuxSensors()
      if (settings.luxSensors) { idLowLightThreshold() }
      nameCustomScene()
      //*********************************************************************
      if (room.scenes) {
        ArrayList scenes = room.scenes.collect { k, v -> return k }
        Map rsPbsgConfig = [
          'name': room.name,
          'allButtons': [ *scenes, 'AUTOMATIC' ] - [ 'OFF' ],
          'defaultButton': 'AUTOMATIC'
          //--DROP-FEATURE-> 'initialActiveButton': null
        ]
        pbsg_Initialize(rsPbsgConfig)
      } else {
        paragraph "Creation of the room's PBSG is pending identification of room scenes"
      }
      //*********************************************************************
      adjustStateScenesKeys()
//->      configureRoomScene()
    }
  }
}

// - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

/* groovylint-disable-next-line MethodSize */
void room_initAllRooms() {
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'brightLuxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Den',
    'scenes': [
      'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 48 ], 'Ind': [ 'Den - Fireplace (02)': 100 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 46 ], 'Ind': [ 'Den - Fireplace (02)': 100 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 45 ], 'Ind': [ 'Den - Fireplace (02)': 100 ] ],
      'Chill': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 41 ], 'Ind': [ 'Den - Fireplace (02)': 100 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 47 ], 'Ind': [ 'Den - Fireplace (02)': 0 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 42 ], 'Ind': [ 'Den - Fireplace (02)': 0 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 45 ], 'Ind': [ 'Den - Fireplace (02)': 0 ] ]
    ]
  ])
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': [ 'true' ],
    'activeScene': 'Day',
    'brightLuxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'DenLamp',
    'scenes': [
      'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 15 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 16 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 15 ] ],
      'Chill': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 11 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 17 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 12 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 15 ]
      ]
    ]
  ])
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'brightLuxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Uplighting (Guest)',
    'scenes': [
      'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 58 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 56 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 55 ] ],
      'Chill': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 51 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 57 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 52 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 55 ]
      ]
    ]
  ])
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': [],
    'activeScene': 'Day',
    'brightLuxSensors': [],
    'currScene': 'OFF',
    'moDetected': [],
    'name': 'Hers',
    'scenes': [
      'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 91 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 93 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 94 ] ],
      'Chill': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 91 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 93 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 93 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 93 ] ],
      'OFF': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 95 ]
      ]
    ]
  ])
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': [],
    'activeScene': 'Day',
    'brightLuxSensors': [],
    'currScene': 'OFF',
    'moDetected': [],
    'name': 'His',
    'scenes': [
      'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 31 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 33 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 34 ] ],
      'Chill': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 31 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 33 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 33 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 33 ] ],
      'OFF': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 35 ]
      ]
    ]
  ])
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'brightLuxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Kitchen',
    'scenes': [
      'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 28 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 26 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 25 ] ],
      'Chill': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 21 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 27 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 22 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 25 ] ]
    ]
  ])
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'brightLuxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Lanai',
    'scenes': [
      'TV': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 8, 'RA2 Repeater 2 (ra2-83)': 78 ] ],
      'Party': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 6, 'RA2 Repeater 2 (ra2-83)': 76 ] ],
      'Night': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 5, 'RA2 Repeater 2 (ra2-83)': 74 ] ],
      'Chill': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 1, 'RA2 Repeater 2 (ra2-83)': 71 ] ],
      'Supplement': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 7, 'RA2 Repeater 2 (ra2-83)': 77 ] ],
      'Cleaning': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 2, 'RA2 Repeater 2 (ra2-83)': 75 ] ],
      'Day': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 3, 'RA2 Repeater 2 (ra2-83)': 75 ] ]
    ]
  ])
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': [],
    'activeScene': 'Day',
    'brightLuxSensors': [],
    'currScene': 'OFF',
    'moDetected': [],
    'name': 'Laundry',
    'roomOccupied': [],
    'scenes': [
      'TV': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 38 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 33 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 34 ] ],
      'Chill': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 31 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 33 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 33 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 33 ] ],
      'OFF': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 35 ] ]
    ]
  ])
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': [
      'LHS Bath - Sensor (ra2-72)'
    ],
    'activeScene': 'Day',
    'brightLuxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'LhsBath',
    'scenes': [
      'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 68 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 66 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 64 ] ],
      'Chill': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 61 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 67 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 62 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 63 ] ],
      'OFF': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 65 ] ]
    ]
  ])
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'brightLuxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'LhsBdrm',
    'scenes': [
      'TV': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 48 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 46 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 45 ] ],
      'Chill': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 41 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 41 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 42 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 45 ] ]
    ]
  ])
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'brightLuxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Main',
    'roomOccupied': true,
    'scenes': [
      'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 88 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 86 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 84 ] ],
      'Chill': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 81 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 87 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 82 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 85 ] ]
    ]
  ])
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'brightLuxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'picoButtonToTargetScene': [
      '6846': [ '1': 'Party', '3': 'AUTOMATIC', '5': 'Off'
      ]
    ],
    'name': 'Office',
    'roomOccupied': true,
    'scenes': [
      'TV': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 58 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 56 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 55 ] ],
      'Chill': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 51 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 57 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 52 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 55 ] ]
    ]
  ])
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'brightLuxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Uplighting (Primary)',
    'roomOccupied': true,
    'scenes': [
      'TV': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 28 ], 'Ind': [ 'Primary Floor Lamp (0B)': 0 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 26 ], 'Ind': [ 'Primary Floor Lamp (0B)': 50 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 25 ], 'Ind': [ 'Primary Floor Lamp (0B)': 0 ] ],
      'Chill': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 21 ], 'Ind': [ 'Primary Floor Lamp (0B)': 20 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 27 ], 'Ind': [ 'Primary Floor Lamp (0B)': 100 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 22 ], 'Ind': [ 'Primary Floor Lamp (0B)': 100 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 25 ], 'Ind': [ 'Primary Floor Lamp (0B)': 0 ] ]
    ]
  ])
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'brightLuxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'PrimBath',
    'scenes': [
      'TV': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 18 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 16 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 15 ] ],
      'Chill': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 11 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 17 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 12 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 15 ] ]
    ]
  ])
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': [],
    'activeScene': 'Day',
    'brightLuxSensors': [],
    'currScene': 'OFF',
    'moDetected': [],
    'name': 'RhsBath',
    'scenes': [
      'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 78 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 76 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 74 ] ],
      'Chill': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 71 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 77 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 72 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 73 ] ],
      'OFF': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 75 ] ]
    ]
  ])
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'brightLuxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'RhsBdrm',
    'scenes': [
      'TV': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 15 ] ],
      'Party': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 14 ] ],
      'Night': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 12 ] ],
      'Chill': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 9 ] ],
      'Supplement': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 9 ] ],
      'Cleaning': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 10 ] ],
      'Day': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 11 ] ]
    ]
  ])
  roomStore_Save([
    'activeButton': 'AUTOMATIC',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'brightLuxSensors': [
      'Control - Rear MultiSensor',
      'Control - Front MultiSensor'
    ],
    'currScene': 'OFF',
    'moDetected': [],
    'name': 'Yard',
    'scenes': [
      'TV': [
        'Rep': [ 'RA2 Repeater 2 (ra2-83)': 64 ],
        'Ind': [ 'Uplighting (Front)': 100, 'Uplighting (Primary)': 100, 'Uplighting (Guest)': 100 ]
      ],
      'Party': [
        'Rep': [ 'RA2 Repeater 2 (ra2-83)': 61 ],
        'Ind': [ 'Uplighting (Front)': 100, 'Uplighting (Primary)': 100, 'Uplighting (Guest)': 100 ]
      ],
      'Night': [
        'Rep': [ 'RA2 Repeater 2 (ra2-83)': 64 ],
        'Ind': [ 'Uplighting (Front)': 100, 'Uplighting (Primary)': 100, 'Uplighting (Guest)': 100 ]
      ],
      'Chill': [
        'Rep': [ 'RA2 Repeater 2 (ra2-83)': 61 ],
        'Ind': [ 'Uplighting (Front)': 100, 'Uplighting (Guest)': 100, 'Uplighting (Primary)': 100 ]
      ],
      'Supplement': [
        'Rep': [ 'RA2 Repeater 2 (ra2-83)': 65 ],
        'Ind': [ 'Uplighting (Front)': 0, 'Uplighting (Guest)': 0, 'Uplighting (Primary)': 0 ]
      ],
      'Cleaning': [
        'Rep': [ 'RA2 Repeater 2 (ra2-83)': 65 ],
        'Ind': [ 'Uplighting (Front)': 0, 'Uplighting (Guest)': 0, 'Uplighting (Primary)': 0 ]
      ],
      'Day': [
        'Rep': [ 'RA2 Repeater 2 (ra2-83)': 65 ],
        'Ind': [ 'Uplighting (Front)': 0, 'Uplighting (Primary)': 0, 'Uplighting (Guest)': 0 ]
      ],
      'OFF': [
        'Rep': [ 'RA2 Repeater 2 (ra2-83)': 65 ],
        'Ind': [ 'Uplighting (Front)': 0, 'Uplighting (Guest)': 0, 'Uplighting (Primary)': 0 ]
      ]
    ]
  ])
}
