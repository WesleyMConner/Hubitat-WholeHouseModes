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
#include wesmc.libFifo
#include wesmc.libHubExt
#include wesmc.libHubUI
#include wesmc.libLutron
#include wesmc.libPbsgCore

definition (
  parent: 'wesmc:wha',
  name: 'roomScenes',
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

//---- CORE METHODS (External)

//---- CORE METHODS (Internal)

void _activateScene (String scene) {
  // Push Repeater buttons and execute Independent switch/dimmer levels.
  Ldebug('_activateScene()', "scene: <b>${scene}</b>")
  // Values are expected at ...
  //   state.sceneToRepeater[sceneName][dni]
  //   state.sceneToIndependent[sceneName][dni]
  // THIS APPLICATION ALLOWS A SINGLE LUTRON MAIN REPEATER PER ROOM
  state.sceneToRepeater?.getAt(scene)?.each{ repeaterDni, buttonNumber ->
    Ldebug(
      '_activateScene()',
      "${scene}: repeater: ${repeaterDni}, button: ${buttonNumber}"
    )
    // Note: The repeater's Id (not DNI) and button are required to track the scene's
    //       LED on the Main Repeater.
    state.roomSceneRepeaterLED = buttonNumber
    DevW matchedRepeater = settings.mainRepeater?.findAll{ repeater ->
      repeater.getDeviceNetworkId() == repeaterDni
    }?.first() ?: {
      Ldebug(
        '_activateScene()',
        "no repeater w/ DNI: ${repeaterDni}"
      )
    }
    state.roomSceneRepeaterDeviceId = matchedRepeater.getId()
    matchedRepeater.push(buttonNumber)
  }
  state.sceneToIndependent?.getAt(scene)?.each{ deviceDni, level ->
    Ldebug(
      '_activateScene()',
      "${scene}': device: ${deviceDni}, level: ${level}"
    )
    DevW matchedDevice = settings.independentDevices?.findAll{ device ->
      device.getDeviceNetworkId() == deviceDni
    }?.first() ?: {                              // There should be one match by DNI.
      Ldebug(
        '_activateScene()',
        "no matchedDevice w/ DNI: ${deviceDni}"
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

Boolean _isRoomSceneLedActive() {
  // Fail true if the current room's scenes DO NOT leverage an
  // "RA2 Shared Scene" (via an RA2 Main Repeater Integration Button).
  Boolean retVal = true
  if (!state.roomSceneRepeaterDeviceId) {
    Ldebug(
      '_isRoomSceneLedActive()',
      'No RA2 Shared Scene in use.'
    )
  } else {
    // LEDs will light if (a) they match an explicitly set Room Scene or
    // (b) they match the room's current AUTOMATIC scene. No LEDs should
    // light if the room's scene is MANUAL_OVERRIDE.
    String ledScene = (state.currScenePerVsw == 'AUTOMATIC')
      ? getSceneForMode()
      : state.currScenePerVsw
    Ldebug(
      '_isRoomSceneLedActive()',
      "ledScene: ${ledScene}"
    )
    Map repeaterData = state.sceneToRepeater?.getAt(ledScene)
    if (repeaterData) {
      Ldebug(
        '_isRoomSceneLedActive()',
        "repeaterData: ${repeaterData}"
      )
      settings?.mainRepeater.eachWithIndex{ rep, index ->
        String repDni = rep.deviceNetworkId
        Ldebug(
          '_isRoomSceneLedActive()',
          "repDni: ${repDni}"
        )
        String associatedButton = repeaterData.getAt(repDni)
        Ldebug(
          '_isRoomSceneLedActive()',
          "associatedButton: ${associatedButton}"
        )
        String led = "buttonLed-${associatedButton}"
        Ldebug(
          '_isRoomSceneLedActive()',
          "led: ${led}"
        )
        String ledVal = rep.currentValue(led)
        Ldebug(
          '_isRoomSceneLedActive()',
          "ledVal: ${ledVal}"
        )
        if (ledVal == 'off') {
          retVal = false
          Ldebug(
            '_isRoomSceneLedActive()',
            "retVal: ${retVal}"
          )
          // Note: It is NOT possible to break from a closure.
        }
      }
    }
  }
  Ldebug(
    '_isRoomSceneLedActive()',
    "R_${state.ROOM_NAME} _isRoomSceneLedActive() -> ${retVal}"
  )
  return retVal
}

Boolean _areRoomSceneDevLevelsCorrect() {
  // Fail true if the current room's scenes DO NOT leverage Independent Devices.
  // Note that device level comparisons are made to state.roomScene (and
  // NOT state.currScenePerVsw). When state.currScenePerVsw == MANUAL_OVERRIDE,
  // state.roomScene will retain the critera required to release the OVERRIDE.
  Boolean retVal = true
  if (!state.sceneToIndependent) {
    Ldebug(
      '_areRoomSceneDevLevelsCorrect()',
      "No Independent Devices."
    )
  } else {
    Ldebug(
      '_areRoomSceneDevLevelsCorrect()',
      "sceneToIndependent: ${state.sceneToIndependent}"               // SEEN IN LOGS
    )
    if (!state.roomScene) {
      if (!state.currScenePerVsw) {
        Lerror(_areRoomSceneDevLevelsCorrect, '!!!!! SPECIAL !!!!! state.roomScene IS NOT populated')
      }
    }
    Ldebug(
      '_areRoomSceneDevLevelsCorrect()',
      "state.roomScene: ${state.roomScene}"                     // NOT AVAILABLE
    )
    String restoreScene = (state.roomScene == 'AUTOMATIC')
      ? getSceneForMode()
      : state.roomScene
    Ldebug(
      '_areRoomSceneDevLevelsCorrect()',
      "restoreScene: ${restoreScene}"
    )
    Map indepDevData = state.sceneToIndependent?.getAt(restoreScene)
    Ldebug(
      '_areRoomSceneDevLevelsCorrect()',
      "indepDevData: ${indepDevData}"
    )
    settings?.independentDevices.each{ dev ->
      Ldebug(
        '_areRoomSceneDevLevelsCorrect()',
        "DNI: ${dev.deviceNetworkId}"
      )
      Integer devTargetVal = indepDevData?.getAt(dev.deviceNetworkId)
      if (devTargetVal) {
        Ldebug(
          '_areRoomSceneDevLevelsCorrect()',
          "devTargetVal: ${devTargetVal}"
        )
        if (dev.hasAttribute('switch')) {
          String expectedSwitch = (devTargetVal == 0) ? 'off' : 'on'
          String actualSwitch = dev.currentValue('switch')
          Ldebug(
            '_areRoomSceneDevLevelsCorrect()',
            "switch: expected: ${expectedSwitch}, actual: ${actualSwitch}"
          )
        }
        if (dev.hasAttribute('level')) {
          Integer actualLevel = dev.currentValue('level')
          Ldebug(
            '_areRoomSceneDevLevelsCorrect()',
            "level: expected: ${devTargetVal} actual: ${actualLevel}"
          )
        }
      }
    }
  }
  Ldebug(
    '_areRoomSceneDevLevelsCorrect()',
    "retVal: ${retVal}"
  )
  return retVal
}

InstAppW _getScenePbsg () {
  InstAppW retVal = app.getChildAppByLabel(state.SCENE_PBSG_APP_NAME)
    ?: Lerror(
      '_getScenePbsg()',
      "<b>FAILED</b> to locate scenePbsg App"
    )
  return retVal
}

Boolean _detectManualOverride() {
  // Turning a PBSG switch on/off that's already on/off WILL NOT generate a
  // change event; so, don't worry about suppressing redundant switch state for now.
  if (!_isRoomSceneLedActive() || !_areRoomSceneDevLevelsCorrect()) {
    _getScenePbsg().turnOnSwitch("${state.SCENE_PBSG_APP_NAME}_MANUAL_OVERRIDE")
  } else {
    _getScenePbsg().turnOffSwitch("${state.SCENE_PBSG_APP_NAME}_MANUAL_OVERRIDE")
  }
}

void updateLutronKpadLeds (String currScene) {
  settings.sceneButtons.each{ ledObj ->
    String dni = ledObj.getDeviceNetworkId()
    String sceneTarget = state.kpadButtonDniToTargetScene[dni]
    if (currScene == sceneTarget) {
      Ldebug(
        'updateLutronKpadLeds()',
        "Turning on LED ${dni} for ${state.ROOM_NAME} scene ${sceneTarget}"
      )
      ledObj.on()
    } else {
      Ldebug(
        'updateLutronKpadLeds()',
        "Turning off LED ${dni} for ${state.ROOM_NAME} scene ${sceneTarget}"
      )
      ledObj.off()
    }
  }
}

String getSceneForMode (String mode = getLocation().getMode()) {
  String result = settings["modeToScene^${mode}"]
  Ldebug(
    'getSceneForMode()',
    "mode: <b>${mode}</b>, scene: <b>${result}</b>"
  )
  return result
}

void pbsgVswTurnedOnCallback (String currPbsgSwitch) {
  String currScene = currPbsgSwitch?.minus("${state.SCENE_PBSG_APP_NAME}_")
  // If 'state.roomScene' is observed, MANUAL_OVERRIDE is resolved.
  state.roomScene = (currScene == 'MANUAL_OVERRIDE') ? state.roomScene : currScene
  if (state.roomScene == 'MANUAL_OVERRIDE') {
    Lerror('pbsgVswTurnedOnCallback()', 'state.roomScene == MANUAL_OVERRIDE')
  }
  state.currScenePerVsw = currScene
  Ldebug(
    'pbsgVswTurnedOnCallback()',
    "currPbsgSwitch: ${currPbsgSwitch}, currScene: ${currScene}, inspectScene: ${state.roomScene}"
  )
  //-----> updateLutronKpadLeds(currScene)
  switch(currScene) {
    case 'AUTOMATIC':
      String targetScene = getSceneForMode()
      Ldebug(
        'pbsgVswTurnedOnCallback()',
        "AUTOMATIC -> ${targetScene}"
      )
      if (!settings?.motionSensor) _activateScene(targetScene)
      break
    default:
      Ldebug(
        'pbsgVswTurnedOnCallback()',
        "processing '${currScene}'"
      )
      if (!settings?.motionSensor) _activateScene(currScene)
  }
}

//---- EVENT HANDLERS

void repeaterLedHandler (Event e) {
  // - The field e.deviceId arrives as a number and must be cast toString().
  // - This subscription processes Main Repeater events, which is applicable
  //   to Rooms that leverage an RA2 Main Repeater (virtual) Integration
  //   Button and corresponding (virtual) LED. Work is delegated to
  //   _detectManualOverride()
  if (
       (e.deviceId.toString() == state.roomSceneRepeaterDeviceId)
       && (e.name == "buttonLed-${state.roomSceneRepeaterLED}")
       && (e.isStateChange == true)
  ) {
    Ldebug(
      'repeaterLedHandler()',
      'calling _detectManualOverride()'
    )
    _detectManualOverride()
  }
}

void independentDeviceHandler (Event e) {
  // - This subscription processes Independent Device events. Work is delegated
  //   to _detectManualOverride.
  Ldebug(
    'independentDeviceHandler()',
    'calling _detectManualOverride()'
  )
  _detectManualOverride()
}

void hubitatModeChangeHandler (Event e) {
  if (
    e.name == 'mode'
    && state.currentScene == 'AUTOMATIC'
  ) {
    if (!state.roomScene) {
      state.roomScene = 'AUTOMATIC'
    }
    String targetScene = getSceneForMode(e.value)
    Ldebug(
      'hubitatModeChangeHandler()',
      "processing AUTOMATIC -> ${targetScene}"
    )
    if (!settings?.motionSensor) _activateScene(targetScene)
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
      String targetScene = state.sceneButtonMap?.getAt(e.deviceId.toString())
                                               ?.getAt(e.value)
      if (targetScene) {
        String targetVsw = "${state.SCENE_PBSG_APP_NAME}_${targetScene}"
        Ldebug(
          'keypadSceneButtonHandler()',
          "toggling ${targetVsw}"
        )
        _getScenePbsg().toggleSwitch(targetVsw)
      }
      break
    case 'held':
    case 'released':
      // Ignore without logging
      break
    default:
      Lwarn(
        'keypadSceneButtonHandler()',
        "for '${state.ROOM_NAME}' unexpected event name '${e.name}' for DNI '${e.deviceId}'"
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
        String scenePbsg = "${state.SCENE_PBSG_APP_NAME}_${scene}"
        if (scene) {
          Ldebug(
            'picoButtonHandler()',
            "w/ ${e.deviceId}-${e.value} toggling ${scenePbsg}"
          )
          app.getChildAppByLabel(state.SCENE_PBSG_APP_NAME).toggleSwitch(scenePbsg)
        } else if (e.value == '2') {  // Default "Raise" behavior
          Ldebug(
            'picoButtonHandler()',
            "Raising ${settings.independentDevices}"
          )
          settings.independentDevices.each{ d ->
            if (getSwitchState(d) == 'off') {
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
          Ldebug(
            'picoButtonHandler()',
            "Lowering ${settings.independentDevices}"
          )
          settings.independentDevices.each{ d ->
              d.setLevel(Math.max(
                (d.currentValue('level') as Integer) - changePercentage,
                0
              ))
          }
        } else {
          Ldebug(
            'picoButtonHandler()',
            "R_${state.ROOM_NAME} picoButtonHandler() w/ ${e.deviceId}-${e.value} no action."
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
      _activateScene(targetScene)
    } else if (e.value == 'inactive') {
      // Use brute-force to ensure automation is restored when the room is empty.
      state.currScenePerVsw = 'AUTOMATIC'
      _activateScene('Off')
    }
  }
}

//---- SYSTEM CALLBACKS

void installed () {
  Ldebug('installed()', 'At Entry')
  //-> initialize()
}

void updated () {
  Ldebug('updated()', 'At Entry')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  //-> initialize()
}

void uninstalled () {
  Ldebug('uninstalled()', 'At Entry')
}

void initialize () {
  Ldebug(
    'initialize()',
    "R_${state.ROOM_NAME} initialize() of '${state.ROOM_NAME}'. "
      + "Subscribing to hubitatModeChangeHandler."
  )
  subscribe(location, "mode", hubitatModeChangeHandler)
  settings.seeTouchKeypads.each{ device ->
    Ldebug(
      'initialize()',
      "R_${state.ROOM_NAME} subscribing to Keypad ${getDeviceInfo(device)}"
    )
    subscribe(device, keypadSceneButtonHandler, ['filterEvents': true])
  }
  settings.mainRepeater.each{ device ->
    Ldebug(
      'initialize()',
      "R_${state.ROOM_NAME} subscribing to Repeater ${getDeviceInfo(device)}"
    )
    subscribe(device, repeaterLedHandler, ['filterEvents': true])
  }
  settings.picos.each{ device ->
    Ldebug(
      'initialize()',
      "R_${state.ROOM_NAME} subscribing to Pico ${getDeviceInfo(device)}"
    )
    subscribe(device, picoButtonHandler, ['filterEvents': true])
  }
  settings.motionSensor.each{ device ->
    Ldebug(
      'initialize()',
      "R_${state.ROOM_NAME} subscribing to Motion Sensor ${getDeviceInfo(device)}"
    )
    subscribe(device, motionSensorHandler, ['filterEvents': true])
  }
  settings.independentDevices.each{ device ->
    Ldebug(
      'initialize()',
      "R_${state.ROOM_NAME} subscribing to independentDevice ${getDeviceInfo(device)}"
    )
    subscribe(device, independentDeviceHandler, ['filterEvents': true])
  }
}

//---- GUI / PAGE RENDERING

void _authMotionSensor () {
      input(
        name: 'motionSensor',
        title: [
          Heading2("Authorize ${state.ROOM_NAME}'s Motion Sensor"),
          Bullet2('Identify one Motion Sensor if desired<br/>.'),
          Bullet2('The Custom Scene "<b>Off</b>" is automatically added below.')
        ].join(),
        type: 'device.LutronMotionSensor',
        submitOnChange: true,
        required: false,
        multiple: false
      )
}

void _selectModeNamesAsSceneNames () {
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

void _identifyCustomScenes () {
}

void _populateStateScenes () {
}

void _selectScenePerMode () {
}

void _authSeeTouchKeypads () {
  input(
    name: 'seeTouchKeypads',
    title: [
      Heading2("Authorize ${state.ROOM_NAME}'s SeeTouch Keypads"),
      Bullet2('Authorize Keypads with buttons that activate room scenes.')
    ].join(),
    type: 'device.LutronSeeTouchKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _identifyRoomSceneButtons () {
  input(
    name: 'sceneButtons',
    title: [
      Heading2("Authorize ${state.ROOM_NAME}'s Keypad Buttons/LEDs"),
      Bullet2('Authorize Keypad LEDs/Buttons that activate room scenes.')
    ].join(),
    type: 'device.LutronComponentSwitch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _wireKeypadButtonsToScenes () {
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
      String deviceDNI = parsedKey[3]
      // If missing, create an empty map for the scene's deviceDNI->value data.
      // Note: Hubitat's dated version of Groovy lacks null-safe indexing.
      String stateKey = "sceneTo${deviceType}"
      if (!state[stateKey][sceneName]) state[stateKey][sceneName] = [:]
      // Populate the current deviceDNI->value data.
      state[stateKey][sceneName][deviceDNI] = value
    }
  }
}

void _authRoomScenesPicos () {
  input(
    name: 'picos',
    title: [
      Heading2("Authorize ${state.ROOM_NAME}'s Picos"),
      Bullet2('Identify Picos with buttons that change the Room scene.')
    ].join(),
    type: 'device.LutronFastPico',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _wirePicoButtonsToScenes () {
  if (settings.picos == null) {
    paragraph('Selection of pico buttons to activate scenes is pending pre-requisites.')
  } else {
    selectPicoButtonsForScene(settings.picos)
    populateStatePicoButtonToTargetScene()
  }
}

void _authMainRepeaterImplementingScenes () {
  input(
    name: 'mainRepeater',
    title: [
      Heading2("Authorize ${state.ROOM_NAME}'s Main Repeaters (with Integration Buttons)"),
      Bullet2('Identify Repeaters that host integration buttons for Room scenes')
    ].join(),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: false
  )
}

void _authIndependentDevices () {
  input(
    name: 'independentDevices',
    title: [
          Heading2("Authorize ${state.ROOM_NAME}'s Independent Devices"),
          Bullet2('Identify Repeaters that host integration buttons for Room scenes.')
    ].join(),
    type: 'capability.switch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
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

void _solicitRoomScenes () {
  if (state.scenes && (settings.independentDevices || settings.mainRepeater)) {
    configureRoomScene()
    populateStateSceneToDeviceValues()
  } else {
    paragraph 'Soliciation of Room scenes is pending pre-requisite data.'
  }
}

void _manageRoomScenePbsg () {
  if (state.scenes == null) {
    paragraph 'Management of child apps is pending selection of Room scenes.'
  } else {
    // Before presenting room drilldown HREFs, prune any d
    //->keepOldestAppObjPerAppLabel([state.SCENE_PBSG_APP_NAME])
    PruneAppDups(
      [state.SCENE_PBSG_APP_NAME],
      false,   // For dups, keep oldest
      app      // The object (parent) pruning dup children
    )
  }
}

void _displayRoomScenesPbsg () {
  paragraph heading('Inspect Room PBSG')
  InstAppW roomPBSG = app.getChildAppByLabel(state.SCENE_PBSG_APP_NAME)
  if (!roomPBSG || roomPBSG.getAllChildDevices().size() == 0) {
    roomPBSG = addChildApp('wesmc', 'roomPBSG', 'roomPbsgPage')
  }
}

void _configureRoomScenesPbsg () {
  List<String> vswDNIs = [
    *state.scenes, 'AUTOMATIC', 'MANUAL_OVERRIDE'
  ].collect{ scene ->
    "${state.SCENE_PBSG_APP_NAME}_${scene}"
  }
  String defaultSwitchDNI = "${state.SCENE_PBSG_APP_NAME}_AUTOMATIC"
  // Set core instance fields immediately after PBSG instantiation.
  roomPBSG.configPbsg(vswDNIs, defaultSwitchDNI, settings.logThreshold)
}

Map RoomScenesPage () {
  // The parent application (Whole House Automation) assigns a unique label
  // to each WHA Rooms instance. Capture app.getLabel() as state.ROOM_NAME.
  return dynamicPage(
    name: 'RoomScenesPage',
    title: [
      "TITLE PLACEHOLDER"
      //Heading1("${app.getLabel()} Scenes<br/>"),
      //Bullet1("Click <b>${'Done'}</b> to enable subscriptions.<br/>"),
      //Bullet1('Tab to register changes.')
    ].join(),
    install: true,
    uninstall: true,
  ) {
    // SAMPLE STATE & SETTINGS CLEAN UP
    //   - app.deleteChildDevice(<INSERT DNI>)
    //   - state.remove('X')
    //   - settings.remove('Y')
    //---------------------------------------------------------------------------------
    // REMOVE NO LONGER USED SETTINGS AND STATE
    //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    //-> settings.remove('log')
    //-> state.remove('ManualOverrideDevice')
    //-> state.remove('PBSGapp')
    //-> state.remove('currentScene')
    //-> state.remove('currentSceneRepeaterDeviceId')
    //-> state.remove('currentSceneRepeaterLED')
    //-> state.remove('inspectScene')
    //---------------------------------------------------------------------------------
    //state.ROOM_NAME = app.getLabel()
    //state.SCENE_PBSG_APP_NAME = "pbsg_${state.ROOM_NAME.replace(' ', '_')}"
    section {
      //solicitLogThreshold('appLogThreshold')        // <- provided by Utils
      //solicitLogThreshold('pbsgLogThreshold')       // <- provided by Utils
      paragraph 'YOU ARE HERE'
      /*
      _authMotionSensor()
      _selectModeNamesAsSceneNames()
      _identifyCustomScenes()
      _populateStateScenes()
      _selectScenePerMode()
      _authSeeTouchKeypads()
      _identifyRoomSceneButtons()
      _wireKeypadButtonsToScenes()
      _wirePicoButtonsToScenes()
      _authMainRepeaterImplementingScenes()
      _authIndependentDevices()
      _solicitRoomScenes()
      _manageRoomScenePbsg()
      _displayRoomScenesPbsg()
      _configureRoomScenesPbsg()
      href (
        name: state.SCENE_PBSG_APP_NAME,
        width: 2,
        url: "/installedapp/configure/${roomPBSG.getId()}/roomPbsgPage",
        style: 'internal',
        title: "Edit <b>${AppInfo(roomPBSG)}</b>",
        state: null
      )
      paragraph([
        heading('Debug<br/>'),
        *appStateAsBullets(true),
        *appSettingsAsBullets(true)
      ].join('<br/>'))
      */
    }
  }
}
