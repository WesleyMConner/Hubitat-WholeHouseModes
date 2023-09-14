
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

definition (
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

preferences {
  page(name: 'whaPage')
}

Map whaPage () {
  return dynamicPage(
    name: 'whaPage',
    title: heading('Whole House Automation (WHA)<br/>') \
      + bullet("Press ${red('<b>Done</b>')} to ensure event subscriptions update!"),
    install: true,
    uninstall: true,
    nextPage: 'whaPage'
  ) {
    app.updateLabel('Whole House Automation')
    state.MODE_PBSG_APP_NAME = 'pbsg_modes'
    state.MODE_SWITCH_NAMES = getLocation().getModes().collect{it.name}
    state.DEFAULT_MODE_SWITCH_NAME = getGlobalVar('defaultMode').value
    state.SPECIALTY_BUTTONS = ['ALARM', 'ALL_AUTO', 'ALL_OFF', 'ALL_ON',
      'AWAY', 'CLEANING', 'FLASH', 'PANIC', 'QUIET']
    // SAMPLE STATE & SETTINGS CLEAN UP
    //   - state.remove('X')
    //   - settings.remove('Y')
    // reLabelLeds()
//-> settings.remove('specialFunctionButtons')
    section {
      configureLogging()                            // <- provided by Utils
      input(
        name: 'seeTouchKeypads',
        title: 'Authorize SeeTouch Keypad Access<br/>' \
          + comment('Identify keypads with buttons that:<br/>') \
          + bullet(comment('Trigger specialty whole-house functions.<br/>')) \
          + bullet(comment('Change the Hubitat mode.')),
        type: 'device.LutronSeeTouchKeypad',
        submitOnChange: true,
        required: false,
        multiple: true
      )

      input(
        name: 'specialFnButtons',
        title: 'Identify Special Function Buttons<br/>' \
          + comment("Examples: ${state.SPECIALTY_BUTTONS}"),
        type: 'device.LutronComponentSwitch',
        submitOnChange: true,
        required: false,
        multiple: true
      )
      if (settings?.specialFnButtons == null) {
        paragraph(red('No specialty activation buttons are selected.'))
      } else {
        identifyLedButtonsForListItems(         // From UtilsLibrary.groovy
          state.SPECIALTY_BUTTONS,              //   - list
          settings.specialFnButtons,           //   - ledDevices
          'specialFnButton'                     //   - prefix
        )
        populateStateKpadButtons('specialFnButton')
        populateStateKpadButtonDniToSpecialFnButtons()
      }

      input(
        name: 'lutronModeButtons',
        title: 'lutronModeButtons<br/>' \
          + comment('Identify Keypad LEDs/Buttons that change the Hubitat mode.'),
        type: 'device.LutronComponentSwitch',
        submitOnChange: true,
        required: false,
        multiple: true
      )
      if (state.MODE_SWITCH_NAMES == null || settings?.lutronModeButtons == null) {
        paragraph(red('Mode activation buttons are pending pre-requisites.'))
      } else {
        identifyLedButtonsForListItems(         // From UtilsLibrary.groovy
          state.MODE_SWITCH_NAMES,              //   - list
          settings.lutronModeButtons,           //   - ledDevices
          'modeButton'                          //   - prefix
        )
        populateStateKpadButtons('modeButton')
        populateStateKpadButtonDniToTargetMode()
      }
      identifyParticipatingRooms()
      if (!settings.rooms) {
        paragraph red('Management of child apps is pending selection of Room Names.')
      } else {
        keepOldestAppObjPerAppLabel([*settings.rooms, state.MODE_PBSG_APP_NAME], false)
        displayInstantiatedRoomHrefs()
        displayInstantiatedPbsgHref(
          state.MODE_PBSG_APP_NAME,
          'modePBSG',
          'modePbsgPage',
          state.MODE_SWITCH_NAMES,
          state.DEFAULT_MODE_SWITCH_NAME
        )
      }
      paragraph(
        heading('Debug<br/>')
        + "${ displayState() }<br/>"
        + "${ displaySettings() }"
      )
    }
  }
}

void identifyParticipatingRooms () {
  roomPicklist = app.getRooms().collect{it.name}.sort()
  input(
    name: 'rooms',
    type: 'enum',
    title: '<b>Select Participating Rooms</b>',
    options: roomPicklist,
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void displayInstantiatedRoomHrefs () {
  paragraph heading('Room Scene Configuration')
  settings.rooms.each{ roomName ->
    InstAppW roomApp = app.getChildAppByLabel(roomName)
    if (!roomApp) {
      if (settings.log) log.trace(
        "WHA addRoomAppsIfMissing() Adding room ${roomName}"
      )
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

void populateStateKpadButtonDniToSpecialFnButtons () {
  Map<String, String> result = [:]
  state.specialFnButtonMap.collect{ kpadDni, buttonMap ->
    buttonMap.each{ buttonNumber, specialtyFn ->
      result["${kpadDni}-${buttonNumber}"] = specialtyFn
    }
  }
  state.kpadButtonDniToSpecialtyFn = result
}


void populateStateKpadButtonDniToTargetMode () {
  Map<String, String> result = [:]
  state.modeButtonMap.collect{ kpadDni, buttonMap ->
    buttonMap.each{ buttonNumber, targetMode ->
      result["${kpadDni}-${buttonNumber}"] = targetMode
    }
  }
  state.kpadButtonDniToTargetMode = result
}

void updateLutronKpadLeds (String currMode) {
  settings.lutronModeButtons.each{ ledObj ->
    String modeTarget = state.kpadButtonDniToTargetMode[ledObj.getDeviceNetworkId()]
    if (currMode == modeTarget) {
      ledObj.on()
    } else {
      ledObj.off()
    }
  }
}

void pbsgVswTurnedOnCallback (String currMode) {
  // Design Notes
  //   - The modePbsg instance calls this method to reflect a state change.
  //   - When a PBSG-managed switch turns on, its peers can be presumed to be off.
  //   - This function's response includes setting mode Keypad LEDs on/off.
  //   - SeeTouch Keypad LEDs are switches that respond to on/off.
  //   - Access to LEDs is approved via a per-scene list of LEDs:
  //       modeButton_<scene> → ["<description>: <LED DNI>", ...]
  log.trace(
    "WHA pbsgVswTurnedOnCallback() activating <b>mode = ${currMode}</b>."
  )
  getLocation().setMode(currMode)
  updateLutronKpadLeds(currMode)
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

void installed () {
  if (settings.log) log.trace 'WHA installed()'
  initialize()
}

void uninstalled () {
  if (settings.log) log.trace "WHA uninstalled()"
  removeAllChildApps()
}

void updated () {
  if (settings.log) log.trace 'WHA updated()'
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void specialFnHandler (Event e) {
  switch (e.name) {
    case 'pushed':
      String specialtyFunction = state.specialFnButtonMap?.getAt(e.deviceId.toString())
                                                         ?.getAt(e.value)
      if (specialtyFunction == null) return
      switch(specialtyFunction) {
        case 'ALARM':
        case 'ALL_AUTO':
        case 'ALL_OFF':
        case 'ALL_ON':
        case 'AWAY':
        case 'CLEANING':
        case 'FLASH':
        case 'PANIC':
        case 'QUIET':
          if (settings.log) log.trace(
            "WHA specialFnHandler() <b>${specialtyFunction}</b> "
            + "function execution is <b>TBD</b>"
          )
          break
        default:
          // Silently
          log.error(
            "WHA specialFnHandler() Unknown specialty function "
            + "<b>'${specialtyFunction}'</b>"
          )
      }
      break;
    case 'held':
    case 'released':
    default:
      if (settings.log) log.trace(
        "WHA specialFnHandler() ignoring ${e.name} ${e.deviceId}-${e.value}"
      )
  }
}

void modeChangeHandler (Event e) {
  // Design Note
  //   - The field e.deviceId arrives as a number and must be cast toString().
  //   - Hubitat runs Groovy 2.4. Groovy 3 constructs - x?[]?[] - are not available.
  //   - Keypad buttons are matched to state data to activate a target VSW.
  switch (e.name) {
    case 'pushed':
      String targetVsw = state.modeButtonMap?.getAt(e.deviceId.toString())
                                            ?.getAt(e.value)
      if (targetVsw) {
        if (settings.log) log.trace "WHA modeChangeHandler() turning on ${targetVsw}"
        app.getChildAppByLabel(state.MODE_PBSG_APP_NAME).turnOnSwitch(targetVsw)
      }
      // Silently ignore buttons that DO NOT impact Hubitat mode.
      break;
    case 'held':
    case 'released':
    default:
      if (settings.log) log.trace(
        "WHA modeChangeHandler() ignoring ${e.name} ${e.deviceId}-${e.value}"
      )
  }
}

void initialize () {
  // Design Notes
  //   - The same keypad may be associated with two different, specialized handlers
  //     (e.g., mode changing buttons vs special functionalily buttons).
  if (settings.log) log.trace "WHA initialize()"
  settings.seeTouchKeypads.each{ d ->
    DevW device = d
    if (settings.log) log.trace(
      "WHA initialize() subscribing ${getDeviceInfo(device)} to mode handler."
    )
    subscribe(device, modeChangeHandler, ['filterEvents': true])
  }
  settings.seeTouchKeypads.each{ d ->
    DevW device = d
    if (settings.log) log.trace "WHA subscribing to ${getDeviceInfo(device)}"
    subscribe(device, specialFnHandler, ['filterEvents': true])
  }
}
