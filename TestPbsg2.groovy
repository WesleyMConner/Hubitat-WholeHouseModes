// ---------------------------------------------------------------------------------
// P B S G 2
//
// Copyright (C) 2023-Present Wesley M. Conner
//
// LICENSE
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
// not use this file except in compliance with the License. Unless
// required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// ---------------------------------------------------------------------------------

import com.hubitat.hub.domain.Event as Event

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include wesmc.lHExt
#include wesmc.lHUI

definition (
  name: 'TestPbsg2',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Preview Pbsg functionality',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(name: 'TestPbsgPage')
}

// The psuedo-class "pbsgConfig" solicits the button data that drives
// PBSG creation.

String pbsgConfig_SolicitName(String nameKey) {
  input(
    name: nameKey, title: '<b>PBSG NAME</b>', width: 2,
    type: 'text', submitOnChange: true, required: true, multiple: false
  )
  return settings."${nameKey}"
}

ArrayList pbsgConfig_SolicitButtons(String buttonsKey) {
  input(
    name: buttonsKey, title: '<b>PBSG BUTTONS</b> (space delimited)', width: 6,
    type: 'text', submitOnChange: true, required: true, multiple: false
  )
  return settings."${buttonsKey}"?.tokenize(' ')
}

String pbsgConfig_SolicitDefault(String defaultButtonKey, ArrayList buttons) {
  input(
    name: defaultButtonKey, title: '<b>DEFAULT BUTTON</b>', width: 2,
    type: 'enum', submitOnChange: true, required: false, multiple: false,
    options: buttons
  )
  return settings."${defaultButtonKey}"
}

String pbsgConfig_SolicitActive(String activeButtonKey, ArrayList buttons) {
  input(
    name: activeButtonKey, title: '<b>ACTIVE BUTTON</b>', width: 2,
    type: 'enum', submitOnChange: true, required: false, multiple: false,
    options: buttons
  )
  return settings."${activeButtonKey}"
}

Map pbsgConfig_SolicitInstance(Integer settingsKeySuffix) {
  // Solicit logical button data used to create a Pbsg instance.
  Map pbsgConfig = [:]
  String nameKey = "pbsgName^${settingsKeySuffix}"
  String name = pbsgConfig_SolicitName(nameKey)
  if (name) {
    String buttonsKey = "pbsgButtons^${settingsKeySuffix}"
    String defaultKey = "pbsgDefault^${settingsKeySuffix}"
    String activeKey = "pbsgActive^${settingsKeySuffix}"
    ArrayList buttons = pbsgConfig_SolicitButtons(buttonsKey)
    String defaultButton = pbsgConfig_SolicitDefault(defaultKey, buttons)
    String activeButton = pbsgConfig_SolicitActive(activeKey, buttons)
    pbsgConfig = [
      'name': name,
      'buttons': buttons,
      'defaultButton': defaultButton,
      'activeButton': activeButton
    ]
  } else {
     paragraph('', width: 10)  // Filler for a 12 cell row
  }
  return pbsgConfig
}

// The psuedo-class "pbsg" creates and manages the VSWs that realize a
// PushButton Switch Group (PBSG).

DevW pbsg_GetDeviceWithDNI(ArrayList deviceFifo, String dni) {
  DevW result = null
  deviceFifo.eachWithIndex{ d, i ->
    if (d.deviceNetworkId == dni) { result = deviceFifo.getAt(i) }
  }
  return result
}

DevW pbsg_ExtractDeviceWithDNI(ArrayList deviceFifo, String dni) {
  Integer foundIndex = null
  deviceFifo.eachWithIndex{ d, i ->
    if (d.deviceNetworkId == dni) { foundIndex = i }
  }
  return (foundIndex != null) ? deviceFifo.removeAt(foundIndex) : null
}

void pbsg_RemoveDevice(ArrayList deviceFifo, DevW device) {
  Integer foundIndex = null
  deviceFifo.eachWithIndex{ d, i ->
    if (d == device) { foundIndex = i }
  }
  if (foundIndex != null) { deviceFifo.removeAt(foundIndex) }
}

void pbsg_PopulateFifo(Map pbsg, Map config) {
  ArrayList deviceFifo = []
  config.buttons.each{ button ->
    String vswDNI = "${config.name}_${button}"
    DevW device = getChildDevice(vswDNI) ?:
      addChildDevice(
        'hubitat',          // namespace
        'Virtual Switch',   // typeName
        vswDNI,             // device's unique DNI
        [isComponent: true, name: vswDNI]
      )
    deviceFifo << device
  }
  pbsg.deviceFifo = deviceFifo
}

void pbsg_PopulateDefault(Map pbsg, Map config) {
  if (config.defaultButton) {
    String defaultDNI = "${config.name}_${config.defaultButton}"
    pbsg.defaultDevice = pbsg_GetDeviceWithDNI(pbsg.deviceFifo, defaultDNI)
  } else {
    pbsg.defaultDevice = null
  }
}

