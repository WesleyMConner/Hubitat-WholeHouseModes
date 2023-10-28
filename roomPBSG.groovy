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
  // Norally, this page IS NOT presented.
  //   - This page can be viewed via an instance link on the main Hubitat Apps menu.
  //   - Instance state & settings are rendered on the parent App's page.
  return dynamicPage (
    name: 'roomPbsgPage',
    install: true,
    uninstall: false
  ) {
    defaultPage()
  }
}

void installed () {
  Ltrace('installed()', 'At entry')
  roomScenePbsgInit()
}

void updated () {
  Ltrace('updated()', 'At entry')
  roomScenePbsgInit()
}

void uninstalled () {
  Ltrace('uninstalled()', 'At entry')
  Ldebug('uninstalled()', 'DELETING CHILD DEVICES')
  getAllChildDevices().collect{ device ->
    deleteChildDevice(device.deviceNetworkId)
  }
}

void roomSceneVswEventHandler (Event e) {
  // Process events for Room Scene PGSG child VSWs.
  //   - The received e.displayName is the DNI of the reporting child VSW.
  //
  // P A R E N T   R E Q U I R E M E N T
  //   - Parent must provide 'activateRoomScene(String roomScene)' which is
  //     invoked when a pbsgVswTurnedOnCallback()'
  Ltrace('roomSceneVswEventHandler', "eventDetails: ${eventDetails(e)}")
  if (e.isStateChange) {
    if (e.value == 'on') {
      if (state.previousVswDni == e.displayName) {
        Lerror(
          'roomSceneVswEventHandler()',
          "The active Room Scene VSW '${state.activeVswDni}' did not change."
        )
      }
      state.previousVswDni = state.activeVswDni ?: state.defaultVswDni
      state.activeVswDni = e.displayName
      Ldebug(
        'roomSceneVswEventHandler()',
        "${state.previousVswDni} -> ${state.activeVswDni}"
      )
      String scene = vswDnitoName(state.activeVswDni)
      parent.activateRoomScene(scene)
    } else if (e.value == 'off') {
      // Take no action when a VSW turns off
    } else {
      Lwarn(
        'roomSceneVswEventHandler()',
        "unexpected event value = >${e.value}<"
      )
    }
  }
}

void roomScenePbsgInit() {
  // P A R E N T   R E Q U I R E M E N T
  //   - Parent must provide 'getCurrentRoomScene()'. If non-null, the
  //     Room Scene VSW is set so as to be consistent with the current
  //     Room Scene. If null, the Room Scene 'AUTOMATIC' is assumed.
  Ltrace('roomScenePbsgInit()', 'At entry')
  unsubscribe()
  getVsws().each{ vsw ->
    Ltrace(
      'roomScenePbsgInit()',
      "Subscribe <b>${vsw.dni} (${vsw.id})</b> to roomSceneVswEventHandler()"
    )
    subscribe(vsw, "switch", roomSceneVswEventHandler, ['filterEvents': false])
  }
  // The initially "on" PBSG VSW should be consistent with the current Room Scene.
  String roomScene = parent.getCurrentRoomScene() ?: 'AUTOMATIC'
  Ldebug(
    'roomScenePbsgInit()',
    "Activating VSW for roomScene: <b>${roomScene}</b>"
  )
  getVswByName(roomScene).turnOnVswExclusively()
}
