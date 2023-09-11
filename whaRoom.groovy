// ---------------------------------------------------------------------------------
// R O O M   S C E N E S
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
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Location as Loc
#include wesmc.UtilsLibrary
#include wesmc.ra2Library

definition(
  parent: 'wesmc:wha',
  name: 'whaRoom',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Manage WHA Rooms for Whole House Automation',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',          // Not supported as of Q3'23
  iconX3Url: '',          // Not supported as of Q3'23
  singleInstance: false
)

// -------------------------------
// C L I E N T   I N T E R F A C E
// -------------------------------
preferences {
  page(name: 'whaRoomPage')
}

Map whaRoomPage () {
  // The parent application (Whole House Automation) assigns a unique label
  // to each WHA Rooms instance. Capture app.getLabel() as state.ROOM_NAME.
  return dynamicPage(
    name: 'whaRoomPage',
    title: \
      heading("${app.getLabel()} Scenes<br/>") \
        + comment("Click <b>${red('Done')}</b> to enable subscriptions.<br/>") \
        + comment('Tab to register changes.'),
    install: true,
    uninstall: true,
    nextPage: 'whaPage'
  ) {
    // SAMPLE STATE & SETTINGS CLEAN UP
    //   - state.remove('X')
    //   - settings.remove('Y')
    state.ROOM_NAME = app.getLabel()
    state.SCENE_PBSG_APP_NAME = "pbsg_${state.ROOM_NAME}"
/*
settings.remove('scene^Chill^indepenent^02')
settings.remove('scene^41^independent^02')
settings.remove('scene^41^repeater^Ra2K-1-1848')
settings.remove('scene^Chill^independent^02')
settings.remove('scene^Chill^independent^Ra2D-59-1848')
settings.remove('scene^Chill^repeater^Ra2K-1-1848')
settings.remove('scene^Day^independent^02')
settings.remove('scene^Day^independent^Ra2D-59-1848')
settings.remove('scene^Day^repeater^Ra2K-1-1848')
settings.remove('scene^Night^independent^02')
settings.remove('scene^Night^independent^Ra2D-59-1848')
settings.remove('scene^Night^repeater^Ra2K-1-1848')
settings.remove('scene^Party^independent^02')
settings.remove('scene^Party^independent^Ra2D-59-1848')
settings.remove('scene^Party^repeater^Ra2K-1-1848')
settings.remove('scene^Supplement^independent^02')
settings.remove('scene^Supplement^independent^Ra2D-59-1848')
settings.remove('scene^Supplement^repeater^Ra2K-1-1848')
settings.remove('scene^TV^independent^02')
settings.remove('scene^TV^independent^Ra2D-59-1848')
settings.remove('scene^TV^repeater^Ra2K-1-1848')
settings.remove('scene^Cook^repeater^Ra2K-1-1848')
settings.remove('scene^Off^repeater^Ra2K-1-1848')
settings.remove('scene^Chill^repeater^Ra2K-1-1848')
settings.remove('scene^Cook^repeater^Ra2K-1-1848')
settings.remove('scene^Day^repeater^Ra2K-1-1848')
settings.remove('scene^Night^repeater^Ra2K-1-1848')
settings.remove('scene^Off^repeater^Ra2K-1-1848')
settings.remove('scene^Party^repeater^Ra2K-1-1848')
settings.remove('scene^Supplement^repeater^Ra2K-1-1848')
settings.remove('scene^TV^repeater^Ra2K-1-1848')
*/


    section {
      solicitLog()                          // <- provided by Utils
      solicitModeNamesAsSceneNames()
      solicitCustomScenes()
      updateScenes()
      solicitSceneForModeName()
      solicitSeeTouchKeypads (
        'lutronSeeTouchKeypads',
        'Identify <b>ALL Keypads</b> with buttons that impact <b>Room scenes</b>.'
      )
      solicitLutronLEDs(
        'lutronSceneButtons',
        'Identify <b>All LEDs/Buttons</b> that enable <b>Room scenes</b>.'
      )
      if (state.scenes == null || settings?.lutronSceneButtons == null) {
        paragraph(red('Scene activation buttons are pending pre-requisites.'))
      } else {
        selectLedsForListItems(
          state.scenes,
          settings.lutronSceneButtons,
          'sceneButton'
        )
        mapKpadDNIandButtonToItem('sceneButton')
        ledDniToScene()
      }
      //-----> TBD START
      //solicitLutronPicos(
      //  'lutronPicoButtons',
      //  'W I P -> Identify <b>Picos</b> and <b>Pico Buttons</b> that enable <b>Room scenes</b>.',
      //  [ required: false ]
      //)
      //-> Process Picos similar to Leds above ?!
      //-----> TBD END
      solicitLutronMainRepeaters(
        'lutronMainRepeaters',
        'Identify Repeaters that host integration buttons for <b>Room scenes</b>.'
      )
      solicitSwitches(
        'independentDevices',
        'Identify devices <b>NOT</b> set via Lutron integration buttons.',
        [ required: false]
      )

      if (state.scenes && (settings.independentDevices || settings.lutronMainRepeaters)) {
        solicitRoomScene()
        processRoomScenes()
      } else {
        paragraph red('Soliciation of Room scenes is pending pre-requisite data.')
      }
      if (state.scenes == null) {
        paragraph red('Management of child apps is pending selection of Room scenes.')
      } else {
        keepOldestAppObjPerAppLabel([state.SCENE_PBSG_APP_NAME], settings.log)
        ArrayList switchNames = [*state.scenes, 'AUTOMATIC', 'MANUAL']
        pbsgChildAppDrilldown(
          state.SCENE_PBSG_APP_NAME,
          'roomPBSG',
          'roomPbsgPage',
          switchNames,      // [*state.scenes, 'AUTOMATIC', 'MANUAL'],
          'AUTOMATIC'
        )
      }
      paragraph(
        heading('Debug<br/>')
        + "${ displayState() }<br/>"
        + "${ displaySettings() }"
      )
    }
  }
}

