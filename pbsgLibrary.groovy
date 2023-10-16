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

// THIS LIBRARY DEPENDS ON 'UtilsLibrary.groovy' WHICH MUST BE INCLUDED
// BY THE APPLICATIONS THAT INSTANTIATE A PBSG INSTANCE.
//   - See 'modePBSG.groovy' and 'roomPBSG.groovy'.
//   - Example depedendency: String getSwitchState (DevW d)

library (
  name: 'pbsgLibrary',
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'PBSG (headless Pushbutton Switch Group)',
  category: 'general purpose',
  documentationLink: '',
  importUrl: ''
)

// Include this page content when instantiating PBSG instances, then call
// configure () - see below - to complete device configuration.
void defaultPage () {
  section {
    paragraph(
      [
        heading ("${app.getLabel()} a PBSG (Pushbutton Switch Group)<br/>"),
        emphasis(red('Use the browser back button to return to the parent page.'))
      ].join()
    )
    manageChildDevices()
    configureLogging()                                  // <- provided by Utils
    paragraph(
      [
        heading('Debug<br/>'),
        "${ displaySettings() }<br/>",
        "${ displayState() }"
      ].join()
    )
  }
}

String switchShortNameToDNI (String shortName) {
  return "${app.getLabel()}_${shortName}"
}

String switchDniToShortName (String dni) {
  return dni.minus("${app.getLabel()}_")
}

void configure (
  List<String> switchNames,
  String defaultSwitchName,
  String logLevel
  ) {
  // !!! DO NOT ATTEMPT ANY LOGGING IN THIS METHOD !!!
  // Invoked by a parent application just after instantiating a new PBSG.
  // See displayInstantiatedPbsgHref() in UtilsLibrary.groovy
  // - pbsgApp.configure(switchNames, defaultSwitchName, settings.log)
  settings.remove('log')
  settings.logThreshold = logLevel
  state.switchNames = switchNames
  state.switchDNIs = switchNames.collect{ switchName ->
    switchShortNameToDNI(switchName)
  }
  state.defaultSwitchName = defaultSwitchName
  state.defaultSwitchDNI = switchShortNameToDNI(defaultSwitchName)
  manageChildDevices()
  enforcePbsgConstraints()
}

void toggleSwitch (String shortName) {
  DevW sw = app.getChildDevice(switchShortNameToDNI(shortName))
  String switchState = getSwitchState(sw)
  switch (switchState) {
    case 'on':
      L(
        'DEBUG',
        "PBSG-LIB toggleSwitch() w/ shortName: ${shortName} on() -> off()"
      )
      sw.off()
      break;
    case 'off':
      L(
        'DEBUG',
        "PBSG-LIB toggleSwitch() w/ shortName: ${shortName} off() -> on()"
      )
      sw.on()
      break;
  }
}

void turnOnSwitch (String shortName) {
  L(
    'DEBUG',
    "PBSG-LIB turnOnSwitch() w/ shortName: ${shortName}"
  )
  DevW sw = app.getChildDevice(switchShortNameToDNI(shortName))
  if (sw) {
    sw.on()
  }
}

void addOrphanChild () {
  // This method supports orphan removal testing. See manageChildDevices().
  addChildDevice(
    'hubitat',         // namespace
    'Virtual Switch',  // typeName
    'bogus_device',
    [isComponent: true, name: 'bogus_device']
  )
}

void manageChildDevices () {
  // Uncomment the following to test orphan child app removal.
  //-> addOrphanChild()
  //=> GET ALL CHILD DEVICES AT ENTRY
  if (!state.switchNames) {
    paragraph red(
      'manageChildDevices() is pending required state data (switchNames).'
    )
  } else {
    List<DevW> entryDNIs = getAllChildDevices().collect{ device ->
      device.deviceNetworkId
    }
    List<DevW> missingDNIs = (state.switchDNIs)?.minus(entryDNIs)
    List<DevW> orphanDNIs = entryDNIs.minus(state.switchDNIs)
    L('TRACE',
      [
        'PBSG-LIB manageChildDevices()<table>',
        "<tr><th>entryDNIs</th><td>${entryDNIs}</td></tr>",
        "<tr><th>state.switchDNIs</th><td>${state.switchDNIs}</td></tr>",
        "<tr><th>missingDNIs</th><td>${missingDNIs}</td></tr>",
        "<tr><th>orphanDNIs:</th><td>${orphanDNIs}</td></tr>",
        '</table>'
      ].join()
    )
    missingDNIs.each{ dni ->
      L(
        'DEBUG',
        "PBSG-LIB manageChildDevices() adding child for DNI: '${dni}'"
      )
      addChildDevice(
        'hubitat', 'Virtual Switch', dni, [isComponent: true, name: dni]
      )}
    orphanDNIs.each{ dni -> deleteChildDevice(dni) }
    initialize()
  }
}

