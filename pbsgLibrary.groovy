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
import com.hubitat.app.ChildDeviceWrapper as ChildDevW
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.DeviceWrapperList as DevWL
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

void configure (
  List<String> switchNames,
  String defaultSwitch,
  Boolean log
  ) {
  app.updateSetting('LOG', log)
  state.SwitchNames = switchNames
  state.DefaultSwitch = defaultSwitch
}

void manageChildDevices () {
  // To test orphan device removal, uncomment the following:
  //--test-> addChildDevice(
  //--test->   'hubitat',         // namespace
  //--test->   'Virtual Switch',  // typeName
  //--test->   'bogus_device',
  //--test->   [isComponent: true, name: 'bogus_device']
  //--test-> )
  // Note: Device creation "errors out" if deviceNetworkId is duplicated.
  //=> GET ALL CHILD DEVICES AT ENTRY
  if (!state.SwitchNames) {
    paragraph red(
      'manageChildDevices() is pending required state data (switchNames).'
    )
  } else {
    List<ChildDevW> childDevices = getAllChildDevices()
    if (settings.LOG) log.trace(
      'manageChildDevices() at entry <b>devices:</b> '
      + childDevices?.collect{ d -> deviceTag(d) }.join(', ')
      + "<b>state.SwitchNames:</b> ${state.SwitchNames}"
    )
    //=> ENSURE TARGET CHILD DEVICES EXIST
    LinkedHashMap<String, ChildDevW> switchNameToVsw = state.SwitchNames
      .collectEntries{ swName ->
        String deviceNetworkId = "${app.getLabel()}_${swName}"
        //-> if (settings.LOG) log.trace "#70 eexpectedDeviceNetworkId >${deviceNetworkId}<"
        ChildDevW vsw = childDevices.find{ d -> d.deviceNetworkId == deviceNetworkId }
          ?: addChildDevice(
            'hubitat',         // namespace
            'Virtual Switch',  // typeName
            deviceNetworkId,
            [isComponent: true, name: deviceNetworkId]
          )
        [swName, vsw]
      }
    state.switchNameToVsw = switchNameToVsw
    if (settings.LOG) log.trace(
      "manageChildDevices() switchNameToVsw: ${switchNameToVsw}."
    )
    //=> DELETE ORPHANED DEVICES
    List<String> currentChildren = switchNameToVsw.collect{ switchName, vsw ->
      vsw.deviceNetworkId
    }
    //-> log.trace "#88 currentChildren = >${currentChildren}<"
    List<String> orphanedDevices = childDevices.collect{ d -> d.deviceNetworkId }
                                  .minus(currentChildren)
    orphanedDevices.each{ deviceNetworkId ->
      if (settings.LOG) log.trace(
        "manageChildDevices() dropping orphaned device `${deviceNetworkId}`."
      )
      assert deviceNetworkId instanceof String
      deleteChildDevice(deviceNetworkId)
    }
  }
}

String extractSwitchState(DevW d) {
  List<String> stateValues = d.collect({ it.currentStates.value }).flatten()
  if (settings.LOG) log.trace(
    "extractSwitchState() w/ stateValues: ${stateValues} for ${d.displayName}"
  )
  return stateValues.contains('on')
      ? 'on'
      : stateValues.contains('off')
        ? 'off'
        : 'unknown'
}

List<DevW> getOnSwitches() {
  if (!state.switchNameToVsw) {
    paragraph red(
      'Mutual Exclusion enforcement is pending required data (switchNameToVsw).'
    )
  } else {
    LinkedHashMap<String, ChildDevW> onList = state.switchNameToVsw.findAll{
       switchName, vsw ->
        // log.trace "#119 switchName: >${switchName}< vsw: >${vsw.displayName}<"
        extractSwitchState(vsw) == 'on'
    }
    onList.collect{ switchName, vsw -> vsw }
  }
}

void enforcePbsgConstraints() {
  if (!state.switchNameToVsw) {
    paragraph red(
      'Mutual Exclusion enforcement is pending required data (switchNameToVsw).'
    )
  } else {
    // Enforce Mutual-Exclusion (not required if 'on' events turn 'off' peers.
    List<DevW> onList = getOnSwitches()
    while (onlist && onList.size() > 1) {
      DevW device = onList?.first()
      if (settings.LOG) log.trace "enforcePbsgConstraints() turning off ${deviceTag(device)}."
      device.off()
      onList = onList.drop(1)
    }
    // Enforce Default Switch
    log.trace "#140 state.DefaultSwitch: ${state.DefaultSwitch}, onList: ${onList}"
    log.trace "#141 state.switchNameToVsw.keySet(): ${state.switchNameToVsw.keySet()}"
    if (state.DefaultSwitch && !onList) {
      ChildDevW dfltSwitch = state.switchNameToVsw[state.DefaultSwitch]
      log.trace "#144 dfltSwitch: ${dfltSwitch}"
      dfltSwitch.on()
    }
  }
}



