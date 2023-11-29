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
    String pbsgLogLevel = 'TRACE' // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
  ) {
  // Returns true if configuration is accepted, false otherwise.
  Boolean retVal = true
Ltrace('pbsgConfig() parms', [
  '',
  "buttons: ${buttons}",
  "defaultButton: ${defaultButton}",
  "activeButton: ${activeButton}",
  "pbsgLogLevel: ${pbsgLogLevel}"
])
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
Ltrace('pbsgConfig() at updated() call', [
  "retVal: ${retVal}",
  "settings.buttons: ${settings.buttons}",
  "settings.dfltButton: ${settings.dfltButton}",
  "settings.activeButton: ${settings.activeButton}",
  "settings.logLevel: ${settings.logLevel}",
  *appStateAsBullets()
])
  if (retVal) updated()
  return retVal
}

Boolean pbsgActivateButton (String button) {
  //-> Ltrace('pbsgActivateButton()', [
  //->   "Called for button: ${b(button)}",
  //->   *appStateAsBullets()
  //-> ])
  _pbsgActivateDni(_buttonToDni(button))
}

Boolean pbsgDeactivateButton (String button) {
  //-> Ltrace('pbsgDeactivateButton()', [
  //->   "Called for button: ${b(button)}",
  //->   *appStateAsBullets()
  //-> ])
  _pbsgDeactivateDni(_buttonToDni(button))
}

Boolean pbsgActivatePredecessor () {
  Ltrace('pbsgActivatePredecessor()', appStateAsBullets(true))
  return _pbsgActivateDni(state.inactiveDnis.first())
}

//---- CORE METHODS (Internal)

String _buttonToDni (String button) {
  return "${app.getLabel()}_${app.getId()}_${button}"
}

String _dniToButton (String dni) {
  return dni ? dni.substring("${app.getLabel()}_${app.getId()}_".length()) : null
}