List<DevW> getOnSwitches () {
  if (!state.switchDNIs) {
    paragraph red(
      'Mutual Exclusion enforcement is pending required data (switchDNIs).'
    )
    return null
  } else {
    return getAllChildDevices()?.findAll{ d ->
      (
        state.switchDNIs.contains(d.deviceNetworkId)
        && getSwitchState(d) == 'on'
      )
    }
  }
}

void enforceMutualExclusion () {
  List<DevW> onList = getOnSwitches()
  while (onList && onList.size() > 1) {
    DevW device = onList?.first()
    if (device) {
      L(
        'DEBUG',
        [
          'PBSG-LIB enforceMutualExclusion(), <br/>',
          "<b>onList:</b> ${onList}, ",
          " turning off <b>${getDeviceInfo(device)}</b>."
        ].join()
      )
      device.off()
      onList = onList.drop(1)
    } else {
      L(
        'TRACE',
        "PBSG-LIB enforceMutualExclusion() taking no action.<br/>onList: >${onList}<"
      )
    }
  }
}

void enforceDefaultSwitch () {
  List<DevW> onList = getOnSwitches()
  if (state.defaultSwitchName && !onList) {
    L(
      'DEBUG',
      "PBSG-LIB enforceDefaultSwitch() turning on <b>${state.defaultSwitchName}</b>"
    )
    app.getChildDevice(state.defaultSwitchDNI).on()
  } else {
    L(
      'TRACE',
      "PBSG-LIB enforceDefaultSwitch() taking no action.<br/>onList: >${onList}<"
    )
  }
}

void enforcePbsgConstraints () {
  if (!state.switchDNIs) {
    paragraph red(
      'Mutual Exclusion enforcement is pending required data (switchDNIs).'
    )
  } else {
    // Enforce Mutual-Exclusion is NOT REQUIRED if 'on' events turn off peers
    enforceMutualExclusion()
    enforceDefaultSwitch()
  }
}

String emphasizeOn (String s) {
  return s == 'on' ? red('<b>on</b>') : "<em>${s}</em>"
}

void displaySwitchStates () {
  if (!state.switchDNIs) {
    paragraph red('Disply of child switch values is pending required data.')
  } else {
    paragraph(
      [
        heading('Current Switch States<br/>'),
        emphasis(red('Refresh browser (&#x27F3;) for current data<br/>')),
        '<table>',
        state.switchDNIs.sort().collect{ dni ->
          DevW d = app.getChildDevice(dni)
          Boolean dflt = d.displayName == state.defaultSwitchDNI
          String label = "${d.displayName}${dflt ? ' (default)' : ''}"
          "<tr><th>${label}:</th><td>${emphasizeOn(getSwitchState(d))}</td></tr>"
        }.join(''),
        '</table>'
      ].join()
    )
  }
}

void turnOffPeers (String callerDNI) {
  state.switchDNIs?.findAll{ dni -> dni != callerDNI }.each{ dni ->
    app.getChildDevice(dni).off()
  }
}

void pbsgEventHandler (Event e) {
  if (e.isStateChange) {
    if (e.value == 'on') {
      turnOffPeers(e.displayName)
      parent.pbsgVswTurnedOnCallback(switchDniToShortName(e.displayName))
    } else if (e.value == 'off') {
      enforceDefaultSwitch()
    } else {
      L(
        'WARN',
        "PBSG-LIB pbsgEventHandler(), unexpected event value = >${e.value}<"
      )
    }
  }
}

void initialize () {
  app.getAllChildDevices().each{ device ->
    L(
      'TRACE',
      "PBSG-LIB initialize() subscribing ${getDeviceInfo(device)}..."
    )
    subscribe(device, "switch", pbsgEventHandler, ['filterEvents': false])
  }
}

void installed () {
  L('TRACE', 'PBSG-LIB installed()')
  initialize()
}

void updated () {
  L('TRACE', 'PBSG-LIB updated()')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled () {
  L('TRACE', 'PBSG-LIB uninstalled(), DELETING CHILD DEVICES')
  getAllChildDevices().collect{ device ->
    deleteChildDevice(device.deviceNetworkId)
  }
}
