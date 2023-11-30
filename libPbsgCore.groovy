// ---------------------------------------------------------------------------------
// P B S G C O R E
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
//   - #include wesmc.libFifo
//   - #include wesmc.libHubExt
//   - #include wesmc.libHubUI

library (
  name: 'libPbsgCore',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Push Button Switch Group (PBSG) Implementation',
  category: 'general purpose'
)

//---- CORE METHODS (External)

Boolean pbsgConfigure (
    List<String> buttons,
    String defaultButton,
    String activeButton,
    String pbsgLogLevel = 'TRACE'
  ) {
  // Returns true if configuration is accepted, false otherwise.
  //   - Log levels: 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
  Boolean retVal = true
  settings.buttons = cleanStrings(buttons)
  if (settings.buttons != buttons) {
    Ltrace('pbsgConfigure()', "buttons: (${buttons}) -> (${settings.buttons})")
  }
  settings.dfltButton = defaultButton ? defaultButton : null
  if (settings.dfltButton != defaultButton) {
    Ltrace('pbsgConfigure()', "defaultButton: (${defaultButton}) -> (${settings.dfltButton})")
  }
  settings.activeButton = activeButton ? activeButton : null
  if (settings.activeButton != activeButton) {
    Ltrace('pbsgConfigure()', "activeButton: (${activeButton}) -> (${settings.activeButton})")
  }
  settings.logLevel = ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'].contains(pbsgLogLevel)
    ? pbsgLogLevel : 'TRACE'
  if (settings.logLevel != pbsgLogLevel) {
    Ltrace('pbsgConfigure()', "pbsgLogLevel: (${pbsgLogLevel}) -> (${settings.logLevel})")
  }
  Integer buttonCount = settings.buttons?.size() ?: 0
  if (buttonCount < 2) {
    retVal = false
    Lerror('pbsgConfigure()', "Button count (${buttonCount}) must be two or more")
  }
  if (settings.dfltButton && settings.buttons?.contains(settings.dfltButton) == false) {
    retVal = false
    Lerror(
      'pbsgConfigure()',
      "defaultButton ${b(settings.dfltButton)} is not found among buttons (${settings.buttons})"
    )
  }
  if (settings.activeButton && settings.buttons?.contains(settings.activeButton) == false) {
    retVal = false
    Lerror(
      'pbsgConfigure()',
      "activeDni ${b(settings.activeButton)} is not found among buttons (${settings.buttons})")
  }
  if (retVal) updated()
  return retVal
}

Boolean pbsgActivateButton (String button) {
  _pbsgActivateDni(_buttonToDni(button))
}

Boolean pbsgDeactivateButton (String button) {
  _pbsgDeactivateDni(_buttonToDni(button))
}

Boolean pbsgActivatePredecessor () {
  _tracePbsgStateAndVswState('pbsgActivatePredecessor() AT ENTRY')
  String predecessor = state.inactiveDnis.first()
  Ltrace('pbsgActivatePredecessor()', "predecessor: ${predecessor}")
  return _pbsgActivateDni(predecessor)
}

//---- CORE METHODS (Internal)

String _buttonToDni (String button) {
  //-> return "${getLabel()}_${getId()}_${button}"
  String dni = "${app.getLabel()}_${button}"
  return dni
}

String _dniToButton (String dni) {
  //-> return dni ? dni.substring("${getLabel()}_${getId()}_".length()) : null
  String button = dni ? dni.substring("${app.getLabel()}_".length()) : null
  return button
}

void _addDni (String dni) {
  // All adds are appended to the inactive Fifo.
  if (dni) {
    if (!state.inactiveDnis) state.inactiveDnis = []
    FifoEnqueue(state.inactiveDnis, dni)
    Lwarn('_addDni()', "Adding child device (${dni})")
    DevW vsw = addChildDevice(
      'hubitat',          // namespace
      'Virtual Switch',   // typeName
      dni,                // device's unique DNI
      [isComponent: true, name: dni]
    )
  }
}

void _dropDni (String dni) {
  // Drop without enforcing Default DNI.
  if (state.activeDni == dni) state.activeDni = null
  else FifoRemove(state.inactiveDnis, dni)
  Lwarn('_dropDni()', "Dropping child device (${dni})")
  deleteChildDevice(dni)
}

