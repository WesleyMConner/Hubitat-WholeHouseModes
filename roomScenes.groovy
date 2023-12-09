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

//---- CORE METHODS (External)

//---- CORE METHODS (Internal)

String _getDeviceRa2Id (DevW device) {
  return _extractRa2Id(device.getLabel())
}

String _extractRa2Id (String deviceLabel) {
  return (deviceLabel =~ /\((.*)\)/)[0][1]
}

void _buttonOnCallback (String button) {
  // The RSPbsg child App calls this method to communicate its state changes.
  if (!button) Lwarn('_buttonOnCallback()', 'Called with null argument')
  state.activeButton = button ?: 'AUTOMATIC'
  Ltrace('_buttonOnCallback()', "Button received: ${b(state.activeButton)}")
  state.targetScene = (state.activeButton == 'AUTOMATIC') ? _getSceneForMode() : state.activeButton
  Linfo('_buttonOnCallback()', "Button ${b(button)} -> targetScene: ${b(state.targetScene)}")
  state._isManualOverride = false
  state.moDetected = [:]  // Empty Map indicates NO Manual Override
  // Separate scene activation to accommodate motion / lux activation.
  _activateScene()
}

void _activateScene () {
  // Process the scene's list of device actions.
  state.scenes[state.targetScene].each{ action ->
    def actionT = action.tokenize('^')
    String devType = actionT[0]
    String ra2Id = actionT[1]
    Integer value = SafeParseInt(actionT[2])
    if (value == null) {
      Lerror('_activateScene()', "Null value for ra2Id: ${b(ra2Id)}")
    }
    switch (devType) {
      case 'Ind':
        // Locate the correct independent device and set its on/off/level
        settings.indDevices.each{ d ->
          if (_getDeviceRa2Id(d) == ra2Id) {
            if (d.hasCommand('setLevel')) {
              Ltrace('_buttonOnCallback()', "Setting ${b(ra2Id)} to level ${b(value)}")
              d.setLevel(value)
            } else if (value == 0) {
              Ltrace('_buttonOnCallback()', "Setting ${b(ra2Id)} to off")
              d.off()
            } else if (value == 100) {
              Ltrace('_buttonOnCallback()', "Setting ${b(ra2Id)} to on")
              d.on()
            }
          } else {
            Ltrace(_buttonOnCallback(), "Skipping Independent ra2Id (${b(ra2Id)})")
          }
        }
        break
      case 'Rep':
        // Locate the correct repeater and push its integration button
        settings.mainRepeaters.each{ d ->
          if (_getDeviceRa2Id(d) == ra2Id) {
            Ltrace('_buttonOnCallback()', "Pushing button (${value}) on ${b(ra2Id)}")
            d.push(value)
          } else {
            Ltrace(_buttonOnCallback(), "Skipping Main Repeater rA2Id (${b(ra2Id)})")
          }
        }
        break
      default:
        Lwarn('_buttonOnCallback()', "Unexpected device type ${b(devType)}")
    }
  }
}

Boolean _isIndDeviceMO() {
  //
  // state.scenes?.getAt(state.targetScene)
}

InstAppW _getScenePbsg () {
  InstAppW retVal = app.getChildAppByLabel(state.ROOM_PBSG_LABEL)
    ?: Lerror(
      '_getScenePbsg()',
      "<b>FAILED</b> to locate RSPbsg ${state.ROOM_PBSG_LABEL}"
    )
  return retVal
}

void _updateLutronKpadLeds (String currScene) {
  settings.sceneButtons.each{ d ->
    String buttonDni = d.getDeviceNetworkId()
    String sceneTarget = state.kpadButtonDniToTargetScene[buttonDni]
    if (currScene == sceneTarget) {
      Linfo(
        '_updateLutronKpadLeds()',
        "Turning on LED ${buttonDni} for ${state.ROOM_LABEL} scene ${sceneTarget}"
      )
      ledObj.on()
    } else {
      Ltrace(
        '_updateLutronKpadLeds()',
        "Turning off LED ${buttonDni} for ${state.ROOM_LABEL} scene ${sceneTarget}"
      )
      ledObj.off()
    }
  }
}

