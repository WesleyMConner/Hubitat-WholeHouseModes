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

String deviceIdToRoom (String deviceId) {
  // Constantly re-pulling app.getRooms() may be slow, but it avoids
  // stale data and brings any consistency issues "to the fore".
  Map<String, String> deviceIdToRoomName = app.getRooms().collectEntries{
    roomObj -> roomObj.deviceIds.each{
      dId -> [ dId, roomObj.name ]
    }
  }
  return deviceIdToRoomName[deviceId] ?: "UNKNOWN"
}

// groovy.lang.MissingMethodException:
// No signature of method:
//   user_app_wesmc_WholeHouseAutomation_332$_deviceIdToRoom_closure16.doCall()
// is applicable for argument types: (java.util.LinkedHashMap)
// values: [[id:1, name:Control, deviceIds:[5536, 3, 5165, 5614, 5615, 5232, ...]]]
// Possible solutions: doCall(java.lang.Object, java.lang.Object),
//   findAll(), findAll(), isCase(java.lang.Object), isCase(java.lang.Object)
// on line 349 (method monoPage) (library wesmc.DeviceLibrary, line 29)

// Map<String, String> mapDeviceIdAsStringToRoomName () {
//   // This function DOES NOT help with missing/unknown room assignments.
//   Map<String, String> results = [:]
//   app.getRooms().each{room ->
//     log.trace "#29 Room ${room.name}"
//     room.deviceIds.each{deviceIdAsInteger ->
//       results[deviceIdAsInteger.toString()] = room.name ?: 'UNKNOWN'
//     }
//   }
//   return results
// }


// ---------------------------------------------------------------------------
// THIS FUNCTION DOES NOT CURRENTLY HANDLE DEVICES THAT HAVE NO ENCLOSING ROOM
// ---------------------------------------------------------------------------
List<DeviceWrapper> getDevicesForRoom (String room, DeviceWrapperList devices) {
  return devices.findAll{ device -> deviceIdToRoom(device.id) == room }
}
