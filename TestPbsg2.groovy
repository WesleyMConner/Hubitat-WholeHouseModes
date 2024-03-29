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
    title: '<b>PBSG NAME</b>',
    type: 'text',
    submitOnChange: true,
    required: false,
    multiple: false,
    width: 2
  )
}

void solicitPbsgButtons(String pbsgButtons) {
  input(
    name: pbsgButtons,
    title: '<b>PBSG BUTTONS</b> (space delimited)',
    type: 'text',
    submitOnChange: true,
    required: false,
    multiple: false,
    width: 6
  )
}

void solicitDefaultButton(String pbsgDefault, ArrayList plist) {
  input(
    name: pbsgDefault,
    title: '<b>DEFAULT BUTTON</b>',
    type: 'enum',
    submitOnChange: true,
    required: false,
    multiple: false,
    options: plist,
    width: 2
  )
}

void solicitActiveButton(String pbsgActive, ArrayList plist) {
  input(
    name: pbsgActive,
    title: '<b>ACTIVE BUTTON</b>',
    type: 'enum',
    submitOnChange: true,
    required: false,
    multiple: false,
    options: plist,
    width: 2
  )
}

Map solicitPbsgInitData(Integer i) {
  pbsg = [:]
  String nameKey = "pbsgName^${i}"
  solicitPbsgName(nameKey)
  String nameValue = settings."${nameKey}"
  if (nameValue) {
    String buttonsKey = "pbsgButtons^${i}"
    String defaultKey = "pbsgDefault^${i}"
    String activeKey = "pbsgActive^${i}"
    solicitPbsgButtons(buttonsKey)
    String buttonsValue = settings."${buttonsKey}"
    ArrayList buttonList = buttonsValue?.tokenize(' ')
    solicitDefaultButton(defaultKey, buttonList)
    solicitActiveButton(activeKey, buttonList)
    pbsg."${nameValue}" = [
      buttons: buttonList,
      defaultButton: settings."${defaultKey}",
      activeButton: settings."${activeKey}"
    ]
  } else {
     paragraph('', width: 10)  // Filler for a 12 cell row
  }
  return pbsg
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
      Map pbsgs = [:]
      for (i in [0, 1, 2, 3]) {
        Map pbsg = solicitPbsgInitData(i)
        pbsg.each{ name, values ->
          if (values.defaultButton) {
            pbsg."${name}".defaultDni = "${name}_${values.defaultButton}"
          }
          if (values.activeButton) {
            pbsg."${name}".activeDni = "${name}_${values.activeButton}"
          }
          ArrayList dnis = []
          ArrayList devices = []
          values.buttons.each{ button ->
            String buttonDni = "${name}_${button}"
            DevW device = getChildDevice(buttonDni)
            if (!device) {
              logWarn('TestPbsgPage', "Adding child device '${buttonDni}'")
              device = addChildDevice(
                'hubitat',          // namespace
                'Virtual Switch',   // typeName
                buttonDni,          // device's unique DNI
                [isComponent: true, name: buttonDni]
    )
            }
            dnis << buttonDni
            devices << device
          }
          pbsg."${name}".dnis = dnis
          pbsg."${name}".devices = devices
        }
        pbsgs << pbsg
      }
      paragraph("<b>pbsgs</b>: ${pbsgs}")
    }
  }
}

// def list = ['a', 'b', 'c']
// def map = list.collectEntries { el -> [(el): { println el }] }
// map.b()

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
