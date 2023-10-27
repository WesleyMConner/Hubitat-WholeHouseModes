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
  name: 'libPbsgPrivate',
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'PBSG (headless Pushbutton Switch Group)',
  category: 'general purpose',
  documentationLink: '',
  importUrl: ''
)

void defaultPage () {
  // Include this page content when instantiating PBSG instances.
  // Then call configPbsg(), see below, to complete device configuration.
  section {
    // Forcibly remove unused settings and state, a missing Hubitat feature.
    settings.remove('log')
    state.remove('activeVswDNI')
    state.remove('defaultVswDNI')
    state.remove('inspectScene')
    state.remove('LOG_LEVEL1_ERROR')
    state.remove('LOG_LEVEL2_WARN')
    state.remove('LOG_LEVEL3_INFO')
    state.remove('LOG_LEVEL4_DEBUG')
    state.remove('LOG_LEVEL5_TRACE')
    state.remove('previousVswDNI')
    state.remove('roomScene')
    state.remove('switchDNIs')
    paragraph(
      [
        heading ("${app.getLabel()} a PBSG (Pushbutton Switch Group)<br/>"),
        emphasis(red('Use the browser back button to return to the parent page.'))
      ].join()
    )
    solicitLogThreshold()                                     // Fn provided by Utils
    paragraph debugStateAndSettings('DEBUG')
  }
}

String debugStateAndSettings (String title) {
  return [ heading(title), displayState(), displaySettings() ].join('<br/>')
}

void configPbsg (List<String> vswDNIs, String defaultVswDNI, String logLevel) {
  // Abstract
  //   Set core instance fields immediately after PBSG instantiation.
  Ltrace(
    'configPbsg()',
    [
      "<b>vswDNIs:</b> ${vswDNIs}",
      "<b>defaultVswDNI:</b> ${defaultVswDNI}",
      "<b>logLevel:</b> ${logLevel}"
    ].join('<br/>&nbsp;&nbsp;')
  )
  settings.logThreshold = logLevel               // Passed in, not solicited locally.
  state.vswDNIs = vswDNIs
  state.defaultVswDNI = defaultVswDNI
  manageChildDevices()
}

void turnOffVsw (String vswDNI) {
  DevW vsw = app.getChildDevice(vswDNI)
  if (!vsw) Lerror('turnOffVsw()', "Cannot find provided vswDNI: ${vswDNI}")
  vsw.off()
}

void turnOnVswExclusively (String vswDNI) {
  DevW vsw = app.getChildDevice(vswDNI)
  if (!vsw) Lerror('turnOnVswExclusively()', "Cannot find provided vswDNI: ${vswDNI}")
  vsw.on()
  state.vswDNIs?.findAll{ dni -> dni != callerDNI }.each{ peerDni ->
    DevW peerVsw app.getChildDevice(peerDni)
    if (!peerVsw) Lerror('turnOnVswExclusively()', "Cannot find peerDni: ${peerDni}")
    peerVsw.off()
  }
}


void toggleSwitch (String vswDNI) {
  DevW sw = app.getChildDevice(vswDNI)
  if (!sw) Lerror('toggleSwitch()', "null sw for vswDNI: ${vswDNI}")
  String switchState = getSwitchState(sw)
  switch (switchState) {
    case 'on':
      Ldebug(
        'toggleSwitch()',
        "w/ vswDNI: ${vswDNI} on() -> off()"
      )
      sw.off()
      break;
    case 'off':
      Ldebug(
        'toggleSwitch()',
        "w/ vswDNI: ${vswDNI} off() -> on()"
      )
      sw.on()
      break;
    default:
      Lerror('toggleSwitch()', "unexpected switchState: ${switchState}")
  }
}



void turnOffPeers (String callerDNI) {
}

void addOrphanChild () {
  // TESTING ONLY
  //   - This method supports orphan removal testing. See manageChildDevices().
  addChildDevice(
    'hubitat',         // namespace
    'Virtual Switch',  // typeName
    'bogus_device',
    [isComponent: true, name: 'bogus_device']
  )
}

