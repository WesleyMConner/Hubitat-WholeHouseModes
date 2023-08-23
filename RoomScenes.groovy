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
  parent: 'wesmc:WholeHouseAutomation',
  name: 'RoomScenes',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Manage Room Scenes for Whole House Automation',
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
  page(name: 'roomScenesPage')
}

Map roomScenesPage () {
  // The parent application (Whole House Automation) assigns a unique label
  // to each Room Scenes instance. Capture app.getLabel() as state.roomName.
  return dynamicPage(
    name: 'roomScenesPage',
    //title: 'Un-Named Room Scenes (RS) Page',
    install: true,
    uninstall: true,
    nextPage: 'whaPage',
    // , returnPath: 'list'
  ) {
    state.roomName = app.getLabel()
    section {
      paragraph (
        heading("${ state.roomName } Scenes<br/>")
        + comment('Tab to register changes.')
      )
      solicitLog()                          // <- provided by Utils
      solicitModeNamesAsSceneNames()
      solicitCustomScenes()
      updateRoomScenes()
      solicitSceneForModeName()
      solicitRepeatersForRoomScenes()
      //-> solicitKeypadsForRoomScenes()
      //-> solicitLedDevicesForRoomScenes()
      //--HOLD-> solicitKeypadButtonsForScene()
      solicitNonLutronDevicesForRoomScenes()
      //solicitLedToScene()
      selectLedsForScene()
      selectPicoButtonsForScene()
      solicitRoomScene()
      manageChildApps()
      paragraph heading('PBSG Configuration')
      displayPbsgHref()
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

void updateRoomScenes () {
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
  state.roomScenes = scenes
}

void solicitSceneForModeName () {
  if (state.roomScenes == null) {
    paragraph red('Mode-to-Scene selection will proceed once scene names exist.')
  } else {
    paragraph emphasis('Select scenes for per-mode automation:')
    getLocation().getModes().collect{mode -> mode.name}.each{ modeName ->
      String inputName = "modeToScene^${modeName}"
      String defaultValue = settings[inputName]
        ?: state.roomScenes.contains(modeName) ? modeName : null
      input(
        name: inputName,
        type: 'enum',
        title: modeName,
        width: 2,
        submitOnChange: true,
        required: true,
        multiple: false,
        options: state.roomScenes,
        defaultValue: defaultValue
      )
    }
  }
}

void solicitRepeatersForRoomScenes () {
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

//-> void solicitKeypadsForRoomScenes () {
//->   collapsibleInput (
//->     blockLabel: "Keypads for ${state.roomName} Scenes",
//->     name: 'deviceKeypadNames',
//->     title: emphasis('Identify Keypad(s) supporting Room Scenes'),
//->     type: 'enum',
//->     options: parent.getKeypads().collect{ d -> d.displayName }?.sort()
//->   )
//-> }

//-> void solicitLedDevicesForRoomScenes () {
//->   collapsibleInput (
//->     blockLabel: "LED Devices for ${state.roomName} Scenes",
//->     name: 'deviceLedNames',
//->     title: emphasis('Identify LED Button(s) supporting Room Scenes'),
//->     type: 'enum',
//->     options: parent.getLedDevices().collect{ d -> d.displayName }?.sort()
//->   )
//-> }

void solicitNonLutronDevicesForRoomScenes () {
  List<DevW> roomSwitches = parent.getNonLutronDevicesForRoom(state.roomName)
  //collapsibleInput (
  input(
    //blockLabel: "Non-Lutron Devices for ${state.roomName} Scenes",
    name: 'deviceNonLutronNames',
    title: emphasis('Identify Required Non-Lutron Devices'),
    type: 'enum',
    width: 6,
    options: roomSwitches.collect{ d -> d.displayName }?.sort(),
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

//-> void solicitLedToScene() {
//->     if (state.roomScenes == null) {
//->     paragraph red('Identification of LED to Room Scene will proceed once scene names exist.')
//->   } else {
//->     // Downstream Goal is Map<String, String> ledNameToSceneName = [:]
//->     settings.deviceLedNames.each{ ledName ->
//->       input(
//->           name: "led2Scene^${ledName}",
//->           type: 'enum',
//->           title: emphasis("Scene Name for ${ ledName }"),
//->           //width: 2,
//->           submitOnChange: true,
//->           required: true,
//->           multiple: false,
//->           options: state.roomScenes
//->         )
//->       //paragraph "LED-to-Scene Placeholder .. ${ledName} -> ${state.roomScenes}"
//->     }
//->   }
//->   // parent.getLedDevices().collect{ d -> d.displayName }?.sort()
//-> }

void selectLedsForScene() {
    if (state.roomScenes == null) {
    paragraph(red(
      'Once scene names exist, this section will solicit affiliated Buttons/LEDs.'
    ))
  } else {
    state.roomScenes.each{ sceneName ->
      input(
        name: "${sceneName}_LEDs",
        type: 'enum',
        width: 6,
        title: emphasis("Buttons/LEDs activating ${state.roomName} '${sceneName}'"),
        submitOnChange: true,
        required: false,
        multiple: true,
        options: parent.getLedDevices().collect{ d -> d.displayName }?.sort()
      )
    }
  }
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
  if (state.roomScenes == null) {
    paragraph(red(
      'Once scene names exist, this section will solicit affiliated pico buttons.'
    ))
  } else {
    List<DevW> picos = parent.getPicoDevices()
    //log.trace "picos [A]: ${picos}"
    state.roomScenes.each{ sceneName ->
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
  if (state.roomScenes == null) {
    paragraph red('Identification of Room Scene deetails selection will proceed once scene names exist.')
  } else {
    state.roomScenes?.each{ sceneName ->
      Integer col = 2
      paragraph("<br/><b>${ sceneName } →</b>", width: 2)
      settings.deviceNonLutronNames?.each{deviceName ->
        col += 2
        input(
          name: "scene^${sceneName}^NonLutron^${deviceName}",
          type: 'number',
          title: "<b>${ deviceName }</b><br/>Level 0..100",
          width: 2,
          submitOnChange: true,
          required: true,
          multiple: false,
          defaultValue: 0
        )
      }
      settings.deviceRepeaterNames?.sort().each{deviceName ->
        col += 2
        input(
          name: "scene^${sceneName}^Repeater^${ deviceName }",
          type: 'number',
          title: "<b>${ deviceName }</b><br/>Button #",
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

void manageChildApps() {
  // Abstract
  //   Room Scenes only expects a single, direct child application - an
  //   rsPBSG instance which manages a virtual child switch per room scene.
  //   This method is a stripped-down version of the similarly-named
  //   method in WholeHouseAutomation.groovy.
  // Design Notes
  //   Application state data managed by this method is limited to:
  //     - state.pbsg_<roomName>
  // ------------------------------------------------
  // Deliberately create noise for testing dups:
  //   addChildApp('wesmc', 'RoomScenes', 'Kitchen')
  //   addChildApp('wesmc', 'RoomScenes', 'Den')
  //   addChildApp('wesmc', 'RoomScenes', 'Kitchen')
  //   addChildApp('wesmc', 'RoomScenes', 'Puppies')
  //   addChildApp('wesmc', 'whaPBSG', 'Butterflies')
  // ------------------------------------------------
  // G E T   A L L   C H I L D   A P P S
  // ------------------------------------------------
  if (settings.log) log.trace (
    'manageChildApps() on entry getAllChildApps(): '
    + getAllChildApps().sort{ a, b ->
        a.getLabel() <=> b.getLabel() ?: a.getId() <=> b.getId()
      }.collect{ app ->
        "<b>${app.getLabel()}</b> -> ${app.getId()}"
      }?.join(', ')
  )
  // -----------------------------------------------
  // T O S S   S T A L E   A P P S   B Y   L A B E L
  // -----------------------------------------------
  // Child apps are managed by App Label, which IS NOT guaranteed to be
  // unique. The following method keeps only the latest (highest) App ID
  // per App Label.
  LinkedHashMap<String, InstAppW> childAppsByLabel \
    = keepOldestAppObjPerAppLabel()
  if (settings.log) log.trace (
    'manageChildApps() after keepOldestAppObjPerAppLabel(): '
    + childAppsByLabel.collect{label, childObj ->
        "<b>${label}</b> -> ${childObj.getId()}"
      }?.join(', ')
  )
  // -------------------------
  // P O P U L A T E   P B S G
  // -------------------------
  // Ensure imutable PBSG init data is in place AND instance(s) exist.
  // The PBSG instance manages its own state data locally.
  String pbsgName = "pbsg_${state.roomName}"
  state.switchNames = state.roomScenes + 'AUTOMATIC' + 'MANUAL'
  state.defaultSwitchName = 'AUTOMATIC'
  InstAppW pbsgApp = getAppByLabel(childAppsByLabel, pbsgName)
    ?:  addChildApp('wesmc', 'rsPBSG', pbsgName)
  if (settings.log) log.trace(
    "manageChildApps() initializing ${pbsgName} with "
    + "<b>SwitchNames:</b> ${state.switchNames}, and "
    + "<b>DefaultSwitchName:</b> ${state.defaultSwitchName}."
  )
  pbsgApp.configure(state.switchNames, state.defaultSwitchName, settings.log)
  state.pbsg_mgr = pbsgApp
  // ---------------------------------------------
  // P U R G E   E X C E S S   C H I L D   A P P S
  // ---------------------------------------------
  childAppsByLabel.each{ label, app ->
    if (label == pbsgName) {
      // Skip, still in use
    } else {
      if (settings.log) log.trace(
        "manageChildApps() deleting orphaned child app ${getAppInfo(app)}."
      )
      deleteChildApp(app.getId())
    }
  }
}

void pbsgSwitchActivated(String switchName) {
  log.trace(
    "pbsgSwitchActivated() RoomScenes <b>'${switchName}' activation is TBD</b>."
  )
}

void displayPbsgHref () {
  if (state.pbsg_mgr) {
    href (
      name: state.pbsg_mgr.getLabel(),
      width: 2,
      url: "/installedapp/configure/${state.pbsg_mgr.getId()}/rsPbsgPage",
      style: 'internal',
      title: "Inspect <b>${getAppInfo(state.pbsg_mgr)}</b>",
      state: null, //'complete'
    )
  } else {
    log.error 'displayPbsgHref() called with pbsg_modes missing.'
  }
}

// -------------------------------
// S T A T E   M A N A G E M E N T
// -------------------------------

void removeAllChildApps () {
  getAllChildApps().each{ child ->
    if (settings.log) log.trace(
      "removeAllChildApps removing ${child.getLabel()} (${child.getId()})"
    )
    deleteChildApp(child.getId())
  }
}

void installed() {
  if (settings.log) log.trace 'RoomScenes installed()'
  initialize()
}

void uninstalled() {
  if (settings.log) log.trace "Room Scenes uninstalled()"
  removeAllChildApps()
}

void updated() {
  if (settings.log) log.trace 'RoomScenes updated()'
  unsubscribe()  // Suspend event processing to rebuild state variables.
  // initialize()
}

void testHandler (Event e) {
  if (settings.log) log.trace "RoomScenes testHandler() w/ event: ${e}"
  if (settings.log) logEventDetails(e, false)
}

void initialize() {
  if (settings.log) log.trace "RoomScenes initialize()"
  if (settings.log) log.trace "RoomScenes subscribing to Lutron Telnet >${settings.lutronTelnet}<"
  settings.lutronTelnet.each{ d ->
    DevW device = d
    if (settings.log) log.trace "RoomScenes subscribing ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
  if (settings.log) log.trace "RoomScenes subscribing to Lutron Repeaters >${settings.lutronRepeaters}<"
  settings.lutronRepeaters.each{ d ->
    DevW device = d
    if (settings.log) log.trace "RoomScenes subscribing to ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
  if (settings.log) log.trace "RoomScenes subscribing to lutron SeeTouch Keypads >${settings.seeTouchKeypad}<"
  settings.seeTouchKeypad.each{ d ->
    DevW device = d
    if (settings.log) log.trace "RoomScenes subscribing to ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
}
