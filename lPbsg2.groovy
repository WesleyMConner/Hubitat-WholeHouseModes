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
  name: 'lPbsgv2',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Push Button Switch Group (PBSG) Implementation',
  category: 'general purpose'
)


Boolean pbsgConfigure(
  ArrayList buttons,
  String defaultButton,
  String activeButton,
  String pbsgLogLevel = 'TRACE'
) {
  // CALLED IMMEDIATELY AFTER PBSG INSTANCE CREATION TO SET INITIAL DATA
  // Returns true if configuration is accepted, false otherwise.
  //   - Log levels: 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
  Boolean retVal = true
  settings.buttons = cleanStrings(buttons)
  if (settings.buttons != buttons) {
    logTrace('pbsgConfigure', "buttons: (${buttons}) -> (${settings.buttons})")
  }
  settings.dfltButton = defaultButton ?: null
  if (settings.dfltButton != defaultButton) {
    logTrace('pbsgConfigure', "defaultButton: (${defaultButton}) -> (${settings.dfltButton})")
  }
  settings.activeButton = activeButton ?: null
  if (settings.activeButton != activeButton) {
    logTrace('pbsgConfigure', "activeButton: (${activeButton}) -> (${settings.activeButton})")
  }
  settings.logLevel = ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'].contains(pbsgLogLevel)
    ? pbsgLogLevel : 'TRACE'
  if (settings.logLevel != pbsgLogLevel) {
    logTrace('pbsgConfigure', "pbsgLogLevel: (${pbsgLogLevel}) -> (${settings.logLevel})")
  }
  Integer buttonCount = settings.buttons?.size() ?: 0
  if (buttonCount < 2) {
    retVal = false
    logError('pbsgConfigure', "Button count (${buttonCount}) must be two or more")
  }
  if (settings.dfltButton && settings.buttons?.contains(settings.dfltButton) == false) {
    retVal = false
    logError(
      'pbsgConfigure',
      "defaultButton ${b(settings.dfltButton)} is not found among buttons (${settings.buttons})"
    )
  }
  if (settings.activeButton && settings.buttons?.contains(settings.activeButton) == false) {
    retVal = false
    logError(
      'pbsgConfigure',
      "activeDni ${b(settings.activeButton)} is not found among buttons (${settings.buttons})")
  }
  if (retVal) { updated() }
  return retVal
}

Boolean pbsgActivateButton(String button) {
  logTrace('pbsgActivateButton', "button: ${b(button)}")
  return pbsgActivateDni(buttonToDni(button))
}

Boolean pbsgDeactivateButton(String button) {
  logTrace('pbsgDeactivateButton', "button: ${b(button)}")
  return pbsgDeactivateDni(buttonToDni(button))
}

Boolean pbsgToggleButton(String button) {
  logTrace('pbsgToggleButton', "button: ${b(button)}")
  return (state.activeDni == buttonToDni(button))
     ? pbsgDeactivateButton(button)
     : pbsgActivateButton(button)
}

Boolean pbsgActivatePrior() {
  logTrace('pbsgActivatePrior', pbsgAndVswStateAsString())
  String predecessor = state.inactiveDnis.first()
  logTrace('pbsgActivatePrior', "predecessor: ${predecessor}")
  return pbsgActivateDni(predecessor)
}

String buttonToDni(String button) {
  String dni = "${app.label}_${button}"
  return dni
}

String dniToButton(String dni) {
  String button = dni ? dni.substring("${app.label}_".length()) : null
  return button
}

void addDni(String dni) {
  // All adds are appended to the inactive Fifo.
  if (dni) {
    state.inactiveDnis = state.inactiveDnis ?: []
    fifoEnqueue(state.inactiveDnis, dni)
    logWarn('addDni', "Adding child device (${dni})")
    addChildDevice(
      'hubitat',          // namespace
      'Virtual Switch',   // typeName
      dni,                // device's unique DNI
      [isComponent: true, name: dni]
    )
  }
}

void dropDni(String dni) {
  // Drop without enforcing Default DNI.
  if (state.activeDni == dni) {
    state.activeDni = null
  } else {
    fifoRemove(state.inactiveDnis, dni)
  }
  logWarn('dropDni', "Dropping child device (${dni})")
  deleteChildDevice(dni)
}

Boolean pbsgActivateDni(String dni) {
  // Return TRUE on a configuration change, FALSE otherwise.
  // Publish an event ONLY IF/WHEN a new dni is activated.
  logTrace('pbsgActivateDni (Entry)', pbsgAndVswStateAsString())
  Boolean isStateChanged = false
  if (state.activeDni == dni) {
    logTrace('pbsgActivateDni', "No action, ${dni} is already active")
  } else if (dni && !pbsgGetDnis()?.contains(dni)) {
    logError(
      'pbsgActivateDni',
      "DNI >${dni}< does not exist (${pbsgGetDnis()})"
    )
  } else {
    isStateChanged = true
    logInfo('pbsgActivateDni', "Moving active (${state.activeDni}) to inactive")
    pbsgMoveActiveToInactive()
    fifoRemove(state.inactiveDnis, dni)
    // Adjust the activeDni and Vsw together
    logTrace('pbsgActivateDni', "Activating ${dni}")
    state.activeDni = dni
    pbsgPublishActiveButton()
    logTrace('pbsgActivateDni (Adjusted)', pbsgAndVswStateAsString())
  }
  return isStateChanged
}