void pbsg_PopulateActive(Map pbsg, Map config) {
  if (config.activeButton) {
    String activeDNI = "${config.name}_${config.activeButton}"
    pbsg.activeDevice = pbsg_ExtractDeviceWithDNI(pbsg.deviceFifo, activeDNI)
  } else if (pbsg.defaultDevice) {
    pbsg.activeDevice = pbsg.defaultDevice
    pbsg_RemoveDevice(pbsg.deviceFifo, pbsg.activeDevice)
  } else {
    pbsg.activeDevice = null
  }
}

void pbsg_RefreshVSWs(Map pbsg) {
  //logInfo('pbsg_RefreshVSWs#162', pbsg_SummarizeState(pbsg))
  if (pbsg) {
    pbsg_UnsubscribeToVswEvents(pbsg)
    //logInfo('pbsg_RefreshVSWs', pbsg_SummarizeState(pbsg))
    pbsg.deviceFifo.each{ d ->
      //logInfo('pbsg_RefreshVSWs', "d: >${d}<")
      d.off()
    }
    pbsg.activeDevice?.on()
    pbsg_SubscribeToVswEvents(pbsg)
  }
}

Map pbsg_CreateInstance(Map config) {
  //logInfo('pbsg_CreateInstance#176', "config: ${config}")
  // Use provided pbsgConfig to create VSWs and initialize FIFO et al.
  if (config.name && config.buttons) {
    Map pbsg = [ 'name': config.name ]
    //logInfo('pbsg_CreateInstance#180', pbsg_SummarizeState(pbsg))
    pbsg_PopulateFifo(pbsg, config)
    //logInfo('pbsg_CreateInstance#182', pbsg_SummarizeState(pbsg))
    pbsg_PopulateDefault(pbsg, config)
    pbsg_PopulateActive(pbsg, config)
    pbsg_RefreshVSWs(pbsg)
    pbsgStore_Save(pbsg)
    logInfo('pbsg_CreateInstance', pbsg_SummarizeState(pbsg))
  } else {
    logInfo(
      'pbsg_CreateInstance#190',
      "Awaiting Name (${config.name}) and Buttons (${config.buttons})."
    )
    return null
  }
  return pbsg
}

void pbsg_SubscribeToVswEvents(Map pbsg) {
  ArrayList vsws = [*pbsg.deviceFifo, pbsg.activeDevice]
  // 2024-04-01: Subscribing to a list of devices still fails!
  // subscribe(vsws, pbsg_VswEventHandler, ['filterEvents': true])
  vsws.each{ d ->
    logInfo("pbsg_SubscribeToVswEvents", "Subscribing to ${pbsg_VswEventHandler}.")
    subscribe(d, pbsg_VswEventHandler, ['filterEvents': true])
  }
}

void pbsg_UnsubscribeToVswEvents(Map pbsg) {
  // 2024-04-01: Avoiding buggy subsribe/unsubscribe to a list of devices.
  // unsubscribe([*pbsg.deviceFifo, pbsg.activeDevice])
  ArrayList vsws = [*pbsg.deviceFifo, pbsg.activeDevice]
  vsws.each{ d -> unsubscribe(d) }
}

void pbsg_DniTurnedOnEvent(Map pbsg, String deviceDNI) {
  // Process a 'VSW turned on' event
  if (pbsg.activeDevice.deviceNetworkId == deviceDNI) {
    // Nothing to do, VSW is already active
  } else if (pbsg.activeDevice != null) {
    pbsg.fifo.push(pbsg.activeDevice)
  }
  pbsg.activeDevice = pbsg_ExtractDeviceWithDNI(pbsg.deviceFifo, deviceDNI)
}

void pbsg_DniTurnedOffEvent(Map pbsg, String deviceDNI, Integer pauseMS = 500) {
  // RA2 turns off one scene BEFORE turning on the replacement scene.
  // For PBSG, the 'turn on' events should be processed BEFORE the
  // 'turn off' events. The introduction of a short delay in processing
  // 'off' events should allow semi-concurrent 'on' events to 'play through'.
  // https://docs2.hubitat.com/developer/common-methods-object
  if (pauseMS) { pauseExecution(pauseMS) }
  if (pbsg.activeDevice.deviceNetworkId == deviceDNI) {
    pbsg.fifo.push(pbsg.activeDevice)
    if (pbsg.defaultDevice) {
      pbsg.activeDevice = pbsg_ExtractDeviceWithDNI(pbsg.deviceFifo, deviceDNI)
    }
  }
}

