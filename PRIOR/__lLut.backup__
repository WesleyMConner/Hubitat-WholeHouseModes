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
