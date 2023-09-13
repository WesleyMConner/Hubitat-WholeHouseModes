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

void changeDeviceLabel (String deviceName, String newDeviceLabel) {
  // -----------------------------------------------------------------
  // I M P O R T A N T - Permission must be granted to individual LEDs
  //                     BEFORE they can be relabled.
  // -----------------------------------------------------------------
  DevW d = settings.lutronLEDs?.findAll{it.name == deviceName}?.first()
  d.setLabel(newDeviceLabel)
}

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
       settingsKey: "E R R O R: Missing 'settingsKey'",
//             title: "E R R O R: Missing 'title'",
              type: "E R R O R: Missing 'type'",
    submitOnChange: true,
          required: true,
          multiple: true,
           options: null
  ] << args
  String boolHideInput = "hide${_args.name}"
  //String devices = settings[_args.name]
  String devices = settings[settingsKey]
  //  ? "(devices=${settings[settingsKey]?.size()})"
  //  : '(devices=0)'
  String title = settings[boolHideInput]
      ? "Hiding ${_args.settingsKey} >${devices}<"
      : "Showing ${_args.settingsKey}"
  input (
    name: boolHideInput,
    type: 'bool',
    title: title,
//    title: settings[boolHideInput]
//      ? "Hiding ${_args.settingsKey} ${devices}"
//      : "Showing ${_args.settingsKey}",
    submitOnChange: true,
    defaultValue: false,
  )
  if (!settings[boolHideInput]) {
    input (
      name: _args.settingsKey,
      type: _args.type,
      title: title,
//      title: _args.title,
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
      title: settingsKey + (note ? comment("<br/>${note}") : ''),
      type: 'device.LutronTelnet'
    ] << args
  )
}

void authorizeMainRepeater (
  // - A WHA application can utilize multiple Lutron Main Repeaters.
  // - Each room, however, is constrained to a single Repeater.
  // - If the LED associated with a room scene's repeater activation button
  //   transitions from on-to-off unexpectedly, a MANUAL override is
  //   assumed.
  String settingsKey = 'mainRepeater',
  String note = '',
  Map args = [:]
  ) {
  collapsibleInput (
    [
      title: settingsKey + (note ? comment("<br/>${note}") : ''),
      type: 'device.LutronKeypad',
      multiple: false
    ] << args
  )
}

void solicitLutronMiscellaneousKeypads (
  String settingsKey = 'lutronMiscKeypads',
  String note = '',
  Map args = [:],
  String deviceType = 'device.LutronKeypad'
  ) {
  // This function IS NOT REQUIRED when miscellaneous devices are manually
  // converted to Lutron SeeTouch device types.
  collapsibleInput ([
    title: settingsKey + (note ? comment("<br/>${note}") : ''),
    type: deviceType
  ] << args)
}

void authorizeSeeTouchKeypads (
  String settingsKey,
  String note = '',
  Map args = [:],
  String deviceType = 'device.LutronSeeTouchKeypad'
  ) {
  collapsibleInput ([
    title: settingsKey + (note ? comment("<br/>${note}") : ''),
    type: deviceType
  ] << args)
}

void authorizeLedButtons (
  String settingsKey = 'lutronKeypadButtons',
  String note = '',
  Map args = [:]
  ) {
  collapsibleInput (
    [
      title: settingsKey + (note ? comment("<br/>${note}") : ''),
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
      title: settingsKey + (note ? comment("<br/>${note}") : ''),
      type: 'device.LutronFastPico'
    ] << args
  )
}

void authorizeSwitches (
  String settingsKey = 'switches',
  String note = '',
  Map args = [:]
  ) {
  collapsibleInput (
    [
      title: settingsKey + (note ? comment("<br/>${note}") : ''),
      type: 'capability.switch'
    ] << args
 )
}

void identifyLedButtonsForListItems(
  List<String> list,
  List<DevW> ledDevices,
  String prefix
  ) {
  // Keypad LEDs are used as a proxy for Keypad buttons.
  //   - The button's displayName is meaningful to clients.
  //   - The button's deviceNetworkId is <KPAD DNI> hyphen <BUTTON #>
  //-> log.trace(
  //->   "RA2 identifyLedButtonsForListItems() "
  //->   + "<b>list:</b> ${list}, "
  //->   + "<b>ledDevices:</b> ${ledDevices}, "
  //->   + "<b>prefix:</b> ${prefix}, "
  //-> )
  list.each{ item ->
    //-> log.trace(
    //->   "RA2 identifyLedButtonsForListItems() Processing <b>prefix:</b> ${prefix}, <b>item:</b> ${item}."
    //-> )
    input(
      name: "${prefix}_${item}",
      type: 'enum',
      width: 6,
      title: emphasis("Identify LEDs/Buttons for <b>${item}</b>"),
      submitOnChange: true,
      required: false,
      multiple: true,
      options: ledDevices.collect{ d ->
        "${d.getLabel()}: ${d.getDeviceNetworkId()}"
      }?.sort()
    )
  }
}
