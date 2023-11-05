// ---------------------------------------------------------------------------------
// roomScenePbsg (an instsantiation of libPbsgBase)
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
#include wesmc.libPbsgBase
#include wesmc.libUtils
#include wesmc.libLogAndDisplay

definition (
  parent: 'wesmc:roomScene',
  name: 'roomScenePbsg',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'A PBSG (extends libPbsgBase) designed for use in roomScene.groovy',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  installOnOpen: false,
  documentationLink: '',  // TBD
  videoLink: '',          // TBD
  importUrl: '',          // TBD
  oauth: false,           // Even if used, must be manually enabled.
  singleInstance: false
)

preferences {
  page(name: '_roomScenePbsgPage')
}

//----
//---- PAGE DISPLAY AND SUPPORT
//----

Map _roomScenePbsgPage () {
  // Norally, this page IS NOT presented.
  //   - This page can be viewed via an instance link on the main Hubitat Apps menu.
  //   - Instance state & settings are rendered on the parent App's page.
  return dynamicPage (
    name: '_roomScenePbsgPage',
    install: true,
    uninstall: false
  ) {
    _pbsgBasePage()
  }
}

//----
//---- STANDALONE METHODS (no inherent "this")
//----

void roomSceneVswEventHandler (Event e) {
  // Process events for Room Scene PGSG child VSWs.
  //   - The received e.displayName is the DNI of the reporting child VSW.
  //
  // P A R E N T   R E Q U I R E M E N T
  //   - Parent must provide 'activateRoomScene(String roomScene)' which is
  //     invoked when a pbsgVswTurnedOnCallback()'
  Ltrace('roomSceneVswEventHandler', "eventDetails: ${eventDetails(e)}")
  if (e.isStateChange) {
    if (e.value == 'on') {
      if (atomicState.previousVswDni == e.displayName) {
        Lerror(
          'roomSceneVswEventHandler()',
          "The active Room Scene VSW '${atomicState.activeVswDni}' did not change."
        )
      }
      atomicState.previousVswDni = atomicState.activeVswDni ?: getDefaultVswDni()
      atomicState.activeVswDni = e.displayName
      Ldebug(
        'roomSceneVswEventHandler()',
        "${atomicState.previousVswDni} -> ${atomicState.activeVswDni}"
      )
      String scene = _vswDnitoName(atomicState.activeVswDni)
      parent.activateRoomScene(scene)
    } else if (e.value == 'off') {
      Linfo()
      // Take no action when a VSW turns off
    } else {
      Lwarn(
        'roomSceneVswEventHandler()',
        "unexpected event value = >${e.value}<"
      )
    }
  }
}

//----
//---- EXPECTED APP METHODS
//----

void installed () {
  Ltrace('installed()', 'Calling _configureRoomScenePbsg()')
  _configureRoomScenePbsg()
}

void updated () {
  Ltrace('updated()', 'Calling _configureRoomScenePbsg()')
  _configureRoomScenePbsg()
}

void uninstalled () {
  Ldebug('uninstalled()', 'DELETING CHILD DEVICES')
  getAllChildDevices().collect{ device ->
    deleteChildDevice(device.deviceNetworkId)
  }
}

//----
//---- CUSTOM APP METHODS
//----

void _configureRoomScenePbsg() {
  Linfo(
    '_configureRoomScenePbsg()',
    "Updating ${app.getLabel()} state, devices and subscriptions"
  )

  Ltrace('_configureRoomScenePbsg()', 'doing App dup checking')
  detectChildAppDupsForLabels([roomScenePbsgLabel])


  Ltrace('_configureRoomScenePbsg()', 'stopping event subscriptions')
  app.unsubscribe()
  Ltrace('_configureRoomScenePbsg()', 'updating state values')
  _updateRoomScenePbsgState()
  Ltrace('_configureRoomScenePbsg()', 'managing child devices')
  _manageChildDevices('_configureRoomScenePbsg()')
  Ltrace(
    '_configureRoomScenePbsg()',
    "ensuring active VSW matches current mode (${mode})"
  )
  _activateVswForCurrentMode()
  Ltrace('_configureRoomScenePbsg()', 'subscribing to VSW changes')
  _subscribeToModeVswChanges()
}

void _removeLegacyRoomScenePbsgState () {
  atomicState.remove('activeVswDni')
  atomicState.remove('defaultVswDni')
  atomicState.remove('defaultVswName')
  atomicState.remove('inspectScene')
  atomicState.remove('LOG_LEVEL1_ERROR')
  atomicState.remove('LOG_LEVEL2_WARN')
  atomicState.remove('LOG_LEVEL3_INFO')
  atomicState.remove('LOG_LEVEL4_DEBUG')
  atomicState.remove('LOG_LEVEL5_TRACE')
  atomicState.remove('logLevel1Error')
  atomicState.remove('logLevel2Warn')
  atomicState.remove('logLevel3Info')
  atomicState.remove('logLevel4Debug')
  atomicState.remove('logLevel5Trace')
  atomicState.remove('previousVswDni')
  atomicState.remove('roomName')
  atomicState.remove('roomScene')
  atomicState.remove('switchDnis')
}

void _updateRoomScenePbsgState() {
  // Used for initial configuration AND configuration refresh.
  _removeLegacyRoomScenePbsgState()
  atomicState.vswDniPrefix = "${app.getLabel()}_"
  List<String> roomScenes = parent._getRoomScenes()
  atomicState.vswNames = [*roomScenes, 'AUTOMATIC', 'MANUAL_OVERRIDE']
  atomicState.vswDefaultName = 'AUTOMATIC'
  atomicState.logLevel = parent._getLogLevel() ?: _lookupLogLevel('TRACE')
  Ltrace(
    '_updateRoomScenePbsgState()',
    [
      '',
      _getPbsgStateBullets()
    ].join('<br/>')
  )
}

void _subscribeToRoomSceneVswChanges() {
  app.unsubscribe()
  List<DevW> vsws = _getVsws()
  if (!vsws) {
    Lerror('_subscribeToRoomSceneVswChanges()', 'The child VSW instances are MISSING.')
  }
  vsws.each{ vsw ->
    Ltrace(
      '_subscribeToRoomSceneVswChanges()',
      "Subscribe <b>${vsw.dni} (${vsw.id})</b> to modeVswEventHandler()"
    )
    app.subscribe(vsw, "switch", roomSceneVswEventHandler, ['filterEvents': false])
  }
}

void _activateVswForCurrentRoomScene () {
  // P A R E N T   R E Q U I R E M E N T
  //   - Parent must provide 'getCurrentRoomScene()'. If non-null, the
  //     Room Scene VSW is set so as to be consistent with the current
  //     Room Scene. If null, the Room Scene 'AUTOMATIC' is assumed.
  Linfo('_activateVswForCurrentRoomScene', 'I M P L E M E N T A T I O N   P E N D I N G')
  // The initially "on" PBSG VSW should be consistent with the current Room Scene.
  //--PENDING-> String roomScene = parent.getCurrentRoomScene() ?: 'AUTOMATIC'
  //--PENDING-> Ldebug(
  //--PENDING->   '_roomScenePbsgInit()',
  //--PENDING->   "Activating VSW for roomScene: <b>${roomScene}</b>"
  //--PENDING-> )
  //--PENDING-> _turnOnVswExclusivelyByName(roomScene)
}
