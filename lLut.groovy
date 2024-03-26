// ---------------------------------------------------------------------------------
// L U T ( R O N )
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

library(
  name: 'lLut',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Lutron extensions',
  category: 'general purpose'
)

String extractNativeIdFromLabel(String deviceLabel) {
  //->x = (deviceLabel =~ /\((.*)\)/)
  //->logDebug('extractNativeIdFromLabel', [
  //->  "deviceLabel: ${deviceLabel}",
  //->  "x: ${x}",
  //->  "x[0]: ${x[0]}",
  //->  "x[0]: ${x[0][1]}",
  //->])
  return (deviceLabel =~ /\((.*)\)/)[0][1]
}

void identifyLedButtonsForListItems(
  ArrayList list,
  List<DevW> ledDevices,
  String prefix
  ) {
  // Kpad LEDs are used as a proxy for Kpad buttons.
  //   - The button's displayName is meaningful to clients.
  //   - The button's deviceNetworkId is <KPAD Dni> hyphen <BUTTON #>
  list.each { item ->
    input(
      name: "${prefix}_${item}",
      title: heading2("Select the Button(s) that activate ${b(item)}"),
      type: 'enum',
      width: 6,
      submitOnChange: true,
      required: false,
      multiple: true,
      options: ledDevices.collect { d ->
        "${d.label}: ${d.deviceNetworkId}"
      }?.sort()
    )
  }
}

void populateStateKpadButtons(String prefix) {
  // Design Note
  //   The Kpad LEDs collected by selectForMode() function as a proxy for
  //   Kpad button presses. Settings data includes the user-friendly
  //   LED displayName and the LED device ID, which is comprised of 'Kpad
  //   Device Id' and 'Button Number', concatenated with a hyphen. This
  //   method populates "state.[<KPAD DNI>]?.[<KPAD Button #>] = mode".
  //
  // Sample Settings Data
  //     key: LEDs_Day,
  //   value: [Central KPAD 2 - DAY: 5953-2]
  //           ^User-Friendly Name
  //                                 ^Kpad DNI
  //                                      ^Kpad Button Number
  // The 'value' is first parsed into a list with two components:
  //   - User-Friendly Name
  //   - Button DNI               [The last() item in the parsed list.]
  // The Button DNI is further parsed into a list with two components:
  //   - Kpad DNI
  //   - Kpad Button number
  String stateKey = "${prefix}Map"
  state[stateKey] = [:]
  settings.each { key, value ->
    if (key.contains("${prefix}_")) {
      String base = key - "${prefix}_"
      value.each { item ->
        /* groovylint-disable-next-line ImplementationAsType */
        ArrayList kpadDniAndButtons = item?.tokenize(' ')?.last()?.tokenize('-')
        if (kpadDniAndButtons.size() == 2 && base) {
          if (state[stateKey][kpadDniAndButtons[0]] == null) {
            state[stateKey][kpadDniAndButtons[0]] = [:]
          }
          state[stateKey][kpadDniAndButtons[0]][kpadDniAndButtons[1]] = base
        }
      }
    }
  }
}

