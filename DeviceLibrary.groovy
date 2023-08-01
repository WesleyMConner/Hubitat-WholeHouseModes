// ---------------------------------------------------------------------------------
// D E V I C E   L I B R A R Y
//
//  Copyright (C) 2023-Present Wesley M. Conner
//
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"); you may not use this file except in compliance with the
// License. You may obtain a copy of the License at
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ---------------------------------------------------------------------------------
// Design Notes
//   - Duplication of DeviceWrapper Objects is avoided.
//   - device.id is used as a proxy for a DeviceWrapper Object.
// ---------------------------------------------------------------------------------
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

List<DeviceWrapper> getDevicesForRoom (String room, DeviceWrapperList devices) {
  return deviceIdsForRoom = app.getRooms()
                            .findAll{it.name == room}
                            .collect{it.deviceIds.toString()}
                            .flatten()
}
