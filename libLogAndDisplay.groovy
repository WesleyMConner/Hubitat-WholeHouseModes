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

String heading1(String s) {
  return """<span style='font-size: 2em; font-weight: bold;'>${s}</span>"""
}

String heading2(String s) {
  return """<span style='font-size: 1em; font-weight: bold;'>${s}</span>"""
}

String bullet1(String s) {
  return "&#x2022;&nbsp;&nbsp;${s}"
}

String bullet2(String s) {
  return "&nbsp;&nbsp;&nbsp;&#x2022;&nbsp;&nbsp;${s}"
}

String comment(String s) {
  return """<span style="font-size: 0.8em; color: ${LIGHT_GREY}; font-style: italic">${s}</span>"""
}

String red(String s) {
  return """<span style="color: ${RED}; font-style: bold">${s}</span>"""
}

//----
//---- LOGGING
//----

void lookupLogLevel (String logThreshold) {
  Integer
  switch(logThreshold) {
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
    default:   // 'ERROR'
      atomicState.logLevel = 1
  }
}

void Lerror (String fnName, String s) {
  // No conditional test to ensure all errors appear.
  log.error(
    "${getAppInfo(app)} <b>${fnName}</b> → ${s}"
  )
}

void Lwarn (String fnName, String s) {
  if (atomicState.logLevel > 1) log.warn(
    "${getAppInfo(app)} <b>${fnName}</b> → ${s}"
  )
}

void Linfo (String fnName, String s) {
  if (atomicState.logLevel > 2) log.info(
    "${getAppInfo(app)} <b>${fnName}</b> → ${s}"
  )
}

void Ldebug (String fnName, String s) {
  if (atomicState.logLevel > 3) log.debug(
    "${getAppInfo(app)} <b>${fnName}</b> → ${s}"
  )
}

void Ltrace (String fnName, String s) {
  if (atomicState.logLevel > 4) log.trace(
    "${getAppInfo(app)} <b>${fnName}</b> → ${s}"
  )
}

//----
//---- TABLES
//----

String tdLft (def x) {
  return """<td style="text-align: left; padding-left: 10px; padding-right: 10px;
    font-size: 1em; font-weight: normal;">${x}</td>"""
}

String tdCtr (def x) {
  return """<td style="text-align: center; padding-left: 10px; padding-right: 10px;
    font-size: 1em; font-weight: normal;">${x}</td>"""
}

String tdRght (def x) {
  return """<td style="text-align: right; padding-left: 10px; padding-right: 10px;
    font-size: 1em; font-weight: normal;">${x}</td>"""
}

//----
//---- RENDERING AND DISPLAY
//----   Methods specific to this execution context
//----

void solicitLogThreshold () {
  input (
    name: 'logThreshold',
    type: 'enum',
    title: 'Log Threshold',
    defaultValue: 'DEBUG',
    options: ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'],
    submitOnChange: true
  )
}
