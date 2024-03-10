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

library(
 name: 'lHExt',
 namespace: 'wesmc',
 author: 'WesleyMConner',
 description: 'General-Purpose Methods that are Reusable across Hubitat Projects',
 category: 'general purpose',
 documentationLink: 'https://github.com/WesleyMConner/Hubitat-lHExt/README.adoc',
 importUrl: 'https://github.com/WesleyMConner/Hubitat-lHExt.git'
)

//----
//---- CONVENIENCE
//----

void stateRemoveAndLog (String stateKey) {
  if (state."${stateKey}") {
    logInfo('stateRemoveAndLog', "Removing stale state key >${stateKey}")
    //state.remove(stateKey)
  }
}

void settingsRemoveAndLog (String settingKey) {
  if (settings."${settingKey}") {
    logInfo('settingsRemoveAndLog', "Removing stale setting >${settingKey}")
    //app.removeSetting(settingKey)
  }
}

Integer safeParseInt(String s) {
  return (s == '0') ? 0 : s.toInteger()
}

void removeAllChildApps() {
  getAllChildApps().each { child ->
    logWarn(
      'removeAllChildApps',
      "child: >${child.id}< >${child.label}<"
    )
    deleteChildApp(child.id)
  }
}

Map<String, ArrayList<String>> compareLists(ArrayList<String> existing, ArrayList<String> revised) {
  // Produces Button Lists for Map keys 'retained', 'dropped' and 'added'.
  Map<String, ArrayList<String>> map = [:]
  if (!existing) {
    map.added = revised.collect()
  } else if (!revised) {
    map.retained = existing.collect()
  } else {
    map.retained = existing.collect()
    map.retained.retainAll(revised)
    map.dropped = existing.collect()
    map.dropped.removeAll(revised)
    map.added = revised.collect()
    map.added.removeAll(existing)
  }
  return map
}

ArrayList<String> modeNames() {
  //return getLocation().getModes().collect { modeObj -> modeObj.name }
  return getLocation().getModes()*.name
}

String switchState(DevW d) {
  /* groovylint-disable-next-line UseCollectMany */
  ArrayList<String> stateValues = d.collect { device -> device.currentStates.value }.flatten()
  return stateValues?.contains('on')
      ? 'on'
      : stateValues?.contains('off')
        ? 'off'
        : 'unknown'
}

String showSwitchAndState(String name, String state) {
  String adjustedState = state ?: 'unk'
  String emphasizedState = state == 'on' ? "${b(adjustedState)}" : "<i>${adjustedState}</i>"
  return "→ ${emphasizedState} - ${name}"
}

//----
//---- NESTED STATE MAP OPERATIONS
//----   Extend and mimic the following built-in method.
//----     state.updateMapValue(stateKey, innerKey, value)
//----       stateKey - isolates the User Map inside State
//----       innerKey - isolates an Entry in that User Map
//----       value is - the new value to associate with that key
//----     asUpdateMapValue() - Local shorthand wrapper
//----

/*
void asUpdateMapValue(String stateKey, String innerKey, def value) {
  state.updateMapValue(stateKey, innerKey, value)
}

void asGetMapValue(String stateKey, String innerKey) {
  state.stateKey.
}

void asRemoveMapKey(String stateKey, String innerKey)
  state.updateMapValue(stateKey, innerKey, value)
*/

//----
//---- APP MANAGEMENT
//----

void pruneAppDups(ArrayList<String> keepLabels, InstAppW appBase) {
  // if keepLatest is false, it implies "Keep Oldest"
  ArrayList<String> result = []
  result += '<table>'
  result += '<tr><th><u>LABEL</u></th><th><u>ID</u></th><th><u>DEVICES</u></th><th><u>ACTION</u></th></tr>'
  appBase.getAllChildApps()
    ?.groupBy { String appLabel -> appLabel.label }
    .each { label, apps ->
      Boolean isOrphan = keepLabels.findIndexOf { String appLabel -> appLabel == label } == -1
      apps.eachWithIndex { a, index ->
        Boolean isDup = index > 0
        if (isOrphan) {
          isWarning = true
          result += "<tr>${tdCtr(label)}${tdCtr(a.id)}${tdCtr(a.getChildDevices().size())}"
            + "${tdCtr('DELETED ORPHAN', 'font-weight: bold;')}</tr>"
          appBase.deleteChildApp(a.id)
        } else if (isDup) {
          isWarning = true
          result += """<tr>
            ${tdCtr(label)}${tdCtr(a.id)}${tdCtr(a.getChildDevices().size())}
            /* groovylint-disable-next-line DuplicateStringLiteral */
            ${tdCtr('DELETED DUPLICATE', 'font-weight: bold;')}
          </tr>""".stripMargin().stripIndent()
          appBase.deleteChildApp(a.id)
        } else {
          result += "<tr>${tdCtr(label)}${tdCtr(a.id)}${tdCtr(a.getChildDevices().size())}${tdCtr('Kept')}</tr>"
        }
      }
    }
  result += '</table>'
}

void removeChildApps() {
  getAllChildApps().each { child ->
    logDebug(
      'removeChildApps',
      "deleting child: ${b(appInfo(appObj))}"
    )
    deleteChildApp(child.id)
  }
}
