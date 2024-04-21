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
// The following are required when using this library.
//   - #include wesmc.lHExt
//   - #include wesmc.lHUI

library(
  name: 'lPbsgv2',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Push Button Switch Group (PBSG) Implementation',
  category: 'general purpose'
)

// The psuedo-class "pbsgStore" facilitates concurrent storage of multiple
// "pbsg" psuedo-class instances. This storage facility can and should be
// used for descendent psuedo-classes - e.g., "roomStore".

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

ArrayList pbsgStore_ListInstances(String instType) {
  Map pbsgStore = state.pbsgStore ?: [:]
  ArrayList instances =  pbsgStore.findResults { k, v ->
    (v.instType == instType) ? k : null
  }.sort()
  return instances
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
    ArrayList allButtons = config_SolicitButtons(buttonsKey)
    String defaultButton = config_SolicitDefault(defaultKey, allButtons)
    config = [
      'name': name,
      'allButtons': allButtons,
      'defaultButton': defaultButton
    ]
  } else {
    paragraph('', width: 10)  // Filler for a 12 cell row
  }
  return config
}

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

void pbsg_ActivateButton(Map pbsgMap, String button, DevW device = null) {
  if (pbsgMap) {
    DevW d = pbsg_GetOrCreateMissingDevice(device, "${pbsgMap.name}_${button}")
    String dState = switchState(d)
    if (pbsg?.activeButton == button) {
      // The button is already active. Ensure its VSW is 'on'.
      if (dState != 'on') {
        logWarn('pbsg_ActivateButton', "Correcting ACTIVE ${button} w/ state '${dState}'")
        unsubscribe(d)
        d.on()
        pbsg_ButtonOnCallback(pbsgMap)
        pauseExecution(100)                // Pause for downstream changes
        subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
      }
    } else {
      if (pbsgMap?.activeButton != null) {
        // IMPORTANT: Move the currently active button out of the way, but
        //            DO NOT leverage pbsg_DeactivateButton() which will
        //            populate an empty activeButton with defaultButton.
        DevW priorActive = pbsg_GetOrCreateMissingDevice(
          null,
          "${pbsgMap.name}_${pbsgMap.activeButton}"
        )
        unsubscribe(priorActive)
        priorActive.off()
        pbsgMap.buttonsLIFO.push(pbsgMap.activeButton)
        pbsgMap.activeButton = null
        pbsgStore_Save(pbsgMap)            // Persist pbsg instance change
        pauseExecution(100)                // Pause for downstream changes
        subscribe(priorActive, pbsg_VswEventHandler, ['filterEvents': true])
      }
      // Move the newly-activated button from the LIFO to the active slot.
      Integer indexInLIFO = null
      pbsgMap.buttonsLIFO.eachWithIndex { b, i ->
        if (b == button) { indexInLIFO = i }
      }
      if (indexInLIFO) {
        pbsgMap.buttonsLIFO.removeAt(indexInLIFO)
      }
      unsubscribe(d)
      pbsgMap.activeButton = button
      d.on()
      pbsgStore_Save(pbsgMap)              // Persist pbsg instance change
      pbsg_ButtonOnCallback(pbsgMap)       // Trigger button 'on' behavior
      pauseExecution(100)                  // Pause for downstream changes
      subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
    }
  } else {
    logError('pbsg_ActivateButton', 'Reveived null pbsgMap')
  }
}

void pbsg_DeactivateButton(Map pbsgMap, String button, DevW device = null) {
  DevW d = pbsg_GetOrCreateMissingDevice(device, "${pbsgMap.name}_${button}")
  if (pbsgMap.activeButton == button) {
    unsubscribe(d)
    d.off()
    pbsgMap.buttonsLIFO.push(pbsgMap.activeButton)
    pbsgMap.activeButton = null
    pbsgStore_Save(pbsgMap  )              // Persist pbsg instance change
    pauseExecution(100)                    // Pause for downstream changes
    subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
    pbsg_EnforceDefault(pbsgMap)
  } else if (!pbsgMap.buttonsLIFO.contains(button)) {
    //-> logInfo('pbsg_DeactivateButton', "Adding button '${button}' → ${pbsg.name} ${pbsg.buttonsLIFO}")
    unsubscribe(d)
    d.off()
    pbsgMap.buttonsLIFO.push(button)
    pbsgStore_Save(pbsgMap)                // Persist pbsg instance change
    pauseExecution(100)                    // Pause for downstream changes
    subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
  }
  // Else: Nothing to do, button is already deactivated
}