Boolean _pbsgActivateDni (String dni) {
  // Return TRUE on a configuration change, FALSE otherwise.
  // Publish an event ONLY IF/WHEN a new dni is activated.
  //-> Ldebug('_pbsgActivateDni()', "DNI: ${b(dni)}")
  _tracePbsgStateAndVswState('_pbsgActivateDni() AT ENTRY')
  Boolean isStateChanged = false
  if (state.activeDni == dni) {
    Ldebug('_pbsgActivateDni()', "No action, ${dni} is already active")
  } else if (dni && !_pbsgGetDnis()?.contains(dni)) {
    Lerror(
      '_pbsgActivateDni()',
      "DNI >${dni}< does not exist (${_pbsgGetDnis()})"
    )
  } else {
    isStateChanged = true
    Ldebug('_pbsgActivateDni()', "Moving active (${state.activeDni}) to inactive")
    _pbsgMoveActiveToInactive()
    FifoRemove(state.inactiveDnis, dni)
    // Adjust the activeDni and Vsw together
    Ldebug('_pbsgActivateDni()', "Activating ${dni}")
    state.activeDni = dni
    _pbsgPublishOnButton()
    _tracePbsgStateAndVswState('_pbsgActivateDni() AT EXIT')
  }
  return isStateChanged
}

Boolean _pbsgDeactivateDni (String dni) {
  // Return TRUE on a configuration change, FALSE otherwise.
  _tracePbsgStateAndVswState('_pbsgDeactivateDni() AT ENTRY')
  Ldebug('_pbsgDeactivateDni()', "DNI: ${b(dni)}")
  Boolean isStateChanged = false
Ldebug('#157', [
  '',
  "dni: ${dni}",
  "state.inactiveDnis: ${state.inactiveDnis}",
  "state.inactiveDnis.contains(dni): ${state.inactiveDnis.contains(dni)}"
])
  if (state.inactiveDnis.contains(dni)) {
    // Nothing to do, dni is already inactive
    Ldebug('_pbsgDeactivateDni()', "Nothing to do for dni: ${b(dni)}")
  } else if (state.activeDni == state.dfltDni) {
    // It is likely that the default DNI has been manually turned off.
    // Update the PBSG state to reflect this likely fact.
    Ldebug('_pbsgDeactivateDni()', "Moving active (${state.activeDni}) to inactive")
    _pbsgMoveActiveToInactive()
    // Force re-enable the default DNI.
    isStateChange = _pbsgActivateDni(state.dfltDni)
  } else {
    Ldebug(
      '_pbsgDeactivateDni()',
      "Activating default ${b(state.dfltDni)}, which deacivates dni: ${b(dni)}"
    )
    isStateChange = _pbsgActivateDni(state.dfltDni)
  }
  return isStateChange
}

String _childVswStates (Boolean includeHeading = false) {
  List<String> results = []
  if (includeHeading) { results += Heading2('VSW States') }
  getChildDevices().each{ d ->
    if (SwitchState(d) == 'on') {
      results += Bullet2("<b>${d.getDeviceNetworkId()}: on</b>")
    } else {
      results += Bullet2("<i>${d.getDeviceNetworkId()}: off</i>")
    }
  }
  return results.join(', ')
}

void _tracePbsgStateAndVswState(String fnName) {
  Ltrace(
    fnName,
    [
      "<table><tr>",
      "<td>${appStateAsBullets(true).join('<br/>')}</td>",
      "<td>${_childVswStates(true).join('<br/>')}</td>",
      "</tr></table"
    ].join()
  )
}

void _adjustVsws () {
  if (state.activeDni) {
    // Make sure the correct VSW is on
    DevW onDevice = getChildDevice(state.activeDni)
    if (SwitchState(onDevice) != 'on') {
      Linfo('_adjustVsws()', "Turning on VSW ${state.activeDni}")
      onDevice.on()
    } else {
      Ltrace('_adjustVsws()', "VSW ${state.activeDni} is already on")
    }
  }
  // Make sure other VSWs are off
  state.inactiveDnis.each{ offDni ->
    DevW offDevice = getChildDevice(offDni)
    if (SwitchState(offDevice) != 'off') {
      Linfo('_adjustVsw()', "Turning off VSW ${offDni}")
      offDevice.off()
    } else {
      Ltrace('_adjustVsws()', "VSW ${offDni} is already off")
    }
  }
}

