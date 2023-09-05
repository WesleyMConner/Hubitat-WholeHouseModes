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
#include wesmc.UtilsLibrary
#include wesmc.ra2Library

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

void removeScenePrefixFromSettings () {
  settings.each{ key, value ->
    if (key.contains('scene^')) {
      log.trace "--45--------> ${key}"
    }
  }
}

Map whaRoomPage () {
  // The parent application (Whole House Automation) assigns a unique label
  // to each WHA Rooms instance. Capture app.getLabel() as state.ROOM_NAME.
  return dynamicPage(
    name: 'whaRoomPage',
    title: \
      heading("${ app.getLabel() } Scenes<br/>") \
      + comment('Tab to register changes.'),
    install: true,
    uninstall: true,
    nextPage: 'whaPage'
  ) {
    // SAMPLE STATE & SETTINGS CLEAN UP
    //   - state.remove('X')
    //   - settings.remove('Y')
//    removeScenePrefixFromSettings()
/*
settings.remove('scene^Chill^Repeater^1')
settings.remove('scene^Day^Repeater^1')
settings.remove('scene^Night^Repeater^1')
settings.remove('scene^Party^Repeater^1')
settings.remove('scene^Supplement^Repeater^1')
settings.remove('scene^TV^Repeater^1')
settings.remove('scene^Chill^NonLutron^null')
settings.remove('scene^Day^NonLutron^null')
settings.remove('scene^Night^NonLutron^null')
settings.remove('scene^Party^NonLutron^null')
settings.remove('scene^Supplement^NonLutron^null')
settings.remove('scene^TV^NonLutron^null')
*/
    state.ROOM_NAME = app.getLabel()
    state.SCENE_PBSG_APP_NAME = "pbsg_${state.ROOM_NAME}"
    // SAMPLE STATE & SETTINGS CLEAN UP
    //   - state.remove('X')
    //   - settings.remove('Y')
    section {
      solicitLog()                          // <- provided by Utils
      solicitModeNamesAsSceneNames()
      solicitCustomScenes()
      updateScenes()
      solicitSeeTouchKeypads (
        'lutronSeeTouchKeypads',
        'Identify <b>ALL Keypads</b> with buttons that impact <b>Room scenes</b>.'
      )
      solicitLutronLEDs(
        'lutronSceneButtons',
        'Identify <b>All LEDs/Buttons</b> that enable <b>Room scenes</b>.'
      )
      if (state.scenes == null || settings?.lutronSceneButtons == null) {
        paragraph(red('Scene activation buttons are pending pre-requisites.'))
      } else {
        selectLedsForListItems(
          state.scenes,
          settings.lutronSceneButtons,
          'sceneButton'
        )
//log.trace "BEFORE KPADDNI: ${ displaySettings() }"
        mapKpadDNIandButtonToItem('sceneButton')
//log.trace "AFTER KPADDNI: ${ displaySettings() }"
      }
      //-----> TBD START
      //solicitLutronPicos(
      //  'lutronPicoButtons',
      //  'W I P -> Identify <b>Picos</b> and <b>Pico Buttons</b> that enable <b>Room scenes</b>.',
      //  [ required: false ]
      //)
      //-> Process Picos similar to Leds above ?!
      //-----> TBD END
      solicitLutronMainRepeaters(
        'lutronMainRepeaters',
        'Identify repeaters that host integration buttons for <b>Room scenes</b>.'
      )
      solicitSwitches(
        'independentDevices',
        'Identify devices <b>NOT</b> configured via Lutron integration buttons.'
      )

      if (state.scenes && (settings.independentDevices || settings.lutronMainRepeaters)) {
        solicitRoomScene()
      } else {
        paragraph red('Soliciation of Room scenes is pending pre-requisite data.')
      }
      if (state.scenes == null) {
        paragraph red('Management of child apps is pending selection of Room scenes.')
      } else {
        keepOldestAppObjPerAppLabel([state.SCENE_PBSG_APP_NAME], settings.log)
        ArrayList switchNames = [*state.scenes, 'AUTOMATIC', 'MANUAL']
        pbsgChildAppDrilldown(
          state.SCENE_PBSG_APP_NAME,
          'roomPBSG',
          'roomPbsgPage',
          switchNames,      // [*state.scenes, 'AUTOMATIC', 'MANUAL'],
          'AUTOMATIC',
          settings.log
        )
      }
      paragraph(
        heading('Debug<br/>')
        + "${ displayState() }<br/>"
        + "${ displaySettings() }"
      )
    }
  }
}

