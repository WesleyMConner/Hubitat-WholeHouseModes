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
// Design Notes
//   - Multiple DeviceWrapperList instances arise due to multiple input() statements.
//   - Initialization of 'state' includes making immutable copies of DeviveWrapper
//     instances, gathered from 'settings'.
// ---------------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DeviceWrapper
import com.hubitat.app.DeviceWrapperList as DeviceWrapperList
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Hub as Hub

// https://docs2.hubitat.com/en/developer/allowed-imports
import com.hubitat.app.ChildDeviceWrapper as ChildDeviceWrapper
import com.hubitat.app.EventSubscriptionWrapper as EventSubscriptionWrapper
import com.hubitat.app.InstalledAppWrapper as InstalledAppWrapper
import com.hubitat.app.ParentDeviceWrapper as ParentDeviceWrapper
import com.hubitat.hub.domain.State as State
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Hub as Hub
import com.hubitat.hub.domain.Location as Location

#include wesmc.UtilsLibrary
#include wesmc.DeviceLibrary

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
        paragraph heading("Whole House Automation") \
          + comment('<br/>The "settings" solicited here are at '\
              + '"parent-scope" and are available to Child applications.')
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
        // Decide what rooms to support at parent scope? Then ...
        // - InstalledAppWrapper addChildApp(String namespace, String name, String label, Map properties = null)
        // - Leverage the Map to push applicable data?
        // - ...AND, mutable.asImmutable() ???
        app(
          name: "RoomScenes",
          appName: "RoomScenes",
          namespace: "wesmc",
          title: "<b>Add Rooms</b>",
          multiple: true//,
          //properties = [
          //  roomName: 'Den',
          //  switches: settings.switches
          //]
        )
        /*
        */
        /*
        InstalledAppWrapper myKid = app.addChildApp(
          name: 'RoomScenes',
          namespace: 'wesmc',
          label: 'Den',
          properties = [
            roomName: 'Den',
            switches: settings.switches
          ]
        )
        */
        paragraph "myKid: ${myKid}"
      }
      paragraph comment("""Whole House Automation - @wesmc, \
        <a href='https://github.com/WesleyMConner/Hubitat-WholeHouseAutomation' \
        target='_blank'> <br/>Click for more information</a>""")
    }
  }
}

// ---------------------------------
// J U S T   F O R   T H E   K I D S
// ---------------------------------
List<String> getLetters() { return ['A', 'B', 'C', 'D', 'E', 'F']}


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
  /*
  state.deviceIdToRoomName = mapDeviceIdAsStringToRoomName()
  identifyParticipatingDevices('Identify Participating Devices')

  if (settings.lutronRepeaters) {
    state.repeaterIds = settings.lutronRepeaters.collect{it.id}
  }
  if (settings.lutronNonRepeaters && settings.lutronKeypads && settings.lutronPicos) {
    addKeypadsToState()
  }
  if (settings.switches) {
    //--NOT-READY--        addRoomToDeviceMapsToState()
  }
  if (state.mainRepeaters
      && state.keypads
      // && state.roomToLutronDevice
      // && state.roomToLutronLED
      // && state.roomToNonLutron
  ) {
    paragraph "mainRepeaters: ${state.mainRepeaters.collect{it.value.displayName}}"
    paragraph "keypads: ${state.keypads.collect{it.value.displayName}}"
    //--NOT-READY--        showDevicesByRoom('Lutron Device', state.roomToLutronDevice)
    //--NOT-READY--        showDevicesByRoom('Lutron LED', state.roomToLutronLED)
    //--NOT-READY--        showDevicesByRoom('Non-Lutron Device', state.roomToNonLutron)
    // paragraph """roomToLutronDevice: """
    // paragraph """[${state.roomToLutronDevice.each{k, v -> "<br/>${k}: ${v}"}}].join()"""
    // paragraph """roomToLutronLED: """
    // paragraph """${state.roomToLutronLED.each{k, v -> "<br/>${k}: ${v}"}}"""
    // paragraph """roomToNonLutron: """
    // paragraph """${state.roomToNonLutron.each{k, v -> "<br/>${k}: ${v}"}}"""
  }
  if (state.roomScenes) {
  }
  */
}

