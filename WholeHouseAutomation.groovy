// ---------------------------------------------------------------------------------
// W H O L E   H O U S E   A U T O M A T I O N
//
//   Copyright (C) 2023-Present Wesley M. Conner
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
import com.hubitat.app.InstalledAppWrapper as InstalledAppWrapper
//import com.hubitat.app.ChildDeviceWrapper as ChildDeviceWrapper
//import com.hubitat.app.EventSubscriptionWrapper as EventSubscriptionWrapper
//import com.hubitat.app.ParentDeviceWrapper as ParentDeviceWrapper
//import com.hubitat.hub.domain.Event as Event
//import com.hubitat.hub.domain.Event as Event
//import com.hubitat.hub.domain.Hub as Hub
//import com.hubitat.hub.domain.Location as Location
//import com.hubitat.hub.domain.State as State
#include wesmc.DeviceLibrary
#include wesmc.UtilsLibrary

definition(
  name: 'WholeHouseAutomation',
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
  page name: 'monoPage', title: '', install: true, uninstall: true
}

List<String> getPartipatingRooms() {
  log.trace "entered getParticipatingRoom()"
  if (!settings.roomNames) {
    log.Error('getParticipatingRooms() called before identifying participating rooms.')
  }
  log.trace "getParticipatingRoom() with ${settings.roomNames}"
  return settings.roomNames
}

