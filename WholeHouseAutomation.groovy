
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
  page(name: 'whaPage')
}

Map whaPage() {
  return dynamicPage(
    name: 'whaPage', title: 'Whole House Automation (WHA)',
    install: true,
    uninstall: true,
    nextPage: 'whaPage'
  ) {
    section {
      // SAMPLE STATE CLEAN UP
      //   - state.remove('KpadButtons')
      state.PBSG_CHILD_APP_NAME = 'pbsg_modes'
      app.updateLabel('Whole House Automation')
      paragraph heading('Whole House Automation<br/>') \
        + bullet('Select participating rooms and authorize device access.<br/>') \
        + bullet('Click <b>Done</b> to proceed to defining <b>Room Scene(s)</b>.')
      // reLabelLeds()
      solicitLog()                          // <- provided by Utils
      solictFocalRoomNames()
      solicitLutronTelnetDevice()
      solicitLutronMainRepeaters()
      solicitLutronMiscellaneousKeypads()
      solicitSeeTouchKeypads()
      solicitLutronPicos()
      solicitLutronLEDs ()
      solicitSwitches()
      selectLedsForMode()
      deriveKpadDNIandButtonToMode()
      manageChildApps()
      paragraph heading('Room Scene Configuration')
      displayRoomNameHrefs()
      paragraph heading('PBSG Configuration')
      displayPbsgHref()
      paragraph(
        heading('Debug<br/>')
        + "${ displayState() }<br/>"
        + "${ displaySettings() }"
      )
    }
  }
}

// -------------------------------------------------
// R E N A M E   L U T R O N   L E D   B U T T O N S
// -------------------------------------------------
void newLedLabel (String deviceName, String newDeviceLabel) {
  DevW d = settings.lutronLEDs.findAll{it.name == deviceName}?.first()
  d.setLabel(newDeviceLabel)
}

