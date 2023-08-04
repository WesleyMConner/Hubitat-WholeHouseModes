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
          blockLabel: "Lutron Telnet Device (for LED events)",
          name: 'lutronTelnet',
          title: 'Identify Lutron Telnet Device<br/>' \
            + "${comment('Used to invoke in-kind Lutron scenes.')}",
          type: 'device.LutronTelnet'
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
      paragraph comment("""Whole House Automation - @wesmc, \
        <a href='https://github.com/WesleyMConner/Hubitat-WholeHouseAutomation' \
        target='_blank'> <br/>Click for more information</a>""")
    }
  }
}

// -------------------------------------
// M E T H O D S   O N   S E T T I N G S
// -------------------------------------
String assignChildAppRoomName (Long childAppId) {
  List<String> roomNames = settings.roomNames
  List<InstalledAppWrapper> kidApps = getChildApps()
  Map<String, String> kidIdToRoomName = \
    kidApps.collectEntries{ kid ->
      [ kid.id.toString(), roomNames.contains(kid.label) ? kid.label : null ]
    }
  Map<String, Boolean> roomNameToKidId = roomNames.collectEntries{[it, false]}
  kidIdToRoomName.each{ kidId, roomName ->
    if (roomName) roomNameToKidId[roomName] = kidId
  }
  return result = kidIdToRoomName[childAppId.toString()]
                  ?: roomNameToKidId.findAll{!it.value}.keySet().first()
}

List<DeviceWrapper> getMainRepeaters () {
  return settings?.lutronRepeaters
}

List<DeviceWrapper> getKeypads() {
  return (settings?.lutronNonRepeaters ?: []) \
         + (settings?.lutronKeypads ?: []) \
         + (settings?.lutronPicos ?: [])
}

List<DeviceWrapper> getLedDevices () {
  return settings?.switches.findAll{ it?.displayName.toString().contains('LED') }
}

List<DeviceWrapper> getLutronDevices (String room) {
  return getDevicesForRoom(room, settings?.switches).findAll{it.displayName.contains('lutron') && ! it.displayName.contains('LED')}
}

List<DeviceWrapper> getNonLutronDevices (String room) {
  return getDevicesForRoom(room, settings?.switches).findAll{
    it.displayName.toString().contains('lutron') == false
  }
}

// ---------------------------------------------------
// I N I T I A L I Z A T I O N   &   O P E R A T I O N
// ---------------------------------------------------
void installed() {
  log.trace 'WHA installed()'
  initialize()
}

