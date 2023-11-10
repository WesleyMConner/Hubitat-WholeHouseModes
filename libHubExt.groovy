// ---------------------------------------------------------------------------------
// H U B I T A T   E X T E N S I O N   M E T H O D S
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
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event

library (
 name: 'libHubExt',
 namespace: 'wesmc',
 author: 'WesleyMConner',
 description: 'General-Purpose Methods that are Reusable across Hubitat Projects',
 category: 'general purpose',
 documentationLink: 'https://github.com/WesleyMConner/Hubitat-libHubExt/README.adoc',
 importUrl: 'https://github.com/WesleyMConner/Hubitat-libHubExt.git'
)

//----
//---- CONVENIENCE
//----

List<String> ModeNames () {
  return getLocation().getModes().collect{ it.name }
}

String SwitchState (DevW d) {
  List<String> stateValues = d.collect({ it.currentStates.value }).flatten()
  return stateValues.contains('on')
      ? 'on'
      : stateValues.contains('off')
        ? 'off'
        : 'unknown'
}

//----
//---- APP MANAGEMENT
//----

void PruneAppDups (
    List<String> keepLabels,
    Boolean keepLatest,
    InstAppW appBase
  ) {
  // if keepLatest is false, it implies "Keep Oldest"
  Boolean isWarning = false
  List<String> result = []
  result += '<table>'
  result += '<tr><th><u>LABEL</u></th><th><u>ID</u></th><th><u>DEVICES</u></th><th><u>ACTION</u></th></tr>'
  appBase.getAllChildApps()?.groupBy{ it.getLabel() }.each{ label, apps ->
    Boolean isOrphan = keepLabels.findIndexOf{ it == label } == -1
    apps.eachWithIndex{ a, index ->
      Boolean isDup = index > 0
      if (isOrphan) {
        isWarning = true
        result += "<tr>${tdCtr(label)}${tdCtr(a.getId())}${tdCtr(a.getChildDevices().size())}${tdCtr('DELETED ORPHAN', 'font-weight: bold;')}</tr>"
        appBase.deleteChildApp(a.getId())
      } else if (isDup) {
        isWarning = true
        result += "<tr>${tdCtr(label)}${tdCtr(a.getId())}${tdCtr(a.getChildDevices().size())}${tdCtr('DELETED DUPLICATE', 'font-weight: bold;')}</tr>"
        appBase.deleteChildApp(a.getId())
      } else {
        result += "<tr>${tdCtr(label)}${tdCtr(a.getId())}${tdCtr(a.getChildDevices().size())}${tdCtr('Kept')}</tr>"
      }
    }
  }
  result += '</table>'
  if (isWarning) {
    Lwarn('PruneAppDups()', result.join())
  } else {
    Ltrace('PruneAppDups()', result.join())
  }
}

void RemoveChildApps () {
  getAllChildApps().each{ child ->
    Ldebug(
      'RemoveChildApps()',
      "deleting child: <b>${AppInfo(appObj)}</b>"
    )
    deleteChildApp(child.getId())
  }
}

/*
InstAppW addChildAppForUniqueLabel (
    String definitionNamespace,
    String definitionName,
    String instanceLabel
  ) {
  // Create a new Child App IFF the 'instanceLabel' is not found.
  InstAppW newApp = null
  if (getChildAppByLabel(instanceLabel)) {
    Lerror(
      'addChildAppForUniqueLabel()',
      "The instanceLabel ${b(instanceLabel)} is already in use"
    )
  } else {
    newApp = app.addChildApp(
      definitionNamespace,
      definitionName,
      instanceLabel
    )
  }
  return newApp
}
*/
