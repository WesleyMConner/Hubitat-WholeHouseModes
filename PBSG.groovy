// ---------------------------------------------------------------------------------
// P B S G   -   ( B A S E D   O N   P U S H B U T T O N   S W I T C H )
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
// Design Notes
//   - This file (effectively) extends an existing application or existing
//     child application - allowing it to subscribe to and process events.
//   - An intermediate application isn't appropriate as there is no user
//     input to solicit.
//   - An intermediate device would not be able to process events.
//   - An instance of this quasi-application's state footprint exists
//     under a single key in the enclosing application's state.
// ---------------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DeviceWrapper
import com.hubitat.app.DeviceWrapperList as DeviceWrapperList // YUCK!!!
import com.hubitat.hub.domain.Event as Event

// -------------------------------------
// I N S T A N C E   M A N A G E M E N T
// -------------------------------------
Map< createPBSG ((Map args = [:]) {
  Map _args = [
              name: "Expected 'name' (String)",
       switchNames: "Expected 'switchNames' (List<String>)",
    dfltSwitchName: "Expected 'dfltSwitchName' (String)",
  ] << args
) {
  log.trace "createPBSG() Creating ${_args.name}<"
  self = state.[_args.name]
  if (self) {
    log.error "createPBSG() '${_args.name}' instance already exists."
  } else if (_args.dfltSwitchName != 'None' && \
             ! _args.switchNames.contains(_args.dfltSwitchName)) {
    log.error "createPBSG() dfltSwitchName (${d_args.dfltSwitchName}) " \
      + "not found in switchNames (${_args.switchNames})."
  } else {
    // Popuate initial instance data
    self = _args
    // Create required Virtual Switch children
    self.vsws: [:]
    self.switchNames.each{ swName ->
      self.vsws += addChildDevice(
        namespace: 'wesmc',
        typeName: 'VirtualSwitch',
        deviceNetworkId: "pbsg-${self.name}-${swName}"
      )
    }
    self.handler: { event ->
      log.trace "Placeholder handler with ${event}."
    }
    //subscribe(self.vsws, "switch", self.&handler)
  }
}

List<DeviceWrapper> getOnSwitches() {
  DeviceWrapperList devices = state[stateName]
  return devices?.findAll({ extractSwitchState(it) == 'on' })
}


void deletePBSG (String name) {
  String stateName = "pbsg-${name}"
  if (state[stateName]) {
    unsubscribe(state[stateName].vsws)
    state[stateName].vsws.each( device-> deleteChildDevice(device.deviceNetworkId) )
    state[stateName] = null
  } else {
    log.error "deletePBSG() no state data for '${stateName}'."
  }
}

// List<String> jobs = ['Groovy', 'Rocks', 'Big', 'Time']
//
// def generateStage(String service, Integer sleepTime=0) {
//     return {
//           sleep sleepTime
//           println "Hello $service"
//     }
// }
//
// BROKEN:
//   Map<String, Closure> map = generateStageMap(jobs)
//   map.each {
//     it.value
//   }
//
// The above is missing closure invocation. 'it.value' is touched, not executed.
// That is, the closure is not invoked. The following invokes the closure.
//
// FIX:
//   Map map = generateStageMap(jobs)
//   map.each {
//     it.value.call()   // Or, shorthand: it.value()
//   }

// ---------------------------------------------------
// S T A T I C   M E T H O D S
// ---------------------------------------------------

String extractSwitchState(DeviceWrapper d) {
  // What's best here? NOT exhaustively tested.
  //   - stateValues = d.collect({ it.currentStates.value }).flatten()
  //   - stateValues = d.currentStates.value
  List<String> stateValues = d.collect({ it.currentStates.value }).flatten()
  return stateValues.contains('on')
      ? 'on'
      : stateValues.contains('off')
        ? 'off'
        : 'unknown'
}

/*
String showSwitchInfoWithState(
  String delimiter = ', ',
  DeviceWrapperList devices = null  // settings.swGroup is only available in fn body ?!
) {
  if (!devices) devices = settings.swGroup
  return devices.collect({
    "${deviceTag(it)} ${emphasizeOn(extractSwitchState(it))}"
  }).sort().join(delimiter) ?: 'N/A'
}
*/

DeviceWrapper getSwitchById(String id) {
  DeviceWrapperList devices = settings.swGroup
  return devices?.find({ it.id == id })
}

/*
void logSettingsAndState(String calledBy) {
  if (settings.LOG) log.trace """logSettingsAndState() from ${calledBy}:<br/>
    <table>
      <tr>
        <th align='right'>LOG:</th>
        <td>${settings.LOG}</td>
      </tr>
      <tr>
        <th align='right'>swGroupName:</th>
        <td>${settings.swGroupName}</td>
      </tr>
      <tr>
        <th align='right'>Switch State:</th>
        <td>${showSwitchInfoWithState(', ')}</td>
      </tr>
      <tr>
        <th align='right'>Default Switch:</th>
        <td>${settings.useDefault
                ? deviceTag(getSwitchById(settings.dfltSwitchNameId))
                : 'N/A'
            }
        </td>
      </tr>
    </table>
  """
}
*/

void enforceDefault() {
  if (settings.LOG) log.trace 'enforceDefault()'
  if (settings.dfltSwitchNameId) {
    List<DeviceWrapper> onDevices = getOnSwitches()
    if (onDevices.size() == 0) {
      //--x-- logSettingsAndState('enforceDefault() triggered IN')
      DeviceWrapper dfltSwitchName = getSwitchById(settings.dfltSwitchNameId)
      if (settings.LOG) log.trace "enforceDefault() turning on ${deviceTag(dfltSwitchName)}."
      dfltSwitchName.on()
      //--x-- logSettingsAndState('enforceDefault() triggered OUT')
    }
  }
}

void enforceMutualExclusion() {
  if (settings.LOG) log.trace 'enforceMutualExclusion()'
  List<DeviceWrapper> onList = getOnSwitches()
  while (onList.size() > 1) {
    DeviceWrapper device = onList.first()
    if (settings.LOG) log.trace "enforceMutualExclusion() turning off ${deviceTag(device)}."
    device.off()
    onList = onList.drop(1)
  }
}

void logEventDetails (Event e, Boolean errorMode = false) {
  if (settings.LOG || errorMode) {
    String rows = """
      <tr>
        <th align='right'>descriptionText</th>
        <td>${e.descriptionText}</td>
      </tr>
      <tr>
        <th align='right'>deviceId</th>
        <td>${e.deviceId}</td>
      </tr>
      <tr>
        <th align='right'>displayName</th>
        <td>${e.displayName}</td>
      </tr>
    """
    if (errorMode) {
      rows += """
        <tr>
          <th align='right'>isStateChange</th>
          <td>${e.isStateChange}</td>
        </tr>
        <tr>
          <th align='right'>date</th>
          <td>${e.date}</td>
        </tr>
        <tr>
          <th align='right'>class</th>
          <td>${e.class}</td>
        </tr>
        <tr>
          <th align='right'>unixTime</th>
          <td>${e.unixTime}</td>
        </tr>
        <tr>
          <th align='right'>name</th>
          <td>${e.name}</td>
        </tr>
        <tr>
          <th align='right'>value</th>
          <td>${e.value}</td>
        </tr>
      """
      log.error """Unexpected event in ${calledBy}:<br/>
        Received an event that IS NOT a state change.<br/>
        <table>${rows}</table>
      """
    } else {
      log.trace """Event highlights from ${calledBy}:<br/>
      <table>${rows}</table>"""
    }
  }
}

// https://stackoverflow.com/questions/52151242/groovy-possible-to-build-map-of-functions
// Closure in map gets executed at the definition point in the map and later when called.
// inside the map:
//   fn: { arg1, arg2 -> ... }
// As needed
//   myMap.getAt('fn').call('val1', 'val2')

void buttonHandler (Event e) {
  if (e.isStateChange) {
    //--x-- logSettingsAndState('buttonHandler()')
    DeviceWrapper eventDevice = getSwitchById(e.deviceId.toString())
    switch(e.value) {
      case 'on':
        // Turn off peers in switch group.
        getOnSwitches().each({ sw ->
          if (sw.id != eventDevice.id) {
            if (settings.LOG) log.trace "buttonHandler() turning off ${deviceTag(sw)}."
            sw.off()
          }
        })
        break
      case 'off':
        enforceDefault()
        break
      default:
        log.error  "buttonHandler() expected 'on' or 'off'; but, \
          received '${e.value}'."
    }
  } else {
    // Report this condition as an ERROR and explore further IF it occurs.
    //--x-- logSettingsAndState('buttonHandler()', true)
  }
}

void initialize() {
  if (settings.LOG) log.trace 'initialize()'
  enforceMutualExclusion()
  enforceDefault()
  //--x-- logSettingsAndState('initialize() about to enable subscription(s)')
  subscribe(settings.swGroup, "switch", buttonHandler)
}

String emphasizeOn(String s) {
  return s == 'on' ? '<b>on</b>' : "<em>${s}</em>"
}

void uninstalled() {
  if (settings.LOG) log.trace 'uninstalled()'
  // Nothing to do. Subscruptions are automatically dropped.
  // This may matter if devices are captured by a switch group in the future.
}
