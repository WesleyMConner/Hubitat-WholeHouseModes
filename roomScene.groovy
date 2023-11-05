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
#include wesmc.libLogAndDisplay
#include wesmc.libPbsgBase
#include wesmc.libUtils

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
  page(name: '_roomScenePage')
}

//----
//---- PAGE DISPLAY AND SUPPORT
//----

Map _roomScenePage () {
  // Lessons Learned
  //   - The WHA parent application assigns a unique label to each WHA Room instance.
  //   - The WHA Room label, app.getLabel(), should be the actual Room Name.
  //   - This page SHOULD NOT create or interact with the Room Scene PBSG instance.
  //   - This page DOES PRESENT Room Scene PBSG state if the Room Scene PBSG exists.
  return dynamicPage(
    name: '_roomScenePage',
    title: [
      heading1("WHA Room - ${getAppInfo(app)}"),
      bullet1("Click <b>'Done'</b> to enable subscriptions."),
      bullet1('Tab to register changes.')
    ].join('<br/>'),
    install: true,
    uninstall: false
  ) {
    _removeLegacySettingsAndState()
    if (!atomicState.logLevel) atomicState.logLevel = 1
    InstAppW roomScenePbsg = getRoomScenePbsg()
    section {
      _solicitLogThreshold()                            // <- provided by Utils
      _authorizeMotionSensor()
      _selectModeNamesAsSceneNames()
      _identifyCustomScenes()
      _populateStateScenes()
      _solicitScenePerMode()
      _authorizeSeeTouchKeypads()
      _identifyKeypadSceneButtons()
      _wireButtonsToScenes ()
      _authorizePicos()
      _wirePicoButtonsToScenes()
      _authorizeRepeaterAccess()
      _authorizeIndependentDevicesAccess()
      _solicitRoomSceneDetails()
      _displayWhaRoomDebugData()
      _displayRoomScenePbsgDebugData()
    }
  }
}

void _removeLegacySettingsAndState () {
  settings.remove('log')
  atomicState.remove('currentScene')
  atomicState.remove('currentSceneRepeaterDeviceId')
  atomicState.remove('currentSceneRepeaterLED')
  atomicState.remove('defaultVswDni')
  atomicState.remove('inspectScene')
  atomicState.remove('LOG_LEVEL1_ERROR')
  atomicState.remove('LOG_LEVEL2_WARN')
  atomicState.remove('LOG_LEVEL3_INFO')
  atomicState.remove('LOG_LEVEL4_DEBUG')
  atomicState.remove('LOG_LEVEL5_TRACE')
  atomicState.remove('logLevel1Error')
  atomicState.remove('logLevel2Warn')
  atomicState.remove('logLevel3Info')
  atomicState.remove('logLevel4Debug')
  atomicState.remove('logLevel5Trace')
  atomicState.remove('ManualOverrideDevice')
  atomicState.remove('PBSGapp')
  atomicState.remove('ROOM_NAME')
  atomicState.remove('roomName')
  atomicState.remove('SCENE_PBSG_APP_NAME')
}

void _authorizeMotionSensor () {
  input(
    name: 'motionSensor',
    title: [
      heading2("Identify/Authorize the Room's Motion Sensor (if applicable)"),
      bullet1("If a Motion Sensor is identify, the Custom Scene '<b>Off</b>' is automatically added")
    ].join('<br/>'),
    type: 'device.LutronMotionSensor',
    submitOnChange: true,
    required: false,
    multiple: false
  )
}

void _selectModeNamesAsSceneNames () {
  List<String> sceneNames = getModeNames()
  input(
    name: 'modeNamesAsSceneNames',
    type: 'enum',
    title: heading2('Optional: Select "Mode Names" to use as "Scene Names":'),
    submitOnChange: true,
    required: false,
    multiple: true,
    options: sceneNames.sort()
  )
}

