// ---------------------------------------------------------------------------------
// T E S T   P B S G   C O R E
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

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include wesmc.lFifo
#include wesmc.lHExt
#include wesmc.lHUI
#include wesmc.lPbsg

definition (
  name: 'TestPbsg',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Implement PBSG functions and is authoritative for pushbutton state.',
  singleInstance: true
)

preferences {
  page(name: 'TestPbsgPage')
}

//---- SYSTEM CALLBACKS

void installed () {
  app.pbsgCoreInstalled()
  TEST_pbsgCoreFunctionality()
}

void updated () {
  pbsgCoreUpdated()
}

void uninstalled () {s
  pbsgCoreUninstalled()
}

//---- RENDERING AND DISPLAY

Map TestPbsgPage () {
  return dynamicPage(
    name: 'TestPbsgPage',
    title: heading1(appInfo(app)),
    install: true,
    uninstall: true,
  ) {
    section {
      paragraph "YOU ARE HERE"
    }
  }
}

//---- TEST SUPPORT

void TEST_pbsgConfigure (
    Integer n,
    ArrayList<String> list,
    String dflt,
    String on,
    String forcedError = null
  ) {
  // Logs display newest to oldest; so, write logs backwards
  ArrayList<String> logMsg = []
  if (forcedError) logMsg += forcedError
  logMsg += "dnis=${b(list)}, dfltButton=${b(dflt)}, activeDni=${b(on)}"
  logMsg += (forcedError ? redBar() : greenBar())
  logInfo("TEST ${n} CONFIG", logMsg)
  // Simulate a Page update (GUI settings) via the System updated() callback.
  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE' .. 'TRACE' for HEAVY DEBUG
  pbsgConfigure(list, dflt, on, 'INFO')
}

void TEST_PbsgActivation (
    Integer n,
    String description,
    String forcedError = null
  ){
  // Logs display newest to oldest; so, write logs backwards
  ArrayList<String> logMsg = []
  logMsg += description
  if (forcedError) { logMsg += forcedError }
  logMsg += (forcedError ? redBar() : greenBar())
  logInfo("TEST ${n} ACTION", logMsg)
}

void TEST_pbsgHasExpectedState (
    Integer n,
    String activeButton,
    ArrayList<String> inactiveButtons,
    String dfltButton
  ) {
  String activeDni = activeButton ? buttonToDni(activeButton) : null
  ArrayList<String> inactiveDnis = inactiveButtons ? inactiveButtons.collect{ buttonToDni(it) } : null
  String dfltDni = dfltButton ? buttonToDni(dfltButton) : null
  Boolean result = true
  Integer actualInactiveDnisSize = state.inactiveDnis?.size() ?: 0
  Integer expectedInactiveDnisSize = inactiveDnis?.size() ?: 0
  ArrayList<String> testLog = []
  testLog += heading2('PBSG State Checks')
  if (state.dfltDni != dfltDni) {
    result = false
    testLog += bullet2("dfltDni ${state.dfltDni} != ${dfltDni}")
  } else if (state.activeDni != activeDni) {
    result = false
    testLog += bullet2("activeDni ${state.activeDni} != ${activeDni}")
  } else if (actualInactiveDnisSize != expectedInactiveDnisSize) {
    result = false
    testLog += bullet2("inActiveDnis size ${actualInactiveDnisSize} != ${expectedInactiveDnisSize}")
    testLog += bullet2("expected: ${inactiveDnis }} got: ${state.inactiveDnis}")
  } else {
    state.inactiveDnis.eachWithIndex{ dni, index ->
      String expectedDni = inactiveDnis[index]
      if (dni != expectedDni) {
        result = false
        testLog += bullet2("At ${index}: inactiveDni ${dni} != ${expectedDni}")
      }
    }
  }
  if (state.activeDni == activeDni) {
    testLog += bullet2("<i>activeDni: ${state.activeDni}</i>")
  } else {
    testLog += bullet2("<i>activeDni: ${state.activeDni}</i> => <b>expected: ${activeDni}</b>")
  }
  if ((state.inactiveDnis == inactiveDnis) || (!state.inactiveDnis && !inactiveDnis)) {
    testLog += bullet2("<i>inactiveDnis: ${state.inactiveDnis}</i>")
  } else {
    testLog += bullet2("<i>inactiveDnis:</b> ${state.inactiveDnis}</i> => <b>expected: ${inactiveDnis}</b>")
  }
  if(state.dfltDni == dfltDni) {
    testLog += bullet2("<i>dfltDni: ${state.dfltDni}</i>")
  } else {
    testLog += bullet2("<i>dfltDni: ${state.dfltDni}</i> => <b>expected: ${dfltDni}</b>")
  }
  testLog += heading2('Button (VSW) State Checks')
  ArrayList<String> buttonState = []
  if (activeButton) {
    String state = switchState(getChildDevice(buttonToDni(activeButton)))
    Boolean isExpected = (state == 'on')
    state = (state == 'on') ? '<b>on</b>' : '<i>off</i>'
    if (!isExpected) result = false
    buttonState += "${activeButton}: ${state} (${isExpected ? '✅' : '❌'})"
  }
  inactiveButtons.each{ offButton ->
    String state = switchState(getChildDevice(buttonToDni(offButton)))
    Boolean isExpected = (state == 'off')
    if (!isExpected) result = false
    buttonState += "${offButton}: ${state} (${isExpected ? '✅' : '❌'})"
  }
  testLog += buttonState.join(', ')
  // results.join('<br/>')
  logInfo(
    "TEST ${n} ${ result ? 'passed' : '<b>F A I L E D</b>' }",
    "<br/>${testLog.join('<br/>')}"
  )
}

