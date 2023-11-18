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

preferences {
  page(name: 'PbsgPage')
}

//---- CORE METHODS
//---- Methods that ARE NOT constrained to any specific execution context.

Boolean pbsgUpdateConfig (
    List<String> requestedButtonsParm,
    String defaultButtonParm = null,
    String activeButtonParm = null
  ) {
  // Return TRUE on a configuration change, FALSE otherwise.
  Boolean isStateChanged = false
  if (!requestedButtonsParm) {
    Lerror('pbsgUpdateConfig()', '<b>No buttons have been defined.</b>')
    return isStateChanged
  }
  if (requestedButtonsParm.size() < 2) {
    Lerror('pbsgUpdateConfig()', "<b>A PBSG needs at least two buttons.</b>")
    return isStateChanged
  }
  if (defaultButtonParm ?: null) {
    if (!requestedButtonsParm.contains(defaultButtonParm)) {
      Lerror('pbsgUpdateConfig()', [
        '<b>Problematic defaultButton</b>',
        "${b(defaultButtonParm)} IS NOT present in ${b(requestedButtonsParm)}"
      ])
      return isStateChanged
    }
  }
  if (atomicState.defaultButton != (defaultButtonParm ?: null)) {
    isStateChange = true
    atomicState.defaultButton = (defaultButtonParm ?: null)
    Linfo(
      'pbsgUpdateConfig()',
      "Adjusted atomicState.defaultButton to ${atomicState.defaultButton}"
    )
  }
  if (activeButtonParm && !requestedButtonsParm.contains(activeButtonParm)) {
    return isStateChanged
  }
  List<String> existingButtons = _pbsgExistingButtons()
  Map<String, List<String>> actions = CompareLists(
    existingButtons,
    requestedButtonsParm
  )
  List<String> retainButtons = actions.retained // Used for accounting only
  List<String> dropButtons = actions.dropped
  List<String> addButtons = actions.added
  String requestedParms = [
    "<b>requestedButtonsParm:</b> ${requestedButtonsParm ?: 'n/a'}",
    "<b>defaultButtonParm:</b> ${(defaultButtonParm ?: null)}",
    "<b>activeButtonParm:</b> ${activeButtonParm}"
  ].join('<br/>')
  String analysis = [
    "<b>existingButtons:</b> ${existingButtons ?: 'n/a'}",
    "<b>retainButtons:</b> ${retainButtons ?: 'n/a'}",
    "<b>dropButtons:</b> ${dropButtons ?: 'n/a'}",
    "<b>addButtons:</b> ${addButtons ?: 'n/a'}"
  ].join('<br/>')
  Ltrace('pbsgUpdateConfig()', [
    '<table style="border-spacing: 0px;" rules="all">',
    '<tr><th>Input Parameters</th><th style="width:10%"/><th>Action Summary</th></tr>',
    "<tr><td>${requestedParms}</td><td/><td>${analysis}</td></tr></table>"
  ])
  if (dropButtons) {
    isStateChanged = true
    // Remove out-of-scope buttons without activating any button.
    if (dropButtons.contains(atomicState.activeButton)) {
      atomicState.activeButton = null
    }
    atomicState.inactiveButtons.removeAll(dropButtons)
  }
  if (addButtons) {
    isStateChanged = true
    // Add new buttons without activating any buttons
    if (atomicState.inactiveButtons) {
      // Add buttons to the existing list.
      Integer nextIndex = atomicState.inactiveButtons?.size() ?: 0
      atomicState.inactiveButtons.addAll(nextIndex, addButtons)
    } else {
      // No existing buttons, so start the list.
      atomicState.inactiveButtons = addButtons
    }
  }
  // Delegate all aspects of button activation to existing methods.
  if (activeButtonParm) {
    Ltrace('pbsgUpdateConfig()', "activating ${activeButtonParm}")
    isStateChanged = pbsgActivateButton(activeButtonParm)
  } else if (atomicState.activeButton == null && atomicState.defaultButton) {
    Ltrace('pbsgUpdateConfig()', "activating ${atomicState.defaultButton}")
    isStateChanged = pbsgActivateButton(atomicState.defaultButton)
  }
  Ltrace('pbsgUpdateConfig()', [
    'AT EXIT',
    *pbsgState()
  ])
}

