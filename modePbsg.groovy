// ---------------------------------------------------------------------------------
// modePbsg (an instsantiation of libPbsgBase)
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
  name: 'modePbsg',
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
  page (name: 'modePbsgPage')
}

//----
//---- CORE APPLICATION
//----   Methods that ARE NOT constrained to any specific execution context.
//----

void removeLegacyModePbsgState () {
  atomicState.remove('activeVswDni')
  atomicState.remove('defaultVswDni')
  atomicState.remove('defaultVswName')
  atomicState.remove('inspectScene')
  atomicState.remove('LOG_LEVEL1_ERROR')
  atomicState.remove('LOG_LEVEL2_WARN')
  atomicState.remove('LOG_LEVEL3_INFO')
  atomicState.remove('LOG_LEVEL4_DEBUG')
  atomicState.remove('LOG_LEVEL5_TRACE')
  atomicState.remove('logLevel1Error')
  atomicState.remove('logLevel2Warn')
  atomicState.remove('logLevel3Info')
  atomicState.remove('logLevel4Debug')
  atomicState.remove('logLevel5Trace')
  atomicState.remove('previousVswDni')
  atomicState.remove('roomName')
  atomicState.remove('roomScene')
  atomicState.remove('switchDnis')
}

void configureModePbsg() {
  Linfo(
    'configureModePbsg()',
    "Updating ${app.getLabel()} state, devices and subscriptions"
  )
  Ltrace('configureModePbsg()', 'stopping event subscriptions')
  app.unsubscribe()
  Ltrace('configureModePbsg()', 'updating state values')
  updateModePbsgState()
  Ltrace('configureModePbsg()', 'managing child devices')
  manageChildDevices('configureModePbsg()')
  Ltrace(
    'configureModePbsg()',
    "ensuring active VSW matches current mode (${mode})"
  )
  activateVswForCurrentMode()
  Ltrace('configureModePbsg()', 'subscribing to VSW changes')
  subscribeToModeVswChanges()
}

void updateModePbsgState () {
  // Used for initial configuration AND configuration refresh.
  removeLegacyModePbsgState()
  atomicState.vswDniPrefix = "${app.getLabel()}_"
  atomicState.vswNames = getModeNames()
  atomicState.vswDefaultName = getGlobalVar('DEFAULT_MODE').value
  atomicState.logLevel = parent.getLogLevel() ?: lookupLogLevel('TRACE')
  //-> UNCOMMENT FOR ADVANCED DEBUGGING ONLY
  Ltrace(
    'updateModePbsgState()', getPbsgStateBullets()
    //[
    //  '',
    //  getPbsgStateBullets()
    //].join('<br/>')
  )
}

void activateVswForCurrentMode () {
  String mode = getLocation().getMode()
  Ltrace('modePbsgInit()', "Activating VSW for current mode: ${mode}")
  turnOnVswExclusivelyByName(mode)
}

void subscribeToModeVswChanges () {
  List<DevW> vsws = getVsws()
  if (!vsws) {
    Lerror('subscribeToModeVswChanges()', 'The child VSW instances are MISSING.')
  }
  vsws.each{ vsw ->
    //-> UNCOMMENT FOR ADVANCED DEBUGGING ONLY
    //-> Ltrace(
    //->   'subscribeToModeVswChanges()',
    //->   "Subscribe <b>${vsw.deviceNetworkId} (${vsw.id})</b> to modeVswEventHandler()"
    //-> )
    app.subscribe(vsw, "switch", modeVswEventHandler, ['filterEvents': false])
  }
}

//----
//---- SYSTEM CALLBACKS
//----   Methods specific to this execution context
//----

void installed () {
  Ltrace('installed()', 'Calling configureModePbsg()')
  configureModePbsg()
}

void updated () {
  Ltrace('updated()', 'Calling configureModePbsg()')
  configureModePbsg()
}

void uninstalled () {
  Ldebug('uninstalled()', 'DELETING CHILD DEVICES')
  getAllChildDevices().collect{ device ->
    Ldebug('uninstalled()', "Deleting '${device.deviceNetworkId}'")
    deleteChildDevice(device.deviceNetworkId)
  }
}

//----
//---- EVENT HANDLERS
//----   Methods specific to this execution context
//----

void modeVswEventHandler (Event e) {
  // Process events for Mode PGSG child VSWs.
  //   - The received e.displayName is the DNI of the reporting child VSW.
  //   - When a Mode VSW turns on, change the Hubitat mode accordingly.
  //   - The Room Scene clients DO NOT get a callback for this event and
  //     should instead app.subscribe to Hubitat Mode change events.
  //--DEEP-DEBUGGING-> Ltrace('modeVswEventHandler()', "eventDetails: ${eventDetails(e)}")
  if (e.isStateChange) {
    if (e.value == 'on') {
      if (atomicState.previousVswDni == e.displayName) {
        Lerror(
          'modeVswEventHandler()',
          "The active Mode VSW '${atomicState.activeVswDni}' did not change."
        )
      }
      Linfo(
        'roomSceneVswEventHandler()',
        'T B D - OPERATIONS ON state DO NOT MAKE SENSE IN THE EVENT HANDLER.'
      )
      atomicState.previousVswDni = atomicState.activeVswDni ?: getDefaultVswDni()
      atomicState.activeVswDni = e.displayName
      Ldebug(
        'modeVswEventHandler()',
        "${atomicState.previousVswDni} -> ${atomicState.activeVswDni}"
      )
      // The vswName is the target mode.
      String vswName = vswDnitoName(e.displayName)
      turnOnVswExclusivelyByName(vswName)
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
//---- SCHEDULED ROUTINES
//----   Methods specific to this execution context
//----

//----
//---- HTTP ENDPOINTS
//----   Methods specific to this execution context
//----

//----
//---- RENDERING AND DISPLAY
//----   Methods specific to this execution context
//----

Map modePbsgPage () {
  // While IT IS POSSIBLE to view a Mode PBSG instance using this function,
  // THIS PAGE IS NOT NORMALLY PRESENTED and NO SETTINGS ARE EXPECTED.
  //   - Use the Hubitat Apps menu to view this page.
  //   - The WHA page displays Mode PBSG state if the Mode PBSG exists.
  return dynamicPage (
    name: 'modePbsgPage',
    install: true,
    uninstall: true
  ) {
    pbsgBasePage()
  }
}
