// ---------------------------------------------------------------------------------
// W H O L E   H O U S E   A U T O M A T I O N
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
//   - Multiple DeviceWrapperList instances arise due to multiple input() statements.
//   - Initialization of 'state' includes making immutable copies of DeviveWrapper
//     instances, gathered from 'settings'.
// ---------------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DeviceWrapper
import com.hubitat.app.DeviceWrapperList as DeviceWrapperList
import com.hubitat.app.InstalledAppWrapper as InstalledAppWrapper
#include wesmc.DeviceLibrary
#include wesmc.PBSG
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
  page(name: 'monoPage', title: '', install: true, uninstall: true)
  page(name: 'callablePage', title: '', install: true, uninstall: true)
}

def callablePage (params) {
  dynamicPage(name: 'callablePage') {
    section {
      paragraph "callablePage with params: >${params}<."
    }
  }
}

void solictfocalRooms () {
  roomPicklist = app.getRooms().collect{it.name}.sort()
  //->paragraph "roomPicklist: >${roomPicklist}<"
  collapsibleInput(
    blockLabel: 'Focal Rooms',
    name: 'focalRooms',
    type: 'enum',
    title: 'Select the Focal Rooms',
    options: roomPicklist
  )
}

void solicitLutronTelnetDevice () {
  collapsibleInput (
    blockLabel: 'Lutron Telnet Device',
    name: 'lutronTelnet',
    title: 'Confirm Lutron Telnet Device<br/>' \
      + comment('used to detect Main Repeater LED state changes'),
    type: 'device.lutronTelnet'
  )
}

void solicitLutronMainRepeaters () {
  collapsibleInput (
    blockLabel: 'Lutron Main Repeaters',
    name: 'lutronRepeaters',
    title: 'Identify Lutron Main Repeater(s)<br/>' \
      + comment('used to invoke in-kind Lutron scenes'),
    type: 'device.LutronKeypad'
  )
}

void solicitLutronMiscellaneousKeypads () {
  collapsibleInput (
    blockLabel: 'Lutron Miscellaneous Keypads',
    name: 'lutronMiscKeypads',
    title: 'Identify Lutron Miscellaneous Devices<br/>' \
      + comment('used to trigger room scenes'),
    type: 'device.LutronKeypad'
  )
}

void solicitSeeTouchKeypads () {
  collapsibleInput (
    blockLabel: 'Lutron SeeTouch Keypads',
    name: 'seeTouchKeypad',
    title: 'Identify Lutron SeeTouch Keypads<br/>' \
      + comment('used to trigger room scenes.'),
    type: 'device.SeeTouchKeypad'
  )
}

void solicitLutronPicos () {
  collapsibleInput (
    blockLabel: 'Lutron Picos',
    name: 'lutronPicos',
    title: 'Identify Lutron Picos<br/>' \
      + comment('used to trigger room scenes'),
    type: 'device.LutronFastPico'
  )
}

void solicitSwitches () {
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
}

LinkedHashMap<String, InstalledAppWrapper> getAllChildAppsByLabel () {
  return getAllChildApps().collectEntries{
    childApp -> [ childApp.getLabel(), childApp ]
  }
}