List<String> pbsgState () {
  return [
    Heading2('STATE'),
    Bullet2("<b>logLevel:</b> ${atomicState.logLevel}"),
    Bullet2("<b>activeButton:</b> ${atomicState.activeButton}"),
    Bullet2("<b>inactiveButtons:</b> ${atomicState.inactiveButtons}"),
    Bullet2("<b>defaultButton:</b> ${atomicState.defaultButton}")
  ]
}

Boolean pbsgActivateButton (String button) {
  // Return TRUE on a configuration change, FALSE otherwise.
  Boolean isStateChanged = false
  if (!_pbsgExistingButtons().contains(button)) {
    Lerror('pbsgActivateButton()', "Argument ${b(button)} IS NOT a button")
  } else {
    isStateChange = _pbsgSafelyActivateButton(button)
  }
  return isStateChanged
}

Boolean pbsgDeactivateButton (String button) {
  // Return TRUE on a configuration change, FALSE otherwise.
  Boolean isStateChanged = false
  if (atomicState.activeButton == atomicState.defaultButton) {
    Linfo(
      'pbsgDeactivateButton()',
      "Ignoring attempt to deactivate the default button (${atomicState.defaultButton})"
    )
  } else {
    isStateChange = _pbsgSafelyActivateButton(atomicState.defaultButton)
  }
  return isStateChange
}

Boolean pbsgActivatePredecessor () {
  return _pbsgSafelyActivateButton(atomicState.inactiveButtons.first())
}

void _pbsgSendEvent() {
  Map<String, String> event = [
    name: 'PbsgActiveButton',
    descriptionText: "Button ${atomicState.activeButton} is active",
    value: [
      'active': atomicState.activeButton,
      'inactive': atomicState.inactiveButtons,
      'default': atomicState.defaultButton
    ].toMapString()
  ]
  Ltrace('_pbsgSendEvent()', event.toMapString())
  sendEvent(event)
}

List<String> _pbsgExistingButtons () {
  return  [atomicState.activeButton, atomicState.inactiveButtons]
          .flatten()
          .findAll{ e -> e?.trim() }
}

Boolean _pbsgIfActiveButtonPushOntoInactiveFifo () {
  // Return TRUE on a configuration change, FALSE otherwise.
  // This method DOES NOT activate a defaultButton!
  Boolean isStateChanged = false
  String button = atomicState.activeButton
  if (button) {
    isStateChanged = true
    atomicState.inactiveButtons = [button, *atomicState.inactiveButtons]
    atomicState.activeButton = null
    Ltrace(
      '_pbsgIfActiveButtonPushOntoInactiveFifo()',
      "${b(button)} is inactive"
    )
  }
  return isStateChanged
}

void _removeButtonFromInactiveButtons (String button) {
  // IMPORANT
  //   atomicState.inactiveButtons.removeAll([button])
  //     - DOES NOT PERSIST CHANGES BACK INTO THE 'atomicState' LIST.
  //     - MAKE LOCAL CHANGES
  //     - SET atomicState.inactiveButtons BY BRUTE FORCE
  if (button) {
    List<String> local = atomicState.inactiveButtons
    local.removeAll([button])
    atomicState.inactiveButtons = local
    Ltrace('_removeButtonFromInactiveButtons()', "${b(button)} is inactive")
  }
}

Boolean _pbsgSafelyActivateButton (String button) {
  // Return TRUE on a configuration change, FALSE otherwise.
  // Publish an event ONLY IF/WHEN a new button is activated.
  Boolean isStateChanged = false
  if (atomicState.activeButton == button) {
    // Nothing to do, button is already active
  } else {
    isStateChanged = true
    _pbsgIfActiveButtonPushOntoInactiveFifo()
    _removeButtonFromInactiveButtons(button)
    atomicState.activeButton = button
    Ltrace(callingFn, "${b(atomicState.activeButton)} is active")
    _pbsgSendEvent()
  }
  return isStateChanged
}

//---- SYSTEM CALLBACKS
//---- Methods specific to this execution context