String _getSceneForMode (String mode = getLocation().getMode()) {
  String result = settings["modeToScene^${mode}"]
  Ltrace('_getSceneForMode()', "mode: ${b(mode)}, scene: ${b(result)}")
  return result
}

List<String> _getTargetSceneConfigList() {
  return state.scenes?.getAt(state.targetScene)
}

Integer _expectedSceneDeviceValue (String devType, String dni) {
  Integer retVal = null
  String prefix = "${devType}^${dni}^"
  for (encodedConfig in _getTargetSceneConfigList()) {
    if (encodedConfig.startsWith(prefix)) {
      retVal = SafeParseInt(encodedConfig.substring(prefix.size()))
      break
    }
  }
  return retVal
}

Boolean _isManualOverride () {
  return state.moDetected
}

//---- EVENT HANDLERS

void indDeviceHandler (Event e) {
  // Devices send various events (e.g., switch, level, pushed, released).
  // Isolate the events that confirm|refute state.targetScene.
  String ra2Id = null
  Integer currLevel = null
  if (e.name == 'switch') {
    ra2Id = _extractRa2Id(e.displayName)
    if (e.value == 'on') {
      currLevel = 100
    } else if (e.value == 'off') {
      currLevel = 0
    }
  } else if (e.name == 'level') {
    ra2Id = _extractRa2Id(e.displayName)
    currLevel = SafeParseInt(e.value)
  } else {
    return  // Ignore the event
  }
  Integer expLevel = _expectedSceneDeviceValue('Ind', ra2Id)
  if (currLevel == expLevel) {
    // Scene compliance confirmed
    Ltrace('indDeviceHandler()', "${ra2Id} complies with scene")
    state.moDetected.remove(ra2Id)
  } else {
    // Scene compliance refuted (i.e., Manual Override)
    String summary = "${ra2Id} value (${currLevel}), expected (${expLevel})"
    Linfo('indDeviceHandler()', [ 'MANUAL OVERRIDE', summary ])
    state.moDetected[ra2Id] = summary
  }
}

void repLedHandler (Event e) {
  // Main Repeaters send various events (e.g., pushed, buttonLed-##).
  // Isolate the buttonLed-## events which confirm|refute state.targetScene.
  if (e.name.startsWith('buttonLed-')) {
    Integer eventButton = SafeParseInt(e.name.substring(10))
    String ra2Id = _extractRa2Id(e.displayName)
    // Is there an expected sceneButton for the ra2Id?
    Integer sceneButton = _expectedSceneDeviceValue('Rep', ra2Id)
    // And if so, does it match the eventButton?
    if (sceneButton && sceneButton == eventButton) {
      // This event can be used to confirm or refute the target scene.
      if (e.value == 'on') {
        // Scene compliance confirmed
        Ltrace('repLedHandler()', "${ra2Id} complies with scene")
        state.moDetected.remove(ra2Id)
      } else if (e.value == 'off') {
        // Scene compliance refuted (i.e., Manual Override)
        String summary = "${ra2Id} button ${eventButton} off, expected on"
        Linfo('repLedHandler()', [ 'MANUAL OVERRIDE', summary ])
        state.moDetected[ra2Id] = summary
      } else {
        // Error condition
        Lwarn(
          'repLedHandler()',
          "Main Repeater (${ra2Id}) with unexpected value (${e.value}"
        )
      }
    }
  }
}

