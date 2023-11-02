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
//---- I N S T A N C E   R E T R I E V A L
//----   - There are two types of PBSG App instances:
//----     - whaPbsg.groovy (one per WHA App instance)
//----     - whaRoomPbsg.groovy (one per whaRoom App instance)
//----   - The App instances are created by, configured by and owned by
//----     WHA App or whaRoom App.
//----   - The following routines facilitate discovering existing PBSGs

//--xx-> InstAppW getModePbsg (String modePbsgName = 'whaPbsg') {
//--xx->   return getChildAppByLabel(modePbsgName)
//--xx->     ?:  addChildApp(
//--xx->           'wesmc',      // See whaPbsg.groovy definition's (App) namespace.
//--xx->           'whaPbsg',   // See whaPbsg.groovy definition's (App) name.
//--xx->           modePbsgName  // Label used to create or get the child App.
//--xx->         )
//--xx-> }

//----
//---- P U B L I C   P B S G   M E T H O D S
//----   These functions are intended for downstream use.
//----

String _vswDnitoName (String modeVswDni) {
  modeVswDni.minus("${state.vswDniPrefix}")
}

void _configPbsg (
    String pbsgName,
    List<String> vswNames,
    String defaultVswName,
    String logLevel
  ) {
  // Set core instance fields immediately after PBSG instantiation.
  //   - The pbsgPrefix is used when naming/labeling applications and devices.
  Ltrace(
    '_configPbsg()',
    [
      '',
      "<b>pbsgName:</b> ${pbsgName}",
      "<b>vswNames:</b> ${vswNames}",
      "<b>defaultVswName:</b> ${defaultVswName}",
      "<b>logLevel:</b> ${logLevel}"
    ].join('<br/>&nbsp;&nbsp;')
  )
  state.vswDniPrefix = "${pbsgName}_"
  state.vswNames = vswNames
  state.defaultVswName = defaultVswName
  state.defaultVswDni = _vswNameToDni(defaultVswName)
  settings.logThreshold = logLevel
  _manageChildDevices()
}

void _turnOffVswByName (String vswName) {
  DevW vsw = app.getChildDevice(_vswNameToDni(vswName))
  if (!vsw) Lerror('turnOffVswByName()', "vsw named '${vswName}' is missing")
  Ltrace('turnOffVswByName()', "turning off vsw named '${vswName}'")
  vsw.off()
  _enforceDefaultSwitch()
}

void _turnOnVswExclusivelyByName (String vswName) {
  // Turn off peers BEFORE turning on vswDni!
  _turnOffVswPeers(vswName)
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
  _removeLegacyPbsgSettingsAndState()
  section {
    paragraph(
      [
        heading2("${app.getLabel()} a PBSG (Pushbutton Switch Group)"),
        emphasis('Use the browser back button to return to the parent page.')
      ].join('<br/>')
    )
    _solicitLogThreshold()                                     // Fn provided by Utils
    paragraph (
      [
        "<h2><b>Debug</b></h2>",
        '<h3><b>STATE</b></h3>',
        _getPbsgStateBullets() ?: bullet('<i>NO DATA AVAILABLE</i>'),
        '<h3><b>SETTINGS</b></h3>',
        _getSettingsBulletsAsIs() ?: bullet('<i>NO DATA AVAILABLE</i>')
      ].join()
    )
  }
}

String _getPbsgStateBullets () {
  List<String> result = []
  state.sort().each{ k, v ->
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
  return "${state.vswDniPrefix}${name}"
}

void _removeLegacyPbsgSettingsAndState () {
  settings.remove('log')
  state.remove('activeVswDni')
  state.remove('defaultVswDni')
  state.remove('inspectScene')
  state.remove('LOG_LEVEL1_ERROR')
  state.remove('LOG_LEVEL2_WARN')
  state.remove('LOG_LEVEL3_INFO')
  state.remove('LOG_LEVEL4_DEBUG')
  state.remove('LOG_LEVEL5_TRACE')
  state.remove('previousVswDni')
  state.remove('roomScene')
  state.remove('switchDnis')
}

List<String> _expectedVswDnis () {
  Ltrace(
    '_expectedVswDnis ()',
    "At entry, <b>state.vswNames:</b> ${state.vswNames}"
  )
  List<String> retVal = state.vswNames.collect{ _vswNameToDni(it) }
  if (!retVal) {
    Lerror('_expectedVswDnis', "Produced '${retVal}'")
  }
  return retVal
}

void _manageChildDevices () {
  Ltrace('_manageChildDevices()', 'At entry')
  // Uncomment the following to test orphan child app removal.
  //==T E S T I N G   O N L Y==> _addOrphanChild()
  // The ONLY child devices for a PBSG are its managed VSWs.
  List<String> expectedDnis = _expectedVswDnis()
  List<String> entryDnis = app.getAllChildDevices().collect{ it.getDeviceNetworkId() }
  // Since removeAll() modifies the collection, copy data before application.
  List<String> missingDnis = expectedDnis
  missingDnis.removeAll(entryDnis)
  List<String> orphanDnis = entryDnis
  orphanDnis.removeAll(expectedDnis)
  //-> USE THE FOLLOWING FOR HEAVY DEBUGGING ONLY
  Ltrace(
    '_manageChildDevices()',
    [
      '<table>',
      "<tr><th>expectedDnis</th><td>${expectedDnis}</td></tr>",
      "<tr><th>entryDnis</th><td>${entryDnis}</td></tr>",
      "<tr><th>missingDnis</th><td>${missingDnis}</td></tr>",
      "<tr><th>orphanDnis:</th><td>${orphanDnis}</td></tr>",
      '</table>'
    ].join()
  )
  missingDnis.each{ dni ->
    Ldebug('_manageChildDevices()', "adding '<b>${dni}'</b>")
    addChildDevice(
      'hubitat', 'Virtual Switch', dni, [isComponent: true, name: dni]
    )}
  orphanDnis.each{ dni ->
    Ldebug('_manageChildDevices()', "deleting orphaned <b>'${dni}'</b>")
    deleteChildDevice(dni)
  }
}

void _turnOffVswPeers (String vswName) {
  // The child devices of a PBSG should be limited to its managed VSWs.
  state.vswNames?.findAll{ name -> name != vswName }.each{ peerName ->
    DevW peerVsw = app.getChildDevice(_vswNameToDni(peerName))
    if (!peerVsw) {
      Lerror('_turnOffVswPeers()', "peerVsw named '${peerName}' is missing")
    }
    Ltrace('_turnOffVswPeers()', "turning off '${peerName}'")
    peerVsw.off()
  }
}

DevW getVswByDni (String vswDni) {
  return app.getChildDevice(vswDni)
}

DevW _getVswByName (String vswName) {
  return getVswByDni(_vswNameToDni(vswName))
}

List<DevW> _getVsws (String option = null) {
  List<DevW> vsws = []
  _expectedVswDnis().collect{ vswDni ->
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

void _enforceDefaultSwitch () {
  List<DevW> onVsws = _getVsws('onOnly')
  if (state.defaultVswDni && !onVsws) {
    Ldebug(
      '_enforceDefaultSwitch()',
      "turning on <b>${state.defaultVswDni}</b>"
    )
    DevW vsw = app.getChildDevice(_vswNameToDni(vswName))
    vsw.on()
    state.prevOnVswName = state.currOnVswName
    state.currOnVswName = vswName
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
