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

// ----------------------------------------------------------------------------------
// S T A N D - A L O N E   M E T H O D S
// ----------------------------------------------------------------------------------
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

List<DeviceWrapper> getOnSwitches(DeviceWrapperList devices) {
  return devices?.findAll({ extractSwitchState(it) == 'on' })
}

void enforceMutualExclusion(DeviceWrapperList devices) {
  if (settings.LOG) log.trace 'enforceMutualExclusion()'
  List<DeviceWrapper> onList = getOnSwitches(devices)
  while (onList.size() > 1) {
    DeviceWrapper device = onList.first()
    if (settings.LOG) log.trace "enforceMutualExclusion() turning off ${deviceTag(device)}."
    device.off()
    onList = onList.drop(1)
  }
}

Map<String, ChildDeviceWrapper> createChildVsws (
  Map<String, DeviceWrapper> scene2Vsw, String deviceIdPrefix) {
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

// ------------------------------------------------------------------------
// C L O S U R E S   ( I N S T A N C E   M E T H O D S )
//   Methods, written as closures, operate on the state data produced by
//   createPBSG().
// ------------------------------------------------------------------------
/*
PRIOR_pbsgVswEventHandler = { event, pbsgInst ->
  // ----------------------------------------------------------------------
  // DO I NEED TO REFRESH THE DEVICES IN PBSG TO GET ACCURATE SWITCH DATA?
  // PRESUMABLY, EVERYTHING WOULD BE ACCURATE DUE TO PRIOR EVENT HANDLING.
  // ----------------------------------------------------------------------
  // event.displayName
  if (event.isStateChange) {
    switch(event.value) {
      case 'on':
        if (settings.LOG) log.trace "pbsgVswEventHandler() ${event.displayName}"
          + 'turned "ON". Turning off switch group peers.'
        pbsgInst.scene2Vsw.each{ scene, vsw ->
          // No harm in turning off a VSW that might already be off.
          if (vsw.deviceNetworkId != event.displayName) vsw.off()
        }
        break
      case 'off':
        //-- PENDING -> enforceDefault()
        break
      default:
        log.error  'pbsgVswEventHandler() expected 'on' or 'off'; but, '
          + "received '${event.value}'."
        app.updateLabel("${_args.enclosingApp} - BROKEN")
    }
  } else {
    log.error 'pbsgVswEventHandler() received an unexpected event:<br/>'
      + logEventDetails(event)
  }
}
*/

void pbsgVswEventHandler (event) {
  // ----------------------------------------------------------------------
  // DO I NEED TO REFRESH THE DEVICES IN PBSG TO GET ACCURATE SWITCH DATA?
  // PRESUMABLY, EVERYTHING WOULD BE ACCURATE DUE TO PRIOR EVENT HANDLING.
  // ----------------------------------------------------------------------
  // event.displayName
  if (settings.LOG) log.trace "pbsgVswEventHandler() w/ parent App: '${event.deviceId.getParentAppId()}'."
  /*
  pbsg = state[pbsgName]
  if (event.isStateChange) {
    switch(event.value) {
      case 'on':
        if (settings.LOG) log.trace "pbsgVswEventHandler() ${event.displayName}"
          + 'turned "ON". Turning off switch group peers.'
        pbsg.scene2Vsw.each{ scene, vsw ->
          // No harm in turning off a VSW that might already be off.
          if (vsw.deviceNetworkId != event.displayName) vsw.off()
        }
        break
      case 'off':
        //-- PENDING -> enforceDefault()
        break
      default:
        log.error  'pbsgVswEventHandler() expected 'on' or 'off'; but, '
          + "received '${event.value}'."
        app.updateLabel("${pbsg.enclosingApp} - BROKEN")
    }
  } else {
    log.error 'pbsgVswEventHandler() received an unexpected event:<br/>'
      + logEventDetails(event)
  }
  */
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
    LinkedHashMap pbsg = [:]
    pbsg << _args
    pbsg.scene2Vsw = createChildVsws(
      _args.sceneNames.collectEntries{ switchName -> [ switchName, null ] },
      _args.name
    )
    //                         pbsgVswEventHandler
    //--pbsg.eventHandler = { e -> pbsgVswEventHandler.call(e, pbsg) }
    //--pbsg.eventHandler = { e -> pbsgVswEventHandler.call(e, pbsg.name) }
    state[pbsg.name] = pbsg

    if (settings.LOG) log.trace "pbsg: instantiated ${state[_args.name]}"

    // --------------------------------------------------------------------
    // D E V I C E   C A L L B A C K   W E I R D N E S S
    //   Device event subscriptions are problematic:
    //     - Per-device subscriptions are utilized to avoid the conflict
    //       between types 'DeviceWrapperList' and 'List<DeviceWrapper>'.
    //     - No device event signature accepts an actual handler function.
    //       All device options require the name (String) of the callback.
    //     - void subscribe(DeviceWrapper device, String handlerMethod, Map options = null)
    // --------------------------------------------------------------------
    //--take1->String callbackFn = "{ e -> pbsgVswEventHandler.call(e, '${pbsg.name}') }"
    //--take1->if (settings.LOG) log.trace "createPBSG() w/ callbackFn: ${callbackFn}"
    //--take1->pbsg.scene2Vsw.each{ scene, vsw ->
    //--take1->  subscribe(
    //--take1->    vsw,                     // DeviceWrapper
    //--take1->    callbackFn,              // String
    //--take1->    [ filterEvents: false ]  // Map (of subsription options)
    //--take1->  )
    //--take1->}

    //String callbackFn = "state['${pbsg.name}'].eventHandler"
    //--if (settings.LOG) log.trace "createPBSG() w/ callbackFn: ${callbackFn}"
    pbsg.scene2Vsw.each{ scene, vsw ->
      subscribe(
        vsw,                     // DeviceWrapper
        'pbsgVswEventHandler',     // callbackFn,              // String
        [ filterEvents: false ]  // Map (of subsription options)
      )
    }

  }
}

  //===== T E S T   B E G I N =============================================
  //===== Closure handlerFactory = { e, pbsgInst ->
  //=====   "Arg '${e}', '${pbsgInst.a}' and '${pbsgInst.b}'."
  //===== }
  //===== def pbsgA = [
  //=====   a: "This is a string",
  //=====   b: "another string,"
  //===== ]
  //===== if (settings.LOG) log.trace "pbsgA: ${pbsgA}"
  //===== def handler = { e -> handlerFactory.call(e, pbsgA) }
  //===== if (settings.LOG) log.trace "handler('puppies'): ${handler('puppies')}"
  //===== T E S T   E N D =================================================


