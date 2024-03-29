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
// The following are required when instantiating a PbsgCore application.
//   - import com.hubitat.app.DeviceWrapper as DevW
//   - import com.hubitat.app.InstalledAppWrapper as InstAppW
//   - import com.hubitat.hub.domain.Event as Event
//   - #include wesmc.lFifo
//   - #include wesmc.lHExt
//   - #include wesmc.lHUI

library(
  name: 'lPbsg2',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Push Button Switch Group (PBSG) Implementation',
  category: 'general purpose'
)

Map pbsgs() {
  if (!state.pbsgs) {
    logWarn('pbsgs', 'Creating null state.pbsgs map.')
    state.pbsgs = [:]
  }
  return state.pbsgs
}

Map getPbsg(String pbsgLabel) {
  return pbsgs()?."${pbsgLabel}"
}

String pbsgs_buttonToDni(String pbsgLabel, String button) {
  return "${pbsgLabel}_${button}"
}

String pbsgs_dniToButton(String pbsgLabel, String dni) {
  return dni.substring("${pbsgLabel}_".length())
}

Map getPbsg(String pbsgLabel) {
  return pbsgs()?."${pbsgLabel}"
}

String pbsgs_buttonToDni(String pbsgLabel, String button) {
  return "${pbsgLabel}_${button}"
}

String pbsgs_dniToButton(String pbsgLabel, String dni) {
  return dni.substring("${pbsgLabel}_".length())
}

Boolean pbsgs_add(String pbsgLabel, ArrayList buttons, String dflt, String active) {
  Boolean retVal = true
  ArrayList cleanButtons = cleanStrings(buttons)
  if (buttonCount < 2) {
    retVal = false
    logError('pbsgs_add', [
      "Got ${buttonCount} buttons, expected two or more unique buttons",
      "Unique buttons: >${cleanButtons}<"
    ])
  }
  if (dflt && cleanButtons?.contains(dflt) == false) {
    retVal = false
    logError('pbsgs_add',
      "default ${b(dflt)} is not found among buttons (${cleanButtons})"
    )
  }
  if (active && cleanButtons?.contains(active) == false) {
    retVal = false
    logError('pbsgs_add',
      "activeDni ${b(active)} is not found among buttons (${cleanButtons})")
  }
  if (retVal) {
    pbsgs."${pbsgLabel}" = [
      dnis: cleanButtons.collect{ b -> pbsgs_buttonToDni(b) },
      dfltDni: pbsg.dflt ? pbsgs_buttonToDni(pbsg.dflt) : null,
      activeDni: pbsg.active ? pbsgs_buttonToDni(pbsg.active) : null,
    ]
  }
  return retVal
}

Boolean pbsgs_activateDni(String pbsgLabel, String dni) {
  // Return TRUE on a configuration change, FALSE otherwise.
  // Publish an event ONLY IF/WHEN a new dni is activated.
  //logTrace('pbsgs_activateDni (Entry)', pbsgs_stateWithVswState())
  Boolean isStateChanged = false
  Map pbsg = getPbsg(pbsgLabel)
  if (pbsg.activeDni == dni) {
    logTrace('pbsgs_activateDni', "No action, ${dni} is already active")
  } else if (dni && !pbsgs_getDnis()?.contains(dni)) {
    logError(
      'pbsgs_activateDni',
      "DNI >${dni}< does not exist (${pbsgs_getDnis()})"
    )
  } else {
    isStateChanged = true
    logInfo('pbsgs_activateDni', "Moving active (${pbsg.activeDni}) to inactive")
    pbsgs_moveActiveToInactive()
    fifoRemove(pbsg.inactiveDnis, dni)
    // Adjust the activeDni and Vsw together
    logTrace('pbsgs_activateDni', "Activating ${dni}")
    pbsg.activeDni = dni
    pbsgPublishactive()
  //logTrace('pbsgs_activateDni (Adjusted)', pbsgs_stateWithVswState())
  }
  return isStateChanged
}

