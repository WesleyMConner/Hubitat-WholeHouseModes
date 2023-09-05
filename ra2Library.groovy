// ---------------------------------------------------------------------------------
// R A 2   L I B R A R Y
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
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.hub.domain.Event as Event

library (
  name: 'ra2Library',
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'Lutron RA2 Hubitat Support Functions',
  category: 'Lutron RA2 Support',
  documentationLink: '',
  importUrl: ''
)

// -------------------------------------------------
// R E N A M E   L U T R O N   L E D   B U T T O N S
// -------------------------------------------------
void newLedLabel (String deviceName, String newDeviceLabel) {
  // -----------------------------------------------------------------
  // I M P O R T A N T - Permission must be granted to individual LEDs
  //                     BEFORE they can be relabled.
  // -----------------------------------------------------------------
  DevW d = settings.lutronLEDs.findAll{it.name == deviceName}?.first()
  d.setLabel(newDeviceLabel)
}
//-> newLedLabel('(lutron-44) Garage KPAD LED 1','Garage KPAD 1 - ALL AUTO')

// -------------------------------------------------------------------
// C O L L A P S I B L E   I N P U T
//
//   Wrap Hubitat input() with a slider (bool) that enables hiding the
//   input() to declutter the screen.
//
//   Default values for all arguments are provided.
// -------------------------------------------------------------------
void collapsibleInput (Map args = [:]) {
  Map _args = [
        blockLabel: "E R R O R: Missing 'blockLabel'",
              name: "E R R O R: Missing 'name'",
             title: "E R R O R: Missing 'title'",
              type: "E R R O R: Missing 'type'",
    submitOnChange: true,
          required: true,
          multiple: true,
           options: null
  ] << args
  String boolHideInput = "hide${_args.name}"
  //String toggleTitle =
  String devices = settings[_args.name]
    ? "(devices=${settings[_args.name].size()})"
    : ''
  input (
    name: boolHideInput,
    type: 'bool',
    // width: settings[boolHideInput] ? 12 : 6,
    title: settings[boolHideInput]
      ? "Hiding ${_args.blockLabel} ${devices}"
      : "Showing ${_args.blockLabel}",
    submitOnChange: true,
    defaultValue: false,
  )
  if (!settings[boolHideInput]) {
    input (
      name: _args.name,
      type: _args.type,
      // width: 6,
      title: _args.title,
      submitOnChange: _args.submitOnChange,
      required: _args.required,
      multiple: _args.multiple,
      width: _args.width,
      options: _args.options
    )
  }
}

void solicitLutronTelnetDevice (
  String settingsKey = 'lutronTelnet',
  String note = '',
  Map args = [:]
  ) {
  collapsibleInput (
    [
      blockLabel: "${settingsKey} Device",
      name: settingsKey,
      title: "Identify ${settingsKey} Devices" + (note ? comment("<br/>${note}") : ''),
      type: 'device.LutronTelnet'
    ] << args
  )
}

void solicitLutronMainRepeaters (
  String settingsKey = 'lutronMainRepeaters',
  String note = '',
  Map args = [:]
  ) {
  collapsibleInput (
    [
      blockLabel: "${settingsKey} Devices",
      name: settingsKey,
      title: "Identify ${settingsKey} Devices" + (note ? comment("<br/>${note}") : ''),
      type: 'device.LutronKeypad'
    ] << args
  )
}

void solicitLutronMiscellaneousKeypads (
  String settingsKey = 'lutronMiscKeypads',
  String note = '',
  Map args = [:]
  ) {
  // This function IS NOT REQUIRED when miscellaneous devices are manually
  // converted to Lutron SeeTouch device types.
  collapsibleInput (
    [
      blockLabel: "${settingsKey} Devices",
      name: settingsKey,
      title: "Identify ${settingsKey} Devices" + (note ? comment("<br/>${note}") : ''),
      type: 'device.LutronKeypad'
    ] << args
   )
}

void solicitSeeTouchKeypads (
  String settingsKey = 'lutronSeeTouchKeypads',
  String note = '',
  Map args = [:]
  ) {
  collapsibleInput (
    [
      blockLabel: "${settingsKey} Devices",
      name: settingsKey,
      title: "Identify ${settingsKey} Devices" + (note ? comment("<br/>${note}") : ''),
      type: 'device.LutronSeeTouchKeypad'
    ] << args
  )
}

void solicitLutronLEDs (
  String settingsKey = 'lutronKeypadButtons',
  String note = '',
  Map args = [:]
  ) {
  collapsibleInput (
    [
      blockLabel: "${settingsKey} Devices",
      name: settingsKey,
      title: "Identify ${settingsKey} Devices" + (note ? comment("<br/>${note}") : ''),
      type: 'device.LutronComponentSwitch'
    ] << args
  )
}

void solicitLutronPicos (
  String settingsKey = 'lutronPicos',
  String note = '',
  Map args = [:]
  ) {
  collapsibleInput (
    [
      blockLabel: "${settingsKey} Devices",
      name: settingsKey,
      title: "Identify ${settingsKey} Devices" + (note ? comment("<br/>${note}") : ''),
      type: 'device.LutronFastPico'
    ] << args
  )
}

void solicitSwitches (
  String settingsKey = 'switches',
  String note = '',
  Map args = [:]
  ) {
  collapsibleInput (
    [
      blockLabel: "${settingsKey} Devices",
      name: settingsKey,
      title: "Identify ${settingsKey} Devices" + (note ? comment("<br/>${note}") : ''),
      type: 'capability.switch'
    ] << args
 )
}

void selectLedsForListItems(
  List<String> list,
  List<DevW> ledDevices,
  String prefix
  ) {
  // Design Note
  //   Keypad LEDs are used as a proxy for Keypad buttons.
  //     - The button's displayName is meaningful to clients.
  //     - The button's deviceNetworkId is <KPAD DNI> hyphen <BUTTON #>
  log.trace(
    "RA2 selectLedsForListItems() "
    + "<b>list:</b> ${list}, "
    + "<b>ledDevices:</b> ${ledDevices}, "
    + "<b>prefix:</b> ${prefix}, "
  )
  list.each{ item ->
    log.trace(
      "RA2 selectLedsForListItems() Processing <b>prefix:</b> ${prefix}, <b>item:</b> ${item}."
    )
    input(
      name: "${prefix}_${item}",
      type: 'enum',
      width: 6,
      title: emphasis("Buttons/LEDs activating '${item}'"),
      submitOnChange: true,
      required: false,
      multiple: true,
      options: ledDevices.collect{ d ->
        "${d.getLabel()}: ${d.getDeviceNetworkId()}"
      }?.sort()
    )
  }
}

List<DevW> narrowDevicesToRoom (String roomName, List<DevW> devices) {
  // This function excludes devices that are not associated with any room.
  List<String> deviceIdsForRoom = app.getRooms()
                                  .findAll{it.name == roomName}
                                  .collect{it.deviceIds.collect{it.toString()}}
                                  .flatten()
  return devices.findAll{ d -> deviceIdsForRoom.contains(d.id.toString())
  }
}
