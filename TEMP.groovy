void pro2RepeaterHandler(Event e) {
  if (e.name == 'pushed') {
    String eventButton = e.value
    String repeaterId = extractLutronIdFromLabel(e.displayName)
    List<String> data = state.repeaterButtonToRoomScene?.getAt(deviceId)?.getAt(eventButton)
    String room = data[0]
    String scene = data[1]
  }
}


void ra2RepeaterHandler(Event e) {
  // Isolate Main Repeater (ra2-1, ra2-83, pro2-1) buttonLed-## events to
  // capture de-centralized Room Scene activation.
  logInfo('#317', eventDetails(e))

descriptionText  Caséta Repeater (pro2-1) button 6 was pushed
displayName  Caséta Repeater (pro2-1)
deviceId  9129
name  pushed
value  6

  if (e.name.startsWith('buttonLed-')) {
    //Integer eventButton = safeParseInt(e.name.substring(10))
    String eventButton = e.name.substring(10)
    String deviceId = extractLutronIdFromLabel(e.displayName)
    List<String> data = state.repeaterButtonToRoomScene?.getAt(deviceId)?.getAt(eventButton)
    String room = data[0]
    String scene = data[1]
    logInfo('ra2RepeaterHandler', "${deviceId}..${eventButton}..${e.value}..${room}..${scene}")

    if (e.value == 'on') {
      state.repeaterButtonToRoomScene.each{ repeaterId, map ->
        if (repeaterId != deviceId) {
          // Press appropriate scene buttons on peer repeaters.
          map.each{ buttonNumber, roomAndScene ->
            if (roomAndScene[0] == room && roomAndScene[1] == scene) {
              logInfo(
                'ra2RepeaterHandler',
                "For room: ${room}, scene: ${scene} -> repeaterId: ${repeaterId}, button: ${buttonNumber}"
              )
              logInfo('#328', "Push ${safeParseInt(buttonNumber)} on ${repeaterId}")
              pushRepeaterButton(repeaterId, safeParseInt(buttonNumber))
            }
          }
        }
      }
    }
  }
}