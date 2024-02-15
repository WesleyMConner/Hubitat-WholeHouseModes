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
// The Groovy Linter generates false positives on Hubitat #include !!!
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

String _extractRa2IdFromLabel (String deviceLabel) {
  return (deviceLabel =~ /\((.*)\)/)[0][1]
}

String _getDeviceRa2Id (DevW device) {
  return _extractRa2IdFromLabel(device.label)
}

void _clearManualOverride () {
  state.moDetected = [:]
}

Boolean _isManualOverride () {
  return state.moDetected
}

Boolean _isRoomOccupied () {
  // Sensors denote occupancy by entering their label in the roomOccupied
  // List<String>. In the absence of a sensor, roomOccupied = [ true ]
  return (state.roomOccupied)
}

void _activateScene () {
  // Responsibilities:
  //   - Enforce _isRoomOccupied() behavior - activating targetScene or INATIVE scene
  //-> if (state.targetScene == 'AUTOMATIC') {
  //->   logError('_activateScene', 'targetScene == AUTOMATIC ===> I N V A L I D')
  //-> } else if (state.targetScene == null) {
  //->   logError('_activateScene', 'targetScene == null ===> I N V A L I D')
  //-> }
  String executeScene = _isRoomOccupied() ? state.targetScene : 'INACTIVE'
  logInfo('_activateScene', "Activating scene: ${executeScene}")
  // Process the scene's list of device actions.
  state.scenes[executeScene].each{ action ->
    def actionT = action.tokenize('^')
    String devType = actionT[0]
    String ra2Id = actionT[1]
    Integer value = safeParseInt(actionT[2])
    if (value == null) {
      logError('_activateScene', "Null value for ra2Id: ${b(ra2Id)}")
    }
    switch (devType) {
      case 'Ind':
        settings.indDevices.each{ d ->
          if (_getDeviceRa2Id(d) == ra2Id) {
            // Independent Devices (especially RA2 and Caséta) are subject
            // to stale Hubitat state data if callbacks occur quickly (within
            // 1/2 second) after a level change. So, briefly unsubscribe
            // device (see runIn subscribe below) to avoid this situation.
            _unsubscribeIndDevToHandler(d)
            if (d.hasCommand('setLevel')) {
              // Some devices cannot support level=100
              if (value == 100) value = 99
              logTrace('_activateScene', "Setting ${b(ra2Id)} to level ${b(value)}")
              d.setLevel(value)
            } else if (value == 0) {
              logTrace('_activateScene', "Setting ${b(ra2Id)} to off")
              d.off()
            } else if (value == 100) {
              logTrace('_activateScene', "Setting ${b(ra2Id)} to on")
              d.on()
            }
            runIn(1, '_subscribeIndDevToHandler', [data: [device: d]])
          } else {
            logTrace('_activateScene', "Skipping Independent ra2Id (${b(ra2Id)})")
          }
        }
        break
      case 'Rep':
        settings.mainRepeaters.each{ d ->
          if (_getDeviceRa2Id(d) == ra2Id) {
            // Unlike some Independent Devices (RA2 and Caséta) RA2 Main
            // Repeaters are not particularly subject to stale Hubitat
            // state; HOWEVER, callbacks that occur quickly (within 1/2
            // second) after a buton press subject Hubitat to callback
            // overload (during WHA scene chantes). Briefly unsubscribe /
            // subscribe to avoid this situation.
            _unsubscribeMainRepToHandler(d)
            logTrace('_activateScene', "Pushing button (${value}) on ${b(ra2Id)}")
            d.push(value)
            runIn(1, '_subscribeIndDevToHandler', [data: [device: d]])
          } else {
            logTrace('_activateScene', "Skipping Main Repeater rA2Id (${b(ra2Id)})")
          }
        }
        break
      default:
        logWarn('_activateScene', "Unexpected device type ${b(devType)}")
    }
  }
}

void _updateTargetScene () {
  // Upstream Pbsg/Dashboard/Alexa actions should clear Manual Overrides
  logTrace('_updateTargetScene', [
    'At entry',
    "state.activeButton: ${b(state.activeButton)}",
    "state.targetScene: ${b(state.targetScene)}",
    "_isManualOverride(): ${b(_isManualOverride())}"
  ])
  if (
    (state.activeButton == 'AUTOMATIC' && !state.targetScene)
    || (state.activeButton == 'AUTOMATIC' && !_isManualOverride())
  ) {
    // Ensure that targetScene is per the latest Hubitat mode.
    String mode = getLocation().getMode()
    state.targetScene = settings["modeToScene^${mode}"]
  } else {
    state.targetScene = state.activeButton
  }
  logTrace('_updateTargetScene', [
    'At exit',
    "state.targetScene: ${b(state.targetScene)}"
  ])
}