void reLabelLeds () {
  // -----------------------------------------------------------------
  // I M P O R T A N T - Permission must be granted to individual LEDs
  //                     BEFORE they can be relabled.
  // -----------------------------------------------------------------
  //newLedLabel('(lutron-44) Garage KPAD LED 1','Garage KPAD 1 - ALL AUTO')
  //newLedLabel('(lutron-44) Garage KPAD LED 2','Garage KPAD 2 - AWAY')
  //newLedLabel('(lutron-44) Garage KPAD LED 3','Garage KPAD 3 - Den Party')
  //newLedLabel('(lutron-44) Garage KPAD LED 4','Garage KPAD 4 - Den Chill')
  //newLedLabel('(lutron-44) Garage KPAD LED 5','Garage KPAD 5 - Den TV')
  //newLedLabel('(lutron-44) Garage KPAD LED 6','Garage KPAD 6 - Hall')
  // -----------------------------------------------------------------
  //newLedLabel('(lutron-80) TV Wall KPAD LED 1 - COOK O/O','TV KPAD 1 - COOK')
  //newLedLabel('(lutron-80) TV Wall KPAD LED 2','TV KPAD 2 - Den Chill')
  //newLedLabel('(lutron-80) TV Wall KPAD LED 3','TV KPAD 3 - Den TV')
  //newLedLabel('(lutron-80) TV Wall KPAD LED 4 - Chill (D)','TV KPAD 4 - Guest High')
  //newLedLabel('(lutron-80) TV Wall KPAD LED 5 - Theater (D)','TV KPAD 5 - Guest Low')
  //newLedLabel('(lutron-80) TV Wall KPAD LED 6','TV KPAD 6 - Accent')
  // -----------------------------------------------------------------
  //newLedLabel('(lutron-41) Kit Slider KPAD LED 1','K Slider KPAD 1 - Lanai Party')
  //newLedLabel('(lutron-41) Kit Slider KPAD LED 2','K Slider KPAD 2 - Lanai Play')
  //newLedLabel('(lutron-41) Kit Slider KPAD LED 3','K Slider KPAD 3 - Lanai Chill')
  //newLedLabel('(lutron-41) Kit Slider KPAD LED 4','K Slider KPAD 4 - Lanai TV')
  //newLedLabel('(lutron-41) Kit Slider KPAD LED 5','K Slider KPAD 5 - Dining')
  //newLedLabel('(lutron-41) Kit Slider KPAD LED 6','K Slider KPAD 6 - Seating')
  // -----------------------------------------------------------------
  //newLedLabel('(lutron-82) Visor CTRL LED 1','Visor KPAD 1 - ')
  //newLedLabel('(lutron-82) Visor CTRL LED 2','Visor KPAD 2 - ')
  //newLedLabel('(lutron-82) Visor CTRL LED 3','Visor KPAD 3 - ')
  //newLedLabel('(lutron-82) Visor CTRL LED 4','Visor KPAD 4 - ')
  //newLedLabel('(lutron-82) Visor CTRL LED 5','Visor KPAD 5 - ')
  //newLedLabel('(lutron-82) Visor CTRL LED 6','Visor KPAD 6 - ')
  // -----------------------------------------------------------------
  //newLedLabel('(lutron-38) Dining KPAD LED 1','Dining KPAD 1 - COOK')
  //newLedLabel('(lutron-38) Dining KPAD LED 2','Dining KPAD 2 - Entry Party')
  //newLedLabel('(lutron-38) Dining KPAD LED 3','Dining KPAD 3 - Entry Play')
  //newLedLabel('(lutron-38) Dining KPAD LED 4','Dining KPAD 4 - Entry Chill')
  //newLedLabel('(lutron-38) Dining KPAD LED 5','Dining KPAD 5 - Entry TV')
  //newLedLabel('(lutron-38) Dining KPAD LED 6','Dining KPAD 6 - Niche')
  // -----------------------------------------------------------------
  //newLedLabel('(lutron-29) Entry LHS KPAD LED 1','EntryL KPAD 1 - ALL AUTO')
  //newLedLabel('(lutron-29) Entry LHS KPAD LED 2','EntryL KPAD 2 - AWAY')
  //newLedLabel('(lutron-29) Entry LHS KPAD LED 3','EntryL KPAD 3 - Yard High')
  //newLedLabel('(lutron-29) Entry LHS KPAD LED 4','EntryL KPAD 4 - Yard Low')
  //newLedLabel('(lutron-29) Entry LHS KPAD LED 5','EntryL KPAD 5 - Yard Off')
  //newLedLabel('(lutron-29) Entry LHS KPAD LED 6','EntryL KPAD 6 - Porch')
  // -----------------------------------------------------------------
  //newLedLabel('(lutron-10) Entry RHS KPAD LED 1 - ALL AUTO','EntryR KPAD 1 - FLASH')
  //newLedLabel('(lutron-10) Entry RHS KPAD LED 2','EntryR KPAD 2 - Entry Party')
  //newLedLabel('(lutron-10) Entry RHS KPAD LED 3','EntryR KPAD 3 - Entry Play')
  //newLedLabel('(lutron-10) Entry RHS KPAD LED 4','EntryR KPAD 4 - Entry Chill')
  //newLedLabel('(lutron-10) Entry RHS KPAD LED 5','EntryR KPAD 5 - Entry TV')
  //newLedLabel('(lutron-10) Entry RHS KPAD LED 6','EntryR KPAD 6 - Foyer')
  // -----------------------------------------------------------------
  //newLedLabel('(lutron-32) Central KPAD LED 1','Central KPAD 1 - CLEANING')
  //newLedLabel('(lutron-32) Central KPAD LED 2','Central KPAD 2 - DAY')
  //newLedLabel('(lutron-32) Central KPAD LED 3','Central KPAD 3 - NIGHT')
  //newLedLabel('(lutron-32) Central KPAD LED 4','Central KPAD 4 - PARTY')
  //newLedLabel('(lutron-32) Central KPAD LED 5','Central KPAD 5 - CHILL')
  //newLedLabel('(lutron-32) Central KPAD LED 6','Central KPAD 6 - TV')
  //newLedLabel('(lutron-32) Central KPAD LED 7','Central KPAD 7 - ALL OFF')
  // -----------------------------------------------------------------
  //newLedLabel('(lutron-20) Mstr Entry KPAD LED 1','Master KPAD 1 - Master High')
  //newLedLabel('(lutron-20) Mstr Entry KPAD LED 2','Master KPAD 2 - Master Low')
  //newLedLabel('(lutron-20) Mstr Entry KPAD LED 3','Master KPAD 3 - Master Off')
  //newLedLabel('(lutron-20) Mstr Entry KPAD LED 4','Master KPAD 4 - Bath High')
  //newLedLabel('(lutron-20) Mstr Entry KPAD LED 5','Master KPAD 5 - Bath Off')
  //newLedLabel('(lutron-20) Mstr Entry KPAD LED 6','Master KPAD 6 - Lamps')
  // -----------------------------------------------------------------
  //newLedLabel('(lutron-46) Mstr Slider KPAD LED 1','M Slider KPAD 1 - Master High')
  //newLedLabel('(lutron-46) Mstr Slider KPAD LED 2','M Slider KPAD 2 - Master Low')
  //newLedLabel('(lutron-46) Mstr Slider KPAD LED 3','M Slider KPAD 3 - Master Off')
  //newLedLabel('(lutron-46) Mstr Slider KPAD LED 4','M Slider KPAD 4 - Lanai Chill')
  //newLedLabel('(lutron-46) Mstr Slider KPAD LED 5','M Slider KPAD 5 - Lanai TV')
  //newLedLabel('(lutron-46) Mstr Slider KPAD LED 6','M Slider KPAD 6 - Spots')
  // -----------------------------------------------------------------
  //newLedLabel('(lutron-73) Tabletop KPAD LED 1','T-Top KPAD 1 - AWAY')
  //newLedLabel('(lutron-73) Tabletop KPAD LED 2','T-Top KPAD 2 - CHILL')
  //newLedLabel('(lutron-73) Tabletop KPAD LED 3','T-Top KPAD 3 - THEATER')
  //newLedLabel('(lutron-73) Tabletop KPAD LED 4','T-Top KPAD 4 - NIGHT')
  //newLedLabel('(lutron-73) Tabletop KPAD LED 5','T-Top KPAD 5 - Floor Lamp')
  //newLedLabel('(lutron-73) Tabletop KPAD LED 6','T-Top KPAD 6 - Den+ Auto')
  //newLedLabel('(lutron-73) Tabletop KPAD LED 7','T-Top KPAD 7 - Guest+ Auto')
  //-->newLedLabel('(lutron-73) Tabletop KPAD LED 8','T-Top KPAD 8 - Main+ Auto')
  //-->newLedLabel('(lutron-73) Tabletop KPAD LED 9','T-Top KPAD 9 - Master_ Auto')
  //-->newLedLabel('(lutron-73) Tabletop KPAD LED 10','T-Top KPAD 10 - Yard Auto')
  //-->newLedLabel('(lutron-73) Tabletop KPAD LED 11','T-Top KPAD 11 - Mute Doors')
  //-->newLedLabel('(lutron-73) Tabletop KPAD LED 12','T-Top KPAD 12 - Mute Sliders')
  //-->newLedLabel('(lutron-73) Tabletop KPAD LED 13','T-Top KPAD 13 - A L A R M')
  //-->newLedLabel('(lutron-73) Tabletop KPAD LED 14','T-Top KPAD 14 - P A N I C')
  //-->newLedLabel('(lutron-73) Tabletop KPAD LED 15','T-Top KPAD 15 - Q U I E T')
  //-->newLedLabel('(lutron-73) Tabletop KPAD LED 16','T-Top KPAD 16 - ALL ON')
  //-->newLedLabel('(lutron-73) Tabletop KPAD LED 17','T-Top KPAD 17 - ALL OFF')
  // -----------------------------------------------------------------
}