Boolean pbsgs_deactivateDni(String pbsgLabel, String dni) {
  // Return TRUE on a configuration change, FALSE otherwise.
  //logTrace('pbsgs_deactivateDni', pbsgs_stateWithVswState())
  logTrace('pbsgs_deactivateDni', "DNI: ${b(dni)}")
  Boolean isStateChange = false
  Map pbsg = getPbsg(pbsgLabel)
  if (pbsg.inactiveDnis.contains(dni)) {
    // Nothing to do, dni is already inactive
    logTrace('pbsgs_deactivateDni', "Nothing to do for dni: ${b(dni)}")
  } else if (pbsg.activeDni == state.dfltDni) {
    // It is likely that the default DNI has been manually turned off.
    // Update the PBSG state to reflect this likely fact.
    logInfo('pbsgs_deactivateDni', "Moving active (${pbsg.activeDni}) to inactive")
    pbsgs_moveActiveToInactive()
    // Force re-enable the default DNI.
    isStateChange = pbsgs_activateDni(state.dfltDni)
  } else {
    logInfo(
      'pbsgs_deactivateDni',
      "Activating default ${b(state.dfltDni)}, which deacivates dni: ${b(dni)}"
    )
    isStateChange = pbsgs_activateDni(state.dfltDni)
  }
  return isStateChange
}





Boolean pbsgs_activateButton(String pbsgLabel, String button) {
  logTrace('pbsgs_activateButton', "pbsgLabel: ${pbsgLabel}, button: ${b(button)}")
  return pbsgs_activateDni(pbsgs_buttonToDni(pbsgLabel, button))
}

Boolean pbsgs_deactivateButton(String pbsgLabel, String button) {
  logTrace('pbsgs_deactivateButton', "pbsgLabel: ${pbsgLabel}, button: ${b(button)}")
  return pbsgs_deactivateDni(pbsgs_buttonToDni(pbsgLabel, button))
}

Boolean pbsgs_toggleButton(String pbsgLabel, String button) {
  logTrace('pbsgs_toggleButton', "pbsgLabel: ${pbsgLabel}, button: ${b(button)}")
  Map pbsg = getPbsg(pbsgLabel)
  return (pbsg.activeDni == pbsgs_buttonToDni(pbsgLabel, button))
     ? pbsgs_deactivateButton(pbsgLabel, button)
     : pbsgs_activateButton(pbsgLabel, button)
}

Boolean pbsgs_activatePrior(String pbsgLabel) {
  //logTrace('pbsgs_activatePrior', pbsgs_stateWithVswState(pbsgLabel))
  Map pbsg = getPbsg(pbsgLabel)
  String predecessor = pbsg.inactiveDnis.first()
  logTrace('pbsgs_activatePrior', "predecessor: ${predecessor}")
  return pbsgs_activateDni(pbsgLabel, predecessor)
}

ArrayList pbsgs_listVsws(String pbsgLabel) {
  ArrayList outputText = [ heading2('DEVICES') ]
  List<InstAppW> devices = getChildDevices()
  devices.each { d -> outputText += bullet2(d.deviceNetworkId) }
  return outputText
}


/*
void addVsw(String pbsgLabel, String dni) {
  // All adds are appended to the inactive Fifo.
  if (dni) {
    Map pbsg = getPbsg(pbsgLabel)
    pbsg.inactiveDnis = pbsg.inactiveDnis ?: []
    fifoEnqueue(pbsg.inactiveDnis, dni)
    logWarn('addVsw', "Adding child device (${dni})")
    addChildDevice(
      'hubitat',          // namespace
      'Virtual Switch',   // typeName
      dni,                // device's unique DNI
      [isComponent: true, name: dni]
    )
  }
}
*/

/*
void pbsgs_dropDni(String pbsgLabel, String dni) {
  // Drop without enforcing Default DNI.
  Map pbsg = getPbsg(pbsgLabel)
  if (pbsg.activeDni == dni) {
    pbsg.activeDni = null
  } else {
    fifoRemove(pbsg.inactiveDnis, dni)
  }
  logWarn('pbsgs_dropDni', "Dropping child device (${dni})")
  deleteChildDevice(dni)
}
*/

ArrayList pbsgs_childVswState(String pbsgLabel, Boolean includeHeading = false) {
  ArrayList results = []
  if (includeHeading) { results += heading2('VSW States') }
  getChildDevices().retainAll { d -> d.name.find(pbsgLabel) }.each { d ->
    if (switchState(d) == 'on') {
      results += bullet2("<b>${d.deviceNetworkId}: on</b>")
    } else {
      results += bullet2("<i>${d.deviceNetworkId}: off</i>")
    }
}
  return results
}

//String pbsgs_stateWithVswState(String pbsgLabel) {
//  return [
//    "<table style='border-spacing: 0px;' rules='all'><tr>",
//    "<th style='width:49%'>STATE</th>",
//    '<th/>',
//    "<th style='width:49%'>VSW STATUS</th>",
//    '</tr><tr>',
//    "<td>${appStateAsBullets().join('<br/>')}</td>",
//    '<td/>',
//    "<td>${pbsgs_childVswState().join('<br/>')}</td>",
//    '</tr></table'
//  ].join()
//}

