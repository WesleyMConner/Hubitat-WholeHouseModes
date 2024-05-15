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
  name: 'lRoomV2',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Room Implementation',
  category: 'general purpose'
)

  // --------------------------------------------------------------------------------
  // Leverage the lRa2IRParser
  //   - The parser input is an RA2 Integration Report (provided as one long string)
  //
  //   - The parser produces a Map with the following keys:
  //      rA2Devices - A multi-row String that can be pasted into the
  //                   "Lutron Integrator App" to create a new RA2 integration
  //                   instance. The App expects triplets in the form
  //                   "code,id,name", where:
  //                      code: Informs the devices 'Type'
  //                        id: Specifies a Hubitat 'DeviceName'
  //                            (see also the 'deviceID' field in Device events)
  //                      name: Specifies a Hubitat 'DeviceLabel'
  //                            (see also the 'name' field in Device events )
  //           kpads - RA2 keypad details
  //        ra2Rooms - RA2 rooms (which ARE NOT the same as Hubitat rooms)
  //        circuits - RA2 circuits that can be switched or dimmed
  //       timeclock - RA2 timeclock data
  //           green - RA2 green mode data
  //
  //   - WHA leverages the Map produced by the lRa2IRParser to produce:
  //   buttonToScene - A nested Map in the form:
  //                   repDevId -> buttonNumber -> room -> scene
  //   sceneToButton - A nested Map in the form:
  //                   room -> scene -> repDevId -> buttonNumber

  // --------------------------------------------------------------------------------

// The "room" psuedo-class extends a "pbsg" psuedo-class instance.
//   - The psuedo-class "pbsgStore" is used to store "room" instance Maps.
//   - A "room" instance Map can be supplied where a "pbsg" instance Map
//     is expected. The converse IS NOT true.

String room_State(String roomName) {
  String result = 'NOT FOUND'
  if (atomicState."${roomName}") {
    result = atomicState."${roomName}".moDetected ? 'MANUAL_OVERRIDE ' : ''
    result += pbsg_State(atomicState."${roomName}")
  } else {
    logError('room_State', "Cannot find room '${roomName}'")
  }
  return result
}

ArrayList room_getScenes(String roomName) {
  ArrayList results = []
  if (atomicState."${roomName}") {
    results = atomicState."${roomName}"?.scenes.collect { k, v -> k }
  } else {
    logError('room_getScenes', "Cannot find room '${roomName}'")
  }
  return results
}

void room_ActivateScene(String roomName) {
  Map room = atomicState."${roomName}"
  if (room) {
    String expectedScene = (
      room.activeMotionSensors == false
      || room.lux?.lowCounter < room.lux?.belowMax
    ) ? 'Off' : room.activeScene
    if (room.currScene != expectedScene) {
      logInfo('room_ActivateScene', "${room.currScene} -> ${expectedScene}")
      atomicState.updateMapValue(roomName, 'currScene', expectedScene)
      // Decode and process the scene's per-device actions
      //-> Map actions = roomMap.scenes.get(roomMap.currScene)
      atomicState."${roomName}".actions.'Rep'.each { repeaterId, button ->
        logInfo('room_ActivateScene', "Pushing ${repeaterId} button ${button}")
        pushLutronRepButton(repeaterId, button)
      }
      atomicState."${roomName}".actions.'Ind'.each { deviceLabel, value ->
        logInfo('room_ActivateScene', "Setting ${deviceLabel} to ${value}")
        setDeviceLevel(deviceLabel, value)
      }
    }
  } else {
    logError('room_ActivateScene', "Cannot find room '${roomName}'")
  }
}

void pbsg_ButtonOnCallback(String pbsgName) {
  Map pbsgMap = atomicState."${pbsgName}"
  if (!pbsgMap) {
    logError('pbsg_ButtonOnCallback', 'Encountered a null pbsgMap')
  }
  switch (pbsgMap?.instType) {
    case 'pbsg':
      logInfo('pbsg_ButtonOnCallback', "Processing pbsg ${pbsg_State(pbsgName)}")
      // DO NOT FORGET -> pbsgStore_Save(pbsgMap)
      /*
      if (roomOverrides) {
        rooms.each{ room ->
          if (roomOverrides.contains(room.name) == false) {
            // Push the appropriate ROOM scene repeater button(s)
            // And, set appropriate ROOM scene device levels
          }
        }
      } else {
        // Push the appropriate WHA scene reepater button(s)
        // And, set appropriate WHA scene device levels
      }
      */
      break
    case 'room':
      logInfo('pbsg_ButtonOnCallback', "Processing room ${pbsg_State(pbsgName)}")
      // DO NOT FORGET -> pbsgStore_Save(pbsgMap)
      /*
      if (newScene == currModeScene) {
        // Minus roomManualOverride
      } else {
        // Plus roomManualOverride
      }
      // Push the appropriate ROOM scene repeater button(s)
      // And, set appropriate ROOM scene device levels
      */
      break
    default:
      if (pbsgMap) {
        logError('pbsg_ButtonOnCallback', "Unexpected instType (${pbsgMap.instType})")
      } else {
        logError('pbsg_ButtonOnCallback', 'Missing pbsgMap')
      }
  }
}

