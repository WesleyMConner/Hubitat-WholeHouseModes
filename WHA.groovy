// ---------------------------------------------------------------------------------
// W H O L E   H O U S E   A U T O M A T I O N
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
#include wesmc.lPbsgV2

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

void pbsg_ButtonOnCallback (String pbsgName) {
  pbsg = atomicState."${pbsgName}"
  if (pbsg?.name == 'mode') {
    // - The MPbsg instance calls this method to reflect a state change.
    String newMode = pbsg.activeButton
    logInfo('pbsg_ButtonOnCallback', "Received mode: ${b(newMode)}")
    getLocation().setMode(newMode)
    // Pass new mode to rooms to alleviate their need to handle modes.
    ArrayList roomNames = settings.rooms
    roomNames.each{ roomName ->
      InstAppW roomObj = app.getChildAppByLabel(roomName)
      logInfo('pbsg_ButtonOnCallback', "roomName: ${roomName}, newMode: ${newMode}")
      roomObj.room_ModeChange(newMode)
    }
  } else {
    logError(
      'pbsg_ButtonOnCallback',
      "Could not find PBSG '${pbsgName}' via atomicState"
    )
  }
}

void installed () {
  logWarn('installed', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled () {
  logWarn('uninstalled', 'Entered')
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
  roomPicklist = getRooms().name.sort()
  paragraph "_idParticipating Rooms with >${roomPickList}<"
  input(
    name: 'rooms',
    type: 'enum',
    title: h2('Identify Participating Rooms'),
    options: roomPicklist,
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void _displayInstantiatedRoomHrefs () {
  paragraph h1('Room Scene Configuration')
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

Map WhaPage () {
  return dynamicPage(
    name: 'WhaPage',
    title: [
      h1("Whole House Automation (WHA) - ${app.id}"),
      bullet1('Tab to register changes.'),
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
    app.removeSetting('pbsgLogThresh')
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
    state.remove('MODE_PBSG_LABEL')
    state.remove('modeButtonMap')
    //state.remove('MPBSG_LABEL')
    state.remove('specialFnButtonMap')
    state.remove('SPECIALTY_BUTTONS')
    app.updateLabel('WHA')
    state.MODES = getLocation().getModes().collect { it.name }
    getGlobalVar('defaultMode').value
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // ERROR, WARN, INFO, DEBUG, TRACE
      //solicitLogThreshold('pbsgLogThresh', 'INFO') // ERROR, WARN, INFO, DEBUG, TRACE
      _idParticipatingRooms()
      atomicState.mode = [
        'name': 'mode',
        'allButtons': getLocation().getModes().collect { it.name },
        'defaultButton': getLocation().currentMode.name,
        'instType': 'pbsg'
      ]
      pbsg_BuildToConfig('mode')
      if (settings.rooms) {
        _displayInstantiatedRoomHrefs()
      }
    }
  }
}
