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
import com.hubitat.app.ChildDeviceWrapper as ChildDeviceWrapper
import com.hubitat.app.DeviceWrapper as DeviceWrapper
import com.hubitat.app.DeviceWrapperList as DeviceWrapperList
import com.hubitat.hub.domain.Event as Event


library (
 name: 'PBSG',
 namespace: 'wesmc',
 author: 'WesleyMConner',
 description: 'PBSG is a headless version of Pushbutton Switch Group',
 category: 'general purpose',
 documentationLink: '',
 importUrl: ''
)

ChildDeviceWrapper createChildVsw (String name) {
   // Simplify child device creation (see below).
   // Perform DUP checking here to avoid noise.
}

// -------------------------------------
// I N S T A N C E   M A N A G E M E N T
// -------------------------------------
Map createPBSG (Map args = [:]) {
  def _args = [
              name: "Expected 'name' (String)",
       switchNames: "Expected 'switchNames' (List<String>)",
    dfltSwitchName: "Expected 'dfltSwitchName' (String)",
  ] << args
  log.trace "createPBSG() Creating ${_args.name}<"
  if (state[_args.name]) {
    log.error "createPBSG() '${_args.name}' instance already exists."
  } else if (_args.dfltSwitchName != 'None'
             && ! _args.switchNames.contains(_args.dfltSwitchName)) {
    log.error "createPBSG() dfltSwitchName (${_args.dfltSwitchName}) "
      + "not found in switchNames (${_args.switchNames})."
  } else {
    // Popuate initial instance data
    state[_args.name] = _args
    log.trace "pbsg: ${state[_args.name]}"
    // Create required Virtual Switch children (NEED TO CHECK FOR DUPS)
    List<ChildDeviceWrapper> vsws = []
    pbsg.switchNames.each{ swName ->
      // groovy.lang.MissingMethodException:
      // No signature of method: user_app_wesmc_WholeHouseAutomation_332.
      // addChildDevice() is applicable for argument types:
      // (java.util.LinkedHashMap) values: [[
      //   namespace:wesmc, typeName:VirtualSwitch,
      //   deviceNetworkId:pbsg-pbsg-modes-Day]]
      vsws += addChildDevice(
        'hubitat',                      // namespace
        'Virtual Switch',               // typeName
        "pbsg-${pbsg.name}-${swName}",  // deviceNetworkId
        [isComponent: true, name: "pbsg-${pbsg.name}-${swName}"]
      )
        //namespace: 'wesmc',
        //typeName: 'VirtualSwitch',
        //deviceNetworkId: "pbsg-${pbsg.name}-${swName}"
    }
    state[_args.name].vsws = vsws
    // At #82 Cannot cast object '[pbsg-pbsg-modes-Day, ... pbsg-pbsg-modes-Night]'
    // with class 'java.util.ArrayList' to class 'java.util.Map'
    // YOU ARE HERE ---->2023-08-05 02:48:23.319 PMerrororg.codehaus.groovy.runtime.typehandling.GroovyCastException: Cannot cast object 'user_app_wesmc_WholeHouseAutomation_332$_createPBSG_closure20@181a9e' with class 'user_app_wesmc_WholeHouseAutomation_332$_createPBSG_closure20' to class 'java.util.Map' on line 504 (method updated) (library wesmc.PBSG, line 78)
    //-- NOT READY -> SEE TEST BELOW
    //-- NOT READY -> pbsg.handler = { event ->
    //-- NOT READY ->   log.trace "Placeholder handler with ${event}."
    //-- NOT READY -> }
    //-- NOT READY -> subscribe(pbsg.vsws, "switch", pbsg.&handler)
  }
}

  // T E S T   B E G I N ==================================================
  /*
  Closure handlerFactory = { e, pbsgInst ->
    "Arg '${e}', '${pbsgInst.a}' and '${pbsgInst.b}'."
  }
  def pbsgA = [
    a: "This is a string",
    b: "another string,"
  ]
  log.trace "pbsgA: ${pbsgA}"
  def handler = { e -> handlerFactory.call(e, pbsgA) }
  log.trace "handler('puppies'): ${handler('puppies')}"
  */
  // T E S T   E N D ======================================================


List<DeviceWrapper> getOnSwitches() {
  DeviceWrapperList devices = state[stateName]
  return devices?.findAll({ extractSwitchState(it) == 'on' })
}


void deletePBSG (String name) {
  String stateName = "pbsg-${name}"
  if (state[stateName]) {
    unsubscribe(state[stateName].vsws)
    state[stateName].vsws.each{ device-> deleteChildDevice(device.deviceNetworkId) }
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
/*
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

DeviceWrapper getSwitchById(String id) {
  DeviceWrapperList devices = settings.swGroup
  return devices?.find({ it.id == id })
}

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
//
// https://freecontent.manning.com/wp-content/uploads/declaring-and-using-closures.pdf
// In Groovy, Closure's curry method returns a clone of the current closure,
// having bound one or more parameters to a given value.
// PAGE 7 Top DIAGRAM

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
*/
