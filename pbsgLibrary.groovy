// ---------------------------------------------------------------------------------
// P B S G   -   ( B A S E D   O N   P U S H B U T T O N   S W I T C H )
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
// ---------------------------------------------------------------------------------
import com.hubitat.app.ChildDeviceWrapper as ChildDevW
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.DeviceWrapperList as DevWL
import com.hubitat.hub.domain.Event as Event

library (
  name: 'pbsgLibrary',
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'PBSG (headless Pushbutton Switch Group)',
  category: 'general purpose',
  documentationLink: '',
  importUrl: ''
)






// ---------------------------------------
// A P P   I N S T A N C E   M E T H O D S
// ---------------------------------------

//--xx->void displayPage (String pbsgName) {
//--xx->  paragraph "You are in the ${app.getLabel()}"
//--xx->}

void createChildVsws () {
  // FOR TESTING THE REMOVAL OF AN ORPHANED DEVICE, UNCOMMENT THE FOLLOWING:
  //-> addChildDevice(
  //->   'hubitat',         // namespace
  //->   'Virtual Switch',  // typeName
  //->   'bogus_device',
  //->   [isComponent: true, name: 'bogus_device']
  //-> )
  // Device creation "errors out" if deviceNetworkId is duplicated.
  // GET ALL CHILD DEVICES AT ENTRY
  List<String> childDevices = getAllChildDevices().collect{ d -> d.deviceNetworkId }
  if (settings.LOG) log.trace(
    "createChildVsws() devices at entry: ${childDevices}."
  )
  // ENSURE GOAL CHILD DEVICES EXIST
  LinkedHashMap<String, String> scene2VswNetwkId = state.switchNames.collectEntries{
    swName ->
      String deviceNetworkId = "${app.getLabel()}_${swName}"
      String vswNetworkId = childDevices.find{ it == deviceNetworkId }
        ?: addChildDevice(
            'hubitat',         // namespace
            'Virtual Switch',  // typeName
            deviceNetworkId,
            [isComponent: true, name: deviceNetworkId]
           ).deviceNetworkId
      [swName, vswNetworkId]
  }
  state.scene2VswNetwkId = scene2VswNetwkId
  if (settings.LOG) log.trace(
    "createChildVsws() scene2VswNetwkId: ${scene2VswNetwkId}."
  )
  // DELETE ORPHANED DEVICES
  List<String> currentChildren = scene2VswNetwkId.collect{ it.value }
  List<String> orphanedDevices = childDevices.minus(currentChildren)
  orphanedDevices.each{ deviceNetworkId ->
    if (settings.LOG) log.trace(
      "createChildVsws() dropping orphaned device `${deviceNetworkId}`."
    )
    deleteChildDevice(deviceNetworkId)
  }
}

Map createVSWs () {
  pbsg.scene2Vsw.each{ scene, vsw ->
    subscribe(
      vsw,                     // DevW
      'pbsgVswEventHandler',     // callbackFn,              // String
      [ filterEvents: false ]  // Map (of subsription options)
    )
  }
}

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

// -----------------------------------------
// I N T E R N A L - O N L Y   M E T H O D S
// -----------------------------------------
String extractSwitchState(DevW d) {
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

List<DevW> getOnSwitches(DevWL devices) {
  return devices?.findAll({ extractSwitchState(it) == 'on' })
}

void enforceMutualExclusion(DevWL devices) {
  if (settings.LOG) log.trace 'enforceMutualExclusion()'
  List<DevW> onList = getOnSwitches(devices)
  while (onList.size() > 1) {
    DevW device = onList.first()
    if (settings.LOG) log.trace "enforceMutualExclusion() turning off ${deviceTag(device)}."
    device.off()
    onList = onList.drop(1)
  }
}

void pbsgVswEventHandler (event) {
  // ----------------------------------------------------------------------
  // DO I NEED TO REFRESH THE DEVICES IN PBSG TO GET ACCURATE SWITCH DATA?
  // PRESUMABLY, EVERYTHING WOULD BE ACCURATE DUE TO PRIOR EVENT HANDLING.
  // ----------------------------------------------------------------------
  // event.displayName
  if (settings.LOG) log.trace "pbsgVswEventHandler() w/ parent App: '${event.deviceId.getParentAppId()}'."
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
}

/*
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

