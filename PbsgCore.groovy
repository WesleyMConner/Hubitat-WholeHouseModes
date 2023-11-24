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
import com.hubitat.app.InstalledAppWrapper as InstAppW
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

// ABSTRACT
//   - Isolate the core behavior of a PushButton Switch Group (PBSG).
//   - Differentiate core PBSG behavior (button state) from its VSWs (see libPbsgVsw).
//   - Minimize interaction between non-VSW and VSW methods, settings and state.

preferences {
  page(name: 'PbsgPage')
}

//---- CORE METHODS
//---- Methods that ARE NOT constrained to any specific execution context.

void _pbsgConfigure (List<String> buttons, String defaultButton, String activeButton) {
  settings.buttons = buttons
  settings.dfltButton = defaultButton
  settings.activeButton = activeButton
  updated()
}

Boolean pbsgActivateButton (String button) {
  // Return TRUE on a configuration change, FALSE otherwise.
  Boolean isStateChanged = false
  //====> if (!_pbsgGetButtons().contains(button)) {
  //====>   Lerror('pbsgActivateButton()', "Argument ${b(button)} IS NOT a button")
  //====> } else {
    isStateChange = _pbsgSafelyActivateButton(button)
  //====> }
  return isStateChanged
}

Boolean pbsgDeactivateButton (String button) {
  // Return TRUE on a configuration change, FALSE otherwise.
  Boolean isStateChanged = false
  if (state.activeButton == state.dfltButton) {
    Linfo(
      'pbsgDeactivateButton()',
      "Ignoring attempt to deactivate the dflt button (${state.dfltButton})"
    )
  } else {
    isStateChange = _pbsgSafelyActivateButton(state.dfltButton)
  }
  return isStateChange
}

Boolean pbsgActivatePredecessor () {
  return _pbsgSafelyActivateButton(state.inactiveButtons.first())
}

List<String> _pbsgState () {
  return [
    Heading2('STATE'),
    Bullet2("<b>logLevel:</b> ${state.logLevel}"),
    Bullet2("<b>activeButton:</b> ${state.activeButton}"),
    Bullet2("<b>inactiveButtons:</b> ${state.inactiveButtons}"),
    Bullet2("<b>dfltButton:</b> ${state.dfltButton}"),
    Bullet2("<b>vswDniPrefix:</b> ${state.vswDniPrefix}")
  ]
}

