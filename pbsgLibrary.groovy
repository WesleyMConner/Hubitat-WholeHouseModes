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
    settings.remove('log')  // No Longer Used, but periodically still seen.
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
        "${ displayState() }<br/>",
        "${ displaySettings() }"
      ].join()
    )
  }
}

void configure (
  List<String> switchDNIs,
  String defaultSwitchDNI,
  String logLevel
  ) {
  // !!! DO NOT ATTEMPT ANY LOGGING IN THIS METHOD !!!
  // Invoked by a parent application just after instantiating a new PBSG.
  // See displayInstantiatedPbsgHref() in UtilsLibrary.groovy
  // - pbsgApp.configure(sceneNames, defaultSwitchName, settings.log)
  //-----------------------------------------
  // REMOVE NO LONGER USED SETTINGS AND STATE
  //-----------------------------------------
  settings.remove('log')
  state.remove('defaultSwitchName')
  state.remove('switchNames')
  //-----------------------------------------
  settings.logThreshold = logLevel
  state.switchDNIs = switchDNIs
  state.defaultSwitchDNI = defaultSwitchDNI
  manageChildDevices()
  enforcePbsgConstraints()
}

void toggleSwitch (String switchDNI) {
  DevW sw = app.getChildDevice(switchDNI)
  String switchState = getSwitchState(sw)
  switch (switchState) {
    case 'on':
      L(
        'DEBUG',
        "PBSG-LIB toggleSwitch() w/ switchDNI: ${switchDNI} on() -> off()"
      )
      sw.off()
      break;
    case 'off':
      L(
        'DEBUG',
        "PBSG-LIB toggleSwitch() w/ switchDNI: ${switchDNI} off() -> on()"
      )
      sw.on()
      break;
  }
}

void turnOnSwitch (String switchDNI) {
  L(
    'DEBUG',
    "PBSG-LIB turnOnSwitch() w/ switchDNI: ${switchDNI}"
  )
  DevW sw = app.getChildDevice(switchDNI)
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
  if (!state.switchDNIs) {
    paragraph red(
      'manageChildDevices() is pending required state data (switchDNIs).'
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
  if (state.defaultSwitchDNI && !onList) {
    L(
      'DEBUG',
      "PBSG-LIB enforceDefaultSwitch() turning on <b>${state.defaultSwitchDNI}</b>"
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
  // e.displayName - The DNI of the reporting switch.
  //       e.value - 'on' or 'off' is expected
  // THIS HANDLER MONITORS VALUE CHANGES FOR ALL PBSG SWITCHES.
  // - It is the final arbiter of the state.currActiveSwitch.
  // - The state.prevActiveSwitch is preserved for reactivation
  //   when MANUAL_OVERRIDE turns off.
  if (e.isStateChange) {
    if (e.value == 'on') {
      turnOffPeers(e.displayName)
      state.prevActiveSwitch = state.currActiveSwitch ?: state.defaultSwitchDNI
      state.currActiveSwitch = e.displayName
      L(
        'DEBUG',
        //-> [
        //->   "PBSG-LIB pbsgEventHandler() e.displayName: ${e.displayName}",
        "PBSG-LIB pbsgEventHandler() ${state.prevActiveSwitch} -> ${state.currActiveSwitch}"
        //-> ].join()
      )
      parent.pbsgVswTurnedOnCallback(state.currActiveSwitch)
    } else if (e.value == 'off') {
      if (e.displayName.contains('MANUAL_OVERRIDE')) {
        // Special behavior for MANUAL ENTRY: Restore previously switch.
        //-> state.currActiveSwitch = state.prevActiveSwitch ?: state.defaultSwitchDNI
        turnOnSwitch(state.prevActiveSwitch)
        //-> L(
        //->   'DEBUG',
        //->   "PBSG-LIB pbsgEventHandler() MANUAL_OVERRIDE -> ${state.currActiveSwitch}"
        //-> )
      } else {
        enforceDefaultSwitch()
        //-> state.prevActiveSwitch = state.currActiveSwitch ?: state.defaultSwitchDNI
        //-> state.currActiveSwitch = onSwitch
        //-> L(
        //->   'DEBUG',
        //->   "PBSG-LIB pbsgEventHandler() ${state.prevActiveSwitch} -> ${state.currActiveSwitch}"
        //-> )
      }
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
      'DEBUG',
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