void buttonOnCallback (String button) {
  // Pbsg/Dashboard/Alexa actions override Manual Overrides.
  // Scene activation enforces room occupancy.
  if (!button) {
    logWarn(
      'buttonOnCallback',
      'A null argument was received, using AUTOMATIC as a default'
    )
  }
  state.activeButton = button ?: 'AUTOMATIC'
  logTrace(
    'buttonOnCallback',
    "Button ${b(button)} -> state.activeButton: ${b(state.activeButton)}")
  _clearManualOverride()
  _updateTargetScene()
  _activateScene()
  _updateLutronKpadLeds()
}

// currScene -> state.targetScene
void _updateLutronKpadLeds () {  // old argument was "String currScene"
  settings.sceneButtons.each{ d ->
    String buttonDni = d.getDeviceNetworkId()
    String sceneTarget = state.kpadButtonDniToTargetScene[buttonDni]
    logInfo('_updateLutronKpadLeds', [
      "state.targetScene: ${b(state.targetScene)}",
      "buttonDni: ${b(buttonDni)}",
      "sceneTarget: ${b(sceneTarget)}"
    ])
    if (state.targetScene == sceneTarget) {
      logInfo(
        '_updateLutronKpadLeds',
        "Turning on LED ${buttonDni} for ${state.ROOM_LABEL} scene ${sceneTarget}"
      )
      d.on()
    } else {
      logTrace(
        '_updateLutronKpadLeds',
        "Turning off LED ${buttonDni} for ${state.ROOM_LABEL} scene ${sceneTarget}"
      )
      d.off()
    }
  }
}

List<String> _getTargetSceneConfigList() {
  return state.scenes?.getAt(state.targetScene)
}

Integer _expectedSceneDeviceValue (String devType, String dni) {
  Integer retVal = null
  String prefix = "${devType}^${dni}^"
  for (encodedConfig in _getTargetSceneConfigList()) {
    if (encodedConfig.startsWith(prefix)) {
      retVal = safeParseInt(encodedConfig.substring(prefix.size()))
      break
    }
  }
  return retVal
}

List<String> _getScenes() {
  state.scenes.collect{ it.key }
}

InstAppW _getOrCreateRSPbsg () {
  // The PBSG is created by _createRSPbsgAndPageLink()
  InstAppW pbsgApp = app.getChildAppByLabel(state.RSPBSG_LABEL)
  if (!pbsgApp) {
    if (_getScenes()) {
      logWarn('_getOrCreateRSPbsg', "Adding RSPbsg ${state.RSPBSG_LABEL}")
      pbsgApp = addChildApp('wesmc', 'RSPbsg', state.RSPBSG_LABEL)
      List<String> roomScenes = [ *_getScenes(), 'AUTOMATIC' ]
      roomScenes.removeAll{ it == 'INACTIVE' }
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
    } else {
      logWarn('_getOrCreateRSPbsg', 'RSPbsg creation is pending room scenes')
    }
  }
  return pbsgApp
}

void _createRSPbsgAndPageLink () {
  InstAppW pbsgApp = _getOrCreateRSPbsg()
  if (pbsgApp) {
    href(
      name: appInfo(pbsgApp),
      width: 2,
      url: "/installedapp/configure/${pbsgApp.id}/RSPbsgPage",
      style: 'internal',
      title: "Edit <b>${appInfo(pbsgApp)}</b>",
      state: null
    )
  } else {
    paragraph "Creation of the MPbsgHref is pending required data."
  }
}

void addMotionSensorToRoomOccupied (String displayName) {
  state.roomOccupied = cleanStrings([*state.roomOccupied, displayName])
}

void removeMotionSensorFromRoomOccupied (String displayName) {
  state.roomOccupied?.removeAll{ it == displayName }
}

void _subscribeToIndDeviceHandlerNoDelay () {
  settings.indDevices.each{ d ->
    logInfo(
      '_subscribeToIndDeviceHandlerNoDelay',
      "${state.ROOM_LABEL} subscribing to independentDevice ${deviceInfo(d)}"
    )
    subscribe(d, indDeviceHandler, ['filterEvents': true])
  }
}

