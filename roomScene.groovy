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
import com.hubitat.app.ChildDeviceWrapper as ChildDevW
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Location as Loc
#include wesmc.libHubUI
#include wesmc.libPbsgBase
#include wesmc.libHubExt

definition (
  parent: 'wesmc:wha',
  name: 'roomScene',
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
  page(name: 'roomScenePage')
}

//----
//---- CORE METHODS
//----   Methods that ARE NOT constrained to any specific execution context.
//----

void removeLegacySettingsAndState () {
  settings.remove('log')
  state.remove('currentScene')
  state.remove('currentSceneRepeaterDeviceId')
  state.remove('currentSceneRepeaterLED')
  state.remove('defaultVswDni')
  state.remove('inspectScene')
  state.remove('LOG_LEVEL1_ERROR')
  state.remove('LOG_LEVEL2_WARN')
  state.remove('LOG_LEVEL3_INFO')
  state.remove('LOG_LEVEL4_DEBUG')
  state.remove('LOG_LEVEL5_TRACE')
  state.remove('logLevel1Error')
  state.remove('logLevel2Warn')
  state.remove('logLevel3Info')
  state.remove('logLevel4Debug')
  state.remove('logLevel5Trace')
  state.remove('ManualOverrideDevice')
  state.remove('PBSGapp')
  state.remove('ROOM_NAME')
  state.remove('roomName')
  state.remove('SCENE_PBSG_APP_NAME')
}

void updateLutronKpadLeds (String currScene) {
  settings.sceneButtons.each{ ledObj ->
    String dni = ledObj.getDeviceNetworkId()
    String sceneTarget = state.kpadButtonDniToTargetScene[dni]
    if (currScene == sceneTarget) {
      Ldebug(
        'updateLutronKpadLeds()',
        "Turning on LED ${dni} for ${state.roomName} scene ${sceneTarget}"
      )
      ledObj.on()
    } else {
      Ldebug(
        'updateLutronKpadLeds()',
        "Turning off LED ${dni} for ${state.roomName} scene ${sceneTarget}"
      )
      ledObj.off()
    }
  }
}

String getSceneForMode (String mode = getLocation().getMode()) {
  String result = settings["modeToScene^${mode}"]
  Ltrace(
    'getSceneForMode()',
    "mode: <b>${mode}</b>, scene: <b>${result}</b>"
  )
  return result
}

InstAppW getRoomScenePbsg () {
  String roomScenePbsgLabel = "pbsg_${app.getLabel()}"
  // PERFORMANCE HIT - Temporarily perform App dup detection.
  Ltrace('getRoomScenePbsg()', 'calling App Dup Detection')
  PruneAppDups([roomScenePbsgLabel], false, app)
  InstAppW roomScenePbsg = getChildAppByLabel(roomScenePbsgLabel)
  if (roomScenePbsg) {
    // PERFORMANCE HIT - Temporarily refresh PBSG configuration.
    roomScenePbsg.configureRoomScenePbsg()
  }
  return roomScenePbsg
}

InstAppW getOrCreateRoomScenePbsg () {
  InstAppW roomScenePbsg = getRoomScenePbsg()
  if (!roomScenePbsg) {
    // PERFORMANCE HIT - Temporarily perform App dup detection.
    String roomScenePbsgLabel = "pbsg_${app.getLabel()}"
    Linfo('getOrCreateRoomScenePbsg()', "addChildApp '${roomScenePbsgLabel}'")
    roomScenePbsg = addChildApp(
      'wesmc',             // See whaModePbsg.groovy App's namespace.
      'roomScenePbsg',     // See whaModePbsg.groovy App's name.
      roomScenePbsgLabel   // Label used to create or get the App instance.
    )
  }
  // No matter how the roomScenePbsg is obtained, assume that either it or
  // its managed VSWs have been manually altered via the Hubitat console.
  // This safety measure comes at the cost of performamce; but, appears to
  // be necessary to address edge cases.
  roomScenePbsg.configRoomScenePbsg()
  return roomScenePbsg
}

String getLogLevel() {
  if (!state.logLevel) Lerror('getLogLevel()', "Missing 'state.logLevel'")
  return state.logLevel
}

List<String> getRoomScenes () {
  List<String> result = state.scenes
  //->result.each{ scene ->
  //->  Lerror('getRoomScenes()', "<b>scene:</b> ${scene}")
  //->}
  return result
}

