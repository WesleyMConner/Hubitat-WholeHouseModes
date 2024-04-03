// ---------------------------------------------------------------------------------
// P B S G 2
//
// Copyright (C) 2023-Present Wesley M. Conner
//
// LICENSE
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
// not use this file except in compliance with the License. Unless
// required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// ---------------------------------------------------------------------------------
// For reference:
//   Unicode 2190 ← LEFTWARDS ARROW
//   Unicode 2192 → RIGHTWARDS ARROW
import com.hubitat.hub.domain.Event as Event

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include wesmc.lHExt
#include wesmc.lHUI

definition (
  name: 'TestPbsg2',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Preview Pbsg functionality',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(name: 'TestPbsgPage')
}

// The psuedo-class "config" solicits the button data that drives the
// creation of a psuedo-class "pbsg" instance.

String config_SolicitName(String nameKey) {
  input(
    name: nameKey, title: '<b>PBSG NAME</b>', width: 2,
    type: 'text', submitOnChange: true, required: true, multiple: false
  )
  return settings."${nameKey}"
}

ArrayList config_SolicitButtons(String buttonsKey) {
  input(
    name: buttonsKey, title: '<b>PBSG BUTTONS</b> (space delimited)', width: 4,
    type: 'text', submitOnChange: true, required: true, multiple: false
  )
  return settings."${buttonsKey}"?.tokenize(' ')
}

String config_SolicitDefault(String defaultButtonKey, ArrayList buttons) {
  input(
    name: defaultButtonKey, title: '<b>DEFAULT BUTTON</b>', width: 3,
    type: 'enum', submitOnChange: true, required: false, multiple: false,
    options: buttons
  )
  return settings."${defaultButtonKey}"
}

String config_SolicitInitialActive(String activeButtonKey, ArrayList buttons) {
  input(
    name: activeButtonKey, title: '<b>INITIAL ACTIVE BUTTON</b>', width: 3,
    type: 'enum', submitOnChange: true, required: false, multiple: false,
    options: buttons
  )
  return settings."${activeButtonKey}"
}

Map config_SolicitInstance(Integer settingsKeySuffix) {
  // Solicit logical button data used to create a Pbsg instance.
  Map config = [:]
  String nameKey = "pbsgName^${settingsKeySuffix}"
  String name = config_SolicitName(nameKey)
  if (name) {
    String buttonsKey = "pbsgButtons^${settingsKeySuffix}"
    String defaultKey = "pbsgDefault^${settingsKeySuffix}"
    String activeKey = "pbsgActive^${settingsKeySuffix}"
    ArrayList allButtons = config_SolicitButtons(buttonsKey)
    String defaultButton = config_SolicitDefault(defaultKey, allButtons)
    String initialActiveButton = config_SolicitInitialActive(activeKey, allButtons)
    config = [
      'name': name,
      'allButtons': allButtons,
      'defaultButton': defaultButton,
      'initialActiveButton': initialActiveButton
    ]
  } else {
     paragraph('', width: 10)  // Filler for a 12 cell row
  }
  return config
}

// The psuedo-class "pbsg" is built to a psuedo-class "config" instance,
// creating and managing per-button VSWs.

DevW pbsg_GetOrFindMissingDevice(DevW device, String deviceDNI) {
  if (device && device.deviceNetworkId != deviceDNI) {
    logError(
      'pbsg_GetOrFindMissingDevice',
      "Provided device DNI (${device.deviceNetworkId}) != deviceDNI (${deviceDNI})"
    )
  }
  DevW d = device
  if (!d) { d = getChildDevice(deviceDNI) }
  if (!d) {
    logWarn('pbsg_GetOrFindMissingDevice', "Creating child device ${deviceDNI}")
    d = addChildDevice(
      'hubitat',          // namespace
      'Virtual Switch',   // typeName
      deviceDNI,          // device's unique DNI
      [isComponent: true, name: deviceDNI]
    )
  }
  return d
}