//-> Boolean isDeviceType(String devTypeCandidate) {
//->   return ['Rep', 'Ind'].contains(devTypeCandidate)
//-> }

void room_ModeChange(String roomName, String newMode, DevW device = null) {
  Map roomMap = atomicState."${roomName}"
  // Hubitat Mode changes only when the scene is 'Automatic'.
  if (roomMap.activeButton == 'Automatic') {
    pbsg_ActivateButton(roomName, newMode, device)
    if (e.name == 'mode') {
      // Let pbsg_ButtonOnCallback(...) handle activeButton == 'Automatic'!
      logInfo('modeHandler', 'Calling pbsg_ButtonOnCallback(...)')
      logInfo('modeHandler', 'TBD FIND PBSG AND SET ACTIVE TO "Automatic"')
      pbsg.activeButton = 'Automatic'
      atomicState."${roomMap.name}" = roomMap // Persist pbsg instance change
      //-> pbsgStore_Save(roomMap)
      pbsg_ButtonOnCallback(roomName)
    } else {
      logWarn('modeHandler', ['UNEXPECTED EVENT', eventDetails(e)])
    }
  } else {
    logInfo(
      'modeHandler', [
        'Ignored: Mode Change',
        "roomMap.activeButton: ${b(roomMap.activeButton)}",
        "roomMap.activeScene: ${b(roomMap.activeScene)}"
      ]
    )
  }
}

// =====
// ===== PICO BUTTON HANDLER
// =====

//-> void toggleButton(String button) {
//->   // Toggle the button's device and let activate and deactivate react.
//->   // This will result in delivery of the scene change via a callback.
//->   String dni = "${roomMap.name}_${button}"
//->   DevW device = getChildDevice(dni)
//->   if (switchState(device) == 'on') {
//->     device.off()
//->   } else {
//->     devive.on()
//->   }
//-> }

/*
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
          logInfo('picoHandler', "Raising ${settings.zWaveDevices}")
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
          logInfo('picoHandler', "Lowering ${settings.zWaveDevices}")
          settings.zWaveDevices.each { d ->
            d.setLevel(Math.max(
              (d.currentValue('level') as Integer) - changePercentage,
              0
            ))
          }
        } else {
          logInfo(
            'picoHandler',
            "${roomMap.name} picoHandler() w/ ${e.deviceId}-${e.value} no action."
          )
        }
        break
    }
  }
}
*/