void deletePBSG (Map args = [:]) {
  // Add default arguments here.
  def _args = [
    enclosingApp: app.getLabel(),
    dropChildVSWs: true
  ] << args
  if (!args.name) {
    log.error([
      'deletePBSG() expects arguments:<br/>',
      '  name: ... (String)<br/>'
    ].join())
    app.updateLabel("${_args.enclosingApp} - BROKEN")
  } else if (state[_args.name]) {
    //--TODO-> unsubscribe(state[_args.name].vsws)
    if (_args.dropChildVSWs) {
      state[_args.name].scene2Vsw.each{ scene, vsw ->
        if (settings.LOG) log.trace "deletePBSG() deleting ${vsw.deviceNetworkId}"
          deleteChildDevice(vsw.deviceNetworkId)
      }
    }
    state[_args.name] = null
  } else {
    log.info "deletePBSG() no state to delete for '${_args.name}'."
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
  if (settings.LOG) log.trace "pbsgA: ${pbsgA}"
  def handler = { e -> handlerFactory.call(e, pbsgA) }
  if (settings.LOG) log.trace "handler('puppies'): ${handler('puppies')}"
  */
  // T E S T   E N D ======================================================


// ---------------------------------------------------
// S U P P O R T I N G   M E T H O D S
// ---------------------------------------------------

/*
DeviceWrapper getSwitchById(String id) {
  DeviceWrapperList devices = settings.swGroup
  return devices?.find({ it.id == id })
}

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

