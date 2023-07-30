// ---------------------------------------------------------------------------------
// W H O L E   H O U S E   M O D E
//
//  Copyright (C) 2023-Present Wesley M. Conner
//
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"); you may not use this file except in compliance with the
// License. You may obtain a copy of the License at
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ---------------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DeviceWrapper
import com.hubitat.app.DeviceWrapperList as DeviceWrapperList
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Hub as Hub
#include wesmc.UtilsLibrary

definition(
  name: "WholeHouseModes",
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'Whole-House Mode Lighting with Per-Room Overrides',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  singleInstance: true
)

Map monoPage() {
  return dynamicPage(name: "monoPage") {
    if (app.getInstallationState() != 'COMPLETE') {
      section {
        paragraph "DIE DIE DIE"
        paragraph([
          heading('Whole-House Modes'),
          emphasis('Before you can create <b>Room Scenes(s)</b> ...'),
          normal('Push the <b>Done</b> button.'),
          bullet('This <em>parent application</em> must be installed.'),
          bullet('This parent groups and manages participating rooms.')
        ].join('\n'))
      }
    } else {
      section {
        paragraph '<span style="font-size: 2em; font-weight: bold;">Whole-House Modes</span>'
        app(
          name: "childAppInstances",
          appName: "RoomScenes Instances",
          namespace: "wesmc",
          title: "<b>Add a new RoomScenes Instance</b>",
          multiple: true
        )
        paragraph """Whole-House Modes - @wesmc, \
          <a href='https://github.com/WesleyMConner/Hubitat-WholeHouseModes' \
          target='_blank'> Click for more info.</a>"""
      }
    }
  }
}

// ---------------------------------------------------
// I N I T I A L I Z A T I O N   &   O P E R A T I O N
// ---------------------------------------------------
void installed() {
  if (settings.LOG) log.trace 'installed()'
  initialize()
}

void updated() {
  if (settings.LOG) log.trace 'updated()'
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void initialize() {
  log.info """initialize() with ${childApps.size()} Rooms</br>
    ${childApps.each({ child -> "&#x2022;&nbsp;${child.label}" })}
  """
}