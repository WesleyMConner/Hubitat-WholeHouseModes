// ------------------------------------------------------------------------
// D E V I C E   L I B R A R Y
//   Copyright (C) 2023-Present Wesley M. Conner
//   Licensed under the Apache License, Version 2.0
//   http://www.apache.org/licenses/LICENSE-2.0
// ------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.DevWL as DevWL
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Hub as Hub

library (
 name: 'DeviceLibrary',
 namespace: 'wesmc',
 author: 'WesleyMConner',
 description: 'Methods extending Hubitat DevWL and DevWs',
 category: 'general purpose',
 documentationLink: '',
 importUrl: ''
)

String deviceTag(DevW device) {
  return "${device.displayName} (${device.id})"
}



// List<String> deviceIdsForRoom (String room) {
//   return app.getRooms().findAll{it.name == room}.collect{it.deviceIds}.flatten().collect{it.toString()}
// }

