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
  parent: 'wesmc:RoomScenes',
  name: 'rsPBSG',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'A PBSG (pbsgLibrary instance) rooted in a Room Scenes instance',
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
  page(name: 'pbsgPage', title: '', install: true, uninstall: true)
}

Map pbsgPage() {
  paragraph heading("${app.getLabel()} Pushbutton Switch Group<br/>") \
        + bullet('Push "Done" to enable and return to parent.')
  paragraph "Current State [placeholder only]"
}

//--TBD-- void initialize(Boolean LOG = false) {
//--TBD--   if (LOG) log.trace 'initialize()'
//--TBD--   LinkedHashMap config = parent.getPbsgMap(String pbsgName)
//--TBD--   createPBSG(
//--TBD--     name: app.getLabel(),
//--TBD--     switchNames: config.switchNames,
//--TBD--     defaultSwitch: config.defaultSwitch
//--TBD--   )
  //->enforceMutualExclusion()
//--TBD--   enforceDefault()
//--TBD--   subscribe(settings.swGroup, "switch", buttonHandler)
//--TBD-- }

void uninstalled() {
//--TBD--   if (settings.LOG) log.trace 'uninstalled()'
//--TBD--   // Nothing to do. Subscruptions are automatically dropped.
//--TBD--   // This may matter if devices are captured by a switch group in the future.
}
