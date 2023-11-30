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
#include wesmc.libFifo
#include wesmc.libHubExt
#include wesmc.libHubUI
#include wesmc.libPbsgCore

definition (
  name: 'TestPbsgCore',
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
  page(name: 'TestPbsgCorePage')
}

//---- SYSTEM CALLBACKS

void installed () {
  app.pbsgCoreInstalled()
  TEST_pbsgCoreFunctionality()
}

void updated () {
  pbsgCoreUpdated(app)
}

void uninstalled () {
  app.pbsgCoreUninstalled()
}


//---- RENDERING AND DISPLAY

Map TestPbsgCorePage () {
  return dynamicPage(
    name: 'TestPbsgCorePage',
    title: Heading1(AppInfo(app)),
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
  List<String> logMsg = []
  logMsg += description
  if (forcedError) { logMsg += forcedError }
  logMsg += (forcedError ? REDBAR() : GREENBAR())
  Linfo("TEST ${n} ACTION", logMsg)
}

void TEST_pbsgHasExpectedState (
    Integer n,
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
  List<String> testLog = []
  testLog += Heading2('PBSG State Checks')
  if (state.dfltDni != dfltDni) {
    result = false
    testLog += Bullet2("dfltDni ${state.dfltDni} != ${dfltDni}")
  } else if (state.activeDni != activeDni) {
    result = false
    testLog += Bullet2("activeDni ${state.activeDni} != ${activeDni}")
  } else if (actualInactiveDnisSize != expectedInactiveDnisSize) {
    result = false
    testLog += Bullet2("inActiveDnis size ${actualInactiveDnisSize} != ${expectedInactiveDnisSize}")
    testLog += Bullet2("expected: ${inactiveDnis }} got: ${state.inactiveDnis}")
  } else {
    state.inactiveDnis.eachWithIndex{ dni, index ->
      String expectedDni = inactiveDnis[index]
      if (dni != expectedDni) {
        result = false
        testLog += Bullet2("At ${index}: inactiveDni ${dni} != ${expectedDni}")
      }
    }
  }
  if (state.activeDni == activeDni) {
    testLog += Bullet2("<i>activeDni: ${state.activeDni}</i>")
  } else {
    testLog += Bullet2("<i>activeDni: ${state.activeDni}</i> => <b>expected: ${activeDni}</b>")
  }
  if ((state.inactiveDnis == inactiveDnis) || (!state.inactiveDnis && !inactiveDnis)) {
    testLog += Bullet2("<i>inactiveDnis: ${state.inactiveDnis}</i>")
  } else {
    testLog += Bullet2("<i>inactiveDnis:</b> ${state.inactiveDnis}</i> => <b>expected: ${inactiveDnis}</b>")
  }
  if(state.dfltDni == dfltDni) {
    testLog += Bullet2("<i>dfltDni: ${state.dfltDni}</i>")
  } else {
    testLog += Bullet2("<i>dfltDni: ${state.dfltDni}</i> => <b>expected: ${dfltDni}</b>")
  }
  testLog += Heading2('Button (VSW) State Checks')
  List<String> buttonState = []
  if (activeButton) {
    String state = SwitchState(getChildDevice(_buttonToDni(activeButton)))
    Boolean isExpected = (state == 'on')
    state = (state == 'on') ? '<b>on</b>' : '<i>off</i>'
    if (!isExpected) result = false
    buttonState += "${activeButton}: ${state} (${isExpected ? '✅' : '❌'})"
  }
  inactiveButtons.each{ offButton ->
    String state = SwitchState(getChildDevice(_buttonToDni(offButton)))
    Boolean isExpected = (state == 'off')
    if (!isExpected) result = false
    buttonState += "${offButton}: ${state} (${isExpected ? '✅' : '❌'})"
  }
  testLog += buttonState.join(', ')
  // results.join('<br/>')
  Linfo(
    "TEST ${n} ${ result ? 'passed' : '<b>F A I L E D</b>' }",
    "<br/>${testLog.join('<br/>')}"
  )
}

void TEST_pbsgCoreFunctionality () {
  //-> FifoTest()
  //----
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
  pbsgActivatePredecessor()
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
  pbsgActivatePredecessor()
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
