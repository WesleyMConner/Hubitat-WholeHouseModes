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
//   - Multiple DevWL instances arise due to multiple input() statements.
//   - Initialization of 'state' includes making immutable copies of DeviveWrapper
//     instances, gathered from 'settings'.
// ---------------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.DeviceWrapperList as DevWL
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Location as Loc
//->#include wesmc.pbsgLibrary
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
  page(name: 'whaPage', title: '', install: true, uninstall: true)
}

// -----------------------------------
// W H A   P A G E   &   S U P P O R T
// -----------------------------------

void solictfocalRoomNames () {
  roomPicklist = app.getRooms().collect{it.name}.sort()
  collapsibleInput(
    blockLabel: 'Focal Rooms',
    name: 'focalRoomNames',
    type: 'enum',
    title: 'Select Participating Rooms',
    options: roomPicklist
  )
}

void solicitLutronTelnetDevice () {
  collapsibleInput (
    blockLabel: 'Lutron Telnet Device',
    name: 'lutronTelnet',
    title: 'Confirm Lutron Telnet Device<br/>' \
      + comment('Used to detect Main Repeater LED state changes'),
    type: 'device.LutronTelnet'
  )
}

void solicitLutronMainRepeaters () {
  collapsibleInput (
    blockLabel: 'Lutron Main Repeaters',
    name: 'lutronRepeaters',
    title: 'Identify Lutron Main Repeater(s)<br/>' \
      + comment('Used to invoke in-kind Lutron scenes'),
    type: 'device.LutronKeypad'
  )
}

void solicitLutronMiscellaneousKeypads () {
  collapsibleInput (
    blockLabel: 'Lutron Miscellaneous Keypads',
    name: 'lutronMiscKeypads',
    title: 'Identify participating Lutron Miscellaneous Devices<br/>' \
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
    type: 'device.LutronSeeTouchKeypad'
  )
}

void solicitLutronLEDs () {
  collapsibleInput (
    blockLabel: 'Lutron LEDs',
    name: 'lutronLEDs',
    title: 'Select participating Lutron LEDs<br/>' \
      + comment('Used to trigger room scenes.'),
    type: 'device.LutronComponentSwitch'
  )
}

void solicitLutronPicos () {
  collapsibleInput (
    blockLabel: 'Lutron Picos',
    name: 'lutronPicos',
    title: 'Select participating Lutron Picos<br/>' \
      + comment('used to trigger room scenes'),
    type: 'device.LutronFastPico'
  )
}

void solicitSwitches () {
  collapsibleInput (
    blockLabel: 'Non-Lutron, Non-VSW Devices',
    name: 'switches',
    title: 'Select participating Non-Lutron, Non-VSW switches and dimmers',
    type: 'capability.switch'
 )
}

