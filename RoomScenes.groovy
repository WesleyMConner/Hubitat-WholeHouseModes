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
// The Groovy Linter generates NglParseError on Hubitat #include !!!
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

//---- CORE METHODS (Internal)

InstAppW createRSPbsg() {
  // --------------------------------------------------------------------
  // W A R N I N G
  //   - As of Q1'24, the 'properties' (4th) parameter of addChildApp()
  //     is still not implemented.
  //   - As an alternative, call the 'RSPbsg' method pbsgConfigure() just
  //     after RSPbsg instance creation to provide initial data.
  // --------------------------------------------------------------------
  InstAppW rsPbsg = addChildApp('wesmc', 'RSPbsg', state.RSPBSG_LABEL)
  InstAPpW parent = getParent()
  logInfo('createRSPbsg #55', "parent: ${parent}")
  InstAPpW grandParent = parent.getParent()
  logInfo('createRSPbsg #57', "grandParent: ${grandParent}")
  //ArrayList<String> sceneNames = getParent().getSceneNames(state.ROOM_LABEL)
  sceneNames << 'AUTO'
  rsPbsg.pbsgConfigure(
    sceneNames,     // Create a PBSG button per Room Scene
    'AUTO',         // Default Scene
    'AUTO',         // Current Scene
    settings.pbsgLogThresh ?: 'INFO' // 'INFO' for normal operations
                                     // 'DEBUG' to walk key PBSG methods
                                     // 'TRACE' to include PBSG and VSW state
  )
  return rsPbsg
}

InstAppW getRSPbsg() {
  // Mpbsg depends on Hubitat Mode properties AND NOT local data.
  Boolean createdNewInstance = false
  InstAppW rsPbsg = app.getChildAppByLabel(state.RSPBSG_LABEL)
  if (! rsPbsg) {
    rsPbsg = createRSPbsg()
    createdNewInstance = true
  }
  logInfo('getRSPbsg', [
    '',
    "Created New Instance: ${createdNewInstance}",
    "Id: ${rsPbsg.getId()}",
    "Label: ${rsPbsg.getLabel()}",
    "Install State: ${rsPbsg.getInstallationState()}<"
  ])
  return rsPbsg
}

//void rsConfigure(String scenes) {
//  state.scenes = scenes
//}

