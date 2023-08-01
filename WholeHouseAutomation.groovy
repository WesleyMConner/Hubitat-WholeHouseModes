// ---------------------------------------------------------------------------------
// P U S H B U T T O N   S W I T C H E S
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
  name: "WholeHouseAutomation",
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Whole House Automation using Modes, RA2 and Room Overrides',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  singleInstance: true
)

// -------------------------------
// C L I E N T   I N T E R F A C E
// -------------------------------
preferences {
  page name: "monoPage", title: "", install: true, uninstall: true
}

Map<String, String> mapDeviceIdAsStringToRoomName () {
  Map<String, String> results = [:]
  app.getRooms().each{room ->
    room.deviceIds.each{deviceIdAsInteger ->
      results[deviceIdAsInteger.toString()] = room.name
    }
  }
  return results
}

Map<String, String> getRoomNameForDeviceId(Integer deviceId) {
  return app.getRooms().deviceIds.collect{}

}

void identifyParticipatingDevices(heading) {
  paragraph emphasis("${heading}<br/>") \
    + comment(
      'The devices selected during this step are organized by room and '
       + 'post-processed for presentation on room-specific screens.'
      )
  collapsibleInput (
    blockLabel: "Prospective Lutron 'Main Repeaters'",
    name: 'lutronRepeaters',
    title: "Identify Lutron Main Repeaters<br/>" \
      + "${comment('Used to invoke in-kind Lutron scenes.')}",
    type: 'device.LutronKeypad'
  )
  collapsibleInput (
    blockLabel: "Prospective Lutron 'Miscellaneous Keypads",
    name: 'lutronNonRepeaters',
    title: "Identify Non-Repeater Lutron Devices<br/>" \
      + "${comment('Used to trigger room scenes.')}",
    type: 'device.LutronKeypad'
  )
  collapsibleInput (
    blockLabel: "Probable 'Lutron Keypads'",
    name: 'lutronKeypads',
    title: "Identify Lutron SeeTouch Keypads<br/>" \
      + "${comment('Used to trigger room scenes.')}",
    type: 'device.LutronSeeTouchKeypad'
  )
  collapsibleInput (
    blockLabel: "Probable 'Lutron Picos'",
    name: 'lutronPicos',
    title: "Identify Lutron Picos<br/>" \
      + "${comment('Used to trigger room scenes and/or devices.')}",
    type: 'device.LutronFastPico'
  )
  collapsibleInput (
    blockLabel: 'Switches/Dimmers',
    name: 'switches',
    title: "Identify Lutron AND Non-Lutron Switches/Dimmers<br/>" \
      + "${comment( \
          'Non-Lutron device levels are set by Room Scenes.<br/>'\
          + 'Lutron device level facilitate MANUAL override of scenes.<br/>' \
          + 'Exclude VSWs (virtual switches).' \
        )}",
    type: 'capability.switch'
  )
}

void addMainRepeatersToState() {
  // - The containing room DOES NOT matter for repeaters.
  Map<String, DeviceWrapper> reps = [:]
  reps = settings.lutronRepeaters.collectEntries{[it.displayName, it]}
  state.mainRepeaters = reps
}

void addKeypadsToState() {
  // - The containing room DOES NOT matter for keypads or picos.
  Map<String, DeviceWrapper> kpads1 = [:]
  kpads1 = settings.lutronNonRepeaters.collectEntries{[it.displayName, it]}
  Map<String, DeviceWrapper> kpads2 = [:]
  kpads2 = settings.lutronKeypads.collectEntries{[it.displayName, it]}
  Map<String, DeviceWrapper> picos = [:]
  picos = settings.lutronPicos.collectEntries{[it.displayName, it]}
  state.keypads = kpads1 + kpads2 + picos
}

Map<String, List<DeviceWrapper>> getRoomToSwitches() {
  Map<String, List<DeviceWrapper>> roomToSwitches = [:]
  app.getRooms().each{ room -> roomToSwitches[room.name] = [] }
  roomToSwitches['UNKNOWN'] = []
  settings.switches.each{ sw ->
    String switchRoom = state.deviceIdToRoomName[sw.id] ?: 'UNKNOWN'
    roomToSwitches[switchRoom] += sw
  }
  return roomToSwitches
}