void hubitatModeHandler (Event e) {
  if (state.activeButton == 'AUTOMATIC') {
    if (settings.motionSensor) {
      Lwarn(
        'hubitatModeHandler()',
        "Ignoring motion sensor ${b(settings.motionSensor)}"
      )
    }
    // Hubitat Mode changes only apply when the room's button is 'AUTOMATIC'.
    if (e.name == 'mode') {
      // Let _buttonOnCallback() handle activeButton == 'AUTOMATIC'!
      Ltrace('hubitatModeHandler()', 'Calling _buttonOnCallback("AUTOMATIC")')
      _buttonOnCallback('AUTOMATIC')
    } else {
      Ltrace('hubitatModeHandler()', ['UNEXPECTED EVENT', EventDetails(e)])
    }
  } else {
    Ltrace(
      'hubitatModeHandler()', [
        'Ignored: Mode Change',
        "state.activeButton: ${b(state.activeButton)}",
        "state.targetScene: ${b(state.targetScene)}"
      ]
    )
  }
}

void kpadSceneButtonHandler (Event e) {
  // Design Note
  //   - The field e.deviceId arrives as a number and must be cast toString().
  //   - Hubitat runs Groovy 2.4. Groovy 3 constructs - x?[]?[] - are not available.
  //   - Kpad buttons are matched to state data to activate a scene.
  switch (e.name) {
    case 'pushed':
      // Toggle the corresponding scene for the keypad button.
      String scene = state.sceneButtonMap?.getAt(e.deviceId.toString())
                                         ?.getAt(e.value)
      if (scene) _getScenePbsg().pbsgActivateButton(scene)
      // The prospective PBSG callback triggers further local processing.
      break
    case 'held':
    case 'released':
      // Ignore without logging
      break
    default:
      Lwarn('kpadSceneButtonHandler()', [
        "DNI: '${b(e.deviceId)}'",
        "For '${state.ROOM_LABEL}' unexpected event name ${b(e.name)}"
      ])
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
        if (scene) {
          Lerror('picoButtonHandler()' [
            '<b>NOT IMPLEMENTED/TESTED</b>',
            "w/ ${e.deviceId}-${e.value} toggling ${scene}"
          ])
          //---> app.getChildAppByLabel(state.ROOM_PBSG_LABEL).toggleSwitch(scenePbsg)
        } else if (e.value == '2') {  // Default "Raise" behavior
          Linfo('picoButtonHandler()', "Raising ${settings.indDevices}")
          settings.indDevices.each{ d ->
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
          Linfo('picoButtonHandler()', "Lowering ${settings.indDevices}")
          settings.indDevices.each{ d ->
              d.setLevel(Math.max(
                (d.currentValue('level') as Integer) - changePercentage,
                0
              ))
          }
        } else {
          Ltrace(
            'picoButtonHandler()',
            "${state.ROOM_LABEL} picoButtonHandler() w/ ${e.deviceId}-${e.value} no action."
          )
        }
        break
    }
  }
}

void motionSensorHandler (Event e) {
  if (e.name == 'motion' && e.isStateChange == true) {
    if (e.value == 'active') {
      String targetScene = (state.targetScene == 'AUTOMATIC')
        ? _getSceneForMode() : state.targetScene
      _buttonOnCallback(targetScene)
    } else if (e.value == 'inactive') {
      // Use brute-force to ensure automation is restored when the room is empty.
      state.targetScene = 'AUTOMATIC'
      _buttonOnCallback('Off')
    }
  }
}

//---- SYSTEM CALLBACKS

void installed () {
  Ltrace('installed()', 'At Entry')
  initialize()
}

void updated () {
  Ltrace('updated()', 'At Entry')
  unsubscribe()  // Suspend all events (e.g., child devices, mode changes).
  initialize()
}

void uninstalled () {
  Lwarn('uninstalled()', 'At Entry')
}

