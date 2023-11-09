// ---------------------------------------------------------------------------------
// P B S G   -   P U S H B U T T O N   S W I T C H   A P P
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
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Location as Loc
#include wesmc.libLogAndDisplay
#include wesmc.libUtils

definition(
  parent: 'wesmc:wha',
  name: 'PBSG',
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'A PBSG App instance, typically created by other Apps',
  category: '',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: '',
  singleInstance: false
)

preferences {
  page(name: 'pbsgPage')
}

//----
//---- CORE APPLICATION
//----   Methods that ARE NOT constrained to any specific execution context.
//----

//-- EXTERNAL METHODS

void pbsgConfigure (
    List<String> buttonNames,
    String defaultButton,
    String logThreshold
) {
  // No legacy atomicState to remove
  // No legacy settings to remove
  atomicState.vswDniPrefix = "${app.getLabel()}_"
  atomicState.buttonNames = buttonNames
  atomicState.vswDefaultButtonName = defaultButton
  atomicState.logLevel = lookupLogLevel(logThreshold)
}

void pbsgAdjustLogLevel (String logThreshold) {
  atomicState.logLevel = lookupLogLevel(logThreshold)
}

void pbsgTurnOn (String buttonName) {
  // Abstract
  //   Turns on one of the PBSG's VSWs exclusively.
  List<String> peerButtons = atomicState.buttonNames?.findAll{ name ->
    name != buttonName
  }
  peerButtons.each{ peerName ->
    DevW peerVsw = app.getChildDevice(_buttonNameToDni(peerName))
    if (!peerVsw) {
      Lerror('pbsgTurnOn()', "Cannot find peer button ${b(peerName)}")
    }
    peerVsw.off()
  }
  DevW vsw = getChildDevice(_buttonNameToDni(buttonName))
  if (!vsw) {
    Lerror('pbsgTurnOn()', "Cannot find target button ${b(buttonName)}")
  } else {
    Linfo('pbsgTurnOn()', "Turning on ${b(buttonName)}")
    vsw.on()
  }
}

void turnOnDefault () {
  DevW vsw = app.getChildDevice(_buttonNameToDni(atomicState.vswDefaultButtonName))
  vsw.on()
}

void pbsgTurnOff (String buttonName) {
  DevW vsw = getChildDevice(_buttonNameToDni(buttonName))
  if (!vsw) Lerror('pbsgTurnOff()', "Cannot find target button ${b(buttonName)}")
  Linfo('pbsgTurnOff()', "Turning off ${b(buttonName)}")
  vsw.off()
  _enforceDefaultVsw()
}

void pbsgToggle (String buttonName) {
  DevW vsw = getChildDevice(_buttonNameToDni(buttonName))
  if (!vsw) Lerror('pbsgToggle()', "Cannot find target button ${b(buttonName)}")
  String switchState = GetSwitchState(vsw)
  switch (switchState) {
    case 'on':
      Linfo('pbsgToggle()', "${b(buttonName)} on() -> off()")
      vsw.off()
      break;
    case 'off':
      Linfo('pbsgToggle()', "${b(buttonName)} off() -> on()")
      vsw.on()
      break;
    default:
      Lerror('pbsgToggle()', "${b(buttonName)} unexpected value: ${b(switchState)}")
  }
}

String pbsgGetStateBullets () {
  // Include the state of the PBSG itself AND a summary of current VSW
  // switch values.
  List<String> result = []
  atomicState.sort().each{ k, v ->
    if (k == 'vswDnis') {
      result += Bullet1("${b(k)}")
      v.each{ dni ->
        DevW vsw = getChildDevice(dni)
        String vswState = vsw ? GetSwitchState(vsw) : null
        String vswWithState = vsw
          ? "→ ${vswState} - ${vsw.getLabel()}"
          : "Vsw w/ DNI ${b(dni)} DOES NOT EXIST"
        result += (vswState == 'on') ? "<b>${vswWithState}</b>" : "<i>${vswWithState}</i>"
      }
    } else {
      result += Bullet1("<b>${k}</b> → ${v}")
    }
  }
  return result.join('<br/>')
}

