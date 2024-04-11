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

void roomStore_Save(Map roomMap) {
  // Add/update a "room" psuedo-class instance.
  Map roomStore = state.roomStore ?: [:]
  roomStore."${roomMap.name}" = room
  state.roomStore = roomStore
}

ArrayList roomStore_ListRooms() {
  Map roomStore = state.roomStore ?: [:]
  ArrayList roomNames2 =  roomStore.collect{ k, v -> k }.sort()
  return roomNames2
}

// The psuedo-class "roomStore" facilitates concurrent storage of multiple
// "room" psuedo-class instances. A "room" psuedo-class instance (its Map)
// extends a "pbsg" psuedo-class instance (its Map). Thus, a "room" Map
// can be supplied where a "pbsg" Map is expected (but, not the converse).

void room_ActivateScene(Map roomMap) {
  String expectedScene = (
    roomMap.activeMotionSensors == false || roomMap.luxSensors == true
  ) ? 'Off' : roomMap.activeScene
  if (roomMap.currScene != expectedScene) {
    logInfo('activateScene', "${roomMap.currScene} -> ${expectedScene}")
    roomMap.currScene = expectedScene
    // Decode and process the scene's per-device actions
    Map actions = roomMap.scenes.get(roomMap.currScene)
    actions.get('Rep').each { repeaterId, button ->
      logInfo('activateScene', "Pushing repeater (${repeaterId}) button (${button})")
      pushRa2RepButton(repeaterId, button)
    }
    actions.get('Ind').each { deviceLabel, value ->
      setDeviceLevel(deviceLabel, value)
    }
  }
}

void pbsg_ButtonOnCallback(Map pbsg) {
  // Pbsg/Dashboard/Alexa actions override Manual Overrides.
  // Scene activation enforces room occupancy.
  if (!pbsg) {
    logError(
      'pbsg_ButtonOnCallback',
      'A null pbsg argument was received, assuming pbsg.activeButton is "Automatic"'
    )
  }
  roomMap.activeButton = pbsg?.activeButton ?: 'Automatic'
  logInfo(
    'pbsg_ButtonOnCallback',
    "Button ${b(button)} -> roomMap.activeButton: ${b(roomMap.activeButton)}")
  roomMap.moDetected = [:] // clears Manual Override
  // UPDATE THE TARGET SCENE
  // Upstream Pbsg/Dashboard/Alexa actions should clear Manual Overrides
  if (
    (roomMap.activeButton == 'Automatic' && !roomMap.activeScene)
    || (roomMap.activeButton == 'Automatic' && !roomMap.moDetected)
  ) {
    // Ensure that targetScene is per the latest Hubitat mode.
    // groovylint-disable-next-line UnnecessaryGetter
    roomMap.activeScene = getLocation().getMode() //settings["modeToScene^${mode}"]
  } else {
    roomMap.activeScene = roomMap.activeButton
  }
  room_ActivateScene(room)
}

Boolean isDeviceType(String devTypeCandidate) {
  return ['Rep', 'Ind'].contains(devTypeCandidate)
}

// =====
// ===== MODE HANDLER
// =====

void subscribeToModeHandler() {
  logInfo(
    'subscribeToModeHandler',
    "${roomMap.name} subscribing to location 'mode'"
  )
  subscribe(location, 'mode', modeHandler)
}