Map monoPage() {
  return dynamicPage(name: "monoPage") {
    section {
      if (app.getInstallationState() != 'COMPLETE') {
        paragraph heading("Whole House Automation")
        paragraph emphasis('Before you can create <b>Room Scene(s)</b> ...')
        paragraph normal('Push the <b>Done</b> button.')
        paragraph bullet('This <em>parent application</em> will be installed.')
        paragraph bullet('The parent collects data used by <b>Room Scenes</b>.')
        paragraph bullet('It also groups <b>Room Scenes</b> (children) together.')
      } else {
        // Err on the side of refreshing device-to-room mappings frequently.
        state.deviceIdToRoomName = mapDeviceIdAsStringToRoomName()
        identifyParticipatingDevices('<b>Step 1:</b> Identify Participating Devices')
      }
      // ------------------      -------------------- -----------------
      //       SETTINGS              INTERMEDIATE          STATE
      // ------------------      -------------------- -----------------
      //    lutronRepeaters ---> reps --------------> mainRepeaters
      // lutronNonRepeaters ---> kpads1 ->\
      //      lutronKeypads ---> kpads2 -->\
      //        lutronPicos ---> picos  --->+-------> keypads
      //           switches +--> lutronSwitches ----> lutronSwitches
      //                     \-> nonLutronSwitches -> nonLutronSwitches
      // ------------------      -------------------- -----------------
      if (settings.lutronRepeaters) {
        addMainRepeatersToState()
      }
      if (settings.lutronNonRepeaters && settings.lutronKeypads && settings.lutronPicos) {
        addKeypadsToState()
      }
      if (settings.switches) {
        //--paragraph "settings.switches ${settings.switches} ${settings.switches.size()}"
        Map<String, List<DeviceWrapper>> roomToSwitches = getRoomToSwitches()
        //--paragraph "roomToSwitches: ${roomToSwitches}"

        // Design Note:
        //   - The device's displayName needs to be processed with toString() before
        //     using .contains('')
        Map<String, List<DeviceWrapper>> lutronNoLED = roomToSwitches.findAll{
          it.value.displayName.toString().contains('lutron') && !it.value.displayName.toString().contains('LED')
        }
        paragraph emphasis('lutronNoLED')
        paragraph """[${lutronNoLED.each{k, v -> "<br/>${k}: ${v}"}}].join()"""

        Map<String, List<DeviceWrapper>> lutronLED = roomToSwitches.findAll{
          it.value.displayName.toString().contains('LED')
        }
        paragraph emphasis('lutronLED')
        paragraph """${lutronLED.each{k, v -> "<br/>${k}: ${v}"}}"""

        Map<String, List<DeviceWrapper>> notLutron = roomToSwitches.findAll{
          !it.value.displayName.toString().contains('lutron')
        }
        paragraph emphasis('notLutron')
        paragraph """${notLutron.each{k, v -> "<br/>${k}: ${v}"}}"""
      }
      if (state.mainRepeaters && state.keypads /* && state.lutronSwitches
         && state.lutronSwitches */) {
        paragraph "state.mainRepeaters (displayName): ${state.mainRepeaters.collect{it.value.displayName}}"
        paragraph "state.keypads (displayName): ${state.keypads.collect{it.value.displayName}}"

//        paragraph "settings.switches: ${settings.switches}"

//        //paragraph "state.lutronSwitches (roomName): " \
//          + "${state.lutronSwitches.collect{it.key}}"
          //paragraph "state.nonLutronSwitches: ${state.nonLutronSwitches.keySet()}"
        app(
          name: "RoomScenes",
          appName: "RoomScenes",
          namespace: "wesmc",
          title: "<b>Add Rooms</b>",
          multiple: true
        )
      }
      paragraph comment("""Whole House Automation - @wesmc, \
        <a href='https://github.com/WesleyMConner/Hubitat-WholeHouseAutomation' \
        target='_blank'> <br/>Click for more information</a>""")
    }
  }
}









// ---------------------------------------------------
// I N I T I A L I Z A T I O N   &   O P E R A T I O N
// ---------------------------------------------------
void installed() {
  if (settings.LOG) log.trace 'installed()'
  initialize()
}

void updated() {
  if (settings.LOG) log.trace 'updated()'
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void initialize() {
  log.info """initialize() with ${childApps.size()} Automation Groups<br/>
    ${childApps.each({ child -> "&#x2022;&nbsp;${child.label}" })}
  """
}