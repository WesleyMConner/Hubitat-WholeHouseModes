// ---------------------------------------------------------------------------------
// ( L U T R O N )   R A 2
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
#include wesmc.lHExt
#include wesmc.lHUI

//library(
//  name: 'lRa2',
//  namespace: 'wesmc',
//  author: 'Wesley M. Conner',
//  description: 'Isolate RA2 Methods',
//  category: 'general purpose'
//)

definition (
  name: 'Test-lRa2',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Preview lRa2 functionality',
  singleInstance: true
)

preferences {
  page(name: 'Ra2Page')
}

/*
Map hubIdToRa2RepData = [
  '6825': [
    type: 'ra2-repeater',
    nativeId: '1',
    buttons: [
      [button: 1, room: 'WHA', scene: 'Chill'],
      [button: 2, room: 'WHA', scene: 'Clean'],
      [button: 3, room: 'WHA', rsvdScene: 'Day', altButton: 5],
      [button: 4, room: 'WHA', scene: 'Night'],
      [button: 5, room: 'WHA', scene: 'Off'],
      [button: 6, room: 'WHA', scene: 'Party'],
      [button: 7, room: 'WHA', scene: 'Supp'],
      [button: 8, room: 'WHA', scene: 'TV'],
      [button: 11, room: 'DenLamp', scene: 'Chill'],
      [button: 12, room: 'DenLamp', scene: 'Clean'],
      [button: 13, room: 'DenLamp', rsvdScene: 'Day', altButton: 15],
      [button: 14, room: 'DenLamp', rsvdScene: 'Night', altButton: 15],
      [button: 15, room: 'DenLamp', scene: 'Off'],
      [button: 16, room: 'DenLamp', scene: 'Party'],
      [button: 17, room: 'DenLamp', scene: 'Supp'],
      [button: 18, room: 'DenLamp', rsvdScene: 'TV', altButton: 15],
      [button: 21, room: 'Kitchen', scene: 'Chill'],
      [button: 22, room: 'Kitchen', scene: 'Clean'],
      [button: 23, room: 'Kitchen', rsvdScene: 'Day', altButton: 25],
      [button: 24, room: 'Kitchen', rsvdScene: 'Night', altButton: 25],
      [button: 25, room: 'Kitchen', scene: 'Off'],
      [button: 26, room: 'Kitchen', scene: 'Party'],
      [button: 27, room: 'Kitchen', scene: 'Supp'],
      [button: 28, room: 'Kitchen', scene: 'TV'],
      [button: 29, room: 'Kitchen', scene: '_Cook'],
      [button: 41, room: 'Den', scene: 'Chill'],
      [button: 42, room: 'Den', scene: 'Clean'],
      [button: 43, room: 'Den', rsvdScene: 'Day', altButton: 45],
      [button: 44, room: 'Den', rsvdScene: 'Night', altButton: 45],
      [button: 45, room: 'Den', scene: 'Off'],
      [button: 46, room: 'Den', scene: 'Party'],
      [button: 47, room: 'Den', scene: 'Supp'],
      [button: 48, room: 'Den', scene: 'TV'],
      [button: 51, room: 'Guest', scene: 'Chill'],
      [button: 52, room: 'Guest', scene: 'Clean'],
      [button: 53, room: 'Guest', rsvdScene: 'Day', altButton: 55],
      [button: 54, room: 'Guest', rsvdScene: 'Night', altButton: 55],
      [button: 55, room: 'Guest', scene: 'Off'],
      [button: 56, room: 'Guest', scene: 'Party'],
      [button: 57, room: 'Guest', scene: 'Supp'],
      [button: 58, room: 'Guest', scene: 'TV'],
      [button: 60, room: 'LhsBath', scene: 'Chill'],
      [button: 62, room: 'LhsBath', scene: 'Clean'],
      [button: 63, room: 'LhsBath', scene: 'Day'],
      [button: 64, room: 'LhsBath', scene: 'Night'],
      [button: 65, room: 'LhsBath', scene: 'Off'],
      [button: 66, room: 'LhsBath', scene: 'Party'],
      [button: 67, room: 'LhsBath', scene: 'Supp'],
      [button: 68, room: 'LhsBath', scene: 'TV'],
      [button: 70, room: 'RhsBath', scene: 'Chill'],
      [button: 72, room: 'RhsBath', scene: 'Clean'],
      [button: 73, room: 'RhsBath', scene: 'Day'],
      [button: 74, room: 'RhsBath', scene: 'Night'],
      [button: 75, room: 'RhsBath', scene: 'Off'],
      [button: 76, room: 'RhsBath', scene: 'Party'],
      [button: 77, room: 'RhsBath', scene: 'Supp'],
      [button: 78, room: 'RhsBath', scene: 'TV'],
      [button: 79, room: 'Main', scene: 'Chill'],
      [button: 82, room: 'Main', rsvdScene: 'Clean', altButton: 85],
      [button: 83, room: 'Main', rsvdScene: 'Day', altButton: 85],
      [button: 84, room: 'Main', scene: 'Night'],
      [button: 85, room: 'Main', scene: 'Off'],
      [button: 86, room: 'Main', scene: 'Party'],
      [button: 87, room: 'Main', scene: 'Supp'],
      [button: 88, room: 'Main', scene: 'TV']
    ]
  ],
  '6892': [
    type: 'ra2-repeater',
    nativeId: '83',
    buttons: [
      [button: 10, room: 'PrimBath', scene: 'Chill'],
      [button: 12, room: 'PrimBath', scene: 'Clean'],
      [button: 13, room: 'PrimBath', rsvdScene: 'Day', altButton: 15],
      [button: 14, room: 'PrimBath', rsvdScene: 'Night', altButton: 15],
      [button: 15, room: 'PrimBath', scene: 'Off'],
      [button: 16, room: 'PrimBath', scene: 'Party'],
      [button: 17, room: 'PrimBath', scene: 'Supp'],
      [button: 18, room: 'PrimBath', scene: 'TV'],
      [button: 21, room: 'Primary', scene: 'Chill'],
      [button: 22, room: 'Primary', scene: 'Clean'],
      [button: 23, room: 'Primary', rsvdScene: 'Day', altButton: 25],
      [button: 24, room: 'Primary', rsvdScene: 'Night', altButton: 25],
      [button: 25, room: 'Primary', scene: 'Off'],
      [button: 26, room: 'Primary', scene: 'Party'],
      [button: 27, room: 'Primary', scene: 'Supp'],
      [button: 28, room: 'Primary', scene: 'TV'],
      [button: 41, room: 'LhsBdrm', scene: 'Chill'],
      [button: 42, room: 'LhsBdrm', scene: 'Clean'],
      [button: 43, room: 'LhsBdrm', rsvdScene: 'Day', altButton: 45],
      [button: 44, room: 'LhsBdrm', rsvdScene: 'Night', altButton: 45],
      [button: 45, room: 'LhsBdrm', scene: 'Off'],
      [button: 46, room: 'LhsBdrm', scene: 'Party'],
      [button: 47, room: 'LhsBdrm', rsvdScene: 'Supp', altButton: 41],
      [button: 48, room: 'LhsBdrm', scene: 'TV'],
      [button: 51, room: 'Office', scene: 'Chill'],
      [button: 52, room: 'Office', scene: 'Clean'],
      [button: 53, room: 'Office', rsvdScene: 'Day', altButton: 55],
      [button: 54, room: 'Office', rsvdScene: 'Night', altButton: 55],
      [button: 55, room: 'Office', scene: 'Off'],
      [button: 56, room: 'Office', scene: 'Party'],
      [button: 57, room: 'Office', scene: 'Supp'],
      [button: 58, room: 'Office', scene: 'TV'],
      [button: 61, room: 'Yard', scene: 'Chill'],
      [button: 62, room: 'Yard', rsvdScene: 'Clean', altButton: 65],
      [button: 63, room: 'Yard', rsvdScene: 'Day', altButton: 65],
      [button: 64, room: 'Yard', scene: 'Night'],
      [button: 65, room: 'Yard', scene: 'Off'],
      [button: 66, room: 'Yard', rsvdScene: 'Party', altButton: 61],
      [button: 67, room: 'Yard', rsvdScene: 'Supp', altButton: 61],
      [button: 68, room: 'Yard', rsvdScene: 'TV', altButton: 61],
      [button: 71, room: 'Lanai', scene: 'Chill'],
      [button: 72, room: 'Lanai', rsvdScene: 'Clean', altButton: 75],
      [button: 73, room: 'Lanai', rsvdScene: 'Day', altButton: 75],
      [button: 74, room: 'Lanai', scene: 'Night'],
      [button: 75, room: 'Lanai', scene: 'Off'],
      [button: 76, room: 'Lanai', scene: 'Party'],
      [button: 77, room: 'Lanai', scene: 'Supp'],
      [button: 78, room: 'Lanai', scene: 'TV'],
      [button: 79, room: 'Lanai', scene: '_Games'],
      [button: 91, room: 'WHA', scene: '_ALARM'],
      [button: 92, room: 'WHA', scene: '_AUTO'],
      [button: 93, room: 'WHA', scene: '_AWAY'],
      [button: 94, room: 'WHA', scene: '_FLASH'],
      [button: 95, room: 'WHA', scene: '_PANIC'],
      [button: 96, room: 'WHA', scene: '_QUIET']
    ]
  ]
]
*/

