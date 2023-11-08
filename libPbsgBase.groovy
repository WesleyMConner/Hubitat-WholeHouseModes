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
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.hub.domain.Event as Event

library (
  name: 'libPbsgBase',
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'PBSG (headless Pushbutton Switch Group)',
  category: 'general purpose',
  documentationLink: '',
  importUrl: ''
)

//----
//---- CORE APPLICATION
//----   Methods that ARE NOT constrained to any specific execution context.
//----

//--
//-- EXTERNAL METHODS
//--

void turnOnExclusivelyByName (String vswName) {
  // The child devices of a PBSG should be limited to its managed VSWs.
  Linfo('turnOnExclusivelyByName()', "Turning on <b>${vswName}</b> exclusively")
  // Turn off peers BEFORE turning on vswDni!
  List<String> peers = atomicState.vswNames?.findAll{ name -> name != vswName }
  //--DEEP-DIVE-> Ltrace('turnOnExclusivelyByName()', "turning off '${peers}'")
  peers.each{ peerName ->
    DevW peerVsw = getChildDevice(vswNameToDni(peerName))
    if (!peerVsw) {
      Lerror(
        'turnOnExclusivelyByName()',
        "peerVsw named '${peerName}' is missing"
      )
    }
    peerVsw.off()
  }
  DevW vsw = getChildDevice(vswNameToDni(vswName))
  if (!vsw) {
    Lerror(
      'turnOnExclusivelyByName()',
      "Cannot find vswName: '<b>${vswName}</b>' to turn on"
    )
  } else {
    //--DEEP-DIVE-> Linfo(
    //--DEEP-DIVE->   'turnOnExclusivelyByName()',
    //--DEEP-DIVE->   "turning on vsw named '${vswName}'"
    //--DEEP-DIVE-> )
    vsw.on()
  }
}

void turnOffByName (String vswName) {
  DevW vsw = getChildDevice(vswNameToDni(vswName))
  if (!vsw) Lerror('turnOffByName()', "vsw named '${vswName}' is missing")
  Linfo('turnOffByName()', "turning off vsw named '${vswName}'")
  vsw.off()
  enforceDefaultVsw()
}

void toggleByName (String vswName) {
  DevW vsw = getChildDevice(vswNameToDni(vswName))
  if (!vsw) Lerror('toggleByName()', "vsw named '${vswName}' is missing")
  String switchState = GetSwitchState(vsw)
  switch (switchState) {
    case 'on':
      Linfo('toggleByName()', "vswName: ${vswName} on() -> off()")
      vsw.off()
      break;
    case 'off':
      Linfo('toggleByName()', "vswDni: ${vswDni} off() -> on()")
      vsw.on()
      //-> vsw.on()
      break;
    default:
      Lerror('toggleByName()', "unexpected switchState: ${switchState}")
  }
}

void configurePbsg (
    String dniPrefix,
    List<String> buttonNames,
    String defaultButton = null,
    String logThreshold = 'TRACE'
  ) {
  removeLegacyModePbsgState()
  atomicState.vswDniPrefix = "${app.getLabel()}_"
  atomicState.vswNames = GetModeNames()
  atomicState.vswDefaultName = getGlobalVar('DEFAULT_MODE').value
  atomicState.logLevel = parent.getLogLevel() ?: lookupLogLevel('TRACE')
}



String getPbsgStateBullets () {
  // Include the state of the PBSG itself AND a summary of current VSW
  // switch values.
  List<String> result = []
  atomicState.sort().each{ k, v ->
    if (k == 'vswDnis') {
      result += Bullet1("<b>${k}</b>")
      v.each{ dni ->
        DevW vsw = getChildDevice(dni)
        String vswState = vsw ? GetSwitchState(vsw) : null
        String vswWithState = vsw
          ? "→ ${vswState} - ${vsw.name}"
          : "VSW DNI '${dni}' DOES NOT EXIST"
        result += (vswState == 'on') ? "<b>${vswWithState}</b>" : "<i>${vswWithState}</i>"
      }
    } else {
      result += Bullet1("<b>${k}</b> → ${v}")
    }
  }
  return result.join('<br/>')
}

//--
//-- INTERNAL METHODS
//--

String vswDnitoName (String vswDni) {
  return vswDni.minus("${atomicState.vswDniPrefix}")
}

String vswNameToDni (String name) {
  return "${atomicState.vswDniPrefix}${name}"
}

DevW getVswByDni (String vswDni) {
  return getChildDevice(vswDni)
}