void _identifyCustomScenes () {
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
  paragraph heading2('Add Custom Scene Names <em>(optional)</em>:')
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

void _populateStateScenes () {
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
  atomicState.scenes = scenes.size() > 0 ? scenes : null
}

void _solicitScenePerMode () {
  if (atomicState.scenes == null) {
    paragraph 'Mode-to-Scene selection will proceed once scene names exist.'
  } else {
    paragraph heading2('Select the Automatic Scene for each Hubitat Mode:')
    getLocation().getModes().collect{mode -> mode.name}.each{ modeName ->
      String inputName = "modeToScene^${modeName}"
      String defaultValue = settings[inputName]
        ?: atomicState.scenes.contains(modeName) ? modeName : null
      input(
        name: inputName,
        type: 'enum',
        title: modeName,
        width: 2,
        submitOnChange: true,
        required: true,
        multiple: false,
        options: atomicState.scenes,
        defaultValue: defaultValue
      )
    }
  }
}

void _authorizeSeeTouchKeypads () {
  input(
    name: 'seeTouchKeypads',
    title: heading2("Identify/Authorize Keypads that host ${app.getLabel()} Room Scene buttons"),
    type: 'device.LutronSeeTouchKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _identifyKeypadSceneButtons () {
  input(
    name: 'sceneButtons',
    title: heading2('Identify Keypad Scene Buttons'),
    type: 'device.LutronComponentSwitch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _populateStateKpadButtonDniToTargetScene () {
  Map<String, String> result = [:]
  atomicState.sceneButtonMap.collect{ kpadDni, buttonMap ->
    buttonMap.each{ buttonNumber, targetScene ->
      result["${kpadDni}-${buttonNumber}"] = targetScene
    }
  }
  atomicState.kpadButtonDniToTargetScene = result
}

void _wireButtonsToScenes () {
  if (atomicState.scenes == null || settings?.sceneButtons == null) {
    paragraph('Scene activation buttons are pending pre-requisites.')
  } else {
    identifyLedButtonsForListItems(
      atomicState.scenes,
      settings.sceneButtons,
      'sceneButton'
    )
    _populateStateKpadButtons('sceneButton')
    _populateStateKpadButtonDniToTargetScene()

  }
}

void _authorizePicos () {
  input(
    name: 'picos',
    title: heading2('Identify/Authorize Picos with buttons that change the Room scene.'),
    type: 'device.LutronFastPico',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _wirePicoButtonsToScenes () {
  if (settings.picos == null) {
    paragraph(
      'The selection of Pico buttons that activate Room Scenes is pending pre-requisites.'
    )
  } else {
    _selectPicoButtonsForScene(settings.picos)
    _populateStatePicoButtonToTargetScene()
  }
}

void _authorizeRepeaterAccess () {
  input(
    name: 'mainRepeater',
    title: heading2('Identify/Authorize the Main Repeaters hosting Room Scene integration buttons'),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: false
  )
}

void _authorizeIndependentDevicesAccess () {
  input(
    name: 'independentDevices',
    title: heading2('Identify/Authorize the Independent Devices that participate in Room Scenes'),
    type: 'capability.switch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _configureRoomScene () {
  // Display may be full-sized (12-positions) or phone-sized (4-position).
  // For phone friendliness, work one scene at a time.
  Set<String> sceneKeysAtStart = _getSettingsSceneKeys()
  //-> Ldebug(
  //->   '_configureRoomScene() <b>sceneKeysAtStart:</b><br/>',
  //->   sceneKeysAtStart.join('<br/>')
  //-> )
  Set<String> currentSceneKeys = []
  if (atomicState.scenes == null) {
    paragraph 'Identification of Room Scene details selection will proceed once scene names exist.'
  } else {
    atomicState.scenes?.each{ sceneName ->
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
  //->   '_configureRoomScene()<br/>',
  //->   [
  //->     "<b>Scene Keys (Start):</b> ${sceneKeysAtStart}",
  //->     "<b>Scene Keys   (End):</b> ${currentSceneKeys}",
  //->     "<b>Excess Keys (Diff):</b> ${sceneKeysAtStart.minus(currentSceneKeys)}"
  //->   ].join('<br/>')
  //-> )
  sceneKeysAtStart.minus(currentSceneKeys).each{ key ->
    Ldebug(
      '_configureRoomScene()',
      "removing setting ${key}"
    )
    settings.remove(key)
  }
}

void _populateStateSceneToDeviceValues () {
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

void _solicitRoomSceneDetails () {
  if (atomicState.scenes && (settings.independentDevices || settings.mainRepeater)) {
    _configureRoomScene()
    _populateStateSceneToDeviceValues()
  } else {
    paragraph 'Soliciation of Room scenes is pending pre-requisite data.'
  }
}

void _displayWhaRoomDebugData () {
  paragraph(
    [
      '<h2><b>roomScenePage Debug</b></h2>',
      '<h3><b>STATE</b></h3>',
      _getStateBulletsAsIs(),
      '<h3><b>SETTINGS</b></h3>',
      _getSettingsBulletsAsIs()
    ].join()
  )
}

void _displayRoomScenePbsgDebugData () {
  // Use the Room Scene PBSG if it exists, but DO NOT create it if missing.
  //--OUTER-SCOPE?-> InstAppW roomScenePbsg = getRoomScenePbsg()
  if (roomScenePbsg) {
    paragraph (
      [
        "<h2><b>${getAppInfo(roomScenePbsg)} Debug</b></h2>",
        '<h3><b>STATE</b></h3>',
        roomScenePbsg._getPbsgStateBullets(),
        //-> '<h3><b>SETTINGS</b></h3>',
        //-> roomScenePbsg._getSettingsBulletsAsIs()
      ].join()
    )
  } else {
    paragraph (
      [
        "<h2><b>${getAppInfo(roomScenePbsg)} Debug</b></h2>",
        'The roomScenePbsg does not exist yet.',
      ].join()
    )
  }
}

void _authorizeNonLutronDevicesForWhaRoom () {
  input(
    name: 'nonLutronDevices',
    title: heading2('Identify Required Non-Lutron Devices'),
    type: 'enum',
    width: 6,
    options: parent.getNonLutronDevicesForRoom(atomicState.roomName).collectEntries{ d ->
      [d, d.displayName]
    },
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

Map<String,String> _namePicoButtons (DevW pico) {
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

Map<String, String> _picoButtonPicklist (List<DevW> picos) {
  Map<String, String> results = [:]
  picos.each{ pico -> results << _namePicoButtons(pico) }
  return results
}

void _selectPicoButtonsForScene (List<DevW> picos) {
  if (atomicState.scenes == null) {
    paragraph(
      'Once scene names exist, this section will solicit affiliated pico buttons.'
    )
  } else {
    List<String> picoScenes = ['AUTOMATIC'] << atomicState.scenes
    picoScenes.flatten().each{ sceneName ->
      input(
          name: "picoButtons_${sceneName}",
          type: 'enum',
          title: emphasis("Pico Buttons activating <b>${sceneName}</b>"),
          width: 6,
          submitOnChange: true,
          required: false,
          multiple: true,
          options: _picoButtonPicklist(picos)
        )
    }
  }
}

void _populateStatePicoButtonToTargetScene () {
  atomicState.picoButtonToTargetScene = [:]
  settings.findAll{ key, value -> key.contains('picoButtons_') }
          .each{ key, value ->
            String scene = key.tokenize('_')[1]
            value.each{ idAndButton ->
              List<String> valTok = idAndButton.tokenize('^')
              String deviceId = valTok[0]
              String buttonNumber = valTok[1]
              if (atomicState.picoButtonToTargetScene[deviceId] == null) {
                atomicState.picoButtonToTargetScene[deviceId] = [:]
              }
              atomicState.picoButtonToTargetScene[deviceId][buttonNumber] = scene
            }
          }
}

Set<String> _getSettingsSceneKeys () {
  return settings.findAll{ key, value -> key.contains('scene^') }.keySet()
}

void _updateLutronKpadLeds (String currScene) {
  settings.sceneButtons.each{ ledObj ->
    String dni = ledObj.getDeviceNetworkId()
    String sceneTarget = atomicState.kpadButtonDniToTargetScene[dni]
    if (currScene == sceneTarget) {
      Ldebug(
        '_updateLutronKpadLeds()',
        "Turning on LED ${dni} for ${atomicState.roomName} scene ${sceneTarget}"
      )
      ledObj.on()
    } else {
      Ldebug(
        '_updateLutronKpadLeds()',
        "Turning off LED ${dni} for ${atomicState.roomName} scene ${sceneTarget}"
      )
      ledObj.off()
    }
  }
}

String _getSceneForMode (String mode = getLocation().getMode()) {
  String result = settings["modeToScene^${mode}"]
  Ldebug(
    '_getSceneForMode()',
    "mode: <b>${mode}</b>, scene: <b>${result}</b>"
  )
  return result
}

void _pbsgVswTurnedOnCallback (String currScene) {
  // If 'atomicState.roomScene' is observed, MANUAL_OVERRIDE is resolved.
  atomicState.roomScene = (currScene == 'MANUAL_OVERRIDE') ? atomicState.roomScene : currScene
  if (atomicState.roomScene == 'MANUAL_OVERRIDE') {
    Lerror('_pbsgVswTurnedOnCallback()', 'atomicState.roomScene == MANUAL_OVERRIDE')
  }
  atomicState.currScenePerVsw = currScene
  Ldebug(
    '_pbsgVswTurnedOnCallback()',
    "currPbsgSwitch: ${currPbsgSwitch}, currScene: ${currScene}, inspectScene: ${atomicState.roomScene}"
  )
  //-----> _updateLutronKpadLeds(currScene)
  switch(currScene) {
    case 'AUTOMATIC':
      String targetScene = _getSceneForMode()
      Ldebug(
        '_pbsgVswTurnedOnCallback()',
        "AUTOMATIC -> ${targetScene}"
      )
      if (!settings?.motionSensor) _activateScene(targetScene)
      break
    default:
      Ldebug(
        '_pbsgVswTurnedOnCallback()',
        "processing '${currScene}'"
      )
      if (!settings?.motionSensor) _activateScene(currScene)
  }
}

//----
//---- STANDALONE METHODS (no inherent "this")
//----

InstAppW getRoomScenePbsg () {
  String roomScenePbsgLabel = "pbsg_${app.getLabel()}"
  // PERFORMANCE HIT - Temporarily perform App dup detection.
  Linfo('getRoomScenePbsg()', 'Calling App Dup Detection')
  detectChildAppDupsForLabels([roomScenePbsgLabel])
  InstAppW roomScenePbsg = getChildAppByLabel(roomScenePbsgLabel)
  if (roomScenePbsg) {
    // PERFORMANCE HIT - Temporarily refresh PBSG configuration.
    roomScenePbsg._configureRoomScenePbsg()
  }
  return roomScenePbsg
}

InstAppW getOrCreateRoomScenePbsg () {
  InstAppW roomScenePbsg = getRoomScenePbsg()
  if (!roomScenePbsg) {
    // PERFORMANCE HIT - Temporarily perform App dup detection.
    String roomScenePbsgLabel = "pbsg_${app.getLabel()}"
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
  roomScenePbsg._configRoomScenePbsg()
  return roomScenePbsg
}

void repeaterLedHandler (Event e) {
  // - The field e.deviceId arrives as a number and must be cast toString().
  // - This subscription processes Main Repeater events, which is applicable
  //   to Rooms that leverage an RA2 Main Repeater (virtual) Integration
  //   Button and corresponding (virtual) LED. Work is delegated to
  //   _detectManualOverride()
  if (
       (e.deviceId.toString() == atomicState.roomSceneRepeaterDeviceId)
       && (e.name == "buttonLed-${atomicState.roomSceneRepeaterLED}")
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
  // Abstract
  //   When the Hubitat mode changes AND atomicState.currScenePerVsw == 'AUTOMATIC':
  //     - Identify the appropriate scene for the Hubitat mode.
  //     - Turn on the roomScenePbsg VSW for that scene.
  //   State changes are deferred to _pbsgVswTurnedOnCallback().
  Ltrace(
    'hubitatModeChangeHandler()',
    [
      "At entry,",
      "<b>event name:</b> ${e.name},",
      "<b>event value:</b> ${e.value},",
      "atomicState.currentScene: ${atomicState.currentScene}"
    ].join(' ')
  )
  if (e.name == 'mode' && atomicState.currentScene == 'AUTOMATIC') {
    if (!settings.motionSensor) {
      turnOnRoomSceneVsw (atomicState.roomName, _getSceneForMode(e.value))
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
      String targetScene = atomicState.sceneButtonMap?.getAt(e.deviceId.toString())
                                               ?.getAt(e.value)
      if (!atomicState.roomScenePbsgAppId) {
        Lerror('keypadSceneButtonHandler()', "Cannot find 'atomicState.roomScenePbsgAppId'")
      }
      if (targetScene) {
        String targetVsw = "${atomicState.roomScenePbsgAppId}_${targetScene}"
        Ldebug(
          'keypadSceneButtonHandler()',
          "toggling ${targetVsw}"
        )
        getRoomScenePbsg()._toggleVsw(targetVsw)
      }
      break
    case 'held':
    case 'released':
      // Ignore without logging
      break
    default:
      Lwarn(
        'keypadSceneButtonHandler()',
        "for '${atomicState.roomName}' unexpected event name '${e.name}' for Dni '${e.deviceId}'"
      )
  }
}

void picoButtonHandler (Event e) {
  Integer changePercentage = 10
  if (e.isStateChange == true) {
    switch (e.name) {
      case 'pushed':
        // Check to see if the received button is assigned to a scene.
        String scene = atomicState.picoButtonToTargetScene?.getAt(e.deviceId.toString())
                                                    ?.getAt(e.value)
        if (!atomicState.roomScenePbsgAppId) {
          Lerror('picoButtonHandler()', "Cannot find 'atomicState.roomScenePbsgAppId'")
        }
        String scenePbsg = "${atomicState.roomScenePbsgAppId}_${scene}"
        if (scene) {
          Ldebug(
            'picoButtonHandler()',
            "w/ ${e.deviceId}-${e.value} toggling ${scenePbsg}"
          )
          app.getChildAppByLabel(atomicState.roomScenePbsgAppId)._toggleVsw(scenePbsg)
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
            "R_${atomicState.roomName} picoButtonHandler() w/ ${e.deviceId}-${e.value} no action."
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
      String targetScene = (atomicState.currScenePerVsw == 'AUTOMATIC')
        ? _getSceneForMode() : atomicState.currScenePerVsw
      _activateScene(targetScene)
    } else if (e.value == 'inactive') {
      // Use brute-force to ensure automation is restored when the room is empty.
      atomicState.currScenePerVsw = 'AUTOMATIC'
      _activateScene('Off')
    }
  }
}

//----
//---- EXPECTED APP METHODS
//----

void installed () {
  Ltrace('installed()', 'Calling _roomSceneInitialize()')
  _roomSceneInitialize()
}

void updated () {
  Ltrace('updated()', 'Calling _roomSceneInitialize()')
  app.unsubscribe()  // Suspend event processing to rebuild state variables.
  _roomSceneInitialize()
}

void uninstalled () {
  Ldebug('uninstalled()', 'Calling removeAllChildApps()')
  removeAllChildApps()
}

//----
//---- CUSTOM METHODS
//----

String _getLogLevel() {
  if (!atomicState.logLevel) Lerror('_getLogLevel()', "Missing 'atomicState.logLevel'")
  return atomicState.logLevel
}

List<String> _getRoomScenes () {
  List<String> result = atomicState.scenes
  //->result.each{ scene ->
  //->  Lerror('_getRoomScenes()', "<b>scene:</b> ${scene}")
  //->}
  return result
}

void _activateScene (String scene) {
  // Push Repeater buttons and execute Independent switch/dimmer levels.
  Ldebug('_activateScene()', "scene: <b>${scene}</b>")
  // Values are expected at ...
  //   atomicState.sceneToRepeater[sceneName][dni]
  //   atomicState.sceneToIndependent[sceneName][dni]
  // THIS APPLICATION ALLOWS A SINGLE LUTRON MAIN REPEATER PER ROOM
  atomicState.sceneToRepeater?.getAt(scene)?.each{ repeaterDni, buttonNumber ->
    Ldebug(
      '_activateScene()',
      "${scene}: repeater: ${repeaterDni}, button: ${buttonNumber}"
    )
    // Note: The repeater's Id (not Dni) and button are required to track the scene's
    //       LED on the Main Repeater.
    atomicState.roomSceneRepeaterLED = buttonNumber
    DevW matchedRepeater = settings.mainRepeater?.findAll{ repeater ->
      repeater.getDeviceNetworkId() == repeaterDni
    }?.first() ?: {
      Ldebug(
        '_activateScene()',
        "no repeater w/ Dni: ${repeaterDni}"
      )
    }
    atomicState.roomSceneRepeaterDeviceId = matchedRepeater.getId()
    matchedRepeater.push(buttonNumber)
  }
  atomicState.sceneToIndependent?.getAt(scene)?.each{ deviceDni, level ->
    Ldebug(
      '_activateScene()',
      "${scene}': device: ${deviceDni}, level: ${level}"
    )
    DevW matchedDevice = settings.independentDevices?.findAll{ device ->
      device.getDeviceNetworkId() == deviceDni
    }?.first() ?: {                              // There should be one match by Dni.
      Ldebug(
        '_activateScene()',
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

Boolean _isRoomSceneLedActive () {
  // Fail true if the current room's scenes DO NOT leverage an
  // "RA2 Shared Scene" (via an RA2 Main Repeater Integration Button).
  Boolean retVal = true
  if (!atomicState.roomSceneRepeaterDeviceId) {
    Ldebug(
      '_isRoomSceneLedActive()',
      'No RA2 Shared Scene in use.'
    )
  } else {
    // LEDs will light if (a) they match an explicitly set Room Scene or
    // (b) they match the room's current AUTOMATIC scene. No LEDs should
    // light if the room's scene is MANUAL_OVERRIDE.
    String ledScene = (atomicState.currScenePerVsw == 'AUTOMATIC')
      ? _getSceneForMode()
      : atomicState.currScenePerVsw
    Ldebug(
      '_isRoomSceneLedActive()',
      "ledScene: ${ledScene}"
    )
    Map repeaterData = atomicState.sceneToRepeater?.getAt(ledScene)
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
    "R_${atomicState.roomName} _isRoomSceneLedActive() -> ${retVal}"
  )
  return retVal
}

Boolean _areRoomSceneDevLevelsCorrect () {
  // Fail true if the current room's scenes DO NOT leverage Independent Devices.
  // Note that device level comparisons are made to atomicState.roomScene (and
  // NOT atomicState.currScenePerVsw). When atomicState.currScenePerVsw == MANUAL_OVERRIDE,
  // atomicState.roomScene will retain the critera required to release the OVERRIDE.
  Boolean retVal = true
  if (!atomicState.sceneToIndependent) {
    Ldebug(
      '_areRoomSceneDevLevelsCorrect()',
      "No Independent Devices."
    )
  } else {
    Ldebug(
      '_areRoomSceneDevLevelsCorrect()',
      "sceneToIndependent: ${atomicState.sceneToIndependent}"               // SEEN IN LOGS
    )
    if (!atomicState.roomScene) {
      if (!atomicState.currScenePerVsw) {
        Lerror(_areRoomSceneDevLevelsCorrect, '!!!!! SPECIAL !!!!! atomicState.roomScene IS NOT populated')
      }
    }
    Ldebug(
      '_areRoomSceneDevLevelsCorrect()',
      "atomicState.roomScene: ${atomicState.roomScene}"                     // NOT AVAILABLE
    )
    String restoreScene = (atomicState.roomScene == 'AUTOMATIC')
      ? _getSceneForMode()
      : atomicState.roomScene
    Ldebug(
      '_areRoomSceneDevLevelsCorrect()',
      "restoreScene: ${restoreScene}"
    )
    Map indepDevData = atomicState.sceneToIndependent?.getAt(restoreScene)
    Ldebug(
      '_areRoomSceneDevLevelsCorrect()',
      "indepDevData: ${indepDevData}"
    )
    settings?.independentDevices.each{ dev ->
      Ldebug(
        '_areRoomSceneDevLevelsCorrect()',
        "Dni: ${dev.deviceNetworkId}"
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

Boolean _detectManualOverride () {
  // Turning a PBSG switch on/off that's already on/off WILL NOT generate a
  // change event; so, don't worry about suppressing redundant switch state for now.
  if (!_isRoomSceneLedActive() || !_areRoomSceneDevLevelsCorrect()) {
    InstAppW roomScenePbsg = getRoomScenePbsg()
    if (!atomicState.roomScenePbsgAppId) {
      Lerror('_detectManualOverride()', "Cannot find 'atomicState.roomScenePbsgAppId'")
    }
    roomScenePbsg.turnOnVswExclusivelyByName('MANUAL_OVERRIDE')
  } else {
    roomScenePbsg._turnOffVswByName('MANUAL_OVERRIDE')
  }
}

void _roomSceneInitialize () {
  // Limit creation or update of the Room Scene PBSG instance to this (parent) method.
  getOrCreateRoomScenePbsg()
  Ltrace(
    '_roomSceneInitialize()',
    "R_${atomicState.roomName} subscribing to hubitatModeChangeHandler()"
  )
  app.subscribe(location, "mode", hubitatModeChangeHandler)
  settings.seeTouchKeypads.each{ device ->
    Ltrace(
      '_roomSceneInitialize()',
      "R_${atomicState.roomName} subscribing seeTouchKeypad '${getDeviceInfo(device)}' to keypadSceneButtonHandler()"
    )
    app.subscribe(device, keypadSceneButtonHandler, ['filterEvents': true])
  }
  settings.mainRepeater.each{ device ->
    Ltrace(
      '_roomSceneInitialize()',
      "R_${atomicState.roomName} subscribing to mainRepeater '${getDeviceInfo(device)}' to repeaterLedHandler()"
    )
    app.subscribe(device, repeaterLedHandler, ['filterEvents': true])
  }
  settings.picos.each{ device ->
    Ltrace(
      '_roomSceneInitialize()',
      "R_${atomicState.roomName} subscribing to Pico '${getDeviceInfo(device)}' to picoButtonHandler()"
    )
    app.subscribe(device, picoButtonHandler, ['filterEvents': true])
  }
  settings.motionSensor.each{ device ->
    Ltrace(
      '_roomSceneInitialize()',
      "R_${atomicState.roomName} subscribing to motionSensor '${getDeviceInfo(device)}' to motionSensorHandler()"
    )
    app.subscribe(device, motionSensorHandler, ['filterEvents': true])
  }
  settings.independentDevices.each{ device ->
    Ltrace(
      '_roomSceneInitialize()',
      "R_${atomicState.roomName} subscribing to independentDevice '${getDeviceInfo(device)}' to independentDeviceHandler()"
    )
    app.subscribe(device, independentDeviceHandler, ['filterEvents': true])
  }
  roomScenePbsg._configRoomScenePbsg()
}