void activateScene (String scene) {
  // Push Repeater buttons and execute Independent switch/dimmer levels.
  Linfo('activateScene()', "scene: <b>${scene}</b>")
  // Values are expected at ...
  //   state.sceneToRepeater[sceneName][dni]
  //   state.sceneToIndependent[sceneName][dni]
  // THIS APPLICATION ALLOWS A SINGLE LUTRON MAIN REPEATER PER ROOM
  state.sceneToRepeater?.getAt(scene)?.each{ repeaterDni, buttonNumber ->
    Ltrace(
      'activateScene()',
      "${scene}: repeater: ${repeaterDni}, button: ${buttonNumber}"
    )
    // Note: The repeater's Id (not Dni) and button are required to track the scene's
    //       LED on the Main Repeater.
    state.roomSceneRepeaterLED = buttonNumber
    DevW matchedRepeater = settings.mainRepeater?.findAll{ repeater ->
      repeater.getDeviceNetworkId() == repeaterDni
    }?.first() ?: {
      Lerror(
        'activateScene()',
        "no repeater w/ Dni: ${repeaterDni}"
      )
    }
    state.roomSceneRepeaterDeviceId = matchedRepeater.getId()
    matchedRepeater.push(buttonNumber)
  }
  state.sceneToIndependent?.getAt(scene)?.each{ deviceDni, level ->
    Ltrace(
      'activateScene()',
      "${scene}': device: ${deviceDni}, level: ${level}"
    )
    DevW matchedDevice = settings.independentDevices?.findAll{ device ->
      device.getDeviceNetworkId() == deviceDni
    }?.first() ?: {                              // There should be one match by Dni.
      Lerror(
        'activateScene()',
        "no matchedDevice w/ Dni: ${deviceDni}"
      )
    }
    if (matchedDevice.hasCommand('setLevel')) {
      matchedDevice.setLevel(level)
    } else if (level == 0) {
      matchedDevice.off()
    } else if (level == 100) {
      matchedDevice.on()
    }
  }
}

Boolean isRoomSceneLedActive () {
  // Fail true if the current room's scenes DO NOT leverage an
  // "RA2 Shared Scene" (via an RA2 Main Repeater Integration Button).
  Boolean retVal = true
  if (!state.roomSceneRepeaterDeviceId) {
    Ltrace(
      'isRoomSceneLedActive()',
      'No RA2 Shared Scene in use.'
    )
  } else {
    // LEDs will light if (a) they match an explicitly set Room Scene or
    // (b) they match the room's current AUTOMATIC scene. No LEDs should
    // light if the room's scene is MANUAL_OVERRIDE.
    String ledScene = (state.currScenePerVsw == 'AUTOMATIC')
      ? getSceneForMode()
      : state.currScenePerVsw
    Ltrace('isRoomSceneLedActive()', "ledScene: ${ledScene}")
    Map repeaterData = state.sceneToRepeater?.getAt(ledScene)
    if (repeaterData) {
      Ltrace('isRoomSceneLedActive()', "repeaterData: ${repeaterData}")
      settings?.mainRepeater.eachWithIndex{ rep, index ->
        String repDni = rep.deviceNetworkId
        Ltrace('isRoomSceneLedActive()', "repDni: ${repDni}")
        String associatedButton = repeaterData.getAt(repDni)
        Ltrace('isRoomSceneLedActive()', "associatedButton: ${associatedButton}")
        String led = "buttonLed-${associatedButton}"
        Ltrace('isRoomSceneLedActive()', "led: ${led}")
        String ledVal = rep.currentValue(led)
        Ltrace('isRoomSceneLedActive()', "ledVal: ${ledVal}"
        )
        if (ledVal == 'off') {
          retVal = false
          Ltrace('isRoomSceneLedActive()', "retVal: ${retVal}")
          // Note: It is NOT possible to break from a closure.
        }
      }
    }
  }
  Ldebug(
    'isRoomSceneLedActive()',
    "R_${state.roomName} isRoomSceneLedActive() -> ${retVal}"
  )
  return retVal
}