void pbsg_VswEventHandler(Event e) {
  // RA2 turns off one scene BEFORE turning on the replacement scene.
  // For PBSG, the 'turn on' events should be processed BEFORE the
  // 'turn off' events. The delay of processing an 'off' event is pushed
  // down to pbsg_DniTurnedOffEvent().
  logInfo('pbsg_VswEventHandler', e.descriptionText)
  logInfo('pbsg_VswEventHandler', "e: ${eventDetails(e)}")
  // The e.displayName is the DNI of the VSW generating the event. The
  // DNI is of the form '${pbsgInstName}-${buttonName}'.
  /*
  String pbsgInstName = e.displayName.tokenize('_')[0]
  logInfo('pbsgInstName#249', "pbsgInstName: >${pbsgInstName}<")
  Map pbsg = pbsgStore_Retrieve(pbsgInstName)
  logInfo('pbsg_VswEventHandler#251', pbsg_SummarizeState(pbsg))
  String dni = e.displayName
  if (e.value == 'on') {
    logInfo('pbsg_VswEventHandler#254', pbsg_SummarizeState(pbsg))
    pbsg_DniTurnedOnEvent(pbsg, dni)
    logInfo('pbsg_VswEventHandler#256', pbsg_SummarizeState(pbsg))
  } else if (e.value == 'off') {
    logInfo('pbsg_VswEventHandler#258', pbsg_SummarizeState(pbsg))
    pbsg_DniTurnedOffEvent(pbsg, dni)
    logInfo('pbsg_VswEventHandler#260', pbsg_SummarizeState(pbsg))
  } else {
    logWarn(
      'pbsg_VswEventHandler',
      "Unexpected value (${e.value}) for DNI (${dni}")
  }
  logInfo('pbsg_VswEventHandler#261', pbsg_SummarizeState(pbsg))
  */
}

// !!!!!   Y O U   A R E   H E R E   !!!!!

void pbsg_TurnOnDni(Map pbsg, String deviceDNI) {
  // For now, adjust the PBSG THEN turn on the device.
  // [The alternative is to turn on the device and wait for a device event
  // to impact the FIFO.]
  pbsg_DniTurnedOnEvent(pbsg, deviceDNI)
  deviceDNI.on()
}

void pbsg_TurnOffDni(Map pbsg, String deviceDNI) {
  // See discusion in pbsg_TurnOnDni().
  pbsg_DniTurnedOffEvent(pbsg, deviceDNI, 0)
  deviceDNI.off()
}

void pbsg_ToggleDni(Map pbsg, String deviceDNI) {
  // Caused the target device to be toggled on->off or off->on.
  DevW device = pbsg_GetDeviceWithDNI(pbsg.deviceFifo, deviceDNI) {
    if (switchState(device) == 'on') {
      pbsg_TurnOffDni(pbsg, deviceDNI)
    } else {
      pbsg_TurnOnDni(pbsg, deviceDNI)
    }
  }
}

void pbsg_ActivatePrior(Map pbsg) {
  // Swap the currently active DNI with the DNI at the front of the FIFO.
  DevW temp = null
  if (pbsg.activeDevice) { temp = pbsg.activeDevice }
  pbsg.activeDevice = pbsg.fifo.pop()
  if (temp) { pbsg.fifo.push(temp) }
}

String buttonName(DevW device) {
  return device ? device.deviceNetworkId?.tokenize('_')[1] : '<em>nil</em>'
}

String buttonNameWithState(DevW device, DevW dflt) {
  String tag = (device && (device?.deviceNetworkId == dflt?.deviceNetworkId))
    ? '*' : ''
  String summary = "${tag}<b>${buttonName(device)}</b> "
  String swState = switchState(device)
  if (swState == 'on') { summary += "(<b>${swState}</b>)" }
  else if (swState == 'off') { summary += "(<em>${swState}</em>)" }
  else { summary += '(--)' }
}

String pbsg_SummarizeState(Map pbsg) {
  String result
  if (pbsg) {
    result = "PBSG <b>${pbsg.name}</b> "
    logInfo('pbsg_SummarizeState', "pbsg: >${pbsg}<")
    result += buttonNameWithState(pbsg.activeDevice, pbsg.defaultDevice)
    result += ', ['
    result += pbsg.deviceFifo.collect{ d ->
      "${buttonNameWithState(d, pbsg.defaultDevice)}"
    }.join(', ')
    result += "]"
  } else {
    logError('pbsg_SummarizeState', 'Called with null pbsg instance.')
  }
  return result
}

ArrayList pbsg_SummarizeActualState() {
  // Provide a visual summary of FIFO and underlying DNI state.
  //   Active: <active> Fifo: [...]
  // Note any discrepancies between the PBSG and underlying VSWs.
}

ArrayList pbsg_ReconcileFifoToVsws() {
  // Find issues and (optionally) "force" corrections.
}

// The psuedo-class "pbsgStore" facilitates concurrent storage of multiple
// "pbsg" psuedo-class instances.

