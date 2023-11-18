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
// ATOMIC STATE
//   logLevel : Integer
//   activeButton : String
//   inactiveButtons : List<String>, functionally a FIFO
//   defaultButton : String
//
// Application Overview
//   - Enable no more than one button at a time.
//   - Publish active button changes (along with the inactive button FIFO)
//   - Delineate "Private" methods with a leading underscore.
//
// "Public" Methods
//   - pbsgUpdateConfig
//   - pbsgState
//   - pbsgActivateButton
//   - pbsgDeactivateButton
//   - pbsgActivatePredecessor
//
// "Private" Methods
//   - _pbsgExistingButtons
//   - _pbsgPublishEvent
// ---------------------------------------------------------------------------------
import com.hubitat.app.InstalledAppWrapper as InstAppW
#include wesmc.libHubExt
#include wesmc.libHubUI

definition (
  name: 'PbsgCore',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Implement PBSG functions and is authoritative for pushbutton state.',
  category: '', // Not supported as of Q3'23
  iconUrl: '', // Not supported as of Q3'23
  iconX2Url: '', // Not supported as of Q3'23
  iconX3Url: '', // Not supported as of Q3'23
  singleInstance: true
)

preferences {
  page(name: 'PbsgPage')
}

//----
//---- CORE METHODS
//---- Methods that ARE NOT constrained to any specific execution context.
//----

