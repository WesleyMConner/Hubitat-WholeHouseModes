
// ---------------------------------------------------------------------------------
// W H O L E   H O U S E   A U T O M A T I O N
//
//   Copyright (C) 2023-Present Wesley M. Conner
//
//   LICENSE
//     Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
//     "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
//     not use this file except in compliance with the License. Unless
//     required by applicable law or agreed to in writing, software
//     distributed under the License is distributed on an "AS IS" BASIS,
//     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
//     implied.
// ---------------------------------------------------------------------------------

definition (
  name: 'deviceMgmt',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Whole House Automation using Modes, RA2 and Room Overrides',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  singleInstance: true
)

preferences {
  page(name: 'deviceMgmtPage')
}

Map deviceMgmtPage () {
  return dynamicPage(
    name: 'deviceMgmtPage',
    title: 'Review and Manage Hubitat Devices<br/>',
    install: true,
    uninstall: true,
    nextPage: 'deviceMgmtPage'
  ) {
    app.updateLabel('Device Management')
    // SAMPLE STATE & SETTINGS CLEAN UP
    //   - state.remove('X')
    //   - settings.remove('Y')
    section {
      input(
        name: 'allDevices',
        title: 'Authorize Specialty Function Repeater Access<br/>',
        type: "capability.*",
        submitOnChange: true,
        required: true,
        multiple: true
      )
      // GATHER DATA
      /*
      paragraph comment(
        '<b>Display Name</b> = <b>Device Label</b> ?: <b>Device Name</b>'
      )
      List<String> tabularOutput = [
        "'Device Id','Device Label','Device Name','Display Name','Dni'"
      ]
      settings.allDevices?.each{ d ->
        tabularOutput << \
          "'${d.getId()}','${d.getLabel()}','${d.getName()}'," \
          + "'${d.getDisplayName()}','${d.getDeviceNetworkId()}'"
      }
      paragraph tabularOutput.join('\n')
      */
      // RE-LABEL DEVICES
      settings.allDevices?.each{ d ->
        switch (d.getId()) {
          case '6848':
            d.setLabel('Control - Central Hall KPAD (ra2-32)')
            break;
          case '6833':
            d.setLabel('Control - Main Entry Right KPAD (ra2-10)')
            break;
          case '6845':
            d.setLabel('Control - Main Entry Left KPAD (ra2-29)')
            break;
          case '6859':
            d.setLabel('Control - Garage KPAD (ra2-44)')
            break;
          case '6856':
            d.setLabel('Control - Kit Slider KPAD (ra2-41)')
            break;
          case '6839':
            d.setLabel('Control - Primary Entry KPAD (ra2-20)')
            break;
          case '6861':
            d.setLabel('Control - Primary Slider KPAD (ra2-46)')
            break;
          case '6882':
            d.setLabel('Control - Primary Tabletop KPAD (ra2-73)')
            break;
          case '6825':
            d.setLabel('Control - REP 1 (ra2-1)')
            break;
          case '6892':
            d.setLabel('Control - REP 2 (ra2-83)')
            break;
          case '6889':
            d.setLabel('Control - TV Wall KPAD (ra2-80)')
            break;
          case '6875':
            d.setLabel('Den - Bev Station (ra2-63)')
            break;
          case '1':
            d.setLabel('Den - Fireplace')
            break;
          case '6841':
            d.setLabel('Den - TV Wall (ra2-23)')
            break;
          case '6874':
            d.setLabel('Den - Fireplace Cans (ra2-62)')
            break;
          case '6858':
            d.setLabel('Den - Garage Hall (ra2-43)')
            break;
          case '6850':
            d.setLabel('Den - Kitchen Table (ra2-34)')
            break;
          case '6876':
            d.setLabel('Den - Uplighting (ra2-64)')
            break;
          case '6871':
            d.setLabel('Den Lamp - Dimmer (ra2-59)')
            break;
          case '6865':
            d.setLabel('Den Lamp - Pico (A) (ra2-50)')
            break;
          case '6891':
            d.setLabel('Control - Visor KPAD (ra2-82)')
            break;
          case '6830':
            d.setLabel('Control - Zone 01 (ra2-7)')
            break;
          case '6840':
            d.setLabel('Control - Zone 02 (ra2-22)')
            break;
          case '6886':
            d.setLabel('Control - Zone 03 (ra2-77)')
            break;
          case '6890':
            d.setLabel('Control - Zone 04 (ra2-81)')
            break;
          case '6895':
            d.setLabel('Guest - Gym Back Outlet (ra2-90)')
            break;
          case '6885':
            d.setLabel('Guest - LHS Cans (ra2-76)')
            break;
          case '6844':
            d.setLabel('Guest - RHS Cans (ra2-28)')
            break;
          case '6857':
            d.setLabel('Guest - RHS Closet (ra2-42)')
            break;
          case '6872':
            d.setLabel('Guest - RHS Closet Sensor (ra2-60)')
            break;
          case '6828':
            d.setLabel('Guest - RHS Hall Lamp (ra2-5)')
            break;
          case '6868':
            d.setLabel('Her - Closet Dimmer (ra2-53)')
            break;
          case '6893':
            d.setLabel('Her - Closet Sensor (ra2-86)')
            break;
          case '6870':
            d.setLabel('His - Closet Dimmer (ra2-56)')
            break;
          case '6894':
            d.setLabel('His - Closet Sensor (ra2-87)')
            break;
          case '6873':
            d.setLabel('Kitchen - Cans (ra2-61)')
            break;
          case '6877':
            d.setLabel('Kitchen - Counters (ra2-65)')
            break;
          case '6827':
            d.setLabel('Kitchen - Soffit (ra2-4)')
            break;
          case '6855':
            d.setLabel('Lanai - Cans (ra2-39)')
            break;
          case '5537':
            d.setLabel('Lanai - Outdoor Dining')
            break;
          case '6869':
            d.setLabel('Lanai - Pool (ra2-54)')
            break;
          case '6837':
            d.setLabel('Laundry - Cans (ra2-18)')
            break;
          case '6831':
            d.setLabel('Laundry - Sensor (ra2-8)')
            break;
          case '6881':
            d.setLabel('LHS Bath - Sensor (ra2-72)')
            break;
          case '6843':
            d.setLabel('LHS Bath - Shower (ra2-27)')
            break;
          case '6883':
            d.setLabel('LHS Bath - Soffit (ra2-74)')
            break;
          case '6826':
            d.setLabel('LHS Bdrm - Entry Pico (ra2-3)')
            break;
          case '8340':
            d.setLabel('LHS Bdrm - Floor Lamp (pro2-3)')
            break;
          case '6852':
            d.setLabel('LHS Bdrm - Table Lamps (ra2-36)')
            break;
          case '6863':
            d.setLabel('LHS Bdrm - Table Pico (ra2-48)')
            break;
          case '6853':
            d.setLabel('Main - Art Niche (ra2-37)')
            break;
          case '6832':
            d.setLabel('Main - Dining Table (ra2-9)')
            break;
          case '6847':
            d.setLabel('Main - Foyer Cans (ra2-31)')
            break;
          case '6849':
            d.setLabel('Main - Living Rm Lamps (ra2-33)')
            break;
          case '6846':
            d.setLabel('Office - Office Desk Pico (W) (ra2-30)')
            break;
          case '6851':
            d.setLabel('Office - Office Wall Lamp (ra2-35)')
            break;
          case '6878':
            d.setLabel('Pantry - Den Pantry (ra2-67)')
            break;
          case '6879':
            d.setLabel('Pantry - Den Pantry Sensor (ra2-69)')
            break;
          case '580':
            d.setLabel('Primary - Primary Floor Lamp (-)')
            break;
          case '6838':
            d.setLabel('Primary - Primary Table Lamps (ra2-19)')
            break;
          case '6866':
            d.setLabel('PrimBath - PrimBath Cans (ra2-51)')
            break;
          case '6862':
            d.setLabel('PrimBath - PrimBath Soffit (ra2-47)')
            break;
          case '6867':
            d.setLabel('PrimBath - PrimBath Tub (ra2-52)')
            break;
          case '6835':
            d.setLabel('PrimBath - PrimBath WC (ra2-15)')
            break;
          case '6836':
            d.setLabel('RHS Bath - Lamp (ra2-16)')
            break;
          case '6864':
            d.setLabel('RHS Bath - Sensor (ra2-49)')
            break;
          case '6887':
            d.setLabel('RHS Bath - Shower (ra2-78)')
            break;
          case '6888':
            d.setLabel('RHS Bath - Vanity (ra2-79)')
            break;
          case '6860':
            d.setLabel('RHS Bdrm - Chair Lamp (ra2-45)')
            break;
          case '8436':
            d.setLabel('RHS Bdrm - Entry Pico (ra2-66)')
            break;
          case '8339':
            d.setLabel('RHS Bdrm - Table Lamps (pro2-2)')
            break;
          case '6834':
            d.setLabel('RHS Bdrm - Table Pico (ra2-12)')
            break;
          case '6884':
            d.setLabel('Yard - Back Porch (ra2-75)')
            break;
          case '6829':
            d.setLabel('Yard - Front Porch (ra2-6)')
            break;
          case '6880':
            d.setLabel('Yard - Outdoor Spots (ra2-71)')
            break;
          case '6842':
            d.setLabel('Yard - Shop Porch (ra2-26)')
            break;
          case '5165':
            d.setLabel('Control - Front MultiSensor')
            break;
          case '3':
            d.setLabel('Control - Rear MultiSensor')
            break;
        }
      }
    }
  }
}
