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





String dah(DeviceWrapperList devices) {
  // Not helpful
  //   - d.getMetaPropertyValues()
  //   = d.type() DOES NOT EXIST
  //   = d.type always null
  String headerRow = """<tr>
    <th style='border: 1px solid black' align='center'>Id</th>
    <th style='border: 1px solid black' align='center'>Display Name</th>
    <th style='border: 1px solid black' align='center'>Room Id</th>
    <th style='border: 1px solid black' align='center'>Room Name</th>
    <th style='border: 1px solid black' align='center'>Supported Attributes</th>
    <th style='border: 1px solid black' align='center'>Data</th>
    <th style='border: 1px solid black' align='center'>Current States</th>
    <th style='border: 1px solid black' align='center'>Supported Commands</th>
    <th style='border: 1px solid black' align='center'>Parent Device ID</th>
    <th style='border: 1px solid black' align='center'>Disabled?</th>
    <th style='border: 1px solid black' align='center'>Type</th>
  </tr>"""
  String dataRows = settings.devices.collect{d ->
    """<tr>
      <td style='border: 1px solid black' align='center'>${d.id}</td>
      <td style='border: 1px solid black' align='center'>${d.displayName}</td>
      <td style='border: 1px solid black' align='center'>${d.getRoomId()}</td>
      <td style='border: 1px solid black' align='center'>${d.getRoomName()}</td>
      <td style='border: 1px solid black' align='center'>${d.getSupportedAttributes()}</td>
      <td style='border: 1px solid black' align='center'>${d.getData()}</td>
      <td style='border: 1px solid black' align='center'>${d.getCurrentStates}</td>
      <td style='border: 1px solid black' align='center'>${d.getSupportedCommands}</td>
      <td style='border: 1px solid black' align='center'>${d.getParentDeviceId()}</td>
      <td style='border: 1px solid black' align='center'>${d.isDisabled()}</td>
      <td style='border: 1px solid black' align='center'>${d.type}</td>
    </tr>"""
  }.join()
  return "<table>${headerRow}${dataRows}</table>"
}








// -------------------------------
// C L I E N T   I N T E R F A C E
// -------------------------------
preferences {
  page name: "monoPage", title: "", install: true, uninstall: true
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
        // addMainRepeatersToState()
        // - The containing room DOES NOT matter for repeaters.
        Map<String, DeviceWrapper> reps = [:]
        reps = settings.lutronRepeaters.collectEntries{[it.displayName, it]}
        state.mainRepeaters = reps
      }
      if (settings.lutronNonRepeaters && settings.lutronKeypads && settings.lutronPicos) {
        // addKeypadsToState()
        // - The containing room DOES NOT matter for keypads or picos.
        Map<String, DeviceWrapper> kpads1 = [:]
        kpads1 = settings.lutronNonRepeaters.collectEntries{[it.displayName, it]}
        Map<String, DeviceWrapper> kpads2 = [:]
        kpads2 = settings.lutronKeypads.collectEntries{[it.displayName, it]}
        Map<String, DeviceWrapper> picos = [:]
        picos = settings.lutronPicos.collectEntries{[it.displayName, it]}
        state.keypads = kpads1 + kpads2 + picos
      }
//      if (settings.switches) {
//        // addLutronSwitchesToState()
//        // - The containing room DOES matter for switches.
//        //Map<String, Map<String, DeviceWrapper>> settings.lutronSwitches = [:].withDefault { [:] }
//        Map settings.lutronSwitches = [:]  //.withDefault { [:] }
//        lutronSwitches = settings.switches
//                         .findAll{it.displayName.contains('lutron')}
//                         .collectEntries{[it.displayName, it]}
//                         .groupBy{it.value.getRoomName ?: 'UNKNOWN'}
//        state.lutronSwitches = lutronSwitches
        // addNonLutronSwitchesToState()
        // - The containing room DOES matter for switches.
//        Map<String, Map<String, DeviceWrapper>> nonLutronSwitches = [:]
//        nonLutronSwitches = settings.switches
//                            .findAll{it.displayName.contains('lutron') == false}
//                            .collectEntries{[it.displayName, it]}
//                            .groupBy{it.value.getRoomName ?: 'UNKNOWN'}
//        state.nonLutronSwitches = nonLutronSwitches
//      }
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