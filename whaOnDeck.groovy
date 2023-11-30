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
#include wesmc.libHubUI
#include wesmc.libHubExt
//#include wesmc.libFifoQ
#include wesmc.libLutron

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

//----
//---- CORE METHODS
//----   Methods that ARE NOT constrained to any specific execution context.
//----

InstAppW getModePbsg (Boolean refreshConfig = false) {
  InstAppW pbsg = null
  if (state.whaPbsgLabel) {
    Ltrace('getModePbsg()', "getChildAppByLabel(${b(state.whaPbsgLabel)})")
    pbsg = app.getChildAppByLabel(state.whaPbsgLabel)
    if (pbsg) {
      // Optionally refresh configuration (at a performance cost)
      Ltrace('getModePbsg()', "Found ${AppInfo(pbsg)}")
      if (refreshConfig) {
        Ltrace('getModePbsg()', "Refreshing ${AppInfo(pbsg)} config")
        pbsg.pbsgConfigure(
          ModeNames(),
          getGlobalVar('defaultMode')?.value,
          settings.pbsgLogThreshold ?: 'TRACE'
        )
      }
    }
  } else {
    Lerror('getModePbsg()', "Called before ${b(state.whaPbsgLabel)} exists")
  }
  return pbsg
}

InstAppW getOrCreateModePbsg (Boolean refreshConfig = false) {
  Ltrace('getOrCreateModePbsg()', 'At entry')
  InstAppW pbsg = null
  if (state.whaPbsgLabel) {
    pbsg = getModePbsg(refreshConfig)
    if (!pbsg) {
      Lwarn('getOrCreateModePbsg()', "Creating new PBSG ${b(state.whaPbsgLabel)}")
      pbsg = app.addChildApp(
        'wesmc',             // See 'namespace' definition in pbsg.groovy
        'pbsgWha',           // See 'name' definition in pbsg.groovy
        state.whaPbsgLabel   // The label for the single Mode PBSG instance
      )
      Lwarn('getOrCreateModePbsg()', "Created PBSG ${AppInfo(pbsg)}")
      // Always configure new instances
      pbsg.pbsgConfigure(
        ModeNames(),
        getGlobalVar('defaultMode')?.value,
        settings.pbsgLogThreshold ?: 'TRACE'
      )
    }
  } else {
    Lerror('getOrCreateModePbsg()', 'Called before state.whaPbsgLabel exists')
  }
  return pbsg
}


void removeLegacyWhaData () {
  // settings?.remove('log')
  // state.remove('defaultMode')
  // state.remove('LOG_LEVEL1_ERROR')
  // state.remove('LOG_LEVEL2_WARN')
  // state.remove('LOG_LEVEL3_INFO')
  // state.remove('LOG_LEVEL4_DEBUG')
  // state.remove('LOG_LEVEL5_TRACE')
  // state.remove('LOG_WARN')
  // state.remove('logLevel1Error')
  // state.remove('logLevel2Warn')
  // state.remove('logLevel3Info')
  // state.remove('logLevel4Debug')
  // state.remove('logLevel5Trace')
  // state.remove('MODE_PBSG_APP_LABEL')
  // state.remove('MODES')
  // state.remove('PBSGapp')
  // state.remove('roomName')
  // state.remove('SPECIALTY_BUTTONS')
  // state.remove('specialtyButtons')
  // state.remove('specialtyFnButtons')
}

void manageNestedChildApps () {
  // Prune any orphaned or duplicated apps in the WHA App hierarchy.
  // (1) Begin with direct children of WHA (the MODE_PBSG and Room Scene instances).
  Ltrace('manageNestedChildApps()', "Calling PruneAppDups() at ${b(root)} level")
  PruneAppDups([state.whaPbsgLabel, *settings.rooms], false, app)
  // (2) Drill into each RoomScene and manage its PBSG Instance
  settings.rooms?.each{ roomName ->
    Ltrace('manageNestedChildApps()', "Calling PruneAppDups() for ${b(roomName)}")
    InstAppW roomApp = getChildAppByLabel(roomName)
    PruneAppDups(["pbsg_${roomName}"], false, roomApp)
  }
}

String getLogLevel() {
  if (!state.logLevel) Lerror('getLogLevel()', "Missing 'state.logLevel'")
  return state.logLevel
}

