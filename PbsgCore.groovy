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





//---- CORE METHODS
//---- Methods that ARE NOT constrained to any specific execution context.

String buttonToDni (String button) {
  return "${app.getLabel()}_${app.getId()}_${button}"
}

String dniToButton (String dni) {
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
  //--xx-> Ldebug('_addDni() at exit', "state.inactiveDnis: >${state.inactiveDnis}<")
}

void _dropDni (String dni) {
  // Drop without enforcing Default DNI.
  if (state.activeDni == dni) state.activeDni = null
  else FifoRemove(state.inactiveDnis, dni)
  deleteChildDevice(dni)
}

void pbsgConfigure (List<String> buttons, String defaultButton, String activeDni) {
  settings.dnis = buttons.collect{ buttonToDni(it) }
  settings.dfltDni = defaultButton ? buttonToDni(defaultButton) : null
  settings.activeDni = activeDni ? buttonToDni(activeDni) : null
  updated()
}

Boolean pbsgActivateDni (String dni) {
  // Return TRUE on a configuration change, FALSE otherwise.
  // Publish an event ONLY IF/WHEN a new dni is activated.
  Ltrace('pbsgActivateDni()', "DNI: ${b(dni)}")
  Boolean isStateChanged = false
  if (state.activeDni == dni) {
    // Nothing to do, dni is already active
  } else if (dni && !_pbsgGetDnis()?.contains(dni)) {
    Lerror(
      'pbsgActivateDni()',
      "DNI >${dni}< does not exist in >${_pbsgGetDnis()}<"
    )
  } else {
    isStateChanged = true
    _pbsgIfActiveDniPushOntoInactiveFifo()
    FifoRemove(state.inactiveDnis, dni)
    state.activeDni = dni
    Ltrace('pbsgActivateDni()', [
      'About to call _pbsgSendEvent()',
      "activeDni: ${b(state.activeDni)}, inactiveDnis: ${b(state.inactiveDnis)}",
      AppStateAsBullets()
    ])
    _pbsgSendEvent()
  }
  return isStateChanged
}

Boolean pbsgDeactivateDni (String dni) {
  // Return TRUE on a configuration change, FALSE otherwise.
  Boolean isStateChanged = false
  if (state.inactiveDnis.contains(dni)) {
    // Nothing to do, dni is already inactive
  } else if (state.activeDni == state.dfltDni) {
    Linfo(
      'pbsgDeactivateDni()',
      "Ignoring attempt to deactivate the dflt dni (${state.dfltDni})"
    )
  } else {
    isStateChange = pbsgActivateDni(state.dfltDni)
  }
  return isStateChange
}

Boolean pbsgActivatePredecessor () {
  return pbsgActivateDni(state.inactiveDnis.first())
}

void _pbsgSendEvent() {
  Ltrace('_pbsgSendEvent()', [
    'At entry ...',
    "activeDni: ${b(state.activeDni)}, inactiveDnis: ${b(state.inactiveDnis)}",
    AppStateAsBullets()
  ])
  Map<String, String> event = [
    name: 'PbsgActiveButton',
    descriptionText: "Button ${state.activeDni} is active",
    value: [
      'active': dniToButton(state.activeDni),
      'inactive': state.inactiveDnis.collect{ dniToButton(it) },
      'dflt': dniToButton(state.dfltDni)
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
  //--> if (event.value?.dflt) { _vswUpdateState() }
  sendEvent(event)
}

List<String> _pbsgGetDnis () {
  return cleanStrings([ state.activeDni, *state.inactiveDnis ])
}

Boolean _pbsgIfActiveDniPushOntoInactiveFifo () {
  // Return TRUE on a configuration change, FALSE otherwise.
  // This method DOES NOT activate a dfltDni!
  Boolean isStateChanged = false
  String dni = state.activeDni
  if (dni) {
    isStateChanged = true
    state.inactiveDnis = [dni, *state.inactiveDnis]
    state.activeDni = null
    Ltrace(
      '_pbsgIfActiveDniPushOntoInactiveFifo()',
      "Button ${b(dni)} pushed onto inactiveDnis ${state.inactiveDnis}"
    )
    Ldebug('#160', getAllChildDevices())
    _vswTurnOff(dni)
    Ldebug('#162', getAllChildDevices())
  }
  return isStateChanged
}

//---- SYSTEM CALLBACKS
//---- Methods specific to this execution context

void installed () {
  // Called on instance creation - i.e., before configuration, etc.
  state.logLevel = LogThresholdToLogLevel('TRACE')  // Integer
  state.activeDni = null                         // String
  state.inactiveDnis = []                        // List<String>
  state.dfltDni = null                           // String
  Linfo('installed()', AppStateAsBullets())
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
    "<td>${AppStateAsBullets().join('<br/>')}</td><td/>",
    "<td>${requested}</td><td/>",
    "<td>${analysis}</td></tr></table>"
  ])
  // Suspend ALL child events (i.e., new AND old VSWs)
  unsubscribe()
  // ADJUST APPLICATION STATE TO MATCH THE PBSG CONFIGURATION (IF REVISED)
  String PBSG_LOG_LEVEL = 'TRACE'   // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
  state.logLevel = LogThresholdToLogLevel(PBSG_LOG_LEVEL)
  state.dfltDni = dfltDni
  dropDnis.each{ dni -> _dropDni(dni) }
  addDnis.each{ dni -> _addDni(dni) }
  // Leverage activation/deactivation methods for initial dni activation.
  if (activeDni) {
    Ltrace('updated()', "activating activeDni ${activeDni}")
    pbsgActivateDni(activeDni)
  } else if (state.activeDni == null && state.dfltDni) {
    Ltrace('updated()', "activating dfltDni ${state.dfltDni}")
    pbsgActivateDni(state.dfltDni)
  }
  Ltrace('updated()', _vswDevices())
  _vswSubscribe()
}