Boolean isValidPbsg (Map<String, List<DevW>> buckets = _getDevicesByState()) {
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
    List<String> onNames = buckets.onList.collect{ _vswDnitoButtonName(it.getDeviceNetworkId()) }
    List<String> offNames = buckets.offList.collect{ _vswDnitoButtonName(it.getDeviceNetworkId()) }
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

//--
//-- INTERNAL METHODS
//--

String _vswDnitoButtonName (String vswDni) {
  return vswDni.minus("${atomicState.vswDniPrefix}")
}

String _buttonNameToDni (String name) {
  return "${atomicState.vswDniPrefix}${name}"
}

Map<String, List<DevW>> _getDevicesByState () {
  // This method obtains GROUND TRUTH child device data.
  // States:
  //   'nonSwitch': Does not support capability 'switch'
  //      'orphan': Unexpected devices per PBSG state
  //          'on': Expected devices that are 'on'
  //         'off': Expected devices that are 'off'
  // Device DNIs are unique !!!
  List<DevW> foundDevices = getAllChildDevices()
  List<String> expectedDnis = atomicState.buttonNames.collect{ _buttonNameToDni(it) }
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

void _enforceMutualExclusion () {
  Ltrace('_enforceMutualExclusion()', 'At entry')
  List<DevW> onVsws = _getDevicesByState()?.getAt('on')
  DevW keepDevice = onVsws.pop()
  if (!keepDevice) {
    Ldebug(
      '_enforceMutualExclusion()',
      [
        '',
        'Found NO "on" buttons, which is unexpected!',
        "Turning on default button name ${b(atomicState.vswDefaultButtonName)}"
      ].join('<br/>&nbsp;&nbsp;')
    )
    turnOnDefault()
  } else {
    onVsws.each{ vsw ->
      Ltrace(
        '_enforceMutualExclusion()',
        "turning off ${b(GetDeviceInfo(device))}"
      )
      vsw.off()
    }
  }
}

void _enforceDefaultVsw () {
  if (_getDevicesByState()?.getAt('on')?.size() == 0) {
    Linfo(
      '_enforceDefaultVsw()',
      "Turning on default button name ${b(atomicState.vswDefaultButtonName)}"
    )
    turnOnDefault()
  }
}

void _manageChildDevices () {
  // Remove unwanted devices. See also getMissingDeviceNames().
  Map<String, List<DevW>> buckets = _getDevicesByState()
  buckets.nonSwitch.each{ dni ->
    Lwarn(
      '_manageChildDevices()',
      "Deleting <b>non-Switch</b> child device '${b(dni)}'"
    )
    deleteChildDevice(dni)
  }
  buckets.orphan.each{ dni ->
    Lwarn(
      '_manageChildDevices()',
      "Deleting <b>orphan</b> child device '${b(dni)}'"
    )
    deleteChildDevice(dni)
  }
  // Add missing devices
  List<String> expectedNames = atomicState.buttonNames.collect{}  // Deep copy
  List<String> foundDeviceNames = getAllChildDevices().collect{
    _vswDnitoButtonName(it.getDeviceNetworkId())
  }
  expectedNames.removeAll(foundDeviceNames).each{ name ->
    String newDni = _buttonNameToDni(name)
    addChildDevice(
      'hubitat',          // namespace
      'Virtual Switch',   // typeName
      newDni,             // device's unique DNI
      [isComponent: true, name: newDni]
    )
  }
  Lerror(
    '_manageChildDevices()',
    "PARENT MAY NEED A HANDLER TO ENSURE THE CORRECT BUTTON IS 'on'"
  )
}

void _addOrphanDevice_TESTING_ONLY () {
  // T E S T I N G   O N L Y
  //   - This method supports orphan removal testing. See _manageChildDevices().
  addChildDevice(
    'hubitat',         // namespace
    'Virtual Switch',  // typeName
    'bogus_device',
    [isComponent: true, name: 'bogus_device']
  )
}

void _exercisePbsg_TESTING_ONLY () {
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

void installed () {
  Lerror(
    'installed()',
    'Unexpected: The PBSG UI does not facilitate configuration data'
  )
}

void updated () {
  Lerror(
    'updated()',
    'Unexpected: The PBSG UI does not facilitate configuration changes'
  )
}

void uninstalled () {
  getAllChildDevices().collect{ device ->
    Lwarn('uninstalled()', "Deleting ${b(device.deviceNetworkId)}")
    deleteChildDevice(device.deviceNetworkId)
  }
}

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
      Map<String, List<DevW>> vswBuckets = _getDevicesByState()
      if (isValidPbsg(vswBuckets)) {
        List<DevW> onSwitches = vswBuckets.getAt('on')
        if (onSwitches?.size() == 1) {
          String switchName = _vswDnitoButtonName(e.displayName)
          Linfo('vswEventHandler()', "Publishing PBSG event ")
          sendEvent([
            name: 'PbsgCurrentSwitch',
            value: switchName,
            descriptionText: "${b(switchName)} is exclusively on"
          ])
        } else {
          _enforceMutualExclusion()
        }
      } else if (e.value == 'off' && vswBuckets.getAt('on')?.size() == 0) {
        // If all VSWs are off, one of two things happens ...
        if (atomicState.vswDefaultButtonName) {
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

Map pbsgPage () {
  return dynamicPage(
    name: 'pbsgPage',
    title: Heading1(GetAppInfo(app)),
    install: false,
    uninstall: false,
  ) {
    section {
      paragraph (
        [
          Heading1('About this page...'),
          Bullet1('The parent App configures the log level for this PBSG'),
          Bullet1('Use your browser to return to the prior-page'),
          '',
          Heading1('STATE'),
          pbsgGetStateBullets() ?: Bullet1('<i>NO DATA AVAILABLE</i>'),
        ].join('<br/>')
      )
    }
  }
}