void modeHandler(Event e) {
  // Relay Hubitat mode changes to rooms.
  if (e.name == 'mode') {
    // FOR EACH ROOM --->
    if (roomMap.activeButton != 'Automatic') { roomMap.activeButton = e.value }
    logTrace('modeHandler', 'Calling pbsg_ButtonOnCallback()')
    logError('modeHandler', 'TBD FIND PBSG AND SET ACTIVE TO "Automatic"')
    pbsg.activeButton = 'Automatic'
    pbsg_ButtonOnCallback(pbsg)
  } else {
    logWarn('modeHandler', ['UNEXPECTED EVENT', eventDetails(e)])
  }
  if (roomMap.activeButton == 'Automatic') {
    // Hubitat Mode changes only apply when the room's button is 'Automatic'.
    if (e.name == 'mode') {
      // Let pbsg_ButtonOnCallback() handle activeButton == 'Automatic'!
      logTrace('modeHandler', 'Calling pbsg_ButtonOnCallback()')
      logError('modeHandler', 'TBD FIND PBSG AND SET ACTIVE TO "Automatic"')
      pbsg.activeButton = 'Automatic'
      pbsg_ButtonOnCallback(pbsg)
    } else {
      logWarn('modeHandler', ['UNEXPECTED EVENT', eventDetails(e)])
    }
  } else {
    logTrace(
      'modeHandler', [
        'Ignored: Mode Change',
        "roomMap.activeButton: ${b(roomMap.activeButton)}",
        "roomMap.activeScene: ${b(roomMap.activeScene)}"
      ]
    )
  }
}

void room_ModeChange(Map roomMap, String newMode) {
  // Hubitat Mode changes only when the scene is 'Automatic'.
  if (roomMap.activeButton == 'Automatic') {
    pbsg_ActivateButton(Map roomMap, String newMode, DevW device = null)
    if (e.name == 'mode') {
      // Let pbsg_ButtonOnCallback() handle activeButton == 'Automatic'!
      logTrace('modeHandler', 'Calling pbsg_ButtonOnCallback()')
      logError('modeHandler', 'TBD FIND PBSG AND SET ACTIVE TO "Automatic"')
      pbsg.activeButton = 'Automatic'
      pbsg_ButtonOnCallback(room)
    } else {
      logWarn('modeHandler', ['UNEXPECTED EVENT', eventDetails(e)])
    }
  } else {
    logTrace(
      'modeHandler', [
        'Ignored: Mode Change',
        "roomMap.activeButton: ${b(roomMap.activeButton)}",
        "roomMap.activeScene: ${b(roomMap.activeScene)}"
      ]
    )
  }
}

// =====
// ===== MOTION SENSOR HANDLER
// =====

