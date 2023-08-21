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
  name: 'pbsgLibrary',
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'PBSG (headless Pushbutton Switch Group)',
  category: 'general purpose',
  documentationLink: '',
  importUrl: ''
)

// ---------------------------------------------------------------------
// C R E A T E   S T A T I C   S T R U C T U R E   &   U I   R E V I E W
// ---------------------------------------------------------------------

String switchNameToDeviceNetworkId (String switchName) {
  return "${app.getLabel()}_${switchName}"
}

String deviceNetworkIdToSwitchName (String dni) {
  log.trace "deviceNetworkIdToSwitchName() ... >${dni}<"
  return dni.minus("${app.getLabel()}_")
}

void configure (
  List<String> switchNames,
  String defaultSwitch,
  Boolean log
  ) {
  // Design Note
  //   Logging is not advised in this function, which is invoked by a
  //   parent application just after instantiating a new PBSG instance.
  //   The parent should provide pbsgSwitchActivated(String SwitchName).
  app.updateSetting('LOG', log)
  state.SwitchNames = switchNames
  state.SwitchDNIs = switchNames.collect{ switchName ->
    switchNameToDeviceNetworkId(switchName)
  }
  state.DefaultSwitchName = defaultSwitch
  state.DefaultSwitchDNI = switchNameToDeviceNetworkId(defaultSwitch)
}

void addOrphanChild() {
  // See manageChildDevices(). This method supports orphan removal testing.
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
  if (!state.SwitchNames) {
    paragraph red(
      'manageChildDevices() is pending required state data (switchNames).'
    )
  } else {
    List<DevW> entryDNIs = getAllChildDevices().collect{ device ->
      device.deviceNetworkId
    }
    List<DevW> missingDNIs = (state.SwitchDNIs)?.minus(entryDNIs)
    List<DevW> orphanDNIs = entryDNIs.minus(state.SwitchDNIs)
    if (settings.LOG) log.trace(
      'manageChildDevices()<table>'
      + "<tr><th>state.SwitchDNIs</th><td>${state.SwitchDNIs}</td></tr>"
      + "<tr><th>entryDNIs</th><td>${entryDNIs}</td></tr>"
      + "<tr><th>missingDNIs</th><td>${missingDNIs}</td></tr>"
      + "<tr><th>orphanDNIs:</th><td>${orphanDNIs}</td></tr>"
      + '</table>'
    )
    missingDNIs.each{ dni -> addChildDevice(
      'hubitat', 'Virtual Switch', dni, [isComponent: true, name: dni]
    )}
    orphanDNIs.each{ dni -> deleteChildDevice(dni) }
  }
}

String getSwitchState(DevW d) {
  List<String> stateValues = d.collect({ it.currentStates.value }).flatten()
  //-> if (settings.LOG) log.trace(
  //->   "getSwitchState() w/ stateValues: ${stateValues} for ${d.displayName}"
  //-> )
  return stateValues.contains('on')
      ? 'on'
      : stateValues.contains('off')
        ? 'off'
        : 'unknown'
}

List<DevW> getOnSwitches() {
  if (!state.SwitchDNIs) {
    paragraph red(
      'Mutual Exclusion enforcement is pending required data (switchDNIs).'
    )
    return null
  } else {
    return getAllChildDevices().findAll{ d ->
      (
        state.SwitchDNIs.contains(d.deviceNetworkId)
        && getSwitchState(d) == 'on'
      )
    }
  }
}

void enforcePbsgConstraints() {
  if (!state.SwitchDNIs) {
    paragraph red(
      'Mutual Exclusion enforcement is pending required data (switchDNIs).'
    )
  } else {
    // Enforce Mutual-Exclusion (NOT REQUIRED if 'on' events turn 'off' peers)
    List<DevW> onList = getOnSwitches()
    while (onlist && onList.size() > 1) {
      DevW device = onList?.first()
      if (settings.LOG) log.trace(
        "enforcePbsgConstraints() turning off ${deviceTag(device)}."
      )
      device.off()
      onList = onList.drop(1)
    }
    // Enforce Default Switch
    if (state.DefaultSwitchName && !onList) {
      getChildDevice(state.DefaultSwitchDNI).on()
    }
  }
}

String emphasizeOn(String s) {
  return s == 'on' ? red('<b>on</b>') : "<em>${s}</em>"
}

void displaySwitchStates () {
  if (!state.SwitchDNIs) {
    paragraph red('Disply of child switch values is pending required data.')
  } else {
    paragraph(
      heading('Current Switch States<br/>')
      + emphasis('Refresh browser (&#x27F3;) for current data<br/>')
      + '<table>'
      + state.SwitchDNIs.sort().collect{ dni ->
        DevW d = app.getChildDevice(dni)
        Boolean dflt = d.displayName == state.DefaultSwitchDNI
        String label = "${d.displayName}${dflt ? ' (default)' : ''}"
        "<tr><th>${label}:</th><td>${emphasizeOn(getSwitchState(d))}</td></tr>"
      }.join('')
      + '</table>'
    )
  }
}

void defaultPage () {
  section {
    paragraph(
      heading("${app.getLabel()} a PBSG (Pushbutton Switch Group)<br/>")
      + bullet('Push <b>Done</b> to return to enable event subcriptions and return to parent.')
    )
    manageChildDevices()
    enforcePbsgConstraints()
    displaySwitchStates()
    paragraph(
      heading('Debug<br/>')
      + "${ displaySettings() }<br/>"
      + "${ displayState() }"
    )
  }
}

// --------------------------------------------------
// I M P L E M E N T   D Y N A M IC   B E H A V I O R
// --------------------------------------------------

void turnOffPeers (String callerDNI) {
  state.SwitchDNIs.findAll{ dni -> dni != callerDNI }.each{ dni ->
    getChildDevice(dni).off()
  }
}

void pbsgEventHandler (Event e) {
  if (settings.LOG) log.trace "pbsgEventHandler() w/ ${e.descriptionText}"
  if (e.value == 'on') {
    turnOffPeers(e.displayName)
    parent.pbsgSwitchActivated(deviceNetworkIdToSwitchName(e.displayName))
  }
}

void initialize() {
  app.getAllChildDevices().each{ device ->
    if (settings.LOG) log.trace "PBSG initialize() subscribing ${deviceTag(device)}..."
    subscribe(device, "switch", pbsgEventHandler, ['filterEvents': false])
  }
}

void installed() {
  if (settings.LOG) log.trace 'PBSG installed()'
  initialize()
}

void updated() {
  if (settings.LOG) log.trace 'PBSG updated()'
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled() {
  if (settings.LOG) log.trace 'PBSG uninstalled(), DELETING CHILD DEVICES'
  getAllChildDevices().collect{ device ->
    deleteChildDevice(device.deviceNetworkId)
  }
}