// -----------------------------------------------------
// R O O M S   S C E N E S   P A G E   &   S U P P O R T
// -----------------------------------------------------

void solicitModeNamesAsSceneNames () {
  input(
    name: 'modeNamesAsSceneNames',
    type: 'enum',
    title: '<span style="margin-left: 10px;">' \
           + emphasis('Select "Mode Names" to use as "Scene Names" <em>(optional)</em>:') \
           + '</span>',
    submitOnChange: true,
    required: false,
    multiple: true,
    options: getLocation().getModes().collect{ mode -> mode.name }?.sort()
  )
}

void solicitCustomScenes () {
  String prefix = 'customScene'
  LinkedHashMap<String, String> slots = [
    "${prefix}1": settings["${prefix}1"],
    "${prefix}2": settings["${prefix}2"],
    "${prefix}3": settings["${prefix}3"],
    "${prefix}4": settings["${prefix}4"],
    "${prefix}5": settings["${prefix}5"],
    "${prefix}6": settings["${prefix}6"],
    "${prefix}7": settings["${prefix}7"],
    "${prefix}8": settings["${prefix}8"],
    "${prefix}9": settings["${prefix}9"]
  ]
  LinkedHashMap<String, String> filled = slots.findAll{it.value}
  // Only present 1 empty sceen "slot" at a time.
  LinkedHashMap<String, String> firstOpen = slots.findAll{!it.value}?.take(1)
  LinkedHashMap<String, String> custom = \
    firstOpen + filled.sort{ a, b -> a.value <=> b.value }
  paragraph emphasis('Add Custom Scene Names <em>(optional)</em>:')
  custom.each{ key, value ->
    input(
      name: key,
      type: 'text',
      title: "<b>Custom Scene Name:</b>",
      width: 2,
      submitOnChange: true,
      required: false,
      defaultValue: value
    )
  }
}

void updateScenes () {
  List<String> scenes = settings.modeNamesAsSceneNames ?: []
  scenes = scenes.flatten()
  String prefix = 'customScene'
  List<String> customScenes = [
    settings["${prefix}1"],
    settings["${prefix}2"],
    settings["${prefix}3"],
    settings["${prefix}4"],
    settings["${prefix}5"],
    settings["${prefix}6"],
    settings["${prefix}7"],
    settings["${prefix}8"],
    settings["${prefix}9"],
  ].findAll{it != null}
  if (customScenes) {
    scenes << customScenes
    scenes = scenes.flatten()
  }
  scenes = scenes.sort()
  state.scenes = scenes.size() > 0 ? scenes : null
}