void _subscribeIndDevToHandler (Map data) {
  // USAGE:
  //   runIn(1, '_subscribeIndDevToHandler', [data: [device: d]])
  // Independent Devices (especially RA2 and Caséta) are subject to stale
  // Hubitat state data if callbacks occur quickly (within 1/2 second)
  // after a level change. So, briefly unsubscribe/subscribe to avoid
  // this situation.
  logTrace(
    '_subscribeIndDevToHandler',
    "${state.ROOM_LABEL} subscribing ${deviceInfo(data.device)}"
  )
  subscribe(device, indDeviceHandler, ['filterEvents': true])
}

void _unsubscribeIndDevToHandler (DevW device) {
  // Independent Devices (especially RA2 and Caséta) are subject to stale
  // Hubitat state data if callbacks occur quickly (within 1/2 second)
  // after a level change. So, briefly unsubscribe/subscribe to avoid
  // this situation.
  logTrace(
    '_unsubscribeToIndDeviceHandler',
    "${state.ROOM_LABEL} unsubscribing ${deviceInfo(device)}"
  )
  unsubscribe(device)
}

void _subscribeToKpadHandler () {
  settings.seeTouchKpads.each{ d ->
    logInfo(
      '_subscribeToKpadHandler',
      "${state.ROOM_LABEL} subscribing to keypad ${deviceInfo(d)}"
    )
    subscribe(d, kpadHandler, ['filterEvents': true])
  }
}

void _subscribeToMainRepHandler () {
  settings.mainRepeaters.each{ d ->
    logInfo(
      '_subscribeToMainRepHandler',
      "${state.ROOM_LABEL} subscribing to Repeater ${deviceInfo(d)}"
    )
    subscribe(d, mainRepHandler, ['filterEvents': true])
  }
}

void _subscribeMainRepToHandler (Map data) {
  // USAGE:
  //   runIn(1, '_subscribeMainRepToHandler', [data: [device: d]])
  // Unlike some Independent Devices (RA2 and Caséta) RA2 Main Repeaters
  // are not particularly subject to stale Hubitat state; HOWEVER,
  // callbacks that occur quickly (within 1/2 second) after a buton press
  // subject Hubitat to callback overload (during WHA scene chantes).
  // Briefly unsubscribe/subscribe to avoid this situation.
  logTrace(
    '_subscribeMainRepToHandler',
    "${state.ROOM_LABEL} subscribing ${deviceInfo(data.device)}"
  )
  subscribe(device, mainRepHandler, ['filterEvents': true])
}

void _unsubscribeMainRepToHandler (DevW device) {
  // Unlike some Independent Devices (RA2 and Caséta) RA2 Main Repeaters
  // are not particularly subject to stale Hubitat state; HOWEVER,
  // callbacks that occur quickly (within 1/2 second) after a buton press
  // subject Hubitat to callback overload (during WHA scene chantes).
  // Briefly unsubscribe/subscribe to avoid this situation.
  logTrace(
    '_unsubscribeMainRepToHandler',
    "${state.ROOM_LABEL} unsubscribing ${deviceInfo(device)}"
  )
  unsubscribe(device)
}

void _subscribeToModeHandler () {
  logInfo(
    '_subscribeToModeHandler',
    "${state.ROOM_LABEL} subscribing to location 'mode'"
  )
  subscribe(location, "mode", modeHandler)
}

void _subscribeToMotionSensorHandler () {
  if (settings.motionSensors) {
    state.roomOccupied = []
    settings.motionSensors.each{ d ->
      logInfo(
        'initialize',
        "${state.ROOM_LABEL} subscribing to Motion Sensor ${deviceInfo(d)}"
      )
      subscribe(d, motionSensorHandler, ['filterEvents': true])
      if (d.latestState('motion').value == 'active') {
        addMotionSensorToRoomOccupied (d.label)
      } else {
        removeMotionSensorFromRoomOccupied(d.label)
      }
    }
  } else {
    state.roomOccupied = [ true ]
  }
}

void _subscribeToPicoHandler () {
  settings.picos.each{ d ->
    logInfo(
      'initialize',
      "${state.ROOM_LABEL} subscribing to Pico ${deviceInfo(d)}"
    )
    subscribe(d, picoHandler, ['filterEvents': true])
  }
}

