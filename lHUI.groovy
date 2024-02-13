// ---------------------------------------------------------------------------------
// U T I L S   L I B R A R Y
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
import com.hubitat.hub.domain.Hub as Hub

library (
 name: 'lHUI',
 namespace: 'wesmc',
 author: 'WesleyMConner',
 description: 'Log-level filtering AND application display enrichment for Hubitat',
 category: 'general purpose',
 documentationLink: 'TBD',
 importUrl: 'TBD'
)

//---- GENERAL PURPOSE

List<String> CleanStrings (List<String> list) {
  // Prune nulls, empty strings and dups
  return list.findAll{ it ?: null }.unique()
}

//---- HTML FORMATTING
//----   -- Focused on Hubitat contexts

String BLACKBAR() { return '<hr style="border: 5px solid black;"/>' }
String GREENBAR() { return '<hr style="border: 5px solid green;"/>' }
String REDBAR() { return '<hr style="border: 5px solid red;"/>' }

String Heading1 (String s) {
  return """<span style='font-size: 2em; font-family: Roboto; font-weight: bold;'>${s}</span>"""
}

String Heading2 (String s) {
  return """<span style='font-size: 1.2em; font-family: Roboto; font-weight: bold;'>${s}</span>"""
}

String Heading3 (String s) {
  return """<span style='font-size: 1.1em; font-family: Roboto; font-weight: bold;'>${s}</span>"""
}

String Bullet1 (String s) {
  return "&#x2022;&nbsp;&nbsp;${s}"
}

String Bullet2 (String s) {
  return "&nbsp;&nbsp;&nbsp;&#x2022;&nbsp;&nbsp;${s}"
}

String b (def val) {
  String retVal = '<b>null</b>'
  if (val == '0') retVal = "'<b>0</b>'"
  else if (val == 0) retVal = "'<b>0</b>'"
  else if (val) retVal = "'<b>${val}</b>'"
  return retVal
}

String i (def val) {
  return val ? "'<i>${val}</i>'" : "<i>null</i>"
}

//---- HTML TABLES
//----   -- Focused on Hubitat contexts

String tdLft (def x) {
  return "<td style='text-align: left; padding-left: 10px; padding-right: 10px;'>${x}</td>"
}

String tdCtr (def x, String css = null) {
  return "<td style='text-align: center; padding-left: 10px; padding-right: 10px; ${css}'>${x}</td>"
}

String tdRght (def x) {
  return "<td style='text-align: right; padding-left: 10px; padding-right: 10px;'>${x}</td>"
}

//---- LOGGING
//----   - Adjust log levels to reduce noise and improve performance

void solicitLogThreshold (String settingsKey, String dfltThresh = 'TRACE') {
  // By passing in the settings key, clients can:
  //   - Specify their choice of settings key.
  //   - Solicit two differentiate keys (e.g., App's level vs child PBSG's level)
  input (
    name: settingsKey,
    type: 'enum',
    title: Heading2("Select ${settingsKey}"),
    options: ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'],
    defaultValue: dfltThresh,
    submitOnChange: true
  )
}

Integer LogThreshToLogLevel (String logThresh) {
  Integer retval
  switch(logThresh ?: 'TRACE') {
    case 'TRACE':
      retval = 5
      break
    case 'DEBUG':
      retval = 4
      break
    case 'INFO':
      retval = 3
      break
    case 'WARN':
      retval = 2
      break
    case 'ERROR':
      retval = 1
      break
    default:
      log.error("${AppInfo(app)} LogThreshToLogLevel() bad logThresh: ${b(logThresh)}")
      retval = 5
  }
}

void Lerror (String fnName, String s) {
  // No conditional test ensures errors appear.
  log.error(
    "${AppInfo(app)} <b>${fnName}</b> → ${s}"
  )
}

void Lwarn (String fnName, String s) {
  // Fail closed if logLevel is missing.
  if ((state.logLevel ?: 5) > 1) {
    log.warn("${AppInfo(app)} <b>${fnName}</b> → ${s}")
  }
}

