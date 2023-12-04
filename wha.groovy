
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
#include wesmc.libFifo
#include wesmc.libHubExt
#include wesmc.libHubUI
#include wesmc.libLutron
#include wesmc.libPbsgCore

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

void AllAuto () {
  settings.rooms.each{ roomName ->
    InstAppW roomApp = app.getChildAppByLabel(roomName)
    String manualOverrideSwitchDNI = "pbsg_${roomApp.getLabel()}_AUTOMATIC"
    Ldebug('AllAuto()', "Turning on <b>${manualOverrideSwitchDNI}</b>")
    roomApp.getScenePbsg().turnOnSwitch(manualOverrideSwitchDNI)
  }
}

void _updateLutronKpadLeds (String currMode) {
  settings.lutronModeButtons.each{ ledObj ->
    String modeTarget = state.kpadButtonDniToTargetMode[ledObj.getDeviceNetworkId()]
    if (currMode == modeTarget) {
      ledObj.on()
    } else {
      ledObj.off()
    }
  }
}

void _buttonOnCallback (String button) {
  // - The ModePbsg instance calls this method to reflect a state change.
  Linfo('_buttonOnCallback()', "Received button: ${b(button)}")
  getLocation().setMode(button)
  _updateLutronKpadLeds(button)
}

void _removeAllChildApps () {
  getAllChildApps().each{ child ->
    Ldebug(
      '_removeAllChildApps()',
      "child: >${child.getId()}< >${child.getLabel()}<"
    )
    deleteChildApp(child.getId())
  }
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
          Ldebug('seeTouchSpecialFnButtonHandler()', 'executing ALL_AUTO')
          AllAuto()
          //--TBD--> Update of Keypad LEDs
          break;
        case 'ALARM':
        case 'AWAY':
        case 'FLASH':
        case 'PANIC':
        case 'QUIET':
          Ldebug(
            'seeTouchSpecialFnButtonHandler()',
            "<b>${specialtyFunction}</b> "
              + "function execution is <b>TBD</b>"
          )
          break
        default:
          // Silently
          Lerror(
            'seeTouchSpecialFnButtonHandler()',
            "Unknown specialty function <b>'${specialtyFunction}'</b>"
          )
      }
      break;
    case 'held':
    case 'released':
    default:
      Ldebug(
        'seeTouchSpecialFnButtonHandler()',
        "ignoring ${e.name} ${e.deviceId}-${e.value}"
      )
  }
}

void seeTouchModeButtonHandler (Event e) {
  // Design Note
  //   - Process Lutron SeeTouch Keypad events.
  //   - The field e.deviceId arrives as a number and must be cast toString().
  //   - Hubitat runs Groovy 2.4. Groovy 3 constructs - x?[]?[] - are not available.
  //   - Keypad buttons are matched to state data to activate a PBSG button.
  switch (e.name) {
    case 'pushed':
      String targetButton = state.modeButtonMap?.getAt(e.deviceId.toString())
                                            ?.getAt(e.value)
      if (targetButton) {
        Ldebug('seeTouchModeButtonHandler()', "turning on ${targetButton}")
        app.getChildAppByLabel(state.MODE_PBSG_LABEL).pbsgActivateButton(targetButton)
      }
      if (targetButton == 'Day') {
        Ldebug('seeTouchModeButtonHandler()', 'executing ALL_AUTO')
        AllAuto()
      }
      // Silently ignore buttons that DO NOT impact Hubitat mode.
      break;
    case 'held':
    case 'released':
    default:
      Ldebug(
        'seeTouchModeButtonHandler()',
        "Ignoring ${e.name} ${e.deviceId}-${e.value}"
      )
  }
}

//---- SYSTEM CALLBACKS