//----
//---- Initialize Ra2Rep settings. Eventually, these settings should be
//---- populated by parsing an RA2 integration report.
//----

void ra2RepInit() {
  settings['ra2_RepButtonToRS'] = [
    // Map's structure = [
    //   hubDeviceId: [
    //     'buttonLed-##': [room: '', sceneL: '']
    //   ]
    // ]
    'RA2 Repeater 1 (ra2-1)': [
      buttons: [
        'buttonLed-1': [room: 'WHA', scene: 'Chill'],
        'buttonLed-2': [room: 'WHA', scene: 'Clean'],
        'buttonLed-4': [room: 'WHA', scene: 'Night'],
        'buttonLed-5': [room: 'WHA', scene: 'Off'],
        'buttonLed-6': [room: 'WHA', scene: 'Party'],
        'buttonLed-7': [room: 'WHA', scene: 'Supp'],
        'buttonLed-8': [room: 'WHA', scene: 'TV'],
        'buttonLed-11': [room: 'DenLamp', scene: 'Chill'],
        'buttonLed-12': [room: 'DenLamp', scene: 'Clean'],
        'buttonLed-15': [room: 'DenLamp', scene: 'Off'],
        'buttonLed-16': [room: 'DenLamp', scene: 'Party'],
        'buttonLed-17': [room: 'DenLamp', scene: 'Supp'],
        'buttonLed-21': [room: 'Kitchen', scene: 'Chill'],
        'buttonLed-22': [room: 'Kitchen', scene: 'Clean'],
        'buttonLed-25': [room: 'Kitchen', scene: 'Off'],
        'buttonLed-26': [room: 'Kitchen', scene: 'Party'],
        'buttonLed-27': [room: 'Kitchen', scene: 'Supp'],
        'buttonLed-28': [room: 'Kitchen', scene: 'TV'],
        'buttonLed-29': [room: 'Kitchen', scene: '_Cook'],
        'buttonLed-41': [room: 'Den', scene: 'Chill'],
        'buttonLed-42': [room: 'Den', scene: 'Clean'],
        'buttonLed-45': [room: 'Den', scene: 'Off'],
        'buttonLed-46': [room: 'Den', scene: 'Party'],
        'buttonLed-47': [room: 'Den', scene: 'Supp'],
        'buttonLed-48': [room: 'Den', scene: 'TV'],
        'buttonLed-51': [room: 'Guest', scene: 'Chill'],
        'buttonLed-52': [room: 'Guest', scene: 'Clean'],
        'buttonLed-55': [room: 'Guest', scene: 'Off'],
        'buttonLed-56': [room: 'Guest', scene: 'Party'],
        'buttonLed-57': [room: 'Guest', scene: 'Supp'],
        'buttonLed-58': [room: 'Guest', scene: 'TV'],
        'buttonLed-60': [room: 'LhsBath', scene: 'Chill'],
        'buttonLed-62': [room: 'LhsBath', scene: 'Clean'],
        'buttonLed-63': [room: 'LhsBath', scene: 'Day'],
        'buttonLed-64': [room: 'LhsBath', scene: 'Night'],
        'buttonLed-65': [room: 'LhsBath', scene: 'Off'],
        'buttonLed-66': [room: 'LhsBath', scene: 'Party'],
        'buttonLed-67': [room: 'LhsBath', scene: 'Supp'],
        'buttonLed-68': [room: 'LhsBath', scene: 'TV'],
        'buttonLed-70': [room: 'RhsBath', scene: 'Chill'],
        'buttonLed-72': [room: 'RhsBath', scene: 'Clean'],
        'buttonLed-73': [room: 'RhsBath', scene: 'Day'],
        'buttonLed-74': [room: 'RhsBath', scene: 'Night'],
        'buttonLed-75': [room: 'RhsBath', scene: 'Off'],
        'buttonLed-76': [room: 'RhsBath', scene: 'Party'],
        'buttonLed-77': [room: 'RhsBath', scene: 'Supp'],
        'buttonLed-78': [room: 'RhsBath', scene: 'TV'],
        'buttonLed-79': [room: 'Main', scene: 'Chill'],
        'buttonLed-84': [room: 'Main', scene: 'Night'],
        'buttonLed-85': [room: 'Main', scene: 'Off'],
        'buttonLed-86': [room: 'Main', scene: 'Party'],
        'buttonLed-87': [room: 'Main', scene: 'Supp'],
      ]
    ],
    'RA2 Repeater 2 (ra2-83)': [
      buttons: [
        'buttonLed-10': [room: 'PrimBath', scene: 'Chill'],
        'buttonLed-12': [room: 'PrimBath', scene: 'Clean'],
        'buttonLed-15': [room: 'PrimBath', scene: 'Off'],
        'buttonLed-16': [room: 'PrimBath', scene: 'Party'],
        'buttonLed-17': [room: 'PrimBath', scene: 'Supp'],
        'buttonLed-18': [room: 'PrimBath', scene: 'TV'],
        'buttonLed-21': [room: 'Primary', scene: 'Chill'],
        'buttonLed-22': [room: 'Primary', scene: 'Clean'],
        'buttonLed-25': [room: 'Primary', scene: 'Off'],
        'buttonLed-26': [room: 'Primary', scene: 'Party'],
        'buttonLed-27': [room: 'Primary', scene: 'Supp'],
        'buttonLed-28': [room: 'Primary', scene: 'TV'],
        'buttonLed-41': [room: 'LhsBdrm', scene: 'Chill'],
        'buttonLed-42': [room: 'LhsBdrm', scene: 'Clean'],
        'buttonLed-45': [room: 'LhsBdrm', scene: 'Off'],
        'buttonLed-46': [room: 'LhsBdrm', scene: 'Party'],
        'buttonLed-48': [room: 'LhsBdrm', scene: 'TV'],
        'buttonLed-51': [room: 'Office', scene: 'Chill'],
        'buttonLed-52': [room: 'Office', scene: 'Clean'],
        'buttonLed-55': [room: 'Office', scene: 'Off'],
        'buttonLed-56': [room: 'Office', scene: 'Party'],
        'buttonLed-57': [room: 'Office', scene: 'Supp'],
        'buttonLed-58': [room: 'Office', scene: 'TV'],
        'buttonLed-61': [room: 'Yard', scene: 'Chill'],
        'buttonLed-64': [room: 'Yard', scene: 'Night'],
        'buttonLed-65': [room: 'Yard', scene: 'Off'],
        'buttonLed-71': [room: 'Lanai', scene: 'Chill'],
        'buttonLed-74': [room: 'Lanai', scene: 'Night'],
        'buttonLed-75': [room: 'Lanai', scene: 'Off'],
        'buttonLed-76': [room: 'Lanai', scene: 'Party'],
        'buttonLed-77': [room: 'Lanai', scene: 'Supp'],
        'buttonLed-78': [room: 'Lanai', scene: 'TV'],
        'buttonLed-79': [room: 'Lanai', scene: '_Games'],
        'buttonLed-91': [room: 'WHA', scene: '_ALARM'],
        'buttonLed-92': [room: 'WHA', scene: '_AUTO'],
        'buttonLed-93': [room: 'WHA', scene: '_AWAY'],
        'buttonLed-94': [room: 'WHA', scene: '_FLASH'],
        'buttonLed-95': [room: 'WHA', scene: '_PANIC'],
      ]
    ]
  ]
  settings['ra2_RSToRepButton'] = [
    'WHA': [
      'Clean': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 2],
      'Night': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 4],
      'Off': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 5],
      'Party': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 6],
      'Supp': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 7],
      'TV': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 8]
    ],
    'DenLamp': [
      'Chill': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 11],
      'Clean': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 12],
      'Off': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 15],
      'Party': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 16],
      'Supp': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 17]
    ],
    'Kitchen': [
      'Chill': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 21],
      'Clean': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 22],
      'Off': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 25],
      'Party': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 26],
      'Supp': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 27],
      'TV': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 28],
      '_Cook': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 29]
    ],
    'Den': [
      'Chill': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 41],
      'Clean': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 42],
      'Off': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 45],
      'Party': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 46],
      'Supp': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 47],
      'TV': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 48]
    ],
    'Guest': [
      'Chill': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 51],
      'Clean': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 52],
      'Off': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 55],
      'Party': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 56],
      'Supp': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 57],
      'TV': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 58]
    ],
    'LhsBath': [
      'Chill': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 60],
      'Clean': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 62],
      'Day': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 63],
      'Night': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 64],
      'Off': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 65],
      'Party': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 66],
      'Supp': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 67],
      'TV': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 68]
    ],
    'RhsBath': [
      'Chill': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 70],
      'Clean': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 72],
      'Day': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 73],
      'Night': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 74],
      'Off': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 75],
      'Party': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 76],
      'Supp': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 77],
      'TV': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 78]
    ],
    'Main': [
      'Chill': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 79],
      'Night': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 84],
      'Off': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 85],
      'Party': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 86],
      'Supp': [deviceId: 'RA2 Repeater 1 (ra2-1)', button: 87]
    ],
    'PrimBath': [
      'Chill': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 10],
      'Clean': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 12],
      'Off': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 15],
      'Party': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 16],
      'Supp': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 17],
      'TV': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 18]
    ],
    'Primary': [
      'Chill': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 21],
      'Clean': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 22],
      'Off': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 25],
      'Party': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 26],
      'Supp': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 27],
      'TV': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 28]
    ],
    'LhsBdrm' : [
      'Chill': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 41],
      'Clean': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 42],
      'Off': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 45],
      'Party': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 46],
      'TV': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 48]
    ],
    'Office' : [
      'Chill': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 51],
      'Clean': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 52],
      'Off': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 55],
      'Party': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 56],
      'Supp': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 57],
      'TV': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 58]
    ],
    'Yard': [
      'Chill': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 61],
      'Night': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 64],
      'Off': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 65]
    ],
    'Lanai': [
      'Chill': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 71],
      'Night': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 74],
      'Off': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 75],
      'Party': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 76],
      'Supp': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 77],
      'TV': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 78],
      '_Games': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 79]
    ],
    'WHA': [
      '_ALARM': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 91],
      '_AUTO': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 92],
      '_AWAY': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 93],
      '_FLASH': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 94],
      '_PANIC': [deviceId: 'RA2 Repeater 2 (ra2-83)', button: 95]
    ]
  ]
  settings['ra2_Rooms'] = ['WHA', 'DenLamp', 'Kitchen', 'Den', 'Guest',
    'LhsBath', 'RhsBath', 'Main', 'PrimBath', 'Primary', 'LhsBdrm',
    'Office', 'Yard', 'WHA' ]
  settings['ra2_RoomToScene'] = [
    'WHA': ['Chill', 'Clean', 'Night', 'Off', 'Party', 'Supp', 'TV'],
    'DenLamp': ['Chill', 'Clean', 'Off', 'Party', 'Supp'],
    'Kitchen': ['Chill', 'Clean', 'Off', 'Party', 'Supp', 'TV', '_Cook'],
    'Den': ['Chill', 'Clean', 'Off', 'Party', 'Supp', 'TV'],
    'Guest': ['Chill', 'Clean', 'Off', 'Party', 'Supp', 'TV'],
    'LhsBath': ['Chill', 'Clean', 'Day', 'Night', 'Off', 'Party', 'Supp', 'TV'],
    'RhsBath': ['Chill', 'Clean', 'Day', 'Night', 'Off', 'Party', 'Supp', 'TV'],
    'Main': ['Chill', 'Night', 'Off', 'Party', 'Supp'],
    'PrimBath': ['Chill', 'Clean', 'Off', 'Party', 'Supp', 'TV'],
    'Primary': ['Chill', 'Clean', 'Off', 'Party', 'Supp', 'TV'],
    'LhsBdrm': ['Chill', 'Clean', 'Off', 'Party', 'TV'],
    'Office': ['Chill', 'Clean', 'Off', 'Party', 'Supp', 'TV'],
    'Yard': ['Chill]', 'Night', 'Off', 'Chill', 'Night', 'Off', 'Party', 'Supp', 'TV', '_Games'],
    'WHA': ['_ALARM', '_AUTO', '_AWAY', '_FLASH', '_PANIC']
  ]
}