void initialize () {
  Linfo(
    'initialize()',
    "${state.ROOM_LABEL} initialize() of '${state.ROOM_LABEL}'. "
      + "Subscribing to hubitatModeHandler."
  )
  subscribe(location, "mode", hubitatModeHandler)
  settings.seeTouchKpads.each{ device ->
    subscribe(device, kpadSceneButtonHandler, ['filterEvents': true])
  }
  settings.mainRepeaters.each{ device ->
    Linfo(
      'initialize()',
      "${state.ROOM_LABEL} subscribing to Repeater ${DeviceInfo(device)}"
    )
    subscribe(device, repLedHandler, ['filterEvents': true])
  }
  settings.picos.each{ device ->
    Linfo(
      'initialize()',
      "${state.ROOM_LABEL} subscribing to Pico ${DeviceInfo(device)}"
    )
    subscribe(device, picoButtonHandler, ['filterEvents': true])
  }
  settings.motionSensor.each{ device ->
    Linfo(
      'initialize()',
      "${state.ROOM_LABEL} subscribing to Motion Sensor ${DeviceInfo(device)}"
    )
    subscribe(device, motionSensorHandler, ['filterEvents': true])
  }
  settings.indDevices.each{ device ->
    Linfo(
      'initialize()',
      "${state.ROOM_LABEL} subscribing to independentDevice ${DeviceInfo(device)}"
    )
    subscribe(device, indDeviceHandler, ['filterEvents': true])
  }
}

//---- GUI / PAGE RENDERING

void _idMotionSensor () {
  input(
    name: 'motionSensor',
    title: Heading3("Identify Room Motion Sensors"),
    type: 'device.LutronMotionSensor',
    submitOnChange: true,
    required: false,
    multiple: false
  )
}

void _selectModesAsScenes () {
  List<String> scenes = ModeNames()
  input(
    name: 'modesAsScenes',
    type: 'enum',
    title: Heading2('Select Mode Names to use as Scenes Names'),
    submitOnChange: true,
    required: false,
    multiple: true,
    options: scenes.sort()
  )
}

void _nameCustomScenes () {
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
  custom.each{ key, value ->
    input(
      name: key,
      type: 'text',
      title: Heading2('Custom Scene Name (Optional)'),
      width: 4,
      submitOnChange: true,
      required: false,
      defaultValue: value
    )
  }
}

void _populateStateScenesKeysOnly () {
  List<String> scenes = settings.modesAsScenes ?: []
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
  state.scenes = scenes?.collectEntries{ [(it): []] }
}

List<String> _getScenes() { state.scenes.collect{ it.key } }

Boolean _sceneExists (String scene) {
  return state.scenes.collect{ it.key }.contains(scene)
}

void _idSceneForMode () {
  if (state.scenes == null) {
    paragraph 'Mode-to-Scene selection will proceed once scene names exist.'
  } else {
    paragraph Heading2('Identify a Scene for each Hubitat Mode')
    getLocation().getModes().collect{mode -> mode.name}.each{ modeName ->
      String inputName = "modeToScene^${modeName}"
      String defaultValue = settings[inputName]
        ?: _sceneExists(modeName) ? modeName : null
      input(
        name: inputName,
        type: 'enum',
        title: Heading3(modeName),
        width: 2,
        submitOnChange: true,
        required: true,
        multiple: false,
        options: _getScenes(),
        defaultValue: defaultValue
      )
    }
  }
}

