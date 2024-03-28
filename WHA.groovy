
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
#include wesmc.lFifo
#include wesmc.lHExt
#include wesmc.lHUI
#include wesmc.lLut
#include wesmc.lPbsg

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

InstAppW _getOrCreateMPbsg () {
  // Mpbsg depends on Hubitat Mode properties AND NOT local data.
  InstAppW pbsgApp = app.getChildAppByLabel(state.MPBSG_LABEL)
  if (!pbsgApp) {
    logWarn('_getOrCreateMPbsg', "Adding Mode PBSG ${state.MPBSG_LABEL}")
    pbsgApp = addChildApp('wesmc', 'MPbsg', state.MPBSG_LABEL)
    ArrayList modeNames = getLocation().getModes().collect{ it.name }
    String currModeName = getLocation().currentMode.name
    pbsgApp.pbsgConfigure(
      modeNames,     // Create a PBSG button per Hubitat Mode name
      'Day',         // 'Day' is the default Mode/Button
      currModeName,  // Activate the Button for the current Mode
      settings.pbsgLogThresh ?: 'INFO' // 'INFO' for normal operations
                                       // 'DEBUG' to walk key PBSG methods
                                       // 'TRACE' to include PBSG and VSW state
    )
  }
  return pbsgApp
}

void _writeMPbsgHref () {
  InstAppW pbsgApp = _getOrCreateMPbsg()
  if (pbsgApp) {
    href(
      name: appInfo(pbsgApp),
      width: 2,
      url: "/installedapp/configure/${pbsgApp.id}/MPbsgPage",
      style: 'internal',
      title: "Review ${appInfo(pbsgApp)}",
      state: null
    )
  } else {
    paragraph "Creation of the MPbsgHref is pending required data."
  }
}

void AllAuto () {
  settings.rooms.each{ roomName ->
    InstAppW roomApp = app.getChildAppByLabel(roomName)
    String manualOverrideSwitchDNI = "pbsg_${roomApp.label}_AUTOMATIC"
    logInfo('AllAuto', "Turning on ${b(manualOverrideSwitchDNI)}")
    roomApp.getRSPbsg().turnOnSwitch(manualOverrideSwitchDNI)
  }
}

void pbsgButtonOnCallback (String mode) {
  // - The MPbsg instance calls this method to reflect a state change.
  logInfo('buttonOnCallback', "Received mode: ${b(mode)}")
  getLocation().setMode(mode)
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
  roomPicklist = app.getRooms().collect{it.name.replace(' ', '_')}.sort()
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
  settings.rooms.each{ roomName ->
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
      heading1("Whole House Automation (WHA) - ${app.id}"),
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
    state.MODES = getLocation().getModes().collect{ it.name }
    getGlobalVar('defaultMode').value
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      solicitLogThreshold('pbsgLogThresh', 'INFO') // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      _idParticipatingRooms()
      _writeMPbsgHref()
      if (!settings.rooms) {
        // Don't be too aggressive deleting child apps and their config data.
        paragraph('Management of child apps is pending selection of Room Names.')
      } else {
        //TBD-> pruneAppDups(
        //TBD->   [*settings.rooms, state.MPBSG_LABEL],
        //TBD->   app      // The object (parent) pruning dup children
        //TBD-> )
        _displayInstantiatedRoomHrefs()
      }
    }
  }
}