void Linfo (String fnName, String s) {
  // Fail closed if logLevel is missing.
  if ((state.logLevel ?: 5) > 2) {
    log.info("${AppInfo(app)} <b>${fnName}</b> → ${s}")
  }
}

void Ldebug (String fnName, String s) {
  // Fail closed if logLevel is missing.
  if ((state.logLevel ?: 5) > 3) {
    log.debug("${AppInfo(app)} <b>${fnName}</b> → ${s}")
  }
}

void Ltrace (String fnName, String s) {
  // Fail closed if logLevel is missing.
  if ((state.logLevel ?: 5) > 4) {
    log.trace("${AppInfo(app)} <b>${fnName}</b> → ${s}")
  }
}

void Lerror (String fnName, List<String> ls, String delim = '<br/>&nbsp&nbsp') {
  Lerror(fnName, ls.join(delim))
}

void Lwarn (String fnName, List<String> ls, String delim = '<br/>&nbsp&nbsp') {
  Lwarn(fnName, ls.join(delim))
}

void Linfo (String fnName, List<String> ls, String delim = '<br/>&nbsp&nbsp') {
  Linfo(fnName, ls.join(delim))
}

void Ldebug (String fnName, List<String> ls, String delim = '<br/>&nbsp&nbsp') {
  Ldebug(fnName, ls.join(delim))
}

void Ltrace (String fnName, List<String> ls, String delim = '<br/>&nbsp&nbsp') {
  Ltrace(fnName, ls.join(delim))
}

//---- Convenience
//----   - Simplify debugging

String AppInfo (InstAppW app) {
  return "${app?.getLabel() ?: 'MISSING_LABEL'} (${app?.id ?: 'MISSING_ID'})"
}

String DeviceInfo (def device) {
  // Design Note:
  //   - The parameter is passed as 'def' in lieu of 'DevW'.
  //   - When devices are used from a LinkedHashMap (e.g., settings, state),
  //     the original DevW type is lost - resulting in method call fail that
  //     reports a type mismatch.
  return device ? "${device.displayName} (${device.id})" : null
}

String EventDetails (Event e, Boolean DEEP = false) {
  String rows = """
    <tr>
      <th align='right'>descriptionText</th>
      <td>${e.descriptionText}</td>
    </tr>
    <tr>
      <th align='right'>displayName</th>
      <td>${e.displayName}</td>
    </tr>
    <tr>
      <th align='right'>deviceId</th>
      <td>${e.deviceId}</td>
    </tr>
    <tr>
      <th align='right'>name</th>
      <td>${e.name}</td>
    </tr>
    <tr>
      <th align='right'>value</th>
      <td>${e.value}</td>
    </tr>
    <tr>
      <th align='right'>isStateChange</th>
      <td>${e.isStateChange}</td>
    </tr>
    """
  if (DEEP) rows += """
    <tr>
      <th align='right'>date</th>
      <td>${e.date}</td>
    </tr>
    <tr>
      <th align='right'>class</th>
      <td>${e.class}</td>
    </tr>
    <tr>
      <th align='right'>unixTime</th>
      <td>${e.unixTime}</td>
    </tr>"""
  return "<table>${rows}</table>"
}

List<String> appStateAsBullets (Boolean includeHeading = false) {
  List<String> result = []
  if (includeHeading) result += Heading2("${AppInfo(app)} STATE")
  state.sort().each{ k, v ->
    result += Bullet2("<b>${k}</b> → ${v}")
  }
  return result.size() != 0 ? result : [ Heading2('NO STATE DATA AVAILABLE') ]
}

List<String> appSettingsAsBullets (Boolean includeHeading = false) {
  List<String> result = []
  if (includeHeading) result += Heading2("${AppInfo(app)} SETTINGS")
  settings.sort().each{ k, v ->
    result += Bullet1("<b>${k}</b> → ${v}")
  }
  return result.size() != 0 ? result : [ Heading2('<i>NO SETTINGS DATA AVAILABLE</i>') ]
}