String ra2ModelToCode(String model) {
  switch (model) {
    case 'LRF2-OCR2B-P-WH':
      return 'm'
    case 'PJ2-3BRL-GWH-L01':
      return 'q'
    case 'RR-MAIN-REP-WH':
    case 'RR-T15RL-SW':
    case 'RR-VCRX-WH':
      return 'k'
    case 'RRD-H6BRL-WH':
    case 'RRD-W7B-WH':
      return 'w'
    default:
      return 'unknown'
  }
}

//----
//---- Convenience methods that operate on State data
//----

Map ra2RepButtonToRS(Event e) {
  return settings.ra2_RepButtonToRS?."${e.displayName}"?."${e.name}" ?: [:]
}

Map ra2RSToRepButton(String room, String scene) {
  return settings.ra2_RSToRepButton?."${room}"?."${scene}" ?: [:]
}

ArrayList<String> ra2RepRooms() {
  return settings.ra2_Rooms
}

ArrayList<String> ra2RepScenes(String Room) {
  return settings.ra2_RoomToScene?."${room}" ?: [:]
}

//----
//---- User Interface Support
//----

void idRa2Repeaters() {
  input(
    name: 'ra2Repeaters',
    title: heading2('Identify Lutron RA2 Repeater(s)'),
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: true,
    multiple: true
  )
}

