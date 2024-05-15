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
// For reference:
//   Unicode 2190 ← LEFTWARDS ARROW
//   Unicode 2192 → RIGHTWARDS ARROW
import com.hubitat.hub.domain.Event as Event

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include wesmc.lHExt
#include wesmc.lHUI
#include wesmc.lPbsgV2

definition (
  name: 'TestPbsgV2',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Preview Pbsg functionality',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(name: 'TestPbsgV2')
}

void pbsg_ButtonOnCallback(Map pbsg) {
  logInfo('pbsg_ButtonOnCallback(...)', "Received button: ${pbsg.activeButton}")
}

// GUI

Map TestPbsgV2() {
  return dynamicPage(
    name: 'TestPbsgV2',
    title: [
      h1("TestPbsgV2 - ${app.id}"),
      bullet1('Click <b>Done</b> to enable subscriptions.')
    ].join('<br/>'),
    install: true,
    uninstall: true
  ) {
    //---------------------------------------------------------------------------------
    // REMOVE NO LONGER USED SETTINGS AND STATE
    //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    //-> app.removeSetting('..')
    //-> atomicState.remove('..')
    //---------------------------------------------------------------------------------
    app.updateLabel('TestPbsgV2')
    atomicState.remove('childVsws')
    section {
      atomicState.logLevel = logThreshToLogLevel('INFO')  // ERROR, WARN, INFO, DEBUG, TRACE
      //solicitLogThreshold('appLogThresh', 'INFO')  // ERROR, WARN, INFO, DEBUG, TRACE
      //atomicState.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
      // NOTE: atomicState.pbsgs are ALWAYS rebuilt from settings and child VSW discovery.
      // Create two PBSGs by Soliciting input data from a human
      for (i in [0, 1]) {
        Map config = config_SolicitInstance(i)
        if (config && config.name && config.allButtons) {
          // The PBSG is created and initialized as the Config is adjusted.
          // Normally, PBSG configs will be provided as a Map by the
          // application - i.e., NOT require user input via settings.
          Map pbsg = pbsg_BuildToConfig(config, 'testPbsg')
          paragraph "${pbsg_State(pbsg)}"
        } else {
          paragraph "PBSG creation is pending sufficient config data"
        }
      }
      // Create a third PBSG by hard-coding a configuration
      atomicState.TestPbsg = [
        'name': 'TestPbsg',
        'instType': 'pbsg',
        'allButtons': ['one', 'two', 'three', 'four', 'five', 'six'],
        'defaultButton': 'four'
      ]
      Map bruteForcePbsg = pbsg_BuildToConfig('TestPbsg')
      paragraph([
        h1('Debug'),
        *appStateAsBullets(),
        *appSettingsAsBullets(),
      ].join('<br/>'))
    }
  }
}

void initialize() {
}

void installed() {
  logWarn('installed', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled() {
  logWarn('uninstalled', 'Entered')
}

void updated() {
  logWarn('updated', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}
