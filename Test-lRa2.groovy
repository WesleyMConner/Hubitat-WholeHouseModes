// ---------------------------------------------------------------------------------
// ( L U T R O N )   R A 2
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
#include wesmc.lRa2

definition (
  name: 'Test-lRa2',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Preview lRa2 functionality',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(name: 'Ra2Page')
}

void solicitRa2IntegrationReport() {
  input(
    name: 'ra2IntegReport',
    title: 'Paste in the Lutron RA2 Integration Report',
    type: 'textarea',
    rows: 5,
    submitOnChange: true,
    required: true,
    multiple: false
  )
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
    //-> atomicState.remove('..')
    //---------------------------------------------------------------------------------
    app.updateLabel('Ra2TestPage')
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      atomicState.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
      solicitRa2IntegrationReport()
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

void initialize() {
  // - The same keypad may be associated with two different, specialized handlers
  //   (e.g., mode changing buttons vs special functionalily buttons).
  logWarn('initialize', 'Entered')

  settings.ra2Repeaters.each{ device ->
  logInfo('initialize', "Subscribing >${device}< to ra2RepHandler")
    subscribe(device, ra2RepHandler, ['filterEvents': true])
  }
  parseRa2IntegRpt()
}

//-> String normalizeCsv(String raw) {
//->   raw?.trim()?.replaceAll(', ', ',')
//-> }

Boolean kpadSupportsLedEvents(String ra2Model) {
  return [
    'RR-MAIN-REP-WH',
    'RR-T15RL-SW',
    'RRD-H6BRL-WH',
    'RRD-W7B-WH'
  ].findAll{ it == ra2Model } ? true : false
}