void updated() {
  log.trace 'WHA updated()'
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void testHandler (Event e) {
  // SAMPLE 1
  //   descriptionText  (lutron-80) TV Wall KPAD button 1 was pushed [physical]
  //          deviceId  5686
  //       displayName  (lutron-80) TV Wall KPAD
  log.trace "WHA testHandler() w/ event: ${e}"
  logEventDetails(e, false)
}

void initialize() {
  log.trace "WHA initialize()"
  log.trace "WHA subscribing to lutronTelnet >${settings.lutronTelnet}<"
  //subscribe(settings.lutronTelnet, "switch", testHandler)
  settings.lutronTelnet.each{ d ->
    DeviceWrapper device = d
    log.trace "WHA subscribe ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
  log.trace "WHA subscribing to lutronRepeaters >${settings.lutronRepeaters}<"
  //subscribe(settings.lutronRepeaters, "switch", testHandler)
  settings.lutronRepeaters.each{ d ->
    DeviceWrapper device = d
    log.trace "WHA subscribe ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
  log.trace "WHA subscribing to lutronKeypads >${settings.lutronKeypads}<"
  //subscribe(settings.lutronKeypads, "switch", testHandler)
  settings.lutronKeypads.each{ d ->
    DeviceWrapper device = d
    log.trace "WHA subscribe ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
}

// ========================================
// 3:41 ISSUE
// ========================================
// PMdebugsendMsg:?monitoring,1
//-------------------> PMtracemissing device id:4, msg:OUTPUT,4,1,0.00
// PMinforcvd: OUTPUT,4,1,0.00
// PMinfo(lutron-44) Garage KPAD LED 3 was turned off
// PMinforcvd: DEVICE,44,83,9,0
// PMinfo(lutron-80) TV Wall KPAD LED 1 was turned off
// PMinforcvd: DEVICE,80,81,9,0
//-------------------> PMtracemissing device id:1, msg:DEVICE,1,129,9,0
// PMinforcvd: DEVICE,1,129,9,0
//-------------------> PMtracemissing device id:1, msg:DEVICE,1,124,9,1
// PMinforcvd: DEVICE,1,124,9,1
//-------------------> PMtracemissing device id:1, msg:DEVICE,1,122,9,1
// PMinforcvd: DEVICE,1,122,9,1
// PMinfo(lutron-65) Kitchen Counters was turned off [physical]
// PMinforcvd: OUTPUT,65,1,0.00
// PMinfo(lutron-61) Kitchen Cans was turned off [physical]
// PMinforcvd: OUTPUT,61,1,0.00
//-------------------> PMtracemissing device id:1, msg:DEVICE,1,125,9,1
// PMinforcvd: DEVICE,1,125,9,1
//-------------------> PMtracemissing device id:1, msg:DEVICE,1,25,3
// PMinforcvd: DEVICE,1,25,3
// PMdebugsendMsg:#device,1,25,3
// PMtraceWHA initialize() [Lutron Telnet] subscribing.

// ========================================
// FOCUS ON DEVICE 1 ... LATEST TO EARLIEST
// ========================================
// PMinforcvd: DEVICE,1,125,9,0 -----------------> buttonLed-25 (Kitchen Off) turned OFF
// PMinforcvd: DEVICE,1,124,9,0 ---------------> buttonLed-24 (Kitchen Night) turned OFF
// PMinforcvd: DEVICE,1,122,9,0 -----------------> buttonLed-22 (Kitchen Day) turned OFF
// PMinforcvd: DEVICE,1,129,9,1 -------------------------> buttonLed-29 (COOK) turned ON
// [Enabled the COOK scene via REP 1 button 29]
// PMinforcvd: DEVICE,1,129,9,0 ------------------------> buttonLed-29 (COOK) turned OFF
// PMinforcvd: DEVICE,1,124,9,1 ----------------> buttonLed-24 (Kitchen Night) turned ON
// PMinforcvd: DEVICE,1,122,9,1 ------------------> buttonLed-22 (Kitchen Day) turned ON
// PMinforcvd: DEVICE,1,125,9,1 ------------------> buttonLed-25 (Kitchen Off) turned ON
// PMinforcvd: DEVICE,1,25,3
// [Enabled the Kitchen Off scene via REP 1 button 25]
// PMtraceRoomScenes.initialize() [Lutron Telnet] subscribing.

// ISSUE as of 3p THURSDAY
// app 1135 is WHA
// den 1179-1182 are children (their legacy subscription?!)


// -----
// T B D
// -----

// -------------------------------
// P R E S E R V E D   S C R A P S
// -------------------------------

// groovy.lang.MissingMethodException:
//   - No signature of method: user_app_wesmc_WholeHouseAutomation_332.subscribe()
//     is applicable for argument
//       - types: (java.util.LinkedHashMap)
//       - values: [[devices:[(lutron-01) REP 1, (lutron-83) REP 2],
//       - handlerMethod:testHandler, ...]] Possible solutions: subscribe(java.lang.Object, java.lang.String, groovy.lang.MetaMethod), subscribe(java.lang.Object, java.lang.String, java.lang.String), subscribe(java.lang.Object, java.lang.String, groovy.lang.MetaMethod, java.util.Map), subscribe(java.lang.Object, java.lang.String, java.lang.String, java.util.Map) on line 260 (method updated)


/*
void initialize() {
  //??--log.info """initialize() with ${childApps.size()} Automation Groups<br/>
  //??--  ${childApps.each({ child -> "&#x2022;&nbsp;${child.label}" })}
  //??--"""
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
}
*/

/*
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
*/

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
