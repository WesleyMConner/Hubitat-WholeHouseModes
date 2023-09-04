
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
// ---------------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Location as Loc
#include wesmc.UtilsLibrary
#include wesmc.ra2Library

definition(
  name: 'wha',
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
  page(name: 'whaPage')
}

Map whaPage() {
  return dynamicPage(
    name: 'whaPage',
    title: heading('Whole House Automation (WHA)<br/>') \
      + bullet('Obtain permission to access select Hubitat keypad devices.<br/>') \
      + bullet('Manage Hubitat Modes via a Pushbutton Switch Group (PBSG).<br/>') \
      + bullet('Identify Rooms and per-room "Scenes".<br/>') \
      + bullet('Facilitate drilldown to child applications.'),
    install: true,
    uninstall: true,
    nextPage: 'whaPage'
  ) {
    app.updateLabel('Whole House Automation')
    state.MODE_PBSG_APP_NAME = 'pbsg_modes'
    state.MODE_SWITCH_NAMES = getLocation().getModes().collect{it.name}
    state.DEFAULT_MODE_SWITCH_NAME = getGlobalVar('defaultMode').value
    // SAMPLE STATE & SETTINGS CLEAN UP
    //   - state.remove('X')
    //   - settings.remove('Y')
    // reLabelLeds()
    section {
      solicitLog()                                  // <- provided by Utils
      solicitSeeTouchKeypads (
        'lutronSeeTouchKeypads',
        'Identify <b>ALL Keypads</b> with buttons that impact Hubitat mode selection.'
      )
      solicitLutronLEDs(
        'lutronModeButtons',
        'Identify <b>All LEDs/Buttons</b> that enable Hubitat mode changes.'
      )
      if (state.MODE_SWITCH_NAMES == null || settings?.lutronModeButtons == null) {
        paragraph(red('Mode activation buttons are pending pre-requisites above.'))
      } else {
        selectLedsForListItems(
          state.MODE_SWITCH_NAMES,
          settings.lutronModeButtons,
          'modeButton'
        )
      }
      solictFocalRoomNames()
      //solicitLutronLEDs('modeLeds')
      //-> solicitLutronTelnetDevice()
      //-> solicitLutronMainRepeaters()
      //---> solicitLutronMiscellaneousKeypads()
      //-> Note Hubitat's RA2 Keypads DO NOT support getChildDevices().
      //-> settings.keypads?.each{ kp ->
      //->   paragraph "device ${kp.getLabel()}"
      //-> }
      //-> solicitLutronPicos()
      //-> solicitSwitches()
      deriveKpadDNIandButtonToMode()
      if (!settings.rooms) {
        paragraph red('Management of child apps is pending selection of Room Names.')
      } else {
        keepOldestAppObjPerAppLabel([*settings.rooms, state.MODE_PBSG_APP_NAME], false)
        roomAppDrilldown()
        modePbsgAppDrilldown()
      }
      paragraph(
        heading('Debug<br/>')
        + "${ displayState() }<br/>"
        + "${ displaySettings() }"
      )
    }
  }
}

/*
List<DevW> getMainRepeaters () {
  return settings.lutronRepeaters
}
*/

/*
List<DevW> getKeypads() {
  return (settings.lutronMiscKeypads ?: [])
         + (settings.seeTouchKeypad ?: [])
         + (settings.lutronPicos ?: [])
}
*/

/*
List<DevW> getLedDevices() {
  return settings.lutronLEDs
}
*/

/*
List<DevW> getPicoDevices() {
  return settings.lutronPicos
}
*/

/*
List<DevW> getNonLutronDevicesForRoom (String roomName) {
  List<DevW> roomSwitches = narrowDevicesToRoom(roomName, settings.switches)
                            .findAll{
                              it.displayName.toString().contains('lutron') == false
                            }
}
*/

// -----------------------------------
// W H A   P A G E   &   S U P P O R T
// -----------------------------------

void solictFocalRoomNames () {
  roomPicklist = app.getRooms().collect{it.name}.sort()
  collapsibleInput(
    blockLabel: 'Focal Rooms',
    name: 'rooms',
    type: 'enum',
    title: 'Select Participating Rooms',
    options: roomPicklist
  )
}

void deriveKpadDNIandButtonToMode () {
  mapKpadDNIandButtonToItem('modeButton')
}

void roomAppDrilldown() {
  paragraph heading('Room Scene Configuration')
  settings.rooms.each{ roomName ->
    InstAppW roomApp = app.getChildAppByLabel(roomName)
    if (!roomApp) {
      if (settings.log) log.trace "WHA addRoomAppsIfMissing() Adding room ${roomName}."
      roomApp = addChildApp('wesmc', 'whaRoom', roomName)
    }
    href (
      name: roomName,
      width: 2,
      url: "/installedapp/configure/${roomApp?.getId()}/whaRoomPage",
      style: 'internal',
      title: "Edit <b>${getAppInfo(roomApp)}</b> Scenes",
      state: null, //'complete'
    )
  }
}