void TEST_pbsgCoreFunctionality () {
  TEST_pbsgConfigure(1, [], 'A', 'B', '<b>Forced Error:</b> "Inadequate parameters"')
  TEST_pbsgHasExpectedState(1, null, [], null)
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(2, ['A', 'B', 'C', 'D', 'E'], '', null)
  TEST_pbsgHasExpectedState(2, null, ['A', 'B', 'C', 'D', 'E'], null)
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(3, ['A', 'B', 'C', 'D', 'E'], 'B', null)
  TEST_pbsgHasExpectedState(3, 'B', ['A', 'C', 'D', 'E'], 'B')
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(4, ['A', 'C', 'D', 'E'], 'B', null, '<b>Forced Error:</b> "Default not in DNIs"')
  TEST_pbsgHasExpectedState(4, 'B', ['A', 'C', 'D', 'E'], 'B')
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(5, ['B', 'C', 'D', 'E', 'F'], '', 'C')
  TEST_pbsgHasExpectedState(5, 'C', ['B', 'D', 'E', 'F'], null)
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(6, ['B', 'F', 'G', 'I'], 'B', 'D', '<b>Forced Error:</b> "Active not in DNIs"')
  TEST_pbsgHasExpectedState(6, 'C', ['B', 'D', 'E', 'F'], null)
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(7, ['B', 'F', 'G', 'I'], 'B', 'G')
  TEST_pbsgHasExpectedState(7, 'G', ['B', 'F', 'I'], 'B')
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  // WITHOUT CHANGING THE CONFIGURATION, START TESTING ACTIVATION OF BUTTONS
  // THE DEFAULT BUTTON REMAINS 'B'
  //----
  TEST_PbsgActivation(8, "With 'G', ['*B', 'F', 'I'], Activate F")
  pbsgActivateButton('F')
  TEST_pbsgHasExpectedState(8, 'F', ['G', 'B', 'I'], 'B')
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_PbsgActivation(9, "With 'F', ['G', '*B', 'I'], Activate Q", '<b>Forced Error:</b> "Button does not exist"')
  pbsgActivateButton('Q')
  TEST_pbsgHasExpectedState(9, 'F', ['G', 'B', 'I'], 'B')
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_PbsgActivation(10, "With 'F', ['G', '*B', 'I'], Deactivate F")
  pbsgDeactivateButton('F')
  TEST_pbsgHasExpectedState(10, 'B', ['F', 'G', 'I'], 'B')
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_PbsgActivation(11, "With '*B', ['F', 'G', 'I'], Activate I")
  pbsgActivateButton('I')
  TEST_pbsgHasExpectedState(11, 'I', ['B', 'F', 'G'], 'B')
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_PbsgActivation(12, "With 'I', ['*B', 'F', 'G'], Activate Predecessor")
  pbsgActivatePrior()
  TEST_pbsgHasExpectedState(12, 'B', ['I', 'F', 'G'], 'B')
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(13, ['B', 'X', 'C', 'E', 'Z'], '', 'C')
  TEST_pbsgHasExpectedState(13, 'C', ['B', 'X', 'E', 'Z'], null)
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_PbsgActivation(14, "With 'C', ['B', 'X', 'E', 'Z'], Deactivate C")
  pbsgDeactivateButton('C')
  TEST_pbsgHasExpectedState(14, null, ['C', 'B', 'X', 'E', 'Z'], null)
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_PbsgActivation(15, "With null, ['C', 'B', 'X', 'E', 'Z'], Activate Predecessor")
  pbsgActivatePrior()
  TEST_pbsgHasExpectedState(15, 'C', ['B', 'X', 'E', 'Z'], null)
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(16, ['B', '', null, 'A', 'G', 'X', null, 'A'], 'X', '')
  TEST_pbsgHasExpectedState(16, 'X', ['B', 'A', 'G'], 'X')
  unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
  TEST_pbsgConfigure(17, ['B', 'A', 'G', 'X'], 'X', 'G')
  TEST_pbsgHasExpectedState(17, 'G', ['X', 'B', 'A'], 'X')
  //--> Stay subscribed for manual Dasboard testing.
  //--> unsubscribe()  // Suspend ALL events that might arise from the last test case.
  //----
}