Map<String, List<DevW>> getChildDevicesByState () {
  // States:
  //   'nonSwitch': Does not support capability 'switch'
  //      'orphan': Unexpected devices per PBSG state
  //          'on': Expected devices that are 'on'
  //         'off': Expected devices that are 'off'
  // Device DNIs are unique !!!
  List<DevW> foundDevices = getAllChildDevices()
  List<String> expectedDnis = atomicState.vswNames.collect{ vswNameToDni(it) }
  List<DevW> nonSwitch = []
  List<DevW> orphan = []
  List<DevW> onList = []
  List<DevW> offList = []
  foundDevices.each{ d ->
    if (d.hasCapability('switch')) {
      String dni = d.getDeviceNetworkId()
      if (expectedDnis.contains(dni)) {
        if (GetSwitchState(d) == 'on') {
          onList += d
        } else {
          offList += d
        }
      } else {
        orphan += d
      }
    } else {
      nonSwitch += d
    }
  }
  return [
    'nonSwitch': nonSwitch,
    'orphan': orphan,
    'onList': onList,
    'offList': offList
  ]
}

Boolean isValidPbsg (Map<String, List<DevW>> buckets = getChildDevicesByState()) {
  Boolean result = true
  if (!buckets) return false
  if (buckets.nonSwitch.size() != 0) {
    result = false
    List<String> dnis = buckets.nonSwitch.collect{ it.getDeviceNetworkId() }
    LError('isValidPbsg()', "nonSwitch DNIs: ${dnis}")
  }
  if (buckets.orphan.size() != 0) {
    result = false
    List<String> dnis = buckets.orphan.collect{ it.getDeviceNetworkId() }
    LError('isValidPbsg()', "orphan DNIs: ${dnis}")
  }
  if (buckets.onList.size() == 1) {
    result = false
    List<String> onNames = buckets.onList.collect{ vswDnitoName(it.getDeviceNetworkId()) }
    List<String> offNames = buckets.offList.collect{ vswDnitoName(it.getDeviceNetworkId()) }
    LError(
      'isValidPbsg()',
      [
        "onList size (${buckets.onList.size()}) != 1",
        "<b>on:</b> ${onNames.join(', ')}",
        "<b>off: ${offNames.join(', ')}"
      ].join('<br/>&nbsp;&nbsp;')
    )
  }
  List<String> missingNames = addMissingDeviceNames()
  if (missingNames > 0) {
    result = false
    Lerror(
      'isValidPbsg()', "Missing devices: ${missingNames.join(', ')}"
    )
  }
  return result
}

void turnOnDefaultVsw () {
  DevW vsw = app.getChildDevice(vswNameToDni(atomicState.vswDefaultName))
  vsw.on()
}

void enforceMutualExclusion () {
  Ltrace('enforceMutualExclusion()', 'At entry')
  List<DevW> onVsws = getChildDevicesByState()?.getAt('on')
  DevW keepDevice = onVsws.pop()
  if (!keepDevice) {
    Ldebug(
      'enforceMutualExclusion()',
      [
        '',
        'Encountered ZERO "on" switches, which is unexpected!',
        "Turning on default switch name '<b>${atomicState.vswDefaultName}</b>'"
      ].join('<br/>&nbsp;&nbsp;')
    )
    turnOnDefaultVsw()
  } else {
    onVsws.each{ vsw ->
      Linfo(
        'enforceMutualExclusion()',
        "turning off <b>${GetDeviceInfo(device)}</b>"
      )
      vsw.off()
    }
  }
}

String getDefaultVswDni () {
  return vswNameToDni(atomicState.vswDefaultName)
}

void enforceDefaultVsw () {
  if (getChildDevicesByState()?.getAt('on')?.size() == 0) {
    turnOnDefaultVsw()
  }
}

void managePbsgChildDevices () {
  // Remove unwanted devices. See also getMissingDeviceNames().
  Map<String, List<DevW>> buckets = getChildDevicesByState()
  buckets.nonSwitch.each{ dni ->
    Lwarn(
      'managePbsgChildDevices()',
      "Deleting <b>non-Switch</b> child device '${dni}'"
    )
    deleteChildDevice(dni)
  }
  buckets.orphan.each{ dni ->
    Lwarn(
      'managePbsgChildDevices()',
      "Deleting <b>orphan</b> child device '${dni}'"
    )
    deleteChildDevice(dni)
  }
  // Add missing devices
  List<String> expectedNames = atomicState.vswNames.collect{}  // Deep copy
  List<String> foundDeviceNames = getAllChildDevices().collect{
    vswDnitoName(it.getDeviceNetworkId())
  }
  expectedNames.removeAll(foundDeviceNames).each{ name ->
    String newDni = vswNameToDni(name)
    addChildDevice(
      'hubitat',          // namespace
      'Virtual Switch',   // typeName
      newDni,             // device's unique DNI
      [isComponent: true, name: newDni]
    )

  }
}