void solicitNonLutronDevicesForWhaRoom () {
  input(
    name: 'nonLutronDevices',
    title: emphasis('Identify Required Non-Lutron Devices'),
    type: 'enum',
    width: 6,
    options: parent.getNonLutronDevicesForRoom(state.ROOM_NAME).collectEntries{ d ->
      [d, d.displayName]
    },
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void solicitSceneForModeName () {
  if (state.scenes == null) {
    paragraph red('Mode-to-Scene selection will proceed once scene names exist.')
  } else {
    paragraph emphasis('Select scenes for per-mode automation:')
    getLocation().getModes().collect{mode -> mode.name}.each{ modeName ->
      String inputName = "modeToScene^${modeName}"
      String defaultValue = settings[inputName]
        ?: state.scenes.contains(modeName) ? modeName : null
      input(
        name: inputName,
        type: 'enum',
        title: modeName,
        width: 2,
        submitOnChange: true,
        required: true,
        multiple: false,
        options: state.scenes,
        defaultValue: defaultValue
      )
    }
  }
}

List<String> picoButtons (DevW pico) {
  String dN = pico.displayName
  return [
    "${dN}^1^Top",
    "${dN}^2^Up",
    "${dN}^3^Middle",
    "${dN}^4^Down",
    "${dN}^5^Bottom"
  ]
}

List<String> picoButtonPicklist (List<DevW> picos) {
  List<String> results = []
  picos.each{ pico -> results << picoButtons(pico) }
  results = results.flatten()
  return results
}

void selectPicoButtonsForScene() {
  if (state.scenes == null) {
    paragraph(red(
      'Once scene names exist, this section will solicit affiliated pico buttons.'
    ))
  } else {
    List<DevW> picos = parent.getPicoDevices()
    //log.trace "R_${state.ROOM_NAME} selectPicoButtonsForScene() picos [A]: ${picos}"
    state.scenes.each{ sceneName ->
      input(
          // Head's Up:
          //   - circa Aug-2023, Hubitat translates settings.Xyz to settings.xyz
          name: "${sceneName}_PicoButtons",
          type: 'enum',
          title: emphasis("Pico Buttons activating ${state.ROOM_NAME} '${sceneName}'."),
          width: 6,
          submitOnChange: true,
          required: false,
          multiple: true,
          options: picoButtonPicklist(picos)
        )
    }
  }
}

void solicitRoomScene () {
  // Display may be full-sized (12-positions) or phone-sized (4-position).
  // For phone friendliness, work one scene at a time.
  if (state.scenes == null) {
    paragraph red('Identification of Room Scene deetails selection will proceed once scene names exist.')
  } else {
    state.scenes?.each{ sceneName ->
      Integer col = 2
      paragraph("<br/><b>${ sceneName } →</b>", width: 2)
      settings.independentDevices?.each{ d ->
        String inputName = "scene^${sceneName}^Independent^${d.getDeviceNetworkId()}"
        //if (settings.log) log.trace(
        //  "R_${state.ROOM_NAME} solicitRoomScene() [INDEPENDENT] inputName: >${inputName}<"
        //  + ", getLabel(): ${red(d.getLabel())}, getDeviceNetworkId(): ${red(d.getDeviceNetworkId())}"
        //)
        col += 2
        input(
          name: inputName,
          type: 'number',
          title: "<b>${ d.getLabel() }</b><br/>Level 0..100",
          width: 2,
          submitOnChange: true,
          required: false,
          multiple: false,
          defaultValue: 0
        )
      }
      // ?.sort{ d -> d.getLabel() }
      settings.lutronMainRepeaters?.each{d ->
      String inputName = "scene^${sceneName}^Repeater^${d.getDeviceNetworkId()}"
        //if (settings.log) log.trace(
        //  "R_${state.ROOM_NAME} solicitRoomScene() [REPEATERS] inputName: >${inputName}<"
        //)
        col += 2
        input(
          name: inputName,
          type: 'number',
          title: "<b>${d.getLabel()}</b><br/>Button #",
          width: 2,
          submitOnChange: true,
          required: false,
          multiple: false,
          defaultValue: 0
        )
      }
      // Fill to end of logical row
      while (col++ % 12) {
        paragraph('', width: 1)
      }
    }
  }
}

void ledDniToScene () {
  Map<String, String> result = [:]
  state.kpadButtons.collect{ kpadDni, buttonMap ->
    buttonMap.each{ buttonNumber, targetScene ->
      result["${kpadDni}-${buttonNumber}"] = targetScene
    }
  }
  state.kpadButtonDniToTargetScene = result
}

void updateLeds (String currScene) {
  settings.lutronSceneButtons.each{ ledObj ->
    String dni = ledObj.getDeviceNetworkId()
    String sceneTarget = state.kpadButtonDniToTargetScene[dni]
    // Note: If sceneTarget is null, all ledObj's are turned off.
    if (currScene == sceneTarget) {
      if (settings.log) log.trace(
        "Turning on LED ${dni} for ${state.ROOM_NAME} scene ${sceneTarget}."
      )
      ledObj.on()
    } else {
      if (settings.log) log.trace(
        "Turning off LED ${dni} for ${state.ROOM_NAME} scene ${sceneTarget}."
      )
      ledObj.off()
    }
  }
}

String getSceneForMode(String mode = getLocation().getMode()) {
  String result = settings["modeToScene^${mode}"]
  if (settings.log) {
    log.trace "R_${state.ROOM_NAME} getSceneForMode() <b>${mode} -> ${result}</b>"
  }
  return result
}

void pbsgVswTurnedOn (String currentScene) {
  //log.trace(
  //  "R_${state.ROOM_NAME} pbsgVswTurnedOn() WhaRooms scene: <b>'${currentScene}'</b> activated."
  //)
  state.currentScene = currentScene
  updateLeds(currentScene)
log.trace "DEBUG #380"
  switch(currentScene) {
    case 'AUTOMATIC':
      if (settings.log) log.trace "R_${state.ROOM_NAME} pbsgVswTurnedOn() processing AUTOMATIC"
log.trace "DEBUG #384"
      activateScene(getSceneForMode())
      break;
    case 'MANUAL':
      if (settings.log) log.trace "R_${state.ROOM_NAME} pbsgVswTurnedOn() ignoring MANUAL"
log.trace "DEBUG #389"
      // DO NOTHING
      break;
    default:
      if (settings.log) log.trace "R_${state.ROOM_NAME} pbsgVswTurnedOn() processing '${currentScene}'"
log.trace "DEBUG #394"
      activateScene(currentScene)
  }
}

void activateScene (String scene) {
  // Push Repeater buttons and execute Independent switch/dimmer levels.
  if (settings.log) log.trace "R_${state.ROOM_NAME} activateScene('${scene}') "
  // Values are found at ...
  //   state.sceneToRepeater[sceneName][dni]
  //   state.sceneToIndependent[sceneName][dni]
  state.sceneToRepeater[scene].each{ repeaterDni, buttonNumber ->
    if (settings.log) log.trace(
      "R_${state.ROOM_NAME} activateScene('${scene}') repeater: ${repeaterDni}, "
      + "button: ${buttonNumber}"
    )
  }
  state.sceneToIndependent[scene].each{ deviceDni, level ->
    if (settings.log) log.trace(
      "R_${state.ROOM_NAME} activateScene('${scene}') device: ${deviceDni}, "
      + "level: ${level}"
    )
  }
}

void processRoomScenes() {
  // Reset state for the Repeater/Independent per-scene device values.
  state['sceneToRepeater'] = [:]
  state['sceneToIndependent'] = [:]
  settings.each{ key, value ->
    //  key w/ delimited data "scene^Night^Independent^Ra2D-59-1848"
    //                               sceneName
    //                                     deviceType
    //                                                 deviceDni
    List<String> parsedKey = key.tokenize('^')
    // Only process settiings keys with the "scene" prefix.
    if (parsedKey[0] == 'scene') {
      // Circa 2023-Sep, no object destructuring syntax in Grooy.
      String sceneName = parsedKey[1]
      String deviceType = parsedKey[2]
      String deviceDNI = parsedKey[3]
      // If missing, create an empty map for the scene's deviceDNI->value data.
      // Note: Hubitat's dated version of Groovy lacks null-safe indexing.
      String stateKey = "sceneTo${deviceType}"
      if (!state[stateKey][sceneName]) state[stateKey][sceneName] = [:]
      // Populate the current deviceDNI->value data.
      state[stateKey][sceneName][deviceDNI] = value
    }
  }
}

// -------------------------------
// S T A T E   M A N A G E M E N T
// -------------------------------

void removeAllChildApps () {
  getAllChildApps().each{ child ->
    if (settings.log) log.trace(
      "R_${state.ROOM_NAME} removeAllChildApps removing ${child.getLabel()} (${child.getId()})"
    )
    deleteChildApp(child.getId())
  }
}

void installed() {
  if (settings.log) log.trace "R_${state.ROOM_NAME} installed() for '${state.ROOM_NAME}'."
  initialize()
}

void uninstalled() {
  if (settings.log) log.trace "R_${state.ROOM_NAME} uninstalled() for '${state.ROOM_NAME}'."
  removeAllChildApps()
}

void updated() {
  if (settings.log) log.trace "R_${state.ROOM_NAME} updated() for '${state.ROOM_NAME}'."
  unsubscribe()  // Suspend event processing to rebuild state variables.
  initialize()
}

void repeaterHandler (Event e) {
  if (settings.log) log.trace(
    "R_${state.ROOM_NAME} testHandler() for '${state.ROOM_NAME}' w/ event: ${e}"
  )
  if (settings.log) logEventDetails(e, false)
}

void keypadToVswHandler (Event e) {
  // Design Note
  //   - The field e.deviceId arrives as a number and must be cast toString().
  //   - Hubitat runs Groovy 2.4. Groovy 3 constructs - x?[]?[] - are not available.
  //   - Keypad buttons are matched to state data to activate a target VSW.
  if (e.name == 'pushed') {
    String targetVsw = state.kpadButtons.getAt(e.deviceId.toString())?.getAt(e.value)
    if (settings.log) log.trace(
      "R_${state.ROOM_NAME} keypadToVswHandler() for '${state.ROOM_NAME}' "
      + "<b>Keypad Device Id:</b> ${e.deviceId}, "
      + "<b>Keypad Button:</b> ${e.value}, "
      + "<b>Affiliated Switch:</b> ${targetVsw}"
    )
    // Turn on appropriate pbsg-modes-X VSW.
    if (targetVsw) app.getChildAppByLabel(state.SCENE_PBSG_APP_NAME).toggleSwitch(targetVsw)
  } else {
    if (settings.log) log.trace(
      "R_${state.ROOM_NAME} keypadToVswHandler() for '${state.ROOM_NAME}' unexpected event "
      + "name '${e.name}' for DNI '${e.deviceId}'"
    )
  }
}

void modeHandler (Event e) {
  logEventDetails(e)
  //log.error('debug Event e at line #496 !!!')
  if (e.name == 'mode') {
    switch(e.value) {
      case 'AUTOMATIC':
        if (settings.log) log.trace "R_${state.ROOM_NAME} modeHandler() processing AUTOMATIC"
        activateScene(getSceneForMode())
        break;
      case 'MANUAL':
        if (settings.log) log.trace "R_${state.ROOM_NAME} modeHandler() ignoring MANUAL"
        // DO NOTHING
        break;
      default:
        if (settings.log) log.trace "R_${state.ROOM_NAME} modeHandler() processing '${e.value}'"
        activateScene(e.value)
    }
  }
}

void initialize() {
  if (settings.log) log.trace "R_${state.ROOM_NAME} initialize() of '${state.ROOM_NAME}'."
  if (settings.log) log.trace "R_${state.ROOM_NAME} subscribing to Lutron Telnet >${settings.lutronTelnet}<"
  //if (settings.log) log.trace "R_${state.ROOM_NAME} subscribing to Lutron Repeaters >${settings.lutronRepeaters}<"
  //settings.lutronMainRepeaters.each{ d ->
  //  DevW device = d
  //  if (settings.log) log.trace "R_${state.ROOM_NAME} subscribing to ${device.displayName} ${device.id}"
  //  subscribe(device, repeaterHandler, ['filterEvents': false])
  //}
  if (settings.log) log.trace "R_${state.ROOM_NAME} subscribing to lutron SeeTouch Keypads >${settings.seeTouchKeypad}<"
  settings.lutronSeeTouchKeypads.each{ d ->
    DevW device = d
    if (settings.log) log.trace "R_${state.ROOM_NAME} subscribing to ${device.displayName} ${device.id}"
    subscribe(device, keypadToVswHandler, ['filterEvents': false])
  }
  if (settings.log) log.trace "R_${state.ROOM_NAME} subscribing to modeHandler"
  subscribe(location, "mode", modeHandler)
}
