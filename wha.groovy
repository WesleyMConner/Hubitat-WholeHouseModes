
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
  page(name: 'whaPage')
}

//----
//---- CORE APPLICATION
//----   Methods that ARE NOT constrained to any specific execution context.
//----

void removeLegacySettingsAndState () {
  settings.remove('log')
  atomicState.remove('defaultMode')
  atomicState.remove('LOG_LEVEL1_ERROR')
  atomicState.remove('LOG_LEVEL2_WARN')
  atomicState.remove('LOG_LEVEL3_INFO')
  atomicState.remove('LOG_LEVEL4_DEBUG')
  atomicState.remove('LOG_LEVEL5_TRACE')
  atomicState.remove('LOG_WARN')
  atomicState.remove('logLevel1Error')
  atomicState.remove('logLevel2Warn')
  atomicState.remove('logLevel3Info')
  atomicState.remove('logLevel4Debug')
  atomicState.remove('logLevel5Trace')
  atomicState.remove('MODE_PBSG_APP_NAME')
  atomicState.remove('MODES')
  atomicState.remove('PBSGapp')
  atomicState.remove('roomName')
  atomicState.remove('SPECIALTY_BUTTONS')
  atomicState.remove('specialtyButtons')
  atomicState.remove('specialtyFnButtons')
}

InstAppW getModePbsg () {
  String pbsgLabel = 'MODE_PBSG'
  //--xx-> App dup detection is deferred to displayInstantiatedRoomHrefs().
  // Prune any per-label dups that may have slipped in.
  Linfo('getModePbsg()', 'Calling App Dup Detection')
  detectChildAppDupsForLabels([*settings.rooms, 'MODE_PBSG'], app)
  InstAppW modePbsg = getChildAppByLabel(pbsgLabel)
  if (modePbsg) {
    // PERFORMANCE HIT - Temporarily refresh PBSG configuration.
    modePbsg.configureModePbsg()
  }
  return modePbsg
}

void addFakeChildAppsForTesting () {
  InstAppW pbsg
  pbsg = addChildApp(
    'wesmc',      // See modePbsg.groovy definition's (App) namespace.
    'modePbsg',   // See modePbsg.groovy definition's (App) name.
    'MODE_PBSG'   // Label used to create or get the child App.
  )
  Linfo('addFakeChildAppsForTesting()', "Added ${getAppInfo(pbsg)}")
  pbsg = addChildApp(
    'wesmc',      // See modePbsg.groovy definition's (App) namespace.
    'modePbsg',   // See modePbsg.groovy definition's (App) name.
    'MODE_PBSG'   // Label used to create or get the child App.
  )
  Linfo('addFakeChildAppsForTesting()', "Added ${getAppInfo(pbsg)}")
  pbsg = addChildApp(
    'wesmc',      // See modePbsg.groovy definition's (App) namespace.
    'modePbsg',   // See modePbsg.groovy definition's (App) name.
    'BADLY_NAMED_PBSG'   // Label used to create or get the child App.
  )
  Linfo('addFakeChildAppsForTesting()', "Added ${getAppInfo(pbsg)}")
}

InstAppW getOrCreateModePbsg () {
  Ltrace('getOrCreateModePbsg()', getAppInfo(app))
  //--TESTING-ONLY-> addFakeChildAppsForTesting()
  InstAppW modePbsg = getModePbsg()
  if (!modePbsg) {
    //--xx-> App dup detection is deferred to displayInstantiatedRoomHrefs().
    // Prune any per-label dups that may have slipped in.
    Linfo('getOrCreateModePbsg()', "SUPPRESSED addChildApp 'MODE_PBSG'")
    //-> modePbsg = addChildApp(
    //->   'wesmc',      // See modePbsg.groovy definition's (App) namespace.
    //->   'modePbsg',   // See modePbsg.groovy definition's (App) name.
    //->   'MODE_PBSG'   // Label used to create or get the child App.
    //-> )
    //-> modePbsg.configureModePbsg()
  }
  return modePbsg
}

String getLogLevel() {
  if (!atomicState.logLevel) Lerror('getLogLevel()', "Missing 'atomicState.logLevel'")
  return atomicState.logLevel
}

