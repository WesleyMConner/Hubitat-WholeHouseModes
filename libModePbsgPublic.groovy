library (
  name: 'libModePbsgPublic',
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'Methods on a Mode PBSG (Pushbutton Switch Group) Instance',
  category: 'general purpose',
  documentationLink: '',
  importUrl: ''
)

String getModePbsgAppLabel () {
  return "pbsg_modes"
}

String getModeVswDNI (String mode) {
  return "${getModePbsgAppLabel()}_${mode}"
}

String getModeNameForVswDNI (String modeVswDNI) {
  modeVswDNI.minus("${getModePbsgAppLabel()}_")
}

InstAppW getModePbsg () {
  return app.getChildAppByLabel(getModePbsgAppLabel())
}

InstAppW createModePbsg(
    List<String> modes,
    String defaultMode,
    String logThreshold
  ) {
  InstAppW modePbsg = getModePbsg()
  if (!modePbsg) {
    modePbsg = app.addChildApp(
      'wesmc',                   // app namespace  (see 'definition' above)
      'modePbsg',                // app name       (see 'definition' above)
      getModePbsgAppLabel()      // app label
    )
    List<String> vswDNIs = modes.collect{ mode -> getModeVswDni(mode) }
    String defaultVswDNI = getModeVswDni(defaultMode)
    modePbsg.configPbsg(vswDNIs, defaultVswDNI, logThreshold)
  }
  return modePbsg
}

void modeVswEventHandler (Event e) {
  // Process events for Mode PGSG child VSWs.
  //   - The received e.displayName is the DNI of the reporting child VSW.
  //   - When a Mode VSW turns on, change the Hubitat mode accordingly.
  //   - Clients DO NOT get a callback for this event.
  //   - Clients should instead respond to Hubitat Mode change events.
  if (e.isStateChange) {
    if (e.value == 'on') {
      turnOffPeers(e.displayName)
      state.previousVswDNI = state.activeVswDNI ?: state.defaultVswDNI
      state.activeVswDNI = e.displayName
      Ldebug(
        'modeVswEventHandler()',
        "${state.previousVswDNI} -> ${state.activeVswDNI}"
      )
      // Adjust the Hubitat mode.
      String mode = getModeNameForVswDNI(e.displayName)
      Ldebug(
        'modeVswEventHandler()',
        "Setting mode to <b>${mode}</b>"
      )
      getLocation().setMode(mode)
    } else if (e.value == 'off') {
      // Take no action when a VSW turns off
    } else {
      Lwarn(
        'modeVswEventHandler()',
        "unexpected event value = >${e.value}<"
      )
    }
  }
}

void turnOnModeVsw (String mode) {
  Ltrace('turnOnModeVsw()', "At entry, <b>mode:</b> ${mode}")
  InstAppW modePbsg = getModePbsg()
  if (!mode) {
    Lerror('turnOnModeVsw()', "required argument <b>mode</b> is null")
  }
  String vswDNI = getModeVswDNI(mode)
  modePbsg.turnOnVsw(vswDNI)
  turnOffPeers
}

void clientProvidedModePbsgInit() {
  // 1. Turn off current event subscriptions.
  // 2. Turning on the Mode VSW for the observed Hubitat Mode.
  // 3. Turning off all other Mode VSWs
  // 4. Subscribing to Mode VSW events
  Ltrace('clientProvidedModePbsgInit()', 'At entry')
  unsubscribe()
  turnOnModeVsw(getLocation().getMode())



  app.getAllChildDevices().each{ device ->
    Ltrace(
      'clientProvidedModePbsgInit()',
      "subscribing ${getDeviceInfo(device)}..."
    )
    subscribe(device, "switch", modeVswEventHandler, ['filterEvents': false])
  }
  //enforceMutualExclusion()
  //enforceDefaultSwitch()
}

void pbsgVswTurnedOnCallback (String modeVswDNI) {
  // - The modePbsg instance calls this method to reflect a state change.
  // - When a PBSG-managed switch turns on, its peers can be presumed to be off.
  // - This function's response includes setting mode Keypad LEDs on/off.
  // - SeeTouch Keypad LEDs are switches that respond to on/off.
  // - Access to LEDs is approved via a per-scene list of LEDs:
  //   modeButton_<scene> → ["<description>: <LED DNI>", ...]
  String currMode = getModeNameForVswDNI(modeVswDNI)
  Ldebug(
    'pbsgVswTurnedOnCallback()',
    "activating <b>mode = ${currMode}</b>."
  )
  getLocation().setMode(currMode)
  updateLutronKpadLeds(currMode)
}