//->TBD Type Data
LinkedHashMap groupChildApps () {
  // Start with all child apps (and whittle the list down)
  LinkedHashMap<String, InstalledAppWrapper> \
    childAppsByLabel = getAllChildAppsByLabel()
  // Account for Room Scene child apps
  LinkedHashMap<String, InstalledAppWrapper> roomAppsByName \
    = settings.focalRooms?.collectEntries{ room ->
      InstalledAppWrapper roomSceneApp = childAppsByLabel.getAt(room)
      if (roomSceneApp) {
        childAppsByLabel.remove(room)
        [room, roomSceneApp]
      } else {
        InstalledAppWrapper newChild = addChildApp('wesmc', 'RoomScenes', room)
        [room, addChildApp]
      }
    }
  if (settings.LOG) log.trace(
    "groupChildApps() <b>roomAppsByName:</b> ${roomAppsByName.keySet()}"
  )
  // Account for PBSG child app(s)
  InstalledAppWrapper pbsgModeApp = childAppsByLabel.getAt('pbsg-mode')
  if (pbsgModeApp) childAppsByLabel.remove('pbsg-mode')
  if (settings.LOG) log.trace(
    "groupChildApps() <b>pbsgModeApp:</b> ${pbsgModeApp}"
  )
  // Remove remaining (ungrouped) child apps.
  childAppsByLabel.each{ label, childApp ->
    if (settings.LOG) "Deleting un-grouped Child App ${name} (${childApp.getId()})"
    deleteChildApp(childApp.getId())
  }
  return [
    'roomAppsByName': roomAppsByName,
    'pbsgModeApp': pbsgModeApp
  ]
}

// """ ... """.stripIndent()

Map monoPage() {
  return dynamicPage(name: 'monoPage') {
    section {
      app.updateLabel('Whole House Automation')
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
        input (
          name: 'LOG',
          type: 'bool',
          title: 'Enable logging?',
          defaultValue: true
        )
        solictfocalRooms()
//        solicitLutronTelnetDevice()
//        solicitLutronMainRepeaters()
//        solicitLutronMiscellaneousKeypads()
//        solicitSeeTouchKeypads()
//        solicitLutronPicos()
//        solicitSwitches()
        /*
        LinkedHashMap unpairedChildAppsByName = getChildAppsByName ()

        //->removeUnpairedChildApps ()
        if (settings.LOG) log.info "childApps: ${childApps.collect{it.getLabel()}.join(', ')}"

        // MapfocalRoomsToRoomSceneApps
        LinkedHashMap roomAppsByName = settings.focalRooms.collectEntries{
          room -> [room, unpairedChildIds.contains(room) ?: null]
        }

        // Prepare to capture the Mode PBSG child app.
        InstalledAppWrapper pbsgModeApp = null

        // Prepare to remove unused child apps.
        List<String> unusedDeviceNetworkIds = []

        // Parse existing (discovered) Child Apps, removing unaffiliated children.
        List<InstalledAppWrapper> childApps = getAllChildApps()
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
          if (settings.LOG) log.info "Removing stale childApps ${deviceNetworkId}"
          deleteChildDevice(deviceNetworkId)
        }

        // Display PBSG Child
        //----> P L A C E H O L D E R

        // Display Room Scene Children
        if (settings.LOG) log.info "roomAppsByName (initial): ${roomAppsByName}"
        roomAppsByName = roomAppsByName?.collectEntries{ roomName, childApp ->
          [roomName, childApp ?: addChildApp('wesmc', 'RoomScenes', roomName)]
        }
        if (settings.LOG) log.info "roomAppsByName (backfilled): ${roomAppsByName}"
        */

        href (
          name: 'TEST PAGE 2',
          width: 5,
          page: callablePage,
          // url: "/installedapp/configure/${childApp?.getId()}/monoPage",
          style: 'internal',
          title: "This is a dummy page",
          state: 'complete',  // null,
          params: [
            listA: ['purple', 'green', 'yellow'],
            mapX: [a: 'apple', b: 'bannana', g: ['grapefruit', 'grape']]
          ]
        )

        def m1 = groupChildApps()
        m1.roomAppsByName.each{ roomName, childApp ->
          href (
            name: roomName,
            width: 2,
            url: "/installedapp/configure/${childApp?.getId()}/monoPage",
            style: 'internal',
            title: "Edit <b>${roomName}</b> Scenes",
            state: null, //'complete'
            params: [
              listA: ['purple', 'green', 'yellow'],
              mapX: [a: 'apple', b: 'bannana', g: ['grapefruit', 'grape']]
            ]
          )
        }
        paragraph comment('Whole House Automation - @wesmc, ' \
          + '<a href="https://github.com/WesleyMConner/Hubitat-WholeHouseAutomation" ' \
          + 'target="_blank"><br/>Click for more information</a>')
      }
    }
  }
}

