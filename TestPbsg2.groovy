// ---------------------------------------------------------------------------------
// P B S G 2
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

import com.hubitat.hub.domain.Event as Event

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include wesmc.lHExt
#include wesmc.lHUI

definition (
  name: 'TestPbsg2',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Preview Pbsg functionality',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(name: 'TestPbsgPage')
}

void solicitPbsgName(String pbsgName) {
  input(
    name: pbsgName,
    title: 'PBSG:',
    type: 'text',
    submitOnChange: true,
    required: true,
    multiple: false,
    width: 2
  )
}

void solicitButtons(String pbsgButtons) {
  input(
    name: pbsgButtons,
    title: 'BUTTONS (space delimited)',
    type: 'text',
    submitOnChange: true,
    required: true,
    multiple: false,
    width: 6
  )
}

void solicitDefault(String pbsgDefault, ArrayList plist) {
  input(
    name: pbsgDefault,
    title: 'Default (optional)',
    type: 'enum',
    submitOnChange: true,
    required: false,
    multiple: false,
    options: plist,
    width: 2
  )
}

void solicitActive(String pbsgActive, ArrayList plist) {
  input(
    name: pbsgActive,
    title: 'Active',
    type: 'enum',
    submitOnChange: true,
    required: false,
    multiple: false,
    options: plist,
    width: 2
  )
}

// (1) Use the following function to adjust one PBSG map.
// (2) Increment the Integer argument for multiple PBSG maps.
// (3) Collect these PBSG maps in a map of PBSGs (state.pbsgs).
// (4) Present the state.pbsgs Map for edits + one extra empty row.
// (5) If a PBSG key is dropped, make sure it is not added to state.pbsgs.

// settings.pbsgButtons?.tokenize(' '),
Map solicitPbsgData(Integer i) {
  result = [:]
  String nameKey = "pbsgName^${i}"
  solicitPbsgName(nameKey)
  String nameValue = settings."${nameKey}"
  if (nameValue) {
    result."${nameValue}" = [:]
    //-> logInfo('#93', "nameKey: ${nameKey} -> ${nameValue}")
    String buttonsKey = "pbsgButtons^${i}"
    solicitButtons(buttonsKey)
    String buttonsValue = settings."${buttonsKey}"
    ArrayList buttonList = buttonsValue?.tokenize(' ')
    result."${nameValue}".buttons = buttonList
    //-> logInfo('#100', "buttonList: ${buttonList}")
    String defaultKey = "pbsgDefault^${i}"
    solicitDefault(defaultKey, buttonList)
    String defaultValue = settings."${defaultKey}"
    result."${nameValue}".default = defaultValue
    //-> logInfo('#105', "defaultKey: ${defaultKey} -> ${defaultValue}")
    String activeKey = "pbsgActive^${i}"
    solicitActive(activeKey, buttonList)
    String activeValue = settings."${activeKey}"
    result."${nameValue}".active = activeValue
    //-> logInfo('#110', "activeKey: ${activeKey} -> ${activeValue}")
  }
  //--> logInfo('#112', "result: ${result}")
  return result
}

Map TestPbsgPage() {
  return dynamicPage(
    name: 'TestPbsgPage',
    title: [
      heading1("TestPbsgPage - ${app.id}"),
      bullet1('Click <b>Done</b> to enable subscriptions.')
    ].join('<br/>'),
    install: true,
    uninstall: true
  ) {
    //---------------------------------------------------------------------------------
    // REMOVE NO LONGER USED SETTINGS AND STATE
    //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    //-> app.removeSetting('..')
    //-> state.remove('..')
    //---------------------------------------------------------------------------------
    app.updateLabel('TestPbsgPage')
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      state.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
      state.pbsgs = solicitPbsgData(0)
      Map m = state.pbsgs
      m << solicitPbsgData(1)
      state.pbsg = m
  logInfo('#139', "state.pbsg: ${state.pbsg}")
      //state.pbsgs = m
      /*
      pbsgs.eachWithIndex{ name, map, i ->
        logInfo('TestPbsgPage (108)', "name: ${name}, map: >${map}<, i: ${i}")
        String pbsgName = "pbsgName^${i}"
        solicitPbsgName(pbsgName)
        if (settings."pbsgName^${i}") {
          solicitButtons("pbsgButtons^${i}")
          solicitDefault("pbsgDefault^${i}")
          solicitActive("pbsgActive^${i}")
          settings."pbsgButtons^${i}" = [
            buttons: settings."pbsgButtons^${i}"?.tokenize(/\s+/),
            dflt: settings."pbsgDefault^${i}",
            active: settings."pbsgActive^${i}"
          ]
        }
      }
      Boolean keepGoing = true
      while (keepGoing) {
        solicitPbsgName('pbsgName')
        if (settings.pbsgName) {
          solicitButtons('pbsgButtons')
          solicitDefault('pbsgDefault')
          solicitActive('pbsgActive')
          pbsgs."${settings.pbsgName}" = [
            buttons: settings.pbsgButtons?.tokenize(/\s+/),
            dflt: settings.pbsgDefault,
            active: settings.pbsgActive
          ]
        }
        keepGoing = false
      }
      */
    }
  }
}

Map pbsgs() {
  if (!state.pbsgs) {
    logWarn('pbsgs', 'Creating null state.pbsgs map.')
    state.pbsgs = [:]
  }
  return state.pbsgs
}

void initialize() {
}


Map Ra2Page() {
  return dynamicPage(
    name: 'Ra2Page',
    title: [
      heading1("Ra2 Test Page - ${app.id}"),
      bullet1('Click <b>Done</b> to enable subscriptions.')
    ].join('<br/>'),
    install: true,
    uninstall: true
  ) {
    //---------------------------------------------------------------------------------
    // REMOVE NO LONGER USED SETTINGS AND STATE
    //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    //-> app.removeSetting('..')
    //-> state.remove('..')
    //---------------------------------------------------------------------------------
    app.updateLabel('Ra2TestPage')
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      state.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
    }
  }
}

void installed() {
  logWarn('installed', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled() {
  logWarn('uninstalled', 'Entered')
  removeAllChildApps()
}

void updated() {
  logWarn('updated', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}