Boolean areRoomSceneDevLevelsCorrect () {
  // Fail true if the current room's scenes DO NOT leverage Independent Devices.
  // Note that device level comparisons are made to state.roomScene (and
  // NOT state.currScenePerVsw). When state.currScenePerVsw == MANUAL_OVERRIDE,
  // state.roomScene will retain the critera required to release the OVERRIDE.
  Boolean retVal = true
  if (!state.sceneToIndependent) {
    Ltrace('areRoomSceneDevLevelsCorrect()', "No Independent Devices.")
  } else {
    Ltrace(
      'areRoomSceneDevLevelsCorrect()',
      "sceneToIndependent: ${state.sceneToIndependent}"
    )
    if (!state.roomScene) {
      if (!state.currScenePerVsw) {
        Lerror(areRoomSceneDevLevelsCorrect, 'state.roomScene IS NOT populated')
      }
    }
    Ltrace(
      'areRoomSceneDevLevelsCorrect()',
      "state.roomScene: ${state.roomScene}"
    )
    String restoreScene = (state.roomScene == 'AUTOMATIC')
      ? getSceneForMode()
      : state.roomScene
    Ltrace('areRoomSceneDevLevelsCorrect()', "restoreScene: ${restoreScene}")
    Map indepDevData = state.sceneToIndependent?.getAt(restoreScene)
    Ltrace('areRoomSceneDevLevelsCorrect()', "indepDevData: ${indepDevData}")
    settings?.independentDevices.each{ dev ->
      Ltrace(
        'areRoomSceneDevLevelsCorrect()',
        "Dni: ${dev.deviceNetworkId}"
      )
      Integer devTargetVal = indepDevData?.getAt(dev.deviceNetworkId)
      if (devTargetVal) {
        Ltrace(
          'areRoomSceneDevLevelsCorrect()',
          "devTargetVal: ${devTargetVal}"
        )
        if (dev.hasAttribute('switch')) {
          String expectedSwitch = (devTargetVal == 0) ? 'off' : 'on'
          String actualSwitch = dev.currentValue('switch')
          Ltrace(
            'areRoomSceneDevLevelsCorrect()',
            "switch: expected: ${expectedSwitch}, actual: ${actualSwitch}"
          )
        }
        if (dev.hasAttribute('level')) {
          Integer actualLevel = dev.currentValue('level')
          Ltrace(
            'areRoomSceneDevLevelsCorrect()',
            "level: expected: ${devTargetVal} actual: ${actualLevel}"
          )
        }
      }
    }
  }
  Ltrace('areRoomSceneDevLevelsCorrect()', "retVal: ${retVal}")
  return retVal
}

Boolean detectManualOverride () {
  // Turning a PBSG switch on/off that's already on/off WILL NOT generate a
  // change event; so, don't worry about suppressing redundant switch state for now.
  if (!isRoomSceneLedActive() || !areRoomSceneDevLevelsCorrect()) {
    InstAppW roomScenePbsg = getRoomScenePbsg()
    if (!state.roomScenePbsgAppId) {
      Lerror('detectManualOverride()', "Cannot find 'state.roomScenePbsgAppId'")
    }
    roomScenePbsg.pbsgTurnOn('MANUAL_OVERRIDE')
  } else {
    roomScenePbsg.pbsgTurnOff('MANUAL_OVERRIDE')
  }
}

void roomSceneInitialize () {
  // Limit creation or update of the Room Scene PBSG instance to this (parent) method.
  getOrCreateRoomScenePbsg()
  Ltrace(
    'roomSceneInitialize()',
    "R_${state.roomName} subscribing to hubitatModeChangeHandler()"
  )
  subscribe(location, "mode", hubitatModeChangeHandler)
  settings.seeTouchKeypads.each{ device ->
    Ltrace(
      'roomSceneInitialize()',
      "R_${state.roomName} subscribing seeTouchKeypad '${DeviceInfo(device)}' to keypadSceneButtonHandler()"
    )
    subscribe(device, keypadSceneButtonHandler, ['filterEvents': true])
  }
  settings.mainRepeater.each{ device ->
    Ltrace(
      'roomSceneInitialize()',
      "R_${state.roomName} subscribing to mainRepeater '${DeviceInfo(device)}' to repeaterLedHandler()"
    )
    subscribe(device, repeaterLedHandler, ['filterEvents': true])
  }
  settings.picos.each{ device ->
    Ltrace(
      'roomSceneInitialize()',
      "R_${state.roomName} subscribing to Pico '${DeviceInfo(device)}' to picoButtonHandler()"
    )
    subscribe(device, picoButtonHandler, ['filterEvents': true])
  }
  settings.motionSensor.each{ device ->
    Ltrace(
      'roomSceneInitialize()',
      "R_${state.roomName} subscribing to motionSensor '${DeviceInfo(device)}' to motionSensorHandler()"
    )
    subscribe(device, motionSensorHandler, ['filterEvents': true])
  }
  settings.independentDevices.each{ device ->
    Ltrace(
      'roomSceneInitialize()',
      "R_${state.roomName} subscribing to independentDevice '${DeviceInfo(device)}' to independentDeviceHandler()"
    )
    subscribe(device, independentDeviceHandler, ['filterEvents': true])
  }
  roomScenePbsg.configRoomScenePbsg()
}

