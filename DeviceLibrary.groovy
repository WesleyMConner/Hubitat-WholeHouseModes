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
 name: 'DeviceLibrary',
 namespace: 'wesmc',
 author: 'WesleyMConner',
 description: 'Methods extending Hubitat DeviceWrapperList and DeviceWrappers',
 category: 'general purpose',
 documentationLink: '',
 importUrl: ''
)

String deviceTag(DeviceWrapper device) {
  return "${device.displayName} (${device.id})"
}



// List<String> deviceIdsForRoom (String room) {
//   return app.getRooms().findAll{it.name == room}.collect{it.deviceIds}.flatten().collect{it.toString()}
// }

// List<DeviceWrapper> getDevicesForRoom (String room, DeviceWrapperList devices) {
//   // This function excludes devices that are not associated with any room.
//   List<String> deviceIdsForRoom = app.getRooms()
//                                   .findAll{it.name == room}
//                                   .collect{it.deviceIds.collect{it.toString()}}
//                                   .flatten()
//   return devices.findAll{ deviceIdsForRoom.contains(it.id.toString()) }
// }