void _pbsgSendEvent() {
  Map<String, String> event = [
    name: 'PbsgActiveButton',
    descriptionText: "Button ${state.activeButton} is active",
    value: [
      'active': state.activeButton,
      'inactive': state.inactiveButtons,
      'dflt': state.dfltButton
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
  sendEvent(event)
}

List<String> _pbsgGetButtons () {
  return [ state.activeButton, *state.inactiveButtons ].findAll()
}

List<String> _pbsgDropButtons (List<String> dropButtons) {
  if (dropButtons) {
    Ltrace('_pbsgDropButtons()', "Buttons before: ${_pbsgGetButtons()}")
    // Dropping activeButton?
    if (dropButtons.contains(state.activeButton)) {
      state.activeButton = null
    }
    if (dropButtons.size() > 0) state.inactiveButtons.removeAll(dropButtons)
    Ltrace('_pbsgDropButtons()', "Buttons after: ${_pbsgGetButtons()}")
  }
}

Boolean _pbsgIfActiveButtonPushOntoInactiveFifo () {
  // Return TRUE on a configuration change, FALSE otherwise.
  // This method DOES NOT activate a dfltButton!
  Boolean isStateChanged = false
  String button = state.activeButton
  if (button) {
    isStateChanged = true
    state.inactiveButtons = [button, *state.inactiveButtons]
    state.activeButton = null
    Ltrace(
      '_pbsgIfActiveButtonPushOntoInactiveFifo()',
      "Button ${b(button)} pushed onto inactiveButtons ${state.inactiveButtons}"
    )
  }
  return isStateChanged
}

void _removeButtonFromInactiveButtons (String button) {
  // IMPORANT
  //   state.inactiveButtons.removeAll([button])
  //     - DOES NOT PERSIST CHANGES BACK INTO THE 'state' LIST.
  //     - MAKE LOCAL CHANGES
  //     - SET state.inactiveButtons BY BRUTE FORCE
  if (button) {
    List<String> local = state.inactiveButtons
    local.removeAll([button])
    state.inactiveButtons = local
  }
}

Boolean _pbsgSafelyActivateButton (String button) {
  // Return TRUE on a configuration change, FALSE otherwise.
  // Publish an event ONLY IF/WHEN a new button is activated.
  Boolean isStateChanged = false
  Ltrace('_pbsgSafelyActivateButton()', "button: ${b(button)}")
  if (button && !_pbsgGetButtons().contains(button)) {
    Lerror('_pbsgSafelyActivateButton()', "Button >${button}< does not exist")
  } else {
    if (state.activeButton == button) {
      // Nothing to do, button is already active
    } else {
      isStateChanged = true
      _pbsgIfActiveButtonPushOntoInactiveFifo()
      _removeButtonFromInactiveButtons(button)
      state.activeButton = button
      Ltrace(
        '_pbsgSafelyActivateButton()',
        "button ${b(button)} moved to activeButton ${b(state.activeButton)}"
      )
      _pbsgSendEvent()
    }
  }
  return isStateChanged
}

//---- SYSTEM CALLBACKS
//---- Methods specific to this execution context

void installed () {
  // Called on instance creation - i.e., before configuration, etc.
  state.logLevel = LogThresholdToLogLevel('TRACE')  // Integer
  state.activeButton = null    // String
  state.inactiveButtons = []   // List<String>
  state.dfltButton = null      // String
  state.vswDniPrefix = "${app.getLabel()}_"         // String
  Ltrace('installed()', 'Calling TEST_pbsgCoreFunctionality()')
  TEST_pbsgCoreFunctionality()
}

void updated () {
  Ltrace('updated()', 'At entry')
  // Adjust the provided parameters to remove empty strings, duplicates, etc.
  List<String> buttons = cleanStrings(settings.buttons)
  if (buttons != settings.buttons) {
    Lwarn('updated()', "settings.buttons: >${settings.buttons}< -> >${buttons}<")
  }
  String dfltButton = settings.dfltButton ?: null
  if (dfltButton != settings.dfltButton) {
    Lwarn('updated()', "dfltButton: >${settings.dfltButton}< -> >${dfltButton}<")
    dfltButton = dfltButton ?: null
  }
  String activeButton = settings.activeButton ?: null
  if (activeButton != settings.activeButton) {
    Lwarn('updated()', "settings.activeButton: >${settings.activeButton}< -> ${activeButton}<")
    activeButton = activeButton ?: null
  }
  if (buttons.size() < 2) {
    Lerror('updated()', "settings.buttons count (${buttons.size()}) must be >= 2")
    return
  }
  if (dfltButton && buttons.contains(dfltButton) == false) {
    Lerror('updated()', "dfltButton ${b(dfltButton)} not found in buttons (${buttons})")
    return
  }
  if (activeButton && buttons.contains(activeButton) == false) {
    Lerror('updated()', "activeButton ${b(activeButton)} not found in buttons (${buttons})")
    return
  }
  // Identify the impact of parametric changes on application state.
  List<String> prevButtons = _pbsgGetButtons()
  Map<String, List<String>> actions = CompareLists(prevButtons, buttons)
  List<String> retainButtons = actions.retained // Used for accounting only
  List<String> dropButtons = actions.dropped
  List<String> addButtons = actions.added
  String requested = [
    "<b>buttons:</b> ${buttons}",
    "<b>dfltButton:</b> ${dfltButton}",
    "<b>activeButton:</b> ${activeButton}"
  ].join('<br/>')
  String analysis = [
    "<b>prevButtons:</b> ${prevButtons}",        //  ?: 'n/a'
    "<b>retainButtons:</b> ${retainButtons}",
    "<b>dropButtons:</b> ${dropButtons}",
    "<b>addButtons:</b> ${addButtons}"
  ].join('<br/>')
  Linfo('updated()', [
    '<table style="border-spacing: 0px;" rules="all">',
    '<tr><th>STATE</th><th style="width:10%"/><th>Input Parameters</th><th style="width:10%"/><th>Action Summary</th></tr>',
    "<tr><td>${_pbsgState().join('<br/>')}</td><td/><td>${requested}</td><td/><td>${analysis}</td></tr></table>"
  ])
  // TBD
  //   - While the following suspends processing of child VSW events ...
  //   - It does not prevent Parent/Child Apps from invoking activate/deactivate.
  unsubscribe()
  // ADJUST APPLICATION STATE TO MATCH ANY REVISED PBSG CONFIGURATION
  state.dfltButton = dfltButton
  _pbsgDropButtons(dropButtons)
  if (addButtons) {
    Integer nextIndex = state.inactiveButtons?.size() ?: 0
    state.inactiveButtons.addAll(nextIndex, addButtons)
    state.inactiveButtons = cleanStrings([
      *state.inactiveButtons, *inactiveButtons
    ])
  }
  // Leverage activation/deactivation methods for initial button activation.
  if (activeButton) {
    Ltrace('updated()', "activating activeButton ${activeButton}")
    pbsgActivateButton(activeButton)
  } else if (state.activeButton == null && state.dfltButton) {
    Ltrace('updated()', "activating dfltButton ${state.dfltButton}")
    pbsgActivateButton(state.dfltButton)
  }
  //==========> _vswConfigure (buttons)
  // HAVE NOT RE-ENABLED SUBSCRIPTIONS YET
}

void uninstalled () {
  Ldebug('uninstalled()', 'No action')
}

//---- RENDERING AND DISPLAY
//----   Methods specific to this execution context

Map PbsgPage () {
  //?? app.getChildDevices()
  // Ensure a log level is available before App
  //??state.logLevel = LogThresholdToLogLevel(settings.appLogThreshold ?: 'TRACE')
  //??Ltrace('PbsgPage()', "pbsg is ${AppInfo(pbsg)}")
  return dynamicPage(
    name: 'PbsgPage',
    title: Heading1(AppInfo(app)),
    install: true,
    uninstall: false,
  ) {
    section {
      //-> solicitLogThreshold('pbsgLogThreshold')
      paragraph "YOU ARE HERE"
    }
  }
}

//---- VSW METHODS
//----   - Isolate PbsgVsw data and methods from PbsgCore data and methods.
//----   - Provide VSW functionality to a PBSG instance.
//----   - Minimize interaction between non-VSW and VSW methods, settings and state.

void _vswConfigure (List<String> buttons) {
  // Ensure a 1-to-1 correspondence between PBSG buttons and VSW instances.
  // Any new VSWs are turned off on creation.
  // Full reconciliation of VSW on/off state occurs at pbsgActivateButton().
  app.unsubscribe()
  List<String> expectedDnis = buttons.collect{ "${state.vswDniPrefix}${it}" }
  // It is unlikely that a Pbsg should have non-switch devices; so,
  // comparins just DNIs and NOT looking at d.hasCapability('Switch')
  List<String> foundDnis = app.getAllChildDevices().collect{ it.getDeviceNetworkId() }
  List<String> dropDnis = foundDnis.collect().minus(expectedDnis)
  List<String> createDnis = expectedDnis.collect().minus(foundDnis)
  Ltrace('_vswConfigure()', [
    '',
    Bullet2("<b>expectedDnis:</b> ${expectedDnis.join(', ')}"),
    Bullet2("<b>foundDnis:</b> ${foundDnis.join(', ')}"),
    Bullet2("<b>createDnis:</b> ${createDnis}"),
    Bullet2("<b>dropDnis:</b> ${dropDnis}")
  ])
  dropDnis.each{ dni ->
    Lwarn('_vswConfigure()', "Deleting orphaned device ${b(dni)}")
    app.deleteChildDevice(dni)
  }
  createDnis.each{ dni ->
    Lwarn('_vswConfigure()', "Adding VSW ${b(dni)}")
    DevW vsw = addChildDevice(
      'hubitat',          // namespace
      'Virtual Switch',   // typeName
      dni,                // device's unique DNI
      [isComponent: true, name: dni, name: dni]
    )
    vsw.off()
  }
}

void _vswUpdateState (String on, List<String> offList) {
  String dni = "${state.vswDniPrefix}${on}"
  DevW d = getChildDevice(dni)
  d ? d.on() : Lerror ('_vswRefreshState()', "Unable to find device ${d}")
  offList.each{ off ->
    dni = "${state.vswDniPrefix}${off}"
    d = getChildDevice(dni)
    d ? d.off() : Lerror ('_vswRefreshState()', "Unable to find device ${d}")
  }
}

Boolean _vswUnsubscribe () {
  app.unsubscribe('VswEventHandler')
}

Boolean _vswSubscribe () {
  // Returns false if an issue arises during subscriptions
  Boolean issueArose = false
  [state.activeButton, *state.inactiveButtons].each{ button ->
    String dni = "${state.vswDniPrefix}${button}"
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
  //   - VSWs are turned on/off as a part of _vswUpdateState().
  //   - BUT, Hubitat Dashboard and Alexa can also turn on/off VSWs.
  //   - Process and SUPPRESS any events that report state inconsistent
  //     with current on/off buttons
  if (e.isStateChange) {
    if (e.value == 'on') {
      String dni = e.displayName
      String button = dni?.minus(state.vswDniPrefix)
      if (state.onButton != button) pbsgActivateButton(button)
    } else if (e.value == 'off') {
      String dni = e.displayName
      String button = dni?.minus(state.vswDniPrefix)
      if (!state.offButtons.contains(button)) pbsgDeactivateButton(button)
    } else {
      Ldebug('VswEventHandler()', "Unexpected event ${EventDetails(e)}")
    }
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
  List<String> traceText = []
  if (forcedError) traceText += forcedError
  traceText += "buttons=${b(list)}, dflt=${b(dflt)}, activeButton=${b(on)}"
  traceText += (forcedError ? REDBAR() : GREENBAR())
  Ltrace("TEST ${n} CONFIG", traceText)

  // Simulate a Page update (GUI settings) and System updated() callback.
  _pbsgConfigure(list, dflt, on)
}

void TEST_PbsgActivation (
    Integer n,
    String description,
    String forcedError = null
  ){
  // Logs display newest to oldest; so, write logs backwards
  List<String> traceText = []
  traceText += description
  if (forcedError) { traceText += forcedError }
  traceText += (forcedError ? REDBAR() : GREENBAR())
  Ltrace("TEST ${n} ACTION", traceText)
}

String TEST_pbsgHasExpectedState (
    String activeButton,
    List<String> inactiveButtons,
    String dfltButton
  ) {
  Boolean result = true
  Integer actualInactiveButtonsSize = state.inactiveButtons?.size() ?: 0
  Integer expectedInactiveButtonsSize = inactiveButtons.size()
  if (state.dfltButton != dfltButton) {
    result = false
    Ltrace(
      'TEST_pbsgHasExpectedState()',
      "dfltButton ${state.dfltButton} != ${dfltButton}"
    )
  } else if (state.activeButton != activeButton) {
    result = false
    Ltrace(
      'TEST_pbsgHasExpectedState()',
      "activeButton ${state.activeButton} != ${activeButton}"
    )
  } else if (actualInactiveButtonsSize != expectedInactiveButtonsSize) {
    result = false
    Ltrace(
      'TEST_pbsgHasExpectedState()',
      [
        "inActiveButtons size ${actualInactiveButtonsSize} != ${expectedInactiveButtonsSize}",
        "expected: ${inactiveButtons} got: ${state.inactiveButtons}"
      ]
    )
  } else {
    state.inactiveButtons.eachWithIndex{ button, index ->
      String expectedButton = inactiveButtons[index]
      if (button != expectedButton) {
        result = false
        Ltrace(
          'TEST_pbsgHasExpectedState()',
          "At ${index}: inactiveButton ${button} != ${expectedButton}"
        )
      }
    }
  }
  List<String> results = [result ? 'true' : '<b>FALSE</b>']
  if (state.activeButton == activeButton) {
    results += "<i>activeButton: ${state.activeButton}</i>"
  } else {
    results += "<i>activeButton: ${state.activeButton}</i> ==> <b>expected: ${activeButton}</b>"
  }
  if (state.inactiveButtons == inactiveButtons) {
    results += "<i>inactiveButtons: ${state.inactiveButtons}</i>"
  } else {
    results += "<i>inactiveButtons:</b> ${state.inactiveButtons}</i> ==> <b>expected: ${inactiveButtons}</b>"
  }
  if(state.dfltButton == dfltButton) {
    results += "<i>dfltButton: ${state.dfltButton}</i>"
  } else {
    results += "<i>dfltButton: ${state.dfltButton}</i> ==> <b>expected: ${dfltButton}</b>"
  }
  return results.join('<br/>')
}

void TEST_pbsgCoreFunctionality () {
  String parkLogLevel = state.logLevel
  state.logLevel = LogThresholdToLogLevel('TRACE')
  //----
  String expectedActive = null
  List<String> expectedInactive = null
  Ltrace('TEST_pbsgCoreFunctionality()', ['At Entry', *_pbsgState(), BLACKBAR()])
  //----
  TEST_pbsgConfigure(1, [], 'A', 'B', '<b>Forced Error:</b> "No buttons"')
  Ltrace('TEST1', TEST_pbsgHasExpectedState(null, [], null))
  //----
  TEST_pbsgConfigure(2, ['A', 'B', 'C', 'D', 'E'], '', null)
  Ltrace('TEST2', TEST_pbsgHasExpectedState(null, ['A', 'B', 'C', 'D', 'E'], null))
  //----
  TEST_pbsgConfigure(3, ['A', 'B', 'C', 'D', 'E'], 'B', null)
  Ltrace('TEST3', TEST_pbsgHasExpectedState('B', ['A', 'C', 'D', 'E'], 'B'))
  //----
  TEST_pbsgConfigure(4, ['A', 'C', 'D', 'E'], 'B', null, '<b>Forced Error:</b> "Default not in Buttons"')
  // TEST4 state is unchanged from TEST3 state
  Ltrace('TEST4', TEST_pbsgHasExpectedState('B', ['A', 'C', 'D', 'E'], 'B'))
  //----
  TEST_pbsgConfigure(5, ['B', 'C', 'D', 'E', 'F'], '', 'C')
  Ltrace('TEST5', TEST_pbsgHasExpectedState('C', ['B', 'D', 'E', 'F'], null))
  //----
  TEST_pbsgConfigure(6, ['B', 'F', 'G', 'I'], 'B', 'D', '<b>Forced Error:</b> "Active not in Buttons"')
  // TEST6 state is unchanged from TEST5 state
  Ltrace('TEST6', TEST_pbsgHasExpectedState('C', ['B', 'D', 'E', 'F'], null))
  //----
  TEST_pbsgConfigure(7, ['B', 'F', 'G', 'I'], 'B', 'G')
  Ltrace('TEST7', TEST_pbsgHasExpectedState('G', ['B', 'F', 'I'], 'B'))
  //----
  // WITHOUT CHANGING THE CONFIGURATION, START TESTING ACTIVATION OF BUTTONS
  // THE DEFAULT BUTTON REMAINS 'B'
  //----
  TEST_PbsgActivation(8, 'Activate F')
  pbsgActivateButton('F')
  Ltrace('TEST8', TEST_pbsgHasExpectedState('F', ['G', 'B', 'I'], 'B'))
  //----
  TEST_PbsgActivation(9, 'Activate Q', '<b>Forced Error:</b> "Button does not exist"')
  pbsgActivateButton('Q')
  // TEST9 state is unchanged from TEST8 state
  Ltrace('TEST9', TEST_pbsgHasExpectedState('F', ['G', 'B', 'I'], 'B'))
  //----
  TEST_PbsgActivation(10, 'Deactivate F')
  pbsgDeactivateButton('F')
  Ltrace('TEST10', TEST_pbsgHasExpectedState('B', ['F', 'G', 'I'], 'B'))
  //----
  TEST_PbsgActivation(11, 'Activate I')
  pbsgActivateButton('I')
  Ltrace('TEST11', TEST_pbsgHasExpectedState('I', ['B', 'F', 'G'], 'B'))
  //----
  TEST_PbsgActivation(12, 'Activate Predecessor')
  pbsgActivatePredecessor()
  Ltrace('TEST12', TEST_pbsgHasExpectedState('B', ['I', 'F', 'G'], 'B'))
  //----
  TEST_pbsgConfigure(13, ['B', 'X', 'C', 'E', 'Z'], '', 'C')
  Ltrace('TEST13', TEST_pbsgHasExpectedState('C', ['B', 'X', 'E', 'Z'], null))
  //----
  TEST_PbsgActivation(14, 'Deactivate C')
  pbsgDeactivateButton('C')
  Ltrace('TEST14', TEST_pbsgHasExpectedState(null, ['C', 'B', 'X', 'E', 'Z'], null))
  //----
  TEST_PbsgActivation(15, 'Activate Predecessor')
  pbsgActivatePredecessor()
  Ltrace('TEST15', TEST_pbsgHasExpectedState('C', ['B', 'X', 'E', 'Z'], null))
  //----
  TEST_pbsgConfigure(16, ['B', '', null, 'A', 'G', 'X', null, 'A'], 'X', '')
  Ltrace('TEST16', TEST_pbsgHasExpectedState('X', ['B', 'A', 'G'], 'X'))
  //----
  state.logLevel = parkLogLevel
}
