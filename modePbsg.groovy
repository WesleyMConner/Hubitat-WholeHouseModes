// ---------------------------------------------------------------------------------
// modePbsg (an instantiation of libPbsgBase)
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
  Ltrace(
    'configureModePbsg()',
    "Updating ${app.getLabel()} state and event subscriptions"
  )
  Ltrace('configureModePbsg()', 'stopping event subscriptions')
  unsubscribe()
  Ltrace('configureModePbsg()', 'updating state values')
  removeLegacyModePbsgState()  //--TBD-> What can migrate to libPbsgBase?
  configurePbsg (
    "${app.getLabel()}_",                 // String dniPrefix
    GetModeNames(),                       // List<String> buttonNames,
    getGlobalVar('DEFAULT_MODE').value,   // String defaultButton
    'TRACE'                               // String logThreshold
  )
  Ltrace('configureModePbsg()', "Active switch for current Hubitat mode (${mode})")
  turnOnExclusivelyByName(mode)
  Ltrace('configureModePbsg()', 'subscribing to PBSG events')
  subscribe(vsw, "PbsgCurrentSwitch", modeVswEventHandler, ['filterEvents': false])
}

//----
//---- SYSTEM CALLBACKS
//----   Methods specific to this execution context
//----

void installed () {
  Ltrace('installed()', 'calling configureModePbsg()')
  configureModePbsg()
}

void updated () {
  Ltrace('updated()', 'calling configureModePbsg()')
  configureModePbsg()
}

void uninstalled () {
  Lwarn('uninstalled()', 'DELETING CHILD DEVICES')
  getAllChildDevices().collect{ device ->
    Lwarn('uninstalled()', "Deleting '${device.deviceNetworkId}'")
    deleteChildDevice(device.deviceNetworkId)
  }
}

//----
//---- EVENT HANDLERS
//----   Methods specific to this execution context
//----   The subscribed "app" (its state) is available in the handler.
//----

void modeVswEventHandler (Event e) {
  logDebug('modeVswEventHandler()', e.descriptionText)
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