//---- EVENT HANDLERS

void indDeviceHandler (Event e) {
  // Devices send various events (e.g., switch, level, pushed, released).
  // Isolate the events that confirm|refute state.targetScene.
  String ra2Id = null
  Integer currLevel = null
  if (e.name == 'switch') {
    ra2Id = _extractRa2IdFromLabel(e.displayName)
    if (e.value == 'on') {
      currLevel = 100
    } else if (e.value == 'off') {
      currLevel = 0
    }
  } else if (e.name == 'level') {
    ra2Id = _extractRa2IdFromLabel(e.displayName)
    currLevel = safeParseInt(e.value)
  } else {
    return  // Ignore the event
  }
  Integer expLevel = _expectedSceneDeviceValue('Ind', ra2Id)
  if (currLevel == expLevel) {
    // Scene compliance confirmed
    logTrace('indDeviceHandler', "${ra2Id} complies with scene")
    state.moDetected.remove(ra2Id)
  } else {
    // Scene compliance refuted (i.e., Manual Override)
    String summary = "${ra2Id} value (${currLevel}), expected (${expLevel})"
    logInfo('indDeviceHandler', [ 'MANUAL OVERRIDE', summary ])
    state.moDetected[ra2Id] = summary
  }
}

void kpadHandler (Event e) {
  // Design Note
  //   - The field e.deviceId arrives as a number and must be cast toString().
  //   - Hubitat runs Groovy 2.4. Groovy 3 constructs - x?[]?[] - are not available.
  //   - Kpad buttons are matched to state data to activate a scene.
  logTrace('kpadHandler', [
    "state.activeButton: ${state.activeButton}",
    "state.targetScene: ${state.targetScene}",
    eventDetails(e)
  ])
  switch (e.name) {
    case 'pushed':
      // Toggle the corresponding scene for the keypad button.
      String scene = state.sceneButtonMap?.getAt(e.deviceId.toString())
                                         ?.getAt(e.value)
      if (scene) _getOrCreateRSPbsg().pbsgToggleButton(scene)
      // The prospective PBSG callback triggers further local processing.
      break
    case 'held':
    case 'released':
      // Ignore without logging
      break
    default:
      logWarn('kpadHandler', [
        "DNI: '${b(e.deviceId)}'",
        "For '${state.ROOM_LABEL}' unexpected event name ${b(e.name)}"
      ])
  }
}

void mainRepHandler (Event e) {
  // Main Repeaters send various events (e.g., pushed, buttonLed-##).
  // Isolate the buttonLed-## events which confirm|refute state.targetScene.
  if (e.name.startsWith('buttonLed-')) {
    Integer eventButton = safeParseInt(e.name.substring(10))
    String ra2Id = _extractRa2IdFromLabel(e.displayName)
    // Is there an expected sceneButton for the ra2Id?
    Integer sceneButton = _expectedSceneDeviceValue('Rep', ra2Id)
    // And if so, does it match the eventButton?
    if (sceneButton && sceneButton == eventButton) {
      // This event can be used to confirm or refute the target scene.
      if (e.value == 'on') {
        // Scene compliance confirmed
        logTrace('mainRepHandler', "${ra2Id} complies with scene")
        state.moDetected.remove(ra2Id)
      } else if (e.value == 'off') {
        // Scene compliance refuted (i.e., Manual Override)
        String summary = "${ra2Id} button ${eventButton} off, expected on"
        logInfo('mainRepHandler', [ 'MANUAL OVERRIDE', summary ])
        state.moDetected[ra2Id] = summary
      } else {
        // Error condition
        logWarn(
          'mainRepHandler',
          "Main Repeater (${ra2Id}) with unexpected value (${e.value}"
        )
      }
    }
  }
}

void modeHandler (Event e) {
  if (state.activeButton == 'AUTOMATIC') {
    // Hubitat Mode changes only apply when the room's button is 'AUTOMATIC'.
    if (e.name == 'mode') {
      // Let buttonOnCallback() handle activeButton == 'AUTOMATIC'!
      logTrace('modeHandler', 'Calling buttonOnCallback("AUTOMATIC")')
      buttonOnCallback('AUTOMATIC')
    } else {
      logTrace('modeHandler', ['UNEXPECTED EVENT', eventDetails(e)])
    }
  } else {
    logTrace(
      'modeHandler', [
        'Ignored: Mode Change',
        "state.activeButton: ${b(state.activeButton)}",
        "state.targetScene: ${b(state.targetScene)}"
      ]
    )
  }
}