Map monoPage() {
  return dynamicPage(name: 'monoPage') {
    section {
      if (app.getInstallationState() != 'COMPLETE') {
        paragraph heading('Whole House Automation')
        paragraph emphasis('Before you can create <b>Room Scene(s)</b> ...')
        paragraph normal('Push the <b>Done</b> button.')
        paragraph bullet('This <em>parent application</em> will be installed.')
        paragraph bullet('The parent collects data used by <b>Room Scenes</b>.')
        paragraph bullet('It also groups <b>Room Scenes</b> (children) together.')
      } else {
        paragraph heading('Whole House Automation') \
          + comment('<br/>The "settings" solicited here are at '\
              + '"parent-scope" and are available to Child applications.')
        List<String> roomPicklist = app.getRooms().collect{it.name}.sort()
        //--paragraph "roomPicklist: >${roomPicklist}<"
        collapsibleInput(
          blockLabel: 'Participating Rooms',
          name: 'roomNames',
          type: 'enum',
          title: 'Select the Participating Rooms',
          options: roomPicklist
        )
        collapsibleInput (
          blockLabel: "Prospective Lutron 'Main Repeaters'",
          name: 'lutronRepeaters',
          title: 'Identify Lutron Main Repeaters<br/>' \
            + "${comment('Used to invoke in-kind Lutron scenes.')}",
          type: 'device.LutronKeypad'
        )
        collapsibleInput (
          blockLabel: "Prospective Lutron 'Miscellaneous Keypads'",
          name: 'lutronNonRepeaters',
          title: 'Identify Non-Repeater Lutron Devices<br/>' \
            + "${comment('Used to trigger room scenes.')}",
          type: 'device.LutronKeypad'
        )
        collapsibleInput (
          blockLabel: "Probable 'Lutron Keypads'",
          name: 'lutronKeypads',
          title: 'Identify Lutron SeeTouch Keypads<br/>' \
            + "${comment('Used to trigger room scenes.')}",
          type: 'device.LutronSeeTouchKeypad'
        )
        collapsibleInput (
          blockLabel: "Probable 'Lutron Picos'",
          name: 'lutronPicos',
          title: 'Identify Lutron Picos<br/>' \
            + "${comment('Used to trigger room scenes and/or devices.')}",
          type: 'device.LutronFastPico'
        )
        collapsibleInput (
          blockLabel: 'Switches/Dimmers',
          name: 'switches',
          title: 'Identify Lutron AND Non-Lutron Switches/Dimmers<br/>' \
            + "${comment( \
                'Non-Lutron device levels are set by Room Scenes.<br/>'\
                + 'Lutron device level facilitate MANUAL override of scenes.<br/>' \
                + 'Exclude VSWs (virtual switches).' \
              )}",
          type: 'capability.switch'
        )
        if (settings.roomNames) {
          /*
          // Identify Incomplete Room
          List<InstalledAppWrapper> kidApps = getChildApps()
          Map <String, InstalledAppWrapper> roomToRoomScenes \
            = settings.roomNames.collectEntries{ room ->
                List<InstalledAppWrapper> matches = kidApps.findAll{ kid -> kid.label.contains(room) }
                InstalledAppWrapper match = matches ? matches.first() : null
                match ? [room, match] : [room, null]
              }
          paragraph """roomToRoomScenes: ${roomToRoomScenes.collectEntries{[it.key, it.value?.label]}}"""
          List<String> incompleteRoom = roomToRoomScenes.findAll{ it.value == null }.collect{ it.key }
          paragraph "incompleteRoom: ${incompleteRoom}"
          */
          //paragraph "getIncompleteRooms: ${getIncompleteRooms()}"
          //paragraph "this: ${this}"
          //paragraph "mapRoomToChildApp(this): ${mapRoomToChildApp(this)}"
          //getIncompleteRooms()

          app(
            name: 'RoomScenes',
            appName: 'RoomScenes',
            namespace: 'wesmc',
            parent: 'WholeHouseAutomation',
            title: """<b>Add Rooms</b> ${comment(
              'New instances are automatically assigned a Room Name from available <em>Participating Rooms</em>.'
            )}""",
            multiple: true
          )
        }
      }

      //paragraph "<b>Main Repeaters:</b> ${getMainRepeaters().collect{it.displayName}.join(', ')}"
      //paragraph "<b>Keypads:</b> ${getKeypads().collect{it.displayName}.join(', ')}"
      //app.getRooms().collect{it.name}.each{ r ->
      //  paragraph "<b>${r}</b>"
      //  paragraph bullet('<b>devicesForRoom: </b>' + getDevicesForRoom(r, settings.switches).join(', '))
      //  paragraph bullet('<b>Lutron Devices: </b>' + getLutronDevices(r).join(', '))
      //  paragraph bullet('<b>LED Devices: </b>' + getLedDevices(r).join(', '))
      //  paragraph bullet('<b>Non-Lutron Devices: </b>' + getNonLutronDevices(r).join(', '))
      //}

//mapDeviceIdAsStringToRoomName()
//      paragraph partial
//&nbsp;&nbsp;<b>LED Devices:</b> "${getLedDevices(r).collect{it.displayName}.join(', ')}"
//&nbsp;&nbsp;<b>Non-Lutron Devices:</b> "${getNonLutronDevices(r).collect{it.displayName}.join(', ')}"
//"""
      paragraph comment("""Whole House Automation - @wesmc, \
        <a href='https://github.com/WesleyMConner/Hubitat-WholeHouseAutomation' \
        target='_blank'> <br/>Click for more information</a>""")
    }
  }
}


// -------------------------------------
// M E T H O D S   O N   S E T T I N G S
// -------------------------------------
List<DeviceWrapper> getMainRepeaters () {
  return settings?.lutronRepeaters?.collect{it}
}

List<DeviceWrapper> getKeypads() {
  return settings?.lutronNonRepeaters?.collect{it}
         + settings?.lutronKeypads?.collect{it}
         + settings?.lutronPicos?.collect{it}
}

List<DeviceWrapper> getLutronDevices (String room) {
  return getDevicesForRoom(room, settings?.switches).findAll{
    it.displayName.toString().contains('lutron') && it.displayName.toString().contains('LED') == false
  }
}

List<DeviceWrapper> getLedDevices (String room) {
  return getDevicesForRoom(room, settings?.switches).findAll{
    it?.displayName.toString().contains('LED')  }
}

List<DeviceWrapper> getNonLutronDevices (String room) {
  return getDevicesForRoom(room, settings?.switches).findAll{
    ! (it.displayName.toString().contains('lutron'))
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
  //??--log.info """initialize() with ${childApps.size()} Automation Groups<br/>
  //??--  ${childApps.each({ child -> "&#x2022;&nbsp;${child.label}" })}
  //??--"""
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