Boolean pbsgDeactivateDni(String dni) {
  // Return TRUE on a configuration change, FALSE otherwise.
  logTrace('pbsgDeactivateDni', pbsgAndVswStateAsString())
  logTrace('pbsgDeactivateDni', "DNI: ${b(dni)}")
  Boolean isStateChange = false
  if (state.inactiveDnis.contains(dni)) {
    // Nothing to do, dni is already inactive
    logTrace('pbsgDeactivateDni', "Nothing to do for dni: ${b(dni)}")
  } else if (state.activeDni == state.dfltDni) {
    // It is likely that the default DNI has been manually turned off.
    // Update the PBSG state to reflect this likely fact.
    logInfo('pbsgDeactivateDni', "Moving active (${state.activeDni}) to inactive")
    pbsgMoveActiveToInactive()
    // Force re-enable the default DNI.
    isStateChange = pbsgActivateDni(state.dfltDni)
  } else {
    logInfo(
      'pbsgDeactivateDni',
      "Activating default ${b(state.dfltDni)}, which deacivates dni: ${b(dni)}"
    )
    isStateChange = pbsgActivateDni(state.dfltDni)
  }
  return isStateChange
}

ArrayList childVswStates(Boolean includeHeading = false) {
  ArrayList results = []
  if (includeHeading) { results += heading2('VSW States') }
  getChildDevices().each { d ->
    if (switchState(d) == 'on') {
      results += bullet2("<b>${d.deviceNetworkId}: on</b>")
    } else {
      results += bullet2("<i>${d.deviceNetworkId}: off</i>")
    }
  }
  return results
}

String pbsgAndVswStateAsString() {
  return [
    "<table style='border-spacing: 0px;' rules='all'><tr>",
    "<th style='width:49%'>STATE</th>",
    '<th/>',
    "<th style='width:49%'>VSW STATUS</th>",
    '</tr><tr>',
    "<td>${appStateAsBullets().join('<br/>')}</td>",
    '<td/>',
    "<td>${childVswStates().join('<br/>')}</td>",
    '</tr></table'
  ].join()
}

void syncChildVswsToPbsgState() {
  // W A R N I N G
  //   - WHEN UPDATING CHILD DEVICES WITH ACTIVE SUBSCRIPTIONS ...
  //   - HUBITAT MAY PROVIDE DEVICE HANDLERS WITH A STALE STATE MAP
  //   - TEMPORARILY SUSPENDING SUBSCRIPTIONS FOR DEVICE CHANGES
  logTrace('syncChildVswsToPbsgState (Entry)', pbsgAndVswStateAsString())
  if (state.activeDni) {
    // Make sure the correct VSW is on
    DevW onDevice = getChildDevice(state.activeDni)
    if (switchState(onDevice) != 'on') { onDevice.on() }
  }
  // Make sure other VSWs are off
  state.inactiveDnis.each { offDni ->
    DevW offDevice = getChildDevice(offDni)
    if (switchState(offDevice) != 'off') { offDevice.off() }
  }
  logTrace('syncChildVswsToPbsgState (Adjusted)', pbsgAndVswStateAsString())
}

