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
//   - The parent App must have settings.LOG == TRUE for non-error logging.
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

Map<String, ChildDeviceWrapper> createChildVsws (
  Map<String, DeviceWrapper> scene2Vsw,
  String deviceIdPrefix
) {
  // Ensure every scene is mapped to a VSW with no extra child VSWs.
  Map<String, ChildDeviceWrapper> result = scene2Vsw.collectEntries{ scene, vsw ->
    String deviceNetworkId = "${deviceIdPrefix}-${scene}"
    ChildDeviceWrapper existingDevice = getChildDevice(deviceNetworkId)
    if (existingDevice) {
      if (settings.LOG) log.trace "createChildVsws() scene (${scene}) found (${existingDevice})"
      [scene, existingDevice]
    } else {
      ChildDeviceWrapper newChild = addChildDevice(
        'hubitat',         // namespace
        'Virtual Switch',  // typeName
        deviceNetworkId,   // deviceNetworkId
        [isComponent: true, name: deviceNetworkId]
      )
      if (settings.LOG) log.trace "createChildVsws() scene (${scene}) created vsw (${newChild})"
      [scene, newChild]
    }
  }
  // Find and drop child VSWs NOT tied to a scene.
  List<ChildDeviceWrapper> childDevices = getAllChildDevices()
  //--log.trace "#66: createChildVsws()<br/>childDevices: ${childDevices}<br/>result: ${result}"
  childDevices.each{ childDevice ->
    def scenes = result.findAll{
      scene, sceneVsw -> sceneVsw?.deviceNetworkId == childDevice.deviceNetworkId
    }
    if (scenes) {
      if (settings.LOG) log.trace "createChildVsws() keeping ${childDevice.deviceNetworkId} with scenes: ${scenes}"
    } else {
      if (settings.LOG) log.trace "createChildVsws() dropping ${childDevice.deviceNetworkId}"
      deleteChildDevice(childDevice.deviceNetworkId)
    }
  }
  return result
}

// -------------------------------------
// I N S T A N C E   M A N A G E M E N T
// -------------------------------------
Map createPBSG (Map args = [:]) {
  // Add default arguments here.
  def _args = [
    enclosingApp: app.getLabel()
  ] << args
  if (!args.name || !args.sceneNames || !args.defaultScene) {
    log.error([
      'createPBSG() expects arguments:<br/>',
      '          name: ... (String)<br/>',
      '    sceneNames: ... (List&lt;String&gt;)<br/>',
      '  defaultScene: ... (String)'
    ].join())
    app.updateLabel("${_args.enclosingApp ?: app.getLabel()} - BROKEN")
  } else if (state[_args.name]) {
    log.error "createPBSG() '${_args.name}' instance already exists."
    app.updateLabel("${_args.enclosingApp} - BROKEN}")
  } else if (_args.defaultScene && ! _args.sceneNames.contains(_args.defaultScene)) {
    log.error "createPBSG() '${_args.defaultScene}' not found in '${_args.sceneNames}'."
    app.updateLabel("${_args.enclosingApp} - BROKEN}")
  } else {
    // Popuate initial instance data
    state[_args.name] = _args
    state[_args.name].scene2Vsw = createChildVsws(
      _args.sceneNames.collectEntries{ switchName -> [ switchName, null ] },
      _args.name
    )
    if (settings.LOG) log.trace "pbsg: instantiated ${state[_args.name]}"
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

void deletePBSG (String instanceName) {
  if (state[instanceName]) {
    //--TODO-> unsubscribe(state[instanceName].vsws)
    //--TODO-> state[instanceName].vsws.each{ device-> deleteChildDevice(device.deviceNetworkId) }
    state[instanceName] = null
  } else {
    log.error "deletePBSG() no state to delete for '${instanceName}'."
    app.updateLabel("${state[instanceName].enclosingApp} - BROKEN}")
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
  if (settings.defaultSceneId) {
    List<DeviceWrapper> onDevices = getOnSwitches()
    if (onDevices.size() == 0) {
      //--x-- logSettingsAndState('enforceDefault() triggered IN')
      DeviceWrapper defaultScene = getSwitchById(settings.defaultSceneId)
      if (settings.LOG) log.trace "enforceDefault() turning on ${deviceTag(defaultScene)}."
      defaultScene.on()
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