void idMotionSensors() {
  input(
    name: 'motionSensors',
    title: [
      heading3('Identify Room Motion Sensors'),
      bullet2('The special scene OFF is Automatically added'),
      bullet2('OFF is invoked when the room is unoccupied')
    ].join('<br/>'),
    type: 'device.LutronMotionSensor',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void subscribeToMotionSensorHandler() { // RETAIN AT ROOM SCOPE
  if (settings.motionSensors) {
    roomMap.activeMotionSensors = []
    settings.motionSensors.each { d ->
      logInfo(
        'initialize',
        "${roomMap.name} subscribing to Motion Sensor ${deviceInfo(d)}"
      )
      subscribe(d, motionSensorHandler, ['filterEvents': true])
      if (d.latestState('motion').value == 'active') {
        roomMap.activeMotionSensors = cleanStrings([*roomMap.activeMotionSensors, d.displayName])
        room_ActivateScene(room)
      } else {
        roomMap.activeMotionSensors?.removeAll { activeSensor -> activeSensor == d.displayName }
        room_ActivateScene(room)
      }
    }
  } else {
    roomMap.activeMotionSensors = true
  }
}

void motionSensorHandler(Event e) {
  // It IS POSSIBLE to have multiple motion sensors per roomMap.
  logDebug('motionSensorHandler', eventDetails(e))
  if (e.name == 'motion') {
    if (e.value == 'active') {
      logInfo('motionSensorHandler', "${e.displayName} is active")
      roomMap.activeMotionSensors = cleanStrings([*roomMap.activeMotionSensors, e.displayName])
      room_ActivateScene(room)
    } else if (e.value == 'inactive') {
      logInfo('motionSensorHandler', "${e.displayName} is inactive")
      roomMap.activeMotionSensors?.removeAll { activeSensor -> activeSensor == e.displayName }
      room_ActivateScene(room)
    } else {
      logWarn('motionSensorHandler', "Unexpected event value (${e.value})")
    }
  }
}

// =====
// ===== LUX SENSOR HANDLER
// =====

void idLuxSensors() {
  input(
    name: 'luxSensors',
    title: [
      heading3('Identify Room Lux Sensors'),
      bullet2('The special scene OFF is Automatically added'),
      bullet2('OFF is invoked when no Lux Sensor is above threshold')
    ].join('<br/>'),
    type: 'capability.illuminanceMeasurement',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void subscribeToLuxSensorHandler() {
  if (settings.luxSensors) {
    roomMap.luxSensors = []
    settings.luxSensors.each { d ->
      logInfo(
        'subscribeToLuxSensorHandler',
        "${roomMap.name} subscribing to Lux Sensor ${deviceInfo(d)}"
      )
      subscribe(d, luxSensorHandler, ['filterEvents': true])
    }
  } else {
    roomMap.luxSensors = [ ]
  }
}

void luxSensorHandler(Event e) {
  // It IS POSSIBLE to have multiple lux sensors impacting a roomMap. The lux
  // sensor(s) change values frequently. The activateScene() method is only
  // invoked if the aggregate light level changes materially (i.e., from
  // no sensor detecting sufficient light to one or more sensors detecting
  // sufficient light).
  if (e.name == 'illuminance') {
    if (e.value.toInteger() >= settings.lowLuxThreshold) {
      // Add sensor to list of sensors with sufficient light.
      roomMap.luxSensors = cleanStrings([*roomMap.luxSensors, e.displayName])
    } else {
      // Remove sensor from list of sensors with sufficient light.
      roomMap.luxSensors?.removeAll { brightSensor -> brightSensor == e.displayName }
    }
    logTrace('luxSensorHandler', [
      "sensor name: ${e.displayName}",
      "illuminance level: ${e.value}",
      "sufficient light threshold: ${settings.lowLuxThreshold}",
      "sufficient light: ${roomMap.luxSensors}"
    ])
    room_ActivateScene(room)
  }
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

// =====
// ===== INDEPENDENT DEVICE HANDLER
// =====

void indDeviceHandler(Event e) {
  // Devices send various events (e.g., switch, level, pushed, released).
  // Isolate the events that confirm|refute roomMap.activeScene.
  Integer reported
  // Only select events are considered for MANUAL OVERRIDE detection.
  if (e.name == 'level') {
    reported = safeParseInt(e.value)
  } else if (e.name == 'switch' && e.value == 'off') {
    reported = 0
  }
  String deviceLabel = e.displayName
  Integer expected = roomMap.scenes?."${roomMap.activeScene}"?."${devType}"?."${deviceLabel}"
  if (reported == expected) {
    roomMap.moDetected = roomMap.moDetected.collect { key, value ->
      if (key != deviceLabel) { [key, value] }
    }
  } else {
    roomMap.moDetected.put(deviceLabel, "${reported} (${expected})")
  }
}

// =====
// ===== PICO BUTTON HANDLER
// =====

void toggleButton(String button) {
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

void picoHandler(Event e) {
  Integer changePercentage = 10
  if (e.isStateChange == true) {
    switch (e.name) {
      case 'pushed':
        // Check to see if the received button is assigned to a scene.
        String scene = roomMap.picoButtonToTargetScene?.getAt(e.deviceId.toString())
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
            "${roomMap.name} picoHandler() w/ ${e.deviceId}-${e.value} no action."
          )
        }
        break
    }
  }
}

void initialize() {
  logInfo(
    'initialize',
    "${roomMap.name} initialize() of '${roomMap.name}'. "
      + 'Subscribing to modeHandler.'
  )
  roomMap.luxSensors = []
  populateStateScenesAssignValues()
  roomMap.moDetected = [:] // clears Manual Override
  settings.zWaveDevices.each { device -> unsubscribe(device) }
  subscribeToModeHandler()
  subscribeToMotionSensorHandler()
  subscribeToLuxSensorHandler()
  // ACTIVATION
  //   - If Automatic is already active in the PBSG, pbsg_ButtonOnCallback()
  //     will not be called.
  //   - It is better to include a redundant call here than to miss
  //     proper room activation on initialization.
  Map pbsg = pbsgStore_Retrieve(roomMap.name)
  if (pbsg) {
    pbsg_ActivateButton(pbsg, 'Automatic')
    logError('modeHandler', 'TBD FIND PBSG AND SET ACTIVE TO "Automatic"')
    pbsg.activeButton = 'Automatic'
    pbsg_ButtonOnCallback(pbsg)
  } else {
    logWarn(
      'initialize',
      'The RSPbsg is pending additional configuration data.'
    )
  }
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
    assembleScenes << 'Off'
  }
  // The following is a work around after issues with map.retainAll {}
  roomMap.scenes = roomMap.scenes?.collectEntries { k, v ->
    assembleScenes.contains(k) ? [k, v] : [:]
  }
}

Map roomScenesPage() {
  // The parent application (Whole House Automation) assigns a unique label
  // to each WHA Rooms instance. Capture app.label as roomMap.name.
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
    roomMap.name = app.label  // WHA creates App w/ Label == Room Name
    roomMap.luxSensors = []
    section {
      idMotionSensors()
      idLuxSensors()
      if (settings.luxSensors) { idLowLightThreshold() }
      nameCustomScene()
      //*********************************************************************
      if (roomMap.scenes) {
        ArrayList scenes = roomMap.scenes.collect { k, v -> return k }
        Map rsPbsgConfig = [
          'name': roomMap.name,
          'allButtons': [ *scenes, 'Automatic' ] - [ 'Off' ],
          'defaultButton': 'Automatic'
        ]
        pbsg_Initialize(rsPbsgConfig)
      } else {
        paragraph "Creation of the room's PBSG is pending identification of room scenes"
      }
      //*********************************************************************
      adjustStateScenesKeys()
    }
  }
}
*/

// groovylint-disable-next-line MethodSize
Map room_initAllRooms() {
  roomStore_Save([   // Den
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
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
  roomStore_Save([   // DenLamp
    'activeButton': 'Automatic',
    'activeMotionSensors': [ 'true' ],
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'DenLamp',
    'scenes': [
      'Chill': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 11 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 12 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 15 ],
      'Night': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 15 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 16 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 17 ] ],
      'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 15 ] ]
      ]
    ]
  ])
  roomStore_Save([   // Guest
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Guest',
    'scenes': [
      'Chill': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 51 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 52 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 55 ],
      'Night': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 55 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 56 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 57 ] ],
      'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 58 ] ]
      ]
    ]
  ])
  roomStore_Save([   // Hers
    'activeButton': 'Automatic',
    'activeMotionSensors': [],
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Off',
    'moDetected': [],
    'name': 'Hers',
    'scenes': [
      'Chill': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 91 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 93 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 93 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 94 ] ],
      'Off': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 95 ],
      'Party': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 93 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 93 ] ],
      'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 91 ] ]
      ]
    ]
  ])
  roomStore_Save([   // His
    'activeButton': 'Automatic',
    'activeMotionSensors': [],
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Off',
    'moDetected': [],
    'name': 'His',
    'scenes': [
      'Chill': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 31 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 33 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 33 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 34 ] ],
      'Off': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 35 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 33 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 33 ] ],
      'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 31 ] ]
    ]
  ])
  roomStore_Save([   // Kitchen
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Kitchen',
    'scenes': [
      'Chill': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 21 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 22 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 25 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 25 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 26 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 27 ] ],
      'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 28 ] ]
    ]
  ])
  roomStore_Save([   // Lanai
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Lanai',
    'scenes': [
      'Chill': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 1, 'RA2 Repeater 2 (ra2-83)': 71 ] ],
      'Cleaning': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 5, 'RA2 Repeater 2 (ra2-83)': 75 ] ],
      'Day': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 5, 'RA2 Repeater 2 (ra2-83)': 75 ] ],
      'Night': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 5, 'RA2 Repeater 2 (ra2-83)': 74 ] ],
      'Off': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 5, 'RA2 Repeater 2 (ra2-83)': '??' ] ],
      'Party': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 3, 'RA2 Repeater 2 (ra2-83)': 76 ] ],
      'Play': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 4, 'RA2 Repeater 2 (ra2-83)': 76 ] ],
      'Supplement': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 1, 'RA2 Repeater 2 (ra2-83)': 77 ] ],
      'TV': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 8, 'RA2 Repeater 2 (ra2-83)': 78 ] ]
    ]
  ])
  roomStore_Save([   // Laundry
    'activeButton': 'Automatic',
    'activeMotionSensors': [],
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Off',
    'moDetected': [],
    'name': 'Laundry',
    'roomOccupied': [],
    'scenes': [
      'Chill': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 31 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 33 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 33 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 34 ] ],
      'Off': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 35 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 33 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 33 ] ],
      'TV': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 38 ] ]
    ]
  ])
  roomStore_Save([   // LhsBath
    'activeButton': 'Automatic',
    'activeMotionSensors': [
      'LHS Bath - Sensor (ra2-72)'
    ],
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'LhsBath',
    'scenes': [
      'Chill': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 61 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 62 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 63 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 64 ] ],
      'Off': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 65 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 66 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 67 ] ],
      'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 68 ] ]
    ]
  ])
  roomStore_Save([   // LhsBdrm
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'LhsBdrm',
    'scenes': [
      'Chill': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 41 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 42 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 45 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 45 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 46 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 41 ] ],
      'TV': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 48 ] ]
    ]
  ])
  roomStore_Save([   // Main
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Main',
    'roomOccupied': true,
    'scenes': [
      'Chill': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 81 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 82 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 85 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 84 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 86 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 87 ] ],
      'TV': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 88 ] ]
    ]
  ])
  roomStore_Save([   // Office
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'picoButtonToTargetScene': [
      '6846': [ '1': 'Party', '3': 'Automatic', '5': 'Off'
      ]
    ],
    'name': 'Office',
    'roomOccupied': true,
    'scenes': [
      'Chill': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 51 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 52 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 55 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 55 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 56 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 57 ] ],
      'TV': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 58 ] ]
    ]
  ])
  roomStore_Save([   // Primary
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Primary',
    'roomOccupied': true,
    'scenes': [
      'Chill': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 21 ], 'Ind': [ 'Primary Floor Lamp (0B)': 20 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 22 ], 'Ind': [ 'Primary Floor Lamp (0B)': 100 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 25 ], 'Ind': [ 'Primary Floor Lamp (0B)': 0 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 25 ], 'Ind': [ 'Primary Floor Lamp (0B)': 0 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 26 ], 'Ind': [ 'Primary Floor Lamp (0B)': 50 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 27 ], 'Ind': [ 'Primary Floor Lamp (0B)': 100 ] ],
      'TV': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 28 ], 'Ind': [ 'Primary Floor Lamp (0B)': 0 ] ]
    ]
  ])
  roomStore_Save([   // PrimBath
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'PrimBath',
    'scenes': [
      'Chill': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 11 ] ],
      'Cleaning': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 12 ] ],
      'Day': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 15 ] ],
      'Night': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 15 ] ],
      'Party': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 16 ] ],
      'Supplement': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 17 ] ],
      'TV': [ 'Rep': [ 'RA2 Repeater 2 (ra2-83)': 18 ] ]
    ]
  ])
  roomStore_Save([   // RhsBath
    'activeButton': 'Automatic',
    'activeMotionSensors': [],
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Off',
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
      'Off': [ 'Rep': [ 'RA2 Repeater 1 (ra2-1)': 75 ] ]
    ]
  ])
  roomStore_Save([   // RhsBdrm
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'RhsBdrm',
    'scenes': [
      'Chill': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 9 ] ],
      'Cleaning': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 10 ] ],
      'Day': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 13 ] ],
      'Night': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 13 ] ],
      'Off': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 13 ] ],
      'Party': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 14 ] ],
      'Supplement': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 9 ] ],
      'TV': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 15 ] ]
    ]
  ])
  roomStore_Save([   // WHA
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [
      'Control - Rear MultiSensor': 400,
      'Control - Front MultiSensor': 400
    ],
    'currScene': 'Off',
    'moDetected': [],
    'name': 'WHA',
    'scenes': [
      'Chill': [
        'Rep': [ 'RA2 Repeater 2 (ra2-1)': 1, 'Caséta Repeater (pro2-1)': 11 ],
        'Ind': [ 'Uplighting (Front)': 100, 'Uplighting (Guest)': 100, 'Uplighting (Primary)': 100 ]
      ],
      'Cleaning': [
        'Rep': [ 'RA2 Repeater 2 (ra2-1)': 2, 'Caséta Repeater (pro2-1)': 16 ],
        'Ind': [ 'Uplighting (Front)': 0, 'Uplighting (Guest)': 0, 'Uplighting (Primary)': 0 ]
      ],
      'Day': [
        'Rep': [ 'RA2 Repeater 2 (ra2-1)': 5, 'Caséta Repeater (pro2-1)': '??' ],
        'Ind': [ 'Uplighting (Front)': 0, 'Uplighting (Primary)': 0, 'Uplighting (Guest)': 17 ]
      ],
      'Night': [
        'Rep': [ 'RA2 Repeater 2 (ra2-1)': 4, 'Caséta Repeater (pro2-1)': 7 ],
        'Ind': [ 'Uplighting (Front)': 100, 'Uplighting (Primary)': 100, 'Uplighting (Guest)': 100 ]
      ],
      'Off': [
        'Rep': [ 'RA2 Repeater 2 (ra2-1)': 5, 'Caséta Repeater (pro2-1)': 17 ],
        'Ind': [ 'Uplighting (Front)': 0, 'Uplighting (Guest)': 0, 'Uplighting (Primary)': 0 ]
      ],
      'Party': [
        'Rep': [ 'RA2 Repeater 2 (ra2-1)': 6, 'Caséta Repeater (pro2-1)': 3 ],
        'Ind': [ 'Uplighting (Front)': 100, 'Uplighting (Primary)': 100, 'Uplighting (Guest)': 100 ]
      ],
      'Supplement': [
        'Rep': [ 'RA2 Repeater 2 (ra2-1)': 7, 'Caséta Repeater (pro2-1)': 12 ],
        'Ind': [ 'Uplighting (Front)': 0, 'Uplighting (Guest)': 0, 'Uplighting (Primary)': 0 ]
      ],
      'TV': [
        'Rep': [ 'RA2 Repeater 2 (ra2-1)': 8, 'Caséta Repeater (pro2-1)': 2 ],
        'Ind': [ 'Uplighting (Front)': 100, 'Uplighting (Primary)': 100, 'Uplighting (Guest)': 100 ]
      ]
    ]
  ])
  roomStore_Save([   // Yard
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [
      'Control - Rear MultiSensor': 40,
      'Control - Front MultiSensor': 40
    ],
    'currScene': 'Off',
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
      'Off': [
        'Rep': [ 'RA2 Repeater 2 (ra2-83)': 65 ],
        'Ind': [ 'Uplighting (Front)': 0, 'Uplighting (Guest)': 0, 'Uplighting (Primary)': 0 ]
      ]
    ]
  ])
  return state.roomStore as Map
}
