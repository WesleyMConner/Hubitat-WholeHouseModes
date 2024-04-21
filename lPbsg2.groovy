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

void pbsg_ActivateButton(String pbsgName, String button, DevW device = null) {
  logInfo('pbsg_ActivateButton#111', "pbsgName: >${pbsgName }<")
  Map pbsgMap = atomicState."${pbsgName}"
  if (pbsgMap) {
    DevW d = pbsg_GetOrCreateMissingDevice(device, "${pbsgMap.name}_${button}")
    String dState = switchState(d)
    if (pbsg?.activeButton == button) {
      // The button is already active. Ensure its VSW is 'on'.
      if (dState != 'on') {
        logWarn('pbsg_ActivateButton', "Correcting ACTIVE ${button} w/ state '${dState}'")
        unsubscribe(d)
        d.on()
        pbsg_ButtonOnCallback(pbsgName)
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
        atomicState."${pbsgMap.name}" = pbsgMap // Persist pbsg instance change
        //-> pbsgStore_Save(pbsgMap)
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
      atomicState."${pbsgMap.name}" = pbsgMap // Persist pbsg instance change
      //-> pbsgStore_Save(pbsgMap)
      pbsg_ButtonOnCallback(pbsgName)       // Trigger button 'on' behavior
      pauseExecution(100)                  // Pause for downstream changes
      subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
    }
  } else {
    logError('pbsg_ActivateButton', "Cannot find pbsg '${pbsgName}'")
  }
}

void pbsg_DeactivateButton(String pbsgName, String button, DevW device = null) {
  Map pbsgMap = atomicState."${pbsgName}"
  if (pbsgMap) {
    DevW d = pbsg_GetOrCreateMissingDevice(device, "${pbsgMap.name}_${button}")
    if (pbsgMap.activeButton == button) {
      unsubscribe(d)
      d.off()
      pbsgMap.buttonsLIFO.push(pbsgMap.activeButton)
      pbsgMap.activeButton = null
      atomicState."${pbsgMap.name}" = pbsgMap // Persist pbsg instance change
      //-> pbsgStore_Save(pbsgMap)
      pauseExecution(100)                    // Pause for downstream changes
      subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
      pbsg_EnforceDefault(pbsgName)
    } else if (!pbsgMap.buttonsLIFO.contains(button)) {
      //-> logInfo('pbsg_DeactivateButton', "Adding button '${button}' → ${pbsg.name} ${pbsg.buttonsLIFO}")
      unsubscribe(d)
      d.off()
      pbsgMap.buttonsLIFO.push(button)
      atomicState."${pbsgMap.name}" = pbsgMap // Persist pbsg instance change
      //-> pbsgStore_Save(pbsgMap)
      pauseExecution(100)                    // Pause for downstream changes
      subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
    }
  // Else: Nothing to do, button is already deactivated
  } else {
    logWarn('pbsg_DeactivateButton', "Cannot find pbsg '${pbsgName}'")
  }
}

void pbsg_EnforceDefault(String pbsgName) {
  if (atomicState."${pbsgName}") {
    if (
      !atomicState."${pbsgName}".activeButton
      && atomicState."${pbsgName}".defaultButton
    ) {
      logInfo('pbsg_EnforceDefault', "Target is '${pbsgName}'")
      pbsg_ActivateButton(pbsgName, atomicState."${pbsgName}".defaultButton)
    }
  } else {
    logWarn('pbsg_EnforceDefault', "Cannot find pbsg '${pbsgName}'")
  }
}

String missingKey(String stateKey, String objectKey) {
  logError('missingKey', "Cannot find objectKey '${objectKey}' for stateKey '${stateKey}'")
}

Boolean pbsgConfigExists (String pbsgName) {
  // PBSG creation leverages the following initial 'configuration' fields:
  //   - name ............. required
  //   - instType ......... required
  //   - allButtons ....... required
  //   - defaultButton .... optional
  Boolean result = false
  if (!atomicState."${pbsgName}") {
    logError('pbsg_BuildToConfig', "Cannot find '${pbsgName}' in atomicState")
  } else if (!atomicState."${pbsgName}".name) {
    missingKey(pbsgName, 'name')
  } else if (!atomicState."${pbsgName}".instType) {
    missingKey(pbsgName, 'instType')
  } else if (!atomicState."${pbsgName}".allButtons) {
    missingKey(pbsgName, 'allButtons')
  } else {
    result = true
  }
  return result
}

Map pbsg_BuildToConfig(String pbsgName) {
  if (pbsgConfigExists(pbsgName)) {
    // Initialize PBSG instance fields
    atomicState."${pbsgName}".buttonsLIFO = []
    atomicState."${pbsgName}".activeButton = null
    // Process buttons to locate or create associated VSWs. The state of
    // existing devices is respected to the extent that a single PBSG
    // VSW is active.
    atomicState."${pbsgName}".allButtons.each { button ->
      String deviceDni = "${pbsgName}_${button}"
      DevW device = getChildDevice(deviceDni)
      switch (switchState(device)) {
        case 'on':
          pbsg_ActivateButton(pbsgName, button)
          break
        default:
          pbsg_DeactivateButton(pbsgName, button)
      }
    }
    if (atomicState."${pbsgName}".activeButton) {
      pbsg_EnforceDefault(pbsgName)
    }
    getChildDevices().each { device ->
      String dni = device.deviceNetworkId
      ArrayList dniNameAndButton = dni.tokenize('_')
      String dniPbsgName = dniNameAndButton[0]
      String dniButtonName = dniNameAndButton[1]
      if (
        pbsgName == dniPbsgName
        && !atomicState."${pbsgName}"?.allButtons.contains(dniButtonName)) {
        logWarn('pbsg_BuildToConfig', "Deleting orphaned VSW '${dni}'")
        deleteChildDevice(dni)
      }
    }
    return pbsgMap
  }
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
  if (e.value == 'on') {
    pbsg_ActivateButton(pbsgName, button)
  } else if (e.value == 'off') {
    pbsg_DeactivateButton(pbsgName, button)
  } else {
    logWarn(
      'pbsg_VswEventHandler',
      "Unexpected value (${e.value}) for DNI (${dni}")
  }
}

String buttonState(String pbsgName, String button) {
  Map pbsgMap = atomicState."${pbsgName}"
  if (button != null) {
    String tag = (button == pbsgMap.defaultButton) ? '*' : ''
    String summary = "${tag}<b>${button}</b> "
    DevW device = getChildDevice("${pbsgName}_${button}")
    String swState = switchState(device)
      ?: logError('buttonState', "switchState() failed for button (${button}).")
    if (swState == 'on') {
      summary += '(<b>on</b>)'
    } else if (swState == 'off') {
      summary += '(<em>off</em>)'
    } else {
      summary += '(--)'
    }
  } else {
    logError('buttonState', 'button arg is NULL')
  }
}

String pbsg_State(String pbsgName) {
  String result
  if (atomicState."${pbsgName}") {
    result = "PBSG \"<b>${atomicState."${pbsgName}".name}</b>\" "
    result += '['
    result += atomicState."${pbsgName}".buttonsLIFO.collect { button ->
      buttonState(pbsgName, button)
    }.join(', ')
    result += ']'
    String activeButton = atomicState."${pbsgName}".activeButton
    result += (activeButton) ? " → ${buttonState(pbsgName, activeButton)}" : ''
  } else {
    logError('pbsg_State', "Could not find pbsg '${pbsgName}'")
  }
  return result
}
