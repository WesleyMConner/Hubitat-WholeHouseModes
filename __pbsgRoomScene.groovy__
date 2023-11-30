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
#include wesmc.libHubUI
#include wesmc.libHubExt
#include wesmc.libPbsg

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
  page(name: 'pbsgRoomScenePage')
}

Map pbsgRoomScenePage () {
  return dynamicPage (
    name: 'pbsgRoomScenePage',
    install: true,
    uninstall: false
  ) {
    defaultPage()
  }
}