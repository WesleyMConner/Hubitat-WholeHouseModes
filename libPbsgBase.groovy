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
//---- P U B L I C   P B S G   M E T H O D S
//----   These functions are intended for downstream use.
//----

String _vswDnitoName (String modeVswDni) {
  modeVswDni.minus("${atomicState.vswDniPrefix}")
}

void _turnOffVswByName (String vswName) {
  DevW vsw = app.getChildDevice(_vswNameToDni(vswName))
  if (!vsw) Lerror('turnOffVswByName()', "vsw named '${vswName}' is missing")
  Ltrace('turnOffVswByName()', "turning off vsw named '${vswName}'")
  vsw.off()
  _enforceDefaultSwitch()
}

void _turnOnVswExclusivelyByName (String vswName) {
  // The child devices of a PBSG should be limited to its managed VSWs.
  Linfo('_turnOnVswExclusivelyByName()', "Turning on <b>${vswName}</b> exclusively")
  // Turn off peers BEFORE turning on vswDni!
  List<String> peers = atomicState.vswNames?.findAll{ name -> name != vswName }
  Ltrace('_turnOnVswExclusivelyByName()', "turning off '${peers}'")
  peers.each{ peerName ->
    DevW peerVsw = app.getChildDevice(_vswNameToDni(peerName))
    if (!peerVsw) {
      Lerror(
        '_turnOnVswExclusivelyByName()',
        "peerVsw named '${peerName}' is missing"
      )
    }
    peerVsw.off()
  }
  DevW vsw = app.getChildDevice(_vswNameToDni(vswName))
  if (!vsw) {
    Lerror(
      '_turnOnVswExclusivelyByName()',
      "Cannot find vswName: '<b>${vswName}</b>' to turn on"
    )
  } else {
    Ltrace(
      '_turnOnVswExclusivelyByName()',
      "turning on vsw named '${vswName}'"
    )
    vsw.on()
  }
}

void _toggleVsw (String vswName, passedVsw = null) {
  DevW vsw = passedVsw ?: app.getChildDevice(_vswNameToDni(vswName))
  if (!vsw) Lerror('_toggleVsw()', "vsw named '${vswName}' is missing")
  String switchState = getSwitchState(vsw)
  switch (switchState) {
    case 'on':
      Ldebug('_toggleVsw()', "vswName: ${vswName} on() -> off()")
      vsw.off()
      break;
    case 'off':
      Ldebug('_toggleVsw()', "vswDni: ${vswDni} off() -> on()")
      vsw.on()
      //-> vsw.on()
      break;
    default:
      Lerror('_toggleVsw()', "unexpected switchState: ${switchState}")
  }
}

void _pbsgBasePage () {
  // Abstract
  //   - Include this page content when instantiating PBSG instances.
  //   - Then, call _configPbsg(), see below, to complete device configuration.
  // Forcibly remove unused settings and state, a missing Hubitat feature.
  //-> _removeLegacyPbsgSettingsAndState()
  section {
    paragraph heading1(getAppInfo(app))
    paragraph (
      [
        '<h3><b>STATE</b></h3>',
        _getPbsgStateBullets() ?: bullet('<i>NO DATA AVAILABLE</i>'),
      ].join()
    )
  }
}

String _getPbsgStateBullets () {
  List<String> result = []
  atomicState.sort().each{ k, v ->
    if (k == 'vswDnis') {
      result += bullet1("<b>${k}</b>")
      v.each{ dni ->
        DevW vsw = app.getChildDevice(dni)
        String vswState = vsw ? getSwitchState(vsw) : null
        String vswWithState = vsw
          ? "→ ${vswState} - ${vsw.name}"
          : "VSW DNI '${dni}' DOES NOT EXIST"
        result += (vswState == 'on') ? "<b>${vswWithState}</b>" : "<i>${vswWithState}</i>"
      }
    } else {
      result += bullet1("<b>${k}</b> → ${v}")
    }
  }
  return result.size() != 0 ? result.join('<br/>') : bullet1('<i>NO DATA AVAILABLE</i>')
}

//----
//---- P R I V A T E   P B S G   M E T H O D S
//----   Hubitat does not facilitate Grooy classes or similar advanced
//----   developer tools. The functions below SHOULD NOT be used directly,
//----   but are included in the library since PUBLIC methods incorporate
//----   them (directly or indirectly).
//----

String _vswNameToDni (String name) {
  return "${atomicState.vswDniPrefix}${name}"
}

//-> List<String> _expectedVswDnis () {
//->   List<String> vswDnis = atomicState.vswNames.collect{ _vswNameToDni(it) }
//->   Ltrace(
//->     '_expectedVswDnis ()',
//->     [
//->       '',
//->       "Given <b>atomicState.vswNames:</b> ${atomicState.vswNames}",
//->       "Proeduced <b>vswDnis:</b> ${vswDnis}"
//->     ].join('<br/>')
//->   )
//->   return vswDnis
//-> }

