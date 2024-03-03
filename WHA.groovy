
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

// The Groovy Linter generates false positives on Hubitat #include !!!
#include wesmc.lFifo
#include wesmc.lHExt
#include wesmc.lHUI
#include wesmc.lLut
#include wesmc.lPbsg

definition (
  name: 'WHA',
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
  page(name: 'WhaPage')
}

//---- CORE METHODS (External)

//---- CORE METHODS (Internal)

InstAppW _getOrCreateMPbsg () {
  // Mpbsg depends on Hubitat Mode properties AND NOT local data.
  InstAppW pbsgApp = app.getChildAppByLabel(state.MPBSG_LABEL)
  if (!pbsgApp) {
    logWarn('_getOrCreateMPbsg', "Adding Mode PBSG ${state.MPBSG_LABEL}")
    pbsgApp = addChildApp('wesmc', 'MPbsg', state.MPBSG_LABEL)
    ArrayList<String> modeNames = getLocation().getModes().collect{ it.name }
    String currModeName = getLocation().currentMode.name
    pbsgApp.pbsgConfigure(
      modeNames,     // Create a PBSG button per Hubitat Mode name
      'Day',         // 'Day' is the default Mode/Button
      currModeName,  // Activate the Button for the current Mode
      settings.pbsgLogThresh ?: 'INFO' // 'INFO' for normal operations
                                       // 'DEBUG' to walk key PBSG methods
                                       // 'TRACE' to include PBSG and VSW state
    )
  }
  return pbsgApp
}

void _writeMPbsgHref () {
  InstAppW pbsgApp = _getOrCreateMPbsg()
  if (pbsgApp) {
    href(
      name: appInfo(pbsgApp),
      width: 2,
      url: "/installedapp/configure/${pbsgApp.id}/MPbsgPage",
      style: 'internal',
      title: "Review ${appInfo(pbsgApp)}",
      state: null
    )
  } else {
    paragraph "Creation of the MPbsgHref is pending required data."
  }
}

