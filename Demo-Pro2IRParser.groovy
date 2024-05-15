// ---------------------------------------------------------------------------------
// Demo-Pro2IRParser - Pro2 Integration Report Parser (Application)
//
// Copyright (C) 2023-Present Wesley M. Conner
//
// LICENSE
//   Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
//   "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
//   not use this file except in compliance with the License. Unless
//   required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
//   implied.
// ---------------------------------------------------------------------------------
import com.hubitat.hub.domain.Event as Event
import groovy.json.JsonSlurper

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include wesmc.lHExt
#include wesmc.lHUI
#include wesmc.lPro2IRParser

definition (
  name: 'Demo-Pro2IRParser',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Preview lPro2 functionality',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(
    name: 'Pro2Page',
    title: h1("Pro2 Integration Report Parser (Pro2IRParser) Demo (${app.id})"),
    install: true,
    uninstall: true
  ) {
    //---------------------------------------------------------------------------------
    // Per https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    //-> app.removeSetting('..')
    //-> atomicState.remove('..')
    //---------------------------------------------------------------------------------
  app.updateLabel("Demo-Pro2IRParser (${app.id})")
  section {
      solicitLogThreshold('appLogThresh', 'INFO')  // ERROR, WARN, INFO, DEBUG, TRACE
      atomicState.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
      solicitPro2IntegrationReport()
    }
  }
}

void installed() {
  unsubscribe()
  initialize()
}

void uninstalled() {
}

void updated() {
  unsubscribe()
  initialize()
}

void initialize() {
  logInfo('initialize', 'Calling parsePro2IntegRpt')
  if (settings.ra2IntegReport) {
    Map results = parseRa2IntegRpt(settings.pro2IntegReport, true)
    logRa2Results(results, 'pro2Devices')
    logRa2Results(results, 'kpads')
    logRa2Results(results, 'circuits')
  }
}
