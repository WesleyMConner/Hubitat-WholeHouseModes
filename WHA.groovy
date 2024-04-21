
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

// The Groovy Linter generates false positives on Hubitat #include !!!
#include wesmc.lHExt
#include wesmc.lHUI
#include wesmc.lPbsgv2
#include wesmc.lRoom

definition (
  name: 'WHA',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Whole House Automation using Modes, RA2 and Room Overrides',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',
  singleInstance: true
)

preferences {
  page(name: 'WhaPage')
}

void logModeAndPerRoomState() {
  ArrayList results = ['']
  modePbsg = pbsgStore_Retrieve('mode')
  results += modePbsg ?pbsg_State(modePbsg) : 'Null modePbsg'
  pbsgStore = state.pbsgStore ?: [:]
  pbsgStore.each { roomName, roomMap ->
    if (roomMap.instType == 'room') { results += room_State(roomMap) }
  }
  logInfo('logModeAndPerRoomState', results)
}

void AllAuto () {
//--TBD->  roomStore_ListRooms().each { roomName ->
//--TBD->    // TBD
//--TBD->    //   - pbsg_ActivateButton(pbsg, 'Automatic')
//--TBD->    //   - pbsg_ButtonOnCallback(pbsg)
//--TBD->    InstAppW roomApp = app.getChildAppByLabel(roomName)
//--TBD->    String manualOverrideSwitchDNI = "${roomApp.label}_Automatic"
//--TBD->    logInfo('AllAuto', "Turning on ${b(manualOverrideSwitchDNI)}")
//--TBD->    roomApp.getRSPbsg().turnOnSwitch(manualOverrideSwitchDNI)
//--TBD->  }
}

//====
//==== REPEATER METHODS
//====

void idLutronRepeaters() {
  input(
    name: 'lutronRepeaters',
    title: [
      heading3('Identify Lutron RA2 Repeaters'),
    ].join('<br/>'),
    type: 'device.LutronKeypad',  // Odly, isolates RA2 and Pro2 Repeaters
    submitOnChange: true,
    required: false,
    multiple: true,
    offerAll: true
  )
}

void pushLutronRepButton(String repeaterId, Long buttonNumber) {
  settings.lutronRepeaters.each { repeater ->
    if(repeater.deviceLabel == repeaterId) {
      repeater.push(buttonNumber)
    }
  }
}

void beginProcessingRepeaterEvents() {
  settings?.lutronRepeaters.each{ device ->
    logInfo('beginProcessingRepeaterEvents', "Subcribing to events for ${device}")
    subscribe(device, repeaterHandler, ['filterEvents': true])
  }
}

void repeaterHandler(Event e) {
  logInfo('repeaterHandler', eventDetails(e))
}

//====
//==== MOTION SENSOR METHODS
//====

void idMotionSensors() {
  // Only tested with RA2 motion sensors (e.g., OCR2B)
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
    multiple: true,
    offerAll: true
  )
}

void motionSensorHandler(Event e) {
  // Reminder
  //   - One room can have more than one motion sensor.
  //
  // Sample Display Names
  //   Laundry-MotionSensor
  //   RhsBath-MotionSensor
  //   LhsBath-MotionSensor
  //   Hers-MotionSensor
  //   His-MotionSensor
  //             value  active
  if (e.name == 'motion') {
    ArrayList roomNameAndDeviceLabel = e.displayName.tokenize('-')
    String roomName = roomNameAndDeviceLabel[0]
    String deviceLabel = roomNameAndDeviceLabel[1]
    Map roomMap = pbsgStore_Retrieve(roomName)
    if (roomMap) {
      if (e.value == 'active') {
        logInfo('motionSensorHandler', "${e.displayName} is active")
        roomMap.activeMotionSensors = cleanStrings([*roomMap.activeMotionSensors, e.displayName])
        room_ActivateScene(roomMap)
        pbsgStore_Save(roomMap)
      } else if (e.value == 'inactive') {
        logInfo('motionSensorHandler', "${e.displayName} is inactive")
        roomMap.activeMotionSensors?.removeAll { it == e.displayName }
        room_ActivateScene(roomMap)
        pbsgStore_Save(roomMap)
      } else {
        logWarn('motionSensorHandler', "Unexpected event value (${e.value})")
      }
    }
    pbsgStore_Save(roomMap)
  }
}

void beginProcessingMotionSensorEvents() {
  settings?.motionSensors.each{ device ->
    logInfo('beginProcessingMotionSensorEvents', "Subcribing to events for ${device}")
    subscribe(device, motionSensorHandler, ['filterEvents': true])
  }
}

//====
//==== LUX SENSOR METHODS
//====

void idLuxSensors() {
  // Only tested with 'Aeon Multisensor 6'
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
    multiple: true,
    offerAll: true
  )
}

void luxSensorHandler(Event e) {
  if (e.name == 'illuminance') {
    Integer luxLevel = e.value.toInteger()
    Map pbsgStore = state.pbsgStore ?: [:]
    pbsgStore.each{ roomName, roomMap ->
      if (roomMap) {
        roomMap?.lux?.sensors.each{ deviceLabel, luxThreshold ->
          if (deviceLabel == e.displayName) {
            if (luxLevel < luxThreshold && roomMap.lux.lowCounter < roomMap.lux.lowMax) {
              // Only increment lux.lowCounter to a maximum value of lux.lowMax
              if (++roomMap.lux.lowCounter == roomMap.lux.lowMax) {
                logInfo(
                  'luxSensorHandler',
                  "${e.displayName} (${luxLevel}), ACTIVATE ${roomName} (${luxThreshold})"
                )
                room_ActivateScene(roomMap)
              }
              pbsgStore_Save(roomMap)
            } else if (luxLevel >= luxThreshold && roomMap.lux.lowCounter > roomMap.lux.lowMin) {
              // Only decrement lux.lowCounter to a minimum value of lux.lowMin
              if (--roomMap.lux.lowCounter == roomMap.lux.lowMin) {
                logInfo(
                  'luxSensorHandler',
                  "${e.displayName} (${luxLevel}), DEACTIVATE ${roomName} (${luxThreshold})"
                )
                room_ActivateScene(roomMap)
              }
              pbsgStore_Save(roomMap)
            }
          }
        }
      } else {
        logError('luxSensorHandler', "No room instance roomMap for '${roomName}'")
      }
    }
  }
}

