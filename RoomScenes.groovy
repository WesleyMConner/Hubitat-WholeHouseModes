// ---------------------------------------------------------------------------------
// R O O M   S C E N E S
//
//  Copyright (C) 2023-Present Wesley M. Conner
//
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"); you may not use this file except in compliance with the
// License. You may obtain a copy of the License at
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ---------------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DeviceWrapper
import com.hubitat.app.DeviceWrapperList as DeviceWrapperList
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Hub as Hub
#include wesmc.UtilsLibrary

definition(
  parent: "wesmc:WholeHouseAutomation",
  name: "RoomScenes",
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: "Define and Execute RA2-aware Scenes for a Hubitat Room",
  category: "",           // Not supported as of Q3'23
  iconUrl: "",            // Not supported as of Q3'23
  iconX2Url: "",          // Not supported as of Q3'23
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
  page(name: "monoPage", title: "", install: true, uninstall: true)
}

void addRoomObjToSettings(String heading) {
  //-----------------------------------------------------------------------
  // ABSTRACT
  //   Ask client to select a single room and save the whole room object
  //   as "state.roomObj". This method must be called from a Hubitat App
  //   page's section.
  //
  // DESIGN NOTES
  //    There may not be an import for defining a RoomWrapper or a
  //    RoomWrapperList.
  //-----------------------------------------------------------------------
  paragraph emphasis(heading)
  ArrayList<LinkedHashMap> rooms = app.getRooms()
  List<Map<String, String>> roomPicklist = rooms
    .sort{ it.name }
    .collect{ [(it.id.toString()): it.name] }
  input(
    name: 'roomId',
    type: 'enum',
    title: 'Select the Room',
    submitOnChange: true,
    required: true,
    multiple: false,
    options: roomPicklist
  )
  if (settings.roomId) {
    state.roomObj = rooms.find{it.id.toString() == settings.roomId}
  }
}

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
  input(
    name: 'modesAsScenes',
    type: 'enum',
    title: 'Select the Modes',
    submitOnChange: true,
    required: false,
    multiple: true,
    options: locationNamePicklist
  )
  paragraph emphasis2('Create Custom Room Scene Name <em>..(optional)</em>')
  for (int i = 1; i<9; i++) {
    input(
      name: "cust${i}",
      type: 'text',
      title: "Custom Name",
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

void addModeIdToSceneToSettings (String heading) {
  // Abstract
  //   Ask client to select a scene for each current Hub mode and persist
  //   the results as "state.modeIdToScene" (a Map<String, String>).
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
    modeIdToScene[mode.id] = settings["${mode.id}ToScene"]
  }
  // Only promote mappings to state if there are zero remaining nulls.
  Map<String, String> nullMappings = modeIdToScene.findAll{it.value == null}
  state.modeIdToScene = nullMappings.size() == 0 ? modeIdToScene : [:]
}

Map monoPage() {
  return dynamicPage(
    name: "monoPage",
    title: "",
    install: true,
    uninstall: true
  ) {
    section {
      app.updateLabel("${state?.roomObj?.name ?: 'ROOM NAME PENDING'} Room Scenes")
      // paragraph heading("${state.roomObj.name ?: 'TBD'} Room Scenes") \
      paragraph important('<br/>Tab to register field changes!')
      addRoomObjToSettings('<b>Step 1:</b> Identify the Hubitat Room to control')
      if (state.roomObj) {
        addScenesToSettings ("<b>Step 2:</b> Identify <b>${state.roomObj.name}</b> Scenes")
      }
      if (state.scenes) {
        paragraph "<b>Current Scenes:</b> ${state.scenes?.join(', ') ?: '...none...'}"
        addModeIdToSceneToSettings("<b>Step 3:</b> Map Hub modes to ${state.roomObj.name} Scenes (for automation)")
      }
      if (state.modeIdToScene) {
        //--paragraph "state.modeIdToScene: ${state.modeIdToScene}"
        // READY TO IDENTIFY DEVICES
        paragraph emphasis("<b>Step 4:</b> Identify Devices for ${state.roomObj.name}")
        paragraph emphasis2('Identify Lutron AND <b>Non-Lutron</b>, <b>Non-VSW</b> Devices')
        input (
          name: 'nonLutronDevices',
          type: 'capability.switch',             // Enums in the future
          title: 'Select Non-Lutron Switches',
          submitOnChange: true,
          required: true,
          multiple: true
        )
        paragraph emphasis2('Identify <b>Lutron</b>, <b>Non-VSW</b> Devices')
        input (
          name: 'lutronRepeaters',
          type: 'device.LutronKeypad',           // Enums in the future
          title: 'Select Lutron Main Repeaters',
          submitOnChange: true,
          required: true,
          multiple: true
        )
      }
      if (settings.nonLutronDevices && settings.lutronRepeaters) {
        paragraph emphasis("<b>Step 5:</b> Configure Scenes for ${state.roomObj.name}")
        state.scenes.each{scene ->
          paragraph emphasis2("SCENE: ${scene}")
          settings.nonLutronDevices.each{device ->
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
          settings.lutronRepeaters.each{device ->
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

void initialize() {
  if (settings.LOG) log.trace 'initialize()'
  //--enforceMutualExclusion()
  //--enforceDefault()
  //--logSettingsAndState('initialize() about to enable subscription(s)')
  //--subscribe(settings.swGroup, "switch", buttonHandler)
}