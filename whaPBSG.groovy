// ---------------------------------------------------------------------------------
// whaPBSG (an instsantiation of pbsgLibrary)
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
#include wesmc.pbsgLibrary
#include wesmc.UtilsLibrary  // Required by wesmc.pbsgLibrary

definition(
  parent: 'wesmc:WholeHouseAutomation',
  name: 'whaPBSG',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'A PBSG (pbsgLibrary instance) rooted in Whole House Automation',
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
  page(name: 'whaPbsgPage', title: '', install: true, uninstall: true)
}

Map whaPbsgPage () {
  return dynamicPage(name: 'whaPbsgPage') {
    defaultPage()
  }
}

void pbsgEventHandler (Event event) {
  if (settings.LOG) log.trace(
    //"pbsgEventHandler() w/ parent App: '${getAppInfo((event.deviceId).parent())}'."
    //+
    logEventDetails(event, false)
  )
  /*
  pbsg = state[pbsgName]
  if (event.isStateChange) {
    switch(event.value) {
      case 'on':
        if (settings.LOG) log.trace "pbsgEventHandler() ${event.displayName}"
          + 'turned "ON". Turning off switch group peers.'
        pbsg.scene2Vsw.each{ scene, vsw ->
          // No harm in turning off a VSW that might already be off.
          if (vsw.deviceNetworkId != event.displayName) vsw.off()
        }
        break
      case 'off':
        //-- PENDING -> enforceDefault()
        break
      default:
        log.error  'pbsgEventHandler() expected 'on' or 'off'; but, '
          + "received '${event.value}'."
        app.updateLabel("${pbsg.enclosingApp} - BROKEN")
    }
  } else {
    log.error 'pbsgEventHandler() received an unexpected event:<br/>'
      + logEventDetails(event, false)
  }
  */
}

void initialize() {
  //if (settings.LOG) log.trace 'PBSG initialize() entry'
  /*
  state.switchNameToVsw.each{ switchName, vsw ->
    if (settings.LOG) log.trace "PBSG initialize() subscribing ${deviceTag(vsw)}..."
    DevW vswx
    if (settings.LOG) "whaPBSG #83 vswx ${deviceTag(vsw)}"
    //subscribe(vswx, "switch", pbsgEventHandler)
    subscribe(vswx, "switch", pbsgEventHandler, ['filterEvents': false])
  }
  DevW biteMe = state.switchNameToVsw['TV']
  log.trace "#89 biteMe: ${biteMe}"
  biteMe.on()
  */
  app.getAllChildDevices().each{ device ->
    if (settings.LOG) log.trace "PBSG initialize() subscribing ${deviceTag(device)}..."
    subscribe(device, "switch", pbsgEventHandler, ['filterEvents': false])
  }
  //if (settings.LOG) log.trace 'PBSG initialize() exit'
}
// groovy.lang.MissingMethodException: No signature of method: java.lang.String.call()
// is applicable for argument types: (java.util.HashMap)
// values: [[data:[:], displayName:pbsg_modes_TV, parentAppId:1627, typeName:Virtual Switch, ...]] Possible solutions: wait(), chars(), any(), wait(long), each(groovy.lang.Closure), take(int) on line 307 (method updated) (library wesmc.pbsgLibrary, line 261)

void installed() {
  if (settings.LOG) log.trace 'PBSG installed()'
  initialize()
}

void updated() {
  if (settings.LOG) log.trace 'PBSG updated()'
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled() {
  if (settings.LOG) log.trace 'PBSG uninstalled()'
  // Nothing to do. Subscruptions are automatically dropped.
  // This may matter if devices are captured by a switch group in the future.
}


