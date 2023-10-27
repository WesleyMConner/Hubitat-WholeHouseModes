library (
  name: 'libRoomPbsgPublic',
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'Methods on a Room PBSG (Pushbutton Switch Group) Instance',
  category: 'general purpose',
  documentationLink: '',
  importUrl: ''
)

void roomSceneVswEventHandler (Event e) {
  // Process events for the Room PBSG child VSWs.
  //   - The received e.displayName is the DNI of the reporting child VSW.
  //   -
  //   - After initialize() identifies 'state.activeVswDNI' and calls
  //     parent.pbsgVswTurnedOnCallback(), this method becomes authoritative
  //     for 'state.activeVswDNI' and parent.pbsgVswTurnedOnCallback()
  //     invocation.
  // - The state.previousVswDNI is preserved for reactivation
  //   when MANUAL_OVERRIDE turns off.
  if (e.isStateChange) {
    if (e.value == 'on') {
      turnOffPeers(e.displayName)
      state.previousVswDNI = state.activeVswDNI ?: state.defaultVswDNI
      state.activeVswDNI = e.displayName
      Ldebug(
        'roomSceneVswEventHandler()',
        "${state.previousVswDNI} -> ${state.activeVswDNI}"
      )
      parent.pbsgVswTurnedOnCallback(state.activeVswDNI)
    } else if (e.value == 'off') {
      if (e.displayName.contains('MANUAL_OVERRIDE')) {
        // Special behavior for MANUAL ENTRY: Restore previously switch.
        state.activeVswDNI = state.previousVswDNI ?: state.defaultVswDNI
        turnOnVsw(state.previousVswDNI)
      } else {
        enforceDefaultSwitch()
      }
    } else {
      Lwarn(
        'roomSceneVswEventHandler()',
        "unexpected event value = >${e.value}<"
      )
    }
  }
}

void clientProvidedRoomPbsgInit() {
  // Abstract
  //   Identify 'state.activeVswDNI', call parent.pbsgVswTurnedOnCallback(),
  //   then hand control over to roomSceneVswEventHandler() to process VSW events.
  Ltrace('initialize()', 'At entry')
  unsubscribe()
  manageChildDevices()
  app.getAllChildDevices().each{ device ->
    Ltrace(
      'initialize()',
      "subscribing ${getDeviceInfo(device)}..."
    )
    subscribe(device, "switch", roomSceneVswEventHandler, ['filterEvents': false])
  }
  enforceMutualExclusion()
  enforceDefaultSwitch()
  state.roomScene = state.currScenePerVsw
  //-> enforcePbsgConstraints()
}


String getRoomPbsgAppLabel (String roomName) {
  return "pbsg_${roomName}"
}

String getRoomSceneVswDNI (String roomName, String sceneName) {
  return "${getRoomPbsgAppLabel(roomName)}_${sceneName}"
}

InstAppW getRoomPbsg (String roomName) {
  String roomPbsgAppLabel = getRoomPbsgAppLabel(roomName)
  return app.getChildAppByLabel(roomPbsgAppLabel)
}

InstAppW createRoomScenePbsg(
    String roomName,
    List<String> roomScenes,
    String defaultScene,
    String logThreshold
  ) {
  InstAppW roomPbsg = getRoomPbsg(roomName)
  //--TBD-> manageChildDevices() ???
  //--TBD-> detectChildAppDupsForLabel([roomPbsgAppLabel])
  if (!roomPbsg) {
    roomPbsg = app.addChildApp(
      'wesmc',                   // app namespace  (see 'definition' above)
      'roomPbsg',                // app name       (see 'definition' above)
      getRoomPbsgAppLabel(roomName)  // app label      (use was 'roomPbsgPage')
    )
    List<String> vswDNIs = roomScenes.collect{ scene -> getVswDni(roomName, scene) }
    String defaultVswDNI = getVswDni(roomName, defaultScene)
    roomPbsg.configPbsg(vswDNIs, defaultVswDNI, logThreshold, roomSceneVswOnCallback)
  }
  return roomPbsg
}

void turnOnRoomSceneVsw (String roomName, String sceneName) {
  Ltrace(
    'turnOnRoomSceneVsw()',
    "At entry, <b>roomName:</b> ${roomName}, <b>sceneName:</b> ${sceneName}"
  )
  InstAppW roomPbsg = getRoomPbsg(roomName)
  if (!sceneName) {
    Lerror('turnOnRoomSceneVsw()', "required argument <b>sceneName</b> is null")
  }
  roomPbsg.turnOnVsw(getRoomSceneVswDNI(roomName, sceneName))
}
