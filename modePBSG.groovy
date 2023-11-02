// ---------------------------------------------------------------------------------
// modePBSG (an instsantiation of libPbsgBase)
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
#include wesmc.libPbsgBase
#include wesmc.libUtils
#include wesmc.libLogAndDisplay

definition (
  parent: 'wesmc:wha',
  name: 'modePBSG',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'A PBSG (extends libPbsgBase) designed for use in wha.groovy',
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
  page (name: '_modePbsgPage')
}

Map _modePbsgPage () {
  // Norally, this page IS NOT presented.
  //   - This page can be viewed via an instance link from the main
  //     Hubitat Apps menu.
  //   - Instance state & settings are rendered on the parent App's page
  //     along with a button that can be used to force update() the App
  //     instance.
  return dynamicPage (
    name: '_modePbsgPage',
    install: true,
    uninstall: true
  ) {
    _pbsgBasePage()
  }
}

//----
//---- EXPECTED APP METHODS
//----

void installed () {
  Ltrace('installed()', 'At entry')
  // Note: Parent is responsible for initial _configModePbsg() call.
  _modePbsgInit()
}

void updated () {
  Ltrace('updated()', 'At entry')
  _configModePbsg()   // Refresh in case VSWs have been manually removed.
  _modePbsgInit()
}

void uninstalled () {
  Ldebug('uninstalled()', 'DELETING CHILD DEVICES')
  getAllChildDevices().collect{ device ->
    Ldebug('uninstalled()', "Deleting '${device.deviceNetworkId}'")
    deleteChildDevice(device.deviceNetworkId)
  }
}

//----
//---- STANDALONE METHODS (no inherent "this")
//----

void modeVswEventHandler (Event e) {
  // Process events for Mode PGSG child VSWs.
  //   - The received e.displayName is the DNI of the reporting child VSW.
  //   - When a Mode VSW turns on, change the Hubitat mode accordingly.
  //   - The whaRoom clients DO NOT get a callback for this event and
  //     should instead app.subscribe to Hubitat Mode change events.
  //--DEEP-DEBUGGING-> Ltrace('modeVswEventHandler()', "eventDetails: ${eventDetails(e)}")
  if (e.isStateChange) {
    if (e.value == 'on') {
      if (state.previousVswDni == e.displayName) {
        Lerror(
          'modeVswEventHandler()',
          "The active Mode VSW '${state.activeVswDni}' did not change."
        )
      }
      Linfo(
        'roomSceneVswEventHandler()',
        'T B D - OPERARIONS ON state DO NOT MAKE SENSE IN THE EVENT HANDLER.'
      )
      state.previousVswDni = state.activeVswDni ?: state.defaultVswDni
      state.activeVswDni = e.displayName
      Ldebug(
        'modeVswEventHandler()',
        "${state.previousVswDni} -> ${state.activeVswDni}"
      )
      // The vswName is the target mode.
      String vswName = _vswDnitoName(e.displayName)
      _turnOnVswExclusivelyByName(vswName)
      // Adjust the Hubitat mode.
      Ldebug(
        'modeVswEventHandler()',
        "Setting mode to <b>${vswName}</b>"
      )
      getLocation().setMode(vswName)
    } else if (e.value == 'off') {
      Linfo(
        'roomSceneVswEventHandler()',
        'T B D - CHECK, IF NO MODE VSW IS "on", SOMETHING WENT WRONG.'
      )
      // Take no action when a VSW turns off
    } else {
      Lwarn(
        'modeVswEventHandler()',
        "unexpected event value = >${e.value}<"
      )
    }
  }
}

//----
//---- CUSTOM APP METHODS
//----

void _configModePbsg () {
  // Used for initial configuration AND refresh of configuration.
  String pbsgName = app.getLabel()
  List<String> vswNames = getModeNames()
  String defaultVswName = getGlobalVar('DEFAULT_MODE').value
  String logLevel = parent.getLogLevel() ?: 'Debug'
  Ldebug(
    '_configModePbsg()',
    [
      '',
      "<b>pbsgName</b>: ${pbsgName}",
      "<b>vswNames</b>: ${vswNames}",
      "<b>defaultVswName</b>: ${defaultVswName}",
      "<b>logLevel</b>: ${logLevel}",
    ].join('<br/>')
  )
  _configPbsg (pbsgName, vswNames, defaultVswName, logLevel)
}

void _subscribeToModeVswChanges() {
  app.unsubscribe()
  List<DevW> vsws = _getVsws()
  if (!vsws) {
    Lerror('_subscribeToModeVswChanges()', 'The child VSW instances are MISSING.')
  }
  vsws.each{ vsw ->
    Ltrace(
      '_subscribeToModeVswChanges()',
      "Subscribe <b>${vsw.dni} (${vsw.id})</b> to modeVswEventHandler()"
    )
    app.subscribe(vsw, "switch", modeVswEventHandler, ['filterEvents': false])
  }
}

void _modePbsgInit() {
  // Three mechanisms invoke this method:
  //   - modePBSG installed() via _modePbsgPage() initialization
  //   - modePBSG updated() via _modePbsgPage() revision
  //   - modePBSG updated() via _whaPage() custom button press
  Ltrace('_modePbsgInit()', 'At entry')
  _subscribeToModeVswChanges()
  // Ensure that the initially "on" VSW is consistent with the Hubitat mode.
  String mode = getLocation().getMode()
  Ldebug(
    '_modePbsgInit()',
    "Activating VSW for mode: <b>${mode}</b>"
  )
  _turnOnVswExclusivelyByName(mode)
}
