// ---------------------------------------------------------------------------------
// modePBSG (an instsantiation of libPbsgPrivate)
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
#include wesmc.libPbsgPrivate
#include wesmc.libUtils
#include wesmc.libLogAndDisplay

definition (
  parent: 'wesmc:wha',
  name: 'modePBSG',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'A PBSG (libPbsgPrivate instance) rooted in Whole House Automation',
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
  page (name: 'modePbsgPage')
}

Map modePbsgPage () {
  // Norally, this page IS NOT presented.
  //   - This page can be viewed via an instance link on the main Hubitat Apps menu.
  //   - Instance state & settings are rendered on the parent App's page.
  return dynamicPage (
    name: 'modePbsgPage',
    install: true,
    uninstall: true
  ) {
    defaultPage()
  }
}

void installed () {
  Ltrace('installed()', 'At entry')
  modePbsgInit()
}

void updated () {
  Ltrace('updated()', 'At entry')
  modePbsgInit()
}

void uninstalled () {
  Ldebug('uninstalled()', 'DELETING CHILD DEVICES')
  getAllChildDevices().collect{ device ->
    Ldebug('uninstalled()', "Deleting '${device.deviceNetworkId}'")
    deleteChildDevice(device.deviceNetworkId)
  }
}

void modeVswEventHandler (Event e) {
  // Process events for Mode PGSG child VSWs.
  //   - The received e.displayName is the DNI of the reporting child VSW.
  //   - When a Mode VSW turns on, change the Hubitat mode accordingly.
  //   - Clients DO NOT get a callback for this event.
  //   - Clients SHOULD subscribe to Hubitat Mode change events.
  Ltrace('modeVswEventHandler', "eventDetails: ${eventDetails(e)}")
  if (e.isStateChange) {
    if (e.value == 'on') {
      if (state.previousVswDni == e.displayName) {
        Lerror(
          'modeVswEventHandler()',
          "The active Mode VSW '${state.activeVswDni}' did not change."
        )
      }
      state.previousVswDni = state.activeVswDni ?: state.defaultVswDni
      state.activeVswDni = e.displayName
      Ldebug(
        'modeVswEventHandler()',
        "${state.previousVswDni} -> ${state.activeVswDni}"
      )
      turnOnVswExclusively(state.activeVswDni)
      // Adjust the Hubitat mode.
      String mode = getModeNameForVswDni(e.displayName)
      Ldebug(
        'modeVswEventHandler()',
        "Setting mode to <b>${mode}</b>"
      )
      getLocation().setMode(mode)
    } else if (e.value == 'off') {
      // Take no action when a VSW turns off
    } else {
      Lwarn(
        'modeVswEventHandler()',
        "unexpected event value = >${e.value}<"
      )
    }
  }
}

void modePbsgInit() {
  Ltrace('modePbsgInit()', 'At entry')
  unsubscribe()
  List<DevW> vsws = getVsws()
manageChildDevices()
  if (!vsws) {
    Lerror('modePbsgInit()', 'The child VSW instances are MISSING.')
  }
  vsws.each{ vsw ->
    Ltrace(
      'modePbsgInit()',
      "Subscribe <b>${vsw.dni} (${vsw.id})</b> to modeVswEventHandler()"
    )
    subscribe(vsw, "switch", modeVswEventHandler, ['filterEvents': false])
  }
  // The initially "on" PBSG VSW should be consistent with the Hubitat mode.
  String mode = getLocation().getMode()
  Ldebug(
    'modePbsgInit()',
    "Activating VSW for mode: <b>${mode}</b>"
  )
  getVswByName(mode).turnOnVswExclusively()
}