void manageChildApps() {
  // Abstract
  //   Manage child applications AND any required initialization data.
  //   Child applications are automatically created and given a "label".
  //   Any required initialization data is stored at state.<label> and
  //   exposed to child applications via getChildInit(<label>). Child
  //   applications house their own state data locally.
  // Design Notes
  //   Application state data managed by this method includes:
  //     - state.childAppsByRoom
  //     - state.<roomName>
  //     - state.pbsg_modes
  // ------------------------------------------------
  // Deliberately create noise for testing dups:
  //   addChildApp('wesmc', 'RoomScenes', 'Kitchen')
  //   addChildApp('wesmc', 'RoomScenes', 'Den')
  //   addChildApp('wesmc', 'RoomScenes', 'Kitchen')
  //   addChildApp('wesmc', 'RoomScenes', 'Puppies')
  //   addChildApp('wesmc', 'whaPBSG', 'Butterflies')
  // ------------------------------------------------
  // G E T   A L L   C H I L D   A P P S
  // ------------------------------------------------
  if (settings.LOG) log.trace (
    'manageChildApps() on entry getAllChildApps(): '
    + getAllChildApps().sort{ a, b ->
        a.getLabel() <=> b.getLabel() ?: a.getId() <=> b.getId()
      }.collect{ app ->
        "<b>${app.getLabel()}</b> -> ${app.getId()}"
      }?.join(', ')
  )
  // -----------------------------------------------
  // T O S S   S T A L E   A P P S   B Y   L A B E L
  // -----------------------------------------------
  // Child apps are managed by App Label, which IS NOT guaranteed to be
  // unique. The following method keeps only the latest (highest) App ID
  // per App Label.
  LinkedHashMap<String, InstAppW> childAppsByLabel \
    = keepOldestAppObjPerAppLabel(settings.LOG)
  if (settings.LOG) log.trace (
    'manageChildApps() after keepOldestAppObjPerAppLabel(): '
    + childAppsByLabel.collect{label, childObj ->
        "<b>${label}</b> -> ${childObj.getId()}"
      }?.join(', ')
  )
  // ---------------------------------------
  // P O P U L A T E   R O O M   S C E N E S
  // ---------------------------------------
  // Ensure Room Scenes instances exist (no init data is required).
  LinkedHashMap<String, InstAppW> childAppsByRoom = \
    settings.focalRoomNames?.collectEntries{ roomName ->
      [
        roomName,
        getAppByLabel(childAppsByLabel, roomName)
          ?: addChildApp('wesmc', 'RoomScenes', roomName)
      ]
    }
  if (settings.LOG) log.trace (
    'manageChildApps() after adding any missing Room Scene apps:'
    + childAppsByRoom.collect{ roomName, roomObj ->
        "<b>${roomName}</b> -> ${roomObj.getId()}"
      }?.join(', ')
  )
  state.roomNameToRoomScenes = childAppsByRoom
  // -------------------------
  // P O P U L A T E   P B S G
  // -------------------------
  // Ensure imutable PBSG init data is in place AND instance(s) exist.
  // The PBSG instance manages its own state data locally.
  String pbsgName = 'pbsg_modes'
  state.modeSwitchNames = getLocation().getModes().collect{it.name}
  state.defaultModeSwitchName = getGlobalVar('defaultMode').value
  InstAppW pbsgApp = getAppByLabel(childAppsByLabel, pbsgName)
    ?:  addChildApp('wesmc', 'whaPBSG', pbsgName)
  if (settings.LOG) log.trace(
    "manageChildApps() initializing ${pbsgName} with "
    + "<b>modeSwitchNames:</b> ${state.modeSwitchNames}, "
    + "<b>defaultModeSwitchName:</b> ${state.defaultModeSwitchName} "
    + "and logging ${settings.LOG}."
  )
  pbsgApp.configure(
    state.modeSwitchNames,
    state.defaultModeSwitchName,
    settings.LOG
  )
  state.pbsg_modes = pbsgApp
  // ---------------------------------------------
  // P U R G E   E X C E S S   C H I L D   A P P S
  // ---------------------------------------------
  childAppsByLabel.each{ label, app ->
    if (childAppsByRoom.keySet().findAll{it == label}) {
      // Skip, still in use
    } else if (label == pbsgName) {
      // Skip, still in use
    } else {
      if (settings.LOG) log.trace(
        "manageChildApps() deleting orphaned child app ${getAppInfo(app)}."
      )
      deleteChildApp(app.getId())
    }
  }
}

void displayRoomNameHrefs () {
  state.roomNameToRoomScenes.each{ roomName, roomApp ->
    href (
      name: roomName,
      width: 2,
      url: "/installedapp/configure/${roomApp?.getId()}/roomScenesPage",
      style: 'internal',
      title: "Edit <b>${getAppInfo(roomApp)}</b> Scenes",
      state: null, //'complete'
    )
  }
}

void displayPbsgHref () {
  if (state.pbsg_modes) {
    href (
      name: state.pbsg_modes.getLabel(),
      width: 2,
      url: "/installedapp/configure/${state.pbsg_modes.getId()}/whaPbsgPage",
      style: 'internal',
      title: "Edit <b>${getAppInfo(state.pbsg_modes)}</b>",
      state: null, //'complete'
    )
  } else {
    log.error 'displayPbsgHref() called with pbsg_modes missing.'
  }
}

void removeAllChildApps () {
  getAllChildApps().each{ child ->
    if (settings.LOG) log.trace "child: >${child.getId()}< >${child.getLabel()}<"
    deleteChildApp(child.getId())
  }
}