void motionSensorHandler (Event e) {
  // It IS POSSIBLE to have multiple motion sensors per room.
  //-> logTrace('motionSensorHandler', eventDetails(e))
  if (e.name == 'motion') {               // Assume e.isStateChange == true
    if (e.value == 'active') {
      logInfo('motionSensorHandler', "${e.displayName} is active")
      addMotionSensorToRoomOccupied(e.displayName)
      _activateScene()
    } else if (e.value == 'inactive') {
      logInfo('motionSensorHandler', "${e.displayName} is inactive")
      removeMotionSensorFromRoomOccupied(e.displayName)
      _activateScene()
    } else {
      logError('motionSensorHandler', "Unexpected event value (${e.value})")
    }
  }
}

void picoHandler (Event e) {
  Integer changePercentage = 10
  if (e.isStateChange == true) {
    switch (e.name) {
      case 'pushed':
//-> logTrace('picoHander', [
//->   "e.deviceId.toString(): ${b(e.deviceId.toString())}",
//->   "e.value: ${b(e.value.toString())}",
//-> ])
        // Check to see if the received button is assigned to a scene.
        String scene = state.picoButtonToTargetScene?.getAt(e.deviceId.toString())
                                                    ?.getAt(e.value.toString())
        if (scene) {
          logDebug('picoHandler', [
            "w/ ${e.deviceId.toString()}-${e.value} toggling ${scene}"
          ])
          _getOrCreateRSPbsg().pbsgToggleButton(scene)
        } else if (e.value == '2') {  // Default "Raise" behavior
          logInfo('picoHandler', "Raising ${settings.indDevices}")
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
          logInfo('picoHandler', "Lowering ${settings.indDevices}")
          settings.indDevices.each{ d ->
logInfo('picoHandler #565', [
  "d.getDeviceNetworkId(): ${b(d.getDeviceNetworkId())}",
  "d.currentValue('level'): ${b(d.currentValue('level'))}"
])
              d.setLevel(Math.max(
                (d.currentValue('level') as Integer) - changePercentage,
                0
              ))
logInfo('picoHandler #573', [
  "d.getDeviceNetworkId(): ${b(d.getDeviceNetworkId())}",
  "d.currentValue('level'): ${b(d.currentValue('level'))}"
])
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

void installed () {
  logTrace('installed', 'At Entry')
  initialize()
}

void updated () {
  logTrace('updated', 'At Entry')
  unsubscribe()  // Suspend all events (e.g., child devices, mode changes).
  initialize()
}

void uninstalled () {
  logWarn('uninstalled', 'At Entry')
}

void initialize () {
  logInfo(
    'initialize',
    "${state.ROOM_LABEL} initialize() of '${state.ROOM_LABEL}'. "
      + "Subscribing to modeHandler."
  )
  _subscribeToIndDeviceHandlerNoDelay()
  _subscribeToKpadHandler()
  _subscribeToMainRepHandler()
  _subscribeToModeHandler()
  _subscribeToMotionSensorHandler()
  _subscribeToPicoHandler()
  // ACTIVATION
  //   - If AUTOMATIC is already active in the PBSG, buttonOnCallback()
  //     will not be called.
  //   - It is better to include a redundant call here than to miss
  //     proper room activation on initialization.
  InstAppW pbsg = _getOrCreateRSPbsg()
  if (pbsg) {
    pbsg.pbsgActivateButton('AUTOMATIC')
    buttonOnCallback('AUTOMATIC')
  } else {
    logWarn(
      'initialize',
      'The RSPbsg is pending additional configuration data.'
    )
  }
}

//---- GUI / PAGE RENDERING

void _idMotionSensors () {
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

void _selectModesAsScenes () {
  List<String> scenes = modeNames()
  input(
    name: 'modesAsScenes',
    type: 'enum',
    title: heading2('Select Mode Names to use as Scenes Names'),
    submitOnChange: true,
    required: false,
    multiple: true,
    options: scenes.sort()
  )
}

void _nameCustomScenes () {
  String prefix = 'customScene'
  LinkedHashMap<String, String> slots = [
    "${prefix}1": settings.motionSensors ? 'INACTIVE' : settings["${prefix}1"],
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
      title: heading2('Custom Scene Name (Optional)'),
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

Boolean _sceneExists (String scene) {
  return state.scenes.collect{ it.key }.contains(scene)
}

void _idSceneForMode () {
  if (state.scenes == null) {
    paragraph 'Mode-to-Scene selection will proceed once scene names exist.'
  } else {
    paragraph heading2('Identify a Scene for each Hubitat Mode')
    getLocation().getModes().collect{mode -> mode.name}.each{ modeName ->
      String inputName = "modeToScene^${modeName}"
      String defaultValue = settings[inputName]
        ?: _sceneExists(modeName) ? modeName : null
      input(
        name: inputName,
        type: 'enum',
        title: heading3(modeName),
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
    title: heading3("Identify Keypads used to Activate Room Scenes"),
    type: 'device.LutronSeeTouchKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _idRoomSceneButtons () {
  input(
    name: 'sceneButtons',
    title: heading3("Identify Keypad Buttons that Activate a Room Scene"),
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
    title: heading3("Identify Picos that Trigger or Adjust Room Scenes"),
    type: 'device.LutronFastPico',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

Map<String,String> namePicoButtons (DevW pico) {
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

Map<String, String> picoButtonPicklist (List<DevW> picos) {
  Map<String, String> results = [:]
  picos.each{ pico -> results << namePicoButtons(pico) }
  return results
}

void selectPicoButtonsForScene (List<DevW> picos) {
  if (state.scenes) {
    List<String> picoScenes = ['AUTOMATIC'] << _getScenes()
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

void _wirePicoButtonsToScenes () {
  if (settings.picos) {
    selectPicoButtonsForScene(settings.picos)
    populateStatePicoButtonToTargetScene()
  }
}

void _idMainRepeatersImplementingScenes () {
  input(
    name: 'mainRepeaters',
    title: heading3("Identify Main Repeaters with Integration Buttons for Room Scenes"),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _idIndDevices () {
  input(
    name: 'indDevices',
    title: heading3("Identify the Room's Non-RA2 Devices (e.g., Lutron Caséta, Z-Wave)"),
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
      Integer tableCol = 3
      paragraph("<br/><b>${scene} →</b>", width: 2)
      settings.indDevices?.each{ d ->
        String inputName = "scene^${scene}^Ind^${_getDeviceRa2Id(d)}"
        currSettingsKeys += inputName
        tableCol += 3
        input(
          name: inputName,
          type: 'number',
          title: "${b(d.label)}<br/>Level 0..100",
          width: 3,
          submitOnChange: true,
          required: false,
          multiple: false,
          defaultValue: 0
        )
      }
      settings.mainRepeaters?.each{d ->
        String inputName = "scene^${scene}^Rep^${_getDeviceRa2Id(d)}"
        currSettingsKeys += inputName
        tableCol += 3
        input(
          name: inputName,
          type: 'number',
          title: "${b(d.label)}<br/>Button #",
          width: 3,
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
        logWarn(
          '_configureRoomScene',
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

Map RoomScenesPage () {
  // The parent application (Whole House Automation) assigns a unique label
  // to each WHA Rooms instance. Capture app.label as state.ROOM_LABEL.
  return dynamicPage(
    name: 'RoomScenesPage',
    title: [
      heading1("${app.label} Scenes"),
      bullet1('Tab to register changes.'),
      bullet1('Click <b>Done</b> to enable subscriptions.')
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
    state.ROOM_LABEL = app.label  // WHA creates App w/ Label == Room Name
    state.RSPBSG_LABEL = "${state.ROOM_LABEL}Pbsg"
    state.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
    section {
      solicitLogThreshold('appLogThresh', 'TRACE')           // lHUI
      solicitLogThreshold('pbsgLogThresh', 'INFO')           // lHUI
      _idMotionSensors()
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
      pruneAppDups(
        [state.RSPBSG_LABEL],   // App Labels to keep
        app                        // Prune children of this app
      )
      /*
      href (
        name: state.RSPBSG_LABEL,
        width: 2,
        url: "/installedapp/configure/${rsPbsg.id}",
        style: 'internal',
        title: "Edit <b>${appInfo(rsPbsg)}</b>",
        state: null
      )
      */
      paragraph([
        heading1('Debug'),
        *appStateAsBullets(true),
        *appSettingsAsBullets(true)
      ].join('<br/>'))
    }
  }
}