void AllAuto () {
  settings.rooms.each{ roomName ->
    InstAppW roomApp = app.getChildAppByLabel(roomName)
    String manualOverrideSwitchDNI = "pbsg_${roomApp.label}_AUTOMATIC"
    logInfo('AllAuto', "Turning on ${b(manualOverrideSwitchDNI)}")
    roomApp.getRSPbsg().turnOnSwitch(manualOverrideSwitchDNI)
  }
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

void buttonOnCallback (String mode) {
  // - The MPbsg instance calls this method to reflect a state change.
  logInfo('buttonOnCallback', "Received mode: ${b(mode)}")
  getLocation().setMode(mode)
  updateLutronKpadLeds(mode)
}

//---- EVENT HANDLERS

void seeTouchSpecialFnButtonHandler (Event e) {
  switch (e.name) {
    case 'pushed':
      String specialtyFunction = state.specialFnButtonMap?.getAt(e.deviceId.toString())
                                                         ?.getAt(e.value)
      if (specialtyFunction == null) return
      switch(specialtyFunction) {
        case 'ALL_AUTO':
          logInfo('seeTouchSpecialFnButtonHandler', 'executing ALL_AUTO')
          AllAuto()
          //--TBD--> Update of Kpad LEDs
          break;
        case 'ALARM':
        case 'AWAY':
        case 'FLASH':
        case 'PANIC':
        case 'QUIET':
          logWarn(
            'seeTouchSpecialFnButtonHandler',
            "${b(specialtyFunction)} function execution is <b>TBD</b>"
          )
          break
        default:
          // Silently
          logError(
            'seeTouchSpecialFnButtonHandler',
            "Unknown specialty function ${b(specialtyFunction)}"
          )
      }
      break;
    case 'held':
    case 'released':
    default:
      logWarn(
        'seeTouchSpecialFnButtonHandler',
        "ignoring ${e.name} ${e.deviceId}-${e.value}"
      )
  }
}

void seeTouchModeButtonHandler (Event e) {
  // Design Note
  //   - Process Lutron SeeTouch Kpad events.
  //   - The field e.deviceId arrives as a number and must be cast toString().
  //   - Hubitat runs Groovy 2.4. Groovy 3 constructs - x?[]?[] - are not available.
  //   - Kpad buttons are matched to state data to activate a PBSG button.
  switch (e.name) {
    case 'pushed':
      String targetButton = state.modeButtonMap?.getAt(e.deviceId.toString())
                                               ?.getAt(e.value)
      if (targetButton) {
        logInfo('seeTouchModeButtonHandler', "turning on ${targetButton}")
        _getOrCreateMPbsg().pbsgToggleButton(targetButton)
      }
      if (targetButton == 'Day') {
        logInfo('seeTouchModeButtonHandler', 'executing ALL_AUTO')
        AllAuto()
      }
      // Silently ignore buttons that DO NOT impact Hubitat mode.
      break;
    case 'held':
    case 'released':
    default:
      logWarn(
        'seeTouchModeButtonHandler',
        "Ignoring ${e.name} ${e.deviceId}-${e.value}"
      )
  }
}

String extractDeviceIdFromLabel(String deviceLabel) {
  //->x = (deviceLabel =~ /\((.*)\)/)
  //->logDebug('extractDeviceIdFromLabel', [
  //->  "deviceLabel: ${deviceLabel}",
  //->  "x: ${x}",
  //->  "x[0]: ${x[0]}",
  //->  "x[0]: ${x[0][1]}",
  //->])
  return (deviceLabel =~ /\((.*)\)/)[0][1]
}

void repeaterHandler(Event e) {
  logInfo('WHA repeaterHandler', "At entry ${e.descriptionText}")
  // Isolate Main Repeater (ra2-1, ra2-83, pro2-1) buttonLed-## events to
  // capture de-centralized Room Scene activation.
  if (e.name.startsWith('buttonLed-')) {
    Integer eventButton = safeParseInt(e.name.substring(10))
    String deviceId = extractDeviceIdFromLabel(e.displayName)
    logInfo('WHA repeaterHandler', "${deviceId}..${eventButton}..${e.value}")
  }
}


//---- SYSTEM CALLBACKS

void installed () {
  logWarn('installed', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled () {
  logWarn('uninstalled', 'Entered')
  removeAllChildApps()
}

void updated () {
  logWarn('updated', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  //---------------------------------------------------------------------------------
  // REMOVE NO LONGER USED SETTINGS AND STATE
  //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
  //   - state.remove('X')
  //   - app.removeSetting('Y')
  //---------------------------------------------------------------------------------
  state.remove('MODE_PBSG_APP_LABEL')
  state.remove('MODE_PBSG_APP_NAME')
  app.removeSetting('hubitatQueryString')
  initialize()
}

void initialize () {
  // - The same keypad may be associated with two different, specialized handlers
  //   (e.g., mode changing buttons vs special functionalily buttons).
  logTrace('initialize', 'Entered')
  settings.seeTouchKpads.each{ device ->
    logInfo('initialize', "subscribing ${deviceInfo(device)} to mode handler."
    )
    subscribe(device, seeTouchModeButtonHandler, ['filterEvents': true])
  }
  settings.seeTouchKpads.each{ device ->
    logInfo('initialize', "subscribing ${deviceInfo(device)} to Keypad Handler")
    subscribe(device, seeTouchSpecialFnButtonHandler, ['filterEvents': true])
  }
  settings.mainRepeaters.each{ device ->
    logInfo('initialize', "subscribing ${deviceInfo(device)} to Repeater Handler")
    subscribe(device, repeaterHandler, ['filterEvents': true])
  }
}

//---- GUI / PAGE RENDERING

void _idSpecialFnMainRepeater () {
  input(
    name: 'mainRepeaters',
    title: [
      heading2('Identify Lutron Main Repeater(s) for Room Scene LED Events'),
      bullet2('e.g., ALL_AUTO, ALARM, AWAY, FLASH, PANIC, QUIET')
    ].join('<br/>'),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _idSpecialFnButtons () {
  input(
    name: 'specialFnButtons',
    title: [
      heading2('Identify Special Function Buttons'),
      bullet2("Examples: ${state.SPECIALTY_BUTTONS}")
    ].join('<br/>'),
    type: 'device.LutronComponentSwitch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _populateSpecialFnButtonMap () {
  Map<String, String> result = [:]
  state.specialFnButtonMap.collect{ kpadDni, buttonMap ->
    buttonMap.each{ buttonNumber, specialtyFn ->
      result["${kpadDni}-${buttonNumber}"] = specialtyFn
    }
  }
  state.kpadButtonDniToSpecialtyFn = result
}

void _wireSpecialFnButtons () {
  if (settings?.specialFnButtons == null) {
    paragraph bullet2('No specialty activation buttons are selected.')
  } else {
    identifyLedButtonsForListItems(         // From UtilsLibrary.groovy
      state.SPECIALTY_BUTTONS,              //   - list
      settings.specialFnButtons,            //   - ledDevices
      'specialFnButton'                     //   - prefix
    )
    populateStateKpadButtons('specialFnButton')
    _populateSpecialFnButtonMap()
  }
}

void _idKpadsWithModeButtons () {
  input(
    name: 'seeTouchKpads',
    title: [
      heading2('Identify Kpad(s) with Mode Selection Buttons'),
      bullet2('The identified buttons are used to set the Hubitat mode')
    ].join('<br/>'),
    type: 'device.LutronSeeTouchKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _idKpadModeButtons () {
  input(
    name: 'lutronModeButtons',
    title: heading2('Identify Kpad Mode Selection Buttons'),
    type: 'device.LutronComponentSwitch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _populateStateKpadButtonDniToTargetMode () {
  Map<String, String> result = [:]
  state.modeButtonMap.collect{ kpadDni, buttonMap ->
    buttonMap.each{ buttonNumber, targetMode ->
      result["${kpadDni}-${buttonNumber}"] = targetMode
    }
  }
  state.kpadButtonDniToTargetMode = result
}

void _wireModeButtons () {
  if (state.MODES == null || settings?.lutronModeButtons == null) {
    paragraph('Mode activation buttons are pending pre-requisites.')
  } else {
    identifyLedButtonsForListItems(         // From UtilsLibrary.groovy
      state.MODES,                          //   - list
      settings.lutronModeButtons,           //   - ledDevices
      'modeButton'                          //   - prefix
    )
    populateStateKpadButtons('modeButton')
    _populateStateKpadButtonDniToTargetMode()
  }
}

void _idParticipatingRooms () {
  roomPicklist = app.getRooms().collect{it.name.replace(' ', '_')}.sort()
  input(
    name: 'rooms',
    type: 'enum',
    title: heading2('Identify Participating Rooms'),
    options: roomPicklist,
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _displayInstantiatedRoomHrefs () {
  paragraph heading1('Room Scene Configuration')
  settings.rooms.each{ roomName ->
    InstAppW roomApp = app.getChildAppByLabel(roomName)
    if (!roomApp) {
      logWarn(
        'addRoomAppsIfMissing',
        "Adding room ${roomName}"
      )
      roomApp = addChildApp('wesmc', 'RoomScenes', roomName)
    }
    href (
      name: roomName,
      width: 2,
      url: "/installedapp/configure/${roomApp?.id}",
      style: 'internal',
      title: "${appInfo(roomApp)} Scenes",
      state: null, //'complete'
    )
  }
}

Map WhaPage () {
  return dynamicPage(
    name: 'WhaPage',
    title: [
      heading1("Whole House Automation (WHA) - ${app.id}"),
      bullet1('Tab to register changes.'),
      bullet1('Click <b>Done</b> to enable subscriptions.')
    ].join('<br/>'),
    install: true,
    uninstall: true
  ) {
    app.updateLabel('WHA')
    state.MPBSG_LABEL = '_MPbsg'
    state.MODES = getLocation().getModes().collect{ it.name }
    getGlobalVar('defaultMode').value
    state.SPECIALTY_BUTTONS = ['ALARM', 'ALL_AUTO', 'ALL_OFF', 'AWAY',
      'FLASH', 'PANIC', 'QUIET']
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      solicitLogThreshold('pbsgLogThresh', 'INFO') // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      _idSpecialFnMainRepeater()
      _idSpecialFnButtons()
      _idKpadsWithModeButtons()
      _wireSpecialFnButtons()
      _idKpadModeButtons()
      _wireModeButtons()
      _idParticipatingRooms()
      _writeMPbsgHref()
      if (!settings.rooms) {
        // Don't be too aggressive deleting child apps and their config data.
        paragraph('Management of child apps is pending selection of Room Names.')
      } else {
        //TBD-> pruneAppDups(
        //TBD->   [*settings.rooms, state.MPBSG_LABEL],
        //TBD->   app      // The object (parent) pruning dup children
        //TBD-> )
        _displayInstantiatedRoomHrefs()
      }
      paragraph([
        heading1('Debug<br/>'),
        *appStateAsBullets(true),
        *appSettingsAsBullets(true)
      ].join('<br/>'))
    }
  }
}
