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
//
//   Design Notes
//   - Multiple DevWL instances arise due to multiple input() statements.
//   - Initialization of 'state' includes making immutable copies of DeviveWrapper
//     instances, gathered from 'settings'.
// ---------------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.DeviceWrapperList as DevWL
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Location as Loc
#include wesmc.pbsgLibrary
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
  page(name: 'roomScenesPage', title: '', install: true, uninstall: true)
}

// -----------------------------------------------------
// R O O M S   S C E N E S   P A G E   &   S U P P O R T
// -----------------------------------------------------

void solicitModesAsScenes () {
  input(
    name: 'modesAsScenes',
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
  String settingsKeyPrefix = 'customScene'
  LinkedHashMap<String, String> slots = [
    "${settingsKeyPrefix}1": settings["${settingsKeyPrefix}1"],
    "${settingsKeyPrefix}2": settings["${settingsKeyPrefix}2"],
    "${settingsKeyPrefix}3": settings["${settingsKeyPrefix}3"],
    "${settingsKeyPrefix}4": settings["${settingsKeyPrefix}4"],
    "${settingsKeyPrefix}5": settings["${settingsKeyPrefix}5"],
    "${settingsKeyPrefix}6": settings["${settingsKeyPrefix}6"],
    "${settingsKeyPrefix}7": settings["${settingsKeyPrefix}7"],
    "${settingsKeyPrefix}8": settings["${settingsKeyPrefix}8"],
    "${settingsKeyPrefix}9": settings["${settingsKeyPrefix}9"]
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
  //-- if (LOG) log.trace "updateRoomScenes() [*]"
  List<String> scenes = settings.modesAsScenes ?: []
log.trace "[001] scenes: >${scenes}<"
  scenes = scenes.flatten()
log.trace "[002] scenes: >${scenes}<"

  String settingsKeyPrefix = 'customScene'
  List<String> customScenes = [
    settings["${settingsKeyPrefix}1"],
    settings["${settingsKeyPrefix}2"],
    settings["${settingsKeyPrefix}3"],
    settings["${settingsKeyPrefix}4"],
    settings["${settingsKeyPrefix}5"],
    settings["${settingsKeyPrefix}6"],
    settings["${settingsKeyPrefix}7"],
    settings["${settingsKeyPrefix}8"],
    settings["${settingsKeyPrefix}9"],
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
      List<String> defaultValue = state.roomScenes.findAll{ scene ->
        //assert scene instanceof String
        //assert modeName instanceof String
        //(scene.toString() == modeName.toString())
        (scene == modeName)
      }
      log.trace "[018] defaultValue: >${defaultValue}<"
      input(
        name: "${modeName}ToScene",
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

void setModeToScene () {
  Map<String, List<String>> map = getLocation().getModes()
    .collect{mode -> mode.name}
    .collectEntries{ modeName ->
      [modeName, settings["${modeName}ToScene"]]
    }
    app.updateSetting('modeToScene', [type: 'Map', value: map])
}

void solicitRepeatersForRoomScenes () {
  collapsibleInput (
    blockLabel: "Repeaters for ${state.roomName} Scenes",
    name: 'repeaters',
    title: 'Identify Repeater(s) supporting Room Scenes',
    type: 'enum',
    options: parent.getMainRepeaters().collect{ d -> d.displayName }?.sort()
  )
}

void solicitKeypadsForRoomScenes () {
  collapsibleInput (
    blockLabel: "Keypads for ${state.roomName} Scenes",
    name: 'keypads',
    title: 'Identify Keypad(s) supporting Room Scenes',
    type: 'enum',
    options: parent.getKeypads().collect{ d -> d.displayName }?.sort()
  )
}

void solicitLedDevicesForRoomScenes () {
  collapsibleInput (
    blockLabel: "LED Devices for ${state.roomName} Scenes",
    name: 'leds',
    title: 'Identify LED Button(s) supporting Room Scenes',
    type: 'enum',
    options: parent.getLedDevices().collect{ d -> d.displayName }?.sort()
  )
}

/*
??? CAN LED DEVICES FUNCTION AS PROXIES ???
void solicitKeypadButtonsForScene () {
  // One slider to collapse all entries in this section.
  input (
    name: boolGroup,
    type: 'bool',
    title: "${settings[boolGroup] ? 'Hiding' : 'Showing'} Keypad Buttons for Scene",
    submitOnChange: true,
    defaultValue: false,
  )
  if (!settings[boolSwitchName]) {
    state.roomScenes?.each{sceneName ->
      input(
        name: "${sceneName}_keypadButtons",
        type: 'enum',
        title: "Identify Keypad Buttons for ${sceneName}.",
        submitOnChange: true,
        required: true,
        multiple: true,
        options: settings["leds"]?.sort()
      )
    }
  }
}
*/

void solicitNonLutronDevicesForRoomScenes () {
  List<DevW> roomSwitches = parent.getNonLutronDevicesForRoom(state.roomName)
  collapsibleInput (
    blockLabel: "Non-Lutron Devices for ${state.roomName} Scenes",
    name: 'nonLutron',
    title: 'Identify Non-Lutron devices supporting Room Scenes',
    type: 'enum',
    options: roomSwitches.collect{ d -> d.displayName }?.sort()
  )
}

void solicitRoomScene () {
  // Display may be full-sized (12-positions) or phone-sized (4-position).
  // For phone friendliness, work one scene at a time.
if (LOG) log.trace "[010]"
  if (state.roomScenes == null) {
    paragraph red('Identification of Room Scene deetails selection will proceed once scene names exist.')
  } else {
if (LOG) log.trace "[011] >${state.roomScenes}<"
    state.roomScenes?.each{ sceneName ->
if (LOG) log.trace "[012] >${sceneName}<"
      Integer col = 1
      paragraph("<br/><b>${ sceneName } →</b>", width: 1)
      settings.nonLutron?.each{deviceName ->
        col += 2
        input(
          name: "${ sceneName }:${ deviceName }",
          type: 'number',
          title: "<b>${ deviceName }</b><br/>Level 0..100",
          width: 2,
          submitOnChange: true,
          required: true,
          multiple: false,
          defaultValue: 0
        )
      }
      settings.repeaters?.sort().each{deviceName ->
        col += 2
        input(
          name: "${ sceneName }.${ deviceName }",
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

//-- void appButtonHandler(String buttonName) {
//--   if (LOG) log.trace "appButtonHandler() received '${buttonName}'"
//--   switch (buttonName) {
//--     case 'updateRoomScenes': { it ->
//--       if (LOG) log.trace  'appButtonHandler() received "updateRoomScenes"'
//--       updateRoomScenes()
//--     }
//--     break
//--     default: { it ->
//--       if (LOG) log.trace 'appButtonHandler() in default section'
//--     }
//--   }
//-- }

def roomScenesPage () {
  // The parent application (Whole House Automation) assigns a unique label
  // to each Room Scenes instance. Capture app.getLabel() as state.roomName.
  dynamicPage(name: 'roomScenesPage') {
    state.roomName = app.getLabel()
    //state.remove('X')
    section {
      paragraph (
        heading("${ state.roomName } Scenes<br/>")
        + comment(red('Tab to register changes.'))
      )
      solicitLOG()  // via Utils

      solicitModesAsScenes()
      solicitCustomScenes()

      updateRoomScenes()
      solicitSceneForModeName()
      //->solicitRepeatersForRoomScenes()
      //->solicitKeypadsForRoomScenes()
      //->solicitLedDevicesForRoomScenes()
      //->solicitKeypadButtonsForScene ()
      //->solicitNonLutronDevicesForRoomScenes()
      // Input type button:
      //   - Renders a UI button with the title in the button
      //   - Generates a callback to a method defined in the app called
      //     'appButtonHandler(String buttonName)', which is passed the
      //     name of the input (when the UI button is pressed) AND causes
      //     a page refresh. The callback CANNOT render anything, but can
      //     set state.
      // BUTTON GETS TRIGGERED, BUT THE FN THEREIN DOES NOT EXECUTE
      // AT LEAST NOT IN THE CURRENT MAIN THREAD.
      //-- input (
      //--   name: 'updateRoomScenes',
      //--   type: 'button',
      //--   title: 'Update Room Scenes',
      //--   submitOnChange: true
      //-- )
      //->solicitRoomScene()
      //->setModeToScene()
      paragraph(
        heading('Debug<br/>')
        + "${ displaySettings() }<br/>"
        + "${ displayState() }"
        //+ "<b>state.roomScenes</b>: ${state.roomScenes}<br/>"
        //+ "<b>Debug Scenes:</b> ${ state.roomScenes }<br/>"
        //+ "<b>Debug Mode-to-Scene:</b> ${ getModeToScene() }<br/>"
        //+ "<b>Repeaters:</b> ${ settings["repeaters"] }<br/>"
        //+ "<b>Keypads:</b>${ settings["keypads"] }<br/>"
        //+ "<b>LED Devices:</b>${ settings["leds"] }<br/>"
        //+ "<b>Non-Lutron Devices:</b> ${ settings["nonLutron"] }"
      )
      //----> Is it necessary to solicit Keypad nuttons that trigger scenes?
    }
  }
}

String displaySettings() {
  [
    '<b>SETTINGS</b>',
    settings.sort().collect{ k, v -> bullet("<b>${k}</b> → ${v}") }.join('<br/>')
  ].join('<br/>')
}

String displayState() {
  [
    '<b>STATE</b>',
    state.sort().collect{ k, v -> bullet("<b>${k}</b> → ${v}") }.join('<br/>')
  ].join('<br/>')
}

// -------------------------------
// S T A T E   M A N A G E M E N T
// -------------------------------

/**********
void installed() {
  if (LOG) log.trace 'WHA installed()'
  initialize()
}

void updated() {
  if (LOG) log.trace 'WHA updated()'
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void testHandler (Event e) {
  // SAMPLE 1
  //   descriptionText  (lutron-80) TV Wall KPAD button 1 was pushed [physical]
  //          deviceId  5686
  //       displayName  (lutron-80) TV Wall KPAD
  if (LOG) log.trace "WHA testHandler() w/ event: ${e}"
  if (LOG) logEventDetails(e, false)
}

void initialize() {
  if (LOG) log.trace "WHA initialize()"
  if (LOG) log.trace "WHA subscribing to Lutron Telnet >${settings.lutronTelnet}<"
  settings.lutronTelnet.each{ d ->
    DevW device = d
    if (LOG) log.trace "WHA subscribing ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
  if (LOG) log.trace "WHA subscribing to Lutron Repeaters >${settings.lutronRepeaters}<"
  settings.lutronRepeaters.each{ d ->
    DevW device = d
    if (LOG) log.trace "WHA subscribing to ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
  if (LOG) log.trace "WHA subscribing to lutron SeeTouch Keypads >${settings.seeTouchKeypad}<"
  settings.seeTouchKeypad.each{ d ->
    DevW device = d
    if (LOG) log.trace "WHA subscribing to ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }

  //ArrayList<LinkedHashMap> modes = getModes()
  // Rebuild the PBSG mode instance adjusting (i.e., reusing or dropping)
  // previously-created VSWs to align with current App modes.
  //if (state['pbsg_modes']) { deletePBSG(name: 'pbsg_modes', dropChildVSWs: false) }
  //createPBSG(
  //  name: 'pbsg_modes',
  //  sceneNames: modes.collect{it.name},
  //  defaultScene: 'Day'
  //)
}
**********/

// -----------
// U N U S E D
// -----------

//--MISSING->displayParticipatingDevices()
//--MISSING->displayAppInfoLink()

LinkedHashMap<String, InstAppW> getAllChildAppsByLabel () {
  return getAllChildApps().collectEntries{
    childApp -> [ childApp.getLabel(), childApp ]
  }
}

void displayCustomScenes () {
  paragraph(
    '<table>'
      + params.collect{ k, v -> "<tr><th>${k}</th><td>${v}</td></tr>" }.join()
      + '</table>'
  )
}

/*
  LinkedHashMap unpairedChildAppsByName = getChildAppsByName (Boolean LOG = false)

  //->removeUnpairedChildApps ()
  if (LOG) log.info "childApps: ${childApps.collect{it.getLabel()}.join(', ')}"

  // MapfocalRoomsToRoomSceneApps
  LinkedHashMap roomAppsByName = settings.focalRooms.collectEntries{
    room -> [room, unpairedChildIds.contains(room) ?: null]
  }

  // Prepare to capture the Mode PBSG child app.
  InstAppW pbsgModeApp = null

  // Prepare to remove unused child apps.
  List<String> unusedDeviceNetworkIds = []

  // Parse existing (discovered) Child Apps, removing unaffiliated children.
  List<InstAppW> childApps = getAllChildApps()
  //--
  childApps.each{ childApp ->
    String childLabel = childApp.getLabel()
    if (childLabel == 'pbsg-mode') {
      pbsgModeApp = childApp
    } else if (settings.focalRooms.contains(childLabel)) {
      roomAppsByName.putAt(childLabel, child)
    } else {
      unusedDeviceNetworkIds << childApp.deviceNetworkId
    }
  }
  unusedDeviceNetworkIds.each{ deviceNetworkId ->
    if (LOG) log.info "Removing stale childApps ${deviceNetworkId}"
    deleteChildDevice(deviceNetworkId)
  }
*/