void unsubscribeChildVswEvents() {
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

void subscribeChildVswEvents() {
  //-> Avoid the List version of subscribe. It seems flaky.
  //-> subscribe(childDevices, vswEventHandler, ['filterEvents': true])
  ArrayList traceSummary = [heading2('Subscribing to vswEventHandler')]
  childDevices.each { d ->
    subscribe(d, vswEventHandler, ['filterEvents': true])
    traceSummary += bullet2(d.deviceNetworkId)
  }
  logTrace('subscribeChildVswEvents', traceSummary)
}

void pbsgPublishActiveButton() {
  logTrace('pbsgPublishActiveButton', pbsgAndVswStateAsString())
  String activeButton = dniToButton(state.activeDni)
  logInfo('pbsgPublishActiveButton', "Processing button ${activeButton}")
  //-----------------------------------------------------------------------
  // Box event subscriptions to reduce stale STATE data in Handlers
  unsubscribeChildVswEvents()
  syncChildVswsToPbsgState()
  //-----------------------------------------------------------------------
  parent.pbsgButtonOnCallback(activeButton)
  Integer delayInSeconds = 1
  logTrace(
    'pbsgPublishActiveButton',
    "Event subscription delayed for ${delayInSeconds} second(s)."
  )
  runIn(delayInSeconds, 'subscribeChildVswEvents')
}

ArrayList pbsgGetDnis() {
  return cleanStrings([ state.activeDni, *state.inactiveDnis ])
}

Boolean pbsgMoveActiveToInactive() {
  // Return TRUE on a configuration change, FALSE otherwise.
  // This method DOES NOT (1) activate a dfltDni OR (2) publish an event change
  Boolean isStateChanged = false
  if (state.activeDni) {
    logTrace(
      'pbsgMoveActiveToInactive',
      "Pushing ${b(state.activeDni)} onto inactiveDnis (${state.inactiveDnis})"
    )
    isStateChanged = true
    state.inactiveDnis = [state.activeDni, *state.inactiveDnis]
    state.activeDni = null
    logTrace('pbsgMoveActiveToInactive', pbsgAndVswStateAsString())
  }
  return isStateChanged
}

ArrayList pbsgListVswDevices() {
  ArrayList outputText = [ heading2('DEVICES') ]
  List<InstAppW> devices = getChildDevices()
  devices.each { d -> outputText += bullet2(d.deviceNetworkId) }
  return outputText
}

void pbsgCoreInstalled() {
  // Called on instance creation - i.e., before configuration, etc.
  state.logLevel = logThreshToLogLevel('TRACE')  // Integer
  state.activeDni = null                            // String
  state.inactiveDnis = []                           // ArrayList
  state.dfltDni = null                              // String
  logTrace('pbsgCoreInstalled', appStateAsBullets(false))
}

void pbsgCoreUpdated() {
  // Values are provided via these settings:
  //   - settings.buttons
  //   - settings.dfltButton
  //   - settings.activeButton
  //   - settings.logLevel
  // PROCESS SETTINGS (BUTTONS) INTO TARGET VSW DNIS
  ArrayList prevDnis = pbsgGetDnis() ?: []
  updatedDnis = settings.buttons.collect { buttonObj -> buttonToDni(buttonObj) }
  updatedDfltDni = settings.dfltButton ? buttonToDni(settings.dfltButton) : null
  updatedActiveDni = settings.activeButton ? buttonToDni(settings.activeButton) : null
  // DETERMINE REQUIRED ADJUSTMENTS BY TYPE
  state.logLevel = logThreshToLogLevel(settings.logLevel)
  Map<String, ArrayList> actions = compareLists(prevDnis, updatedDnis)
  ArrayList retainDnis = actions.retained // Used for accounting only
  ArrayList dropDnis = actions.dropped
  ArrayList addDnis = actions.added
  String requested = [
    "<b>dnis:</b> ${updatedDnis}",
    "<b>dfltDni:</b> ${updatedDfltDni}",
    "<b>activeDni:</b> ${updatedActiveDni}"
  ].join('<br/>')
  String analysis = [
    "<b>prevDnis:</b> ${prevDnis}",
    "<b>retainDnis:</b> ${retainDnis}",
    "<b>dropDnis:</b> ${dropDnis}",
    "<b>addDnis:</b> ${addDnis}"
  ].join('<br/>')
  logInfo('pbsgCoreUpdated', [
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
  dropDnis.each { dni -> dropDni(dni) }
  addDnis.each { dni -> addDni(dni) }
  // Leverage activation/deactivation methods for initial dni activation.
  if (updatedActiveDni) {
    logTrace('pbsgCoreUpdated', "activating activeDni ${updatedActiveDni}")
    pbsgActivateDni(updatedActiveDni)
  } else if (state.activeDni == null && state.dfltDni) {
    logTrace('pbsgCoreUpdated', "activating dfltDni ${state.dfltDni}")
    pbsgActivateDni(state.dfltDni)
  }
  logTrace('pbsgCoreUpdated', pbsgListVswDevices())
  pbsgPublishActiveButton()
}

void pbsgCoreUninstalled() {
  logTrace('pbsgCoreUninstalled', 'No action')
}

void vswEventHandler(Event e) {
  // Design Notes
  //   - Events can arise from:
  //       1. Methods in this App that change state
  //       2. Manual manipulation of VSWs (via dashboards or directly)
  //       3. Remote manipulation of VSWs (via Amazon Alexa)
  //   - Let downstream functions discard redundant state information
  // W A R N I N G
  //   As of 2023-11-30 Handler continues to receive STALE state values!!!
  logTrace('vswEventHandler (ENTRY)', pbsgAndVswStateAsString())
  logTrace('vswEventHandler', e.descriptionText)
  if (e.isStateChange) {
    String dni = e.displayName
    if (e.value == 'on') {
      pbsgActivateDni(dni)
    } else if (e.value == 'off') {
      pbsgDeactivateDni(dni)
    } else {
      logWarn(
        'vswEventHandler',
        "Unexpected value (${e.value}) for DNI (${dni}")
    }
  } else {
    logWarn('vswEventHandler', "Unexpected event: ${eventDetails(e)}")
  }
}