void manageChildDevices () {
  Ltrace('manageChildDevices()', 'At entry')
  // Uncomment the following to test orphan child app removal.
  //==TESTING-ONLY=> addOrphanChild()
  //=> GET ALL CHILD DEVICES AT ENTRY
  if (!state.vswDNIs) {
    Lerror('manageChildDevices()', 'state.vswDNIs is null')
  } else {
    List<DevW> entryDNIs = getAllChildDevices().collect{ device ->
      device.deviceNetworkId
    }
    List<DevW> missingDNIs = (state.vswDNIs)?.minus(entryDNIs)
    List<DevW> orphanDNIs = entryDNIs.minus(state.vswDNIs)
    //-> USE THE FOLLOWING FOR HEAVY DEBUGGING ONLY
    //-> Ltrace(
    //->   'manageChildDevices()',
    //->   [
    //->     '<table>',
    //->     "<tr><th>entryDNIs</th><td>${entryDNIs}</td></tr>",
    //->     "<tr><th>state.vswDNIs</th><td>${state.vswDNIs}</td></tr>",
    //->     "<tr><th>missingDNIs</th><td>${missingDNIs}</td></tr>",
    //->     "<tr><th>orphanDNIs:</th><td>${orphanDNIs}</td></tr>",
    //->     '</table>'
    //->   ].join()
    //-> )
    missingDNIs.each{ dni ->
      Ldebug('manageChildDevices()', "adding child DNI: '${dni}'")
      addChildDevice(
        'hubitat', 'Virtual Switch', dni, [isComponent: true, name: dni]
      )}
    orphanDNIs.each{ dni ->
      Ldebug('manageChildDevices()', "deleting orphaned child DNI: '${dni}'")
      deleteChildDevice(dni)
    }
  }
}

List<DevW> getOnSwitches () {
  if (!state.vswDNIs) {
    paragraph red(
      'Mutual Exclusion enforcement is pending required data (vswDNIs).'
    )
    return null
  } else {
    return getAllChildDevices()?.findAll{ d ->
      (
        state.vswDNIs.contains(d.deviceNetworkId)
        && getSwitchState(d) == 'on'
      )
    }
  }
}

void enforceMutualExclusion () {
  Ltrace('enforceMutualExclusion()', 'At entry')
  List<DevW> onList = getOnSwitches()
  while (onList && onList.size() > 1) {
    DevW device = onList?.first()
    if (device) {
      Ldebug(
        'enforceMutualExclusion()',
        "With <b>onList:</b> ${onList} turning off <b>${getDeviceInfo(device)}</b>."
      )
      device.off()
      onList = onList.drop(1)
    } else {
       Ltrace('enforceMutualExclusion()', 'taking no action')
    }
  }
}

void enforceDefaultSwitch () {
  List<DevW> onList = getOnSwitches()
  if (state.defaultVswDNI && !onList) {
    Ldebug(
      'enforceDefaultSwitch()',
      "turning on <b>${state.defaultVswDNI}</b>"
    )
    turnOnVsw(state.defaultVswDNI)
  } else {
    Ltrace(
      'enforceDefaultSwitch()',
      "taking no action for <b>onList:</b> ${onList}<"
    )
  }
}

//-> void enforcePbsgConstraints () {
//->   if (!state.vswDNIs) {
//->     paragraph red(
//->       'Mutual Exclusion enforcement is pending required data (vswDNIs).'
//->     )
//->   } else {
//->     // Enforce Mutual-Exclusion is NOT REQUIRED if 'on' events turn off peers
//->     enforceMutualExclusion()
//->     enforceDefaultSwitch()
//->   }
//-> }

String emphasizeOn (String s) {
  return s == 'on' ? red('<b>on</b>') : "<em>${s}</em>"
}

//-> void displaySwitchStates () {
//->   if (!state.vswDNIs) {
//->     paragraph red('Disply of child switch values is pending required data.')
//->   } else {
//->     paragraph(
//->       [
//->         heading('Current Switch States<br/>'),
//->         emphasis(red('Refresh browser (&#x27F3;) for current data<br/>')),
//->         '<table>',
//->         state.vswDNIs.sort().collect{ dni ->
//->           DevW d = app.getChildDevice(dni)
//->           Boolean dflt = d.displayName == state.defaultVswDNI
//->           String label = "${d.displayName}${dflt ? ' (default)' : ''}"
//->           "<tr><th>${label}:</th><td>${emphasizeOn(getSwitchState(d))}</td></tr>"
//->         }.join(''),
//->         '</table>'
//->       ].join()
//->     )
//->   }
//-> }
