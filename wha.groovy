
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

void _pbsgVswTurnedOnCallback (String currPbsgSwitch) {
  String currMode = currPbsgSwitch?.minus("${state.MODE_PBSG_APP_LABEL}_")
  // - The modePbsg instance calls this method to reflect a state change.
  // - When a PBSG-managed switch turns on, its peers can be presumed to be off.
  // - This function's response includes setting mode Keypad LEDs on/off.
  // - SeeTouch Keypad LEDs are switches that respond to on/off.
  // - Access to LEDs is approved via a per-scene list of LEDs:
  //   modeButton_<scene> → ["<description>: <LED DNI>", ...]
  Ldebug(
    '_pbsgVswTurnedOnCallback()',
    "activating <b>mode = ${currMode}</b>."
  )
  getLocation().setMode(currMode)
  _updateLutronKpadLeds(currMode)
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

void specialFnButtonHandler (Event e) {
  switch (e.name) {
    case 'pushed':
      String specialtyFunction = state.specialFnButtonMap?.getAt(e.deviceId.toString())
                                                         ?.getAt(e.value)
      if (specialtyFunction == null) return
      switch(specialtyFunction) {
        case 'ALL_AUTO':
          Ldebug('specialFnButtonHandler()', 'executing ALL_AUTO')
          AllAuto()
          //--TBD--> Update of Keypad LEDs
          break;
        case 'ALARM':
        case 'AWAY':
        case 'FLASH':
        case 'PANIC':
        case 'QUIET':
          Ldebug(
            'specialFnButtonHandler()',
            "<b>${specialtyFunction}</b> "
              + "function execution is <b>TBD</b>"
          )
          break
        default:
          // Silently
          Lerror(
            'specialFnButtonHandler()',
            "Unknown specialty function <b>'${specialtyFunction}'</b>"
          )
      }
      break;
    case 'held':
    case 'released':
    default:
      Ldebug(
        'specialFnButtonHandler()',
        "ignoring ${e.name} ${e.deviceId}-${e.value}"
      )
  }
}

void modeChangeButtonHandler (Event e) {
  // Design Note
  //   - The field e.deviceId arrives as a number and must be cast toString().
  //   - Hubitat runs Groovy 2.4. Groovy 3 constructs - x?[]?[] - are not available.
  //   - Keypad buttons are matched to state data to activate a target VSW.
  switch (e.name) {
    case 'pushed':
      String targetVsw = state.modeButtonMap?.getAt(e.deviceId.toString())
                                            ?.getAt(e.value)
      if (targetVsw) {
        Ldebug(
          'modeChangeButtonHandler()',
          "turning on ${targetVsw}"
        )
        app.getChildAppByLabel(state.MODE_PBSG_APP_LABEL).turnOnSwitch(targetVsw)
      }
      if (targetVsw == 'Day') {
        Ldebug(
          'modeChangeButtonHandler()',
          "executing ALL_AUTO"
        )
        AllAuto()
      }
      // Silently ignore buttons that DO NOT impact Hubitat mode.
      break;
    case 'held':
    case 'released':
    default:
      Ldebug(
        'modeChangeButtonHandler()',
        "ignoring ${e.name} ${e.deviceId}-${e.value}"
      )
  }
}

//---- SYSTEM CALLBACKS

void installed () {
  Ldebug('installed()', '')
  initialize()
}

void uninstalled () {
  Ldebug('uninstalled()', '')
  _removeAllChildApps()
}

void updated () {
  Ltrace('updated()', '')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void initialize () {
  // - The same keypad may be associated with two different, specialized handlers
  //   (e.g., mode changing buttons vs special functionalily buttons).
  Ldebug('initialize()', '')
  settings.seeTouchKeypads.each{ d ->
    DevW device = d
    Ldebug('initialize()', "subscribing ${getDeviceInfo(device)} to mode handler."
    )
    subscribe(device, modeChangeButtonHandler, ['filterEvents': true])
  }
  settings.seeTouchKeypads.each{ d ->
    DevW device = d
    Ldebug('initialize()', "subscribing ${getDeviceInfo(device)}")
    subscribe(device, specialFnButtonHandler, ['filterEvents': true])
  }
}

//---- GUI / PAGE RENDERING

void _authSpecialFnMainRepeaterAccess () {
  input(
    name: 'specialtyFnMainRepeater',
    title: [
      Heading2('Authorize Specialty Function Repeater Access<br/>'),
      Bullet2("Used for 'all off'... behavior.")
    ].join(),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: false
  )
}

void _authSeeTouchKpadAccess () {
  input(
    name: 'seeTouchKeypads',
    title: [
      Heading2('Authorize SeeTouch Keypad Access<br/>'),
      Bullet2('Identify keypads with buttons that:<br/>'),
      Bullet2('Trigger specialty whole-house functions.<br/>'),
      Bullet2('Change the Hubitat mode.')
    ].join(),
    type: 'device.LutronSeeTouchKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _identifySpecialFnButtons () {
  input(
    name: 'specialFnButtons',
    title: [
      Heading2('Identify Special Function Buttons<br/>'),
      Bullet2("Examples: ${state.SPECIALTY_BUTTONS}")
    ].join(),
    type: 'device.LutronComponentSwitch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _populateStateKpadButtonDniToSpecialFnButtons () {
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
    paragraph('No specialty activation buttons are selected.')
  } else {
    identifyLedButtonsForListItems(         // From UtilsLibrary.groovy
      state.SPECIALTY_BUTTONS,              //   - list
      settings.specialFnButtons,            //   - ledDevices
      'specialFnButton'                     //   - prefix
    )
    populateStateKpadButtons('specialFnButton')
    _populateStateKpadButtonDniToSpecialFnButtons()
  }
}

void _identifyKpadModeButtons () {
  input(
    name: 'lutronModeButtons',
    title: [
      Heading2('lutronModeButtons<br/>'),
      Bullet2('Identify Keypad LEDs/Buttons that change the Hubitat mode.')
    ].join(),
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
      state.MODES,              //   - list
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
    title: '<b>Select Participating Rooms</b>',
    options: roomPicklist,
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _pruneOrphanedChildApps () {
  //Initially, assume InstAppW supports instance equality tests -> values is a problem
  List<InstAppW> kids = getAllChildApps()
  Ldebug(
    '_pruneOrphanedChildApps()',
    "processing ${kids.collect{it.getLabel()}.join(', ')}"
  )
  List<String> roomNames =
  kids.each{ kid ->
    if (settings.rooms?.contains(kid)) {
      Ldebug(
        '_pruneOrphanedChildApps()',
        "skipping ${kid.getLabel()} (room)"
      )
    } else {
      Ldebug(
        '_pruneOrphanedChildApps()',
        "deleting ${kid.getLabel()} (orphan)"
      )
      deleteChildApp(kid.getId())
    }
  }
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
      roomApp = addChildApp('wesmc', 'whaRoom', roomName)
    }
    href (
      name: roomName,
      width: 2,
      url: "/installedapp/configure/${roomApp?.getId()}/whaRoomPage",
      style: 'internal',
      title: "<b>${AppInfo(roomApp)}</b> Scenes",
      state: null, //'complete'
    )
  }
}

//--xx-> void _createModePbsgAndLink () {
//--xx->   paragraph heading('Inspect Mode PBSG')
//--xx->   InstAppW modePBSG = app.getChildAppByLabel(state.MODE_PBSG_APP_LABEL)
//--xx->   if (!modePBSG || modePBSG.getAllChildDevices().size() == 0) {
//--xx->     modePBSG = addChildApp('wesmc', 'modePBSG', 'modePbsgPage')
//--xx->   }
//--xx-> }

void _createModePbsgAndPageLink () {
  InstAppW pbsg = app.getChildAppByLabel(pbsgLabel)
    ?: app.addChildApp('wesmc', state.MODE_PBSG_APP_LABEL, pbsgLabel)
  List<String> modeNames = getLocation().getModes().collect{ it.name }
  String currModeName = getLocation().currentMode.name
  pbsg.pbsgConfigure(
    modeNames,     // Create a PBSG button per Hubitat Mode name
    'Day',         // 'Day' is the default Mode/Button
    currModeName   // Activate the Button for the current Mode
  )
  app.subscribe(pbsg, ModePbsgHandler)
  paragraph Heading1('Mode Pbsg Page')
  href(
    name: pbsgLabel,
    width: 2,
    url: "/installedapp/configure/${pbsg.getId()}/ModePbsgPage",
    style: 'internal',
    title: "Edit <b>${AppInfo(pbsg)}</b>",
    state: null
  )
}

void ModePbsgHandler (Event e) {
  // e.name                                              'PbsgActiveButton'
  // e.descriptionText                    'Button <activeButton> is active'
  // e.value.active                                        '<activeButton>'
  // e.value.inactive                 FIFO: ['<latestInactiveButton', ... ]
  // e.value.dflt                                           <defaultButton>
  Ltrace('ModePbsgHandler()', EventDetails(e))
}

Map whaPage () {
  return dynamicPage(
    name: 'whaPage',
    title: [
      Heading2('Whole House Automation (WHA) Application<br/>'),
      Bullet2("Authorize Access to appropriate devices.<br/>"),
      Bullet2("Create the Mode PBSG App Instance.<br/>"),
      Bullet2("Identify Participating Rooms.<br/>"),
      Bullet2("Press <b>Done</b> (see below) to ensure event subscription updates !!!")
    ].join(),
    install: true,
    uninstall: true,
    nextPage: 'whaPage'
  ) {
    app.updateLabel('Whole House Automation')
    state.MODE_PBSG_APP_LABEL = 'ModePbsg'
    state.MODES = getLocation().getModes().collect{ it.name }
    getGlobalVar('defaultMode').value
    state.SPECIALTY_BUTTONS = ['ALARM', 'ALL_AUTO', 'ALL_OFF', 'AWAY',
      'FLASH', 'PANIC', 'QUIET']
    // SAMPLE STATE & SETTINGS CLEAN UP
    //   - state.remove('X')
    //   - settings.remove('Y')
    //---------------------------------------------------------------------------------
    // REMOVE NO LONGER USED SETTINGS AND STATE
    //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    settings.remove('log')
    state.remove('PBSGapp')
    //?? state.remove('MODE_SWITCH_NAMES')
    //?? state.remove('DEFAULT_MODE_SWITCH_NAME')
    //---------------------------------------------------------------------------------
    section {
      solicitLogThreshold()                            // <- provided by Utils
      _authSpecialFnMainRepeaterAccess()
      _authSeeTouchKpadAccess()
      _identifySpecialFnButtons()
      _wireSpecialFnButtons()
      _identifyKpadModeButtons()
      _wireModeButtons()
      _identifyParticipatingRooms()
      _pruneOrphanedChildApps()
      _createModePbsgAndPageLink()
      if (!settings.rooms) {
        paragraph('Management of child apps is pending selection of Room Names.')
      } else {
        PruneAppDups(
          [*settings.rooms, state.MODE_PBSG_APP_LABEL],
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