Boolean pbsgUpdateConfig (
    List<String> requestedButtons,
    String defaultButton = null,
    String activeButton = null
  ) {
  // Return TRUE on a configuration change, FALSE otherwise.
  Boolean isStateChanged = false
  if (!requestedButtons) {
    Lerror('pbsgUpdateConfig()', '<b>No buttons have been defined.</b>')
    return isStateChanged
  }
  if (requestedButtons.size() < 2) {
    Lerror('pbsgUpdateConfig()', "<b>A PBSG needs at least two buttons.</b>")
    return isStateChanged
  }
  if (defaultButton && !requestedButtons.contains(defaultButton)) {
    Lerror('pbsgUpdateConfig()', [
      '<b>Problematic defaultButton</b>',
      "${b(defaultButton)} IS NOT present in ${b(requestedButtons)}"
    ])
    return isStateChanged
  }
  if (activeButton && !requestedButtons.contains(activeButton)) {
    Lerror('pbsgUpdateConfig()', [
      '<b>Problematic activeButton</b>',
      "${b(activeButton)} IS NOT present in ${b(requestedButtons)}"
    ])
    return isStateChanged
  }
  if (atomicState.defaultButton != defaultButton) {
    atomicState.defaultButton = defaultButton
    isStateChanged = true
  }
  List<String> existingButtons = _pbsgExistingButtons()
  Map<String, List<String>> actions = CompareLists(
    existingButtons,
    requestedButtons
  )
  List<String> retainButtons = actions.retained // Used for accounting only
  List<String> dropButtons = actions.dropped
  List<String> addButtons = actions.added
  Ltrace('pbsgUpdateConfig()', [
    Heading2('OVERVIEW AT ENTRY'),
    Bullet1('Parameters'),
    Bullet2("<b>requestedButtons:</b> ${requestedButtons ?: 'n/a'}"),
    Bullet2("<b>defaultButton:</b> ${defaultButton ?: null}"),
    Bullet2("<b>activeButton:</b> ${activeButton}"),
    Bullet1('Analysis'),
    Bullet2("<b>existingButtons:</b> ${existingButtons ?: 'n/a'}"),
    Bullet2("<b>retainButtons:</b> ${retainButtons ?: 'n/a'}"),
    Bullet2("<b>dropButtons:</b> ${dropButtons ?: 'n/a'}"),
    Bullet2("<b>addButtons:</b> ${addButtons ?: 'n/a'}")
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
  if (activeButton) {
    Ltrace('pbsgUpdateConfig()', "activating ${activeButton}")
    isStateChanged = pbsgActivateButton(activeButton)
  } else if (atomicState.activeButton == null && defaultButton) {
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
    '',
    Heading2('STATE'),
    Bullet2("<b>logLevel:</b> ${atomicState.logLevel}"),
    Bullet2("<b>activeButton:</b> ${atomicState.activeButton}"),
    Bullet2("<b>inactiveButtons:</b> ${atomicState.inactiveButtons}"),
    Bullet2("<b>defaultButton:</b> ${atomicState.defaultButton}")
  ]
}

//=============================== REWORKED ===============================//

Boolean pbsgActivateButton (String button) {
  // Return TRUE on a configuration change, FALSE otherwise.
  Boolean isStateChanged = false
  if (!_pbsgExistingButtons().contains(button)) {
    Lerror('pbsgActivateButton()', "Argument ${b(button)} IS NOT a button")
  } else if (atomicState.activeButton == button) {
    // Do nothing. The button is already active.
  } else {
    isStateChanged = true
    if (atomicState.activeButton != null) {
      // Move active button to the front of the inactive FIFO queue.
      atomicState.inactiveButtons = [atomicState.activeButton, *atomicState.inactiveButtons]
      Ltrace('pbsgActivateButton()', "${b(atomicState.activeButton)} is inactive")
    }
    // Move button from inactiveButtons to activeButton.
    // DIRECT OPERATION ON atomicState.inactiveButtons DOES NOT PERSIST
    List<String> local2 = atomicState.inactiveButtons
    local2.removeAll([button])
    atomicState.inactiveButtons = local2
    atomicState.activeButton = button
    //Ltrace('pbsgActivateButton()', "${b(button)} is active}")
  }
  return isStateChanged
}

Boolean pbsgDeactivateButton (String button) {
  // Return TRUE on a configuration change, FALSE otherwise.
  Boolean isStateChanged = false
  if (atomicState.activeButton != button) {
    // Do nothing. The button is already inactive.
  } else {
    isStateChanged = true
    if (atomicState.activeButton != null) {
      // Move active button to the front of the inactive FIFO queue.
      atomicState.inactiveButtons = [atomicState.activeButton, *atomicState.inactiveButtons]
      Ltrace('pbsgDeactivateButton()', "${b(atomicState.activeButton)} is inactive")
    }
    // Move the default button inactiveButtons to activeButton
    if (atomicState.defaultButton) {
      // To remove, manipulate a copy of object state THEN refresh object state.
      List<String> local = atomicState.inactiveButtons
      local.removeAll(atomicState.defaultButton)
      atomicState.inactiveButtons = local
    }
    // Activate the defaultButton (which may exist OR may be null).
    atomicState.activeButton = atomicState.defaultButton
    Ltrace('pbsgDeactivateButton()', "${b(atomicState.defaultButton)} is active")
  }
  return isStateChanged
}

Boolean pbsgActivatePredecessor () {
  // Return TRUE on a configuration change, FALSE otherwise.
  // Expecting this method to ALWAYS make a change.
  Boolean isStateChanged = true
  List<String> inactiveButtons = atomicState.inactiveButtons
  if (atomicState.activeButton != null) {
    // Swap the currently active button with the front-most inactive button.
    // The following pop alters the local copy of inactiveButtons (only).
    String toBeActivated = inactiveButtons.pop() ?: null
    atomicState.inactiveButtons = [ atomicState.activeButton, *inactiveButtons ]
    Ltrace('pbsgActivatePredecessor()', "${b(atomicState.activeButton)} is inactive")
    atomicState.activeButton = toBeActivated
    Ltrace('pbsgActivatePredecessor()', "${b(toBeActivated)} is active")
  } else {
    // Activate the front-most inactive button.
    String toBeActivated = inactiveButtons.pop() ?: null
    atomicState.inactiveButtons = inactiveButtons
    atomicState.activeButton = toBeActivated
    Ltrace('pbsgActivatePredecessor()', "${b(toBeActivated)} is active")
  }
  return isStateChanged
}

/*
void _pbsgPublishEvent() {
  // When a default button is present, publish the currently on button.
  // If no default button is configured, publish a null if there is no
  // current button.
  Integer activeButtons = atomicState.activeButton.size()
  if (atomicState.defaultButton && activeButtons != 1) {
    Lerror('_pbsgPublishEvent()', [
      '<b>SUPPRESSED EVENT PUBLICATION</b>',
      "activeButtonFifo.size() expected <b>1</b> got <b>${activeButtons}</b>"
    ])
  } else if (!atomicState.defaultButton && activeButtons > 1) {
    Lerror('_pbsgPublishEvent()', [
      '<b>SUPPRESSED EVENT PUBLICATION</b>',
      "activeButtonFifo.size() expected <b>0 or 1</b> got <b>${activeButtons}</b>"
    ])
  } else {
    String activeButton = atomicState.activeButton[0]
    String eventValue = [
      'active': activeButton,
      'inactive': atomicState.inactiveButtons
    ].toMapString()
  Ldebug('_pbsgPublishEvent()', [
    '',
    "activeButton: ${activeButton}",
    "eventValue: ${eventValue}"
  ])
    Map<String, String> event = [
      name: 'PbsgCurrentButton',
      descriptionText: "Button ${activeButton} is active",
      value: [
        active: activeButton,
        inactive: atomicState.inactiveButtons
      ].toMapString()
    ]
    //Ltrace('_pbsgPublishEvent()', event.toMapString())
    sendEvent(event)
  }
}
*/

//-- PRIVATE METHODS

List<String> _pbsgExistingButtons () {
  return  [atomicState.activeButton, atomicState.inactiveButtons]
          .flatten()
          .findAll{ e -> e?.trim() }
}



/*
void _pbsgEnforceControls () {
  Ltrace('_pbsgEnforceControls', [
    'At entry ...',
    *pbsgState()
  ])
  // Enforce Mutual Exclusion
  while (atomicState.activeButton.size() > 1) {
    String button = ListPop(atomicState.activeButton)
    ListPushItem(atomicState.inactiveButtons, button)
    Ltrace('pcEnforceMutualExclusion()', "Turning off ${b(button)}")
    //--MOVE-TO-CHILD-> _turnOffVsw(button)
  }
  Ltrace('_pbsgEnforceControls', [
    'After Mutual Exclusion ...',
    *pbsgState()
  ])
  // Enforce Default Button
  if (atomicState.defaultButton && atomicState.activeButton.size() == 0) {
    ListRemove(atomicState.inactiveButtons, atomicState.defaultButton)
    ListPushItem(atomicState.activeButton, atomicState.defaultButton)
    Ltrace('pcEnforceDefaultButton()', "Turning on ${b(atomicState.defaultButton)}")
    //--MOVE-TO-CHILD-> _turnOnVsw(button)
  }
  Ltrace('_After Default Button', [
    'At entry ...',
    *pbsgState()
  ])
}
*/

//----
//---- SYSTEM CALLBACKS
//---- Methods specific to this execution context
//----

void installed () {
  // Called on instance creation - i.e., before configuration, etc.
  String logLevel =
  atomicState.logLevel = LogThresholdToLogLevel('TRACE')  // Integer
  atomicState.activeButton = null                         // String
  atomicState.inactiveButtons = null                      // List<String>
  atomicState.defaultButton = null                        // String
  Ltrace('installed()', 'Calling PbsgCore_TEST()')
  PbsgCore_TEST()
}

void updated () {
  Ltrace('updated()', 'Calling PbsgCore_TEST()')
  PbsgCore_TEST()
}

void uninstalled () {
  Ldebug('uninstalled()', 'No action')
}

//----
//---- RENDERING AND DISPLAY
//----   Methods specific to this execution context
//----

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

//----
//---- TEST SUPPORT
//----

String BLACKBAR() { return '<hr style="border: 5px solid black;"/>' }
String GREENBAR() { return '<hr style="border: 5px solid green;"/>' }
String REDBAR() { return '<hr style="border: 5px solid red;"/>' }

void TEST_ConfigChange (
    Integer n,
    List<String> list,
    String dflt,
    String on,
    String forcedError = null
  ) {
  // Logs display newest to oldest; so, write logs backwards
  List<String> traceText = []
  traceText += "buttons=${b(list)}, dflt=${b(dflt)}, activeButton=${b(on)}"
  if (forcedError) traceText += forcedError
  traceText += (forcedError ? REDBAR() : GREENBAR())
  Ltrace("TEST ${n} CONFIG", traceText)
  pbsgUpdateConfig(list, dflt, on)
}

void PbsgActivation_TEST (
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

String _hasExpectedState (String activeButton, List<String> inactiveButtons) {
  Boolean result = true
  Integer actualInactiveButtonsSize = atomicState.inactiveButtons?.size() ?: 0
  Integer expectedInactiveButtonsSize = inactiveButtons.size()
  if (atomicState.activeButton != activeButton) {
    result = false
    Ltrace(
      '_hasExpectedState()',
      "activeButton ${atomicState.activeButton} != ${activeButton}"
    )
  } else if (actualInactiveButtonsSize != expectedInactiveButtonsSize) {
    result = false
    Ltrace(
      '_hasExpectedState()',
      "inActiveButtons size ${actualInactiveButtonsSize} != ${expectedInactiveButtonsSize}",
      "expected: ${inactiveButtons} got: ${atomicState.inactiveButtons}"
    )
  } else {
    atomicState.inactiveButtons.eachWithIndex{ button, index ->
      String expectedButton = inactiveButtons[index]
      if (button != expectedButton) {
        result = false
        Ltrace(
          '_hasExpectedState()',
          "At ${index}: inactiveButton ${button} != ${expectedButton}"
        )
      }
    }
  }
  return [
    result ? 'true' : '<b>FALSE</b>',
    *pbsgState()
  ].join('<br/>')
}

void PbsgCore_TEST () {
  String parkLogLevel = atomicState.logLevel
  atomicState.logLevel = LogThresholdToLogLevel('TRACE')
  // START AS THOUGH FRESHLY INITIALIZED
  atomicState.activeButton = null
  atomicState.inactiveButtons = null
  atomicState.defaultButton = null
  //----
  String expectedActive = null
  List<String> expectedInactive = null
  Ltrace('PbsgCore_TEST()', ['At Entry', *pbsgState(), BLACKBAR()])
  //----
  TEST_ConfigChange(1, [], 'A', 'B', 'Force Error: "No buttons"')
  Ltrace('TEST1', _hasExpectedState(null, []))
  //----
  TEST_ConfigChange(2, ['A', 'B', 'C', 'D', 'E'], '', null)
  Ltrace('TEST2', _hasExpectedState(null, ['A', 'B', 'C', 'D', 'E']))
  //----
  TEST_ConfigChange(3, ['A', 'B', 'C', 'D', 'E'], 'B', null)
  Ltrace('TEST3', _hasExpectedState('B', ['A', 'C', 'D', 'E']))
  //----
  TEST_ConfigChange(4, ['A', 'C', 'D', 'E'], 'B', null, '<b>Forced Error:</b> "Default not in Buttons"')
  // The state post TEST4 should be the same as the state post TEST3!
  Ltrace('TEST4', _hasExpectedState('B', ['A', 'C', 'D', 'E']))
  //----
  TEST_ConfigChange(5, ['B', 'C', 'D', 'E', 'F'], '', 'C')
  Ltrace('TEST5', _hasExpectedState('C', ['B', 'D', 'E', 'F']))
  //----
  TEST_ConfigChange(6, ['B', 'F', 'G', 'I'], 'B', 'D', '<b>Forced Error:</b> "Active not in Buttons"')
  // The state post TEST6 should be the same as the state post TEST5!
  Ltrace('TEST6', _hasExpectedState('C', ['B', 'D', 'E', 'F']))
  //----
  TEST_ConfigChange(7, ['B', 'F', 'G', 'I'], 'B', 'G')
  Ltrace('TEST7', _hasExpectedState('G', ['B', 'F', 'I']))
  //----
  // WITHOUT CHANGING THE CONFIGURATION, START TESTING ACTIVATION OF BUTTONS
  // THE DEFAULT BUTTON REMAINS 'B'
  //----
  PbsgActivation_TEST(8, 'Activate F')
  pbsgActivateButton('F')
  Ltrace('TEST8', _hasExpectedState('F', ['G', 'B', 'I']))
  //----
  PbsgActivation_TEST(9, 'Activate Q', '<b>Forced Error:</b> "Button does not exist"')
  pbsgActivateButton('Q')
  // The state post TEST9 should be the same as the state post TEST8
  Ltrace('TEST9', _hasExpectedState('F', ['G', 'B', 'I']))
  //----
  PbsgActivation_TEST(10, 'Deactivate F')
  pbsgDeactivateButton('F')  // NOTE: Dflt=B
  Ltrace('TEST10', _hasExpectedState('B', ['F', 'G', 'I']))
  //----
  PbsgActivation_TEST(11, 'Activate I')
  pbsgActivateButton('I')
  Ltrace('TEST11', _hasExpectedState('I', ['B', 'F', 'G']))
  //----
  PbsgActivation_TEST(12, 'Activate Predecessor')
  pbsgActivatePredecessor()
  Ltrace('TEST12', _hasExpectedState('B', ['I', 'F', 'G']))
  //----
  atomicState.logLevel = parkLogLevel
}