void syncChildVswsToPbsgState(String pbsgLabel) {
  // W A R N I N G
  //   - WHEN UPDATING CHILD DEVICES WITH ACTIVE SUBSCRIPTIONS ...
  //   - HUBITAT MAY PROVIDE DEVICE HANDLERS WITH A STALE STATE MAP
  //   - TEMPORARILY SUSPENDING SUBSCRIPTIONS FOR DEVICE CHANGES
  //logTrace('syncChildVswsToPbsgState (Entry)', pbsgs_stateWithVswState())
  Map pbsg = getPbsg(pbsgLabel)
  if (pbsg.activeDni) {
    // Make sure the correct VSW is on
    DevW onDevice = getChildDevice(pbsg.activeDni)
    if (switchState(onDevice) != 'on') { onDevice.on() }
  }
  // Make sure other VSWs are off
  pbsg.inactiveDnis.each { offDni ->
    DevW offDevice = getChildDevice(offDni)
    if (switchState(offDevice) != 'off') { offDevice.off() }
  }
//logTrace('syncChildVswsToPbsgState (Adjusted)', pbsgs_stateWithVswState())
}

void unsubscribeChildVswEvents(String pbsgLabel) {
  // Unsubscribing to individual devices due to some prior issues with the
  // List version of subscribe()/unsubsribe().
  ArrayList traceSummary = [
    '',
    heading2('Unsubscribed these Child Devices from Events:')
  ]
  getChildDevices().each { d ->
    traceSummary += bullet2(d.deviceNetworkId)
    unsubscribe(d)
  }
  logTrace('unsubscribeChildVswEvents', traceSummary)
}

void subscribeChildVswEvents(String pbsgLabel) {
  //-> Avoid the List version of subscribe. It seems flaky.
  //-> subscribe(childDevices, vswEventHandler, ['filterEvents': true])
  ArrayList traceSummary = [heading2('Subscribing to vswEventHandler')]
  childDevices.each { d ->
    subscribe(d, vswEventHandler, ['filterEvents': true])
    traceSummary += bullet2(d.deviceNetworkId)
  }
  logTrace('subscribeChildVswEvents', traceSummary)
}

void pbsgPublishactive(String pbsgLabel) {
  //logTrace('pbsgPublishactive', pbsgs_stateWithVswState())
  Map pbsg = getPbsg(pbsgLabel)
  String active = pbsgs_dniToButton(pbsg.activeDni)
  logInfo('pbsgPublishactive', "Processing button ${active}")
  //-----------------------------------------------------------------------
  // Box event subscriptions to reduce stale STATE data in Handlers
  unsubscribeChildVswEvents()
  syncChildVswsToPbsgState()
  //-----------------------------------------------------------------------
  parent.pbsgButtonOnCallback(pbsgLabel, active)
  Integer delayInSeconds = 1
  //logTrace(
  //  'pbsgPublishactive',
  //  "Event subscription delayed for ${delayInSeconds} second(s)."
  //)
  runIn(delayInSeconds, 'subscribeChildVswEvents')
}

ArrayList pbsgs_getDnis(String pbsgLabel) {
  Map pbsg = getPbsg(pbsgLabel)
  return cleanStrings([ pbsg.activeDni, *pbsg.inactiveDnis ])
}

Boolean pbsgs_moveActiveToInactive(String pbsgLabel) {
  // Return TRUE on a configuration change, FALSE otherwise.
  // This method DOES NOT (1) activate a dfltDni OR (2) publish an event change
  Boolean isStateChanged = false
  Map pbsg = getPbsg(pbsgLabel)
  if (pbsg.activeDni) {
    logTrace(
      'pbsgs_moveActiveToInactive',
      "Pushing ${b(pbsg.activeDni)} onto inactiveDnis (${pbsg.inactiveDnis})"
    )
    isStateChanged = true
    pbsg.inactiveDnis = [pbsg.activeDni, *pbsg.inactiveDnis]
    pbsg.activeDni = null
  //logTrace('pbsgs_moveActiveToInactive', pbsgs_stateWithVswState())
  }
  return isStateChanged
}

