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
//   - pbsgConfig
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

void pbsgConfig (
    List<String> requestedButtons,
    String defaultButton = null,
    String activeButton = null
  ) {
  if (!requestedButtons) {
    Lerror('pbsgConfig()', '<b>No buttons have been defined.</b>')
    return
  }
  if (defaultButton && !requestedButtons.contains(defaultButton)) {
    Lerror('pbsgConfig()', [
      '<b>Problematic defaultButton</b>',
      "${b(defaultButton)} IS NOT present in ${b(requestedButtons)}"
    ])
    return
  }
  if (activeButton && !requestedButtons.contains(activeButton)) {
    Lerror('pbsgConfig()', [
      '<b>Problematic activeButton</b>',
      "${b(activeButton)} IS NOT present in ${b(requestedButtons)}"
    ])
    return
  }
  atomicState.defaultButton = defaultButton
  List<String> existingButtons = _pbsgExistingButtons()
  Map<String, List<String>> actions = CompareLists(
    existingButtons,
    requestedButtons
  )
  List<String> retainButtons = actions.retained
  List<String> dropButtons = actions.dropped
  List<String> addButtons = actions.added
  Ltrace('pbsgConfig()', [
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
    // Remove out-of-scope buttons without activating any button.
    if (dropButtons.contains(atomicState.activeButton)) {
      atomicState.activeButton = null
    }
    atomicState.inactiveButtons.removeAll(dropButtons)
  }
  if (addButtons) {
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
  //=============================== REWORKED ===============================//
  Ltrace('pbsgConfig()', [
    'REWORK CHECKPOINT',
    *pbsgState()
  ])

/*
  app.unsubscribe()
  // Reconcile existing state to possibly-revised buttons list.
  if (buttons.size() > 0) {
    if (defaultButton && buttons?.contains(defaultButton) == false) {
      Lerror(
        'pbsgConfig()',
        "defaultButton (${b(defaultButton)}) not present in buttons (${b(buttons)})"
      )
    } else {
      // Initialize dropped.. lists
      List<String> droppedactiveButtons = atomicState.activeButton.collect { it }
      List<String> droppedinactiveButtons = atomicState.inactiveButtons.collect { it }
      // Potentially retain some buttons (and their state).
      atomicState.activeButton.retainAll(buttons)
      atomicState.inactiveButtons.retainAll(buttons)
      droppedactiveButtons.removeAll(atomicState.activeButton)
      if (droppedactiveButtons.size() > 0) {
        Ltrace('pbsgConfig()', "Dropped 'On' button(s): ${b(droppedactiveButtons)}")
      }
      droppedinactiveButtons.removeAll(atomicState.inactiveButtons)
      if (droppedinactiveButtons.size() > 0) {
        Ltrace('pbsgConfig()', "Dropped 'Off' buttons: ${b(droppedinactiveButtons)}")
      }
      // Identify and add any new buttons
      List<String> newButtons = buttons.collect{ it }
      newButtons.removeAll(atomicState.activeButton)
      newButtons.removeAll(atomicState.inactiveButtons)
      if (newButtons.size() > 0) {
        newButtons.each{ button -> atomicState.inactiveButtons.push(button) }
        Ltrace('pbsgConfig()', "Added (off) button(s): ${b(newButtons)}")
      }
      // If an activeButton was supplied, turn it on.
      // Else, if the activeButtonFifo queue is empty, turn on the default button.
      String turnOnTarget = null
      if (activeButton != null) {                          // No Java Truth (atomicState)
        turnOnTarget = activeButton
      } else if (atomicState.defaultButton != null) {  // No Java Truth (atomicState)
        turnOnTarget = atomicState.defaultButton
      }
      Ltrace('pbsgConfig()', "turnOnTarget: ${b(turnOnTarget)}")
      if (turnOnTarget) {
        Ltrace('pbsgConfig()', 'Entered the turnOnTarget block')
        if (atomicState.activeButton.contains(turnOnTarget)) {
          // Ensure activeButton is the last item in the Fifo queue
          Ltrace(
            'pbsgConfig()',
            "Adjusting ${b(turnOnTarget)} position in activeButtonFifo."
          )
          atomicState.activeButton.removeAll(turnOnTarget)
          atomicState.activeButton.push(turnOnTarget)
        } else {
          // Move activeButton from off FIFO to on FIFO
          Ltrace(
            'pbsgConfig()',
            "Move ${b(turnOnTarget)} from inactiveButtons to activeButtonFifo."
          )
          atomicState.activeButton.removeAll(turnOnTarget)
          atomicState.activeButton.push(turnOnTarget)
        }
      }
      _pbsgEnforceControls()
      Ltrace('pbsgConfig()', [
        'Calling _pbsgPublishEvent()',
        *pbsgState()
      ])
      _pbsgPublishEvent()
    }
  } else {
    Lerror('pbsgConfig()', "The '<b>buttons</b>' argument is null")
  }
*/
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

void pbsgActivateButton (String button) {
  if (!_pbsgExistingButtons().contains(button)) {
    Lerror('pbsgActivateButton()', "Argument ${b(button)} IS NOT a button")
  } else if (atomicState.activeButton == button) {
    // Do nothing. The button is already active.
  } else {
    // Move active button to the front of the inactive FIFO queue.
    atomicState.inactiveButtons.push(atomicState.activeButton)
    Ltrace('pbsgActivateButton()', "${b(atomicState.activeButton) is inactive}")
    // Move button from inactiveButtons to activeButton.
    atomicState.inactiveButtons.removeAll(button)
    atomicState.activeButton = button
    Ltrace('pbsgActivateButton()', "${b(button) is active}")
  }
}

void pbsgDeactivateButton (String button) {
  if (atomicState.activeButton != button) {
    // Do nothing. The button is already inactive.
  } else {
    // Move active button to the front of the inactive FIFO queue.
    atomicState.inactiveButtons.push(atomicState.activeButton)
    Ltrace('pbsgDeactivateButton()', "${b(atomicState.activeButton) is inactive}")
    // Activate the defaultButton (which may exist OR may be null).
    atomicState.activeButton = atomicState.defaultButton
    Ltrace('pbsgDeactivateButton()', "${b(atomicState.defaultButton) is active}")
  }
}

void pbsgActivatePredecessor () {
  // Swap the currently active button with the front-most inactive button
  String temp = atomicState.inactiveButtons.pop()
  atomicState.inactiveButtons.push(atomicState.activeButton)
  Ltrace('pbsgActivatePredecessor()', "${b(atomicState.activeButton) is inactive}")
  atomicState.activeButton = temp
  Ltrace('pbsgActivatePredecessor()', "${b(temp) is active}")
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

void PbsgCore_TEST () {
  String parkLogLevel = atomicState.logLevel
  atomicState.logLevel = LogThresholdToLogLevel('TRACE')
  String B = '<hr style="border: 5px solid green;"/>'
  Ltrace('PbsgCore_TEST()', ['At Entry', *pbsgState()])
  Ltrace('PbsgCore_TEST()', [
    '',
    '=====> CONFIG 1 Test <b>Error Condition</b> "no buttons"',
    B
  ])
  pbsgConfig([], 'A', 'B')
  Ltrace('PbsgCore_TEST()', [
    '',
    "=====> CONFIG 2: buttons=[A, B, C, D, E], dflt='', activeButton=null",
    B
  ])
  pbsgConfig(['A', 'B', 'C', 'D', 'E'], '')
  Ltrace('PbsgCore_TEST()', [
    '',
    '=====> CONFIG 3: buttons=[A, B, C, D, E], dflt=B, activeButton=null',
    B
  ])
  pbsgConfig(['A', 'B', 'C', 'D', 'E'], 'B')
  Ltrace('PbsgCore_TEST()', [
    '',
    '=====> CONFIG 4 Test <b>Error Condition</b> default IS NOT in the button list',
    B
  ])
  pbsgConfig(['A', 'B', 'C', 'D', 'E'], 'F')
  Ltrace('PbsgCore_TEST()', [
    '',
    "=====> CONFIG 5: [A, B, C, D, E, F], dflt='', activeButton=C",
    B
  ])
  pbsgConfig(['A', 'B', 'C', 'D', 'E', 'F'], '', 'C')
  Ltrace('PbsgCore_TEST()', [
    '',
    '=====> CONFIG 6: Test <b>Error Condition</b> active button IS NOT in the button list',
    B
  ])
  pbsgConfig(['B', 'F', 'G', 'I'], 'B', 'D')
  Ltrace('PbsgCore_TEST()', [
    '',
    "=====> CONFIG 7: [B, F, G, I], dflt='B', activeButton=''",
    B
  ])
  pbsgConfig(['B', 'F', 'G', 'I'], 'B', '')
  Ltrace('PbsgCore_TEST()', [
    '',
    '=====> Turn on F',
    B
  ])
  pbsgActivateButton('F')
  Ltrace('PbsgCore_TEST()', [
    '',
    '=====> Turn off Q (which does not exist)',
    B
  ])
  pbsgDeactivateButton('Q')
  Ltrace('PbsgCore_TEST()', [
    '',
    '=====> Turn off F',
    B
  ])
  pbsgDeactivateButton('F')
  Ltrace('PbsgCore_TEST()', [
    '',
    '=====> Turn on I',
    B
  ])
  pbsgActivateButton('I')
  atomicState.logLevel = parkLogLevel
}

// THE HUBITAT DISPLAY UI CAN BE USED TO CONSTRUCT AND ADJUST APP
// INSTANCES. THE SAMPLE CODE BELOW CAN ALSO CREATE AND DELETE INSTANCES

/*
M O N D A Y
- Keep it simple. Use Map toString fn for now.
- Later, Map<String, Bool> can have a fancier display

init()
- placeholders for atomicStateKeys
updated()
- pbsgState()
- publishLatestState()

pbsgCoreTest()
- pbsgState()
- configure() // Initial
- configure() // Revised
- turnOn()
- turnOff()
- forEach, turnOn()
- turnOn()
- turnOn()
- turnOff() // Redundant
- turnOff() // Only
*/