void solicitRa2IntegrationReport() {
  input(
    name: 'ra2IntegReport',
    title: 'Paste in the Lutron RA2 Integration Report',
    type: 'textarea',
    rows: 5,
    submitOnChange: true,
    required: true,
    multiple: false
  )
}

Map Ra2Page() {
  return dynamicPage(
    name: 'Ra2Page',
    title: [
      heading1("Ra2 Test Page - ${app.id}"),
      bullet1('Click <b>Done</b> to enable subscriptions.')
    ].join('<br/>'),
    install: true,
    uninstall: true
  ) {
    //---------------------------------------------------------------------------------
    // REMOVE NO LONGER USED SETTINGS AND STATE
    //   - https://community.hubitat.com/t/issues-with-deselection-of-settings/36054/42
    //-> Prefer settingsRemoveAndLog() over app.removeSetting('..')
    //-> Prefer stateRemoveAndLog() over state.remove('..')
    //---------------------------------------------------------------------------------
    //-> stateAndSessionCleanup()
    app.updateLabel('Ra2TestPage')
    section {
      solicitLogThreshold('appLogThresh', 'INFO')  // 'ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE'
      state.logLevel = logThreshToLogLevel(settings.appLogThresh) ?: 5
      idRa2Repeaters()
      solicitRa2IntegrationReport()
      paragraph([
        heading1('Debug<br/>'),
        *appStateAsBullets(true),
        *appSettingsAsBullets(true)
      ].join('<br/>'))
    }
  }
}

