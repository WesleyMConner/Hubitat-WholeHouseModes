// ------------------------------------------------------------------------
// R O O M   S C E N E S
//   Copyright (C) 2023-Present Wesley M. Conner
//   Licensed under the Apache License, Version 2.0
//   http://www.apache.org/licenses/LICENSE-2.0
// ------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DeviceWrapper
import com.hubitat.app.DeviceWrapperList as DeviceWrapperList
import com.hubitat.app.InstalledAppWrapper as InstalledAppWrapper
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Hub as Hub
import com.hubitat.hub.domain.Location as Location
#include wesmc.UtilsLibrary
#include wesmc.DeviceLibrary

definition(
  parent: 'wesmc:WholeHouseAutomation',
  name: 'RoomScenes',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Define and Execute RA2-aware Scenes for a Hubitat Room',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  installOnOpen: false,
  documentationLink: '',  // TBD
  videoLink: '',          // TBD
  importUrl: '',          // TBD
  oauth: false,           // Even if used, must be manually enabled.
  singleInstance: false
)

String getRoomName (Long appId) {
  if (!parent) log.error "getRoomName() called before parent was defined."
  List<String> roomNames = parent.getPartipatingRooms()
  log.trace "Participating roomNames: ${roomNames}"
  List<InstalledAppWrapper> kidApps = parent.getChildApps()
  log.trace "Current kidApps: ${kidApps}"
  Map<String, String> kidIdToRoomName = \
    kidApps.collectEntries{ kid ->
      [ kid.id.toString(), roomNames.contains(kid.label) ? kid.label : null ]
    }
  log.trace "Current kidIdToRoomName: ${kidIdToRoomName}"
  Map<String, Boolean> roomNameToKidId = roomNames.collectEntries{[it, false]}
  kidIdToRoomName.each{ kidId, roomName ->
    if (roomName) roomNameToKidId[roomName] = kidId
  }
  log.trace "Current roomNameToKidId: ${roomNameToKidId}"
  log.trace "roomNameToKidId: ${roomNameToKidId}"
  log.trace "roomNameToKidId.findAll{!it.value}: ${roomNameToKidId.findAll{!it.value}}"
  log.trace "roomNameToKidId.findAll{!it.value}.keySet(): ${roomNameToKidId.findAll{!it.value}.keySet()}"
  log.trace "roomNameToKidId.findAll{!it.value}.keySet().first(): ${roomNameToKidId.findAll{!it.value}.keySet().first()}"
  String result = kidIdToRoomName[appId.toString()] ?: roomNameToKidId.findAll{!it.value}.keySet().first()
  log.trace "result: ${result}"
  return result
}

// -------------------------------
// C L I E N T   I N T E R F A C E
// -------------------------------
preferences {
  page(name: 'monoPage', title: '', install: true, uninstall: true)
}