void pruneOrphanedChildApps () {
  //Initially, assume InstAppW supports instance equality tests -> values is a problem
  List<InstAppW> kids = getAllChildApps()
  if (settings.LOG) log.info(
    "pruneOrphanedChildApps() processing ${kids.collect{it.getLabel()}.join(', ')}"
  )
  List<String> roomNames =
  kids.each{ kid ->
    if (settings.focalRoomNames?.contains(kid)) {
      if (settings.LOG) log.info "pruneOrphanedChildApps() skipping ${kid.getLabel()} (room)"
    // Presently, PBSG IS NOT a child app, it is a contained instance.
    //} else if (kid == state['pbsg_modes'].name) {
    //  if (settings.LOG) log.info "pruneOrphanedChildApps() skipping ${kid.getLabel()} (pbsg)"
    } else {
      if (settings.LOG) log.info "pruneOrphanedChildApps() deleting ${kid.getLabel()} (orphan)"
      deleteChildApp(kid.getId())
    }
  }
}

void displayAppInfoLink () {
  paragraph comment('Whole House Automation - @wesmc, ' \
    + '<a href="https://github.com/WesleyMConner/Hubitat-WholeHouseAutomation" ' \
    + 'target="_blank"><br/>Click for more information</a>')
}

Map whaPage() {
  return dynamicPage(name: 'whaPage') {
    section {
      app.updateLabel('Whole House Automation')
      paragraph heading('Whole House Automation<br/>') \
        + bullet('Select participating rooms and authorize device access.<br/>') \
        + bullet('Click <b>Done</b> to proceed to defining <b>Room Scene(s)</b>.')
      solicitLOG()  // via Utils
      solictfocalRoomNames()
      solicitLutronTelnetDevice()
      solicitLutronMainRepeaters()
      solicitLutronMiscellaneousKeypads()
      solicitSeeTouchKeypads()
      solicitLutronPicos()
      solicitLutronLEDs ()
      solicitSwitches()
      //->removeAllChildApps()  // Clean after errant process
      manageChildApps()
      paragraph heading('Room Scene Configuration')
      displayRoomNameHrefs()
      paragraph heading('PBSG Configuration')
      displayPbsgHref()
      //pruneOrphanedChildApps()
      //displayAppInfoLink()
    }
  }
}

// -----------------------------
// " C H I L D "   S U P P O R T
// -----------------------------

List<DevW> narrowDevicesToRoom (String roomName, DevWL devices) {
  // This function excludes devices that are not associated with any room.
  List<String> deviceIdsForRoom = app.getRooms()
                                  .findAll{it.name == roomName}
                                  .collect{it.deviceIds.collect{it.toString()}}
                                  .flatten()
  return devices.findAll{ d -> deviceIdsForRoom.contains(d.id.toString())
  }
}

List<DevW> getMainRepeaters () {
  return settings.lutronRepeaters
}

List<DevW> getKeypads() {
  return (settings.lutronMiscKeypads ?: []) \
         + (settings.seeTouchKeypad ?: []) \
         + (settings.lutronPicos ?: [])
}

List<DevW> getLedDevices () {
  return settings.lutronLEDs
}

List<DevW> getNonLutronDevicesForRoom (String roomName) {
  List<DevW> roomSwitches = narrowDevicesToRoom(roomName, settings.switches)
                            .findAll{
                              it.displayName.toString().contains('lutron') == false
                            }
}

// -------------------------------
// S T A T E   M A N A G E M E N T
// -------------------------------

void installed() {
  if (settings.LOG) log.trace 'WHA installed()'
  initialize()
}

def uninstalled() {
  if (settings.LOG) log.trace "WHA uninstalled()"
  removeAllChildApps()
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
    DevW device = d
    if (settings.LOG) log.trace "WHA subscribing ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
  if (settings.LOG) log.trace "WHA subscribing to Lutron Repeaters >${settings.lutronRepeaters}<"
  settings.lutronRepeaters.each{ d ->
    DevW device = d
    if (settings.LOG) log.trace "WHA subscribing to ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
  if (settings.LOG) log.trace "WHA subscribing to lutron SeeTouch Keypads >${settings.seeTouchKeypad}<"
  settings.seeTouchKeypad.each{ d ->
    DevW device = d
    if (settings.LOG) log.trace "WHA subscribing to ${device.displayName} ${device.id}"
    subscribe(device, testHandler, ['filterEvents': false])
  }
}
