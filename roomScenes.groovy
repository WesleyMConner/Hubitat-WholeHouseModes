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
#include wesmc.UtilsLibrary

definition (
  parent: 'wesmc:wha',
  name: 'whaRoom',
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
  page(name: 'whaRoomPage')
}

Map whaRoomPage () {
  // The parent application (Whole House Automation) assigns a unique label
  // to each WHA Rooms instance. Capture app.getLabel() as state.ROOM_NAME.
  return dynamicPage(
    name: 'whaRoomPage',
    title: [
      heading("${app.getLabel()} Scenes<br/>"),
      comment("Click <b>${red('Done')}</b> to enable subscriptions.<br/>"),
      comment('Tab to register changes.')
    ].join(),
    install: true,
    uninstall: true,
    //nextPage: 'whaPage'
  ) {
    // Rename pbsg_modes to ModePbsg
    InstAppW xPbsg = app.getChildAppByLabel("pbsg_${underscoreRoomName}").updateLabel("${cleanRoomName}Pbsg")

    // SAMPLE STATE & SETTINGS CLEAN UP
    //   - app.deleteChildDevice(<INSERT DNI>)
    //   - state.remove('X')
    //   - settings.remove('Y')
    //---------------------------------------------------------------------------------
    // REMOVE NO LONGER USED SETTINGS AND STATE
    //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    settings.remove('log')
    state.remove('ManualOverrideDevice')
    state.remove('PBSGapp')
    state.remove('currentScene')
    state.remove('currentSceneRepeaterDeviceId')
    state.remove('currentSceneRepeaterLED')
    state.remove('inspectScene')
    //?? state.remove('MANUAL_OVERRIDE_VSW_DNI')
    //?? app.deleteChildDevice("${state.ROOM_NAME}_ManualOverride".replaceAll(' ', '_'))
    //---------------------------------------------------------------------------------
    //----PRIOR-REPAIR----> app.updateLabel(app.getLabel().replace(' ', '_'))

    String rsLabel = app.getLabel()
    String newRsLabel = ''
    if (rsLabel == 'Her_Closet') { newRsLabel = 'Hers' }
    else if (rsLabel == 'His_Closet') { newRsLabel = 'His' }
    else {
      List<String> segments = rsLabel.split('_')
      segments.each{ segment -> newRsLabel += segment.toLowerCase().capitalize() }
    }
    if (rsLabel != newRsLabel) {
      paragraph "${rsLabel} -> ${newRsLabel}"
      //app.updateLabel(newRsLabel)
      //paragraph "===>${app.getLabel()}"
    }

    state.ROOM_NAME = app.getLabel()
    state.SCENE_PBSG_APP_NAME = "pbsg_${state.ROOM_NAME.replace(' ', '_')}"
    section {
      solicitLogThreshold()                            // <- provided by Utils
      input(
        name: 'motionSensor',
        title: [
          'motionSensor<br/>',
          comment('Identify one Motion Sensor if desired<br/>.'),
          comment('The Custom Scene "<b>Off</b>" is automatically added below.')
        ].join(),
        type: 'device.LutronMotionSensor',
        submitOnChange: true,
        required: false,
        multiple: false
      )
      selectModeNamesAsSceneNames()
      identifyCustomScenes()
      populateStateScenes()
      selectScenePerMode()
      input(
        name: 'seeTouchKeypads',
        title: [
          'seeTouchKeypads<br/>',
          comment('Authorize Keypads with buttons that activate room scenes.')
        ].join(),
        type: 'device.LutronSeeTouchKeypad',
        submitOnChange: true,
        required: false,
        multiple: true
      )
      input(
        name: 'sceneButtons',
        title: [
          'sceneButtons<br/>',
          comment('Authorize Keypad LEDs/Buttons that activate room scenes.')
        ].join(),
        type: 'device.LutronComponentSwitch',
        submitOnChange: true,
        required: false,
        multiple: true
      )
      if (state.scenes == null || settings?.sceneButtons == null) {
        paragraph(red('Scene activation buttons are pending pre-requisites.'))
      } else {
        identifyLedButtonsForListItems(
          state.scenes,
          settings.sceneButtons,
          'sceneButton'
        )
        populateStateKpadButtons('sceneButton')
        populateStateKpadButtonDniToTargetScene()
      }
      input(
        name: 'picos',
        title: [
          'lutronPicos<br/>',
          comment('Identify Picos with buttons that change the Room scene.')
        ].join(),
        type: 'device.LutronFastPico',
        submitOnChange: true,
        required: false,
        multiple: true
      )
      if (settings.picos == null) {
        paragraph(
          red('Selection of pico buttons to activate scenes is pending pre-requisites.')
        )
      } else {
        selectPicoButtonsForScene(settings.picos)
        populateStatePicoButtonToTargetScene()
      }
      input(
        name: 'mainRepeater',
        title: [
          'mainRepeater<br/>',
          comment('Identify Repeaters that host integration buttons for Room scenes')
        ].join(),
        type: 'device.LutronKeypad',
        submitOnChange: true,
        required: false,
        multiple: false
      )
      input(
        name: 'independentDevices',
        title: [
          'independentDevices<br/>',
          comment('Identify Repeaters that host integration buttons for Room scenes.')
        ].join(),
        type: 'capability.switch',
        submitOnChange: true,
        required: false,
        multiple: true
      )
      if (state.scenes && (settings.independentDevices || settings.mainRepeater)) {
        configureRoomScene()
        populateStateSceneToDeviceValues()
      } else {
        paragraph red('Soliciation of Room scenes is pending pre-requisite data.')
      }
      if (state.scenes == null) {
        paragraph red('Management of child apps is pending selection of Room scenes.')
      } else {
        // Before presenting room drilldown HREFs, prune any d
        //->keepOldestAppObjPerAppLabel([state.SCENE_PBSG_APP_NAME])
        PruneAppDups(
          [state.SCENE_PBSG_APP_NAME],
          false,   // For dups, keep oldest
          app      // The object (parent) pruning dup children
        )

        paragraph heading('Inspect Room PBSG')
        InstAppW roomPBSG = app.getChildAppByLabel(state.SCENE_PBSG_APP_NAME)
        if (!roomPBSG || roomPBSG.getAllChildDevices().size() == 0) {
          roomPBSG = addChildApp('wesmc', 'roomPBSG', 'roomPbsgPage')
        }
        List<String> vswDNIs = [
          *state.scenes, 'AUTOMATIC', 'MANUAL_OVERRIDE'
        ].collect{ scene ->
          "${state.SCENE_PBSG_APP_NAME}_${scene}"
        }
        String defaultSwitchDNI = "${state.SCENE_PBSG_APP_NAME}_AUTOMATIC"
        // Set core instance fields immediately after PBSG instantiation.
        roomPBSG.configPbsg(vswDNIs, defaultSwitchDNI, settings.logThreshold)
        href (
          name: state.SCENE_PBSG_APP_NAME,
          width: 2,
          url: "/installedapp/configure/${roomPBSG.getId()}/roomPbsgPage",
          style: 'internal',
          title: "Edit <b>${AppInfo(roomPBSG)}</b>",
          state: null
        )
      }
      paragraph(
        [
          heading('Debug<br/>'),
          *appStateAsBullets(true),
          *appSettingsAsBullets(true)
        ].join('<br/>')
      )
    }
  }
}