void pbsg_ActivateButton(Map pbsg, String button, DevW device = null) {
  //-> if (button == null) { logError('pbsg_ActivateButton', 'button arg is NULL') }
  DevW d = pbsg_GetOrFindMissingDevice(device, "${pbsg.name}_${button}")
  String dState = switchState(d)
  //-> logInfo('pbsg_ActivateButton', "button: ${button} ('${dState}')")
  if (pbsg.activeButton == button) {
    // The button is already active. Ensure its VSW is 'on'.
    if (dState != 'on') {
      logWarn('pbsg_ActivateButton', "Correcting ACTIVE ${button} w/ state '${dState}'")
      unsubscribe(d)
      d.on()
      subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
    }
  } else {
    if (pbsg.activeButton != null) {
      // Move the currently active button out of the way.
      pbsg_DeactivateButton(pbsg, pbsg.activeButton)
    }
    // Relocate the newly activated button (from the LIFO) and turn it 'on'.
    Integer indexInLIFO = null
    pbsg.buttonsLIFO.eachWithIndex{ b, i ->
      if (b == button) { indexInLIFO = i }
    }
    if (indexInLIFO == null) {
      logWarn(
        'pbsg_ActivateButton',
        "Target ${button} not found in LIFO (${pbsg.buttonsLIFO})")
    } else {
      pbsg.buttonsLIFO.removeAt(indexInLIFO)
    }
    unsubscribe(d)
    pbsg.activeButton = button
    d.on()
    subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
  }
}

void pbsg_DeactivateButton(Map pbsg, String button, DevW device = null) {
  //-> logError('pbsg_DeactivateButton', ['E N T R Y',
  //->   "pbsg: ${pbsg}",
  //->   "button: ${button}",
  //->   "device: ${null}"
  //-> ])
  DevW d = pbsg_GetOrFindMissingDevice(device, "${pbsg.name}_${button}")
  String dState = switchState(d)
  //-> logInfo('pbsg_DeactivateButton', ['',
  //->   pbsg_State(pbsg),
  //->   "button: ${button}, ${pbsg.name}_${button}",
  //->   "d: ${d}",
  //->   "dState: ${dState}"
  //-> ])
  //-> logInfo('pbsg_DeactivateButton', "button: ${button} ('${dState}')")
  if (pbsg.activeButton == button) {
    unsubscribe(d)
    d.off()
    pbsg.buttonsLIFO.push(pbsg.activeButton)
    pbsg.activeButton = null
    subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
  } else if (pbsg.buttonsLIFO.contains(button)) {
    // Nothing to do
  } else {
    logWarn('pbsg_DeactivateButton', "Adding ${button} to ${pbsg.name} LIFO.")
    unsubscribe(d)
    d.off()
    pbsg.buttonsLIFO.push(button)
    subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
  }
}

//void pbsg_ActivatePrior(Map pbsg) {
//  // Swap the currently active DNI with the DNI at the front of the LIFO.
//  DevW temp = null
//  if (pbsg.activeButton) { temp = pbsg.activeButton }
//  pbsg.activeButton = pbsg.buttonsLIFO.pop()
//  if (temp) { pbsg.buttonsLIFO.push(temp) }
//}

void pbsg_EnforceDefault(Map pbsg) {
  if (!pbsg.activeButton && pbsg.defaultButton) {
    logInfo('pbsg_EnforceDefault', "${pbsg.defaultButton} → active")
    pbsg_ActivateButton(pbsg, pbsg.defaultButton)
  }
}

Map pbsg_Initialize(Map config) {
  // (Re-)Build the PBSG from config. [Ignore 'state.pbsgStore'.]
  Map pbsg = [
    name: config?.name,
    buttonsLIFO: [],
    defaultButton: null,
    activeButton: null
  ]
  // Process buttons into order leveraging their current state for treatment
  // as 'active' or 'inactive'. If a single button is active, it should be
  // preserved.
  config?.allButtons.each{ button ->
    switch (switchState(device)) {
      case 'on':
        pbsg_ActivateButton(pbsg, button)
        break;
      default:
        pbsg_DeactivateButton(pbsg, button)
    }
  }
  if (!pbsg.activeButton && config.initialActiveButton) {
    logInfo(
      'pbsg_Initialize',
      "initialActiveButton (${config.initialActiveButton}) → active"
    )
    pbsg_ActivateButton(pbsg, config.initialActiveButton)
  }
  // No immediate action when setting the pbsg.defaultButton
  pbsg.defaultButton = config.defaultButton
  // The defaultButton is used to populate an empty activeButton
  pbsg_EnforceDefault(pbsg)
  logInfo('pbsg_Initialize', "Initial PBSG: ${pbsg_State(pbsg)}")
  pbsgStore_Save(pbsg)
  return pbsg
}

//----------> Integer pauseMS = 500
//----------> if (pauseMS) { pauseExecution(pauseMS) }