void addOrphanChild_TESTING_ONLY () {
  // T E S T I N G   O N L Y
  //   - This method supports orphan removal testing. See managePbsgChildDevices().
  addChildDevice(
    'hubitat',         // namespace
    'Virtual Switch',  // typeName
    'bogus_device',
    [isComponent: true, name: 'bogus_device']
  )
}

void exercisePbsgMethods_TESTING_ONLY () {
  // Baseline Expected VSWs per State
  // Confirm Child VSWs match Expected VSWs
  // Add Orphan VSW
  // Confirm Child VSWs DO NOT match Expected VSWs
  // Manage Child Devices
  // Confirm Child VSWs match Expected VSWs
  // Get VSW state and confirm a single VSW is active
  // Per VSW, turn on VSW and confirm expected VSW is the only enabled VSW
  // Turn off current VSW and ensure default VSW is enabled
}

//----
//---- SYSTEM CALLBACKS
//----   Methods specific to this execution context
//----   See downstream instances (modePbsg, roomScenePbsg)

// System

/*
void installed () {
  Ltrace('installed()', 'calling configureModePbsg()')
  configureModePbsg()
}

void updated () {
  Ltrace('updated()', 'calling configureModePbsg()')
  configureModePbsg()
}

void uninstalled () {
  Lwarn('uninstalled()', 'DELETING CHILD DEVICES')
  getAllChildDevices().collect{ device ->
    Lwarn('uninstalled()', "Deleting '${device.deviceNetworkId}'")
    deleteChildDevice(device.deviceNetworkId)
  }
}
*/

//----
//---- EVENT HANDLERS
//----   Methods specific to this execution context
//----

void vswEventHandler (Event e) {
  // Key Principles
  //   - Downstream Apps SHOULD subscribe to PBSG events.
  //   - Downstream Apps SHOULD NOT subscribe to PBSG VSW events.
  //   - This event handler determines when (or if) the PBSG should emit
  //     an event to downstream Apps.
  //   - The PBSG should ensure constraints (mutual exclusion, default switch)
  //     are MET before emiting events when possible.
  //   - As a convention, PBSG turns on a newly active push-button VSW
  //     BEFORE it turns off a previously active push-button VSW.
  //   - The PBSG DOES NOT track the currently emited value, relying instead
  //     on Hubitat to determine if the event IS or IS NOT a state change.
  DevW d = getChildDevice(e.displayName)
  Ldebug('modeVswEventHandler()', GetDeviceInfo(d))
  Ldebug('modeVswEventHandler()', d.getCapabilities().join(', '))
  // VSW Capabilities are: 'Switch' and 'Refresh'
  if (e.isStateChange) {
    if (e.value == 'on') {
      // The PBSG should only emit an event if the VSW is uniquely on.
      Map<String, List<DevW>> vswBuckets = getChildDevicesByState()
      if (isValidPbsg(vswBuckets)) {
        List<DevW> onSwitches = vswBuckets.getAt('on')
        if (onSwitches?.size() == 1) {
          String switchName = vswDnitoName(e.displayName)
          Linfo('vswEventHandler()', "Publishing PBSG event ")
          sendEvent([
            name: 'PbsgCurrentSwitch',
            value: switchName,
            descriptionText: "'<b>${switchName}</b>' is exclusively on"
          ])
        } else {
          enforceMutualExclusion()
        }
      } else if (e.value == 'off' && vswBuckets.getAt('on')?.size() == 0) {
        // If all VSWs are off, one of two things happens ...
        if (atomicState.vswDefaultName) {
          // (1) The available default VSW is turned on. But, the PBSG
          //     DOES NOT publish a PBSG event yet - waiting instead for
          //     Handler to receive the default VSW's 'on' event.
          // DO NOTHING !
        } else {
          // (2) There being no default switch, the PBSG publishes a null.
          sendEvent([
            name: 'PbsgCurrentSwitch',
            value: null,
            descriptionText: '<b>No PBSG switch is active</b>'
          ])
        }
      }
    } else {
      Lwarn('modeVswEventHandler()', "Ignoring '${e.descriptionText}'")
    }
  }
}

//----
//---- SCHEDULED ROUTINES
//----   Methods specific to this execution context
//----

//----
//---- RENDERING AND DISPLAY
//----   Methods specific to this execution context
//----

void pbsgBasePage () {
  section {
    paragraph Heading1(GetAppInfo(app))
    paragraph (
      [
        '<h3><b>STATE</b></h3>',
        getPbsgStateBullets() ?: bullet('<i>NO DATA AVAILABLE</i>'),
      ].join()
    )
  }
}
