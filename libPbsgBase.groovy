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
//---- I N S T A N C E   C O N S T R U C T O R S
//----   - For App instance (singleton) modePBSG.groovy
//----   - For App instances defined in roomPBSG.groovy
//----

InstAppW getOrCreateModePbsg (
    String modePbsgName = 'whaModePbsg',
    String defaultMode = 'Day',
    String logThreshold = 'Debug'
  ) {
  // I M P O R T A N T
  //   - Functionally, a Mode PBSG is a singleton.
  //   - Any real work should occur in modePBSG.groovy (the App definition).
  //   - On creation, a Mode PBSG should create all of its child VSWs.
  //   - If an existing Mode PBSG is available, it is returned "AS IS".
  //   - The Mode PBSG's update() process will refresh child VSWs, but at
  //     some performance cost.
  return modePbsg = getChildAppByLabel(modePbsgName)
    ?:  addChildApp(
          'wesmc',      // See modePBSG.groovy definition's (App) namespace.
          'modePBSG',   // See modePBSG.groovy definition's (App) name.
          modePbsgName  // Label used to create or get the child App.
        )
}

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
      "<b>pbsgName:</b> ${pbsgName}",
      "<b>vswNames:</b> ${vswNames}",
      "<b>defaultVswName:</b> ${defaultVswName}",
      "<b>logLevel:</b> ${logLevel}"
    ].join('<br/>&nbsp;&nbsp;')
  )
  state.pbsgName = pbsgName
  state.vswDniPrefix = "${state.pbsgName}_"
  state.vswNames = vswNames
  populateStateVswDnis()
  state.defaultVswName = defaultVswName
  state.defaultVswDni = _vswNameToDni(defaultVswName)
  settings.logThreshold = logLevel
  _manageChildDevices()
}

void _turnOffVswByName (String vswName) {
  DevW vsw = app.getChildDevice(_vswNameToDni(vswName))
  if (!vsw) Lerror('turnOffVsw()', "vsw named '${vswName}' is missing")
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
      "vswName: '<b>${vswName}</b>' not found"
    )
  }
  vsw.on()
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
    solicitLogThreshold()                                     // Fn provided by Utils
    paragraph _pbsgStateAndSettings('DEBUG')
  }
}

String _getPbsgStateBullets () {
  List<String> result = []
  state.sort().each{ k, v ->
    if (k == 'vswDnis') {
      result += bullet1("<b>${k}</b>")
      v.each{ dni ->
        DevW vsw = app.getChildDevice(dni)
        String state = getSwitchState(vsw)
        //state = (state == 'on') ? "<b>on</b>" : "<i>${state}</i>"
        String vswWithState = "→ ${state} - ${vsw.name}"
        //result += vswWithState
        result += (state == 'on') ? "<b>${vswWithState}</b>" : "<i>${vswWithState}}</i>"
      }
    } else {
      result += bullet1("<b>${k}</b> → ${v}")
    }
  }
  return result.join('<br/>')
}

/*
  List<String> results = []
  results += "<h2><b>${title}</b></h2>"
  results += "<b>${state ? 'STATE' : 'N O   S T A T E'}</b>"
  state.sort().collect{ k, v ->
    if (k == 'vswDnis') {
      if (!v) {
        Lerror('_pbsgStateAndSettings()', 'At key vswDnis, encountered null.')
      }
      v.each{ vswDni ->
        DevW vsw = app.getChildDevice(vswDni)
        String state = getSwitchState(vsw)
        state = (state == 'on') ? "<b>on</b>" : "<i>${state}</i>"
        results += bullet2("${vswDni} (${state})")
      }
    } else {
      results += bullet1("<b>${k}</b> → ${v}")
    }
  }
  results += _getSettingsBulletsAsIs()
  return results.join('<br/>')
}
*/
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
  return state.vswNames.collect{ name -> _vswNameToDni(name) }
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
  //--DEEP-DEBUGGING-> Ldebug('_getVsws', "<br/>${_pbsgStateAndSettings('INSIDE _getVsws()')}")
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
       Ltrace('_enforceMutualExclusion()', 'taking no action')
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
