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

library (
  name: 'libPbsgVsw',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'The (isolated) VSW component of PbsgCore',
  category: 'general purpose'
)

//---- VSW METHODS
//----   - Isolate PbsgVsw data and methods from PbsgCore data and methods.
//----   - Provide VSW functionality to a PBSG instance.
//----   - Minimize interaction between non-VSW and VSW methods, settings and state.

void _vswConfigure (List<String> buttons) {
  // Ensure a 1-to-1 correspondence between PBSG buttons and VSW instances.
  // Any new VSWs are turned off on creation.
  // Full reconciliation of VSW on/off state occurs at pbsgActivateButton().
  app.unsubscribe()
  state.vswDniPrefix = "${app.getLabel()}_"
  List<String> expectedDnis = buttons.collect{ _pbsgButtonNameToVswDni(it) }
  List<DevW> foundDevices = app.getAllChildDevices()
  foundDevices.each{ d ->
    String dni = d.getDeviceNetworkId()
    if (d.hasCapability('Switch')) {
      if (expectedDnis.contains(dni)) {
        // Nothing to do, keep this device
      } else {
        Lwarn('_vswConfigure()', "Deleting orphaned device ${b(dni)}")
        app.deleteChildDevice(dni)
      }
    } else {
      Lwarn('_vswConfigure()', "Deleting non-switch ${b(dni)}")
      app.deleteChildDevice(dni)
    }
  }
  List<String> missingDNIs = expectedDnis.collect()
  missingDNIs.removeAll(foundDevices.collect{ it.getDeviceNetworkId() })
  missingDNIs.each{ dni ->
    Lwarn('_vswConfigure()', "Adding VSW ${b(dni)}")
    DevW vsw = addChildDevice(
      'hubitat',          // namespace
      'Virtual Switch',   // typeName
      dni,                // device's unique DNI
      [isComponent: true, name: dni, name: dni]
    )
    vsw.off()
  }
  _vswSubscribe()
}

void _vswUpdateState (String on, List<String> offList) {
  String dni = "${state.vswDniPrefix}${on}"
  DevW d = getChildDevice(dni)
  d ? d.on() : Lerror ('_vswRefreshState()', "Unable to find device ${d}")
  offList.each{ off ->
    dni = "${state.vswDniPrefix}${off}"
    d = getChildDevice(dni)
    d ? d.off() : Lerror ('_vswRefreshState()', "Unable to find device ${d}")
  }
}

Boolean _vswUnsubscribe () {
  app.unsubscribe('vswEventHandler')
}

Boolean _vswSubscribe () {
  // Returns false if an issue arises during subscriptions
  Boolean issueArose = false
  [state.activeButton, *state.inactiveButtons].each{ button ->
    String dni = "${state.vswDniPrefix}${button}"
    DevW vsw = getChildDevice(dni)
    if (!vsw) {
      issueArose = true
      Lerror('_vswSubscribe()', "Cannot find ${b(dni)}")
      result = false
    } else {
      subscribe(vsw, vswEventHandler, ['filterEvents': true])
    }
  }
  return issueArose
}

void vswEventHandler (Event e) {
  // Design Notes
  //   - VSWs are turned on/off as a part of _vswUpdateState().
  //   - BUT, Hubitat Dashboard and Alexa can also turn on/off VSWs.
  //   - Process and SUPPRESS any events that report state inconsistent
  //     with current on/off buttons
  if (e.isStateChange) {
    if (e.value == 'on') {
      String dni = e.displayName
      String button = dni?.minus(state.vswDniPrefix)
      if (state.onButton != button) pbsgActivateButton(button)
    } else if (e.value == 'off') {
      String dni = e.displayName
      String button = dni?.minus(state.vswDniPrefix)
      if (!state.offButtons.contains(button)) pbsgDeactivateButton(button)
    } else {
      Ldebug('vswEventHandler()', "Unexpected event ${EventDetails(e)}")
    }
  }
}