void whaInitialize () {
  Ltrace(
    'whaInitialize()',
    "Updating ${app.getLabel()} state, devices and subscriptions"
  )
  // Limit creation or update of the Mode PBSG instance to this (parent) method.
  Ltrace('whaInitialize()', 'stopping event subscriptions')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  Ltrace(
    'whaInitialize()',
    [
      AppInfo(app),
      Bullet2('subscribing seeTouchKeypads to modeChangeButtonHandler()'),
      Bullet2('subscribing seeTouchKeypads to specialFnButtonHandler()')
    ].join('<br/>&nbsp;&nbsp;')
  )
  settings.seeTouchKeypads.each{ d ->
    DevW device = d
    subscribe(device, modeChangeButtonHandler, ['filterEvents': true])
    subscribe(device, specialFnButtonHandler, ['filterEvents': true])
  }
}

void allAuto () {
  settings.rooms.each{ roomName ->
    InstAppW roomApp = getChildAppByLabel(roomName)
    Ldebug('allAuto()', 'Turning on <b>MANUAL_OVERRIDE</b>')
    roomApp.getRoomScenePbsg().pbsgTurnOn('MANUAL_OVERRIDE')
  }
}

//----
//---- SYSTEM CALLBACKS
//----   Methods specific to this execution context
//----

void installed () {
  // ON FIRST LOAD OF whaPage() THERE IS NO PBSG
  Ldebug('installed()', "pbsg is ${AppInfo(pbsg)}")
  Ltrace('installed()', 'calling whaInitialize()')
  whaInitialize()
}

void updated () {
  Ldebug('updated()', "pbsg is ${AppInfo(pbsg)}")
  if (settings.appLogThreshold) {
    state.logLevel = LogThresholdToLogLevel(settings.appLogThreshold)
  }
  if (pbsg && settings.pbsgLogThreshold) {
    pbsg.pbsgAdjustLogLevel(LogThresholdToLogLevel(settings.pbsgLogThreshold))
  }
  Ltrace('updated()', 'calling whaInitialize()')
  whaInitialize()

  solicitLogThreshold('appLogThreshold')
  solicitLogThreshold('pbsgLogThreshold')
    if (pbsg) state.logLevel = LogThresholdToLogLevel(settings.pbsgLogThreshold ?: 'DEBUG')

  if (pbsg) state.logLevel = LogThresholdToLogLevel(settings.pbsgLogThreshold ?: 'DEBUG')



}

void uninstalled () {
  Lwarn('uninstalled()', 'calling RemoveChildApps()')
  RemoveChildApps()
}

//----
//---- EVENT HANDLERS
//----   Methods specific to this execution context
//----

