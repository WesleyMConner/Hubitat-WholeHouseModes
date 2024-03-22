// ---------------------------------------------------------------------------------
// S I T E   D A T A
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

library(
  name: 'lSiteData',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Push Button Switch Group (PBSG) Implementation',
  category: 'general purpose'
)

/*
state.hubIdToRa2RepData = [
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
    ],
  ],
  '9129': [
    type: 'pro2-repeater',
    nativeId: '1',
    buttons: [
      [button: 1, room: 'Lanai', scene: 'Chill'],
      [button: 2, room: 'Lanai', scene: 'Clean'],
      [button: 3, room: 'Lanai', scene: 'Day'],
      [button: 4, room: 'Lanai', scene: 'Games'],
      [button: 5, room: 'Lanai', scene: 'Night'],
      [button: 6, room: 'Lanai', scene: 'Party'],
      [button: 7, room: 'Lanai', scene: 'Supp'],
      [button: 8, room: 'Lanai', scene: 'TV'],
      [button: 9, room: 'RhsBdrm', scene: 'Chill'],
      [button: 10, room: 'RhsBdrm', scene: 'Clean'],
      [button: 11, room: 'RhsBdrm', scene: 'Day'],
      [button: 12, room: 'RhsBdrm', scene: 'Night'],
      [button: 13, room: 'RhsBdrm', scene: 'Off'],
      [button: 14, room: 'RhsBdrm', scene: 'Party'],
      [button: 15, room: 'RhsBdrm', scene: 'TV']
    ]
  ]
  '9129': [
    type: 'pro2',  // Grill Can Remote
    nativeId: '6',
    buttons: [
    ],
    type: 'pro2',  // Grill Can Remote
    nativeId: '6',
    buttons: [
    ]
]
      {
        "nativeId": 6,
        "Area": {
          "Name": "Lanai"
        },
        "Name": "Grill Can Remote 1",
        "Buttons": [
          {
            "Number": 2
          },
          {
            "Number": 3
          },
          {
            "Number": 4
          },
          {
            "Number": 5
          },
          {
            "Number": 6
          }
        ]
      },
      {
        "nativeId": 8,
        "Area": {
          "Name": "LhsBdrm"
        },
        "Name": "Entry Pico",
        "Buttons": [
          {
            "Number": 2
          },
          {
            "Number": 3
          },
          {
            "Number": 4
          },
          {
            "Number": 5
          },
          {
            "Number": 6
          }
        ]
      },
      {
        "nativeId": 9,
        "Area": {
          "Name": "RhsBdrm"
        },
        "Name": "Entry Pico",
        "Buttons": [
          {
            "Number": 2
          },
          {
            "Number": 3
          },
          {
            "Number": 4
          },
          {
            "Number": 5
          },
          {
            "Number": 6
          }
        ]
      },
      {
        "nativeId": 10,
        "Area": {
          "Name": "RhsBdrm"
        },
        "Name": "Table Pico",
        "Buttons": [
          {
            "Number": 2
          },
          {
            "Number": 3
          },
          {
            "Number": 4
          },
          {
            "Number": 5
          },
          {
            "Number": 6
          }
        ]
      }
    ],
    "Zones": [
      {
        "nativeId": 2,
        "Name": "Table Lamps",
        "Area": {
          "Name": "RhsBdrm"
        }
      },
      {
        "nativeId": 3,
        "Name": "Floor Lamp",
        "Area": {
          "Name": "RhsBdrm"
        }
      },
      {
        "nativeId": 4,
        "Name": "Down Lighting",
        "Area": {
          "Name": "Lanai"
        }
      },
      {
        "nativeId": 5,
        "Name": "Grill Can",
        "Area": {
          "Name": "Lanai"
        }
      },
      {
        "nativeId": 7,
        "Name": "Outdoor Dining",
        "Area": {
          "Name": "Lanai"
        }
      }
    ]
  }
}
*/

Map getRoomSceneForRepButton(Long repHubId, String button) {
  return state.repButtonToRS."${repHubId}"?."${button}"
}

Map getRoomSceneMapForRep(Long repHubId) {
  return state.repButtonToRS."${repHubId}"
}

ArrayList<String> getRoomNames() {
  // Extract all room names across known repeaters.
  //   state.hubIdToRa2RepData = [
  //     '6825': [
  //       type: 'ra2',
  //       nativeId: '1',
  //       buttons: [
  //         [button: 1, room: 'WHA', scene: 'Chill'],
  //                     ^^^^^^^^^^^
  ArrayList<String> rooms = []
  state.hubIdToRa2RepData.each { repHubId, map ->
    map.buttons.each { buttonMap ->
      String candidateRoom = buttonMap.room
      if (candidateRoom && candidateRoom != 'WHA') {
        if (!rooms.contains(candidateRoom)) {
          rooms << candidateRoom
        }
      }
    }
  }
  return rooms
}

ArrayList<String> getSceneNames(String room) {
  // Extract all room names across known repeaters.
  //   state.hubIdToRa2RepData = [
  //     '6825': [
  //       type: 'ra2',
  //       nativeId: '1',
  //       buttons: [
  //         [button: 1, room: 'WHA', scene: 'Chill'],
  //                                  ^^^^^^^^^^^^^^
  ArrayList<String> roomScenes = []
  state.hubIdToRa2RepData.each { repHubId, map ->
    map.buttons.each { buttonMap ->
      log('lSiteData #224', "${buttonMap}")
      String candidateScene = buttonMap.scene
      if (candidateScene && candidateScene.room == room) {
        if (!roomScenes.contains(candidateScene)) {
          roomScenes << candidateScene
        }
      }
    }
  }
  return roomScenes
}

//state.RoomSceneIndDeviceLevels = [
//  [room: '', scene: '', hubDeviceId: '1', level: '']
//]
//  HUB ID          LABEL
//  ======   ====================
//       1   Den Fireplace (02)
//    5232   Pool Pong (05)
//    5831   Uplighting (Front)
//    5533   Uplighting (Guest)
//    5534   Uplighting (Primary)

