// ---------------------------------------------------------------------------------
// P B S G   -   P U S H B U T T O N   S W I T C H   A P P
//
//   Copyright (C) 2023-Present Wesley M. Conner
//
//   LICENSE
//     Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
//     "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
//     not use this file except in compliance with the License. Unless
//     required by applicable law or agreed to in writing, software
//     distributed under the License is distributed on an "AS IS" BASIS,
//     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
//     implied.
// ---------------------------------------------------------------------------------
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Location as Loc
#include wesmc.libLogAndDisplay
#include wesmc.libUtils
// FIX-004 Create one 'Library Instantiation App' per prospective Parent App.
// FIX-004-A #include your library
// FIX-004-B Clone your original definition section
//   - adding a parent key
//   - modifying the name to something unique
//   - adjust the description
// FIX-004-C Clone your original preferences section

definition(
  parent: 'wesmc:wha',
  name: 'pbsgRoomScene',
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'An instantiated PBSG App (from libPbsg) living under a specific parent',
  category: '',
  iconUrl: '',
  iconX2Url: '',
  iconX3Url: '',
  singleInstance: false
)

preferences {
  page(name: 'pbsgPage')
}