void beginProcessingLuxSensorEvents() {
  settings?.luxSensors.each{ device ->
    logInfo('beginProcessingLuxSensorEvents', "Subscribing to events for ${device}")
    subscribe(device, luxSensorHandler, ['filterEvents': true])
  }
}

//====
//==== Z-WAVE DEVICE METHODS
//====

// Find devices

//GenericZWaveDimmer
//GenericZWaveOutlet
//GeEnbrightenZWaveSmartSwitch
//offerAll: true

// TBD
/*
void subscribeIndDevToHandler(Map roomMap, Map data) {
  // USAGE:
  //   runIn(1, 'subscribeIndDevToHandler', [data: [device: d]])
  // Independent Devices (especially RA2 and Caséta) are subject to stale
  // Hubitat state data if callbacks occur quickly (within 1/2 second)
  // after a level change. So, briefly unsubscribe/subscribe to avoid
  // this situation.
  logInfo(
    'subscribeIndDevToHandler',
    "${roomMap.name} subscribing ${deviceInfo(data.device)}"
  )
  subscribe(device, indDeviceHandler, ['filterEvents': true])
}
*/

// TBD
/*
void unsubscribeIndDevToHandler(Map roomMap, DevW device) {
  // Independent Devices (especially RA2 and Caséta) are subject to stale
  // Hubitat state data if callbacks occur quickly (within 1/2 second)
  // after a level change. So, briefly unsubscribe/subscribe to avoid
  // this situation.
  logInfo(
    '_unsubscribeToIndDeviceHandler',
    "${roomMap.name} unsubscribing ${deviceInfo(device)}"
  )
  unsubscribe(device)
}
*/

//TBD
/*
void setDeviceLevel(String deviceId, Long level) {
  settings.indDevices.each { device ->
    if (getDeviceId(device) == deviceId) {
      if (device.hasCommand('setLevel')) {
        logInfo('room_ActivateScene', "Setting ${b(deviceId)} to level ${b(level)}")
        // Some devices DO NOT support a level of 100.
        device.setLevel(level == 100 ? 99 : level)
      }
      if (level == 0) {
        logInfo('room_ActivateScene', "Setting ${b(deviceId)} to off")
        device.off()
      } else if (level == 100) {
        logInfo('room_ActivateScene', "Setting ${b(deviceId)} to on")
        device.on()
      }
    }
  }
}
*/

//====
//==== PICOS TRIGGERING HUB ACTIONS
//====
// LhsBdrm-EntryPico
// Office-DeskPico
// LhsBdrm-TablePico
// DenLamp-Pico

// TBD
/*
void picoHandler(Event e) {
  Map roomMap = xxx
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
          logInfo('picoHandler', "Raising ${settings.indDevices}")
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
          logInfo('picoHandler', "Lowering ${settings.indDevices}")
          settings.indDevices.each { d ->
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

//====
//==== OTHER
//====

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
  //---------------------------------------------------------------------------------
  // REMOVE NO LONGER USED SETTINGS AND STATE
  //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
  //   - state.remove('X')
  //   - app.removeSetting('Y')
  //---------------------------------------------------------------------------------
  initialize()
}

void initializeModePbsg() {
  Map modePbsg = [
    name: 'mode',
    allButtons: modeNames(),
    defaultButton: getLocation().getMode()
  ]
  modePbsg = pbsg_CreateInstance(modePbsg, 'pbsg')
  pbsgStore_Save(modePbsg)
  logInfo('initializeModePbsg', pbsg_State(modePbsg))
}

void createRoomStateDataFromScratch() {
  // Create room state data "from scratch" (if and only if required).
  logInfo('createRoomStateDataFromScratch', 'Loading room data maps')
  room_restoreOriginalState()
}

void populatePerRoomPbsgs() {
  Map pbsgStore = (state.pbsgStore ?: [:])
  pbsgStore.each{ k, v ->
    if (v.instType == 'room') {
      v.allButtons = [*room_getScenes(v), 'Automatic']
      v.defaultButton = 'Automatic'
      roomPbsg = pbsg_CreateInstance(v, 'room')
      logInfo('populatePerRoomPbsgs', pbsg_State(roomPbsg))
    }
  }
}

void initialize () {
  initializeModePbsg()
  //createRoomStateDataFromScratch()
  populatePerRoomPbsgs()
  logModeAndPerRoomState()
  beginProcessingRepeaterEvents()
  beginProcessingLuxSensorEvents()
  beginProcessingMotionSensorEvents()
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
    //-> app.removeSetting('..')
    //-> state.remove('..')
    //---------------------------------------------------------------------------------
    state.remove('roomStore')
    app.updateLabel('WHA')
    //state.MODES = getLocation().getModes().collect { it.name }
    //getGlobalVar('defaultMode').value
    section {
      settings.appLogThreshold = 'INFO'
      idLutronRepeaters()
      idMotionSensors()
      idLuxSensors()
    }
  }
}