void _authSeeTouchKpads () {
  input(
    name: 'seeTouchKpads',
    title: Heading3("Identify Keypads used to Activate Room Scenes"),
    type: 'device.LutronSeeTouchKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _idRoomSceneButtons () {
  input(
    name: 'sceneButtons',
    title: Heading3("Identify Keypad Buttons that Activate a Room Scene"),
    type: 'device.LutronComponentSwitch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _mapKpadButtonDniToScene () {
  Map<String, String> result = [:]
  state.sceneButtonMap.collect{ kpadDni, buttonMap ->
    buttonMap.each{ buttonNumber, targetScene ->
      result["${kpadDni}-${buttonNumber}"] = targetScene
    }
  }
  state.kpadButtonDniToTargetScene = result
}

void _wireKpadButtonsToScenes () {
  if (_getScenes() == null || settings?.sceneButtons == null) {
    //paragraph('No Room Scene Kpad buttons have been identified.')
  } else {
    identifyLedButtonsForListItems(
      _getScenes(),
      settings.sceneButtons,
      'sceneButton'
    )
    populateStateKpadButtons('sceneButton')
    _mapKpadButtonDniToScene()
  }
}

void _populateStateScenesAssignValues () {
  // Process settings (e.g., "scene^Night^Ind^Ra2D-59-1848") into state.
  settings.findAll{ it.key.startsWith('scene^') }.each{ key, value ->
  Integer v = value
    List<String> keyT = key.tokenize('^')
    String scene = keyT[1]
    String devType = keyT[2]
    String dni = keyT[3]
    if (!state.scenes[scene]) state.scenes[scene] = []
    state.scenes[scene] += "${devType}^${dni}^${value}"
  }
}

void _authRoomScenesPicos () {
  input(
    name: 'picos',
    title: Heading3("Identify Picos that Trigger or Adjust Room Scenes"),
    type: 'device.LutronFastPico',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _wirePicoButtonsToScenes () {
  if (settings.picos == null) {
    //paragraph('Selection of pico buttons to activate scenes is pending pre-requisites.')
  } else {
    selectPicoButtonsForScene(settings.picos)
    Lerror('_wirePicoButtonsToScenes()', [
      '<b>NOT IMPLEMENTED</b>',
      "Debug missing populateStatePicoButtonToTargetScene()"
    ])
  }
}

void _idMainRepeatersImplementingScenes () {
  input(
    name: 'mainRepeaters',
    title: Heading3("Identify Main Repeaters with Integration Buttons for Room Scenes"),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _idIndDevices () {
  input(
    name: 'indDevices',
    title: Heading3("Identify the Room's Non-RA2 Devices (e.g., Lutron Caséta, Z-Wave)"),
    type: 'capability.switch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _configureRoomScene () {
  // DESIGN NOTES
  //   There are three steps to populate "state.scenes" map.
  //   (1) _populateStateScenesKeysOnly() creates a per-scene key and
  //       sets the value to [].
  //   (2) This method populates Settings keys "scene^SCENENAME^Ind|Rep^DNI".
  //       with Integer values:
  //         - 'light level' for Independent devices (Ind)
  //         - 'virtual button number' for Main Repeaters (Rep)
  //   (3) _populateStateScenesAssignValues() harvests the settings from
  //       Step 2 to complete the "state.scenes" map.
  // VIRTUAL TABLE
  //   Hubitat page display logic simulates table cells.
  //     - Full-sized displays (computer monitors) are 12 cells wide.
  //     - Phone-sized displays are 4 cells wide.
  //   To ensure that each scene starts on a new row, this method adds
  //   empty cells (modulo 12) to ensure each scene begins in column 1.
  _populateStateScenesKeysOnly()
  if (state.scenes) {
    List<String> currSettingsKeys = []
    state.scenes?.each{ scene, EMPTY_LIST ->
      // Ignore the current componentList. Rebuilt it from scratch.
      Integer tableCol = 2
      paragraph("<br/><b>${scene} →</b>", width: 2)
      settings.indDevices?.each{ d ->
        String inputName = "scene^${scene}^Ind^${_getDeviceRa2Id(d)}"
        currSettingsKeys += inputName
        tableCol += 2
        input(
          name: inputName,
          type: 'number',
          title: "${b(d.getLabel())}<br/>Level 0..100",
          width: 2,
          submitOnChange: true,
          required: false,
          multiple: false,
          defaultValue: 0
        )
      }
      settings.mainRepeaters?.each{d ->
        String inputName = "scene^${scene}^Rep^${_getDeviceRa2Id(d)}"
        currSettingsKeys += inputName
        tableCol += 2
        input(
          name: inputName,
          type: 'number',
          title: "${b(d.getLabel())}<br/>Button #",
          width: 2,
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
    // Prune stale Settings keys
    settings.findAll{ it.key.startsWith('scene^') }.each{ key, value ->
      if (!currSettingsKeys.contains(key)) {
        Lwarn(
          '_configureRoomScene()',
          "Removing stale setting, ${key} -> ${value}"
        )
        settings.remove(key)
      }
    }
  }
}

void _solicitRoomScenes () {
  if (_getScenes() && (settings.indDevices || settings.mainRepeaters)) {
    _configureRoomScene()
    _populateStateScenesAssignValues()
  } else {
    // paragraph 'Soliciation of Room scenes is pending pre-requisite data.'
  }
}

void _createRSPbsgAndPageLink () {
  if (!_getScenes()) {
    Lwarn(
      '_createRSPbsgAndPageLink()',
      'PBSG creation is pending identification of Room Scenes'
    )
  } else {
    InstAppW pbsgApp = app.getChildAppByLabel(state.ROOM_PBSG_LABEL)
    if (!pbsgApp) {
      Lwarn(
        '_createRSPbsgAndPageLink()',
        "Adding Room Scene PBSG ${state.ROOM_PBSG_LABEL}"
      )
      pbsgApp = addChildApp('wesmc', 'RSPbsg', state.ROOM_PBSG_LABEL)
    }
    List<String> roomScenes = [ *_getScenes(), 'AUTOMATIC' ]
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
    href(
      name: AppInfo(pbsgApp),
      width: 2,
      url: "/installedapp/configure/${pbsgApp.getId()}/MPbsgPage",
      style: 'internal',
      title: "Edit <b>${AppInfo(pbsgApp)}</b>",
      state: null
    )
  }
}

Map RoomScenesPage () {
  // The parent application (Whole House Automation) assigns a unique label
  // to each WHA Rooms instance. Capture app.getLabel() as state.ROOM_LABEL.
  return dynamicPage(
    name: 'RoomScenesPage',
    title: [
      Heading1("${app.getLabel()} Scenes"),
      Bullet1('Tab to register changes.'),
      Bullet1('Click <b>Done</b> to enable subscriptions.')
    ].join('<br/>'),
    install: true,
    uninstall: true,
  ) {
    //---------------------------------------------------------------------------------
    // REMOVE NO LONGER USED SETTINGS AND STATE
    //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    //-> settings.remove('..')
    //-> state.remove('..')
    //---------------------------------------------------------------------------------
    state.ROOM_LABEL = app.getLabel()  // WHA creates App w/ Label == Room Name
    state.ROOM_PBSG_LABEL = "${state.ROOM_LABEL}Pbsg"
    section {
      solicitLogThreshold('appLogThresh', 'TRACE')           // lHUI
      solicitLogThreshold('pbsgLogThresh', 'INFO')           // lHUI
      _idMotionSensor()
      _selectModesAsScenes()
      _nameCustomScenes()
      _populateStateScenesKeysOnly()
      _idSceneForMode()
      _authSeeTouchKpads()
      _idRoomSceneButtons()
      _wireKpadButtonsToScenes()
      _authRoomScenesPicos()
      _wirePicoButtonsToScenes()
      _idMainRepeatersImplementingScenes()
      _idIndDevices()
      _solicitRoomScenes()
      _createRSPbsgAndPageLink()
      PruneAppDups(
        [state.ROOM_PBSG_LABEL],   // App Labels to keep
        false,                     // For dups, keep oldest
        app                        // Prune children of this app
      )
      /*
      href (
        name: state.ROOM_PBSG_LABEL,
        width: 2,
        url: "/installedapp/configure/${rsPbsg.getId()}",
        style: 'internal',
        title: "Edit <b>${AppInfo(rsPbsg)}</b>",
        state: null
      )
      */
      paragraph([
        Heading1('Debug'),
        *appStateAsBullets(true),
        *appSettingsAsBullets(true)
      ].join('<br/>'))
    }
  }
}
