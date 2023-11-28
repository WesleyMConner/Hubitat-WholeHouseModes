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
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
#include wesmc.libFifo
#include wesmc.libHubExt
#include wesmc.libHubUI

definition (
  name: 'PbsgCore',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Implement PBSG functions and is authoritative for pushbutton state.',
  category: '',    // Not supported as of Q3'23
  iconUrl: '',     // Not supported as of Q3'23
  iconX2Url: '',   // Not supported as of Q3'23
  iconX3Url: '',   // Not supported as of Q3'23
  singleInstance: true
)

preferences {
  page(name: 'PbsgPage')
}

//---- CORE METHODS (External)

void pbsgConfigure (List<String> buttons, String defaultButton, String activeButton) {
  settings.dnis = cleanStrings(buttons).collect{ _buttonToDni(it) }
  settings.dfltDni = defaultButton ? _buttonToDni(defaultButton) : null
  settings.activeDni = activeButton ? _buttonToDni(activeButton) : null
  updated()
}

Boolean pbsgActivateButton (String button) {
  Ltrace('pbsgActivateButton()', [
    "Called for button: ${b(button)}",
    appStateAsBullets()
  ])
  _pbsgActivateDni(_buttonToDni(button))
}

Boolean pbsgDeactivateButton (String button) {
  Ltrace('pbsgDeactivateButton()', [
    "Called for button: ${b(button)}",
    appStateAsBullets()
  ])
  _pbsgDeactivateDni(_buttonToDni(button))
}

Boolean pbsgActivatePredecessor () {
  Ltrace('pbsgActivatePredecessor()', appStateAsBullets())
  return _pbsgActivateDni(state.inactiveDnis.first())
}

//---- CORE METHODS (Internal)

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
  // Make sure the correct VSW is on
  DevW onDevice = app.getChildDevice(state.activeDni)
  if (SwitchState(onDevice) != 'on') {
    Ltrace('_adjustVsw()', "Turning on VSW ${state.activeDni}")
    onDevice.on()
  }
  // Make sure other VSWs are off
  state.inactiveDnis.each{ offDevice ->
    if (SwitchState(offDevice) != 'off') {
      Ltrace('_adjustVsw()', "Turning on VSW ${state.activeDni}")
      offDevice.off()
    }
  }
}

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
    appStateAsBullets()
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
    _pbsgSendEvent()
  }
  return isStateChanged
}

Boolean _pbsgDeactivateDni (String dni) {
  // Return TRUE on a configuration change, FALSE otherwise.
  Boolean isStateChanged = false
Ldebug('_pbsgDeactivateDni()', [
  "Received dni: ${b(dni)}",
  "inactive contains dni?: ${state.inactiveDnis.contains(dni)}",
  appStateAsBullets()
])
  if (state.inactiveDnis.contains(dni)) {
Ldebug('_pbsgDeactivateDni()', "Nothing to do for dni: ${b(dni)}")
    // Nothing to do, dni is already inactive
  } else if (state.activeDni == state.dfltDni) {
    Linfo(
      '_pbsgDeactivateDni()',
      "Ignoring attempt to deactivate the dflt dni (${state.dfltDni})"
    )
  } else {
Ldebug('_pbsgDeactivateDni()', "Activating default ${b(state.dfltDni)}, which deacivates dni: ${b(dni)}")
    isStateChange = _pbsgActivateDni(state.dfltDni)
  }
  return isStateChange
}

