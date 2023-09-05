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

// Include this page content when instantiating PBSG instances.
void defaultPage () {
  section {
    paragraph(
      heading("${app.getLabel()} a PBSG (Pushbutton Switch Group)<br/>")
      + emphasis(red('Use the browser back button to return to the parent page.'))
    )
    manageChildDevices()
    solicitLog()                                  // <- provided by Utils
    paragraph(
      heading('Debug<br/>')
      + "${ displaySettings() }<br/>"
      + "${ displayState() }"
    )
  }
}

// ---------------------------------------------------------------------
// C R E A T E   S T A T I C   S T R U C T U R E   &   U I   R E V I E W
// ---------------------------------------------------------------------

String switchShortNameToDNI (String shortName) {
  return "${app.getLabel()}_${shortName}"
}

String switchDniToShortName (String dni) {
  return dni.minus("${app.getLabel()}_")
}

void configure (
  List<String> switchNames,
  String defaultSwitchName,
  Boolean log
  ) {
  // !!! DO NOT ATTEMPT ANY LOGGING IN THIS METHOD !!!
  // Invoked by a parent application just after instantiating a new PBSG.
  app.updateSetting('log', log)
  state.switchNames = switchNames
  state.switchDNIs = switchNames.collect{ switchName ->
    switchShortNameToDNI(switchName)
  }
  state.defaultSwitchName = defaultSwitchName
  state.defaultSwitchDNI = switchShortNameToDNI(defaultSwitchName)
  manageChildDevices()
  enforcePbsgConstraints()
}

void turnOnSwitch (String shortName) {
  if (settings.log) log.trace(
    "PBSG-LIB turnOnSwitch() w/ shortName: ${shortName}"
  )
  DevW sw = app.getChildDevice(switchShortNameToDNI(shortName))
  if (sw) {
    sw.on()
  }
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
    if (settings.log) log.trace(
      'PBSG-LIB manageChildDevices()<table>'
      + "<tr><th>entryDNIs</th><td>${entryDNIs}</td></tr>"
      + "<tr><th>state.switchDNIs</th><td>${state.switchDNIs}</td></tr>"
      + "<tr><th>missingDNIs</th><td>${missingDNIs}</td></tr>"
      + "<tr><th>orphanDNIs:</th><td>${orphanDNIs}</td></tr>"
      + '</table>'
    )
    missingDNIs.each{ dni ->
      if (settings.log) log.trace(
        "PBSG-LIB manageChildDevices() adding child for DNI: '${dni}'"
      )
      addChildDevice(
        'hubitat', 'Virtual Switch', dni, [isComponent: true, name: dni]
      )}
    orphanDNIs.each{ dni -> deleteChildDevice(dni) }
    initialize()
  }
}

String getSwitchState(DevW d) {
  List<String> stateValues = d.collect({ it.currentStates.value }).flatten()
  //-> if (settings.log) log.trace(
  //->   "PBSG-LIB getSwitchState() w/ stateValues: ${stateValues} "
  //->   + "for ${d.displayName}"
  //-> )
  return stateValues.contains('on')
      ? 'on'
      : stateValues.contains('off')
        ? 'off'
        : 'unknown'
}

List<DevW> getOnSwitches() {
  if (!state.switchDNIs) {
    paragraph red(
      'Mutual Exclusion enforcement is pending required data (switchDNIs).'
    )
    return null
  } else {
    return getAllChildDevices().findAll{ d ->
      (
        state.switchDNIs.contains(d.deviceNetworkId)
        && getSwitchState(d) == 'on'
      )
    }
  }
}

void enforceMutualExclusion() {
  List<DevW> onList = getOnSwitches()
  while (onList && onList.size() > 1) {
    DevW device = onList?.first()
    if (settings.log) log.trace(
      'PBSG-LIB enforceMutualExclusion(), <br/>'
      + "<b>onList:</b> ${onList}, "
      + " turning off <b>${deviceTag(device)}</b>."
    )
    device.off()
    onList = onList.drop(1)
  }
}

