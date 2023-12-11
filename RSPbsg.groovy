// ---------------------------------------------------------------------------------
// R O O M   S C E N E   P B S G
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
#include wesmc.lFifo
#include wesmc.lHExt
#include wesmc.lHUI
#include wesmc.lPbsg

definition (
  parent: 'wesmc:RoomScenes',
  name: 'RSPbsg',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'A pbsgLibrary instance rooted in a Room Scenes instance',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  installOnOpen: false,
  documentationLink: '',  // TBD
  videoLink: '',          // TBD
  importUrl: '',          // TBD
  oauth: false,           // Even if used, must be manually enabled.
  singleInstance: false
)

preferences {
  page(name: 'RSPbsgPage')
}

////-----------------------------------------------------------------------
//// EXTERNAL-FACING METHODS
////   Boolean pbsgConfigure (
////     List<String> buttons,
////     String defaultButton,
////     String activeButton,
////     String pbsgLogLevel = 'TRACE'
////   )
////
////   Boolean pbsgActivateButton (String button)
////
////   Boolean pbsgDeactivateButton (String button)
////
////   Boolean pbsgActivatePrior ()
////
//// PARENT CALLBACK
////   void buttonOnCallback (String button)
////
////xx PUBLISHED EVENT
////xx   Map event = [
////xx     name: 'PbsgActiveButton',                                 String
////xx     descriptionText: "Button <activeButton> is active",       String
////xx     value: [
////xx         'active': activeButton,                               String
////xx       'inactive': inactiveButtonFifo,                   List<String>
////xx           'dflt': defaultButton                               String
////-----------------------------------------------------------------------

//---- SYSTEM CALLBACKS

void installed () {
  pbsgCoreInstalled(app)
}

void updated () {
  pbsgCoreUpdated(app)
}

void uninstalled () {
  pbsgCoreUninstalled(app)
}

//---- RENDERING AND DISPLAY

Map RSPbsgPage () {
  return dynamicPage(
    name: 'RSPbsgPage',
    title: Heading1(AppInfo(app)),
    install: true,
    uninstall: true,
  ) {
    section {
      paragraph([
        Heading1('Debug'),
        *appStateAsBullets(true)
      ].join('<br/>'))
    }
  }
}
