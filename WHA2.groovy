
// ---------------------------------------------------------------------------------
// W H O L E   H O U S E   A U T O M A T I O N   V 2
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

// The Groovy Linter generates false positives on Hubitat #include !!!
#include wesmc.lHExt
#include wesmc.lHUI
#include wesmc.lLut
#include wesmc.lPbsgv2

definition (
  name: 'WHA',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Whole House Automation using Modes, RA2 and Room Overrides',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',
  singleInstance: true
)

preferences {
  page(name: 'WhaPage')
}

void AllAuto () {
  settings.rooms.each { roomName ->
    InstAppW roomApp = app.getChildAppByLabel(roomName)
    String manualOverrideSwitchDNI = "${roomApp.label}_Automatic"
    logInfo('AllAuto', "Turning on ${b(manualOverrideSwitchDNI)}")
    roomApp.getRSPbsg().turnOnSwitch(manualOverrideSwitchDNI)
  }
}

void pbsg_ButtonOnCallback (Map pbsg) {
  // - The MPbsg instance calls this method to reflect a state change.
  String newMode = pbsg.activeButton
  logInfo('pbsg_ButtonOnCallback', "Received mode: ${b(newMode)}")
  getLocation().setMode(newMode)
}

void installed () {
  logWarn('installed', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled () {
  logWarn('uninstalled', 'Entered')
  removeAllChildApps()
}

void updated () {
  logWarn('updated', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  //---------------------------------------------------------------------------------
  // REMOVE NO LONGER USED SETTINGS AND STATE
  //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
  //   - state.remove('X')
  //   - app.removeSetting('Y')
  //---------------------------------------------------------------------------------
  state.remove('MODE_PBSG_APP_LABEL')
  state.remove('MODE_PBSG_APP_NAME')
  app.removeSetting('hubitatQueryString')
  initialize()
}

void initialize () {
  // - The same keypad may be associated with two different, specialized handlers
  //   (e.g., mode changing buttons vs special functionalily buttons).
  logTrace('initialize', 'Entered')
}

void _idParticipatingRooms () {
  roomPicklist = app.getRooms().collect {it.name.replace(' ', '_')}.sort()
  input(
    name: 'rooms',
    type: 'enum',
    title: heading2('Identify Participating Rooms'),
    options: roomPicklist,
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _displayInstantiatedRoomHrefs () {
  paragraph heading1('Room Scene Configuration')
  settings.rooms.each { roomName ->
    InstAppW roomApp = app.getChildAppByLabel(roomName)
    if (!roomApp) {
      logWarn(
        'addRoomAppsIfMissing',
        "Adding room ${roomName}"
      )
      roomApp = addChildApp('wesmc', 'RoomScenes', roomName)
    }
    href (
      name: roomName,
      width: 2,
      url: "/installedapp/configure/${roomApp?.id}",
      style: 'internal',
      title: "${appInfo(roomApp)} Scenes",
      state: null
    )
  }
}

void authorizeRa2Repeaters() {
  input(
    name: 'ra2Repeaters',
    title: heading3('Grant Access to RA2 Repeaters'),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void pushRa2RepButton(String repeaterId, Long buttonNumber) {
  settings.ra2Repeaters.each { repeater ->
    if (getDeviceId(repeater) == repeaterId) {
      repeater.push(buttonNumber)
    }
  }
}

void authorizeZwaveDevices() {
  input(
    name: 'zWaveDevices',
    title: heading3("Grant Access to Z-Wave Devices"),
    type: 'capability.switch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void subscribeIndDevToHandler(Map data) {
  // USAGE:
  //   runIn(1, 'subscribeIndDevToHandler', [data: [device: d]])
  // Independent Devices (especially RA2 and Caséta) are subject to stale
  // Hubitat state data if callbacks occur quickly (within 1/2 second)
  // after a level change. So, briefly unsubscribe/subscribe to avoid
  // this situation.
  logTrace(
    'subscribeIndDevToHandler',
    "${room.name} subscribing ${deviceInfo(data.device)}"
  )
  subscribe(device, indDeviceHandler, ['filterEvents': true])
}

void unsubscribeIndDevToHandler(DevW device) {
  // Independent Devices (especially RA2 and Caséta) are subject to stale
  // Hubitat state data if callbacks occur quickly (within 1/2 second)
  // after a level change. So, briefly unsubscribe/subscribe to avoid
  // this situation.
  logTrace(
    '_unsubscribeToIndDeviceHandler',
    "${room.name} unsubscribing ${deviceInfo(device)}"
  )
  unsubscribe(device)
}

/*
void subscribeToRa2RepEvents(Map data) {
  // USAGE:
  //   runIn(1, 'subscribeToRa2RepEvents', [data: [device: d]])
  // Unlike some Independent Devices (RA2 and Caséta) RA2 Repeaters
  // are not particularly subject to stale Hubitat state; HOWEVER,
  // callbacks that occur quickly (within 1/2 second) after a buton press
  // subject Hubitat to callback overload (during WHA scene chantes).
  // Briefly unsubscribe/subscribe to avoid this situation.
  logTrace(
    'subscribeToRa2RepEvents',
    "${room.name} subscribing ${deviceInfo(data.device)}"
  )
  subscribe(device, repeaterHandler, ['filterEvents': true])
}

void unsubscribeToRa2RepEvents(DevW device) {
  // Unlike some Independent Devices (RA2 and Caséta) RA2 Repeaters
  // are not particularly subject to stale Hubitat state; HOWEVER,
  // callbacks that occur quickly (within 1/2 second) after a buton press
  // subject Hubitat to callback overload (during WHA scene chantes).
  // Briefly unsubscribe/subscribe to avoid this situation.
  logTrace(
    'unsubscribeToRa2RepEvents',
    "${room.name} unsubscribing ${deviceInfo(device)}"
  )
  unsubscribe(device)
}
*/

void setDeviceLevel(String deviceLabel, Long level) {
  settings.zWaveDevices.each { device ->
    if (device.label == deviceLabel) {
      if (device.hasCommand('setLevel')) {
        logInfo('activateScene', "Setting ${b(deviceLabel)} to level ${b(level)}")
        // Some devices DO NOT support a level of 100.
        device.setLevel(level == 100 ? 99 : level)
      }
      if (level == 0) {
        logInfo('activateScene', "Setting ${b(deviceLabel)} to off")
        device.off()
      } else if (level == 100) {
        logInfo('activateScene', "Setting ${b(deviceLabel)} to on")
        device.on()
      }
    }
  }
}

Map WhaPage () {
  return dynamicPage(
    name: 'WhaPage',
    title: [
      heading1("Whole House Automation (WHA) - ${app.id}"),
      bullet1('Tab to register changes.'),
      bullet1('Click <b>Done</b> to enable subscriptions.')
      authorizeRa2Repeaters()
      authorizeZwaveDevices()
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
    app.removeSetting('hubitatQueryString')
    app.removeSetting('modeButton_Chill')
    app.removeSetting('modeButton_Cleaning')
    app.removeSetting('modeButton_Day')
    app.removeSetting('modeButton_Night')
    app.removeSetting('modeButton_Party')
    app.removeSetting('modeButton_TV')
    app.removeSetting('specialFnButton_ALL_OFF')
    state.remove('kpadButtonDniToSpecialtyFn')
    state.remove('kpadButtonDniToTargetMode')
    state.remove('specialFnButtonMap')
    state.remove('SPECIALTY_BUTTONS')
    state.remove('MODE_PBSG_LABEL')
    state.remove('modeButtonMap')
    app.updateLabel('WHA')
    state.MPBSG_LABEL = '_MPbsg'
    state.MODES = getLocation().getModes().collect { it.name }
    getGlobalVar('defaultMode').value
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      solicitLogThreshold('pbsgLogThresh', 'INFO') // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      _idParticipatingRooms()
      Map modePbsgConfig = [
        'name': 'mode',
        'allButtons': getLocation().getModes().collect { it.name },
        'defaultButton': getLocation().currentMode.name
      ]
      Map modePbsg = pbsg_Initialize(modePbsgConfig)
      if (settings.rooms) {
        _displayInstantiatedRoomHrefs()
      }
    }
  }
}
