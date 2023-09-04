// ---------------------------------------------------------------------------------
// R O O M   S C E N E S
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
//->#include wesmc.pbsgLibrary
#include wesmc.UtilsLibrary

definition(
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

// -------------------------------
// C L I E N T   I N T E R F A C E
// -------------------------------
preferences {
  page(name: 'whaRoomPage')
}

Map whaRoomPage () {
  // The parent application (Whole House Automation) assigns a unique label
  // to each WHA Rooms instance. Capture app.getLabel() as state.roomName.
  return dynamicPage(
    name: 'whaRoomPage',
    title: \
      heading("${ state.roomName } Scenes<br/>") \
      + comment('Tab to register changes.'),
    install: true,
    uninstall: true,
    nextPage: 'whaPage',
    // , returnPath: 'list'
  ) {
    // SAMPLE STATE & SETTINGS CLEAN UP
    //   - state.remove('X')
    //   - settings.remove('Y')
    state.roomName = app.getLabel()
    state.SCENE_PBSG_APP_NAME = "pbsg_${state.roomName}"
    // SAMPLE STATE & SETTINGS CLEAN UP
    //   - state.remove('X')
    //   - settings.remove('Y')
    section {
      solicitLog()                          // <- provided by Utils
      solicitModeNamesAsSceneNames()
      solicitCustomScenes()
      updateScenes()
      solicitSceneForModeName()
      solicitRepeatersForWhaRoom()
      //-> solicitKeypadsForWhaRoom()
      //-> solicitLedDevicesForWhaRoom()
      //--HOLD-> solicitKeypadButtonsForScene()
      solicitNonLutronDevicesForWhaRoom()
      //solicitLedToScene()
      selectLedsForScene()
      deriveKpadDNIandButtonToScene()
      selectPicoButtonsForScene()
      solicitRoomScene()
      if (!state.SCENE_PBSG_APP_NAME) {
        log.error 'state.SCENE_PBSG_APP_NAME is not defined'
      } else {
        keepOldestAppObjPerAppLabel([state.SCENE_PBSG_APP_NAME], false)
        roomPbsgAppDrilldown()
      }
      paragraph(
        heading('Debug<br/>')
        + "${ displayState() }<br/>"
        + "${ displaySettings() }"
      )
    }
  }
}

// -----------------------------------------------------
// R O O M S   S C E N E S   P A G E   &   S U P P O R T
// -----------------------------------------------------

void solicitModeNamesAsSceneNames () {
  input(
    name: 'modeNamesAsSceneNames',
    type: 'enum',
    title: '<span style="margin-left: 10px;">' \
           + emphasis('Select "Mode Names" to use as "Scene Names" <em>(optional)</em>:') \
           + '</span>',
    submitOnChange: true,
    required: false,
    multiple: true,
    options: getLocation().getModes().collect{ mode -> mode.name }?.sort()
  )
}

void solicitCustomScenes () {
  String prefix = 'customScene'
  LinkedHashMap<String, String> slots = [
    "${prefix}1": settings["${prefix}1"],
    "${prefix}2": settings["${prefix}2"],
    "${prefix}3": settings["${prefix}3"],
    "${prefix}4": settings["${prefix}4"],
    "${prefix}5": settings["${prefix}5"],
    "${prefix}6": settings["${prefix}6"],
    "${prefix}7": settings["${prefix}7"],
    "${prefix}8": settings["${prefix}8"],
    "${prefix}9": settings["${prefix}9"]
  ]
  LinkedHashMap<String, String> filled = slots.findAll{it.value}
  // Only present 1 empty sceen "slot" at a time.
  LinkedHashMap<String, String> firstOpen = slots.findAll{!it.value}?.take(1)
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

void updateScenes () {
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
    scenes = scenes.flatten()
  }
  scenes = scenes.sort()
  state.scenes = scenes
}

void solicitSceneForModeName () {
  if (state.scenes == null) {
    paragraph red('Mode-to-Scene selection will proceed once scene names exist.')
  } else {
    paragraph emphasis('Select scenes for per-mode automation:')
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

void solicitRepeatersForWhaRoom () {
  //collapsibleInput (
  input(
    //blockLabel: "Repeaters for ${state.roomName} Scenes",
    name: 'repeaters',
    title: emphasis('Identify Required Repeater(s)'),
    type: 'enum',
    width: 6,
    options: parent.getMainRepeaters().collectEntries{ d ->
      [d, d.displayName]
    },  //.collect{ d -> d.displayName }?.sort(),
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

/*
void solicitRepeatersForWhaRoom () {
  //collapsibleInput (
  input(
    //blockLabel: "Repeaters for ${state.roomName} Scenes",
    name: 'deviceRepeaterNames',
    title: emphasis('Identify Required Repeater(s)'),
    type: 'enum',
    width: 6,
    options: parent.getMainRepeaters().collect{ d -> d.displayName }?.sort(),
    submitOnChange: true,
    required: false,
    multiple: true
  )
}
*/

void solicitNonLutronDevicesForWhaRoom () {
  input(
    name: 'nonLutronDevices',
    title: emphasis('Identify Required Non-Lutron Devices'),
    type: 'enum',
    width: 6,
    options: parent.getNonLutronDevicesForRoom(state.roomName).collectEntries{ d ->
      [d, d.displayName]
    },
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

/*
void solicitNonLutronDevicesForWhaRoom () {
  // The parent (wha.groovy) solicits the non-lutron device objects.
  // This solicitation isolates select data from these objects:
  //   "<displayName>^dni^switch|dimmer"
  // which is used subsequently to capture and execute per-scene values.
  List<String> plist = (parent.getNonLutronDevicesForRoom(state.roomName)).collect{
    d -> """${d.getName()}^${d.getDeviceNetworkId()}^${
      d.getSupportedCommands().contains('setLevel') ? 'dimmer' : 'switch'
    }"""
  }
  input(
    name: 'nonLutronDevices',
    title: emphasis('Identify Required Non-Lutron Devices'),
    type: 'enum',
    width: 6,
    options: plist, //roomSwitches.collect{ d -> d.displayName }?.sort(),
    submitOnChange: true,
    required: false,
    multiple: true
  )
}
*/

void selectLedsForScene() {
  selectLedsForListItems(state.scenes, parent.getLedDevices(), 'sceneButtons')
}

void deriveKpadDNIandButtonToScene () {
  mapKpadDNIandButtonToItem('sceneButtons')
}

List<String> picoButtons (DevW pico) {
  String dN = pico.displayName
  return [
    "${dN}^1^Top",
    "${dN}^2^Up",
    "${dN}^3^Middle",
    "${dN}^4^Down",
    "${dN}^5^Bottom"
  ]
}

List<String> picoButtonPicklist (List<DevW> picos) {
  List<String> results = []
  picos.each{ pico -> results << picoButtons(pico) }
  results = results.flatten()
  return results
}

void selectPicoButtonsForScene() {
  if (state.scenes == null) {
    paragraph(red(
      'Once scene names exist, this section will solicit affiliated pico buttons.'
    ))
  } else {
    List<DevW> picos = parent.getPicoDevices()
    //log.trace "RS selectPicoButtonsForScene() picos [A]: ${picos}"
    state.scenes.each{ sceneName ->
      input(
          // Head's Up:
          //   - circa Aug-2023, Hubitat translates settings.Xyz to settings.xyz
          name: "${sceneName}_PicoButtons",
          type: 'enum',
          title: emphasis("Pico Buttons activating ${state.roomName} '${sceneName}'."),
          width: 6,
          submitOnChange: true,
          required: false,
          multiple: true,
          options: picoButtonPicklist(picos)
        )
    }
  }
}

void solicitRoomScene () {
  // Display may be full-sized (12-positions) or phone-sized (4-position).
  // For phone friendliness, work one scene at a time.
  if (state.scenes == null) {
    paragraph red('Identification of Room Scene deetails selection will proceed once scene names exist.')
  } else {
    state.scenes?.each{ sceneName ->
      Integer col = 2
      paragraph("<br/><b>${ sceneName } →</b>", width: 2)
      settings.nonLutronDevices?.each{ d ->
        col += 2
        input(
          name: "scene^${sceneName}^NonLutron^${d.deviceName}",
          type: 'number',
          title: "<b>${ d.deviceName }</b><br/>Level 0..100",
          width: 2,
          submitOnChange: true,
          required: true,
          multiple: false,
          defaultValue: 0
        )
      }
      // ?.sort{ d -> d.getLabel() }
      settings.repeaters?.each{d ->
        log.trace "---------->${d}"
        col += 2
        input(
          name: "scene^${sceneName}^Repeater^${ d.name }",
          type: 'number',
          title: "<b>${ d.name }</b><br/>Button #",
          width: 2,
          submitOnChange: true,
          required: true,
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
}

void pbsgVswTurnedOn(String simpleName) {
  log.trace(
    "RS pbsgVswTurnedOn() WhaRooms <b>'${simpleName}' activation is TBD</b>."
  )
}

void roomPbsgAppDrilldown() {
  paragraph heading('Scene PBSG Inspection')
  InstAppW roomPbsgApp = app.getChildAppByLabel(state.SCENE_PBSG_APP_NAME)
  if (!roomPbsgApp) {
    roomPbsgApp = addChildApp('wesmc', 'roomPBSG', state.SCENE_PBSG_APP_NAME)
    roomPbsgApp.configure(
      [*state.scenes, 'AUTOMATIC', 'MANUAL'],
      'AUTOMATIC',
      settings.log
    )
  }
  href (
    name: settings.MODE_PBSG_APP_NAME,
    width: 2,
    url: "/installedapp/configure/${roomPbsgApp.getId()}/roomPbsgPage",
    style: 'internal',
    title: "Edit <b>${getAppInfo(roomPbsgApp)}</b>",
    state: null, //'complete'
  )
}

void deriveSceneToRepeater(String sceneName) {
  // Push appropriate Repeater buttons for sceneName
  // Process
  //   - scene^Chill^Repeater^(lutron-1) REP 1 → 41
  // Into
  //   - state.sceneToRepeater[scene: [repeaterDni, button], ...]
}

void deriveSceneToNonLutronDevice(String sceneName) {
  // Set dimmer level or switch on/off for sceneName.
  // Process
  //   - scene^Chill^NonLutron^Fireplace Lighting → 100
  // Into
  //   - Need to differentiate switch from dimmer
  //       - DEVICE: String getDeviceNetworkId()
  //       - DEVICE: List<Command> getSupportedCommands()
  //       - DEVICE: List<Capability> getCapabilities()
  //       - DEVICE: List<State> getCurrentStates()
  //       - DEVICE: Boolean hasCapability(String capability)
  //       - DEVICE: Boolean hasAttribute(String attribute)
  //       - DEVICE: Boolean hasCommand(String command)
  //       - DEVICE: void updateSetting(String name, Map options)
  //--
  //       - DRIVER: void updateDataValue(String name, String value)
  //       - DRIVER: String getDataValue(String name)
  //   - state.sceneToNonLutronSwitch[scene: [deviceDni, on/off], ...]
  //   - state.sceneToNonLutronDimmer[scene: [deviceDni, level], ...]
}

// -------------------------------
// S T A T E   M A N A G E M E N T
// -------------------------------

void removeAllChildApps () {
  getAllChildApps().each{ child ->
    if (settings.log) log.trace(
      "RS removeAllChildApps removing ${child.getLabel()} (${child.getId()})"
    )
    deleteChildApp(child.getId())
  }
}

void installed() {
  if (settings.log) log.trace "RS installed() for '${state.roomName}'."
  initialize()
}

void uninstalled() {
  if (settings.log) log.trace "RS uninstalled() for '${state.roomName}'."
  removeAllChildApps()
}

void updated() {
  if (settings.log) log.trace "RS updated() for '${state.roomName}'."
  unsubscribe()  // Suspend event processing to rebuild state variables.
  // initialize()
}

void telnetHandler (Event e) {
  if (settings.log) log.trace(
    "RS testHandler() for '${state.roomName}' w/ event: ${e}"
  )
  if (settings.log) logEventDetails(e, false)
}

void repeaterHandler (Event e) {
  if (settings.log) log.trace(
    "RS testHandler() for '${state.roomName}' w/ event: ${e}"
  )
  if (settings.log) logEventDetails(e, false)
}

void keypadToVswHandler (Event e) {
  // Design Note
  //   - The field e.deviceId arrives as a number and must be cast toString().
  //   - Hubitat runs Groovy 2.4. Groovy 3 constructs - x?[]?[] - are not available.
  //   - Keypad buttons are matched to state data to activate a target VSW.
  if (e.name == 'pushed') {
    String targetVsw = state.kpadButtons.getAt(e.deviceId.toString())?.getAt(e.value)
    if (settings.log) log.trace(
      "RS keypadToVswHandler() for '${state.roomName}' "
      + "<b>Keypad Device Id:</b> ${e.deviceId}, "
      + "<b>Keypad Button:</b> ${e.value}, "
      + "<b>Affiliated Switch:</b> ${targetVsw}"
    )
    // Turn on appropriate pbsg-modes-X VSW.
    app.getChildAppByLabel(state.SCENE_PBSG_APP_NAME).turnOnSwitch(targetVsw)
  } else {
    if (settings.log) log.trace(
      "RS keypadToVswHandler() for '${state.roomName}' unexpected event "
      + "name '${e.name}' for DNI '${e.deviceId}'"
    )
  }
}

void initialize() {
  if (settings.log) log.trace "RS initialize() of '${state.roomName}'."
  if (settings.log) log.trace "RS subscribing to Lutron Telnet >${settings.lutronTelnet}<"
  settings.lutronTelnet.each{ d ->
    DevW device = d
    if (settings.log) log.trace "RS subscribing ${device.displayName} ${device.id}"
    subscribe(device, telnetHandler, ['filterEvents': false])
  }
  if (settings.log) log.trace "RS subscribing to Lutron Repeaters >${settings.lutronRepeaters}<"
  settings.lutronRepeaters.each{ d ->
    DevW device = d
    if (settings.log) log.trace "RS subscribing to ${device.displayName} ${device.id}"
    subscribe(device, repeaterHandler, ['filterEvents': false])
  }
  if (settings.log) log.trace "RS subscribing to lutron SeeTouch Keypads >${settings.seeTouchKeypad}<"
  settings.seeTouchKeypad.each{ d ->
    DevW device = d
    if (settings.log) log.trace "RS subscribing to ${device.displayName} ${device.id}"
    subscribe(device, keypadToVswHandler, ['filterEvents': false])
  }
}