void pbsg_VswEventHandler(Event e) {
  // VSW events are suppressed when this application adjusts a VSW
  // (e.g., in response to Lutron RA2 and Pro2 events). The events here
  // should indicate a manual action (e.g., via the Hubitat GUI, Alexa).
  // VSW events leverage pbsg_ActivateButton() and pbsg_DeactivateButton()
  // to cause the PBSGs to match the VSW event.
  //
  // CIRCULAR LOOPS OCCUR IF EVENT SUBCRIPTIONS ARE NOT CORRECTLY PAUSED.
  //
  // The DNIs is of the form '${pbsgInstName}-${buttonName}'.
  //
  // RA2 turns off one scene BEFORE turning on the replacement scene.
  // PRO2 turns on scenes without turning off predecessors.
  logInfo('pbsg_VswEventHandler', e.descriptionText)
  ArrayList parsedDNI = e.displayName.tokenize('_')
  String pbsgName = parsedDNI[0]
  String button = parsedDNI[1]
  Map pbsg = pbsgStore_Retrieve(pbsgName)
  if (e.value == 'on') { pbsg_ActivateButton(pbsg, button) }
  else if (e.value == 'off') { pbsg_DeactivateButton(pbsg, button) }
  else {
    logWarn(
      'pbsg_VswEventHandler',
      "Unexpected value (${e.value}) for DNI (${dni}")
  }
}

String buttonState(Map pbsg, String button) {
  if (button == null) { logError('buttonState', 'button arg is NULL') }
  String tag = (button && (button == pbsg.defaultButton)) ? '*' : ''
  String summary = "${tag}<b>${button}</b> "
  DevW device = getChildDevice("${pbsg.name}_${button}")
  String swState = switchState(device)
    ?: logError('buttonState', "switchState() failed for button (${button}).")
  if (swState == 'on') { summary += '(<b>on</b>)' }
  else if (swState == 'off') { summary += '(<em>off</em>)' }
  else { summary += '(--)' }
}

String pbsg_State(Map pbsg) {
  String result
  if (pbsg) {
    result = "PBSG \"<b>${pbsg.name}</b>\" "
    result += '['
    result += pbsg.buttonsLIFO.collect{ button ->
      buttonState(pbsg, button)
    }.join(', ')
    result += "]"
    if (pbsg.activeButton) {
      result += " → ${buttonState(pbsg, pbsg.activeButton)}"
    }
  } else {
    logError('pbsg_State', 'Called with null pbsg instance.')
  }
  return result
}

// The psuedo-class "pbsgStore" facilitates concurrent storage of multiple
// "pbsg" psuedo-class instances.

Map pbsgStore_Retrieve(String pbsgName) {
  // Retrieve a "pbsg" psuedo-class instance.
  Map store = state.pbsgStore ?: [:]
  //-> logInfo('pbsgStore_Retrieve', "Stored PBSG count: ${store.size()}")
  return store."${pbsgName}"
}

void pbsgStore_Save(Map pbsg) {
  // Add/update a "pbsg" psuedo-class instance.
  Map store = state.pbsgStore ?: [:]
  store."${pbsg.name}" = pbsg
  //-> logInfo('pbsgStore_Save', "Stored PBSG count: ${store.size()}")
  state.pbsgStore = store
}

// GUI

Map TestPbsgPage() {
  return dynamicPage(
    name: 'TestPbsgPage',
    title: [
      heading1("TestPbsgPage - ${app.id}"),
      bullet1('Click <b>Done</b> to enable subscriptions.')
    ].join('<br/>'),
    install: true,
    uninstall: true
  ) {
    //---------------------------------------------------------------------------------
    // REMOVE NO LONGER USED SETTINGS AND STATE
    //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    //-> app.removeSetting('..')
    //-> state.remove('..')
    //---------------------------------------------------------------------------------
    app.updateLabel('TestPbsgPage')
    state.remove('childVsws')
    // Map mapOfPbsgs = [:]
    Map pbsgInst = [:]
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      state.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
      // NOTE: state.pbsgs is ALWAYS rebuilt from settings and child VSW discovery.
      for (i in [0]) {
        Map config = config_SolicitInstance(1)
        if (config && config.name && config.allButtons) {
          Map pbsg = pbsg_Initialize(config)
          paragraph "${pbsg_State(pbsg)}"
        } else {
          paragraph "PBSG creation is pending sufficient config data"
        }
      }
      paragraph([
        heading1('Debug'),
        *appStateAsBullets(),
        *appSettingsAsBullets(),
      ].join('<br/>'))
    }
  }
}

/*
void deleteOrphanedDevices() {
  ArrayList dnis = getChildDevices().collect{ d -> d.deviceNetworkId }
}
*/

void initialize() {
  // Drop unclaimed child devices
}

void installed() {
  logWarn('installed', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled() {
  logWarn('uninstalled', 'Entered')
  removeAllChildApps()
}

void updated() {
  logWarn('updated', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}