void selectModeNamesAsSceneNames () {
  List<String> sceneNames = getLocation().getModes().collect{ mode -> mode.name }
  input(
    name: 'modeNamesAsSceneNames',
    type: 'enum',
    title: '<span style="margin-left: 10px;">' \
           + emphasis('Select "Mode Names" to use as "Scene Names" <em>(optional)</em>:') \
           + '</span>',
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
  paragraph emphasis('Add Custom Scene Names <em>(optional)</em>:')
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

void solicitNonLutronDevicesForWhaRoom () {
  input(
    name: 'nonLutronDevices',
    title: emphasis('Identify Required Non-Lutron Devices'),
    type: 'enum',
    width: 6,
    options: parent.getNonLutronDevicesForRoom(state.ROOM_NAME).collectEntries{ d ->
      [d, d.displayName]
    },
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void selectScenePerMode () {
  if (state.scenes == null) {
    paragraph red('Mode-to-Scene selection will proceed once scene names exist.')
  } else {
    paragraph emphasis('Select automatic scene per Hubitat mode:')
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
    paragraph(red(
      'Once scene names exist, this section will solicit affiliated pico buttons.'
    ))
  } else {
    List<String> picoScenes = ['AUTOMATIC'] << state.scenes
    picoScenes.flatten().each{ sceneName ->
      input(
          name: "picoButtons_${sceneName}",
          type: 'enum',
          title: emphasis("Pico Buttons activating <b>${sceneName}</b>"),
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
    paragraph red('Identification of Room Scene details selection will proceed once scene names exist.')
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
  //->     "<b>Scene Keys (Start):</b> ${sceneKeysAtStart}<br/>",
  //->     "<b>Scene Keys   (End):</b> ${currentSceneKeys}<br/>",
  //->     "<b>Excess Keys (Diff):</b> ${sceneKeysAtStart.minus(currentSceneKeys)}"
  //->   ].join()
  //-> )
  sceneKeysAtStart.minus(currentSceneKeys).each{ key ->
    Ldebug(
      'configureRoomScene()',
      "removing setting ${key}"
    )
    settings.remove(key)
  }
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
      if (!settings?.motionSensor) activateScene(targetScene)
      break
    default:
      Ldebug(
        'pbsgVswTurnedOnCallback()',
        "processing '${currScene}'"
      )
      if (!settings?.motionSensor) activateScene(currScene)
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

void activateScene (String scene) {
  // Push Repeater buttons and execute Independent switch/dimmer levels.
  Ldebug('activateScene()', "scene: <b>${scene}</b>")
  // Values are expected at ...
  //   state.sceneToRepeater[sceneName][dni]
  //   state.sceneToIndependent[sceneName][dni]
  // THIS APPLICATION ALLOWS A SINGLE LUTRON MAIN REPEATER PER ROOM
  state.sceneToRepeater?.getAt(scene)?.each{ repeaterDni, buttonNumber ->
    Ldebug(
      'activateScene()',
      "${scene}: repeater: ${repeaterDni}, button: ${buttonNumber}"
    )
    // Note: The repeater's Id (not DNI) and button are required to track the scene's
    //       LED on the Main Repeater.
    state.roomSceneRepeaterLED = buttonNumber
    DevW matchedRepeater = settings.mainRepeater?.findAll{ repeater ->
      repeater.getDeviceNetworkId() == repeaterDni
    }?.first() ?: {
      Ldebug(
        'activateScene()',
        "no repeater w/ DNI: ${repeaterDni}"
      )
    }
    state.roomSceneRepeaterDeviceId = matchedRepeater.getId()
    matchedRepeater.push(buttonNumber)
  }
  state.sceneToIndependent?.getAt(scene)?.each{ deviceDni, level ->
    Ldebug(
      'activateScene()',
      "${scene}': device: ${deviceDni}, level: ${level}"
    )
    DevW matchedDevice = settings.independentDevices?.findAll{ device ->
      device.getDeviceNetworkId() == deviceDni
    }?.first() ?: {                              // There should be one match by DNI.
      Ldebug(
        'activateScene()',
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

Boolean isRoomSceneLedActive() {
  // Fail true if the current room's scenes DO NOT leverage an
  // "RA2 Shared Scene" (via an RA2 Main Repeater Integration Button).
  Boolean retVal = true
  if (!state.roomSceneRepeaterDeviceId) {
    Ldebug(
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
    Ldebug(
      'isRoomSceneLedActive()',
      "ledScene: ${ledScene}"
    )
    Map repeaterData = state.sceneToRepeater?.getAt(ledScene)
    if (repeaterData) {
      Ldebug(
        'isRoomSceneLedActive()',
        "repeaterData: ${repeaterData}"
      )
      settings?.mainRepeater.eachWithIndex{ rep, index ->
        String repDni = rep.deviceNetworkId
        Ldebug(
          'isRoomSceneLedActive()',
          "repDni: ${repDni}"
        )
        String associatedButton = repeaterData.getAt(repDni)
        Ldebug(
          'isRoomSceneLedActive()',
          "associatedButton: ${associatedButton}"
        )
        String led = "buttonLed-${associatedButton}"
        Ldebug(
          'isRoomSceneLedActive()',
          "led: ${led}"
        )
        String ledVal = rep.currentValue(led)
        Ldebug(
          'isRoomSceneLedActive()',
          "ledVal: ${ledVal}"
        )
        if (ledVal == 'off') {
          retVal = false
          Ldebug(
            'isRoomSceneLedActive()',
            "retVal: ${retVal}"
          )
          // Note: It is NOT possible to break from a closure.
        }
      }
    }
  }
  Ldebug(
    'isRoomSceneLedActive()',
    "R_${state.ROOM_NAME} isRoomSceneLedActive() -> ${retVal}"
  )
  return retVal
}

Boolean areRoomSceneDevLevelsCorrect() {
  // Fail true if the current room's scenes DO NOT leverage Independent Devices.
  // Note that device level comparisons are made to state.roomScene (and
  // NOT state.currScenePerVsw). When state.currScenePerVsw == MANUAL_OVERRIDE,
  // state.roomScene will retain the critera required to release the OVERRIDE.
  Boolean retVal = true
  if (!state.sceneToIndependent) {
    Ldebug(
      'areRoomSceneDevLevelsCorrect()',
      "No Independent Devices."
    )
  } else {
    Ldebug(
      'areRoomSceneDevLevelsCorrect()',
      "sceneToIndependent: ${state.sceneToIndependent}"               // SEEN IN LOGS
    )
    if (!state.roomScene) {
      if (!state.currScenePerVsw) {
        Lerror(areRoomSceneDevLevelsCorrect, '!!!!! SPECIAL !!!!! state.roomScene IS NOT populated')
      }
    }
    Ldebug(
      'areRoomSceneDevLevelsCorrect()',
      "state.roomScene: ${state.roomScene}"                     // NOT AVAILABLE
    )
    String restoreScene = (state.roomScene == 'AUTOMATIC')
      ? getSceneForMode()
      : state.roomScene
    Ldebug(
      'areRoomSceneDevLevelsCorrect()',
      "restoreScene: ${restoreScene}"
    )
    Map indepDevData = state.sceneToIndependent?.getAt(restoreScene)
    Ldebug(
      'areRoomSceneDevLevelsCorrect()',
      "indepDevData: ${indepDevData}"
    )
    settings?.independentDevices.each{ dev ->
      Ldebug(
        'areRoomSceneDevLevelsCorrect()',
        "DNI: ${dev.deviceNetworkId}"
      )
      Integer devTargetVal = indepDevData?.getAt(dev.deviceNetworkId)
      if (devTargetVal) {
        Ldebug(
          'areRoomSceneDevLevelsCorrect()',
          "devTargetVal: ${devTargetVal}"
        )
        if (dev.hasAttribute('switch')) {
          String expectedSwitch = (devTargetVal == 0) ? 'off' : 'on'
          String actualSwitch = dev.currentValue('switch')
          Ldebug(
            'areRoomSceneDevLevelsCorrect()',
            "switch: expected: ${expectedSwitch}, actual: ${actualSwitch}"
          )
        }
        if (dev.hasAttribute('level')) {
          Integer actualLevel = dev.currentValue('level')
          Ldebug(
            'areRoomSceneDevLevelsCorrect()',
            "level: expected: ${devTargetVal} actual: ${actualLevel}"
          )
        }
      }
    }
  }
  Ldebug(
    'areRoomSceneDevLevelsCorrect()',
    "retVal: ${retVal}"
  )
  return retVal
}

InstAppW getScenePbsg () {
  InstAppW retVal = app.getChildAppByLabel(state.SCENE_PBSG_APP_NAME)
    ?: Lerror(
      'getScenePbsg()',
      "<b>FAILED</b> to locate scenePbsg App"
    )
  return retVal
}

Boolean detectManualOverride() {
  // Turning a PBSG switch on/off that's already on/off WILL NOT generate a
  // change event; so, don't worry about suppressing redundant switch state for now.
  if (!isRoomSceneLedActive() || !areRoomSceneDevLevelsCorrect()) {
    getScenePbsg().turnOnSwitch("${state.SCENE_PBSG_APP_NAME}_MANUAL_OVERRIDE")
  } else {
    getScenePbsg().turnOffSwitch("${state.SCENE_PBSG_APP_NAME}_MANUAL_OVERRIDE")
  }
}

void removeAllChildApps () {
  getAllChildApps().each{ child ->
    Ldebug(
      'removeAllChildApps()',
      "removing ${child.getLabel()} (${child.getId()})"
    )
    deleteChildApp(child.getId())
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
    Ldebug(
      'repeaterLedHandler()',
      'calling detectManualOverride()'
    )
    detectManualOverride()
  }
}

void independentDeviceHandler (Event e) {
  // - This subscription processes Independent Device events. Work is delegated
  //   to detectManualOverride.
  Ldebug(
    'independentDeviceHandler()',
    'calling detectManualOverride()'
  )
  detectManualOverride()
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
    if (!settings?.motionSensor) activateScene(targetScene)
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
        getScenePbsg().toggleSwitch(targetVsw)
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
      activateScene(targetScene)
    } else if (e.value == 'inactive') {
      // Use brute-force to ensure automation is restored when the room is empty.
      state.currScenePerVsw = 'AUTOMATIC'
      activateScene('Off')
    }
  }
}

void installed () {
  Ldebug('installed()', '')
  initialize()
}

void uninstalled () {
  Ldebug('uninstalled()', '')
  removeAllChildApps()
}

void updated () {
  Ldebug('updated()', '')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
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