String getDeviceId(DevW device) {
  return device?.label ? extractNativeIdFromLabel(device.label) : null
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

//--> void setDeviceLevel (String deviceId, Long level) {
//-->   settings.indDevices.each{ device ->
//-->     if (getDeviceId(device) == deviceId) {
//-->       if (device.hasCommand('setLevel')) {
//-->         logInfo('activateScene', "Setting ${b(deviceId)} to level ${b(level)}")
//-->         // Some devices DO NOT support a level of 100.
//-->         device.setLevel(level == 100 ? 99 : level)
//-->       }
//-->       if (level == 0) {
//-->         logInfo('activateScene', "Setting ${b(deviceId)} to off")
//-->         device.off()
//-->       } else if (level == 100) {
//-->         logInfo('activateScene', "Setting ${b(deviceId)} to on")
//-->         device.on()
//-->       }
//-->     }
//-->   }
//--> }

//--> =====================================================================
//--> USE PARENT TO PUSH REPEATER BUTTONS
//--> TREATMENT OF INDEPENDENT DEVICES IS TEMPORARILY ON HOLD
//--> =====================================================================
//--> void activateScene() {
//-->   String expectedScene = expectedScene()
//-->   if (state.currScene != expectedScene) {
//-->     logInfo('activateScene', "${state.currScene} -> ${expectedScene}")
//-->     state.currScene = expectedScene
//-->     // Decode and process the scene's per-device actions
//-->     Map actions = state.scenes.get(state.currScene)
//-->     actions.get('Rep').each{ repeaterId, button ->
//-->       logInfo('activateScene', "Pushing repeater (${repeaterId}) button (${button})")
//-->       pushRepeaterButton(repeaterId, button)
//-->     }
//-->     actions.get('Ind').each{ deviceId, value ->
//-->       setDeviceLevel(deviceId, value)
//-->     }
//-->   }
//--> }

void updateTargetScene() {
  // Upstream Pbsg/Dashboard/Alexa actions should clear Manual Overrides
  if (
    (state.activeButton == 'AUTO' && !state.activeScene)
    || (state.activeButton == 'AUTO' && !isManualOverride())
  ) {
    // Ensure that targetScene is per the latest Hubitat mode.
    String mode = getLocation().getMode()
    state.activeScene = settings["modeToScene^${mode}"]
  } else {
    state.activeScene = state.activeButton
  }
}

void pbsgButtonOnCallback(String button) {
  // Pbsg/Dashboard/Alexa actions override Manual Overrides.
  // Scene activation enforces room occupancy.
  if (!button) {
    logWarn(
      'pbsgButtonOnCallback (RS)',
      'A null argument was received, using AUTO as a default'
    )
  }
  state.activeButton = button ?: 'AUTO'
  logTrace(
    'pbsgButtonOnCallback (RS)',
    "Button ${b(button)} -> state.activeButton: ${b(state.activeButton)}")
  clearManualOverride()
  updateTargetScene()
  logWarn('pbsgButtonOnCallback (RS)', "ACTIVATION OF '$button' IS PENDING")
  //getParent().activateScene()
}

Boolean isDeviceType(String devTypeCandidate) {
  return ['Rep', 'Ind'].contains(devTypeCandidate)
}

//x--> Integer expectedSceneDeviceValue(String devType, String deviceId) {
//x-->   Integer retVal = null
//x-->   if (isDeviceType(devType)) {
//x-->     retVal = state.scenes?.get(state.activeScene)?.get(devType)?.get(deviceId)
//x-->     //-> logInfo('#328', "${devType}..${deviceId} -> ${retVal}")
//x-->   } else {
//x-->     logError('expectedSceneDeviceValue', "devType (${devType}) not recognized")
//x-->   }
//x-->   return retVal
//x--> }

void createRSPageLinks() {
  InstAppW rsPbsg = getRSPbsg()
  if (rsPbsg) {
    href(
      name: appInfo(rsPbsg),
      width: 2,
      url: "/installedapp/configure/${rsPbsg.id}/RSPbsgPage",
      style: 'internal',
      title: "Edit <b>${appInfo(rsPbsg)}</b>",
      state: null
    )
  } else {
    paragraph "Creation of the RSPbsgHref is pending required data."
  }
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

void modeHandler(Event e) {
  if (state.activeButton == 'AUTO') {
    // Hubitat Mode changes only apply when the room's button is 'AUTO'.
    if (e.name == 'mode') {
      // Let pbsgButtonOnCallback() handle activeButton == 'AUTO'!
      logTrace('modeHandler', 'Calling pbsgButtonOnCallback("AUTO")')
      pbsgButtonOnCallback('AUTO')
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
          getRSPbsg().pbsgToggleButton(scene)
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
  logInfo('initialize', 'At Entry')
  state.brightLuxSensors = []
  //x--> populateStateScenesAssignValues()
  clearManualOverride()
  //-> subscribeToIndDeviceHandlerNoDelay()
  settings.indDevices.each{ device -> unsubscribe(device) }
  //x--> subscribeToKpadHandler()
  subscribeToModeHandler()
  subscribeToMotionSensorHandler()
  subscribeToLuxSensorHandler()
  subscribeToPicoHandler()
  // ACTIVATION
  //   - If AUTO is already active in the PBSG, pbsgButtonOnCallback()
  //     will not be called.
  //   - It is better to include a redundant call here than to miss
  //     proper room activation on initialization.
  InstAppW pbsg = getRSPbsg()
  if (pbsg) {
    pbsg.pbsgActivateButton('AUTO')
    pbsgButtonOnCallback('AUTO')
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
  ArrayList<String> scenes = getParent().getSceneNames(state.ROOM_LABEL)
  if (scenes) {
    ArrayList<String> picoScenes = ['AUTO'] << scenes
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

void stateAndSessionCleanup () {
  /*
  stateRemoveAndLog('sufficientLight')
  stateRemoveAndLog('targetScene')
  settingsRemoveAndLog('ra2Repeaters')
  settingsRemoveAndLog('mainRepeaters')
  settingsRemoveAndLog('hubitatQueryString')
  settingsRemoveAndLog('mainRepeaters')
  settingsRemoveAndLog('ra2Repeaters')
  settingsRemoveAndLog('scene^Chill^RA2^pro2-1')
  settingsRemoveAndLog('scene^Chill^RA2^ra2-1')
  settingsRemoveAndLog('scene^Chill^RA2^ra2-83')
  settingsRemoveAndLog('scene^Cleaning^RA2^pro2-1')
  settingsRemoveAndLog('scene^Cleaning^RA2^ra2-1')
  settingsRemoveAndLog('scene^Cleaning^RA2^ra2-83')
  settingsRemoveAndLog('scene^Cook^Rep^ra2-1')
  settingsRemoveAndLog('scene^Day^RA2^pro2-1')
  settingsRemoveAndLog('scene^Day^RA2^ra2-1')
  settingsRemoveAndLog('scene^Day^RA2^ra2-83')
  settingsRemoveAndLog('scene^Games^RA2^pro2-1')
  settingsRemoveAndLog('scene^Games^RA2^ra2-83')
  settingsRemoveAndLog('scene^Games^Rep^ra2-1')
  settingsRemoveAndLog('scene^Games^Rep^ra2-83')
  settingsRemoveAndLog('scene^INACTIVE^RA2^ra2-1')
  settingsRemoveAndLog('scene^INACTIVE^RA2^ra2-83')
  settingsRemoveAndLog('scene^Night^RA2^pro2-1')
  settingsRemoveAndLog('scene^Night^RA2^ra2-1')
  settingsRemoveAndLog('scene^Night^RA2^ra2-83')
  settingsRemoveAndLog('scene^Off^RA2^ra2-1')
  settingsRemoveAndLog('scene^Off^RA2^ra2-83')
  settingsRemoveAndLog('scene^Party^RA2^pro2-1')
  settingsRemoveAndLog('scene^Party^RA2^ra2-1')
  settingsRemoveAndLog('scene^Party^RA2^ra2-83')
  settingsRemoveAndLog('scene^Supplement^RA2^pro2-1')
  settingsRemoveAndLog('scene^Supplement^RA2^ra2-1')
  settingsRemoveAndLog('scene^Supplement^RA2^ra2-83')
  settingsRemoveAndLog('scene^TV^RA2^pro2-1')
  settingsRemoveAndLog('scene^TV^RA2^ra2-1')
  settingsRemoveAndLog('scene^TV^RA2^ra2-83')
  settingsRemoveAndLog('scene^TV^Rep^ra2-1')
  settingsRemoveAndLog('scene^TV^Rep^ra2-83')
  */
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
    //-> Prefer settingsRemoveAndLog() over app.removeSetting('..')
    //-> Prefer stateRemoveAndLog() over state.remove('..')
    //---------------------------------------------------------------------------------
    stateAndSessionCleanup()
    //---------------------------------------------------------------------------------
    section {
      state.ROOM_LABEL = app.label  // WHA creates App w/ Label == Room Name
      state.RSPBSG_LABEL = "${state.ROOM_LABEL}Pbsg"
      state.scenes = getParent().getSceneNames(state.ROOM_LABEL)
logInfo('RoomScenesPage #625', [
  "ROOM_LABEL: ${state.ROOM_LABEL}",
  "RSPBSG_LABEL: ${state.RSPBSG_LABEL}",
  "scenes: ${state.scenes}"
])
      state.brightLuxSensors = []
      getRSPbsg()
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      solicitLogThreshold('pbsgLogThresh', 'INFO') // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      state.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
      idMotionSensors()
      idLuxSensors()
      if (settings.luxSensors) { idLowLightThreshold() }
      //x--> selectModesAsScenes()
      //x--> nameCustomScenes()
      //x--> adjustStateScenesKeys()
      //x--> idSceneForMode()
      //x--> authSeeTouchKpads()
      //x--> idRoomSceneButtons()
      //x--> wireKpadButtonsToScenes()
      authRoomScenesPicos()
      wirePicoButtonsToScenes()
      //x--> idRa2RepeatersImplementingScenes()
      //x--> idIndDevices()
      /* configureRoomScene() */
      if (! getRSPbsg()) { createRSPbsg() }
      createRSPageLinks()
      paragraph([
        heading1('Debug'),
        *appStateAsBullets(true),
        *appSettingsAsBullets(true)
      ].join('<br/>'))
    }
  }
}