void whaInitialize () {
  Linfo(
    'whaInitialize()',
    "Updating ${app.getLabel()} state, devices and subscriptions"
  )
  // Limit creation or update of the Mode PBSG instance to this (parent) method.
  Ltrace('whaInitialize()', 'stopping event subscriptions')
  app.unsubscribe()  // Suspend event processing to rebuild state variables.
  Ltrace(
    'whaInitialize()',
    [
      getAppInfo(app),
      bullet2('subscribing seeTouchKeypads to modeChangeButtonHandler()'),
      bullet2('subscribing seeTouchKeypads to specialFnButtonHandler()')
    ].join('<br/>&nbsp;&nbsp;')
  )
  settings.seeTouchKeypads.each{ d ->
    DevW device = d
    app.subscribe(device, modeChangeButtonHandler, ['filterEvents': true])
    app.subscribe(device, specialFnButtonHandler, ['filterEvents': true])
  }
  Ltrace('whaInitialize()', 'Calling getOrCreateModePbsg()')
  InstAppW modePbsg = getOrCreateModePbsg()
}

void allAuto () {
  settings.rooms.each{ roomName ->
    InstAppW roomApp = app.getChildAppByLabel(roomName)
    String manualOverrideVswDni = "pbsg_${roomApp.getLabel()}_AUTOMATIC"
    Ldebug('allAuto()', "Turning on <b>${manualOverrideVswDni}</b>")
    roomApp.getRoomScenePbsg().turnOnSwitch(manualOverrideVswDni)
  }
}

//----
//---- SYSTEM CALLBACKS
//----   Methods specific to this execution context
//----

//----
//---- EVENT HANDLERS
//----   Methods specific to this execution context
//----