void enforceDefaultSwitch() {
  // Enforce Default Switch
  List<DevW> onList = getOnSwitches()
log.trace "#175 >>>>> state.defaultSwitchName: ${state.defaultSwitchName}, onList: ${onList} <<<<<"
  if (state.defaultSwitchName && !onList) {
    if (settings.log) log.trace(
      'PBSG-LIB enforceDefaultSwitch() turning on , '
      + "<b>state.defaultSwitchName:</b> ${state.defaultSwitchName}"
    )
    app.getChildDevice(state.defaultSwitchDNI).on()
  } else {
    if (settings.log) log.trace 'PBSG-LIB enforceDefaultSwitch() taking no action.'
  }
}

void enforcePbsgConstraints() {
  if (!state.switchDNIs) {
    paragraph red(
      'Mutual Exclusion enforcement is pending required data (switchDNIs).'
    )
  } else {
    // Enforce Mutual-Exclusion (NOT REQUIRED if 'on' events turn 'off' peers)
    enforceMutualExclusion()
    enforceDefaultSwitch()
  }
}

String emphasizeOn(String s) {
  return s == 'on' ? red('<b>on</b>') : "<em>${s}</em>"
}

void displaySwitchStates () {
  if (!state.switchDNIs) {
    paragraph red('Disply of child switch values is pending required data.')
  } else {
    paragraph(
      heading('Current Switch States<br/>')
      + emphasis(red('Refresh browser (&#x27F3;) for current data<br/>'))
      + '<table>'
      + state.switchDNIs.sort().collect{ dni ->
        DevW d = app.getChildDevice(dni)
        Boolean dflt = d.displayName == state.defaultSwitchDNI
        String label = "${d.displayName}${dflt ? ' (default)' : ''}"
        "<tr><th>${label}:</th><td>${emphasizeOn(getSwitchState(d))}</td></tr>"
      }.join('')
      + '</table>'
    )
  }
}

// --------------------------------------------------
// I M P L E M E N T   D Y N A M IC   B E H A V I O R
// --------------------------------------------------

void turnOffPeers (String callerDNI) {
  state.switchDNIs.findAll{ dni -> dni != callerDNI }.each{ dni ->
    app.getChildDevice(dni).off()
  }
}

void pbsgEventHandler (Event e) {
  if (e.isStateChange) {
    if (e.value == 'on') {
      if (settings.log) log.trace(
        "PBSG-LIB pbsgEventHandler() ${e.descriptionText}, turning off peers ..."
      )
      turnOffPeers(e.displayName)
      parent.pbsgVswTurnedOn(switchDniToShortName(e.displayName))
    } else if (e.value == 'off') {
      if (settings.log) log.trace(
        "PBSG-LIB pbsgEventHandler(), ${e.descriptionText}"
      )
      enforceDefaultSwitch()
    } else {
      if (settings.log) log.error(
        "PBSG-LIB pbsgEventHandler(), unexpected value = >${e.value}<"
      )
    }
  } else {
    //-> if (settings.log) log.trace(
    //->   "PBSG-LIB pbsgEventHandler() IGNORING ${e.descriptionText}<br/>"
    //->   + logEventDetails(e)
    //-> )
  }
}

void initialize() {
  app.getAllChildDevices().each{ device ->
    if (settings.log) log.trace(
      "PBSG-LIB initialize() subscribing ${deviceTag(device)}..."
    )
    subscribe(device, "switch", pbsgEventHandler, ['filterEvents': false])
  }
}

void installed() {
  if (settings.log) log.trace 'PBSG-LIB installed()'
  initialize()
}

void updated() {
  if (settings.log) log.trace 'PBSG-LIB updated()'
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled() {
  if (settings.log) log.trace 'PBSG-LIB uninstalled(), DELETING CHILD DEVICES'
  getAllChildDevices().collect{ device ->
    deleteChildDevice(device.deviceNetworkId)
  }
}
