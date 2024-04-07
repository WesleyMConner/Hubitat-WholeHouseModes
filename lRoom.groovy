// ---------------------------------------------------------------------------------
// R O O M
//
// Copyright (C) 2023-Present Wesley M. Conner
//
// LICENSE
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
// not use this file except in compliance with the License. Unless
// required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// ---------------------------------------------------------------------------------
// Referenced types below
//   - import com.hubitat.app.DeviceWrapper as DevW
//   - import com.hubitat.hub.domain.Event as Event
// The following are required when using this library.
//   - #include wesmc.lHExt
//   - #include wesmc.lHUI

library(
  name: 'lRoom',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Room Implementation',
  category: 'general purpose'
)

// The psuedo-class "roomStore" facilitates concurrent storage of multiple
// "room" psuedo-class instances.

Map roomStore_Retrieve(String roomName) {
  // Retrieve a "room" psuedo-class instance.
  Map store = state.roomStore ?: [:]
  return store."${roomName}"
}

void roomStore_Save(Map room) {
  // Add/update a "room" psuedo-class instance.
  Map store = state.roomStore ?: [:]
  store."${room.name}" = room
  state.roomStore = store
}