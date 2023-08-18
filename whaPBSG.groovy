// ---------------------------------------------------------------------------------
// P B S G   L I B R A R Y
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
import com.hubitat.app.ChildDeviceWrapper as ChildDevW
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.DeviceWrapperList as DevWL
import com.hubitat.hub.domain.Event as Event
#include wesmc.pbsgLibrary
#include wesmc.UtilsLibrary

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

// ------------------------------------------------------------------------
// D E S I G N   N O T E S
//   The PARENT "Whole House Automation" application is expected to create
//   this child application using:
//     addChildApp('wesmc', 'whaPGSG', pbsgName)
//   Once instantiated, this CHILD calls the following PARENT method:
//     parent.getPbsgMap(String pbsgName)
//   which supplies the Map:
//     [
//         switchNames: ... // a List<String>
//       defaultSwitch: ... // a String
//     ]
//   This CHILD exposes the following functions to the parent.
//     child.activateSceneForMode(String modeName, Boolean FORCE = false).
//   When the CHILD is operating in "AUTO" mode, it sets an appropriate
//   scene for the whole-house mode. If FORCE = true, the child enters
//   its "AUTO" state, overriding any room-specific scene (or MANUAL mode)
//   which would otherwise prevail.
// ------------------------------------------------------------------------

// -------------------------------
// C L I E N T   I N T E R F A C E
// -------------------------------
preferences {
  page(name: 'whaPbsgPage', title: '', install: true, uninstall: true)
}

Map whaPbsgPage () {
  return dynamicPage(name: 'whaPbsgPage') {
    section {
      paragraph(
        heading("${app.getLabel()} a PBSG (Pushbutton Switch Group)<br/>")
        + bullet('Push <b>Done</b> to enable subcriptions and return to parent.')
      )
      paragraph(
        heading('Debug<br/>')
        + "${ displaySettings() }<br/>"
        + "${ displayState() }"
      )
    }
  }
}

void configure (List<String> switchNames, String defaultSwitch, Boolean log) {
  app.updateSetting('LOG', log)
  state.switchNames = switchNames
  state.defaultSwitch = defaultSwitch
  createChildVsws()
}

void createChildVsws () {
  // FOR TESTING THE REMOVAL OF AN ORPHANED DEVICE, UNCOMMENT THE FOLLOWING:
  //-> addChildDevice(
  //->   'hubitat',         // namespace
  //->   'Virtual Switch',  // typeName
  //->   'bogus_device',
  //->   [isComponent: true, name: 'bogus_device']
  //-> )
  // Device creation "errors out" if deviceNetworkId is duplicated.
  // GET ALL CHILD DEVICES AT ENTRY
  List<String> childDevices = getAllChildDevices().collect{ d -> d.deviceNetworkId }
  if (settings.LOG) log.trace(
    "createChildVsws() devices at entry: ${childDevices}."
  )
  // ENSURE GOAL CHILD DEVICES EXIST
  LinkedHashMap<String, String> scene2VswNetwkId = state.switchNames.collectEntries{
    swName ->
      String deviceNetworkId = "${app.getLabel()}_${swName}"
      String vswNetworkId = childDevices.find{ it == deviceNetworkId }
        ?: addChildDevice(
            'hubitat',         // namespace
            'Virtual Switch',  // typeName
            deviceNetworkId,
            [isComponent: true, name: deviceNetworkId]
           ).deviceNetworkId
      [swName, vswNetworkId]
  }
  state.scene2VswNetwkId = scene2VswNetwkId
  if (settings.LOG) log.trace(
    "createChildVsws() scene2VswNetwkId: ${scene2VswNetwkId}."
  )
  // DELETE ORPHANED DEVICES
  List<String> currentChildren = scene2VswNetwkId.collect{ it.value }
  List<String> orphanedDevices = childDevices.minus(currentChildren)
  orphanedDevices.each{ deviceNetworkId ->
    if (settings.LOG) log.trace(
      "createChildVsws() dropping orphaned device `${deviceNetworkId}`."
    )
    deleteChildDevice(deviceNetworkId)
  }
}

void initialize() {
  if (settings.LOG) log.trace 'initialize()'
  //->enforceMutualExclusion()
  //->subscribe(settings.swGroup, "switch", buttonHandler)
}

void installed() {
  if (settings.LOG) log.trace 'WHA installed()'
  initialize()
}

void updated() {
  if (settings.LOG) log.trace 'WHA updated()'
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled() {
  if (settings.LOG) log.trace 'uninstalled()'
  // Nothing to do. Subscruptions are automatically dropped.
  // This may matter if devices are captured by a switch group in the future.
}

