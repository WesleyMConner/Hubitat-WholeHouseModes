
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
#include wesmc.libLogAndDisplay
#include wesmc.libPbsgBase
#include wesmc.libUtils

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
  page(name: '_whaPage')
}

InstAppW createModePbsg () {
  return addChildApp(
    'wesmc',       // See modePBSG.groovy definition's (App) namespace.
    'modePBSG',    // See modePBSG.groovy definition's (App) name.
    'whaModePbsg'  // Label used to create or get the child App.
  )
}

InstAppW getModePbsg () {
  return getChildAppByLabel('whaModePbsg')
}


void _removeLegacySettingsAndState () {
  settings.remove('log')
  state.remove('LOG_LEVEL1_ERROR')
  state.remove('LOG_LEVEL2_WARN')
  state.remove('LOG_LEVEL3_INFO')
  state.remove('LOG_LEVEL4_DEBUG')
  state.remove('LOG_LEVEL5_TRACE')
  state.remove('LOG_WARN')
  state.remove('MODE_PBSG_APP_NAME')
  state.remove('MODES')
  state.remove('PBSGapp')
  state.remove('SPECIALTY_BUTTONS')
  state.remove('specialtyButtons')
  state.remove('specialtyFnButtons')
}

void _authorizeMainRepeater () {
  input(
    name: 'specialtyFnMainRepeater',
    title: [
      heading2('Authorize Specialty Function Repeater Access'),
      bullet1('Identify repeaters supporting special function implementation.')
    ].join('<br/>'),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: false
  )
}