Map monoPage() {
  return dynamicPage(
    name: 'monoPage',
    title: '',
    install: true,
    uninstall: true
  ) {
    section {
      String assignedRoom = getRoomName(app.id)
      paragraph "app.id: ${app.id}, assignedRoom: ${assignedRoom} "
      app.updateLabel(assignedRoom)
      paragraph heading(assignedRoom)
      List<String> modes = location.getModes()   // No Mode for expected List<Mode>
      input(
        name: 'modesAsScenes',
        type: 'enum',
        title: '<b>Use Hubitat Modes to name Room Scenes</b> <em>..(optional)</em>',
        submitOnChange: true,
        required: false,
        multiple: true,
        options: modes.collect{it.name}
      )
      Integer max = 1
      Boolean keepLooping = true
      /*
      while (keepLooping && max <= 9) {
        //paragraph "DEBUG-ALPHA keepLooping: ${keepLooping}, max: ${max}"
        for (int i = 0; i < max; i++) {
          //paragraph "DEBUG-GAMMA-${i}"
      */
          Integer i=0
          input(
            name: "cust${i}",
            type: 'text',
            title: 'Add Scene Name<br/><em>(optional)</em>',
            width: 3,
            submitOnChange: true,
            required: false,
            defaultValue: 'n/a'
          )
      /*
          //if (settings["cust${i}"]) {
          //  max++
          //  paragraph "settings[cust${i}]: ${settings["cust${i}"]}, max: ${max}"
          //}
        }
      }
      */
      //----------------------------------
      // P U R G E   S T A T E   B E L O W
      //----------------------------------
      modes.each{mode ->
        Boolean modeNameIsSceneName = state.scenes.find{it == mode.name} ? true : false
        input(
          name: "${mode.id}ToScene",
          type: 'enum',
          title: "Scene for ${mode.name}",
          width: 2,
          submitOnChange: true,
          required: true,
          multiple: false,
          options: state.scenes,
          defaultValue: modeNameIsSceneName ? mode.name : ''
        )
      }
      ////
      //// EXIT
      ////
      input(
        name: 'exit',
        type: 'bool',
        title: comment('Exit?'),
        submitOnChange: true,
        required: true,
        defaultValue: false
      )
    }
  }
}

      /*
      if (state.scenes) {
        paragraph "<b>Current Scenes:</b> ${state.scenes?.join(', ') ?: '...none...'}"
        addModeIdToSceneToSettings("Map Hub modes to ${roomName} Scenes (for automation)")
      }
      if (state.modeIdToScene) {
      }
      */

/*
        paragraph "state.modeIdToScene: ${state.modeIdToScene}"
        paragraph "<b>Main Repeaters:</b><br/>${parent.getMainRepeaters().collect{it.displayName}.join('<br/>')}"
        paragraph "<b>Keypads:</b><br/>${parent.getMainRepeaters().collect{it.displayName}.join('<br/>')}"
        paragraph "<b>Lutron Devices:</b><br/>${parent.getLutronDevices(roomName).collect{it.displayName}.join('<br/>')}"
        paragraph "<b>Lutron LEDs:</b><br/>${parent.getLutronLedDevices(roomName).collect{it.displayName}.join('<br/>')}"
        paragraph "<b>Non-Lutron Devices:</b><br/>${parent.getNonLutronDevices(roomName).collect{it.displayName}.join('<br/>')}"
        paragraph emphasis("Configure Scenes for ${roomName}")
        state.scenes.each{scene ->
          paragraph emphasis2("SCENE: ${scene}")
          parent.getNonLutronDevices(roomName).each{device ->
            input(
              name: "${scene}.${device.id}",
              type: 'number',
              title: "<b>${device.displayName}</b><br/>Level 0..100",
              width: 2,
              submitOnChange: true,
              required: true,
              multiple: false,
              defaultValue: 0
            )
          }
          parent.getMainRepeaters().each{device ->
            input(
              name: "${scene}.${device.id}",
              type: 'number',
              title: "<b>${device.displayName}</b><br/>Button #",
              width: 2,
              submitOnChange: true,
              required: true,
              multiple: false,
              defaultValue: 0
            )
          }
        }
*/

/*
void deviceSceneInputs(DeviceWrapper d, List<String> scenes) {
  scenes.collect{scene, index ->
    input(
      name: "${d.id}:${scene}",
      type: 'integer',
      width: 1,
      title: ${index == 1 ? d.displayName : ''},
      defaultValue: 0
    )
  }
}
*/

// String addRoomHtml(LinkedHashMap room) {
//   return """<table>
//     ${roomHeadingsHtml(room.scenes)}
//     ${roomRowHtml(settings.devices[0], room.scenes)}
//   </table>"""
//   return """<table>
//     <td>Device Name</td>${roomHeadingsHtml(room.scenes)}
//     ${room.nonLutronId.collect{ d -> roomRowHtml(d, room.scenes) }}
//     ${room.mainRepId.collect{ d -> roomRowHtml(d, room.scenes) }}
//    </table>"""
// }