//----
//---- SYSTEM CALLBACKS
//----   Methods specific to this execution context
//----

void installed () {
  Ltrace('installed()', 'calling roomSceneInitialize()')
  roomSceneInitialize()
}

void updated () {
  Ltrace('updated()', 'calling roomSceneInitialize()')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  roomSceneInitialize()
}

void uninstalled () {
  Lwarn('uninstalled()', 'calling RemoveChildApps()')
  RemoveChildApps()
}

//----
//---- EVENT HANDLERS
//----   Methods specific to this execution context
//----

void pbsgVswTurnedOnCallback (String currScene) {
  // If 'state.roomScene' is observed, MANUAL_OVERRIDE is resolved.
  state.roomScene = (currScene == 'MANUAL_OVERRIDE') ? state.roomScene : currScene
  if (state.roomScene == 'MANUAL_OVERRIDE') {
    Lerror('pbsgVswTurnedOnCallback()', 'state.roomScene == MANUAL_OVERRIDE')
  }
  state.currScenePerVsw = currScene
  Ltrace(
    'pbsgVswTurnedOnCallback()',
    [
      '',
      "currPbsgSwitch: ${currPbsgSwitch}",
      "currScene: ${currScene}",
      "inspectScene: ${state.roomScene}"
    ].join('<br/>')
  )
  //-----> updateLutronKpadLeds(currScene)
  switch(currScene) {
    case 'AUTOMATIC':
      String targetScene = getSceneForMode()
      Linfo('pbsgVswTurnedOnCallback()', "AUTOMATIC -> ${targetScene}")
      if (!settings?.motionSensor) activateScene(targetScene)
      break
    default:
      Ltrace('pbsgVswTurnedOnCallback()', "processing '${currScene}'")
      if (!settings?.motionSensor) activateScene(currScene)
  }
}

void repeaterLedHandler (Event e) {
  // - The field e.deviceId arrives as a number and must be cast toString().
  // - This subscription processes Main Repeater events, which is applicable
  //   to Rooms that leverage an RA2 Main Repeater (virtual) Integration
  //   Button and corresponding (virtual) LED. Work is delegated to
  //   detectManualOverride()
  if (
       (e.deviceId.toString() == state.roomSceneRepeaterDeviceId)
       && (e.name == "buttonLed-${state.roomSceneRepeaterLED}")
       && (e.isStateChange == true)
  ) {
    Ldebug('repeaterLedHandler()', 'calling detectManualOverride()')
    detectManualOverride()
  }
}

void independentDeviceHandler (Event e) {
  // - This subscription processes Independent Device events. Work is delegated
  //   to detectManualOverride.
  Ldebug('independentDeviceHandler()', 'calling detectManualOverride()')
  detectManualOverride()
}

void hubitatModeChangeHandler (Event e) {
  // Abstract
  //   When the Hubitat mode changes AND state.currScenePerVsw == 'AUTOMATIC':
  //     - Identify the appropriate scene for the Hubitat mode.
  //     - Turn on the roomScenePbsg VSW for that scene.
  //   State changes are deferred to pbsgVswTurnedOnCallback().
  Ltrace(
    'hubitatModeChangeHandler()',
    [
      '',
      "<b>event name:</b> ${e.name},",
      "<b>event value:</b> ${e.value},",
      "<b>state.currentScene:</b> ${state.currentScene}"
    ].join('<br/>')
  )
  if (e.name == 'mode' && state.currentScene == 'AUTOMATIC') {
    if (!settings.motionSensor) {
      turnOnRoomSceneVsw (state.roomName, getSceneForMode(e.value))
    }
  }
}