// ------------------------------------------------------------------------
// M E T H O D S   B A S E D   O N   S E T T I N G S
//   Clients can use the following methods (which operate exclusively on
//   Parent'settings' when rendering data entry screens.
// ------------------------------------------------------------------------
String assignChildAppRoomName (Long childAppId) {
  List<String> focalRooms = settings.focalRooms
  List<InstalledAppWrapper> kidApps = getChildApps()
  Map<String, String> kidIdToRoomName =
    kidApps.collectEntries{ kid ->
      [ kid.id.toString(), focalRooms.contains(kid.label) ? kid.label : null ]
  }
  Map<String, Boolean> roomNameToKidId = focalRooms.collectEntries{[it, false]}
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
         + (settings?.seeTouchKeypad ?: []) \
         + (settings?.lutronPicos ?: [])
}

List<DeviceWrapper> getLedDevices () {
  return settings?.switches.findAll{ it?.displayName.toString().contains('LED') }
}

//--xx-- Main Repeater LEDs will be used in lieu of individual Lutron
//--xx-- devices to detect Manual overrides.
//--xx--
//--xx-- List<DeviceWrapper> getLutronDevices (String room) {
//--xx--   return getDevicesForRoom(room, settings?.switches).findAll{it.displayName.contains('lutron') && ! it.displayName.contains('LED')}
//--xx-- }

List<DeviceWrapper> getNonLutronDevices (String room) {
  return getDevicesForRoom(room, settings?.switches).findAll{
    it.displayName.toString().contains('lutron') == false
  }
}

// ------------------------------------------------------------------------
// I N I T I A L I Z A T I O N   &   O P E R A T I O N
//   'Settings' are used to develop 'state'.
//   App operations depend primarily on state.
// ------------------------------------------------------------------------
void installed() {
  if (settings.LOG) log.trace 'WHA installed()'
  initialize()
}

void updated() {
  if (settings.LOG) log.trace 'WHA updated()'
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void testHandler (Event e) {
  // SAMPLE 1
  //   descriptionText  (lutron-80) TV Wall KPAD button 1 was pushed [physical]
  //          deviceId  5686
  //       displayName  (lutron-80) TV Wall KPAD
  if (settings.LOG) log.trace "WHA testHandler() w/ event: ${e}"
  if (settings.LOG) logEventDetails(e, false)
}

void initialize() {
  if (settings.LOG) log.trace "WHA initialize()"
  if (settings.LOG) log.trace "WHA subscribing to Lutron Telnet >${settings.lutronTelnet}<"
  settings.lutronTelnet.each{ d ->
    DeviceWrapper device = d
    if (settings.LOG) log.trace "WHA subscribing ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
  if (settings.LOG) log.trace "WHA subscribing to Lutron Repeaters >${settings.lutronRepeaters}<"
  settings.lutronRepeaters.each{ d ->
    DeviceWrapper device = d
    if (settings.LOG) log.trace "WHA subscribing to ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
  if (settings.LOG) log.trace "WHA subscribing to lutron SeeTouch Keypads >${settings.seeTouchKeypad}<"
  settings.seeTouchKeypad.each{ d ->
    DeviceWrapper device = d
    if (settings.LOG) log.trace "WHA subscribing to ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }

  ArrayList<LinkedHashMap> modes = location.getModes()
  // Rebuild the PBSG mode instance adjusting (i.e., reusing or dropping)
  // previously-created VSWs to align with current App modes.
  if (state['pbsg-modes']) { deletePBSG(name: 'pbsg-modes', dropChildVSWs: false) }
  createPBSG(
    name: 'pbsg-modes',
    sceneNames: modes.collect{it.name},
    defaultScene: 'Day'
  )
}