void uninstalled () {
  Ldebug('uninstalled()', 'No action')
}

//---- RENDERING AND DISPLAY
//----   Methods specific to this execution context

Map PbsgPage () {
  return dynamicPage(
    name: 'PbsgPage',
    title: Heading1(AppInfo(app)),
    install: true,
    uninstall: true,
  ) {
    String xIn = 'zebra'
    String xIntermediate = buttonToDni(xIn)
    String xOut = dniToButton(xIntermediate)
    section {
      paragraph "YOU ARE HERE"
      paragraph "in: >${xIn}<, intermediate: >${xIntermediate}<, out: >${xOut}<"
    }
  }
}

//---- VSW METHODS
//----   - Isolate PbsgVsw data and methods from PbsgCore data and methods.
//----   - Provide VSW functionality to a PBSG instance.
//----   - Minimize interaction between non-VSW and VSW methods, settings and state.

List<String> _vswDevices () {
  List<String> outputText = [ Heading2('DEVICES') ]
  List<InstAppW> devices = app.getChildDevices()
  devices.each{ d -> outputText += Bullet2(d.getDeviceNetworkId()) }
  return outputText
}

void _vswTurnOn (dni) {
  DevW device = app.getChildDevice(dni)
  if (device) {
    device.on()
  } else {
    Lerror('_vswTurnOn()', "For DNI (${dni}) w/ dni ${dni} no device in ${app.getChildDevices().collect{ it.getDeviceNetworkId() }}")
  }
}

void _vswTurnOff (dni) {
    Ldebug('#375', getAllChildDevices())
  DevW device = app.getChildDevice(dni)
  if (device) {
    device.off()
  } else {
    Lerror('_vswTurnOff()', "For DNI (${dni}) w/ dni ${dni} no device in ${app.getChildDevices().collect{ it.getDeviceNetworkId() }}")
  }
}

//-> Boolean _vswUnsubscribe () {
//->   unsubscribe('VswEventHandler')
//-> }

