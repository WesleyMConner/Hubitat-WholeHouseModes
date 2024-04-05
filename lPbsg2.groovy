// ---------------------------------------------------------------------------------
// P ( U S H )   B ( U T T O N )   S ( W I T C H )   G ( R O U P )
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
// Referenced types below
//   - import com.hubitat.app.DeviceWrapper as DevW
//   - import com.hubitat.hub.domain.Event as Event
// The following are required when instantiating a PbsgCore application.
//   - #include wesmc.lHExt
//   - #include wesmc.lHUI

library(
  name: 'lPbsgv2',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Push Button Switch Group (PBSG) Implementation',
  category: 'general purpose'
)

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

DevW pbsg_GetOrCreateMissingDevice(DevW device, String deviceDNI) {
  if (device && device.deviceNetworkId != deviceDNI) {
    logError(
      'pbsg_GetOrCreateMissingDevice',
      "Provided device DNI (${device.deviceNetworkId}) != deviceDNI (${deviceDNI})"
    )
  }
  Map addChildArgs = [
    isComponent: true,
    name: deviceDNI.replaceAll('_', ' ')
  ]
  DevW d = device
  ?: getChildDevice(deviceDNI)
  ?: addChildDevice(
    'hubitat',          // namespace
    'Virtual Switch',   // typeName
    deviceDNI,          // device's unique DNI (with '_' delimiter)
    addChildArgs
  )
  return d
}

void pbsg_ActivateButton(Map pbsg, String button, DevW device = null) {
  DevW d = pbsg_GetOrCreateMissingDevice(device, "${pbsg.name}_${button}")
  String dState = switchState(d)
  if (pbsg.activeButton == button) {
    // The button is already active. Ensure its VSW is 'on'.
    if (dState != 'on') {
      logWarn('pbsg_ActivateButton', "Correcting ACTIVE ${button} w/ state '${dState}'")
      unsubscribe(d)
      d.on()
      pbsgButtonOnCallback(button)
      pauseExecution(100)  // Take a breath to let device update
      subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
    }
  } else {
    if (pbsg.activeButton != null) {
      // IMPORTANT: Move the currently active button out of the way, but
      //            DO NOT leverage pbsg_DeactivateButton() which will
      //            populate an empty activeButton with defaultButton.
      DevW priorActive = pbsg_GetOrCreateMissingDevice(null, "${pbsg.name}_${pbsg.activeButton}")
      unsubscribe(priorActive)
      priorActive.off()
      pauseExecution(100)  // Take a breath to let device update
      pbsg.buttonsLIFO.push(pbsg.activeButton)
      pbsg.activeButton = null
      subscribe(priorActive, pbsg_VswEventHandler, ['filterEvents': true])
    }
    // Relocate the newly activated button (from the LIFO) and turn it 'on'.
    Integer indexInLIFO = null
    pbsg.buttonsLIFO.eachWithIndex { b, i ->
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
    pbsgButtonOnCallback(button)
    pauseExecution(100)  // Take a breath to let device update
    subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
  }
}

void pbsg_DeactivateButton(Map pbsg, String button, DevW device = null) {
  DevW d = pbsg_GetOrCreateMissingDevice(device, "${pbsg.name}_${button}")
  if (pbsg.activeButton == button) {
    unsubscribe(d)
    d.off()
    pauseExecution(100)  // Take a breath to let device update
    pbsg.buttonsLIFO.push(pbsg.activeButton)
    pbsg.activeButton = null
    subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
    pbsg_EnforceDefault(pbsg)
  } else if (!pbsg.buttonsLIFO.contains(button)) {
    logWarn('pbsg_DeactivateButton', "${button} → ${pbsg.name} ${pbsg.buttonsLIFO}")
    unsubscribe(d)
    d.off()
    pauseExecution(100)  // Take a breath to let device update
    pbsg.buttonsLIFO.push(button)
    subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
  }
  // else -> Nothing to do, button is already deactivated
}

//void pbsg_ActivatePrior(Map pbsg) {
//  // Swap the currently active DNI with the DNI at the front of the LIFO.
//  DevW temp = null
//  if (pbsg.activeButton) { temp = pbsg.activeButton }
//  pbsg.activeButton = pbsg.buttonsLIFO.pop()
//  if (temp) { pbsg.buttonsLIFO.push(temp) }
//}

void pbsg_EnforceDefault(Map pbsg) {
  if (pbsg && (!pbsg.activeButton && pbsg.defaultButton)) {
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
  config?.allButtons.each { button ->
    DevW device = getChildDevice("${config.name}_${button}")
    switch (switchState(device)) {
      case 'on':
        pbsg_ActivateButton(pbsg, button)
        break
      default:
        pbsg_DeactivateButton(pbsg, button)
    }
  }
  if (!pbsg.activeButton) {
    // During INIT: Use initialActiveButton in lieu of defaultButton
    if (config?.initialActiveButton) {
      logInfo(
        'pbsg_Initialize',
        "initialActiveButton (${config.initialActiveButton}) → active"
      )
      pbsg_ActivateButton(pbsg, config.initialActiveButton)
      pbsg.defaultButton = config.defaultButton
    } else {
      pbsg.defaultButton = config.defaultButton
      pbsg_EnforceDefault(pbsg)
    }
  }
  // Post INIT: Use defaultButton to populate an empty activeButton
  logInfo('pbsg_Initialize', "Initial PBSG: ${pbsg_State(pbsg)}")
  pbsgStore_Save(pbsg)
  // Delete Child Devices with DNIs prefixed with this PBSG instance name
  // and with buttons names that no longer exist.
  getChildDevices().each { device ->
    String dni = device.deviceNetworkId
    ArrayList nameAndButton = dni.tokenize('_')
    String pbsgName = nameAndButton[0]
    String buttonName = nameAndButton[1]
    if (pbsgName == pbsg.name && !config.allButtons.contains(buttonName)) {
      logWarn('pbsg_Initialize', "Deleting orphaned VSW '${dni}'")
      deleteChildDevice(dni)
    }
  }
  return pbsg
}

void pbsg_VswEventHandler(Event e) {
  // VERY IMPORTANT
  //   - VSW event subscriptions are suppressed when this application is
  //     adjusts VSWs (e.g., in response to Lutron RA2 and Pro2 events).\
  //   - This handler processes events that arise from external actions
  //     (e.g., Hubitat GUI, Alexa).
  //   - An event's displayName is the "name" of the VSW, not the DNI.
  //       Format of name: '${pbsgInstName} ${buttonName}'
  //       Format of DNI:  '${pbsgInstName}_${buttonName}'
  //     So, care must be taken when tokenizing e.displayName.
  //   - RA2 turns off one scene BEFORE turning on the replacement scene.
  //   - PRO2 turns on scenes without turning off predecessors.
  //
  logInfo('pbsg_VswEventHandler', "${e.displayName} → ${e.value}")
  ArrayList parsedDNI = e.displayName.tokenize(' ')
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
    result += pbsg.buttonsLIFO.collect { button ->
      buttonState(pbsg, button)
    }.join(', ')
    result += ']'
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
  return store."${pbsgName}"
}

void pbsgStore_Save(Map pbsg) {
  // Add/update a "pbsg" psuedo-class instance.
  Map store = state.pbsgStore ?: [:]
  store."${pbsg.name}" = pbsg
  state.pbsgStore = store
}