// groovylint-disable-next-line MethodSize
Map room_restoreOriginalState() {
  atomicState.Den = [
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Den',
    'scenes': [
      'Chill': [ 'Rep': [ 'Rep1': 41 ], 'Ind': [ 'Den - Fireplace (02)': 100 ] ],
      'Cleaning': [ 'Rep': [ 'Rep1': 42 ], 'Ind': [ 'Den - Fireplace (02)': 0 ] ],
      'Day': [ 'Rep': [ 'Rep1': 45 ], 'Ind': [ 'Den - Fireplace (02)': 0 ] ],
      'Night': [ 'Rep': [ 'Rep1': 45 ], 'Ind': [ 'Den - Fireplace (02)': 100 ] ],
      'Party': [ 'Rep': [ 'Rep1': 46 ], 'Ind': [ 'Den - Fireplace (02)': 100 ] ],
      'Supplement': [ 'Rep': [ 'Rep1': 47 ], 'Ind': [ 'Den - Fireplace (02)': 0 ] ],
      'TV': [ 'Rep': [ 'Rep1': 48 ], 'Ind': [ 'Den - Fireplace (02)': 100 ] ]
    ]
  ]
  atomicState.DenLamp = [
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'DenLamp',
    'scenes': [
      'Chill': [ 'Rep': [ 'Rep1': 11 ] ],
      'Cleaning': [ 'Rep': [ 'Rep1': 12 ] ],
      'Day': [ 'Rep': [ 'Rep1': 15 ],
      'Night': [ 'Rep': [ 'Rep1': 15 ] ],
      'Party': [ 'Rep': [ 'Rep1': 16 ] ],
      'Supplement': [ 'Rep': [ 'Rep1': 17 ] ],
      'TV': [ 'Rep': [ 'Rep1': 15 ] ]
      ]
    ]
  ]
  atomicState.Guest = [
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Guest',
    'scenes': [
      'Chill': [ 'Rep': [ 'Rep1': 51 ] ],
      'Cleaning': [ 'Rep': [ 'Rep1': 52 ] ],
      'Day': [ 'Rep': [ 'Rep1': 55 ],
      'Night': [ 'Rep': [ 'Rep1': 55 ] ],
      'Party': [ 'Rep': [ 'Rep1': 56 ] ],
      'Supplement': [ 'Rep': [ 'Rep1': 57 ] ],
      'TV': [ 'Rep': [ 'Rep1': 58 ] ]
      ]
    ]
  ]
  atomicState.Hers = [
    'activeButton': 'Automatic',
    'activeMotionSensors': [],
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Off',
    'moDetected': [],
    'name': 'Hers',
    'scenes': [
      'Chill': [ 'Rep': [ 'Rep1': 91 ] ],
      'Cleaning': [ 'Rep': [ 'Rep1': 93 ] ],
      'Day': [ 'Rep': [ 'Rep1': 93 ] ],
      'Night': [ 'Rep': [ 'Rep1': 94 ] ],
      'Off': [ 'Rep': [ 'Rep1': 95 ],
      'Party': [ 'Rep': [ 'Rep1': 93 ] ],
      'Supplement': [ 'Rep': [ 'Rep1': 93 ] ],
      'TV': [ 'Rep': [ 'Rep1': 91 ] ]
      ]
    ]
  ]
  atomicState.His = [
    'activeButton': 'Automatic',
    'activeMotionSensors': [],
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Off',
    'moDetected': [],
    'name': 'His',
    'scenes': [
      'Chill': [ 'Rep': [ 'Rep1': 31 ] ],
      'Cleaning': [ 'Rep': [ 'Rep1': 33 ] ],
      'Day': [ 'Rep': [ 'Rep1': 33 ] ],
      'Night': [ 'Rep': [ 'Rep1': 34 ] ],
      'Off': [ 'Rep': [ 'Rep1': 35 ] ],
      'Party': [ 'Rep': [ 'Rep1': 33 ] ],
      'Supplement': [ 'Rep': [ 'Rep1': 33 ] ],
      'TV': [ 'Rep': [ 'Rep1': 31 ] ]
    ]
  ]
  atomicState.Kitchen = [
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Kitchen',
    'scenes': [
      'Chill': [ 'Rep': [ 'Rep1': 21 ] ],
      'Cleaning': [ 'Rep': [ 'Rep1': 22 ] ],
      'Day': [ 'Rep': [ 'Rep1': 25 ] ],
      'Night': [ 'Rep': [ 'Rep1': 25 ] ],
      'Party': [ 'Rep': [ 'Rep1': 26 ] ],
      'Supplement': [ 'Rep': [ 'Rep1': 27 ] ],
      'TV': [ 'Rep': [ 'Rep1': 28 ] ]
    ]
  ]
  atomicState.Lanai = [
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Lanai',
    'scenes': [
      'Chill': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 1, 'Rep2': 71 ] ],
      'Cleaning': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 5, 'Rep2': 75 ] ],
      'Day': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 5, 'Rep2': 75 ] ],
      'Night': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 5, 'Rep2': 74 ] ],
'Off': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 5, 'Rep2': 75 ] ],
      'Party': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 3, 'Rep2': 76 ] ],