//---- SYSTEM CALLBACKS

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

Integer forwardButton(ArrayList<String> rawSceneData) {
  switch(rawSceneData.size()) {
    case 2:
      return 0
      break
    case 3:
      return safeParseInt(rawSceneData[0].substring(1))
      break
    default:
      logError('#555', "Unexpected Button Count")
      return -1
  }
}

void parseRa2IntegRpt() {
  // settings['ra2_RepButtonToRS'] = [
  //   "${deviceRoom}-${deviceLocation}": [
  //     "buttonLed-${componentNumber}": "${room}:'${scene}'"]
  //   ]
  // ]
  //-> logInfo('#566', [
  //->   "Alpha Beta >${forwardButton('Alpha Beta'.tokenize(' '))}<",
  //->   "#40 Alpha Gamma >${forwardButton('#40 Alpha Gamma'.tokenize(' '))}<"
  //-> ])
  ArrayList<String> ra2IntegrationConfig = []
  Map ra2Devices = [:]
  ArrayList<String> expectedCols = ['Device Room', 'Device Location',
    'Device name', 'Model', 'ID', 'Component', 'Component Number', 'Name']
  // NOTES
  //   - Rows with a subset of expected columns provide 'sticky' data.
  //   - Subsequent rows populate initial columns with the 'stick' data.
  //==> ArrayList<String> stickyCols = []
  String hubDeviceLabel
  // Split (vs Tokenize) rows to preserve the original row number.
  settings.ra2IntegReport.split('\n').eachWithIndex{ row, i ->
    // Cleanup Lutron's sloppy CSV
    row = row.trim()
    row = row.replaceAll(', ', ',')
    if (row) {
      ArrayList<String> stickyColumns
      switch(i) {
        case 0:
          // CONFIRM INITIAL ROW MATCHES EXPECTED
          String expect = 'RadioRA 2 Integration Report'
          if (row != expect) {
            logError('parseRa2IntegRpt', "${i}: Expected >${expect}<, Got >${row}<")
          }
          break
        case 2:
          // ENSURE COLUMN TITLES ROW MATCHES EXPECTED
          // Split (vs Tokenize) cols to preserve the original col positions.
          String[] actualTitles = row.split(',')
          expectedCols.eachWithIndex{ expectedTitle, j ->
            if (expectedTitle != actualTitles[j]) {
              logError(
                'parseRa2IntegRpt',
                "Title Column #${j}: Expected >${expectedTitle}< Got >${actualTitles[j]}<"
              )
            }
          }
          break
        default:
          // Split (vs Tokenize) cols to preserve the original col positions.
          String[] cols = row.split(',')
          String[] noLeadingNulls = cols.dropWhile{ it == '' }
          Boolean hasLeadingNulls = cols.size() != noLeadingNulls.size()
          if (!hasLeadingNulls && cols.size() != expectedCols.size()) {
            // No leading nulls and fewer then expected columns indicates sticky columns
            //==> stickyCols = cols
            //String roomAndName = ra2Cols[3] ? ra2Cols[3].find(/(\w)+.(\w)+/) : ''
            if (cols.size() >= 5) {
              String hubType = ra2ModelToCode(cols[3])
              String ra2Id = cols[4]
              String ra2Name = cols[1]?.tokenize(' ')[0] // Drop from initial ws.
              if (ra2Name == 'Enclosure Device 001') { ra2Name = cols[1] }
              hubDeviceLabel = "${ra2Name} (ra2-${ra2Id})"
              ra2Devices[hubLabel] = [:]
              ra2IntegrationConfig << "'${hubType},${ra2Id},${hubDeviceLabel}'"
              //-> logInfo('parseRa2IntegRpt', [
              //->   '#621',
              //->   "ra2IntegrationConfig: >${ra2IntegrationConfig}<",
              //->   "hubDeviceLabel: >${hubDeviceLabel}<"
              //-> ])
            } else {
              logInfo('parseRa2IntegRpt #624', "???..${cols}")
            }
          } else {
            //===> Integer takeSticky = expectedCols.size() - noLeadingNulls.size()
            // Prune leading nulls columns
            //===> ArrayList<String> completeCols = [*stickyCols.take(takeSticky), *noLeadingNulls]
            logInfo('parseRa2IntegRpt #630', "${hubDeviceLabel}==> ${noLeadingNulls}")
            // Example Data
            //   'REP1 (ra2-1)':
            //     [Button 1, 1, All Chill]
            //     [Button 40, 40]
            //     [Button 43, 43, #45 Den Day]
            //     [Led 1, 101]
            //   'Entry (ra2-3)':   // Is this an RA2 PICO?
            //     [Button 1, 2, LhsBdrm 100%]
            //     [Button 2, 3, Toggle Auto]
            //     [Button 3, 4, LhsBdrm 0%]
            //     [Button 4, 5, LhsBdrm Up]
            //     [Button 5, 6, LhsBdrm Down]
            //   'FrontDoor (ra2-10)':  // RA2 KPAD
            //     [Button 1, 1, FLASH]
            //     [Button 18, 18]
            //     [Button 19, 19]
            //     [Button 2, 2, Main Party]
            //     [Button 3, 3, Main Play]
            //     [Button 4, 4, Main Chill]
            //     [Button 5, 5, Main TV]
            //     [Button 6, 6, Foyer]
            //     [Led 1, 81]
            //     [Led 2, 82]
            //     [Led 3, 83]
            //     [Led 4, 84]
            //     [Led 5, 85]
            //     [Led 6, 86]
            //   'Desk (ra2-30)':
            //     [Button 1, 2, Office 100%]
            //     [Button 2, 3, Toggle Auto]
            //     [Button 3, 4, Office 0%]
            //     [Button 4, 5, Office Raise]
            //     [Button 5, 6, Office Lower]
            //   'Credenza (ra2-73)':
            //     [Button 1, 1, ALARM]
            //     [Button 10, 10, Yard Auto]
            //     [Button 11, 11, PARTY]
            //     [Button 12, 12, CHILL]
            //     [Button 13, 13, TV]
            //     [Button 14, 14, NIGHT]
            //     [Button 15, 15, Floor Lamp]
            //     [Button 16, 16, ALL OFF]
            //     [Button 17, 17, CLEAN]
            //     [Button 2, 2, PANIC]
            //     [Button 24, 24]
            //     [Button 25, 25]
            //     [Button 3, 3, FLASH]
            //     [Button 4, 4, QUIET]
            //     [Button 5, 5, AWAY]
            //     [Button 6, 6, Guest Auto]
            //     [Button 7, 7, Den Auto]
            //     [Button 8, 8, Main Auto]
            //     [Button 9, 9, Lanai Auto]
            //     [Led 1, 81]
            //          :
            //     [Led 16, 96]
            //     [Led 17, 97]
            //   'Visor (ra2-82)':
            //     [CCI 9, 34]
            //     [CCI 8, 33]
            //     [CCI 7, 32]
            //     [CCI 6, 31]
            //     [Led 6, 86]
            //     [Led 5, 85]
            //     [Led 4, 84]
            //     [Led 3, 83]
            //     [Led 2, 82]
            //     [Led 1, 81]
            //     [Button 6, 6]
            //     [Button 5, 5]
            //     [Button 4, 4, ALARM]
            //     [Button 3, 3, FLASH]
            //     [Button 2, 2, AWAY]
            //     [Button 1, 1, AUTO]
            //   'HisCloset-MotionSensor (ra2-87)':'
            //     [Green Button Mode, 24]
            //     [Green Mode, 2]
            //     [Green Mode, ID, Mode Name, Step Number]
            //     [Green100%, 7]
            //     [Green20%, 3]
            //     [Green40%, 4]
            //     [Green60%, 5]
            //     [Green80%, 6]
            //     [New Event 002, 1]
            //     [Off, 1]
            //   'SAMPLES ???':'
            //     [Project Timeclock, 25]
            //     [Timeclock, ID, Event, Event Index]
            //     [Gym, NA-BackOutlet, 90]
            //     [Control, Zone 04, 81]
            //     [RhsBath, RhsBath-Vanity, 79]
            //     [RhsBath, RhsBath-Shower, 78]
            //     [Laundry, 99]
            //     [Shop, 88]
            //     [Office, 85]
          }
      }
    }
  }
  logInfo('parseRa2IntegRpt', ['LUTRON INTEGRATION (RA2) CONFIG LIST',
    ra2IntegrationConfig])
}

