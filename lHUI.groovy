// ---------------------------------------------------------------------------------
// H ( U B I T A T )   U ( S E R )   I ( N T E R F A C E )
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

library(
 name: 'lHUI',
 namespace: 'wesmc',
 author: 'WesleyMConner',
 description: 'Log-level filtering AND application display enrichment for Hubitat',
 category: 'general purpose',
 documentationLink: 'TBD',
 importUrl: 'TBD'
)

//---- GENERAL PURPOSE

ArrayList cleanStrings(ArrayList list) {
  // Prune nulls, empty strings and dups
  return list.findAll { s -> s ?: null }.unique()
}

//---- HTML FORMATTING
//----   -- Focused on Hubitat contexts

String blackBar() { return '<hr style="border: 5px solid black;"/>' }
String greenBar() { return '<hr style="border: 5px solid green;"/>' }
String redBar() { return '<hr style="border: 5px solid red;"/>' }

String heading1(String s) {
  return """<span style='font-size: 2em; font-family: Roboto; font-weight: bold;'>${s}</span>"""
}

String heading2(String s) {
  return """<span style='font-size: 1.2em; font-family: Roboto; font-weight: bold;'>${s}</span>"""
}

String heading3(String s) {
  return """<span style='font-size: 1.1em; font-family: Roboto; font-weight: bold;'>${s}</span>"""
}

String bullet1(String s) {
  return "&#x2022;&nbsp;&nbsp;${s}"
}

String bullet2(String s) {
  return "&nbsp;&nbsp;&nbsp;&#x2022;&nbsp;&nbsp;${s}"
}

String b(def val) {
  String retVal = '<b>null</b>'
  if (val == '0') {
    retVal = "'<b>0</b>'"
  } else if (val == 0) {
    retVal = "'<b>0</b>'"
  } else if (val) {
    retVal = "'<b>${val}</b>'"
  }
  return retVal
}

String i(def val) {
  return val ? "'<i>${val}</i>'" : '<i>null</i>'
}

//---- HTML TABLES
//----   -- Focused on Hubitat contexts

String tdLft(def x) {
  return "<td style='text-align: left; padding-left: 10px; padding-right: 10px;'>${x}</td>"
}

String tdCtr(def x, String css = null) {
  return "<td style='text-align: center; padding-left: 10px; padding-right: 10px; ${css}'>${x}</td>"
}

String tdRght(def x) {
  return "<td style='text-align: right; padding-left: 10px; padding-right: 10px;'>${x}</td>"
}

//---- LOGGING
//----   - Adjust log levels to reduce noise and improve performance

void solicitLogThreshold(String settingsKey, String dfltThresh) {
  // By passing in the settings key, clients can:
  //   - Specify their choice of settings key.
  //   - Solicit two differentiate keys (e.g., App's level vs child PBSG's level)
  input(
    name: settingsKey,
    type: 'enum',
    title: heading2("Select ${settingsKey}"),
    options: ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'],
    defaultValue: dfltThresh,
    submitOnChange: true
  )
}

Integer logThreshToLogLevel(String logThresh) {
  Integer retval
  switch (logThresh ?: 'TRACE') {
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
      log.error("${appInfo(app)} logThreshToLogLevel() bad logThresh: ${b(logThresh)}")
      retval = 5
  }
  return retval
}

void logError(String fnName, String s) {
  // No conditional test ensures errors appear.
  log.error(
    "${appInfo(app)} <b>${fnName}</b> → ${s}"
  )
}

void logWarn(String fnName, String s) {
  // Fail closed if logLevel is missing.
  if ((state.logLevel ?: 5) > 1) {
    log.warn("${appInfo(app)} <b>${fnName}</b> → ${s}")
  }
}

void logInfo(String fnName, String s) {
  // Fail closed if logLevel is missing.
  if ((state.logLevel ?: 5) > 2) {
    log.info("${appInfo(app)} <b>${fnName}</b> → ${s}")
  }
}

void logDebug(String fnName, String s) {
  // Fail closed if logLevel is missing.
  if ((state.logLevel ?: 5) > 3) {
    log.debug("${appInfo(app)} <b>${fnName}</b> → ${s}")
  }
}

void logTrace(String fnName, String s) {
  // Fail closed if logLevel is missing.
  if ((state.logLevel ?: 5) > 4) {
    log.trace("${appInfo(app)} <b>${fnName}</b> → ${s}")
  }
}

void logError(String fnName, ArrayList ls, String delim = '<br/>&nbsp&nbsp') {
  logError(fnName, ls.join(delim))
}

void logWarn(String fnName, ArrayList ls, String delim = '<br/>&nbsp&nbsp') {
  logWarn(fnName, ls.join(delim))
}

void logInfo(String fnName, ArrayList ls, String delim = '<br/>&nbsp&nbsp') {
  logInfo(fnName, ls.join(delim))
}

void logDebug(String fnName, ArrayList ls, String delim = '<br/>&nbsp&nbsp') {
  logDebug(fnName, ls.join(delim))
}

void logTrace(String fnName, ArrayList ls, String delim = '<br/>&nbsp&nbsp') {
  logTrace(fnName, ls.join(delim))
}

//---- Convenience
//----   - Simplify debugging

String appInfo(InstAppW app) {
  return "${app?.label ?: 'MISSING_LABEL'} (${app?.id ?: 'MISSING_ID'})"
}

String deviceInfo(def device) {
  // Design Note:
  //   - The parameter is passed as 'def' in lieu of 'DevW'.
  //   - When devices are used from a LinkedHashMap (e.g., settings, state),
  //     the original DevW type is lost - resulting in method call fail that
  //     reports a type mismatch.
  return device ? "${device.displayName} (${device.id})" : null
}

String eventDetails(Event e) {
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
      <td>${e.deviceId} (hubitat)</td>
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
  return "<table>${rows}</table>"
}

ArrayList appStateAsBullets(Boolean includeHeading = false) {
  ArrayList result = []
  if (includeHeading) { result += heading2("${appInfo(app)} STATE") }
  state.sort().each { k, v ->
    result += bullet2("<b>${k}</b> → ${v}")
  }
  return result.size() == 0 ? [ heading2('NO STATE DATA AVAILABLE') ] : result
}

ArrayList appSettingsAsBullets(Boolean includeHeading = false) {
  ArrayList result = []
  if (includeHeading) { result += heading2("${appInfo(app)} SETTINGS") }
  settings.sort().each { k, v ->
    result += bullet1("<b>${k}</b> → ${v}")
  }
  return result.size() == 0 ? [ heading2('<i>NO SETTINGS DATA AVAILABLE</i>') ] : result
}