void specialFnButtonHandler (Event e) {
  switch (e.name) {
    case 'pushed':
      String specialtyFunction = atomicState.specialFnButtonMap?.getAt(e.deviceId.toString())
                                                         ?.getAt(e.value)
      if (specialtyFunction == null) return
      switch(specialtyFunction) {
        case 'ALL_AUTO':
          Ldebug('specialFnButtonHandler()', 'executing ALL_AUTO')
          allAuto()
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
      String targetVswName = atomicState.modeButtonMap?.getAt(e.deviceId.toString())
                                                ?.getAt(e.value)
      //-> Ltrace(
      //->   'modeChangeButtonHandler()',
      //->   [
      //->     "<b>atomicState.modeButtonMap:</b> ${atomicState.modeButtonMap}",
      //->     "<b>e.deviceId:</b> ${e.deviceId}",
      //->     "<b>atomicState.modeButtonMap?.getAt(e.deviceId.toString()):</b> ${atomicState.modeButtonMap?.getAt(e.deviceId.toString())}",
      //->     "<b>e.value:</b> ${e.value}"
      //->     "<b>targetVswName:</b> ${targetVswName}",
      //->   ].join('<br/>')
      //-> )
      if (targetVswName) {
        Ldebug(
          'modeChangeButtonHandler()',
          "turning ${targetVswName} on (exclusively)"
        )
        InstAppW modePbsg = getModePbsg()
        modePbsg.turnOnVswExclusivelyByName(targetVswName)
      }
      if (targetVswName == 'Day') {
        Ldebug(
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
      Ldebug(
        'modeChangeButtonHandler()',
        "ignoring ${e.name} ${e.deviceId}-${e.value}"
      )
  }
}

//----
//---- SCHEDULED ROUTINES
//----   Methods specific to this execution context
//----

void installed () {
  Ldebug('installed()', 'Calling whaInitialize()')
  whaInitialize()
}

void updated () {
  Ldebug('updated()', 'Calling whaInitialize()')
  whaInitialize()
}

void uninstalled () {
  Ltrace('uninstalled()', 'Calling removeAllChildApps()')
  removeAllChildApps()
}

//----
//---- HTTP ENDPOINTS
//----   Methods specific to this execution context
//----

//----
//---- RENDERING AND DISPLAY
//----   Methods specific to this execution context
//----

Map whaPage () {
  // The whaPage() SHOULD NOT create or interact with the Mode PBSG instance.
  // The whaPage() DOES PRESENT Mode PBSG state if the Mode PBSG exists.
    removeLegacySettingsAndState()
    // This App instance is NEVER retrieved by its label, so an update is okay.
    app.updateLabel('Whole House Automation (WHA)')
    if (!atomicState.logLevel) atomicState.logLevel = lookupLogLevel('DEBUG')
    return dynamicPage(
    name: 'whaPage',
    title: [
      heading1(getAppInfo(app)),
      bullet1('Press <b>Done</b> to call <b>install()</b> for initial data registration.'),
      bullet1('Press <b>Done</b> to call <b>update()</b> for adjusted data registration.')
    ].join('<br/>'),
    install: true,
    uninstall: false,
  ) {
    section {
      solicitLogThreshold()
      //-> checkForUpdatedLogLevel()
      authorizeMainRepeater()
      authorizeSeeTouchKeypads()
      identifySpecialFunctionButtons()
      wireButtonsToSpecialFunctions()
      identifyModeButtons()
      wireButtonsToModes()
      solicitParticipatingRooms()
//--temporary-hold->      displayInstantiatedRoomHrefs()
      displayWhaDebugData()
      displayModePbsgDebugData()
    }
  }
}

void authorizeMainRepeater () {
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

void authorizeSeeTouchKeypads () {
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

void identifySpecialFunctionButtons() {
  input(
    name: 'specialFnButtons',
    title: [
      heading2('Identify Special Function Buttons'),
      bullet1("Examples: ${atomicState.specialFnButtons}")
    ].join('<br/>'),
    type: 'device.LutronComponentSwitch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void wireButtonsToSpecialFunctions () {
  atomicState.specialFnButtons = [
    'ALARM', 'ALL_AUTO', 'ALL_OFF', 'AWAY', 'FLASH', 'PANIC', 'QUIET'
  ]
  if (settings?.specialFnButtons == null) {
    paragraph('No specialty activation buttons are selected.')
  } else {
    identifyLedButtonsForListItems(               // Wire
      atomicState.specialFnButtons,                    //   - to Special Functions
      settings.specialFnButtons,                 //   - from Keypad Button
      'specialFnButton'                          //   - prefix
    )
    populateStateKpadButtons('specialFnButton')  // specialFnButton_*
    Map<String, String> result = [:]              // kpadButtonDniToSpecialtyFn
    atomicState.specialFnButtonMap.collect{ kpadDni, buttonMap ->
      buttonMap.each{ buttonNumber, specialtyFn ->
        result["${kpadDni}-${buttonNumber}"] = specialtyFn
      }
      atomicState.kpadButtonDniToSpecialtyFn = result
    }
  }
}

void identifyModeButtons () {
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

void wireButtonsToModes () {
  if (atomicState.modes == null || settings?.lutronModeButtons == null) {
    paragraph('Mode activation buttons are pending pre-requisites.')
  } else {
    identifyLedButtonsForListItems(            // Wire
      atomicState.modes,                             //   - to Hubitat Mode
      settings.lutronModeButtons,              //   - from Keypad Button
      'modeButton'                             //   - prefix
    )
    populateStateKpadButtons('modeButton')     // modeButton_*
    Map<String, String> result = [:]           // kpadButtonDniToTargetMode
    atomicState.modeButtonMap.collect{ kpadDni, buttonMap ->
      buttonMap.each{ buttonNumber, targetMode ->
        result["${kpadDni}-${buttonNumber}"] = targetMode
      }
    }
    atomicState.kpadButtonDniToTargetMode = result
  }
}

void solicitParticipatingRooms () {
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

void displayInstantiatedRoomHrefs () {
  if (!settings.rooms) {
    paragraph 'Management of child apps is pending selection of Room Names.'
  } else {
    paragraph '<h2><b>Room Scene Configuration</b></h2>'
    settings.rooms.each{ roomName ->
      InstAppW roomApp = app.getChildAppByLabel(roomName)
      if (!roomApp) {
        Linfo('displayInstantiatedRoomHrefs()', "SUPPRESSED addChildApp '${roomName}'")
        //-> Linfo('addRoomAppsIfMissing()', "Adding room <b>${roomName}</b>")
        //-> roomApp = addChildApp('wesmc', 'roomScene', roomName)
      }
      href (
        name: roomName,
        width: 2,
        url: "/installedapp/configure/${roomApp?.getId()}/roomScenePage",
        style: 'internal',
        title: "<b>${getAppInfo(roomApp)}</b> Scenes",
        state: null, //'complete'
      )
    }
  }
}

void displayWhaDebugData() {
  paragraph (
    [
      '<h2><b>whaPage Debug</b></h2>',
      '<h3><b>STATE</b></h3>',
      getStateBulletsAsIs(),
      '<h3><b>SETTINGS</b></h3>',
      getSettingsBulletsAsIs()
    ].join()
  )
}

void displayModePbsgDebugData () {
  // Use the Mode PBSG if it exists, but DO NOT create it if missing.
  InstAppW modePbsg = getModePbsg()
  if (modePbsg) {
    paragraph (
      [
        "<h2><b>${getAppInfo(modePbsg)} Debug</b></h2>",
        '<h3><b>STATE</b></h3>',
        modePbsg.getPbsgStateBullets(),
      ].join()
    )
  } else {
    paragraph '<h2><b>No Mode PBSG (yet)</b></h2>'
  }
}