// groovy.lang.MissingMethodException: No signature of method:
// user_app_wesmc_whaRoom_338.pbsgChildAppDrilldown() is applicable for
// argument types:
//   (
//     org.codehaus.groovy.runtime.GStringImpl,
//     java.lang.String,
//     java.lang.String,
//     java.util.ArrayList,
//     java.lang.String,
//     java.lang.Boolean
//   ) values:
//   [
//     pbsg_Den,
//     roomPBSG,
//     roomPbsgPage,
//     [Chill, Day, Night, Party, ...],
//     ...
//   ] on line 108 (method whaRoomPage)

/*
void roomPbsgAppDrilldown() {
  paragraph heading('Room PBSG Inspection')
  InstAppW modePbsgApp = app.getChildAppByLabel(state.MODE_PBSG_APP_NAME)
  if (!modePbsgApp) {
    modePbsgApp = addChildApp('wesmc', 'modePBSG', state.MODE_PBSG_APP_NAME)
    modePbsgApp.configure(
      state.MODE_SWITCH_NAMES,
      state.DEFAULT_MODE_SWITCH_NAME,
      settings.log
    )
  }
  href (
    name: settings.MODE_PBSG_APP_NAME,
    width: 2,
    url: "/installedapp/configure/${modePbsgApp.getId()}/modePbsgPage",
    style: 'internal',
    title: "Edit <b>${getAppInfo(modePbsgApp)}</b>",
    state: null, //'complete'
  )
}
*/

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

/*
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
*/

/*
void solicitRepeatersForWhaRoom () {
  //collapsibleInput (
  input(
    //blockLabel: "Repeaters for ${state.ROOM_NAME} Scenes",
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
*/

/*
void solicitRepeatersForWhaRoom () {
  //collapsibleInput (
  input(
    //blockLabel: "Repeaters for ${state.ROOM_NAME} Scenes",
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
    options: parent.getNonLutronDevicesForRoom(state.ROOM_NAME).collectEntries{ d ->
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
  List<String> plist = (parent.getNonLutronDevicesForRoom(state.ROOM_NAME)).collect{
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

//-> void deriveKpadDNIandButtonToScene () {
//->   mapKpadDNIandButtonToItem('sceneButtons')
//-> }

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
          title: emphasis("Pico Buttons activating ${state.ROOM_NAME} '${sceneName}'."),
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
      settings.independentDevices?.each{ d ->
      String inputName = "scene^${sceneName}^indepenent^${d.getLabel()}"
log.trace "#419 inputName: >${inputName}<"
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
      // ?.sort{ d -> d.getLabel() }
      settings.lutronMainRepeaters?.each{d ->
      String inputName = "scene^${sceneName}^repeater^${d.getLabel()}"
log.trace "#436 inputName: >${inputName}<"
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
  if (settings.log) log.trace "RS installed() for '${state.ROOM_NAME}'."
  initialize()
}

void uninstalled() {
  if (settings.log) log.trace "RS uninstalled() for '${state.ROOM_NAME}'."
  removeAllChildApps()
}

void updated() {
  if (settings.log) log.trace "RS updated() for '${state.ROOM_NAME}'."
  unsubscribe()  // Suspend event processing to rebuild state variables.
  // initialize()
}

void telnetHandler (Event e) {
  if (settings.log) log.trace(
    "RS testHandler() for '${state.ROOM_NAME}' w/ event: ${e}"
  )
  if (settings.log) logEventDetails(e, false)
}

void repeaterHandler (Event e) {
  if (settings.log) log.trace(
    "RS testHandler() for '${state.ROOM_NAME}' w/ event: ${e}"
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
      "RS keypadToVswHandler() for '${state.ROOM_NAME}' "
      + "<b>Keypad Device Id:</b> ${e.deviceId}, "
      + "<b>Keypad Button:</b> ${e.value}, "
      + "<b>Affiliated Switch:</b> ${targetVsw}"
    )
    // Turn on appropriate pbsg-modes-X VSW.
    app.getChildAppByLabel(state.SCENE_PBSG_APP_NAME).turnOnSwitch(targetVsw)
  } else {
    if (settings.log) log.trace(
      "RS keypadToVswHandler() for '${state.ROOM_NAME}' unexpected event "
      + "name '${e.name}' for DNI '${e.deviceId}'"
    )
  }
}

void initialize() {
  if (settings.log) log.trace "RS initialize() of '${state.ROOM_NAME}'."
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