void keypadSceneButtonHandler (Event e) {
  // Design Note
  //   - The field e.deviceId arrives as a number and must be cast toString().
  //   - Hubitat runs Groovy 2.4. Groovy 3 constructs - x?[]?[] - are not available.
  //   - Keypad buttons are matched to state data to activate a target VSW.
  switch (e.name) {
    case 'pushed':
      // Toggle the corresponding pbsg-modes-X VSW for the keypad button.
      String targetScene = state.sceneButtonMap
                             ?.getAt(e.deviceId.toString())?.getAt(e.value)
      if (!state.roomScenePbsgAppId) {
        Lerror(
          'keypadSceneButtonHandler()',
          "Cannot find 'state.roomScenePbsgAppId'"
        )
      }
      if (targetScene) {
        String targetVsw = "${state.roomScenePbsgAppId}_${targetScene}"
        Ldebug('keypadSceneButtonHandler()', "toggling ${targetVsw}")
        getRoomScenePbsg().pbsgToggle(targetVsw)
      }
      break
    case 'held':
    case 'released':
      // Ignore without logging
      break
    default:
      Lwarn(
        'keypadSceneButtonHandler()',
        "for '${state.roomName}' unexpected event name '${e.name}' for Dni '${e.deviceId}'"
      )
  }
}