//--> CALLING "EXPECTED" PARENT FUNCTION _buttonOnCallback(button)
//--> IN LIEU OF SENDING AN EVENT DUE TO HUBITAT SUBSCRIPTION ISSUES.
//--> void _pbsgAdjustVswsAndSendEvent() {
void _pbsgPublishOnButton() {
  _tracePbsgStateAndVswState('_pbsgPublishOnButton() AT ENTRY')
  String activeButton = _dniToButton(state.activeDni)
  Linfo('_pbsgPublishOnButton()', "Processing button ${activeButton}")
  //-> List<String> inactiveButtonFifo = state.inactiveDnis.collect{
  //->   _dniToButton(it)
  //-> }
  //-> String defaultButton = _dniToButton(state.dfltDni)
  //-> Map event = [
  //->   name: 'PbsgActiveButton',                             // String
  //->   descriptionText: "Button ${activeButton} is active",  // String
  //->   value: [
  //->     'active': activeButton,                             // String
  //->     'inactive': inactiveButtonFifo,                     // List<String>
  //->     'dflt': defaultButton                               // String
  //->   ]
  //-> ]
  //-> Linfo('_pbsgAdjustVswsAndSendEvent()', [
  //->   '<b>EVENT MAP</b>',
  //->   Bullet2("<b>name:</b> ${event.name}"),
  //->   Bullet2("<b>descriptionText:</b> ${event.descriptionText}"),
  //->   Bullet2("<b>value.active:</b> ${event.value['active']}"),
  //->   Bullet2("<b>value.inactive:</b> ${event.value['inactive']}"),
  //->   Bullet2("<b>value.dflt:</b> ${event.value['dflt']}")
  //-> ])
  _adjustVsws()
  parent._buttonOnCallback(activeButton)
  //-> Broadcast the state change to subscribers
  //-> sendEvent(event)
}

List<String> _pbsgGetDnis () {
  return cleanStrings([ state.activeDni, *state.inactiveDnis ])
}

Boolean _pbsgMoveActiveToInactive () {
  // Return TRUE on a configuration change, FALSE otherwise.
  // This method DOES NOT (1) activate a dfltDni OR (2) publish an event change
  Boolean isStateChanged = false
  if (state.activeDni) {
    Ldebug(
      '_pbsgMoveActiveToInactive()',
      "Pushing ${b(state.activeDni)} onto inactiveDnis (${state.inactiveDnis})"
    )
    isStateChanged = true
    state.inactiveDnis = [state.activeDni, *state.inactiveDnis]
    state.activeDni = null
    _tracePbsgStateAndVswState('_pbsgMoveActiveToInactive() AFTER MOVE')
  }
  return isStateChanged
}

List<String> _pbsgListVswDevices () {
  List<String> outputText = [ Heading2('DEVICES') ]
  List<InstAppW> devices = getChildDevices()
  devices.each{ d -> outputText += Bullet2(d.getDeviceNetworkId()) }
  return outputText
}

//---- SYSTEM CALLBACKS
//----   The downstream instantiations of this library should include
//----   the System callbacks - installed(), updated(), uninstalled().
//----   Those downstream methods should call the following methods,
//----     pbsgCoreInstalled(app)
//----     pbsgCoreUpdated(app)
//----     pbsgCoreUninstalled(app)

void pbsgCoreInstalled (InstAppW app) {
  // Called on instance creation - i.e., before configuration, etc.
  state.logLevel = LogThresholdToLogLevel('TRACE')  // Integer
  state.activeDni = null                            // String
  state.inactiveDnis = []                           // List<String>
  state.dfltDni = null                              // String
  trace('pbsgCoreInstalled()', appStateAsBullets(true))
}

