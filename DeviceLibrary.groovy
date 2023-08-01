// ------------------------------------------------------------------------
// D E V I C E   L I B R A R Y
//   Copyright (C) 2023-Present Wesley M. Conner
//   Licensed under the Apache License, Version 2.0
//   http://www.apache.org/licenses/LICENSE-2.0
// ------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DeviceWrapper
import com.hubitat.app.DeviceWrapperList as DeviceWrapperList
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Hub as Hub

library (
 name: "DeviceLibrary",
 namespace: "wesmc",
 author: 'WesleyMConner',
 description: "Methods extending Hubitat DeviceWrapperList and DeviceWrappers",
 category: "general purpose",
 documentationLink: '',
 importUrl: ''
)

List<String> deviceIdsForRoom (String room) {
  return app.getRooms().findAll{it.name == room}.collect{it.deviceIds}.flatten().collect{it.toString()}
}

Map<String, String> mapDeviceIdAsStringToRoomName () {
  Map<String, String> results = [:]
  app.getRooms().each{room ->
    log.trace "#29 Room ${room.name}"
    room.deviceIds.each{deviceIdAsInteger ->
      results[deviceIdAsInteger.toString()] = room.name ?: 'UNKNOWN'
    }
  }
//--DEBUG--  paragraph "DEBUG XXX: ${results}"
  return results
}


// ---------------------------------------------------------------------------
// THIS FUNCTION DOES NOT CURRENTLY HANDLE DEVICES THAT HAVE NO ENCLOSING ROOM
// ---------------------------------------------------------------------------
List<DeviceWrapper> getDevicesForRoom (String room, DeviceWrapperList devices) {
  List<Integer> deviceIds = deviceIdsForRoom(room)
  return devices.findAll{ deviceIds.contains(it.id) }
}