void installed () {
  Ldebug('installed()', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled () {
  Ldebug('uninstalled()', 'Entered')
  _removeAllChildApps()
}

void updated () {
  Ltrace('updated()', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  //---------------------------------------------------------------------------------
  // REMOVE NO LONGER USED SETTINGS AND STATE
  //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
  //   - state.remove('X')
  //   - settings.remove('Y')
  //---------------------------------------------------------------------------------
  state.remove('MODE_PBSG_APP_LABEL')
  state.remove('MODE_PBSG_APP_NAME')


  initialize()
}

void initialize () {
  // - The same keypad may be associated with two different, specialized handlers
  //   (e.g., mode changing buttons vs special functionalily buttons).
  Ldebug('initialize()', 'Entered')
  settings.seeTouchKeypads.each{ d ->
    DevW device = d
    Ldebug('initialize()', "subscribing ${DeviceInfo(device)} to mode handler."
    )
    subscribe(device, seeTouchModeButtonHandler, ['filterEvents': true])
  }
  settings.seeTouchKeypads.each{ d ->
    DevW device = d
    Ldebug('initialize()', "subscribing ${DeviceInfo(device)}")
    subscribe(device, seeTouchSpecialFnButtonHandler, ['filterEvents': true])
  }
}

//---- GUI / PAGE RENDERING

void _idSpecialFnMainRepeater () {
  input(
    name: 'specialtyFnMainRepeater',
    title: [
      Heading2('Identify Lutron Repeater(s) Supporting Specialty Functions'),
      Bullet2('e.g., ALL_AUTO, ALARM, AWAY, FLASH, PANIC, QUIET')
    ].join('<br/>'),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: false
  )
}

void _idKpadsWithModeButtons () {
  input(
    name: 'seeTouchKeypads',
    title: [
      Heading2('Identify Keypad(s) with Mode Selection Buttons'),
      Bullet2('The identified buttons are used to set the Hubitat mode')
    ].join('<br/>'),
    type: 'device.LutronSeeTouchKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _idSpecialFnButtons () {
  input(
    name: 'specialFnButtons',
    title: [
      Heading2('Identify Special Function Buttons'),
      Bullet2("Examples: ${state.SPECIALTY_BUTTONS}")
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
    paragraph Bullet2('No specialty activation buttons are selected.')
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

void _identifyKpadModeButtons () {
  input(
    name: 'lutronModeButtons',
    title: Heading2('Identify Keypad Mode Selection Buttons'),
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

void _identifyParticipatingRooms () {
  roomPicklist = app.getRooms().collect{it.name.replace(' ', '_')}.sort()
  input(
    name: 'rooms',
    type: 'enum',
    title: Heading2('Identify Participating Rooms'),
    options: roomPicklist,
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _displayInstantiatedRoomHrefs () {
  paragraph Heading1('Room Scene Configuration')
  settings.rooms.each{ roomName ->
    InstAppW roomApp = app.getChildAppByLabel(roomName)
    if (!roomApp) {
      Ldebug(
        'addRoomAppsIfMissing()',
        "Adding room ${roomName}"
      )
      roomApp = addChildApp('wesmc', 'roomScenes', roomName)
    }
    href (
      name: roomName,
      width: 2,
      url: "/installedapp/configure/${roomApp?.getId()}",
      style: 'internal',
      title: "<b>${AppInfo(roomApp)}</b> Scenes",
      state: null, //'complete'
    )
  }
}

void _createModePbsgAndPageLink () {
  InstAppW pbsgApp = app.getChildAppByLabel(state.MODE_PBSG_LABEL)
  if (!pbsgApp) {
    Ldebug(
      '_createModePbsgAndPageLink()',
      "Adding Mode PBSG ${state.MODE_PBSG_LABEL}"
    )
    pbsgApp = addChildApp('wesmc', 'ModePbsg', state.MODE_PBSG_LABEL)
  }
  List<String> modeNames = getLocation().getModes().collect{ it.name }
  String currModeName = getLocation().currentMode.name
  pbsgApp.pbsgConfigure(
    modeNames,     // Create a PBSG button per Hubitat Mode name
    'Day',         // 'Day' is the default Mode/Button
    currModeName,  // Activate the Button for the current Mode
    settings.pbsgLogThreshold ?: 'INFO' // 'INFO' for normal operations
                                        // 'DEBUG' to walk key PBSG methods
                                        // 'TRACE' to include PBSG and VSW state
  )
  href(
    name: AppInfo(pbsgApp),
    width: 2,
    url: "/installedapp/configure/${pbsgApp.getId()}/ModePbsgPage",
    style: 'internal',
    title: "Review <b>${AppInfo(pbsgApp)}</b>",
    state: null
  )
}

Map WhaPage () {
  return dynamicPage(
    name: 'WhaPage',
    title: [
      Heading1("Whole House Automation (WHA) - ${app.getId()}"),
      Bullet1('Tab to register changes.'),
      Bullet1("Click <b>${'Done'}</b> to enable subscriptions.")
    ].join('<br/>'),
    install: true,
    uninstall: true
  ) {
    app.updateLabel('WHA')
    state.MODE_PBSG_LABEL = '_ModePbsg'
    state.MODES = getLocation().getModes().collect{ it.name }
    getGlobalVar('defaultMode').value
    state.SPECIALTY_BUTTONS = ['ALARM', 'ALL_AUTO', 'ALL_OFF', 'AWAY',
      'FLASH', 'PANIC', 'QUIET']
    section {
      solicitLogThreshold('appLogThreshold')        // <- provided by Utils
      solicitLogThreshold('pbsgLogThreshold')       // <- provided by Utils
      _idSpecialFnMainRepeater()
      _idKpadsWithModeButtons()
      _idSpecialFnButtons()
      _wireSpecialFnButtons()
      _identifyKpadModeButtons()
      _wireModeButtons()
      _identifyParticipatingRooms()
      _createModePbsgAndPageLink()
      if (!settings.rooms) {
        // Don't be too aggressive deleting child apps and their config data.
        paragraph('Management of child apps is pending selection of Room Names.')
      } else {
        PruneAppDups(
          [*settings.rooms, state.MODE_PBSG_LABEL],
          false,   // For dups, keep oldest
          app      // The object (parent) pruning dup children
        )
        _displayInstantiatedRoomHrefs()
      }
      paragraph([
        Heading1('Debug<br/>'),
        *appStateAsBullets(true),
        *appSettingsAsBullets(true)
      ].join('<br/>'))
    }
  }
}
