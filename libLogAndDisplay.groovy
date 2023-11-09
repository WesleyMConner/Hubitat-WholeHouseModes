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
 name: 'libLogAndDisplay',
 namespace: 'wesmc',
 author: 'WesleyMConner',
 description: 'Log-level filtering AND application display enrichment for Hubitat',
 category: 'general purpose',
 documentationLink: 'TBD',
 importUrl: 'TBD'
)

//----
//---- HTML FORMATTING
//----

String BLUE = 'rgba(51, 92, 255, 1.0)'
String LIGHT_GREY = 'rgba(100, 100, 100, 1.0)'
String RED = 'rgba(255, 0, 0, 1.0)'

String CSS_HIGHLIGHT = 'background-color: yellow; font-weight: bold;'

String Heading1 (String s) {
  return """<span style='font-size: 2em; font-weight: bold;'>${s}</span>"""
}

String Heading2 (String s) {
  return """<span style='font-size: 1em; font-weight: bold;'>${s}</span>"""
}

String Bullet1 (String s) {
  return "&#x2022;&nbsp;&nbsp;${s}"
}

String Bullet2 (String s) {
  return "&nbsp;&nbsp;&nbsp;&#x2022;&nbsp;&nbsp;${s}"
}

String b (def val) {
  return "'<b>val</b>'"
}

//--UNUSED-> String Comment(String s) {
//--UNUSED->   return """<span style="font-size: 0.8em; color: ${LIGHT_GREY}; font-style: italic">${s}</span>"""
//--UNUSED-> }

//--UNUSED-> String red(String s) {
//--UNUSED->   return """<span style="color: ${RED}; font-style: bold">${s}</span>"""
//--UNUSED-> }

//----
//---- LOGGING
//----

void lookupLogLevel (String logThreshold) {
  Integer
  switch(logThreshold ?: 'TRACE') {
    case 'TRACE':
      atomicState.logLevel = 5
      break
    case 'DEBUG':
      atomicState.logLevel = 4
      break
    case 'INFO':
      atomicState.logLevel = 3
      break
    case 'WARN':
      atomicState.logLevel = 2
      break
    case 'ERROR':
      atomicState.logLevel = 1
      break
    default:
      log.error("${GetAppInfo(app)} lookupLogLevel() bad logThreshold: ${b(logThreshold)}")
      atomicState.logLevel = 5
  }
}

void Lerror (String fnName, String s) {
  // No conditional test ensures errors appear.
  log.error(
    "${GetAppInfo(app)} <b>${fnName}</b> → ${s}"
  )
}

void Lwarn (String fnName, String s) {
  // Fail closed if logLevel is missing.
  if ((atomicState.logLevel ?: 5) > 1) {
    log.warn("${GetAppInfo(app)} <b>${fnName}</b> → ${s}")
  }
}

void Linfo (String fnName, String s) {
  // Fail closed if logLevel is missing.
  if ((atomicState.logLevel ?: 5) > 2) {
    log.info("${GetAppInfo(app)} <b>${fnName}</b> → ${s}")
  }
}

void Ldebug (String fnName, String s) {
  // Fail closed if logLevel is missing.
  if ((atomicState.logLevel ?: 5) > 3) {
    log.debug("${GetAppInfo(app)} <b>${fnName}</b> → ${s}")
  }
}

void Ltrace (String fnName, String s) {
  // Fail closed if logLevel is missing.
  if ((atomicState.logLevel ?: 5) > 4) {
    log.trace("${GetAppInfo(app)} <b>${fnName}</b> → ${s}")
  }
}

//----
//---- TABLES
//----

// font-size: 1em; font-weight: normal;

String tdLft (def x) {
  return "<td style='text-align: left; padding-left: 10px; padding-right: 10px;'>${x}</td>"
}

String tdCtr (def x, String css = null) {
  return "<td style='text-align: center; padding-left: 10px; padding-right: 10px; ${css}'>${x}</td>"
}

String tdRght (def x) {
  return "<td style='text-align: right; padding-left: 10px; padding-right: 10px;'>${x}</td>"
}

//----
//---- RENDERING AND DISPLAY
//----   Methods specific to this execution context
//----

void solicitLogThreshold (String settingsKey) {
  // By passing in the settings key, clients can:
  //   - Specify their choice of settings key.
  //   - Solicit two differentiate keys (e.g., App's level vs child PBSG's level)
  input (
    name: settingsKey,
    type: 'enum',
    title: "Set the ${settingsKey}",
    options: ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'],
    defaultValue: 'TRACE',
    submitOnChange: true
  )
}