// state.ra2Device = [:]
//   Id -> [name: , code: ]
// LutronIntegrationApp
//   - String generateConfigurationList() {
//       // Code,Id,Name
//     }

void ra2RepHandler (Event e) {
  logInfo('ra2RepHandler', "${eventDetails(e)}")
// EXAMPLE 1 - Cook
//   descriptionText  RA2 Repeater 1 (ra2-1) led 29 was turned on
//   displayName  RA2 Repeater 1 (ra2-1)
//   deviceId  6825
//   name  buttonLed-29
//   value  on
// EXAMPLE 2a Den Chill (in lieu of Cook Off)
//   descriptionText  RA2 Repeater 1 (ra2-1) led 29 was turned off
//   displayName  RA2 Repeater 1 (ra2-1)
//   deviceId  6825
//   name  buttonLed-29
//   value  off
//   isStateChange  true
// EXAMPLE 2b
//   descriptionText  RA2 Repeater 1 (ra2-1) led 28 was turned on
//   displayName  RA2 Repeater 1 (ra2-1)
//   deviceId  6825
//   name  buttonLed-28
//   value  on
//   isStateChange  true
// EXAMPLE 3
//   descriptionText  RA2 Repeater 1 (ra2-1) led 40 was turned on
//   displayName  RA2 Repeater 1 (ra2-1)
//   deviceId  6825
//   name  buttonLed-40
//   value  on
//   isStateChange  true
// ALARM TOGGLE (From Visor or 15-Button Controller)
//   descriptionText  RA2 Repeater 2 (ra2-83) led 91 was turned off
//   displayName  RA2 Repeater 2 (ra2-83)
//   deviceId  6892
//   name  buttonLed-91
//   value  off
//   isStateChange  true
//   ----
//   descriptionText  RA2 Repeater 2 (ra2-83) led 91 was turned on
//   displayName  RA2 Repeater 2 (ra2-83)
//   deviceId  6892
//   name  buttonLed-91
//   value  on
//   isStateChange  true

}

void initialize() {
  // - The same keypad may be associated with two different, specialized handlers
  //   (e.g., mode changing buttons vs special functionalily buttons).
  logWarn('initialize', 'Entered')
  settings.ra2Repeaters.each{ device ->
  logInfo('initialize', "Subscribing >${device}< to ra2RepHandler")
    subscribe(device, ra2RepHandler, ['filterEvents': true])
  }
  parseRa2IntegRpt()

}