void _authorizeSeeTouchKeypads () {
  input(
    name: 'seeTouchKeypads',
    title: [
      heading2('Authorize SeeTouch Keypad Access'),
      bullet1('Identify Specialty Function keypad buttons.'),
      bullet1('Identify keypad buttons used to change the Hubitat Mode.')
    ].join('<br/>'),
    type: 'device.LutronSeeTouchKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _identifySpecialFunctionButtons() {
  input(
    name: 'specialFnButtons',
    title: [
      heading2('Identify Special Function Buttons'),
      bullet1("Examples: ${state.specialFnButtons}")
    ].join('<br/>'),
    type: 'device.LutronComponentSwitch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _wireButtonsToSpecialFunctions () {
  state.specialFnButtons = [
    'ALARM', 'ALL_AUTO', 'ALL_OFF', 'AWAY', 'FLASH', 'PANIC', 'QUIET'
  ]
  if (settings?.specialFnButtons == null) {
    paragraph('No specialty activation buttons are selected.')
  } else {
    identifyLedButtonsForListItems(              // Wire
      state.specialFnButtons,                    //   - to Special Functions
      settings.specialFnButtons,                 //   - from Keypad Button
      'specialFnButton'                          //   - prefix
    )
    _populateStateKpadButtons('specialFnButton')  // specialFnButton_*
    Map<String, String> result = [:]             // kpadButtonDniToSpecialtyFn
    state.specialFnButtonMap.collect{ kpadDni, buttonMap ->
      buttonMap.each{ buttonNumber, specialtyFn ->
        result["${kpadDni}-${buttonNumber}"] = specialtyFn
      }
      state.kpadButtonDniToSpecialtyFn = result
    }
  }
}

void _identifyModeButtons () {
  input(
    name: 'lutronModeButtons',
    title: [
      heading2('Identify Hubitat Mode Buttons'),
      bullet1('Identify Keypad LEDs/Buttons that change the Hubitat mode.')
    ].join('<br/>'),
    type: 'device.LutronComponentSwitch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _wireButtonsToModes () {
  if (state.modes == null || settings?.lutronModeButtons == null) {
    paragraph('Mode activation buttons are pending pre-requisites.')
  } else {
    identifyLedButtonsForListItems(            // Wire
      state.modes,                             //   - to Hubitat Mode
      settings.lutronModeButtons,              //   - from Keypad Button
      'modeButton'                             //   - prefix
    )
    _populateStateKpadButtons('modeButton')     // modeButton_*
    Map<String, String> result = [:]           // kpadButtonDniToTargetMode
    state.modeButtonMap.collect{ kpadDni, buttonMap ->
      buttonMap.each{ buttonNumber, targetMode ->
        result["${kpadDni}-${buttonNumber}"] = targetMode
      }
    }
    state.kpadButtonDniToTargetMode = result
  }
}

void _solicitParticipatingRooms () {
  // By convention, Hubitat room names DO NOT have whitespace.
  roomPicklist = app.getRooms().collect{it.name}.sort()
  input(
    name: 'rooms',
    type: 'enum',
    title: '<h2><b>Select Participating Rooms</b></h2>',
    //title: heading2('Select Participating Rooms'),
    options: roomPicklist,
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _displayInstantiatedRoomHrefs () {
  if (!settings.rooms) {
    paragraph 'Management of child apps is pending selection of Room Names.'
  } else {
    paragraph '<h2><b>Room Scene Configuration</b></h2>'
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
        url: "/installedapp/configure/${roomApp?.getId()}/_whaRoomPage",
        style: 'internal',
        title: "<b>${getAppInfo(roomApp)}</b> Scenes",
        state: null, //'complete'
      )
    }
  }
}

Map _whaPage () {
  _removeLegacySettingsAndState()
  InstAppW modePbsg = getModePbsg() ?: createModePbsg()
  return dynamicPage(
    name: '_whaPage',
    title: [
      heading1("Whole House Automation - ${getAppInfo(app)}"),
      bullet1('Press <b>Done</b> to call <b>install()</b> for initial data registration.'),
      bullet1('Press <b>Done</b> to call <b>update()</b> for adjusted data registration.')
    ].join('<br/>'),
    install: true,
    uninstall: false,
  ) {
    app.updateLabel('Whole House Automation (WHA)')
    section {
      solicitLogThreshold()                                           // Utils.groovy
      //-> DON'T MAKE ADJUSTMENTS HERE, INSTEAD WAIT FOR updated().
      //-> if (settings.logThreshold) {
      //->   modePbsg._setLogLevels(settings.logThreshold)
      //-> }
      _authorizeMainRepeater()
      _authorizeSeeTouchKeypads()
      _identifySpecialFunctionButtons()
      _wireButtonsToSpecialFunctions()
      _identifyModeButtons()
      _wireButtonsToModes()
      _solicitParticipatingRooms()
      _displayInstantiatedRoomHrefs()
      //--xx-> modePbsg._displayStateAndSettings() ?: heading1('modePbsg IS NOT present')
      //-> NEED TO DISPLAY CHILD PBSG DETAILS HERE
      paragraph (
        [
          '<h2><b>whaPage Debug</b></h2>',
          '<h3><b>STATE</b></h3>',
          _getStateBulletsAsIs(),
          '<h3><b>SETTINGS</b></h3>',
          _getSettingsBulletsAsIs()
        ].join()
      )
      if (modePbsg) {
        paragraph (
          [
            "<h2><b>${getAppInfo(modePbsg)} Debug</b></h2>",
            '<h3><b>STATE</b></h3>',
            modePbsg._getPbsgStateBullets(),
            '<h3><b>SETTINGS</b></h3>',
            modePbsg._getSettingsBulletsAsIs()
          ].join()
        )
      }
    }
  }
}

// No signature of method: java.lang.String.call() is applicable for
// argument types: (java.lang.String) values: [updateModePbsg]
// ossible solutions: wait(), chars(), any(), wait(long),
// split(java.lang.String), each(groovy.lang.Closure) on line 279 (method appButtonHandler)

//-> void updateLutronKpadLeds (String currMode) {
//->   settings.lutronModeButtons.each{ ledObj ->
//->     String modeTarget = state.kpadButtonDniToTargetMode[ledObj.getDeviceNetworkId()]
//->     if (currMode == modeTarget) {
//->       ledObj.on()
//->     } else {
//->       ledObj.off()
//->     }
//->   }
//-> }

void pruneOrphanedChildApps () {
  List<InstAppW> kids = app.getAllChildApps()
  Ldebug(
    'pruneOrphanedChildApps()',
    "processing ${kids.collect{it.getLabel()}.join(', ')}"
  )
  //--xx-> List<String> modeNames =
  kids.each{ kid ->
    if (settings.rooms?.contains(kid)) {
      Ldebug(
        'pruneOrphanedChildApps()',
        "skipping ${kid.getLabel()} (room)"
      )
    } else {
      Ldebug(
        'pruneOrphanedChildApps()',
        "deleting ${kid.getLabel()} (orphan)"
      )
      deleteChildApp(kid.getId())
    }
  }
}

//-> void displayAppInfoLink () {
//->   paragraph comment(
//->     [
//->       'Whole House Automation - @wesmc, ',
//->       '<a href="https://github.com/WesleyMConner/Hubitat-wha" ',
//->       'target="_blank"><br/>Click for more information</a>'
//->     ].join()
//->   )
//-> }

void installed () {
  Ldebug('installed()', 'At entry')
  _whaInitialize()
}

void updated () {
  Ltrace('updated()', 'At entry')
  app.unsubscribe()  // Suspend event processing to rebuild state variables.
  _whaInitialize()
}

void uninstalled () {
  Ltrace('uninstalled()', 'At entry')
  removeAllChildApps()
}

void _allAuto () {
  // ??? RELOCATE ???
  settings.rooms.each{ roomName ->
    InstAppW roomApp = app.getChildAppByLabel(roomName)
    String manualOverrideSwitchDni = "pbsg_${roomApp.getLabel()}_AUTOMATIC"
    Ldebug('_allAuto()', "Turning on <b>${manualOverrideSwitchDni}</b>")
    roomApp.getScenePbsg().turnOnSwitch(manualOverrideSwitchDni)
  }
}

void specialFnButtonHandler (Event e) {
  switch (e.name) {
    case 'pushed':
      String specialtyFunction = state.specialFnButtonMap?.getAt(e.deviceId.toString())
                                                         ?.getAt(e.value)
      if (specialtyFunction == null) return
      switch(specialtyFunction) {
        case 'ALL_AUTO':
          Ldebug('specialFnButtonHandler()', 'executing ALL_AUTO')
          _allAuto()
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
        app.getChildAppByLabel(state.MODE_PBSG_APP_NAME).turnOnSwitch(targetVsw)
      }
      if (targetVsw == 'Day') {
        Ldebug(
          'modeChangeButtonHandler()',
          "executing ALL_AUTO"
        )
        _allAuto()
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

void _whaInitialize () {
  // - The same keypad may be associated with two different, specialized handlers
  //   (e.g., mode changing buttons vs special functionalily buttons).
  Ltrace('_whaInitialize()', 'At entry')
  settings.seeTouchKeypads.each{ d ->
    DevW device = d
    Ltrace(
      '_whaInitialize()',
      "subscribing seeTouchKeypad '${getDeviceInfo(device)}' to modeChangeButtonHandler()."
    )
    app.subscribe(device, modeChangeButtonHandler, ['filterEvents': true])
    Ltrace(
      '_whaInitialize()',
      "subscribing seeTouchKeypad '${getDeviceInfo(device)}' to specialFnButtonHandler()"
    )
    app.subscribe(device, specialFnButtonHandler, ['filterEvents': true])
  }
  Ltrace('_whaInitialize()', 'Invoking modePbsg._modePbsgInit()')
  modePbsg._modePbsgInit()
}