//      if (settings.roomObj) {
//        paragraph "roomObj: ${settings.roomObj}"
//      }

/*
      paragraph "settings keys are >${settings.keySet()}<"
      if (settings.devices && settings.repeaters) {
        // Leverag types for coding.
        List<String> scenes = ['alpha', 'beta', 'gamma']
        Map<Integer, String> devices = settings.devices
        Map<Integer, String> repeaters = settings.repeaters
        Map<String, Integer> deviceValues = [:]
        Map<String, Integer> repeaterValues = [:]

        // Preserve room state
        state.room = [:]
        state.room.scenes = scenes
        state.room.devices = devices
        state.room.repeaters = repeaters
        state.room.deviceValues = deviceValues
        state.room.repeaterValues = repeaterValues

        // Isolation test of roomCellHtml(...)
              ${roomCellHtml(settings.devices[0], "BIRDY")}
        // Isolation test of roomRowHtml(...)
            ${roomHeadingsHtml(room.scenes)}
            ${roomRowHtml(settings.devices[0], room.scenes)}
*/

void installed() {
  if (settings.LOG) log.trace 'installed()'
  //--not-used-- unsubscribe()  // A D D E D   T O   D E B U G   I S S U E
  initialize()
}

void updated() {
  if (settings.LOG) log.trace 'updated()'
  unsubscribe()  // Suspend event processing to rebuild state variables.
  unschedule()   // Placeholder for any future scheduled jobs.
  initialize()
}

/*
void addScenesToSettings (String heading) {
  paragraph emphasis(heading)
  paragraph emphasis2('Use Hubitat Modes to name Room Scenes <em>..(optional)</em>')
  // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  // !!! UNKNOWN IMPORT FOR ModeWrapper or ModeWrapperList !!!
  // !!!   Mode appears to have mode.id, mode.name, ...    !!!
  // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  ArrayList<LinkedHashMap> modes = location.modes
  state.modes = modes
  List<String> locationNamePicklist = state.modes.collect{it.name}
    for (int i = 1; i<9; i++) {
    input(
      name: "cust${i}",
      type: 'text',
      title: 'Create Scene Name',
      width: 3,
      submitOnChange: true,
      required: false,
      defaultValue: 'n/a'
    )
  }
  List<String> scenes = (
    modesAsScenes.findAll{it}
    + [settings.cust1, settings.cust2, settings.cust3,
       settings.cust4, settings.cust5, settings.cust6,
       settings.cust7, settings.cust8].findAll{it && it != 'n/a'}
  ).sort{}
  state.scenes = scenes
}
*/

/*
void addModeIdToSceneToSettings (String heading) {
  // Abstract
  //   Ask client to select a scene for each current Hub mode and persist
  //   the results as 'state.modeIdToScene' (a Map<String, String>).
  //
  // Design Notes
  //   - The mapping facilitates state.currentScene == 'AUTO'
  //   - Refresh the mapping if/when site modes are changed.
  paragraph emphasis(heading)
  Map<String, String> modeIdToRoomScene
  ArrayList<LinkedHashMap> modes = location.modes
  Map<String, String> modeIdToScene = [:]
  modes.each{mode ->
    Boolean modeNameIsSceneName = state.scenes.find{it == mode.name} ? true : false
    modeIdToScene[mode.id] = settings["${mode.id}ToScene"]
  }
  // Only promote mappings to state if there are zero remaining nulls.
  Map<String, String> nullMappings = modeIdToScene.findAll{it.value == null}
  state.modeIdToScene = nullMappings.size() == 0 ? modeIdToScene : [:]
}
*/

void initialize() {
  if (settings.LOG) log.trace 'initialize()'
  //--enforceMutualExclusion()
  //--enforceDefault()
  //--logSettingsAndState('initialize() about to enable subscription(s)')
  //--subscribe(settings.swGroup, 'switch', buttonHandler)
}