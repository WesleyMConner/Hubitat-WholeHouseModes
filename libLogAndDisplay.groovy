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

// -------------
// G L O B A L S
// -------------
String BLACK = 'rgba(0, 0, 0, 1.0)'
String BLUE = 'rgba(51, 92, 255, 1.0)'
String LIGHT_GREY = 'rgba(100, 100, 100, 1.0)'
String RED = 'rgba(255, 0, 0, 1.0)'

// ---------------------------------------
// P A R A G R A P H   F O R M A T T I N G
// ---------------------------------------
String heading(String s) {
  HEADING_CSS = 'font-size: 2em; font-weight: bold;'
  return """<span style="${HEADING_CSS}">${s}</span>"""
}

//-> String important(String s) {
//->   IMPORTANT_CSS = "font-size: 1em; color: ${RED};"
//->   return """<span style="${IMPORTANT_CSS}">${s}</span>"""
//-> }

String emphasis(String s) {
  EMPHASIS_CSS = "font-size: 1.3em; color: ${BLUE}; margin-left: 0px;"
  return """<span style="${EMPHASIS_CSS}">${s}</span>"""
}

//-> String emphasis2(String s) {
//->   EMPHASIS2_CSS = "font-size: 1.1em; color: ${BLUE}; margin-left: 0px;"
//->   return """<span style="${EMPHASIS2_CSS}">${s}</span>"""
//-> }

//-> String normal(String s) {
//->   NORMAL_CSS = 'font-size: 1.1em;'
//->   return """<span style="${NORMAL_CSS}">${s}</span>"""
//-> }

String bullet(String s) {
  BULLET_CSS = 'font-size: 1.0em; margin-left: 10px;'
  return """<span style="${BULLET_CSS}">&#x2022;&nbsp;&nbsp;${s}</span>"""
}

String comment(String s) {
  COMMENT_CSS = "font-size: 0.8em; color: ${LIGHT_GREY}; font-style: italic"
  return """<span style="${COMMENT_CSS}">${s}</span>"""
}

String red(String s) {
  RED_BOLD = "color: ${RED}; font-style: bold"
  return """<span style="${RED_BOLD}">${s}</span>"""
}

// -------------
// L O G G I N G
// -------------

void solicitLogThreshold () {
  input (
    name: 'logThreshold',
    type: 'enum',
    title: 'Log Threshold',
    defaultValue: 'DEBUG',
    options: ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'],
    submitOnChange: true
  )
  if (settings.logThreshold) {
    // Establish defaults
    state.logLevel1Error = true
    state.logLevel2Warn = false
    state.logLevel3Info = false
    state.logLevel4Debug = false
    state.logLevel5Trace = false
    // NOTE: MAKING DELIBERATE USE OF SWITCH "FALL THROUGH" BEHAVIOR!
    switch(settings.logThreshold) {
      case 'TRACE':
        state.logLevel5Trace = true
      case 'DEBUG':
        state.logLevel4Debug = true
      case 'INFO':
        state.logLevel3Info = true
      case 'WARN':
        state.logLevel2Warn = true
    }
  }
}

void Lerror (String fnName, String s) {
  log.error("${app.getLabel()}.${fnName} ${s}")
}

void Lwarn (String fnName, String s) {
  if (state.logLevel2Warn) log.warn("${app.getLabel()}.${fnName} ${s}")
}

void Linfo (String fnName, String s) {
  if (state.logLevel3Info) log.info("${app.getLabel()}.${fnName} ${s}")
}

void Ldebug (String fnName, String s) {
  if (state.logLevel4Debug) log.debug("${app.getLabel()}.${fnName} ${s}")
}

void Ltrace (String fnName, String s) {
  if (state.logLevel5Trace) log.trace("${app.getLabel()}.${fnName} ${s}")
}