void specialFnButtonHandler (Event e) {
  Ldebug('specialFnButtonHandler()', "pbsg is ${AppInfo(pbsg)}")
  switch (e.name) {
    case 'pushed':
      String specialtyFunction = state.specialFnButtonMap?.getAt(e.deviceId.toString())
                                                         ?.getAt(e.value)
      if (specialtyFunction == null) return
      switch(specialtyFunction) {
        case 'ALL_AUTO':
          Ltrace('specialFnButtonHandler()', 'executing ALL_AUTO')
          allAuto()
          //--TBD--> Update of Keypad LEDs
          break;
        case 'ALARM':
        case 'AWAY':
        case 'FLASH':
        case 'PANIC':
        case 'QUIET':
          Ltrace(
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
      Lwarn(
        'specialFnButtonHandler()',
        "ignoring ${e.name} ${e.deviceId}-${e.value}"
      )
  }
}

void modeChangeButtonHandler (Event e) {
  Ldebug('modeChangeButtonHandler()', "pbsg is ${AppInfo(pbsg)}")
  // Design Note
  //   - The field e.deviceId arrives as a number and must be cast toString().
  //   - Hubitat runs Groovy 2.4. Groovy 3 constructs - x?[]?[] - are not available.
  //   - Keypad buttons are matched to state data to activate a target VSW.
  Ldebug('modeChangeButtonHandler()', 'TBD - VSW LEAKAGE ???')
  switch (e.name) {
    case 'pushed':
      String targetVswName = state.modeButtonMap?.getAt(e.deviceId.toString())
                                                ?.getAt(e.value)
      //-> Ltrace(
      //->   'modeChangeButtonHandler()',
      //->   [
      //->     "<b>state.modeButtonMap:</b> ${state.modeButtonMap}",
      //->     "<b>e.deviceId:</b> ${e.deviceId}",
      //->     "<b>state.modeButtonMap?.getAt(e.deviceId.toString()):</b> ${state.modeButtonMap?.getAt(e.deviceId.toString())}",
      //->     "<b>e.value:</b> ${e.value}"
      //->     "<b>targetVswName:</b> ${targetVswName}",
      //->   ].join('<br/>')
      //-> )
      if (targetVswName) {
        Ltrace(
          'modeChangeButtonHandler()',
          "turning ${targetVswName} on (exclusively)"
        )
        InstAppW modePbsg = getOrCreateModePbsg(true)
        modePbsg.pbsgTurnOn(targetVswName)
      }
      if (targetVswName == 'Day') {
        Ltrace(
          'modeChangeButtonHandler()',
          "executing ALL_AUTO"
        )
        allAuto()
      }
      // Silently ignore buttons that DO NOT impact Hubitat mode.
      break;
    case 'held':
    case 'released':
    default:
      Lwarn(
        'modeChangeButtonHandler()',
        "ignoring ${e.name} ${e.deviceId}-${e.value}"
      )
  }
}

//----
//---- SCHEDULED ROUTINES
//----   Methods specific to this execution context
//----

//----
//---- HTTP ENDPOINTS
//----   Methods specific to this execution context
//----

//----
//---- RENDERING AND DISPLAY
//----   Methods specific to this execution context
//----

void updateAppAndPbsgLogLevels () {
  state.logLevel = LogThresholdToLogLevel(settings.appLogThreshold ?: 'TRACE')
  modePbsg.pbsgAdjustLogLevel(
    LogThresholdToLogLevel(settings.pbsgLogThreshold ?: 'TRACE')
  )
}

Map<String, List<String>> whaPage () {
  List<String> e = ['A', 'B', 'C', 'D', 'F', 'G', 'H', 'I', 'J']
  List<String> r = ['C', 'D', 'E', 'F', 'H', 'I', 'J', 'K', 'L', 'M']
  Map<String, List<String>> x = CompareLists(e, r)
  Ltrace('COMPARISON', [
    '',
    Heading2('COMPARISON)
    "existing: ${e}",
    "replacement: ${r}",
    "retained: ${x.retained}",
    "dropped: ${x.dropped}",
    "added: ${x.added}"
  ])


  app.updateLabel('Whole House Automation (WHA)')
  removeLegacyWhaData()
  state.whaPbsgLabel = 'MODE_PBSG'
  InstAppW pbsg = getOrCreateModePbsg(true)
  Ldebug('whaPage()', "pbsg is ${AppInfo(pbsg)}")
  // Ensure a log level is available before App
  state.logLevel = LogThresholdToLogLevel(settings.appLogThreshold ?: 'DEBUG')
    return dynamicPage(
    name: 'whaPage',
    title: [
      Heading1(AppInfo(app)),
      Bullet1('Press <b>Done</b> to call <b>install()</b> for initial data registration.'),
      Bullet1('Press <b>Done</b> to call <b>update()</b> for adjusted data registration.')
    ].join('<br/>'),
    install: true,
    uninstall: false,
  ) {
    section {
      solicitLogThreshold('appLogThreshold')
      solicitLogThreshold('pbsgLogThreshold')
      //-> checkForUpdatedLogLevel()
      authorizeMainRepeater()
      authorizeSeeTouchKeypads()
      identifySpecialFunctionButtons()
      wireButtonsToSpecialFunctions()
      identifyModeButtons()
      wireButtonsToModes()
      solicitParticipatingRooms()
      //-----> _displayInstantiatedRoomHrefs()
      displayWhaDebugData()
      displayModePbsgDebugData()
    }
  }
}

void authorizeMainRepeater () {
  input(
    name: 'specialtyFnMainRepeater',
    title: [
      Heading2('Authorize Specialty Function Repeater Access'),
      Bullet1('Identify repeaters supporting special function implementation.')
    ].join('<br/>'),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: false
  )
}

void authorizeSeeTouchKeypads () {
  input(
    name: 'seeTouchKeypads',
    title: [
      Heading2('Authorize SeeTouch Keypad Access'),
      Bullet1('Identify Specialty Function keypad buttons.'),
      Bullet1('Identify keypad buttons used to change the Hubitat Mode.')
    ].join('<br/>'),
    type: 'device.LutronSeeTouchKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void identifySpecialFunctionButtons() {
  input(
    name: 'specialFnButtons',
    title: [
      Heading2('Identify Special Function Buttons'),
      Bullet1("Examples: ${state.specialFnButtons}")
    ].join('<br/>'),
    type: 'device.LutronComponentSwitch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void wireButtonsToSpecialFunctions () {
  Ldebug('wireButtonsToSpecialFunctions()', 'TBD - DNI LEAKAGE ???')
  state.specialFnButtons = [
    'ALARM', 'ALL_AUTO', 'ALL_OFF', 'AWAY', 'FLASH', 'PANIC', 'QUIET'
  ]
  if (settings?.specialFnButtons == null) {
    paragraph('No specialty activation buttons are selected.')
  } else {
    identifyLedButtonsForListItems(               // Wire
      state.specialFnButtons,                    //   - to Special Functions
      settings.specialFnButtons,                 //   - from Keypad Button
      'specialFnButton'                          //   - prefix
    )
    populateStateKpadButtons('specialFnButton')  // specialFnButton_*
    Map<String, String> result = [:]              // kpadButtonDniToSpecialtyFn
    state.specialFnButtonMap.collect{ kpadDni, buttonMap ->
      buttonMap.each{ buttonNumber, specialtyFn ->
        result["${kpadDni}-${buttonNumber}"] = specialtyFn
      }
      state.kpadButtonDniToSpecialtyFn = result
    }
  }
}

void identifyModeButtons () {
  input(
    name: 'lutronModeButtons',
    title: [
      Heading2('Identify Hubitat Mode Buttons'),
      Bullet1('Identify Keypad LEDs/Buttons that change the Hubitat mode.')
    ].join('<br/>'),
    type: 'device.LutronComponentSwitch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void wireButtonsToModes () {
  Ldebug('wireButtonsToModes()', 'TBD - DNI LEAKAGE ???')
  if (state.modes == null || settings?.lutronModeButtons == null) {
    paragraph('Mode activation buttons are pending pre-requisites.')
  } else {
    identifyLedButtonsForListItems(            // Wire
      state.modes,                             //   - to Hubitat Mode
      settings.lutronModeButtons,              //   - from Keypad Button
      'modeButton'                             //   - prefix
    )
    populateStateKpadButtons('modeButton')     // modeButton_*
    Map<String, String> result = [:]           // kpadButtonDniToTargetMode
    state.modeButtonMap.collect{ kpadDni, buttonMap ->
      buttonMap.each{ buttonNumber, targetMode ->
        result["${kpadDni}-${buttonNumber}"] = targetMode
      }
    }
    state.kpadButtonDniToTargetMode = result
  }
}

void solicitParticipatingRooms () {
  // By convention, Hubitat room names DO NOT have whitespace.
  roomPicklist = getRooms().collect{it.name}.sort()
  input(
    name: 'rooms',
    type: 'enum',
    title: '<h2><b>Select Participating Rooms</b></h2>',
    //title: Heading2('Select Participating Rooms'),
    options: roomPicklist,
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

/*
void _displayInstantiatedRoomHrefs () {
  if (!settings.rooms) {
    paragraph 'Management of child apps is pending selection of Room Names.'
  } else {
    paragraph '<h2><b>Room Scene Configuration</b></h2>'
    settings.rooms.each{ roomName ->
      InstAppW roomApp = getChildAppByLabel(roomName)
      String roomScenePbsgName = "pbsg_${roomName}"
      if (!roomApp) {
        Lwarn(
          '_displayInstantiatedRoomHrefs()',
          "adding child App '${roomScenePbsgName}'"
        )
        roomApp.addChildApp('wesmc', 'roomScene', roomScenePbsgName)
      }
      href (
        name: roomName,
        width: 2,
        url: "/installedapp/configure/${roomApp?.getId()}/roomScenePage",
        style: 'internal',
        title: "<b>${AppInfo(roomApp)}</b> Scenes",
        state: null, //'complete'
      )
    }
  }
}
*/

void displayWhaDebugData() {
  paragraph (
    [
      '<h2><b>whaPage Debug</b></h2>',
      '<h3><b>STATE</b></h3>',
      appStateAsBullets(),
      '<h3><b>SETTINGS</b></h3>',
      appSettingsAsBullets()
    ].join()
  )
}

void displayModePbsgDebugData () {
  //--hold-> // Use the Mode PBSG if it exists, but DO NOT create it if missing.
  //--hold-> InstAppW modePbsg = getOrCreateModePbsg()
  if (modePbsg) {
    paragraph (
      [
        "<h2><b>${AppInfo(modePbsg)} Debug</b></h2>",
        '<h3><b>STATE</b></h3>',
        modePbsg.pbsgGetStateBullets(),
      ].join()
    )
  } else {
    paragraph '<h2><b>No Mode PBSG (yet)</b></h2>'
  }
}
