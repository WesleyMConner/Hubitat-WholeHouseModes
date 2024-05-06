// ---------------------------------------------------------------------------------
// lPro2IRParser - Pro2 Integration Report Parser (Library)
//
// Copyright (C) 2023-Present Wesley M. Conner
//
// LICENSE
//   Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
//   "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
//   not use this file except in compliance with the License. Unless
//   required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
//   implied.
// ---------------------------------------------------------------------------------

import java.util.regex.Matcher

library(
  name: 'lPro2IRParser',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Isolate RA2 Methods',
  category: 'general purpose'
)

// The Pro2 parser mimics RA2 parser output to simplify conjoined operatons.
//
// +-----------------+------------------+------------------------+
// | Ra2 Result Keys | Pro2 Result Keys |        Comments        |
// +-----------------+------------------+------------------------+
// |   ra2Devices    |   pro2Devices    | From kpads and ciruits |
// |      kpads      |      kpads       | From LIPIdList/Devices |
// |    ra2Rooms     |    pro2Rooms     |           n/a          |
// |     circuits    |     circuits     |  From LIPIdList/Zones  |
// |    timeclock    |        n/a       |           n/a          |
// |      green      |        n/a       |           n/a          |
// +-----------------+------------------+------------------------+

void logResults(Map results, String key) {
  ArrayList lines = []
  lines << "<b>${key}</b>"
  results."${key}".each { entry -> lines << "${entry}" }
  logInfo('logResults', lines)
}

Map JsonDeserializeMap(String json) {
  /* groovylint-disable-next-line VariableTypeRequired */
  def slurper = new JsonSlurper()
  return slurper.parseText(json)
}

void populatePro2Devices(Map results) {
  // Populates the ArrayList of results.pr2Devices code,id,name strings
  //   code → The device 'Type' (see below)
  //     id → Specifies the Hubitat 'DeviceName' (aka 'deviceID' in Device events)
  //   name → Specifies the Hubitat 'DeviceLabel' (aka 'name' in Device events)
  // Allowed Device Types
  //   LIPIdList/Devices
  //     k: Keypad                     // Assigned by default
  //     q: Pico (pushed/released)     // 'pico' appears in the name
  //   LIPIdList/Zones
  //     d: Dimmer                     // Assigned by default
  //   Some manual adjustments may be appropriate
  //     p: Pico (pushed/held) ...     // See 'q' above
  //     s: Switch ... Assigned 'd'    // See 'd' above
  //   Unused / Not Tested
  //     e: Shade
  //     f: Fan Control
  //     h: HVAC Controller
  //     m: Motion
  //     o: VCRX Output
  //     r: Shade Remote
  //     t: Thermostat
  //     v: VCRX
  //     w: Wall mount Keypad
  results.ra2Devices = []              // Erase and rebuild
  results.kpads.each { kpad ->
    // Users cannot edit the name of some device types (e.g., main repeaters,
    // motion sensors). For these devices the (editable) "Device Location"
    // (aka 'physicalLocation' is used as the device name.
    String nameNormalized = kpad.name.toLowerCase()
    if (nameNormalized.contains('pico')) {
      results.ra2Devices << "q,${kpad.name},${kpad.id}"
    } else if (nameNormalized.contains('pro2')) {
      results.ra2Devices << "k,${kpad.name},${kpad.id}"
    } else {
      results.ra2Devices << "d,${kpad.name},${kpad.id}"
    }
  }
  results.circuits.each { device ->
    results.ra2Devices << "d,${device.name},${device.id}"
  }
}

void logPro2Results(Map results, String key) {
  ArrayList lines = []
  lines << "<b>${key}</b>"
  results."${key}".each { e -> lines << "${e}" }
  logInfo('logPro2Results', lines)
}

Map parsePro2IntegRpt(String pro2IntegrationReport) {
  //---------------------------------------------------------------------------------
  //  Processes a 'pro2IntegrationReport' (provided as a multi-line String)
  //    - The Pro2 Integration Report is a JSON-serialized Map.
  //    - Produces a 'results' Map with the following keys:
  //      rA2Devices → An ArrayList of strings that can be copy-pasted into the
  //                   "Lutron Integrator App" to create a new RA2 integration
  //                   instance
  //           kpads → An ArrayList of RA2 keypad Map(s)
  //        circuits → An ArrayList of RA2 circuit Map(s) - switched or dimmed
  // While Ra2 rooms reflect device installation location (vs circuit location)
  // Pro2 rooms are functional and should match Hubitat rooms.
  Map m = JsonDeserializeMap(pro2IntegrationReport)
  logInfo('parsePro2IntegRpt', "m: ${m}")
  Map results = [ pro2Devices: [], kpads: [], circuits: [] ]
  results.kpads = m.LIPIdList.Devices.collect { d ->
    String pro2Room = d.Area?.Name ?: 'Control'
    String deviceName = (d.Name == 'Smart Bridge 2')
      ? 'Pro2'
      : d.Name
    [
      name: "${pro2Room}-${deviceName}",
      id: d.ID,
      buttons: d.Buttons.collect { e ->
        [ number: e.Number, 'Name': e.Name ]
      }
    ]
  }
  results.circuits = m.LIPIdList.Zones.collect { z ->
    String pro2Room = z.Area?.Name ?: 'Control'
    [
      pro2Room: pro2Room,
      name: "${pro2Room}-${z.Name}",
      id: z.ID
    ]
  }
  populatePro2Devices(results)
  return results
}
