// ---------------------------------------------------------------------------------
// R O O M   S C E N E   P B S G
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
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include wesmc.lFifo
#include wesmc.lHExt
#include wesmc.lHUI
#include wesmc.lPbsg

definition (
  parent: 'wesmc:RoomScenes',
  name: 'RSPbsg',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'A pbsgLibrary instance rooted in a Room Scenes instance',
  installOnOpen: false,
  documentationLink: '',  // TBD
  videoLink: '',          // TBD
  importUrl: '',          // TBD
  oauth: false,           // Even if used, must be manually enabled.
  singleInstance: false
)

preferences {
  page(name: 'RSPbsgPage')
}

//---- SYSTEM CALLBACKS

void installed () {
  pbsgCoreInstalled()
}

void updated () {
  pbsgCoreUpdated()
}

void uninstalled () {
  pbsgCoreUninstalled()
}

//---- RENDERING AND DISPLAY

Map RSPbsgPage () {
  return dynamicPage(
    name: 'RSPbsgPage',
    title: heading1(appInfo(app)),
    install: true,
    uninstall: true,
  ) {
    section {
      paragraph([
        heading1('Debug'),
        *appStateAsBullets(true)
      ].join('<br/>'))
    }
  }
}