Map pbsgStore_Retrieve(String pbsgName) {
  // Retrieve a "pbsg" psuedo-class instance.
  Map store = state.pbsgStore ?: [:]
  logInfo('pbsgStore_Retrieve', "Stored PBSG count: ${store.size()}")
  return store."${pbsgName}"
}

void pbsgStore_Save(Map pbsg) {
  // Add/update a "pbsg" psuedo-class instance.
  Map store = state.pbsgStore ?: [:]
  store."${pbsg.name}" = pbsg
  logInfo('pbsgStore_Save', "Stored PBSG count: ${store.size()}")
  state.pbsgStore = store
}

void pbsgStore_InitAllPbsgs() {
  Map pbsgStore = state.pbsgStore
  pbsgStore.each{pbsgName, pbsg ->
    pbsg_SubscribeToVswEvents(pbsg)
  }
}

// MAP OF PBSGS

Map TestPbsgPage() {
  return dynamicPage(
    name: 'TestPbsgPage',
    title: [
      heading1("TestPbsgPage - ${app.id}"),
      bullet1('Click <b>Done</b> to enable subscriptions.')
    ].join('<br/>'),
    install: true,
    uninstall: true
  ) {
    //---------------------------------------------------------------------------------
    // REMOVE NO LONGER USED SETTINGS AND STATE
    //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    //-> app.removeSetting('..')
    //-> state.remove('..')
    //---------------------------------------------------------------------------------
    app.updateLabel('TestPbsgPage')
    state.remove('childVsws')
    // Map mapOfPbsgs = [:]
    Map pbsgInst = [:]
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      state.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
      // NOTE: state.pbsgs is ALWAYS rebuilt from settings and child VSW discovery.
      for (i in [0]) {
        Map pbsgConfig = pbsgConfig_SolicitInstance(i)
        if (pbsgConfig && pbsgConfig.name && pbsgConfig.buttons) {
          pbsgInst = pbsg_CreateInstance(pbsgConfig)
        } else {
          logInfo('pbsg_CreateInstance', 'PBSG creation is pending config data')
        }
      }
    }
  }
}

/*
void deleteOrphanedDevices() {
  // At application scope, identify orphaned child devices by:
  //   - Beginning with current child devices
  //   - Dropping any expected / current devices
  ArrayList dnis = getChildDevices().collect{ d -> d.deviceNetworkId }
  //logInfo('deleteOrphanedDevices', "All Child Devices: ${dnis}")
  // Drop dnis currently associated with PBSGs

  logInfo('deleteOrphanedDevices()', 'ENTERED')
  logPbsgs(state.pbsgs)
  /*
  logInfo('deleteOrphanedDevices', ['',
    "Child Dnis: ${dnis}",
    " Pbsg Dnis: ${pbsgDnis()}"
  ])
  */
  /*
  pbsgs().each{ pbsgName, map ->
    logInfo('deleteOrphanedDevices', pbsgName)
    logInfo('deleteOrphanedDevices', ['',
      "dnis In: ${dnis}",
      "Dropping: ${map.dnis}"
    ])
    dnis.removeAll{ item -> map.dnis.contains(item) }
  }
  logInfo('deleteOrphanedDevices', "Child Devices Less PBSG-owned: ${dnis}")
  //}

  /*
  ArrayList orphanedDNIs = getChildDevices().

  ArrayList orphanedDnis = childVswsAtStart.minus(childVswsCurrent)
      paragraph("<b>orphanedDnis</b>: ${orphanedDnis}")
      orphanedDnis.each{ dni ->
        paragraph("Deleting orphaned device DNI: '${d.deviceNetworkId}'")
        deleteChildDevice(dni)
      }

  Map pbsgs = state.pbsgs
    ArrayList parsedDeviceName = d.name.tokenize('_')
    String pbsgName = parsedDeviceName[0]
    String buttonName = parsedDeviceName[1]
    Boolean isActive = pbsgs."${pbsgName}".buttons.contains(buttonName)
    logInfo('initialize', [
     "<b>pbsgName</b>: ${pbsgName}, ",
     "<b>pbsgDni</b>: ${d.deviceNetworkId}, ",
     "<b>buttonName</b>: ${buttonName}, ",
     "<b>isActive</b>: ${isActive}"
    ].join())
    if (isActive == false) {
      logInfo('initialize',
        "Deleting unaffiliated device '${d.name} with dni '${d.deviceNetworkId}'"
      )
      deleteChildDevice(d.deviceNetworkId)
    }
  }
}
*/

void initialize() {
  pbsgStore_InitAllPbsgs()
}

void installed() {
  logWarn('installed', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled() {
  logWarn('uninstalled', 'Entered')
  removeAllChildApps()
}

void updated() {
  logWarn('updated', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}