//void pbsgs_Installed(String pbsgLabel) {
//  // Called on instance creation - i.e., before configuration, etc.
//  pbsgs().each { pbsgLabel, map ->
//    map.activeDni = null                            // String
//    map.inactiveDnis = []                           // ArrayList
//    state.dfltDni = null                              // String
//    logTrace('pbsgs_Installed', appStateAsBullets(false))
//  }
//}
//
 //     buttons: cleanStrings(buttons),
 //     default: default ?: null,
 //     active: active ?: null

void addRemoveChildDevices() {

}
pbsgs_updated() {
  // Called by the enclosing application's updated() method.
  Map pbsgs = pbsgs().each{ pbsgLabel, pbsg ->
    ArrayList buttons,
    String dflt,
    String active
    ArrayList prevDnis = pbsgs_getDnis()
//    updatedDnis = pbsg.buttons.collect { buttonObj -> pbsgs_buttonToDni(buttonObj) }
//    updatedDfltDni = pbsg.default ? pbsgs_buttonToDni(pbsg.default) : null
    updatedActiveDni = pbsg.active ? pbsgs_buttonToDni(pbsg.active) : null
    // DETERMINE REQUIRED ADJUSTMENTS BY TYPE
    Map<String, ArrayList> actions = compareLists(prevDnis, updatedDnis)
    ArrayList retainDnis = actions.retained // Used for accounting only
    ArrayList pbsgsDropDnis = actions.dropped
    ArrayList addDnis = actions.added
    String requested = [
      "<b>dnis:</b> ${updatedDnis}",
      "<b>dfltDni:</b> ${updatedDfltDni}",
      "<b>activeDni:</b> ${updatedActiveDni}"
    ].join('<br/>')
    String analysis = [
      "<b>prevDnis:</b> ${prevDnis}",
      "<b>retainDnis:</b> ${retainDnis}",
      "<b>pbsgs_dropDnis:</b> ${pbsgsDropDnis}",
      "<b>addDnis:</b> ${addDnis}"
    ].join('<br/>')
    logInfo('pbsgs_updated', [
      [
        '<table style="border-spacing: 0px;" rules="all"><tr>',
        '<th style="width:32%">ENTRY STATE</th><th style="width:2%"/>',
        '<th style="width:32%">CONFIGURATION PARAMETERS</th><th style="width:2%"/>',
        '<th>REQUIRED ACTIONS</th>',
        '</tr><tr>'
      ].join(),
      "<td>${appStateAsBullets(true).join('<br/>')}</td><td/>",
      "<td>${requested}</td><td/>",
      "<td>${analysis}</td></tr></table>"
    ])
    // Suspend ALL events, irrespective of type. This can be problematic since
    // it also takes out Mode change event subscriptions, et al.
    unsubscribeChildVswEvents()
    state.dfltDni = updatedDfltDni
    pbsgs_dropDnis.each { dni -> pbsgs_dropDni(dni) }
    addDnis.each { dni -> addVsw(dni) }
    // Leverage activation/deactivation methods for initial dni activation.
    if (updatedActiveDni) {
      logTrace('pbsgs_updated', "activating activeDni ${updatedActiveDni}")
      pbsgs_activateDni(updatedActiveDni)
    } else if (pbsg.activeDni == null && state.dfltDni) {
      logTrace('pbsgs_updated', "activating dfltDni ${state.dfltDni}")
      pbsgs_activateDni(state.dfltDni)
    }
      logTrace('pbsgs_updated', pbsgs_listVsws())
      pbsgPublishactive()
  }
}

/*
void pbsgs_uninstalled(String pbsgLabel) {
  logTrace('pbsgs_uninstalled', 'No action')
}
*/

void vswEventHandler(Event e) {
  // Design Notes
  //   - Events can arise from:
  //       1. Methods in this App that change state
  //       2. Manual manipulation of VSWs (via dashboards or directly)
  //       3. Remote manipulation of VSWs (via Amazon Alexa)
  //   - Let downstream functions discard redundant state information
  // W A R N I N G
  //   As of 2023-11-30 Handler continues to receive STALE state values!!!
  //logTrace('vswEventHandler (ENTRY)', pbsgs_stateWithVswState())
  logTrace('vswEventHandler', e.descriptionText)
  if (e.isStateChange) {
    String dni = e.displayName
    if (e.value == 'on') {
      pbsgs_activateDni(dni)
    } else if (e.value == 'off') {
      pbsgs_deactivateDni(dni)
    } else {
      logWarn(
        'vswEventHandler',
        "Unexpected value (${e.value}) for DNI (${dni}")
    }
  } else {
    logWarn('vswEventHandler', "Unexpected event: ${eventDetails(e)}")
  }
}