void installed () {
  // Called on instance creation - i.e., before configuration, etc.
  String logLevel =
  atomicState.logLevel = LogThresholdToLogLevel('TRACE')  // Integer
  atomicState.activeButton = null                         // String
  atomicState.inactiveButtons = []                        // List<String>
  atomicState.defaultButton = null                        // String
  Ltrace('installed()', 'Calling TEST_PbsgCore()')
  TEST_PbsgCore()
}

void updated () {
  Ltrace('updated()', 'Calling TEST_PbsgCore()')
  TEST_PbsgCore()
}

void uninstalled () {
  Ldebug('uninstalled()', 'No action')
}

//---- RENDERING AND DISPLAY
//----   Methods specific to this execution context

Map PbsgPage () {
  app.getChildDevices()
  // Ensure a log level is available before App
  atomicState.logLevel = LogThresholdToLogLevel(settings.appLogThreshold ?: 'DEBUG')
  Ltrace('PbsgPage()', "pbsg is ${AppInfo(pbsg)}")
  return dynamicPage(
    name: 'PbsgPage',
    title: Heading1(AppInfo(app)),
    install: true,
    uninstall: false,
  ) {
    section {
      paragraph "YOU ARE HERE"
    }
  }
}

//---- TEST SUPPORT

void TEST_ConfigChange (
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
  pbsgUpdateConfig(list, dflt, on)
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

String TEST_HasExpectedState (
    String activeButton,
    List<String> inactiveButtons,
    String defaultButton
  ) {
  Boolean result = true
  Integer actualInactiveButtonsSize = atomicState.inactiveButtons?.size() ?: 0
  Integer expectedInactiveButtonsSize = inactiveButtons.size()
  if (atomicState.defaultButton != defaultButton) {
    result = false
    Ltrace(
      'TEST_HasExpectedState()',
      "defaultButton ${atomicState.defaultButton} != ${defaultButton}"
    )
  } else if (atomicState.activeButton != activeButton) {
    result = false
    Ltrace(
      'TEST_HasExpectedState()',
      "activeButton ${atomicState.activeButton} != ${activeButton}"
    )
  } else if (actualInactiveButtonsSize != expectedInactiveButtonsSize) {
    result = false
    Ltrace(
      'TEST_HasExpectedState()',
      [
        "inActiveButtons size ${actualInactiveButtonsSize} != ${expectedInactiveButtonsSize}",
        "expected: ${inactiveButtons} got: ${atomicState.inactiveButtons}"
      ]
    )
  } else {
    atomicState.inactiveButtons.eachWithIndex{ button, index ->
      String expectedButton = inactiveButtons[index]
      if (button != expectedButton) {
        result = false
        Ltrace(
          'TEST_HasExpectedState()',
          "At ${index}: inactiveButton ${button} != ${expectedButton}"
        )
      }
    }
  }
  List<String> results = [result ? 'true' : '<b>FALSE</b>']
  if (atomicState.activeButton == activeButton) {
    results += "<i>activeButton: ${atomicState.activeButton}</i>"
  } else {
    results += "<i>activeButton: ${atomicState.activeButton}</i> ==> <b>expected: ${activeButton}</b>"
  }
  if (atomicState.inactiveButtons == inactiveButtons) {
    results += "<i>inactiveButtons: ${atomicState.inactiveButtons}</i>"
  } else {
    results += "<i>inactiveButtons:</b> ${atomicState.inactiveButtons}</i> ==> <b>expected: ${inactiveButtons}</b>"
  }
  if(atomicState.defaultButton == defaultButton) {
    results += "<i>defaultButton: ${atomicState.defaultButton}</i>"
  } else {
    results += "<i>defaultButton: ${atomicState.defaultButton}</i> ==> <b>expected: ${defaultButton}</b>"
  }
  return results.join('<br/>')
}

void TEST_PbsgCore () {
  String parkLogLevel = atomicState.logLevel
  atomicState.logLevel = LogThresholdToLogLevel('TRACE')
  // START AS THOUGH FRESHLY INITIALIZED
  atomicState.activeButton = null
  atomicState.inactiveButtons = []
  atomicState.defaultButton = null
  //----
  String expectedActive = null
  List<String> expectedInactive = null
  Ltrace('TEST_PbsgCore()', ['At Entry', *pbsgState(), BLACKBAR()])
  //----
  TEST_ConfigChange(1, [], 'A', 'B', '<b>Forced Error:</b> "No buttons"')
  Ltrace('TEST1', TEST_HasExpectedState(null, [], null))
  //----
  TEST_ConfigChange(2, ['A', 'B', 'C', 'D', 'E'], '', null)
  Ltrace('TEST2', TEST_HasExpectedState(null, ['A', 'B', 'C', 'D', 'E'], null))
  //----
  TEST_ConfigChange(3, ['A', 'B', 'C', 'D', 'E'], 'B', null)
  Ltrace('TEST3', TEST_HasExpectedState('B', ['A', 'C', 'D', 'E'], 'B'))
  //----
  TEST_ConfigChange(4, ['A', 'C', 'D', 'E'], 'B', null, '<b>Forced Error:</b> "Default not in Buttons"')
  // TEST4 state is unchanged from TEST3 state
  Ltrace('TEST4', TEST_HasExpectedState('B', ['A', 'C', 'D', 'E'], 'B'))
  //----
  TEST_ConfigChange(5, ['B', 'C', 'D', 'E', 'F'], '', 'C')
  Ltrace('TEST5', TEST_HasExpectedState('C', ['B', 'D', 'E', 'F'], null))
  //----
  TEST_ConfigChange(6, ['B', 'F', 'G', 'I'], 'B', 'D', '<b>Forced Error:</b> "Active not in Buttons"')
  // TEST6 state is unchanged from TEST5 state
  Ltrace('TEST6', TEST_HasExpectedState('C', ['B', 'D', 'E', 'F'], 'B'))
  //----
  TEST_ConfigChange(7, ['B', 'F', 'G', 'I'], 'B', 'G')
  Ltrace('TEST7', TEST_HasExpectedState('G', ['B', 'F', 'I'], 'B'))
  //----
  // WITHOUT CHANGING THE CONFIGURATION, START TESTING ACTIVATION OF BUTTONS
  // THE DEFAULT BUTTON REMAINS 'B'
  //----
  TEST_PbsgActivation(8, 'Activate F')
  pbsgActivateButton('F')
  Ltrace('TEST8', TEST_HasExpectedState('F', ['G', 'B', 'I'], 'B'))
  //----
  TEST_PbsgActivation(9, 'Activate Q', '<b>Forced Error:</b> "Button does not exist"')
  pbsgActivateButton('Q')
  // TEST9 state is unchanged from TEST8 state
  Ltrace('TEST9', TEST_HasExpectedState('F', ['G', 'B', 'I'], 'B'))
  //----
  TEST_PbsgActivation(10, 'Deactivate F')
  pbsgDeactivateButton('F')
  Ltrace('TEST10', TEST_HasExpectedState('B', ['F', 'G', 'I'], 'B'))
  //----
  TEST_PbsgActivation(11, 'Activate I')
  pbsgActivateButton('I')
  Ltrace('TEST11', TEST_HasExpectedState('I', ['B', 'F', 'G'], 'B'))
  //----
  TEST_PbsgActivation(12, 'Activate Predecessor')
  pbsgActivatePredecessor()
  Ltrace('TEST12', TEST_HasExpectedState('B', ['I', 'F', 'G'], 'B'))
  //----
  TEST_ConfigChange(13, ['B', 'X', 'C', 'E', 'Z'], '', 'C')
  Ltrace('TEST13', TEST_HasExpectedState('C', ['B', 'X', 'E', 'Z'], null))
  //----
  TEST_PbsgActivation(14, 'Deactivate C')
  pbsgDeactivateButton('C')
  Ltrace('TEST14', TEST_HasExpectedState(null, ['C', 'B', 'X', 'E', 'Z'], null))
  //----
  TEST_PbsgActivation(12, 'Activate Predecessor')
  pbsgActivatePredecessor()
  Ltrace('TEST15', TEST_HasExpectedState('C', ['B', 'X', 'E', 'Z'], null))
  //----
  atomicState.logLevel = parkLogLevel
}