// -----
// T B D
// -----

Map<String, String> mapDeviceIdAsStringToRoomName () {
  Map<String, String> results = [:]
  app.getRooms().each{room ->
    room.deviceIds.each{deviceIdAsInteger ->
      results[deviceIdAsInteger.toString()] = room.name ?: 'UNKNOWN'
    }
  }
//--DEBUG--  paragraph "DEBUG XXX: ${results}"
  return results
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

List<DeviceWrapper> keepLutron (List<DeviceWrapper> dList) {
  return dList.findAll{ (it.displayName.toString().contains('lutron') && !it.displayName.toString().contains('LED')) }
}

List<DeviceWrapper> keepLED (List<DeviceWrapper> dList) {
  return dList.findAll{ it.displayName.toString().contains('LED') }
}

List<DeviceWrapper> keepNonLutron (List<DeviceWrapper> dList) {
  return dList.findAll{ !(it.displayName.toString().contains('lutron')) }
}

/*
void addRoomToDeviceMapsToState() {
  // Design Note:
  //   - Use "device.displayName.toString().contains('...')".
  //   - The use of "toString()" is critical for proper parsing.
  Map<String, List<DeviceWrapper>> roomToSwitches = getRoomToSwitches()
  //Map<String, List<DeviceWrapper>> den = roomToSwitches.findAll{it.key == 'Den'}
  Map<String, List<DeviceWrapper>> zzz = roomToSwitches.findAll{['UNKNOWN', 'Den'].contains(it.key)}
// (room2switches) ${roomToSwitches}
//--DEBUG--  paragraph """DEBUG:
//--DEBUG--(raw) ${zzz}
//--DEBUG--(led) ${zzz.collectEntries{r, dlist ->
//--DEBUG--  [r, keepLED(dList)]
//--DEBUG--}}
//--DEBUG--(non-lutron) ${zzz.collectEntries{r, dlist ->
//--DEBUG--  [r, keepNonLutron(dList)]
//--DEBUG--}}
//--DEBUG--(lutron) ${zzz.collectEntries{r, dList ->
//--DEBUG--  [r, keepLutron(dList)]
//--DEBUG--}}
"""
//==>  Map<String, List<DeviceWrapper>> roomToLutronDevice = roomToSwitches.findAll{ r, dList ->
//==>    dList.findAll{ d -> d.displayName.toString().contains('lutron') }
//==>  }

//==>String testString = '(lutron-100 something with LED)'
//==>paragraph """EXPLORE \
//==>${testString.contains('lutron')} \
//==>${testString.contains('lutron') && !testString.contains('LED')} \
//==>${!testString.contains('lutron')}"""

//  paragraph """DEBUG:
//<b>original:</b> ${roomToSwitches}
//<b>roomToLutronDevice:</b> ${roomToSwitches.findAll{ r, dList -> keepLutron(dList)}}
//"""
//==>    (it.value.displayName.toString().contains('lutron') && !it.value.displayName.toString().contains('LED'))
//==>  }
  state.roomToLutronDevice = roomToLutronDevice
  Map<String, List<DeviceWrapper>> roomToLutronLED = roomToSwitches.findAll{
    it.value.displayName.toString().contains('LED')
  }
  state.roomToLutronLED = roomToLutronLED
  Map<String, List<DeviceWrapper>> roomToNonLutron = roomToSwitches.findAll{
    !it.value.displayName.toString().contains('lutron')
  }
  state.roomToNonLutron = roomToNonLutron
}
*/

/*
void showDevicesByRoom (String label, Map<String, List<DeviceWrapper>> roomToDevice) {
  String summary = roomToDevice.collect{ r, dList ->
    bullet("<b>${r}</b>: ${dList.collect{it.displayName}.join(', ')}")
  }.join('<br/>')
  paragraph "<b>${label}:</b><br/>${summary}"
}
*/