'Play': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 4, 'Rep2': 76 ] ],
      'Supplement': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 1, 'Rep2': 77 ] ],
      'TV': [ 'Rep': [ 'Caséta Repeater (pro2-1)': 8, 'Rep2': 78 ] ]
    ]
  ]
  atomicState.Laundry = [
    'activeButton': 'Automatic',
    'activeMotionSensors': [],
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Off',
    'moDetected': [],
    'name': 'Laundry',
    'scenes': [
      'Chill': [ 'Rep': [ 'Rep2': 31 ] ],
      'Cleaning': [ 'Rep': [ 'Rep2': 33 ] ],
      'Day': [ 'Rep': [ 'Rep2': 33 ] ],
      'Night': [ 'Rep': [ 'Rep2': 34 ] ],
      'Off': [ 'Rep': [ 'Rep2': 35 ] ],
      'Party': [ 'Rep': [ 'Rep2': 33 ] ],
      'Supplement': [ 'Rep': [ 'Rep2': 33 ] ],
      'TV': [ 'Rep': [ 'Rep2': 38 ] ]
    ]
  ]
  atomicState.LhsBath = [
    'activeButton': 'Automatic',
    'activeMotionSensors': [],
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'LhsBath',
    'scenes': [
      'Chill': [ 'Rep': [ 'Rep1': 61 ] ],
      'Cleaning': [ 'Rep': [ 'Rep1': 62 ] ],
      'Day': [ 'Rep': [ 'Rep1': 63 ] ],
      'Night': [ 'Rep': [ 'Rep1': 64 ] ],
      'Off': [ 'Rep': [ 'Rep1': 65 ] ],
      'Party': [ 'Rep': [ 'Rep1': 66 ] ],
      'Supplement': [ 'Rep': [ 'Rep1': 67 ] ],
      'TV': [ 'Rep': [ 'Rep1': 68 ] ]
    ]
  ]
  atomicState.LhsBdrm = [
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'LhsBdrm',
    'scenes': [
      'Chill': [ 'Rep': [ 'Rep2': 41 ] ],
      'Cleaning': [ 'Rep': [ 'Rep2': 42 ] ],
      'Day': [ 'Rep': [ 'Rep2': 45 ] ],
      'Night': [ 'Rep': [ 'Rep2': 45 ] ],
      'Party': [ 'Rep': [ 'Rep2': 46 ] ],
      'Supplement': [ 'Rep': [ 'Rep2': 41 ] ],
      'TV': [ 'Rep': [ 'Rep2': 48 ] ]
    ]
  ]
  atomicState.Main = [
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Main',
    'scenes': [
      'Chill': [ 'Rep': [ 'Rep1': 81 ] ],
      'Cleaning': [ 'Rep': [ 'Rep1': 82 ] ],
      'Day': [ 'Rep': [ 'Rep1': 85 ] ],
      'Night': [ 'Rep': [ 'Rep1': 84 ] ],
      'Party': [ 'Rep': [ 'Rep1': 86 ] ],
      'Supplement': [ 'Rep': [ 'Rep1': 87 ] ],
      'TV': [ 'Rep': [ 'Rep1': 88 ] ]
    ]
  ]
  atomicState.Office = [
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
    'scenes': [
      'Chill': [ 'Rep': [ 'Rep2': 51 ] ],
      'Cleaning': [ 'Rep': [ 'Rep2': 52 ] ],
      'Day': [ 'Rep': [ 'Rep2': 55 ] ],
      'Night': [ 'Rep': [ 'Rep2': 55 ] ],
      'Party': [ 'Rep': [ 'Rep2': 56 ] ],
      'Supplement': [ 'Rep': [ 'Rep2': 57 ] ],
      'TV': [ 'Rep': [ 'Rep2': 58 ] ]
    ]
  ]
  atomicState.Primary = [
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'Primary',
    'scenes': [
      'Chill': [ 'Rep': [ 'Rep2': 21 ], 'Ind': [ 'Primary Floor Lamp (0B)': 20 ] ],
      'Cleaning': [ 'Rep': [ 'Rep2': 22 ], 'Ind': [ 'Primary Floor Lamp (0B)': 100 ] ],
      'Day': [ 'Rep': [ 'Rep2': 25 ], 'Ind': [ 'Primary Floor Lamp (0B)': 0 ] ],
      'Night': [ 'Rep': [ 'Rep2': 25 ], 'Ind': [ 'Primary Floor Lamp (0B)': 0 ] ],
      'Party': [ 'Rep': [ 'Rep2': 26 ], 'Ind': [ 'Primary Floor Lamp (0B)': 50 ] ],
      'Supplement': [ 'Rep': [ 'Rep2': 27 ], 'Ind': [ 'Primary Floor Lamp (0B)': 100 ] ],
      'TV': [ 'Rep': [ 'Rep2': 28 ], 'Ind': [ 'Primary Floor Lamp (0B)': 0 ] ]
    ]
  ]
  atomicState.PrimBath = [
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Day',
    'moDetected': [],
    'name': 'PrimBath',
    'scenes': [
      'Chill': [ 'Rep': [ 'Rep2': 11 ] ],
      'Cleaning': [ 'Rep': [ 'Rep2': 12 ] ],
      'Day': [ 'Rep': [ 'Rep2': 15 ] ],
      'Night': [ 'Rep': [ 'Rep2': 15 ] ],
      'Party': [ 'Rep': [ 'Rep2': 16 ] ],
      'Supplement': [ 'Rep': [ 'Rep2': 17 ] ],
      'TV': [ 'Rep': [ 'Rep2': 18 ] ]
    ]
  ]
  atomicState.RhsBath = [
    'activeButton': 'Automatic',
    'activeMotionSensors': [],
    'activeScene': 'Day',
    'luxSensors': [],
    'currScene': 'Off',
    'moDetected': [],
    'name': 'RhsBath',
    'scenes': [
      'Chill': [ 'Rep': [ 'Rep1': 71 ] ],
      'Cleaning': [ 'Rep': [ 'Rep1': 72 ] ],
      'Day': [ 'Rep': [ 'Rep1': 73 ] ],
      'Night': [ 'Rep': [ 'Rep1': 74 ] ],
      'Off': [ 'Rep': [ 'Rep1': 75 ] ],
      'Party': [ 'Rep': [ 'Rep1': 76 ] ],
      'Supplement': [ 'Rep': [ 'Rep1': 77 ] ],
      'TV': [ 'Rep': [ 'Rep1': 78 ] ]
    ]
  ]
  atomicState.RhsBdrm = [
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
  ]
  atomicState.WHA = [
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'luxLowCounter': 0,
    'lux': [
      'sensors': [
        'Control - Rear MultiSensor': 400,
        'Control - Front MultiSensor': 400
      ],
      'lowCounter': 0,
      'lowMin': 0,
      'lowMax': 5
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
        'Rep': [ 'RA2 Repeater 2 (ra2-1)': 5, 'Caséta Repeater (pro2-1)': 5 ],
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
  ]
  atomicState.Yard = [
    'activeButton': 'Automatic',
    'activeMotionSensors': true,
    'activeScene': 'Day',
    'lux': [
      'sensors': [
        'Control - Rear MultiSensor': 40,
        'Control - Front MultiSensor': 40
      ],
      'lowCounter': 0,
      'lowMin': 0,
      'lowMax': 5
    ],
    'currScene': 'Off',
    'moDetected': [],
    'name': 'Yard',
    'scenes': [
      'Chill': [
        'Rep': [ 'Rep2': 61 ],
        'Ind': [ 'Uplighting (Front)': 100, 'Uplighting (Guest)': 100, 'Uplighting (Primary)': 100 ]
      ],
      'Cleaning': [
        'Rep': [ 'Rep2': 65 ],
        'Ind': [ 'Uplighting (Front)': 0, 'Uplighting (Guest)': 0, 'Uplighting (Primary)': 0 ]
      ],
      'Day': [
        'Rep': [ 'Rep2': 65 ],
        'Ind': [ 'Uplighting (Front)': 0, 'Uplighting (Primary)': 0, 'Uplighting (Guest)': 0 ]
      ],
      'Night': [
        'Rep': [ 'Rep2': 64 ],
        'Ind': [ 'Uplighting (Front)': 100, 'Uplighting (Primary)': 100, 'Uplighting (Guest)': 100 ]
      ],
      'Off': [
        'Rep': [ 'Rep2': 65 ],
        'Ind': [ 'Uplighting (Front)': 0, 'Uplighting (Guest)': 0, 'Uplighting (Primary)': 0 ]
      ],
      'Party': [
        'Rep': [ 'Rep2': 61 ],
        'Ind': [ 'Uplighting (Front)': 100, 'Uplighting (Primary)': 100, 'Uplighting (Guest)': 100 ]
      ],
      'Supplement': [
        'Rep': [ 'Rep2': 65 ],
        'Ind': [ 'Uplighting (Front)': 0, 'Uplighting (Guest)': 0, 'Uplighting (Primary)': 0 ]
      ],
      'TV': [
        'Rep': [ 'Rep2': 64 ],
        'Ind': [ 'Uplighting (Front)': 100, 'Uplighting (Primary)': 100, 'Uplighting (Guest)': 100 ]
      ]
    ]
  ]
  return atomicState.pbsgs as Map
}