// -----------------------------------
// W H A   P A G E   &   S U P P O R T
// -----------------------------------

void solictFocalRoomNames () {
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

void selectLedsForMode() {
  // Design Note
  //   Keypad LEDs are used as a proxy for Keypad buttons.
  //     - The button's displayName is meaningful to clients.
  //     - The button's deviceNetworkId is <KPAD DNI> hyphen <BUTTON #>
  if (state.modeSwitchNames == null || getLedDevices() == null) {
    paragraph(red(
      'Mode activation buttons are pending pre-requisites above.'
    ))
  } else {
    state.modeSwitchNames.each{ msn ->
      input(
        name: "${msn}_LEDs",
        type: 'enum',
        width: 6,
        title: emphasis("Buttons/LEDs activating '${msn}'"),
        submitOnChange: true,
        required: false,
        multiple: true,
        options: getLedDevices().collect{ d ->
          "${d.displayName}: ${d.deviceNetworkId}"
        }?.sort()
      )
    }
  }
}

void deriveKpadDNIandButtonToMode () {
  // Design Note
  //   The Keypad LEDs collected by selectForMode() function as a proxy for
  //   Keypad button presses. Settings data includes the user-friendly
  //   LED displayName and the LED device ID, which is comprised of 'Keypad
  //   Device Id' and 'Button Number', concatenated with a hyphen. This
  //   method populates "state.[<KPAD DNI>]?.[<KPAD Button #>] = mode".
  state.kpadButtons = [:]
  // Sample Settings Data
  //     key: Day_LEDs,
  //   value: [Central KPAD 2 - DAY: 5953-2]
  //           ^User-Friendly Name
  //                                 ^Keypad DNI
  //                                      ^Keypad Button Number
  // The 'value' is first parsed into a list with two components:
  //   - User-Friendly Name
  //   - Button DNI               [The last() item in the parsed list.]
  // The Button DNI is further parsed into a list with two components:
  //   - Keypad DNI
  //   - Keypad Button number
  settings.each{ key, value ->
    if (key.contains('_LEDs')) {
      String mode = key.minus('_LEDs')
      value.each{ item ->
        List<String> kpadDniAndButton = item?.tokenize(' ')?.last()?.tokenize('-')
        if (kpadDniAndButton.size() == 2 && mode) {
          if (!state.kpadButtons[kpadDniAndButton[0]]) state.kpadButtons[kpadDniAndButton[0]] = [:]
          state.kpadButtons[kpadDniAndButton[0]][kpadDniAndButton[1]] = mode
        }
      }
  }
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
  if (settings.log) log.trace (
    'WHA manageChildApps() on entry getAllChildApps(): '
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
  LinkedHashMap<String, InstAppW> childAppsByLabel = keepOldestAppObjPerAppLabel()
  if (settings.log) log.trace (
    'WHA manageChildApps() after keepOldestAppObjPerAppLabel(): '
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
        childAppsByLabel[roomName] ?: addChildApp('wesmc', 'RoomScenes', roomName)
      ]
    }
  if (settings.log) {
    List<String> kids = childAppsByRoom.collect{ roomName, roomObj ->
      "<b>${roomName}</b> -> ${roomObj.getId()}"
    }
    log.trace(
      'WHA manageChildApps() after adding any missing Room Scene apps: '
      + "${kids ? kids.join(', ') : red('<b>NULL</b>')}"
    )
  }
  state.roomNameToRoomScenes = childAppsByRoom
  // -------------------------
  // P O P U L A T E   P B S G
  // -------------------------
  // Ensure imutable PBSG init data is in place AND instance(s) exist.
  // The PBSG instance manages its own state data locally.
  state.modeSwitchNames = getLocation().getModes().collect{it.name}
  state.defaultModeSwitchName = getGlobalVar('defaultMode').value
  InstAppW pbsgApp = childAppsByLabel[state.PBSG_CHILD_APP_NAME]
    ?:  addChildApp('wesmc', 'whaPBSG', state.PBSG_CHILD_APP_NAME)
  if (settings.log) log.trace(
    "WHA manageChildApps() initializing ${state.PBSG_CHILD_APP_NAME} with<br/>"
    + "<b>modeSwitchNames:</b> ${state.modeSwitchNames},<br/>"
    + "<b>defaultModeSwitchName:</b> ${state.defaultModeSwitchName}<br/>"
    + "<b>logging:</b> ${settings.log}"
  )
  pbsgApp.configure(
    state.modeSwitchNames,
    state.defaultModeSwitchName,
    settings.log
  )
  state.pbsg_modes = pbsgApp
  // ---------------------------------------------
  // P U R G E   E X C E S S   C H I L D   A P P S
  // ---------------------------------------------
  childAppsByLabel.each{ label, app ->
    if (childAppsByRoom?.keySet().findAll{it == label}) {
      // Skip, still in use
    } else if (label == state.PBSG_CHILD_APP_NAME) {
      // Skip, still in use
    } else {
      if (settings.log) log.trace(
        "WHA manageChildApps() deleting orphaned child app ${getAppInfo(app)}."
      )
      deleteChildApp(app.getId())
    }
  }
}

void pbsgVswTurnedOn(String simpleName) {
  log.trace(
    "WHA pbsgVswTurnedOn() activating mode='<b>${simpleName}</b> [PENDING]'."
  )
  getLocation().setMode(simpleName)
}

void displayRoomNameHrefs () {
  if (state.roomNameToRoomScenes) {
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
  } else {
    paragraph red("No Rooms have been identified yet.")
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
    log.error 'WHA displayPbsgHref() called with pbsg_modes missing.'
  }
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
    if (settings.focalRoomNames?.contains(kid)) {
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
    + '<a href="https://github.com/WesleyMConner/Hubitat-WholeHouseAutomation" ' \
    + 'target="_blank"><br/>Click for more information</a>')
}

// -----------------------------
// " C H I L D "   S U P P O R T
// -----------------------------

List<DevW> narrowDevicesToRoom (String roomName, List<DevW> devices) {
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

List<DevW> getLedDevices() {
  return settings.lutronLEDs
}

List<DevW> getPicoDevices() {
  return settings.lutronPicos
}

List<DevW> getNonLutronDevicesForRoom (String roomName) {
  List<DevW> roomSwitches = narrowDevicesToRoom(roomName, settings.Switches)
                            .findAll{
                              it.displayName.toString().contains('lutron') == false
                            }
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

void keypadHandler (Event e) {
  // Design Note
  //   - The field e.deviceId arrives as a number and must be cast toString().
  //   - Hubitat runs Groovy 2.4. Groovy 3 constructs - x?[]?[] - are not available.
  //   - Hubitat mode is adjusted via pbsg-modes-X switches exclusivly.
  if (e.name == 'pushed') {
    String nextMode = state.kpadButtons.getAt(e.deviceId.toString())?.getAt(e.value)
    if (settings.log) log.trace(
      "WHA keypadHandler() <b>Keypad Device Id:</b> ${e.deviceId}, "
      + "<b>Keypad Button:</b> ${e.value}, <b>Requested Mode:</b> ${nextMode}"
      //+ "<b>switchShortNameToDNI(nextMode):</b> ${switchShortNameToDNI(nextMode)}, "
      //+ "<b>getChildDevice(switchShortNameToDNI(nextMode)):</b> ${getChildDevice(switchShortNameToDNI(nextMode))}"
    )
    // Turn on appropriate pbsg-modes-X VSW.
    //-----> app.getChildDevice(switchShortNameToDNI("pbsg_modes_${nextMode}"))?.on()
    //-----> turnOnSwitch(nextMode)
  } else {
    if (settings.log) log.trace(
      "WHA keypadHandler() unexpected event name '${e.name}' for DNI '${e.deviceId}'"
    )
  }
}

void initialize() {
  // TACTICALLY, DROP EVERYTHING
  if (settings.log) log.trace "WHA initialize()"
  if (settings.log) log.trace "WHA subscribing to Lutron Telnet >${settings.lutronTelnet}<"
  settings.lutronTelnet.each{ d ->
    DevW device = d
    if (settings.log) log.trace "WHA subscribing ${device.displayName} ${device.id}"
    //unsubscribe(d)
    subscribe(device, telnetHandler, ['filterEvents': false])
  }
  if (settings.log) log.trace "WHA subscribing to Lutron Repeaters >${settings.lutronRepeaters}<"
  settings.lutronRepeaters.each{ d ->
    DevW device = d
    if (settings.log) log.trace "WHA subscribing to ${device.displayName} ${device.id}"
    //unsubscribe(d)
    subscribe(device, repeaterHandler, ['filterEvents': false])
  }
  if (settings.log) log.trace "WHA subscribing to lutron SeeTouch Keypads >${settings.seeTouchKeypad}<"
  settings.seeTouchKeypad.each{ d ->
    DevW device = d
    if (settings.log) log.trace "WHA subscribing to ${device.displayName} ${device.id}"
    //unsubscribe(d)
    subscribe(device, keypadHandler, ['filterEvents': false])
  }
}
