// ---------------------------------------------------------------------------------
// P L A Y G R O U N D
//
// Copyright (C) 2023-Present Wesley M. Conner
//
// LICENSE
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
// not use this file except in compliance with the License. Unless
// required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// ---------------------------------------------------------------------------------

import com.hubitat.hub.domain.Event as Event

// The Groovy Linter generates NglParseError on Hubitat #include !!!
#include wesmc.lHExt
#include wesmc.lHUI

definition (
  name: 'Playground',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Test Concepts',
  singleInstance: true,
  iconUrl: '',
  iconX2Url: ''
)

preferences {
  page(name: 'PlaygroundPage')
}

Map PlaygroundPage() {
  return dynamicPage(
    name: 'PlaygroundPage',
    title: [
      heading1("PlaygroundPage - ${app.id}"),
      bullet1('Click <b>Done</b> to enable subscriptions.')
    ].join('<br/>'),
    install: true,
    uninstall: true
  ) {
    //---------------------------------------------------------------------------------
    // REMOVE NO LONGER USED SETTINGS AND STATE
    //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    //-> app.removeSetting('..')
    //-> state.remove('..')
    //---------------------------------------------------------------------------------
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      state.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
    }
  }
}

void installed() {
  logWarn('installed', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void uninstalled() {
  logWarn('uninstalled', 'Entered')
  removeAllChildApps()
}

void updated() {
  logWarn('updated', 'Entered')
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void initialize() {
  ArrayList data = ['a', 'b', 'c', 'd']
  Map fifo = [
      Push: { list, item -> list.push(item) },
       Pop: { list -> list.pop() },
      Find: { list, target ->
              String found = null
              list.eachWithIndex { e, i ->
                if (e == target) { found = list.getAt(i) }
              }
              return found
            },
  Contains: { list, target -> list.contains(target) },
    Remove: { list, target ->
              Integer foundIndex = null
              list.eachWithIndex { e, i ->
                if (e == target) { foundIndex = i }
              }
              return (foundIndex != null) ? list.removeAt(foundIndex) : null
            }
  ]
  logInfo('A', "data: ${data}")

  String s1 = fifo.Pop(data)
  logInfo('B', "data: ${data}, s1: ${s1}")

  fifo.Push(data, 'q')
  logInfo('C', "data: ${data}")

  logInfo('C1', "${fifo.Contains(data, 'q')}")

  String s2 = fifo.Find(data, 'c')
  logInfo('D', "data: ${data}, s2: ${s2}")

  String s3 = fifo.Remove(data, 'b')
  logInfo('E', "data: ${data}, s3: ${s3}")

  logInfo('F', fifo.Reflect())
}