void _pbsgSendEvent() {
  Map<String, String> event = [
    name: 'PbsgActiveButton',
    descriptionText: "Button ${state.activeDni} is active",
    value: [
      'active': _dniToButton(state.activeDni),
      'inactive': state.inactiveDnis.collect{ _dniToButton(it) },
      'dflt': _dniToButton(state.dfltDni)
    ]
  ]
  Linfo('_pbsgSendEvent()', [
    '<b>EVENT MAP</b>',
    Bullet2("<b>name:</b> ${event.name}"),
    Bullet2("<b>descriptionText:</b> ${event.descriptionText}"),
    Bullet2("<b>value.active:</b> ${event.value['active']}"),
    Bullet2("<b>value.inactive:</b> ${event.value['inactive']}"),
    Bullet2("<b>value.dflt:</b> ${event.value['dflt']}")
  ])
  // Update the state of child devices
  _childVswStates()
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
      "Button ${b(dni)} pushed onto inactiveDnis ${state.inactiveDnis}"
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

void installed () {
  // Called on instance creation - i.e., before configuration, etc.
  state.logLevel = LogThresholdToLogLevel('TRACE')  // Integer
  state.activeDni = null                         // String
  state.inactiveDnis = []                        // List<String>
  state.dfltDni = null                           // String
  Linfo('installed()', appStateAsBullets())
  Ltrace('installed()', 'Calling TEST_pbsgCoreFunctionality()')
  TEST_pbsgCoreFunctionality()
}

void updated () {
  // New values are passed into this method as :
  //   - settings.dnis
  //   - settings.dfltDni
  //   - settings.activeDni
  Ltrace('updated()', 'At entry')
  // INSPECT SETTINGS FOR VALIDITY AND ISSUES
  List<String> cleanedDnis = cleanStrings(settings.dnis)
  if (cleanedDnis != settings.dnis) {
    Lwarn('updated()', "settings.dnis: >${settings.dnis}< -> >${cleanedDnis}<")
  }
  String dfltDni = settings.dfltDni ?: null
  if (dfltDni != settings.dfltDni) {
    Lwarn('updated()', "dfltDni: >${settings.dfltDni}< -> >${dfltDni}<")
    dfltDni = dfltDni ?: null
  }
  String activeDni = settings.activeDni ?: null
  if (activeDni != settings.activeDni) {
    Lwarn('updated()', "settings.activeDni: >${settings.activeDni}< -> ${activeDni}<")
    activeDni = activeDni ?: null
  }
  if (cleanedDnis.size() < 2) {
    Lerror('updated()', "settings.dnis count (${cleanedDnis.size()}) must be >= 2")
    return
  }
  if (dfltDni && cleanedDnis.contains(dfltDni) == false) {
    Lerror('updated()', "dfltDni ${b(dfltDni)} not found in DNIs (${cleanedDnis})")
    return
  }
  if (activeDni && cleanedDnis.contains(activeDni) == false) {
    Lerror('updated()', "activeDni ${b(activeDni)} not found in DNIs (${cleanedDnis})")
    return
  }
  // ASSESS THE NET IMPACT OF SETTINGS ON APPLICATION STATE
  List<String> prevDnis = _pbsgGetDnis() ?: []
  Map<String, List<String>> actions = CompareLists(prevDnis, cleanedDnis)
  List<String> retainDnis = actions.retained // Used for accounting only
  List<String> dropDnis = actions.dropped
  List<String> addDnis = actions.added
  String requested = [
    "<b>dnis:</b> ${cleanedDnis}",
    "<b>dfltDni:</b> ${dfltDni}",
    "<b>activeDni:</b> ${activeDni}"
  ].join('<br/>')
  String analysis = [
    "<b>prevDnis:</b> ${prevDnis}",        //  ?: 'n/a'
    "<b>retainDnis:</b> ${retainDnis}",
    "<b>dropDnis:</b> ${dropDnis}",
    "<b>addDnis:</b> ${addDnis}"
  ].join('<br/>')
  Linfo('updated()', [
    '<table style="border-spacing: 0px;" rules="all"><tr>',
    '<th>STATE</th><th style="width:3%"/>',
    '<th>Input Parameters</th><th style="width:3%"/>',
    '<th>Action Summary</th>',
    '</tr><tr>',
    "<td>${appStateAsBullets().join('<br/>')}</td><td/>",
    "<td>${requested}</td><td/>",
    "<td>${analysis}</td></tr></table>"
  ])
  // Suspend ALL events, irrespective of type
  unsubscribe()
  // ADJUST APPLICATION STATE TO MATCH THE PBSG CONFIGURATION (IF REVISED)
  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
  String PBSG_LOG_LEVEL = 'TRACE' // Heavy debug w/ 'TRACE'
  state.logLevel = LogThresholdToLogLevel(PBSG_LOG_LEVEL)
  state.dfltDni = dfltDni
  dropDnis.each{ dni -> _dropDni(dni) }
  addDnis.each{ dni -> _addDni(dni) }
  // Leverage activation/deactivation methods for initial dni activation.
  if (activeDni) {
    Ltrace('updated()', "activating activeDni ${activeDni}")
    _pbsgActivateDni(activeDni)
  } else if (state.activeDni == null && state.dfltDni) {
    Ltrace('updated()', "activating dfltDni ${state.dfltDni}")
    _pbsgActivateDni(state.dfltDni)
  }
  Ltrace('updated()', _pbsgListVswDevices())
  List<DevW> childDevices = app.getChildDevices()
  childDevices.each{ d ->
    app.subscribe(d, VswEventHandler, ['filterEvents': true])
  }
  //app.subscribe(childDevices, VswEventHandler, ['filterEvents': true])
}

void uninstalled () {
  Ldebug('uninstalled()', 'No action')
}

//---- RENDERING AND DISPLAY

Map PbsgPage () {
  return dynamicPage(
    name: 'PbsgPage',
    title: Heading1(AppInfo(app)),
    install: true,
    uninstall: true,
  ) {
    section {
      paragraph "YOU ARE HERE"
    }
  }
}

void VswEventHandler (Event e) {
  // Design Notes
  //--> //   - VSWs are turned on/off as a part of _vswUpdateState().
  //   - Hubitat Dashboard and Alexa can also turn on/off VSWs.
  //   - Tactically, process ALL events:
  //       - Events that report VSW state consistent with current dnis
  //         are suppressed downstream.
  //       - Allow race conditions to "play through" without manipulating
  //         subscriptions.
  // IMPORTANT
  //   - This IS NOT an instance method. It IS a standalone method; so,
  //     there IS NO IMPLIED app. You have to invoke it via 'app.'
  Linfo('VswEventHandler()', [
    e.descriptionText,
    AppInfo(app),
    AppStateAsBullets(app),
    _childVswStates()
  ])
  if (e.isStateChange) {
    String dni = e.displayName
    if (e.value == 'on') {
      _pbsgActivateDni(dni)
    } else if (e.value == 'off') {
      _pbsgDeactivateDni(dni)
    } else {
      Ldebug(
        'VswEventHandler()',
        "Unexpected value (${e.value}) for DNI (${dni}")
    }
  } else {
    Ldebug('VswEventHandler()', "Unexpected event: ${EventDetails(e)}")
  }
}

//---- TEST SUPPORT

void TEST_pbsgConfigure (
    Integer n,
    List<String> list,
    String dflt,
    String on,
    String forcedError = null
  ) {
  // Logs display newest to oldest; so, write logs backwards
  List<String> logMsg = []
  if (forcedError) logMsg += forcedError
  logMsg += "dnis=${b(list)}, dfltButton=${b(dflt)}, activeDni=${b(on)}"
  logMsg += (forcedError ? REDBAR() : GREENBAR())
  Linfo("TEST ${n} CONFIG", logMsg)

  // Simulate a Page update (GUI settings) and System updated() callback.
  pbsgConfigure(list, dflt, on)
}

void TEST_PbsgActivation (
    Integer n,
    String description,
    String forcedError = null
  ){
  // Logs display newest to oldest; so, write logs backwards
  List<String> logMsg = []
  logMsg += description
  if (forcedError) { logMsg += forcedError }
  logMsg += (forcedError ? REDBAR() : GREENBAR())
  Linfo("TEST ${n} ACTION", logMsg)
}

String TEST_pbsgHasExpectedState (
    String activeButton,
    List<String> inactiveButtons,
    String dfltButton
  ) {
  String activeDni = activeButton ? _buttonToDni(activeButton) : null
  List<String> inactiveDnis = inactiveButtons ? inactiveButtons.collect{ _buttonToDni(it) } : null
  String dfltDni = dfltButton ? _buttonToDni(dfltButton) : null
  Boolean result = true
  Integer actualInactiveDnisSize = state.inactiveDnis?.size() ?: 0
  Integer expectedInactiveDnisSize = inactiveDnis?.size() ?: 0
  if (state.dfltDni != dfltDni) {
    result = false
    Linfo(
      'TEST_pbsgHasExpectedState()',
      "dfltDni ${state.dfltDni} != ${dfltDni}"
    )
  } else if (state.activeDni != activeDni) {
    result = false
    Linfo(
      'TEST_pbsgHasExpectedState()',
      "activeDni ${state.activeDni} != ${activeDni}"
    )
  } else if (actualInactiveDnisSize != expectedInactiveDnisSize) {
    result = false
    Linfo(
      'TEST_pbsgHasExpectedState()',
      [
        "inActiveDnis size ${actualInactiveDnisSize} != ${expectedInactiveDnisSize}",
        "expected: ${inactiveDnis }} got: ${state.inactiveDnis}"
      ]
    )
  } else {
    state.inactiveDnis.eachWithIndex{ dni, index ->
      String expectedDni = inactiveDnis[index]
      if (dni != expectedDni) {
        result = false
        Linfo(
          'TEST_pbsgHasExpectedState()',
          "At ${index}: inactiveDni ${dni} != ${expectedDni}"
        )
      }
    }
  }
  List<String> results = [result ? 'true' : '<b>FALSE</b>']
  if (state.activeDni == activeDni) {
    results += "<i>activeDni: ${state.activeDni}</i>"
  } else {
    results += "<i>activeDni: ${state.activeDni}</i> => <b>expected: ${activeDni}</b>"
  }
  if ((state.inactiveDnis == inactiveDnis) || (!state.inactiveDnis && !inactiveDnis)) {
    results += "<i>inactiveDnis: ${state.inactiveDnis}</i>"
  } else {
    results += "<i>inactiveDnis:</b> ${state.inactiveDnis}</i> => <b>expected: ${inactiveDnis}</b>"
  }
  if(state.dfltDni == dfltDni) {
    results += "<i>dfltDni: ${state.dfltDni}</i>"
  } else {
    results += "<i>dfltDni: ${state.dfltDni}</i> => <b>expected: ${dfltDni}</b>"
  }
  return results.join('<br/>')
}

void TEST_pbsgCoreFunctionality () {
  String parkLogLevel = state.logLevel
  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
  state.logLevel = LogThresholdToLogLevel('TRACE')  // Heavy debug w/ 'TRACE'

  //-> FifoTest()

  //----
  TEST_pbsgConfigure(1, [], 'A', 'B', '<b>Forced Error:</b> "No dnis"')
  Linfo('TEST1', TEST_pbsgHasExpectedState(null, [], null))
  //----
  TEST_pbsgConfigure(2, ['A', 'B', 'C', 'D', 'E'], '', null)
  Linfo('TEST2', TEST_pbsgHasExpectedState(null, ['A', 'B', 'C', 'D', 'E'], null))
  //----
  TEST_pbsgConfigure(3, ['A', 'B', 'C', 'D', 'E'], 'B', null)
  Linfo('TEST3', TEST_pbsgHasExpectedState('B', ['A', 'C', 'D', 'E'], 'B'))
  //----
  TEST_pbsgConfigure(4, ['A', 'C', 'D', 'E'], 'B', null, '<b>Forced Error:</b> "Default not in DNIs"')
  // TEST4 state is unchanged from TEST3 state
  Linfo('TEST4', TEST_pbsgHasExpectedState('B', ['A', 'C', 'D', 'E'], 'B'))
  //----
  TEST_pbsgConfigure(5, ['B', 'C', 'D', 'E', 'F'], '', 'C')
  Linfo('TEST5', TEST_pbsgHasExpectedState('C', ['B', 'D', 'E', 'F'], null))
  //----
  TEST_pbsgConfigure(6, ['B', 'F', 'G', 'I'], 'B', 'D', '<b>Forced Error:</b> "Active not in DNIs"')
  // TEST6 state is unchanged from TEST5 state
  Linfo('TEST6', TEST_pbsgHasExpectedState('C', ['B', 'D', 'E', 'F'], null))
  //----
  TEST_pbsgConfigure(7, ['B', 'F', 'G', 'I'], 'B', 'G')
  Linfo('TEST7', TEST_pbsgHasExpectedState('G', ['B', 'F', 'I'], 'B'))
  //----
  // WITHOUT CHANGING THE CONFIGURATION, START TESTING ACTIVATION OF BUTTONS
  // THE DEFAULT BUTTON REMAINS 'B'
  //----
  TEST_PbsgActivation(8, "With 'G', ['*B', 'F', 'I'], Activate F")
  pbsgActivateButton('F')
  Linfo('TEST8', TEST_pbsgHasExpectedState('F', ['G', 'B', 'I'], 'B'))
  //----
  TEST_PbsgActivation(9, "With 'F', ['G', '*B', 'I'], Activate Q", '<b>Forced Error:</b> "Button does not exist"')
  pbsgActivateButton('Q')
  // TEST9 state is unchanged from TEST8 state
  Linfo('TEST9', TEST_pbsgHasExpectedState('F', ['G', 'B', 'I'], 'B'))
  //----
  TEST_PbsgActivation(10, "With 'F', ['G', '*B', 'I'], Deactivate F")
  pbsgDeactivateButton('F')
  Linfo('TEST10', TEST_pbsgHasExpectedState('B', ['F', 'G', 'I'], 'B'))
  //----
  TEST_PbsgActivation(11, "With '*B', ['F', 'G', 'I'], Activate I")
  pbsgActivateButton('I')
  Linfo('TEST11', TEST_pbsgHasExpectedState('I', ['B', 'F', 'G'], 'B'))
  //----
  TEST_PbsgActivation(12, "With 'I', ['*B', 'F', 'G'], Activate Predecessor")
  pbsgActivatePredecessor()
  Linfo('TEST12', TEST_pbsgHasExpectedState('B', ['I', 'F', 'G'], 'B'))
  //----
  TEST_pbsgConfigure(13, ['B', 'X', 'C', 'E', 'Z'], '', 'C')
  Linfo('TEST13', TEST_pbsgHasExpectedState('C', ['B', 'X', 'E', 'Z'], null))
  //----
  TEST_PbsgActivation(14, "With 'C', ['B', 'X', 'E', 'Z'], Deactivate C")
  pbsgDeactivateButton('C')
  Linfo('TEST14', TEST_pbsgHasExpectedState(null, ['C', 'B', 'X', 'E', 'Z'], null))
  //----
  TEST_PbsgActivation(15, "With null, ['C', 'B', 'X', 'E', 'Z'], Activate Predecessor")
  pbsgActivatePredecessor()
  Linfo('TEST15', TEST_pbsgHasExpectedState('C', ['B', 'X', 'E', 'Z'], null))
  //----
  TEST_pbsgConfigure(16, ['B', '', null, 'A', 'G', 'X', null, 'A'], 'X', '')
  Linfo('TEST16', TEST_pbsgHasExpectedState('X', ['B', 'A', 'G'], 'X'))
  //----
  TEST_pbsgConfigure(17, ['B', 'A', 'G', 'X'], 'X', 'G')
  Linfo('TEST17', TEST_pbsgHasExpectedState('G', ['X', 'B', 'A'], 'X'))
  //----
  state.logLevel = parkLogLevel
}
