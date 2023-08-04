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
      String assignedRoom = parent.assignChildAppRoomName(app.id)
      app.updateLabel(assignedRoom)
      paragraph heading(assignedRoom)
      ArrayList<LinkedHashMap> modes = location.getModes()   // Type def for Mode is TBD
      input(
        name: 'modesAsScenes',
        type: 'enum',
        title: '<span style="margin-left: 10px;">' \
               + '<b>Create some/all scene names based on Hubitat Modes names:</b>' \
               + ' <em>(tab to register changes)</em>' \
               + '</span>',
        //title: '<b>Option 1:</b> Create scenes based on some/all Hubitat Modes names',
        submitOnChange: true,
        required: false,
        multiple: true,
        options: modes.collect{it.name}
      )
      LinkedHashMap<String, String> indexToCustomScene = [
        'cust1': settings.cust1 ?: 'TBD', 'cust2': settings.cust2 ?: 'TBD',
        'cust3': settings.cust3 ?: 'TBD', 'cust4': settings.cust4 ?: 'TBD',
        'cust5': settings.cust5 ?: 'TBD', 'cust6': settings.cust6 ?: 'TBD',
        'cust7': settings.cust7 ?: 'TBD', 'cust8': settings.cust8 ?: 'TBD',
        'cust9': settings.cust9 ?: 'TBD'
      ]
      LinkedHashMap<String, String> haveValues = indexToCustomScene.findAll{it.value != 'TBD'}
      String firstEmptyKey = indexToCustomScene.findAll{it.value == 'TBD'}.keySet().first()
      LinkedHashMap<String, String> needsValue = indexToCustomScene.findAll{ it.key == firstEmptyKey }
      LinkedHashMap<String, String> customScenes = needsValue + haveValues.sort()
      paragraph '<b>Create some/all ad hoc scene names:</b>'
      customScenes.eachWithIndex{ settingName, settingValue, index ->
        input(
          name: settingName,
          type: 'text',
          title: "<b>Custom ${index+1}:</b>",
          width: 1,
          submitOnChange: true,
          required: false,
          defaultValue: settingValue
        )
      }
      paragraph '<b>Select Scenes for Automation Modes:</b>'
      List<String> scenes = (settings.modesAsScenes ?: []) + (indexToCustomScene.findAll{it.value != 'TBD'}.collect{it.value} ?: [])
      modes.each{mode ->
        Boolean modeNameIsSceneName = scenes.find{it == mode.name} ? true : false
        input(
          name: "${mode.id}ToScene",
          type: 'enum',
          title: mode.name,
          width: 2,
          submitOnChange: true,
          required: true,
          multiple: false,
          options: scenes,
          defaultValue: modeNameIsSceneName ? mode.name : ''
        )
      }
      //----> Keypad Buttons that Trigger Scenes
      // Use a picklist to narrow the required keypads
      List<DeviceWrapper> keypads = parent.getKeypads()
      paragraph "keypads: ${keypads}"

      //----> Non-Lutron Devices with per-Scene levels
      List<DeviceWrapper> nonLutronDevices = parent.getNonLutronDevices(assignedRoom)
      paragraph "nonLutronDevices: ${nonLutronDevices}"

      //----> Main Repeater Buttons that Realize Scenes
      List<DeviceWrapper> mainRepeaters = parent.getMainRepeaters()
      paragraph "mainRepeaters: ${mainRepeaters}"

      //----> LEDs that are Set on Scene Activaton
      // https://community.hubitat.com/t/bug-report-lutron-ra2-main-repeater-not-reporting-led-status-correctly/63317/7`
      // It appears that your suggestion about subtracting 80 for LED numbers less
      // than 100 and subtracting 100 from those above 100 will work with the
      // outcome of LED number being the same as button number.
      // ----------
      // NOTE: The required keypads likely match the scene-triggering buttons
      List<DeviceWrapper> leds = parent.getLedDevices()
      paragraph "leds: ${leds}"

      //----> Lutron Devices that Potentially Disrupt Scenes
      //----> NOTE: REP LEDs MAY BE A PREFERABLE MECHANIS
      List<DeviceWrapper> lutronDevices = parent.getLutronDevices(assignedRoom)
      paragraph "lutronDevices: ${lutronDevices}"

      //---->
      // R E V I E W   D A T A
      paragraph "scenes: ${scenes}"
      paragraph "assignedRoom: ${assignedRoom}"
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
//     <td>Device Name</td>${oomHeadingsHtml(room.scenes)}
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

void initialize() {
  if (settings.LOG) log.trace 'initialize()'
  //--enforceMutualExclusion()
  //--enforceDefault()
  //--logSettingsAndState('initialize() about to enable subscription(s)')
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