void modePbsgAppDrilldown() {
  paragraph heading('Mode PBSG Inspection')
  InstAppW modePbsgApp = app.getChildAppByLabel(state.MODE_PBSG_APP_NAME)
  if (!modePbsgApp) {
    modePbsgApp = addChildApp('wesmc', 'modePBSG', state.MODE_PBSG_APP_NAME)
    modePbsgApp.configure(
      state.MODE_SWITCH_NAMES,
      state.DEFAULT_MODE_SWITCH_NAME,
      settings.log
    )
  }
  href (
    name: settings.MODE_PBSG_APP_NAME,
    width: 2,
    url: "/installedapp/configure/${modePbsgApp.getId()}/modePbsgPage",
    style: 'internal',
    title: "Edit <b>${getAppInfo(modePbsgApp)}</b>",
    state: null, //'complete'
  )
}

void pbsgVswTurnedOn(String simpleName) {
  log.trace(
    "WHA pbsgVswTurnedOn() activating mode='<b>${simpleName}</b> [PENDING]'."
  )
  getLocation().setMode(simpleName)
}

void removeAllChildApps () {
  getAllChildApps().each{ child ->
    if (settings.log) log.trace(
      "WHA removeAllChildApps() child: >${child.getId()}< >${child.getLabel()}<"
    )
    deleteChildApp(child.getId())
  }
}

void pruneOrphanedChildApps () {
  //Initially, assume InstAppW supports instance equality tests -> values is a problem
  List<InstAppW> kids = getAllChildApps()
  if (settings.log) log.info(
    'WHA pruneOrphanedChildApps() processing '
    + "${kids.collect{it.getLabel()}.join(', ')}"
  )
  List<String> roomNames =
  kids.each{ kid ->
    if (settings.rooms?.contains(kid)) {
      if (settings.log) log.info(
        "WHA pruneOrphanedChildApps() skipping ${kid.getLabel()} (room)"
      )
    } else {
      if (settings.log) log.info(
        "WHA pruneOrphanedChildApps() deleting ${kid.getLabel()} (orphan)"
      )
      deleteChildApp(kid.getId())
    }
  }
}

void displayAppInfoLink () {
  paragraph comment('Whole House Automation - @wesmc, ' \
    + '<a href="https://github.com/WesleyMConner/Hubitat-wha" ' \
    + 'target="_blank"><br/>Click for more information</a>')
}

// -------------------------------
// S T A T E   M A N A G E M E N T
// -------------------------------

void installed() {
  if (settings.log) log.trace 'WHA installed()'
  initialize()
}

void uninstalled() {
  if (settings.log) log.trace "WHA uninstalled()"
  removeAllChildApps()
}

void updated() {
  if (settings.log) log.trace 'WHA updated()'
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void telnetHandler (Event e) {
  if (settings.log) log.trace(
    "WHA <b>telnetHandler() w/ event: ${e.descriptionText}"
  )
}

void repeaterHandler (Event e) {
  if (settings.log) log.trace(
    "WHA <b>repeaterHandler() w/ event: ${e.descriptionText}"
  )
}

void keypadToVswHandler (Event e) {
  // Design Note
  //   - The field e.deviceId arrives as a number and must be cast toString().
  //   - Hubitat runs Groovy 2.4. Groovy 3 constructs - x?[]?[] - are not available.
  //   - Keypad buttons are matched to state data to activate a target VSW.
  if (e.name == 'pushed') {
    String targetVsw = state.kpadButtons.getAt(e.deviceId.toString())?.getAt(e.value)
    if (settings.log) log.trace(
      "WHA keypadToVswHandler() "
      + "<b>Keypad Device Id:</b> ${e.deviceId}, "
      + "<b>Keypad Button:</b> ${e.value}, "
      + "<b>Affiliated Switch Name:</b> ${targetVsw}"
    )
    // Turn on appropriate pbsg-modes-X VSW.
    InstAppW pbsgApp = app.getChildAppByLabel(state.MODE_PBSG_APP_NAME)
    if (settings.log) log.trace(
      "WHA keypadToVswHandler() pbsgApp '${pbsgApp}'"
    )
    pbsgApp.turnOnSwitch(targetVsw)
  } else {
    if (settings.log) log.trace(
      "WHA keypadToVswHandler() unexpected event name '${e.name}' for DNI '${e.deviceId}'"
    )
  }
}

void initialize() {
  // TACTICALLY, DROP EVERYTHING
  if (settings.log) log.trace "WHA initialize()"
  //-> if (settings.log) log.trace "WHA subscribing to Lutron Telnet >${settings.lutronTelnet}<"
  //-> settings.lutronTelnet.each{ d ->
  //->   DevW device = d
  //->   if (settings.log) log.trace "WHA subscribing ${device.displayName} ${device.id}"
  //->   //unsubscribe(d)
  //->   subscribe(device, telnetHandler, ['filterEvents': false])
  //-> }
  //-> if (settings.log) log.trace "WHA subscribing to Lutron Repeaters >${settings.lutronRepeaters}<"
  //-> settings.lutronRepeaters.each{ d ->
  //->   DevW device = d
  //->   if (settings.log) log.trace "WHA subscribing to ${device.displayName} ${device.id}"
  //->   //unsubscribe(d)
  //->   subscribe(device, repeaterHandler, ['filterEvents': false])
  //-> }
  //-> if (settings.log) log.trace "WHA subscribing to lutron SeeTouch Keypads >${settings.seeTouchKeypad}<"
  settings.lutronSeeTouchKeypads.each{ d ->
    DevW device = d
    if (settings.log) log.trace "WHA subscribing to ${device.displayName} ${device.id}"
    //unsubscribe(d)
    subscribe(device, keypadToVswHandler, ['filterEvents': false])
  }
}
