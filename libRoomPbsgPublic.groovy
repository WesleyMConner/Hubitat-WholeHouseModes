library (
  name: 'libRoomPbsgPublic',
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'Methods on a Room PBSG (Pushbutton Switch Group) Instance',
  category: 'general purpose',
  documentationLink: '',
  importUrl: ''
)

String roomNameToRoomPbsgName(String roomName) {
  return "pbsg_${roomName}"
}

InstAppW getRoomScenePbsg (String roomName) {
  String roomPbsgName = roomNameToRoomPbsgName(roomName)
  InstAppW roomScenePbsg = getChildAppByLabel(roomPbsgName)
  if (!roomScenePbsg) {
    Lerror('getRoomScenePbsg()', 'Room Scene PBSG is NOT FOUND')
  }
  return roomScenePbsg
}

InstAppW createRoomScenePbsg(
    String roomName,
    List<String> roomScenes,
    String defaultScene,
    String logThreshold
  ) {
  String roomPbsgName = roomNameToRoomPbsgName(roomName)
  InstAppW roomScenePbsg = getChildAppByLabel(roomPbsgName)
  if (!roomScenePbsg) {
    Ldebug('createRoomScenePbsg()', "creating new Room PBSG instance '${roomPbsgName}'")
    roomScenePbsg = app.addChildApp(
      'wesmc',       // See roomPBSG.groovy 'definition.namespace'
      'roomPBSG',    // See roomPBSG.groovy 'definition.name'
      roomPbsgName   // PBSG's label/name (id will be a generated integer)
    )
    configPbsg(roomPbsgName, roomScenes, defaultScene, logThreshold)
  } else {
    Ltrace('createRoomScenePbsg()', "using existing modePbsg instance '${roomPbsgName}'")
  }
  return roomScenePbsg
}

void turnOnRoomSceneExclusively (String sceneName) {
  Ltrace('turnOnRoomSceneVsw()', "At entry, <b>sceneName:</b> ${sceneName}")
  //InstAppW roomPbsg = getRoomPbsg(roomName)
  if (!sceneName) {
    Lerror('turnOnRoomSceneVsw()', "required argument <b>sceneName</b> is null")
  }
  //roomPbsg.turnOnVsw(sceneName)
  turnOnVswExclusively(sceneName)
}

void toggleRoomScene (String sceneName) {
  Ltrace('toggleRoomScene()', "At entry, <b>sceneName:</b> ${sceneName}")
  //InstAppW roomPbsg = getRoomPbsg(roomName)
  if (!sceneName) {
    Lerror('toggleRoomScene()', "required argument <b>sceneName</b> is null")
  }
  //roomPbsg.toggleVsw(sceneName)
  toggleVsw(sceneName)
}
