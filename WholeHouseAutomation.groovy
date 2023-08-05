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
          blockLabel: "Lutron Telnet Device </em>",
          name: 'lutronTelnet',
          title: 'Confirm Lutron Telnet Device<br/>' \
            + "${comment('used to detect Main Repeater LED state changes')}",
          type: 'device.LutronTelnet'
        )
        collapsibleInput (
          blockLabel: "Lutron Main Repeaters",
          name: 'lutronRepeaters',
          title: 'Identify Lutron Main Repeater(s)<br/>' \
            + "${comment('used to invoke in-kind Lutron scenes')}",
          type: 'device.LutronKeypad'
        )
        collapsibleInput (
          blockLabel: "Lutron Miscellaneous Keypads",
          name: 'lutronMiscKeypads',
          title: 'Identify Lutron Miscellaneous Devices<br/>' \
            + "${comment('used to trigger room scenes')}",
          type: 'device.LutronKeypad'
        )
        collapsibleInput (
          blockLabel: "Lutron SeeTouch Keypads",
          name: 'lutronSeeTouchKeypads',
          title: 'Identify Lutron SeeTouch Keypads<br/>' \
            + "${comment('used to trigger room scenes.')}",
          type: 'device.LutronSeeTouchKeypad'
        )
        collapsibleInput (
          blockLabel: "Lutron Picos",
          name: 'lutronPicos',
          title: 'Identify Lutron Picos<br/>' \
            + "${comment('used to trigger room scenes')}",
          type: 'device.LutronFastPico'
        )
        collapsibleInput (
          blockLabel: 'Lutron LEDs and Non-Lutron Devices',
          name: 'switches',
          title: 'Identify Lutron LEDs and Non-Lutron switches and dimmers' \
            + comment('<br/>Lutron LEDs are set to reflect the current scene.') \
            + comment('<br/>Non-Lutron device levels are set per room scenes.') \
            + comment('<br/>Non-LED Lutron devices can be skipped.') \
            + comment('<br/>VSWs (virtual switches) can be skipped.'),
          type: 'capability.switch'
        )
        if (settings.roomNames) {
          app(
            name: 'RoomScenes',
            appName: 'RoomScenes',
            namespace: 'wesmc',
            parent: 'WholeHouseAutomation',
            title: '<b>Add Rooms</b>' \
              + comment('Room names are automatically assigned.'),
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
  return (settings?.lutronMiscKeypads ?: []) \
         + (settings?.lutronSeeTouchKeypads ?: []) \
         + (settings?.lutronPicos ?: [])
}

List<DeviceWrapper> getLedDevices () {
  return settings?.switches.findAll{ it?.displayName.toString().contains('LED') }
}

//--xx-- Main Repeater LEDs will be used to detect Manual overrides.
//--xx-- List<DeviceWrapper> getLutronDevices (String room) {
//--xx--   return getDevicesForRoom(room, settings?.switches).findAll{it.displayName.contains('lutron') && ! it.displayName.contains('LED')}
//--xx-- }

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
  log.trace "WHA subscribing to Lutron Telnet >${settings.lutronTelnet}<"
  //subscribe(settings.lutronTelnet, "switch", testHandler)
  settings.lutronTelnet.each{ d ->
    DeviceWrapper device = d
    log.trace "WHA subscribing ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
  log.trace "WHA subscribing to Lutron Repeaters >${settings.lutronRepeaters}<"
  //subscribe(settings.lutronRepeaters, "switch", testHandler)
  settings.lutronRepeaters.each{ d ->
    DeviceWrapper device = d
    log.trace "WHA subscribing to ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
  log.trace "WHA subscribing to lutron SeeTouch Keypads >${settings.lutronSeeTouchKeypads}<"
  //subscribe(settings.lutronSeeTouchKeypads, "switch", testHandler)
  settings.lutronSeeTouchKeypads.each{ d ->
    DeviceWrapper device = d
    log.trace "WHA subscribing to ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }

  // T E S T   B E G I N ==================================================
  Closure handlerFactory = { e, pbsgInst ->
    "Arg '${e}', '${pbsgInst.a}' and '${pbsgInst.b}'."
  }

  def pbsgA = [
    a: "This is a string",
    b: "another string,"
  ]

  log.trace "-A-"
  log.trace "pbsgA: ${pbsgA}"
  log.trace "-B-"
  def handler = { e -> handlerFactory.call(e, pbsgA) }
  log.trace handler
  log.trace "-C-"
  log.trace handler('puppies')
  log.trace "-D-"
  // T E S T   E N D ======================================================
}


// groovy.lang.MissingMethodException: No signature of method:
// user_app_wesmc_WholeHouseAutomation_332$_initialize_closure12.doCall()
// is applicable for argument types: () values: [] Possible solutions:
// doCall(java.lang.Object, java.lang.Object), isCase(java.lang.Object),
// isCase(java.lang.Object), findAll(), findAll(), findAll(groovy.lang.Closure) (updated)

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
  if (settings.lutronMiscKeypads && settings.lutronSeeTouchKeypads && settings.lutronPicos) {
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