void pbsg_EnforceDefault(Map pbsgMap) {
  if (pbsgMap && (!pbsgMap.activeButton && pbsgMap.defaultButton)) {
    //-> logInfo('pbsg_EnforceDefault', "${pbsg.defaultButton} → active")
    pbsg_ActivateButton(pbsgMap, pbsgMap.defaultButton)
  }
}

Map pbsg_CreateInstance(Map pbsgMap, String instType) {
  // (Re-)Build the PBSG from config. [Ignore 'state.pbsgStore'.]
  // Per config_SolicitInstance(...), the following subsect of psuedo-class
  // "pbsg" fields functon collectively drive configuration:
  //   - name
  //   - allButtons
  //   - defaultButton
  pbsgMap.instType = instType
  pbsgMap.buttonsLIFO = []
  pbsgMap.activeButton = null
  // Process buttons into order leveraging their current state for treatment
  // as 'active' or 'inactive' (in the fifo). If a single button is active,
  // it should be preserved.
  pbsgMap?.allButtons.each { button ->
    DevW device = getChildDevice("${pbsgMap.name}_${button}")
    switch (switchState(device)) {
      case 'on':
        pbsg_ActivateButton(pbsgMap, button)
        break
      default:
        pbsg_DeactivateButton(pbsgMap, button)
    }
  }
  if (!pbsgMap.activeButton) {
    pbsg_EnforceDefault(pbsgMap)
  }
  // Summarize the initial state of the PBSG
  if (!pbsgMap) {
    logError('pbsg_CreateInstance', 'Encountered a null pbsgMap')
  }
  //->logTrace('pbsg_CreateInstance', pbsg_State(pbsgMap))
  pbsgStore_Save(pbsgMap)
  // Clean up (delete) Child Devices with DNIs prefixed with this PBSG
  // instance name and with buttons names that no longer exist.
  getChildDevices().each { device ->
    String dni = device.deviceNetworkId
    ArrayList nameAndButton = dni.tokenize('_')
    String pbsgName = nameAndButton[0]
    String buttonName = nameAndButton[1]
    if (pbsgName == pbsgMap.name && !pbsgMap.allButtons.contains(buttonName)) {
      logWarn('pbsg_CreateInstance', "Deleting orphaned VSW '${dni}'")
      deleteChildDevice(dni)
    }
  }
  return pbsgMap
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
  Map pbsgMap = pbsgStore_Retrieve(pbsgName)
  if (e.value == 'on') { pbsg_ActivateButton(pbsgMap, button) }
  else if (e.value == 'off') { pbsg_DeactivateButton(pbsgMap, button) }
  else {
    logWarn(
      'pbsg_VswEventHandler',
      "Unexpected value (${e.value}) for DNI (${dni}")
  }
}

String buttonState(Map pbsgMap, String button) {
  if (button == null) { logError('buttonState', 'button arg is NULL') }
  String tag = (button && (button == pbsgMap.defaultButton)) ? '*' : ''
  String summary = "${tag}<b>${button}</b> "
  DevW device = getChildDevice("${pbsgMap.name}_${button}")
  String swState = switchState(device)
    ?: logError('buttonState', "switchState() failed for button (${button}).")
  if (swState == 'on') { summary += '(<b>on</b>)' }
  else if (swState == 'off') { summary += '(<em>off</em>)' }
  else { summary += '(--)' }
}

String pbsg_State(Map pbsgMap) {
  String result
  if (pbsgMap) {
    result = "PBSG \"<b>${pbsgMap.name}</b>\" "
    result += '['
    result += pbsgMap.buttonsLIFO.collect { button ->
      buttonState(pbsgMap, button)
    }.join(', ')
    result += ']'
    if (pbsgMap.activeButton) {
      result += " → ${buttonState(pbsgMap, pbsgMap.activeButton)}"
    }
  } else {
    logError('pbsg_State', 'Called with null pbsg instance.')
  }
  return result
}
