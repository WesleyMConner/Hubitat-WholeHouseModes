// ---------------------------------------------------------------------------------
// roomPBSG (an instsantiation of libPbsgPrivate)
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
#include wesmc.libPbsgPrivate
#include wesmc.libUtils
#include wesmc.libLogAndDisplay

definition (
  parent: 'wesmc:whaRoom',
  name: 'roomPBSG',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'A PBSG (libPbsgPrivate instance) rooted in a WHA Rooms instance',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  installOnOpen: false,
  documentationLink: '',  // TBD
  videoLink: '',          // TBD
  importUrl: '',          // TBD
  oauth: false,           // Even if used, must be manually enabled.
  singleInstance: false
)

preferences {
  page(name: 'roomPbsgPage')
}

Map roomPbsgPage () {
  return dynamicPage (
    name: 'roomPbsgPage',
    install: true,
    nextPage: whaPage,
    uninstall: false
  ) {
    defaultPage()
  }
}

void installed () {
  Ltrace('installed()', 'At entry')
  clientProvidedRoomPbsgInit()
}

void updated () {
  Ltrace('updated()', 'At entry')
  clientProvidedRoomPbsgInit()
}

void uninstalled () {
  Ltrace('uninstalled()', 'At entry')
  Ldebug('uninstalled()', 'DELETING CHILD DEVICES')
  getAllChildDevices().collect{ device ->
    deleteChildDevice(device.deviceNetworkId)
  }
}