void picoButtonHandler (Event e) {
  Integer changePercentage = 10
  if (e.isStateChange == true) {
    switch (e.name) {
      case 'pushed':
        // Check to see if the received button is assigned to a scene.
        String scene = state.picoButtonToTargetScene?.getAt(e.deviceId.toString())
                                                    ?.getAt(e.value)
        if (!state.roomScenePbsgAppId) {
          Lerror('picoButtonHandler()', "Cannot find 'state.roomScenePbsgAppId'")
        }
        String scenePbsg = "${state.roomScenePbsgAppId}_${scene}"
        if (scene) {
          Lwarn(
            'picoButtonHandler()',
            "w/ ${e.deviceId}-${e.value} toggling ${scenePbsg}"
          )
          getChildAppByLabel(state.roomScenePbsgAppId).pbsgToggle(scenePbsg)
        } else if (e.value == '2') {  // Default "Raise" behavior
          Lwarn('picoButtonHandler()', "Raising ${settings.independentDevices}")
          settings.independentDevices.each{ d ->
            if (SwitchState(d) == 'off') {
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
          Lwarn('picoButtonHandler()', "Lowering ${settings.independentDevices}")
          settings.independentDevices.each{ d ->
              d.setLevel(Math.max(
                (d.currentValue('level') as Integer) - changePercentage,
                0
              ))
          }
        } else {
          Ltrace(
            'picoButtonHandler()',
            "R_${state.roomName} picoButtonHandler() w/ ${e.deviceId}-${e.value} no action."
          )
        }
        break
      // case 'held':
      // case 'released':
      // default:
    }
  }
  // Ignore non-state change events.
}

void motionSensorHandler (Event e) {
  if (e.name == 'motion' && e.isStateChange == true) {
    if (e.value == 'active') {
      String targetScene = (state.currScenePerVsw == 'AUTOMATIC')
        ? getSceneForMode() : state.currScenePerVsw
      activateScene(targetScene)
    } else if (e.value == 'inactive') {
      // Use brute-force to ensure automation is restored when the room is empty.
      state.currScenePerVsw = 'AUTOMATIC'
      activateScene('Off')
    }
  }
}

//----
//---- SCHEDULED ROUTINES
//----   Methods specific to this execution context
//----

//----
//---- HTTP ENDPOINTS
//----   Methods specific to this execution context
//----

//----
//---- RENDERING AND DISPLAY
//----   Methods specific to this execution context
//----

Map roomScenePage () {
  // Lessons Learned
  //   - The WHA parent application assigns a unique label to each WHA Room instance.
  //   - The WHA Room label, getLabel(), should be the actual Room Name.
  //   - This page SHOULD NOT create or interact with the Room Scene PBSG instance.
  //   - This page DOES PRESENT Room Scene PBSG state if the Room Scene PBSG exists.
  return dynamicPage(
    name: 'roomScenePage',
    title: [
      Heading1("WHA Room - ${AppInfo(app)}"),
      Bullet1("Click <b>'Done'</b> to enable subscriptions."),
      Bullet1('Tab to register changes.')
    ].join('<br/>'),
    install: true,
    uninstall: false
  ) {
    removeLegacySettingsAndState()
    if (!state.logLevel) state.logLevel = 1
    InstAppW roomScenePbsg = getRoomScenePbsg()
    section {
      solicitLogThreshold()                            // <- provided by Utils
      authorizeMotionSensor()
      selectModeNamesAsSceneNames()
      identifyCustomScenes()
      populateStateScenes()
      solicitScenePerMode()
      authorizeSeeTouchKeypads()
      identifyKeypadSceneButtons()
      wireButtonsToScenes ()
      authorizePicos()
      wirePicoButtonsToScenes()
      authorizeRepeaterAccess()
      authorizeIndependentDevicesAccess()
      solicitRoomSceneDetails()
      displayWhaRoomDebugData()
      displayRoomScenePbsgDebugData()
    }
  }
}

void authorizeMotionSensor () {
  input(
    name: 'motionSensor',
    title: [
      Heading2("Identify/Authorize the Room's Motion Sensor (if applicable)"),
      Bullet1("If a Motion Sensor is identify, the Custom Scene '<b>Off</b>' is automatically added")
    ].join('<br/>'),
    type: 'device.LutronMotionSensor',
    submitOnChange: true,
    required: false,
    multiple: false
  )
}

void selectModeNamesAsSceneNames () {
  List<String> sceneNames = ModeNames()
  input(
    name: 'modeNamesAsSceneNames',
    type: 'enum',
    title: Heading2('Optional: Select "Mode Names" to use as "Scene Names":'),
    submitOnChange: true,
    required: false,
    multiple: true,
    options: sceneNames.sort()
  )
}

void identifyCustomScenes () {
  String prefix = 'customScene'
  LinkedHashMap<String, String> slots = [
    "${prefix}1": settings.motionSensor ? 'Off' : settings["${prefix}1"],
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
  paragraph Heading2('Add Custom Scene Names <em>(optional)</em>:')
  custom.each{ key, value ->
    input(
      name: key,
      type: 'text',
      title: "<b>Custom Scene Name:</b>",
      width: 2,
      submitOnChange: true,
      required: false,
      defaultValue: value
    )
  }
}

void populateStateScenes () {
  List<String> scenes = settings.modeNamesAsSceneNames ?: []
  scenes = scenes.flatten()
  String prefix = 'customScene'
  List<String> customScenes = [
    settings["${prefix}1"],
    settings["${prefix}2"],
    settings["${prefix}3"],
    settings["${prefix}4"],
    settings["${prefix}5"],
    settings["${prefix}6"],
    settings["${prefix}7"],
    settings["${prefix}8"],
    settings["${prefix}9"],
  ].findAll{it != null}
  if (customScenes) {
    scenes << customScenes
    scenes = scenes.flatten().toUnique()
  }
  scenes = scenes.sort()
  state.scenes = scenes.size() > 0 ? scenes : null
}

void solicitScenePerMode () {
  if (state.scenes == null) {
    paragraph 'Mode-to-Scene selection will proceed once scene names exist.'
  } else {
    paragraph Heading2('Select the Automatic Scene for each Hubitat Mode:')
    getLocation().getModes().collect{mode -> mode.name}.each{ modeName ->
      String inputName = "modeToScene^${modeName}"
      String defaultValue = settings[inputName]
        ?: state.scenes.contains(modeName) ? modeName : null
      input(
        name: inputName,
        type: 'enum',
        title: modeName,
        width: 2,
        submitOnChange: true,
        required: true,
        multiple: false,
        options: state.scenes,
        defaultValue: defaultValue
      )
    }
  }
}

void authorizeSeeTouchKeypads () {
  input(
    name: 'seeTouchKeypads',
    title: Heading2("Identify/Authorize Keypads that host ${app.getLabel()} Room Scene buttons"),
    type: 'device.LutronSeeTouchKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void identifyKeypadSceneButtons () {
  input(
    name: 'sceneButtons',
    title: Heading2('Identify Keypad Scene Buttons'),
    type: 'device.LutronComponentSwitch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void populateStateKpadButtonDniToTargetScene () {
  Map<String, String> result = [:]
  state.sceneButtonMap.collect{ kpadDni, buttonMap ->
    buttonMap.each{ buttonNumber, targetScene ->
      result["${kpadDni}-${buttonNumber}"] = targetScene
    }
  }
  state.kpadButtonDniToTargetScene = result
}

void wireButtonsToScenes () {
  if (state.scenes == null || settings?.sceneButtons == null) {
    paragraph('Scene activation buttons are pending pre-requisites.')
  } else {
    identifyLedButtonsForListItems(
      state.scenes,
      settings.sceneButtons,
      'sceneButton'
    )
    populateStateKpadButtons('sceneButton')
    populateStateKpadButtonDniToTargetScene()

  }
}

void authorizePicos () {
  input(
    name: 'picos',
    title: Heading2('Identify/Authorize Picos with buttons that change the Room scene.'),
    type: 'device.LutronFastPico',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

Map<String,String> namePicoButtons (DevW pico) {
  String label = pico.getLabel()
  String id = pico.getId()
  return [
    "${id}^1": "${label}^1",
    "${id}^2": "${label}^2",
    "${id}^3": "${label}^3",
    "${id}^4": "${label}^4",
    "${id}^5": "${label}^5"
  ]
}

Map<String, String> picoButtonPicklist (List<DevW> picos) {
  Map<String, String> results = [:]
  picos.each{ pico -> results << namePicoButtons(pico) }
  return results
}

void selectPicoButtonsForScene (List<DevW> picos) {
  if (state.scenes == null) {
    paragraph(
      'Once scene names exist, this section will solicit affiliated pico buttons.'
    )
  } else {
    List<String> picoScenes = ['AUTOMATIC'] << state.scenes
    picoScenes.flatten().each{ sceneName ->
      input(
          name: "picoButtons_${sceneName}",
          type: 'enum',
          title: Heading2("Pico Buttons activating <b>${sceneName}</b>"),
          width: 6,
          submitOnChange: true,
          required: false,
          multiple: true,
          options: picoButtonPicklist(picos)
        )
    }
  }
}

void populateStatePicoButtonToTargetScene () {
  state.picoButtonToTargetScene = [:]
  settings.findAll{ key, value -> key.contains('picoButtons_') }
    .each{ key, value ->
      String scene = key.tokenize('_')[1]
      value.each{ idAndButton ->
        List<String> valTok = idAndButton.tokenize('^')
        String deviceId = valTok[0]
        String buttonNumber = valTok[1]
        if (state.picoButtonToTargetScene[deviceId] == null) {
          state.picoButtonToTargetScene[deviceId] = [:]
        }
        state.picoButtonToTargetScene[deviceId][buttonNumber] = scene
      }
    }
}

void wirePicoButtonsToScenes () {
  if (settings.picos == null) {
    paragraph(
      'The selection of Pico buttons that activate Room Scenes is pending pre-requisites.'
    )
  } else {
    selectPicoButtonsForScene(settings.picos)
    populateStatePicoButtonToTargetScene()
  }
}

void authorizeRepeaterAccess () {
  input(
    name: 'mainRepeater',
    title: Heading2('Identify/Authorize the Main Repeaters hosting Room Scene integration buttons'),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: false
  )
}

void authorizeIndependentDevicesAccess () {
  input(
    name: 'independentDevices',
    title: Heading2('Identify/Authorize the Independent Devices that participate in Room Scenes'),
    type: 'capability.switch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void populateStateSceneToDeviceValues () {
  // Reset state for the Repeater/Independent per-scene device values.
  state['sceneToRepeater'] = [:]
  state['sceneToIndependent'] = [:]
  settings.each{ key, value ->
    //  key w/ delimited data "scene^Night^Independent^Ra2D-59-1848"
    //                               sceneName
    //                                     deviceType
    //                                                 deviceDni
    List<String> parsedKey = key.tokenize('^')
    // Only process settings keys with the "scene" prefix.
    if (parsedKey[0] == 'scene') {
      // Circa 2023-Sep, no object destructuring syntax in Grooy.
      String sceneName = parsedKey[1]
      String deviceType = parsedKey[2]
      String deviceDni = parsedKey[3]
      // If missing, create an empty map for the scene's deviceDni->value data.
      // Note: Hubitat's dated version of Groovy lacks null-safe indexing.
      String stateKey = "sceneTo${deviceType}"
      if (!state[stateKey][sceneName]) state[stateKey][sceneName] = [:]
      // Populate the current deviceDni->value data.
      state[stateKey][sceneName][deviceDni] = value
    }
  }
}

void solicitRoomSceneDetails () {
  if (state.scenes && (settings.independentDevices || settings.mainRepeater)) {
    configureRoomScene()
    populateStateSceneToDeviceValues()
  } else {
    paragraph 'Soliciation of Room scenes is pending pre-requisite data.'
  }
}

void displayWhaRoomDebugData () {
  paragraph(
    [
      '<h2><b>roomScenePage Debug</b></h2>',
      '<h3><b>STATE</b></h3>',
      appStateAsBullets(),
      '<h3><b>SETTINGS</b></h3>',
      appSettingsAsBullets()
    ].join()
  )
}

void displayRoomScenePbsgDebugData () {
  // Use the Room Scene PBSG if it exists, but DO NOT create it if missing.
  //--OUTER-SCOPE?-> InstAppW roomScenePbsg = getRoomScenePbsg()
  if (roomScenePbsg) {
    paragraph (
      [
        "<h2><b>${AppInfo(roomScenePbsg)} Debug</b></h2>",
        '<h3><b>STATE</b></h3>',
        roomScenePbsg.pbsgGetStateBullets(),
      ].join()
    )
  } else {
    paragraph (
      [
        "<h2><b>${AppInfo(roomScenePbsg)} Debug</b></h2>",
        'The roomScenePbsg does not exist yet.',
      ].join()
    )
  }
}

//--unused-> void authorizeNonLutronDevicesForWhaRoom () {
//--unused->   input(
//--unused->     name: 'nonLutronDevices',
//--unused->     title: Heading2('Identify Required Non-Lutron Devices'),
//--unused->     type: 'enum',
//--unused->     width: 6,
//--unused->     options: parent.getNonLutronDevicesForRoom(state.roomName).collectEntries{ d ->
//--unused->       [d, d.displayName]
//--unused->     },
//--unused->     submitOnChange: true,
//--unused->     required: false,
//--unused->     multiple: true
//--unused->   )
//--unused-> }

Set<String> getSettingsSceneKeys () {
  return settings.findAll{ key, value -> key.contains('scene^') }.keySet()
}

void configureRoomScene () {
  // Display may be full-sized (12-positions) or phone-sized (4-position).
  // For phone friendliness, work one scene at a time.
  Set<String> sceneKeysAtStart = getSettingsSceneKeys()
  //-> Ldebug(
  //->   'configureRoomScene() <b>sceneKeysAtStart:</b><br/>',
  //->   sceneKeysAtStart.join('<br/>')
  //-> )
  Set<String> currentSceneKeys = []
  if (state.scenes == null) {
    paragraph 'Identification of Room Scene details selection will proceed once scene names exist.'
  } else {
    state.scenes?.each{ sceneName ->
      Integer col = 2
      paragraph("<br/><b>${ sceneName } →</b>", width: 2)
      settings.independentDevices?.each{ d ->
        String inputName = "scene^${sceneName}^Independent^${d.getDeviceNetworkId()}"
        currentSceneKeys += inputName
        col += 2
        input(
          name: inputName,
          type: 'number',
          title: "<b>${ d.getLabel() }</b><br/>Level 0..100",
          width: 2,
          submitOnChange: true,
          required: false,
          multiple: false,
          defaultValue: 0
        )
      }
      settings.mainRepeater?.each{d ->
        String inputName = "scene^${sceneName}^Repeater^${d.getDeviceNetworkId()}"
        currentSceneKeys += inputName
        col += 2
        input(
          name: inputName,
          type: 'number',
          title: "<b>${d.getLabel()}</b><br/>Button #",
          width: 2,
          submitOnChange: true,
          required: false,
          multiple: false,
          defaultValue: 0
        )
      }
      // Fill to end of logical row
      while (col++ % 12) {
        paragraph('', width: 1)
      }
    }

  }
  // Prune stale scene settings keys.
  //-> Ldebug(
  //->   'configureRoomScene()<br/>',
  //->   [
  //->     "<b>Scene Keys (Start):</b> ${sceneKeysAtStart}",
  //->     "<b>Scene Keys   (End):</b> ${currentSceneKeys}",
  //->     "<b>Excess Keys (Diff):</b> ${sceneKeysAtStart.minus(currentSceneKeys)}"
  //->   ].join('<br/>')
  //-> )
  sceneKeysAtStart.minus(currentSceneKeys).each{ key ->
    Ldebug('configureRoomScene()', "removing setting ${key}")
    settings.remove(key)
  }
}