void pbsgCoreUpdated (InstAppW app) {
  // Values are provided via these settings:
  //   - settings.buttons
  //   - settings.dfltButton
  //   - settings.activeButton
  //   - settings.logLevel
  // PROCESS SETTINGS (BUTTONS) INTO TARGET VSW DNIS
  List<String> prevDnis = _pbsgGetDnis() ?: []
  updatedDnis = settings.buttons.collect{ _buttonToDni(it) }
  updatedDfltDni = settings.dfltButton ? _buttonToDni(settings.dfltButton) : null
  updatedActiveDni = settings.activeButton ? _buttonToDni(settings.activeButton) : null
  // DETERMINE REQUIRED ADJUSTMENTS BY TYPE
  state.logLevel = LogThresholdToLogLevel(settings.logLevel)
  Map<String, List<String>> actions = CompareLists(prevDnis, updatedDnis)
  List<String> retainDnis = actions.retained // Used for accounting only
  List<String> dropDnis = actions.dropped
  List<String> addDnis = actions.added
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
  Linfo('pbsgCoreUpdated()', [
    [
      '<table style="border-spacing: 0px;" rules="all"><tr>',
      '<th>ENTRY STATE</th><th style="width:3%"/>',
      '<th>CONFIGURATION PARAMETERS</th><th style="width:3%"/>',
      '<th>REQUIRED ACTIONS</th>',
      '</tr><tr>'
    ].join(),
    "<td>${appStateAsBullets(true).join('<br/>')}</td><td/>",
    "<td>${requested}</td><td/>",
    "<td>${analysis}</td></tr></table>"
  ])
  // Suspend ALL events, irrespective of type
  unsubscribe()
  state.dfltDni = updatedDfltDni
  dropDnis.each{ dni -> _dropDni(dni) }
  addDnis.each{ dni -> _addDni(dni) }
  // Leverage activation/deactivation methods for initial dni activation.
  if (updatedActiveDni) {
    Ltrace('pbsgCoreUpdated()', "activating activeDni ${updatedActiveDni}")
    _pbsgActivateDni(updatedActiveDni)
  } else if (state.activeDni == null && state.dfltDni) {
    Ltrace('pbsgCoreUpdated()', "activating dfltDni ${state.dfltDni}")
    _pbsgActivateDni(state.dfltDni)
  }
  Ltrace('pbsgCoreUpdated()', _pbsgListVswDevices())
  List<DevW> childDevices = getChildDevices()
  // Avoid the List version of subscribe. It seems flaky.
  //-> subscribe(childDevices, VswEventHandler, ['filterEvents': true])
  childDevices.each{ d ->
    Ltrace('pbsgCoreUpdated()', "Subscribing ${d} to VswEventHandler")
    subscribe(d, VswEventHandler, ['filterEvents': true])
  }
  // Reconcile the PBSG / Child VSW state AND publish a first event.
  _pbsgPublishOnButton()
}

void pbsgCoreUninstalled (InstAppW app) {
  Ldebug('pbsgCoreUninstalled()', 'No action')
}

//---- EVENT HANDLERS

void VswEventHandler (Event e) {
  // Design Notes
  //   - Events can arise from:
  //       1. Methods in this App that change state
  //       2. Manual manipulation of VSWs (via dashboards or directly)
  //       3. Remote manipulation of VSWs (via Amazon Alexa)
  //   - Let downstream functions discard redundant state information
  // ==================================================================
  // == PREFIX APP METHODS WITH 'app.'                               ==
  // ==                                                              ==
  // == This IS NOT an instance method; so, there is no implied app. ==
  // == This IS a standalone method !!!                              ==
  // ==================================================================
  app._tracePbsgStateAndVswState('VswEventHandler() AT ENTRY')
  Ldebug('VswEventHandler()', e.descriptionText)
  if (e.isStateChange) {
    String dni = e.displayName
    if (e.value == 'on') {
      app._pbsgActivateDni(dni)
    } else if (e.value == 'off') {
      app._pbsgDeactivateDni(dni)
    } else {
      Ldebug(
        'VswEventHandler()',
        "Unexpected value (${e.value}) for DNI (${dni}")
    }
  } else {
    Ldebug('VswEventHandler()', "Unexpected event: ${EventDetails(e)}")
  }
}