void _addDni (String dni) {
  if (dni) {
    if (!state.inactiveDnis) state.inactiveDnis = []
    FifoEnqueue(state.inactiveDnis, dni)
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
  deleteChildDevice(dni)
}

Boolean _pbsgActivateDni (String dni) {
  // Return TRUE on a configuration change, FALSE otherwise.
  // Publish an event ONLY IF/WHEN a new dni is activated.
  Ltrace('_pbsgActivateDni()', [
    "DNI: ${b(dni)}",
    *appStateAsBullets()
  ])
  Boolean isStateChanged = false
  if (state.activeDni == dni) {
    // Nothing to do, dni is already active
  } else if (dni && !_pbsgGetDnis()?.contains(dni)) {
    Lerror(
      '_pbsgActivateDni()',
      "DNI >${dni}< does not exist in >${_pbsgGetDnis()}<"
    )
  } else {
    isStateChanged = true
    _pbsgIfActiveDniPushOntoInactiveFifo()
    FifoRemove(state.inactiveDnis, dni)
    // Adjust the activeDni and Vsw together
    state.activeDni = dni
    _pbsgAdjustVswsAndSendEvent()
  }
  return isStateChanged
}

Boolean _pbsgDeactivateDni (String dni) {
  // Return TRUE on a configuration change, FALSE otherwise.
  Ltrace('_pbsgDeactivateDni()', [
    "DNI: ${b(dni)}",
    *appStateAsBullets()
  ])
  Boolean isStateChanged = false
  Ltrace('_pbsgDeactivateDni()', [ "Received dni: ${b(dni)}", *appStateAsBullets() ])
  if (state.inactiveDnis.contains(dni)) {
    // Nothing to do, dni is already inactive
    Ltrace('_pbsgDeactivateDni()', "Nothing to do for dni: ${b(dni)}")
  } else if (state.activeDni == state.dfltDni) {
    // It is likely that the default DNI has been manually turned off.
    // Update the PBSG state to reflect this likely fact.
    _pbsgIfActiveDniPushOntoInactiveFifo()
    // Force re-enable the default DNI.
    isStateChange = _pbsgActivateDni(state.dfltDni)
  } else {
    Linfo(
      '_pbsgDeactivateDni()',
      "Activating default ${b(state.dfltDni)}, which deacivates dni: ${b(dni)}"
    )
    isStateChange = _pbsgActivateDni(state.dfltDni)
  }
  return isStateChange
}

String _childVswStates () {
  List<String> results = []
  app.getChildDevices().each{ d ->
    if (SwitchState(d) == 'on') {
      results += "<b>${d.getDeviceNetworkId()}: on</b>"
    } else {
      results += "<i>${d.getDeviceNetworkId()}: off</i>"
    }
  }
  return results.join(', ')
}

void _adjustVsws () {
  if (state.activeDni) {
    // Make sure the correct VSW is on
    DevW onDevice = app.getChildDevice(state.activeDni)
    if (SwitchState(onDevice) != 'on') {
      Linfo('_adjustVsws()', "Turning on VSW ${state.activeDni}")
      onDevice.on()
    }
  }
  // Make sure other VSWs are off
  state.inactiveDnis.each{ offDni ->
    DevW offDevice = app.getChildDevice(offDni)
    if (SwitchState(offDevice) != 'off') {
      Linfo('_adjustVsw()', "Turning off VSW ${offDni}")
      offDevice.off()
    }
  }
}

void _pbsgAdjustVswsAndSendEvent() {
  Map<String, String> event = [
    name: 'PbsgActiveButton',
    descriptionText: "Button ${state.activeDni} is active",
    value: [
      'active': _dniToButton(state.activeDni),
      'inactive': state.inactiveDnis.collect{ _dniToButton(it) },
      'dflt': _dniToButton(state.dfltDni)
    ]
  ]
  Linfo('_pbsgAdjustVswsAndSendEvent()', [
    '<b>EVENT MAP</b>',
    Bullet2("<b>name:</b> ${event.name}"),
    Bullet2("<b>descriptionText:</b> ${event.descriptionText}"),
    Bullet2("<b>value.active:</b> ${event.value['active']}"),
    Bullet2("<b>value.inactive:</b> ${event.value['inactive']}"),
    Bullet2("<b>value.dflt:</b> ${event.value['dflt']}")
  ])
  // Update the state of child devices
  _adjustVsws()
  // Broadcast the state change to subscribers
  sendEvent(event)
}

List<String> _pbsgGetDnis () {
  return cleanStrings([ state.activeDni, *state.inactiveDnis ])
}

Boolean _pbsgIfActiveDniPushOntoInactiveFifo () {
  // Return TRUE on a configuration change, FALSE otherwise.
  // This method DOES NOT (1) activate a dfltDni OR (2) publish an event change
  Boolean isStateChanged = false
  String dni = state.activeDni
  if (dni) {
    isStateChanged = true
    // Adjust inactiveDnis, activeDni and Vsw together
    state.inactiveDnis = [dni, *state.inactiveDnis]
    state.activeDni = null
    Ltrace(
      '_pbsgIfActiveDniPushOntoInactiveFifo()',
      "DNI ${b(dni)} pushed onto inactiveDnis ${state.inactiveDnis}"
    )
  }
  return isStateChanged
}

List<String> _pbsgListVswDevices () {
  List<String> outputText = [ Heading2('DEVICES') ]
  List<InstAppW> devices = app.getChildDevices()
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
  Linfo('pbsgCoreInstalled()', appStateAsBullets(true))
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
      '<th>STATE</th><th style="width:3%"/>',
      '<th>Input Parameters</th><th style="width:3%"/>',
      '<th>Action Summary</th>',
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
    Linfo('pbsgCoreUpdated()', "activating activeDni ${updatedActiveDni}")
    _pbsgActivateDni(updatedActiveDni)
  } else if (state.activeDni == null && state.dfltDni) {
    Linfo('pbsgCoreUpdated()', "activating dfltDni ${state.dfltDni}")
    _pbsgActivateDni(state.dfltDni)
  }
  Ltrace('pbsgCoreUpdated()', _pbsgListVswDevices())
  List<DevW> childDevices = app.getChildDevices()
  // Avoid the List version of app.subscribe. It seems flaky.
  //-> app.subscribe(childDevices, VswEventHandler, ['filterEvents': true])
  childDevices.each{ d ->
    Linfo('pbsgCoreUpdated()', "Subscribing ${d} to VswEventHandler")
    subscribe(d, VswEventHandler, ['filterEvents': true])
  }
  // Reconcile the PBSG / Child VSW state AND publish a first event.
  _pbsgAdjustVswsAndSendEvent()
}

void pbsgCoreUninstalled (InstAppW app) {
  Ldebug('pbsgCoreUninstalled()', 'No action')
}

//---- SUBSCRIPTION HANDLER

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
  Linfo('VswEventHandler()', [
    e.descriptionText,
    app.appStateAsBullets().join('<br/>'),
    app._childVswStates().join(', ')
  ])
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