void displaySwitchStates () {
  if (!state.switchNameToVsw) {
    paragraph red('Disply of child switch values is pending required data.')
  } else {
    paragraph(
      heading('Current Switch States<br/>')
      + '<table>'
      + state.switchNameToVsw.sort().collect{ switchName, vsw ->
        ChildDevW d = app.getChildDevice(vsw.deviceNetworkId)
        Boolean dflt = switchName == state.DefaultSwitch
        "<tr><th>${switchName}${dflt ? ' (default)' : ''}:</th><td>${extractSwitchState(d)}</td></tr>"
      }.join('')
      + '</table>'
    )
  }
}

void defaultPage () {
  section {
    paragraph(
      heading("${app.getLabel()} a PBSG (Pushbutton Switch Group) - ${app.getInstallationState()}<br/>")
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







// -----------------------------------------
// M A N A G E D   C H I L D   D E V I C E S
// -----------------------------------------




void pbsgVswEventHandler (event) {
  // ----------------------------------------------------------------------
  // DO I NEED TO REFRESH THE DEVICES IN PBSG TO GET ACCURATE SWITCH DATA?
  // PRESUMABLY, EVERYTHING WOULD BE ACCURATE DUE TO PRIOR EVENT HANDLING.
  // ----------------------------------------------------------------------
  // event.displayName
  if (settings.LOG) log.trace "pbsgVswEventHandler() w/ parent App: '${event.deviceId.getParentAppId()}'."
  pbsg = state[pbsgName]
  if (event.isStateChange) {
    switch(event.value) {
      case 'on':
        if (settings.LOG) log.trace "pbsgVswEventHandler() ${event.displayName}"
          + 'turned "ON". Turning off switch group peers.'
        pbsg.scene2Vsw.each{ scene, vsw ->
          // No harm in turning off a VSW that might already be off.
          if (vsw.deviceNetworkId != event.displayName) vsw.off()
        }
        break
      case 'off':
        //-- PENDING -> enforceDefault()
        break
      default:
        log.error  'pbsgVswEventHandler() expected 'on' or 'off'; but, '
          + "received '${event.value}'."
        app.updateLabel("${pbsg.enclosingApp} - BROKEN")
    }
  } else {
    log.error 'pbsgVswEventHandler() received an unexpected event:<br/>'
      + logEventDetails(event, false)
  }
}



// -------------------------------
// S T A T E   M A N A G E M E N T
// -------------------------------

/*
void buttonHandler (Event e) {
  if (e.isStateChange) {
    logSettingsAndState('buttonHandler()')
    DevW eventDevice = getSwitchById(e.deviceId.toString())
    switch(e.value) {
      case 'on':
        // Turn off peers in switch group.
        getOnSwitches().each({ sw ->
          if (sw.id != eventDevice.id) {
            if (settings.LOG) log.trace "buttonHandler() turning off ${deviceTag(sw)}."
            sw.off()
          }
        })
        break
      case 'off':
        enforceDefault()
        break
      default:
        log.error  "buttonHandler() expected 'on' or 'off'; but, \
          received '${e.value}'."
    }
  } else {
    // Report this condition as an ERROR and explore further IF it occurs.
    logSettingsAndState('buttonHandler()', true)
  }
}
*/

void initialize() {
  if (settings.LOG) log.trace 'initialize()'
  //-> enforcePbsgConstraints()
  //-- PENDING -> enforceDefault()
  subscribe(settings.swGroup, "switch", pbsgVswEventHandler)
}

void installed() {
  if (settings.LOG) log.trace 'WHA installed()'
  initialize()
}

void updated() {
  if (settings.LOG) log.trace 'WHA updated()'
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled() {
  if (settings.LOG) log.trace 'uninstalled()'
  // Nothing to do. Subscruptions are automatically dropped.
  // This may matter if devices are captured by a switch group in the future.
}

/*
String emphasizeOn(String s) {
  return s == 'on' ? '<b>on</b>' : "<em>${s}</em>"
}
*/