
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
#include wesmc.libModePbsgPublic
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

Map whaPage () {
  return dynamicPage(
    name: 'whaPage',
    title: [
      heading('Whole House Automation (WHA) Application<br/>'),
      bullet("Authorize Access to appropriate devices.<br/>"),
      bullet("Create the Mode PBSG App Instance.<br/>"),
      bullet("Identify Participating Rooms.<br/>"),
      bullet(red("Press <b>Done</b> (see below) to ensure event subscription updates !!!"))
    ].join(),
    install: true,
    uninstall: true,
    nextPage: 'whaPage'
  ) {
    app.updateLabel('Whole House Automation')
    //--hold-> state.MODE_PBSG_APP_NAME = getModePbsgAppLabel()
    state.modes = getLocation().getModes().collect{it.name}
    getGlobalVar('defaultMode').value
    state.specialtyButtons = ['ALARM', 'ALL_AUTO', 'ALL_OFF', 'AWAY',
      'FLASH', 'PANIC', 'QUIET']
    // Forcibly remove unused settings and state, a missing Hubitat feature.
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
    //?? state.remove('MODE_SWITCH_NAMES')
    //?? state.remove('DEFAULT_MODE_SWITCH_NAME')
    section {
      InstAppW modePbsg
      solicitLogThreshold()                            // <- provided by Utils
      if (!state.modes || !settings.logThreshold) {
        paragraph(
          red(
            [
              'Creation of Mode PBSG is pending prerequites.',
              "<b>state.modes:</b> ${state.modes}",
              "<b>settings.logThreshold:</b> ${settings.logThreshold}"
            ].join('<br/>')
          )
        )
      } else {
        modePbsg = createModePbsg(
          state.modes,            // List<String> modes,
          'Day',                  // String defaultMode,
          settings.logThreshold,  // String logThreshold
        )
      }
      input(
        name: 'specialtyFnMainRepeater',
        title: [
          'Authorize Specialty Function Repeater Access<br/>',
          comment("Used for 'all off'... behavior.")
        ].join(),
        type: 'device.LutronKeypad',
        submitOnChange: true,
        required: false,
        multiple: false
      )
      input(
        name: 'seeTouchKeypads',
        title: [
          'Authorize SeeTouch Keypad Access<br/>',
          comment('Identify keypads with buttons that:<br/>'),
          bullet(comment('Trigger specialty whole-house functions.<br/>')),
          bullet(comment('Change the Hubitat mode.'))
        ].join(),
        type: 'device.LutronSeeTouchKeypad',
        submitOnChange: true,
        required: false,
        multiple: true
      )
      input(
        name: 'specialFnButtons',
        title: [
          'Identify Special Function Buttons<br/>',
          comment("Examples: ${state.specialtyButtons}")
        ].join(),
        type: 'device.LutronComponentSwitch',
        submitOnChange: true,
        required: false,
        multiple: true
      )
      if (settings?.specialFnButtons == null) {
        paragraph(red('No specialty activation buttons are selected.'))
      } else {
        identifyLedButtonsForListItems(     // From libUtils.groovy
          state.specialtyButtons,          //   - list
          settings.specialFnButtons,        //   - ledDevices
          'specialFnButton'                 //   - prefix
        )
        populateStateKpadButtons('specialFnButton')
        populateStateKpadButtonDniToSpecialFnButtons()
      }
      input(
        name: 'lutronModeButtons',
        title: [
          'lutronModeButtons<br/>',
          comment('Identify Keypad LEDs/Buttons that change the Hubitat mode.')
        ].join(),
        type: 'device.LutronComponentSwitch',
        submitOnChange: true,
        required: false,
        multiple: true
      )
      if (state.modes == null || settings?.lutronModeButtons == null) {
        paragraph(red('Mode activation buttons are pending pre-requisites.'))
      } else {
        identifyLedButtonsForListItems(         // From libUtils.groovy
          state.modes,              //   - list
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
        //--TBD-> detectChildAppDupsForLabel([*settings.rooms, state.MODE_PBSG_APP_NAME])
        displayInstantiatedRoomHrefs()
      }
      paragraph(
        [
          heading('Scene PBSG<br/>'),
          "${ displayState() }<br/>",
          "${ displaySettings() }"
        ].join()
      )
      paragraph([
        heading('Debug<br/>'),
        "${ displayState() }<br/>",
        "${ displaySettings() }"
      ].join())
      paragraph modePbsg.debugStateAndSettings("${modePbsg.getLabel()} (${modePbsg.getId()})")
    }
  }
}

void identifyParticipatingRooms () {
  // By convention, Hubitat room names DO NOT have whitespace.
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
  settings.rooms.each{ modeName ->
    InstAppW roomApp = app.getChildAppByLabel(modeName)
    if (!roomApp) {
      Ldebug(
        'addRoomAppsIfMissing()',
        "Adding room ${modeName}"
      )
      roomApp = addChildApp('wesmc', 'whaRoom', modeName)
    }
    href (
      name: modeName,
      width: 2,
      url: "/installedapp/configure/${roomApp?.getId()}/whaRoomPage",
      style: 'internal',
      title: "<b>${getAppInfo(roomApp)}</b> Scenes",
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

void removeAllChildApps () {
  app.getAllChildApps().each{ child ->
    Ldebug(
      'removeAllChildApps()',
      "child: >${child.getId()}< >${child.getLabel()}<"
    )
    deleteChildApp(child.getId())
  }
}

void pruneOrphanedChildApps () {
  //Initially, assume InstAppW supports instance equality tests -> values is a problem
  List<InstAppW> kids = app.getAllChildApps()
  Ldebug(
    'pruneOrphanedChildApps()',
    "processing ${kids.collect{it.getLabel()}.join(', ')}"
  )
  List<String> modeNames =
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

void displayAppInfoLink () {
  paragraph comment(
    [
      'Whole House Automation - @wesmc, ',
      '<a href="https://github.com/WesleyMConner/Hubitat-wha" ',
      'target="_blank"><br/>Click for more information</a>'
    ].join()
  )
}

void installed () {
  Ldebug('installed()', '')
  initialize()
}

void uninstalled () {
  Ldebug('uninstalled()', '')
  removeAllChildApps()
}

void updated () {
  Ltrace('updated()', '')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void allAuto () {
  settings.rooms.each{ modeName ->
    InstAppW roomApp = app.getChildAppByLabel(modeName)
    String manualOverrideSwitchDNI = "pbsg_${roomApp.getLabel()}_AUTOMATIC"
    Ldebug('allAuto()', "Turning on <b>${manualOverrideSwitchDNI}</b>")
    roomApp.getScenePbsg().turnOnSwitch(manualOverrideSwitchDNI)
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

void initialize () {
  // - The same keypad may be associated with two different, specialized handlers
  //   (e.g., mode changing buttons vs special functionalily buttons).
  Ltrace('initialize()', '')
  settings.seeTouchKeypads.each{ d ->
    DevW device = d
    Ltrace(
      'initialize()',
      "subscribing seeTouchKeypad '${getDeviceInfo(device)}' to modeChangeButtonHandler()."
    )
    subscribe(device, modeChangeButtonHandler, ['filterEvents': true])
    Ltrace(
      'initialize()',
      "subscribing seeTouchKeypad '${getDeviceInfo(device)}' to specialFnButtonHandler()"
    )
    subscribe(device, specialFnButtonHandler, ['filterEvents': true])
  }
}
