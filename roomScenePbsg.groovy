
/*
pbsgGetOrCreateInstance (
  app.getLabel(),
  [*roomScenes, 'AUTOMATIC', 'MANUAL_OVERRIDE'],
  'AUTOMATIC'
)

subscribe(vsw, "PbsgCurrentSwitch", roomScenePbsgHandler, ['filterEvents': false])

void roomeScenePbsgHandler (Event e) {
  logDebug('modePbsgHandler()', e.descriptionText)
}

//----
//---- RESOURCES
//----

void roomSceneVswEventHandler (Event e) {
  // Process events for Room Scene PGSG child VSWs.
  //   - The received e.displayName is the DNI of the reporting child VSW.
  //
  // P A R E N T   R E Q U I R E M E N T
  //   - Parent must provide 'activateRoomScene(String roomScene)' which is
  //     invoked when a pbsgVswTurnedOnCallback()'
  Ltrace('roomSceneVswEventHandler', "EventDetails: ${EventDetails(e)}")
  if (e.isStateChange) {
    if (e.value == 'on') {
      if (atomicState.previousVswDni == e.displayName) {
        Lerror(
          'roomSceneVswEventHandler()',
          "The active Room Scene VSW '${atomicState.activeVswDni}' did not change."
        )
      }
      //--TBD-> TREAT 'getDefaultVswDni' AS PRIVATE
      atomicState.previousVswDni = atomicState.activeVswDni ?: getDefaultVswDni()
      atomicState.activeVswDni = e.displayName
      Linfo(
        'roomSceneVswEventHandler()',
        "${atomicState.previousVswDni} -> ${atomicState.activeVswDni}"
      )
      //--TBD-> TREAT 'vswDniToName' AS PRIVATE
      String scene = _vswDnitoButtonName(atomicState.activeVswDni)
      parent.activateRoomScene(scene)
    } else if (e.value == 'off') {
      Ltrace()
      // Take no action when a VSW turns off
    } else {
      Lwarn(
        'roomSceneVswEventHandler()',
        "unexpected event value = >${e.value}<"
      )
    }
  }
}
*/