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

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include wesmc.lUtils
#include wesmc.lRa2IRParser

definition (
  name: 'Demo-Ra2IRParser',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Preview lRa2 functionality',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(
    name: 'Ra2Parser',
    title: h1("Ra2 Integration Report Parser (Ra2IRParser) Demo (${app.id})"),
    install: true,
    uninstall: true
  ) {
    //---------------------------------------------------------------------------------
    // Per https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    //-> app.removeSetting('..')
    //-> atomicState.remove('..')
    //---------------------------------------------------------------------------------
    app.updateLabel("Demo-Ra2IRParser (${app.id})")
    section {
      paragraph([
        h2('Instructions'),
        bullet1("Download a RadioRA 2 ${bi('Integration Report')} (use menu ${bi('Reports')} → ${bi('Integration')})."),
        bullet1("Open the downloaded ${bi('Integration Report')}."),
        bullet1("Copy-paste the whole ${bi('Integration Report')} \"as is\" below."),
        bullet1("Click ${bi('Done')} to begin parsing."),
        bullet1("Allow a few seconds for results to appear in the ${bi('Hubitat log')}.")
      ].join('<br/>'))
      solicitLogThreshold('appLogThresh', 'INFO')  // ERROR, WARN, INFO, DEBUG, TRACE
      atomicState.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
      solicitRa2IntegrationReport()
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
  if (settings.ra2IntegReport) {
    Map results = parseRa2IntegRpt(settings.ra2IntegReport, true)
    logRa2Results(results, 'ra2Devices')
    logRa2Results(results, 'kpads')
    logRa2Results(results, 'ra2Rooms')
    logRa2Results(results, 'circuits')
    logRa2Results(results, 'timeclock')
    logRa2Results(results, 'green')
  } else {
    logError('initiaize', 'No content was supplied for "settings.ra2IntegReport"')
  }
}