Boolean _vswSubscribe () {
  // Returns false if an issue arises during subscriptions
  Boolean issueArose = false
  _pbsgGetDnis().each{ dni ->
    DevW vsw = getChildDevice(dni)
    if (!vsw) {
      issueArose = true
      Lerror('_vswSubscribe()', "Cannot find ${b(dni)}")
      result = false
    } else {
      subscribe(vsw, VswEventHandler, ['filterEvents': true])
    }
  }
  return issueArose
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
  Ltrace('VswEventHandler()', e.descriptionText)
  if (e.isStateChange) {
    String dni = e.displayName
    if (e.value == 'on') {
      pbsgActivateDni(dni)
    } else if (e.value == 'off') {
      pbsgDeactivateDni(dni)
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
  String activeDni = activeButton ? buttonToDni(activeButton) : null
  List<String> inactiveDnis = inactiveButtons ? inactiveButtons.collect{ buttonToDni(it) } : null
  String dfltDni = dfltButton ? buttonToDni(dfltButton) : null
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
  state.logLevel = LogThresholdToLogLevel('TRACE')

  //-> FifoTest()

  //----
  String expectedActive = null
  List<String> expectedInactive = null
  Linfo('TEST_pbsgCoreFunctionality()', ['At Entry', AppStateAsBullets(), BLACKBAR()])
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
  TEST_pbsgConfigure(4, ['A', 'C', 'D', 'E'], 'B', null, '<b>Forced Error:</b> "Default not in Buttons"')
  // TEST4 state is unchanged from TEST3 state
  Linfo('TEST4', TEST_pbsgHasExpectedState('B', ['A', 'C', 'D', 'E'], 'B'))
  //----
  TEST_pbsgConfigure(5, ['B', 'C', 'D', 'E', 'F'], '', 'C')
  Linfo('TEST5', TEST_pbsgHasExpectedState('C', ['B', 'D', 'E', 'F'], null))
  //----
  TEST_pbsgConfigure(6, ['B', 'F', 'G', 'I'], 'B', 'D', '<b>Forced Error:</b> "Active not in Buttons"')
  // TEST6 state is unchanged from TEST5 state
  Linfo('TEST6', TEST_pbsgHasExpectedState('C', ['B', 'D', 'E', 'F'], null))
  //----
  TEST_pbsgConfigure(7, ['B', 'F', 'G', 'I'], 'B', 'G')
  Linfo('TEST7', TEST_pbsgHasExpectedState('G', ['B', 'F', 'I'], 'B'))
  /*
  //----
  // WITHOUT CHANGING THE CONFIGURATION, START TESTING ACTIVATION OF BUTTONS
  // THE DEFAULT BUTTON REMAINS 'B'
  //----
  TEST_PbsgActivation(8, 'Activate F')
  pbsgActivateDni('F')
  Linfo('TEST8', TEST_pbsgHasExpectedState('F', ['G', 'B', 'I'], 'B'))
  //----
  TEST_PbsgActivation(9, 'Activate Q', '<b>Forced Error:</b> "Button does not exist"')
  pbsgActivateDni('Q')
  // TEST9 state is unchanged from TEST8 state
  Linfo('TEST9', TEST_pbsgHasExpectedState('F', ['G', 'B', 'I'], 'B'))
  //----
  TEST_PbsgActivation(10, 'Deactivate F')
  pbsgDeactivateDni('F')
  Linfo('TEST10', TEST_pbsgHasExpectedState('B', ['F', 'G', 'I'], 'B'))
  //----
  TEST_PbsgActivation(11, 'Activate I')
  pbsgActivateDni('I')
  Linfo('TEST11', TEST_pbsgHasExpectedState('I', ['B', 'F', 'G'], 'B'))
  //----
  TEST_PbsgActivation(12, 'Activate Predecessor')
  pbsgActivatePredecessor()
  Linfo('TEST12', TEST_pbsgHasExpectedState('B', ['I', 'F', 'G'], 'B'))
  //----
  TEST_pbsgConfigure(13, ['B', 'X', 'C', 'E', 'Z'], '', 'C')
  Linfo('TEST13', TEST_pbsgHasExpectedState('C', ['B', 'X', 'E', 'Z'], null))
  //----
  TEST_PbsgActivation(14, 'Deactivate C')
  pbsgDeactivateDni('C')
  Linfo('TEST14', TEST_pbsgHasExpectedState(null, ['C', 'B', 'X', 'E', 'Z'], null))
  //----
  TEST_PbsgActivation(15, 'Activate Predecessor')
  pbsgActivatePredecessor()
  Linfo('TEST15', TEST_pbsgHasExpectedState('C', ['B', 'X', 'E', 'Z'], null))
  //----
  TEST_pbsgConfigure(16, ['B', '', null, 'A', 'G', 'X', null, 'A'], 'X', '')
  Linfo('TEST16', TEST_pbsgHasExpectedState('X', ['B', 'A', 'G'], 'X'))
  //----
  TEST_pbsgConfigure(17, ['B', 'A', 'G', 'X'], 'X', 'G')
  Linfo('TEST17', TEST_pbsgHasExpectedState('G', ['X', 'B', 'A', 'G'], 'X'))
  //----
  */
  state.logLevel = parkLogLevel
}