void _manageChildDevices (String caller = "UNKNOWN_CALLER") {
  // Uncomment the following to test orphan child app removal.
  //==T E S T I N G   O N L Y==> _addOrphanChild()
  // The ONLY child devices for a PBSG are its managed VSWs.
  List<String> expectedDnis = atomicState.vswNames.collect{ _vswNameToDni(it) }
  //-> Ltrace(
  //->   '_manageChildDevices() [002a]',
  //->   [
  //->     "Called by '${caller}'",
  //->     _getPbsgStateBullets(),
  //->     "<b>expectedDnis:</b> ${expectedDnis} (${expectedDnis.size()})"
  //->   ].join('<br/>')
  //-> )
  if (expectedDnis.size() > 0) {
    List<String> entryDnis = app.getAllChildDevices().collect{ it.getDeviceNetworkId() }
    // Clone lists with .collect() to avoid shallow copies OR removeAll()
    // will impact the shallow copy AND the original.
    List<String> missingDnis = expectedDnis.collect()
    missingDnis.removeAll(entryDnis)
    List<String> orphanDnis = entryDnis.collect()
    orphanDnis.removeAll(expectedDnis)
    //-> USE THE FOLLOWING FOR HEAVY DEBUGGING ONLY
    Ltrace(
      '_manageChildDevices() [002b]',
      [
        '<table>',
        "<tr><th>expectedDnis</th><td>${expectedDnis} (${expectedDnis.size()})</td></tr>",
        "<tr><th>entryDnis</th><td>${entryDnis} (${entryDnis.size()})</td></tr>",
        "<tr><th>missingDnis</th><td>${missingDnis} (${missingDnis.size()})</td></tr>",
        "<tr><th>orphanDnis:</th><td>${orphanDnis} (${orphanDnis.size()})</td></tr>",
        '</table>'
      ].join()
    )
    missingDnis.each{ dni ->
      Lwarn('_manageChildDevices()', "adding '<b>${dni}'</b>")
      addChildDevice(
        'hubitat', 'Virtual Switch', dni, [isComponent: true, name: dni]
      )}
    orphanDnis.each{ dni ->
      Lwarn('_manageChildDevices()', "deleting orphaned <b>'${dni}'</b>")
      deleteChildDevice(dni)
    }
  } else {
    Lerror(
      '_manageChildDevices()',
      [
        "Called by '${caller}'",
        "Missing <b>${expectedDnis}</b>, taking <b>NO ACTION</b>"
      ].join('<br/>&nbsp;&nbsp;')
    )
  }
}

/*
void _turnOffVswPeers (String vswName) {
  // The child devices of a PBSG should be limited to its managed VSWs.
  List<String> peers = atomicState.vswNames?.findAll{ name -> name != vswName }
  Ltrace('_turnOffVswPeers()', "turning off '${peers}'")
  peers.each{ peerName ->
    DevW peerVsw = app.getChildDevice(_vswNameToDni(peerName))
    if (!peerVsw) {
      Lerror('_turnOffVswPeers()', "peerVsw named '${peerName}' is missing")
    }
    peerVsw.off()
  }
}
*/

DevW getVswByDni (String vswDni) {
  return app.getChildDevice(vswDni)
}

DevW _getVswByName (String vswName) {
  return getVswByDni(_vswNameToDni(vswName))
}

List<DevW> _getVsws (String option = null) {
  List<DevW> vsws = []
  atomicState.vswNames.collect{ _vswNameToDni(it) }.each{ vswDni ->
    DevW vsw = app.getChildDevice(vswDni)
    if (option == 'onOnly' && getSwitchState(vsw) == 'on') {
      vsws += vsw
    } else {
      vsws += vsw
    }
  }
  return vsws
}

void _enforceMutualExclusion () {
  Ltrace('_enforceMutualExclusion()', 'At entry')
  List<DevW> onVsws = _getVsws('onOnly')
  while (onVsws && onVsws.size() > 1) {
    DevW device = onVsws?.first()
    if (device) {
      Ldebug(
        '_enforceMutualExclusion()',
        "With <b>onVsws:</b> ${onVsws} turning off <b>${getDeviceInfo(device)}</b>."
      )
      device.off()
      onVsws = onVsws.drop(1)
    } else {
       Ltrace(
        '_enforceMutualExclusion()',
        "taking no action for onVsws: ${onVsws}"
      )
    }
  }
}

String getDefaultVswDni () {
  return _vswNameToDni(atomicState.vswDefaultName)
}

void _enforceDefaultSwitch () {
  List<DevW> onVsws = _getVsws('onOnly')
  String defaultVswDni = getDefaultVswDni()
  if (defaultVswDni && !onVsws) {
    Ldebug(
      '_enforceDefaultSwitch()',
      "turning on <b>${defaultVswDni}</b>"
    )
    DevW vsw = app.getChildDevice(defaultVswDni)
    vsw.on()
    atomicState.prevOnVswName = atomicState.currOnVswName
    atomicState.currOnVswName = vswName
  } else {
    Ltrace(
      '_enforceDefaultSwitch()',
      "taking no action for <b>onVsws:</b> ${onVsws}<"
    )
  }
}

//----
//---- T E S T I N G   O N L Y
//----

void _addOrphanChild () {
  // T E S T I N G   O N L Y
  //   - This method supports orphan removal testing. See _manageChildDevices().
  addChildDevice(
    'hubitat',         // namespace
    'Virtual Switch',  // typeName
    'bogus_device',
    [isComponent: true, name: 'bogus_device']
  )
}
