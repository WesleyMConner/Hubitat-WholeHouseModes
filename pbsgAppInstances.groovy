// ---------------------------------------------------------------------------------
// P B S G   -   G R O U P E D   P U S H B U T T O N   S W I T C H E S
//
//   Copyright (C) 2023-Present Wesley M. Conner
//
//   LICENSE
//     Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
//     "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
//     not use this file except in compliance with the License. Unless
//     required by applicable law or agreed to in writing, software
//     distributed under the License is distributed on an "AS IS" BASIS,
//     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
//     implied.
// ---------------------------------------------------------------------------------
#include wesmc.libLogAndDisplay

definition(
  name: "pbsgAppInstances",
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'This application groups PBSG App instances in Hubitat',
  category: '',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: '',
  singleInstance: true
)

preferences {
  page(name: "PbsgAppInstancesPage", title: "", install: true, uninstall: false)
}

//----
//---- SYSTEM CALLBACKS
//----   Methods specific to this execution context
//----

void installed() {
}

void updated() {
}

void uninstall () {
}

//----
//---- RENDERING AND DISPLAY
//----   Methods specific to this execution context
//----

Map PbsgAppInstancesPage() {
  // This App instance is NEVER retrieved by its label, so an update is okay.
  app.updateLabel('PBSG App Instances')
  // Hardwire log level given the expected low logging volume.
  atomicState.logLevel = lookupLogLevel('TRACE')
  return dynamicPage(
    name: 'PBSG App Instances',
    install: true,
    uninstall: false,
  ) {
    section {
      if (app.getInstallationState() != 'COMPLETE') {
        paragraph(
          [
            Heading1('Install PBSG'),
            Bullet1('Push the <b>Done</b> button to install this App'),
            Bullet1('This "parent" App collects PBSG instances on the Hubitat Apps page'),
            Bullet1('PBSG App instances are created by and owned by other Apps'),
            Bullet2('See the single modePbsg instance in wha.groovy'),
            Bullet2('See the multiple {roomName}Pbsg instances in roomScenePbsg.groovy')
          ].join('<br/>')
        )
      } else {
        paragraph(
          [
            Heading1('Other Apps typically create one or more PBSG App Instances'),
            Bullet1('App instances will appear below on this page ... TBD'),
            '',
            '<b>EXAMPLE</b>',
            '&nbsp;&nbsp;<b>Example Pending</b>',
            '',
            Bullet2("Use Browser's Back Button to return to exit this page.")
          ].join('<br/>')
        )
      }
    }
  }
}
