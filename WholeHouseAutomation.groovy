// ---------------------------------------------------------------------------------
// P U S H B U T T O N   S W I T C H E S
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
#include wesmc.UtilsLibrary

definition(
  name: "WholeHouseAutomation",
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Whole House Automation using Modes, RA2 and Room Overrides',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  singleInstance: true
)

// -------------------------------
// C L I E N T   I N T E R F A C E
// -------------------------------
preferences {
  page name: "monoPage", title: "", install: true, uninstall: true
}

Map monoPage() {
  return dynamicPage(name: "monoPage") {
    section {
      if (app.getInstallationState() != 'COMPLETE') {
        paragraph heading("Whole House Automation")
        paragraph emphasis('Before you can create <b>Room Scene(s)</b> ...')
        paragraph normal('Push the <b>Done</b> button.')
        paragraph bullet('This <em>parent application</em> will be installed.')
        paragraph bullet('The parent collects data used by <b>Room Scenes</b>.')
        paragraph bullet('It also groups <b>Room Scenes</b> (children) together.')
      } else {
        //addRoomListToSettings('<b> Step 1:</b> Identify the Participating Rooms')
      //}
      //if (settings.roomIds) {
        paragraph emphasis('<b>Step 1: Identify Participating Devices<br/>') \
          + emphasis2('Identify all Lutron Main Repeaters<br/>') \
          + bullet('Repeater buttons invoke in-kind Lutron scenes')
        input (
          name: 'lutronRepeaters',
          type: 'device.LutronKeypad',
          //title: 'Select Lutron Main Repeaters',
          submitOnChange: true,
          required: true,
          multiple: true
        )
        paragraph emphasis2('Identify ALL Remaining Devices<br/>') \
          + bullet('Include Lutron devices (used to detect manual overrides)<br/>') \
          + bullet('Include Lutron keypads/picos (their buttons trigger room scenes)<br/>') \
          + bullet('Include non-Lutron devices (room scenes establish their levels)<br/>') \
          + bullet('Do not include VSWs (virtual switches) as a rule<br/>') \
          + comment('An appropriate subset of devices will appear in subsequent screens')
        input (
          name: 'hideParticipatingDevices',
          type: 'bool',
          title: "${settings.hideParticipatingDevices ? 'Hiding Devices' : 'Showing Devices'}",
          submitOnChange: true,
          defaultValue: false
        )
        if (!settings.hideParticipatingDevices) {
          input (
            name: 'participatingDevices',
            type: 'capability.switch',
            //title: 'Select Non-Lutron Switches',
            submitOnChange: true,
            required: true,
            multiple: true
          )
        }
        if (settings.lutronRepeaters && settings.participatingDevices) {
          app(
            name: "RoomScenes",
            appName: "RoomScenes",
            namespace: "wesmc",
            title: "<b>Add Rooms</b>",
            multiple: true
          )
        }
        paragraph comment("""Whole House Automation - @wesmc, \
          <a href='https://github.com/WesleyMConner/Hubitat-WholeHouseAutomation' \
          target='_blank'> <br/>Click for more information</a>""")
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
  log.info """initialize() with ${childApps.size()} Automation Groups</br>
    ${childApps.each({ child -> "&#x2022;&nbsp;${child.label}" })}
  """
}