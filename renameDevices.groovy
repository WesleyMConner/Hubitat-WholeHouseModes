
import com.hubitat.app.DeviceWrapper as DevW
import com.hubitat.app.InstalledAppWrapper as InstAppW
import com.hubitat.hub.domain.Event as Event
import com.hubitat.hub.domain.Location as Loc

// The Groovy Linter generates false positives on Hubitat #include !!!
#include wesmc.lHExt
#include wesmc.lHUI
#include wesmc.lPbsgV2

definition (
  name: 'RenameDevices',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'FIXING STUFF',
  category: '',           // Not supported as of Q3'23
  iconUrl: '',            // Not supported as of Q3'23
  iconX2Url: '',
  singleInstance: true
)

preferences {
  page(name: 'RenameDevicesPage', title: 'RenameDevicesPage')
}

void installed() {
  logInfo('installed', 'Entered')
  initialize()
}

void updated() {
  logInfo('updated', 'Entered')
  initialize()
}

void idDimmers() {
  input(
    name: 'dimmers',
    title:'dimmers',
    type: 'device.LutronDimmer',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void idKpads1() {
  input(
    name: 'kpads1',
    title:'kpads1',
    type: 'device.LutronSeeTouchKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void idKpads2() {
  input(
    name: 'kpads2',
    title:'kpads2',
    type: 'device.LutronKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void idMS() {
  input(
    name: 'ms',
    title:'ms',
    type: 'device.LutronMotionSensor',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void idPicos() {
  input(
    name: 'picos',
    title:'picos',
    type: 'device.LutronFastPico',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void idSwitches() {
  input(
    name: 'switches',
    title:'switches',
    type: 'device.LutronSwitch',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void idKpads3() {
  input(
    name: 'kpads3',
    title:'kpads3',
    type: 'device.LutronSeeTouchKeypad',
    submitOnChange: true,
    required: false,
    multiple: true
  )
}

void initialize() {
  ArrayList devices = [
    *settings.dimmers,
    *settings.kpads1,
    *settings.kpads2,
    *settings.ms,
    *settings.picos,
    *settings.switches,
    *settings.kpads3
  ]
  Map dni2Name = [
    'Ra2D-1-2145':  'Pro2',
    'Ra2Q-6-2145':  'Lanai-GrillCanPico',
    'Ra2Q-9-2145':  'RhsBdrm-EntryPico',
    'Ra2Q-10-2145': 'RhsBdrm-TablePico',
    'Ra2D-2-2145':  'RhsBdrm-TableLamps',
    'Ra2D-3-2145':  'RhsBdrm-FloorLamp',
    'Ra2D-4-2145':  'Lanai-DownLighting',
    'Ra2D-5-2145':  'Lanai-GrillCan',
    'Ra2D-7-2145':  'Lanai-OutdoorDining',
    'Ra2D-32-1848': 'DROP-CentralKpad',
    'Ra2D-38-1848': 'DROP-DiningKpad',
    'Ra2D-44-1848': 'DROP-GarageKpad',
    'Ra2D-80-1848': 'DROP-KitchenKpad',
    'Ra2D-41-1848': 'DROP-LanaiKpad',
    'Ra2D-29-1848': 'DROP-MainEntryKpad',
    'Ra2D-10-1848': 'DROP-MainFoyerKpad',
    'Ra2D-73-1848': 'DROP-Primary15ButKpad',
    'Ra2D-20-1848': 'DROP-PrimaryEntryKpad',
    'Ra2D-46-1848': 'DROP-PrimarySliderKpad',
    'Ra2D-63-1848': 'Den-BevStation',
    'Ra2D-62-1848': 'Den-DenCans',
    'Ra2D-43-1848': 'Den-HallCans',
    'Ra2D-34-1848': 'Den-KitchenTable',
    'Ra2D-23-1848': 'Den-TvWall',
    'Ra2D-64-1848': 'Den-UplightLeds',
    'Ra2D-59-1848': 'DenLamp-Dimmer',
    'Ra2Q-50-1848': 'DenLamp-Pico',
    'Ra2D-76-1848': 'Guest-LhsHallCans',
    'Ra2D-28-1848': 'Guest-RhsHallCans',
    'Ra2D-5-1848':  'Guest-RhsHallLamp',
    'Ra2D-53-1848': 'Hers-CeilingLight',
    'Ra2M-86-1848': 'Hers-MotionSensor',
    'Ra2D-56-1848': 'His-CeilingLight',
    'Ra2M-87-1848': 'His-MotionSensor',
    'Ra2D-61-1848': 'Kitchen-Cans',
    'Ra2D-65-1848': 'Kitchen-CounterLeds',
    'Ra2D-4-1848':  'Kitchen-Soffit',
    'Ra2D-68-1848': 'Lanai-LedUplighting',
    'Ra2D-39-1848': 'Lanai-Pendants',
    'Ra2D-54-1848': 'Lanai-Pool',
    'Ra2D-18-1848': 'Laundry-Cans',
    'Ra2M-8-1848':  'Laundry-MotionSensor',
    'Ra2M-72-1848': 'LhsBath-MotionSensor',
    'Ra2D-27-1848': 'LhsBath-Shower',
    'Ra2D-74-1848': 'LhsBath-Soffit',
    'Ra2Q-3-1848':  'LhsBdrm-EntryPico',
    'Ra2D-45-1848': 'LhsBdrm-FloorLamp',
    'Ra2D-36-1848': 'LhsBdrm-TableLamps',
    'Ra2Q-48-1848': 'LhsBdrm-TablePico',
    'Ra2D-37-1848': 'Main-ArtNiche',
    'Ra2D-9-1848':  'Main-DiningTable',
    'Ra2D-31-1848': 'Main-FoyerCans',
    'Ra2D-33-1848': 'Main-LivRmLamps',
    'Ra2D-90-1848': 'NA-BackOutlet',
    'Ra2D-67-1848': 'DROP-PantryLedFixture',
    'Ra2D-69-1848': 'DROP-PantrySensor',
    'Ra2M-60-1848': 'DROP-RhsClosetMotionSensor',
    'Ra2D-42-1848': 'DROP-RhsHallCloset',
    'Ra2D-15-1848': 'DROP-WaterCloset',
    'Ra2Q-30-1848': 'Office-DeskPico',
    'Ra2D-35-1848': 'Office-LeftTableLamp',
    'Ra2D-51-1848': 'PrimBath-Cans',
    'Ra2D-47-1848': 'PrimBath-Soffit',
    'Ra2D-52-1848': 'PrimBath-TubAndShower',
    'Ra2D-19-1848': 'Primary-TableLamps',
    'Ra2D-1-1848':  'REP1',
    'Ra2D-83-1848': 'REP2',
    'Ra2D-16-1848': 'RhsBath-Lamp',
    'Ra2M-49-1848': 'RhsBath-MotionSensor',
    'Ra2D-78-1848': 'RhsBath-Shower',
    'Ra2D-79-1848': 'RhsBath-Vanity',
    'Ra2D-7-1848':  'DROP-VCRX Output 1',
    'Ra2D-22-1848': 'DROP-VCRX Output 2',
    'Ra2D-77-1848': 'DROP-VCRX Output 3',
    'Ra2D-81-1848': 'DROP-VCRX Output 4',
    'Ra2D-82-1848': 'DROP-Visor CTRL',
    'Ra2D-75-1848': 'Yard-BackPorch',
    'Ra2D-6-1848':  'Yard-FrontPorch',
    'Ra2D-26-1848': 'Yard-ShopPorch',
    'Ra2D-71-1848': 'Yard-Spots'
  ]
  logInfo('#206', "dni2Name: ${dni2Name}")
  devices.each{ d ->
    logInfo('#208', "Processing ${d}")
    d.each{ x ->
      logInfo('#210', "x: ${x}")
    }
    String dni = d.getDeviceNetworkId()
    String name = dni2Name.getAt(dni)
    if (name) {
      logInfo('#215', "${dni},${name}")
      d.setName(name)
      d.setLabel(name)
      d.setDisplayName(name)
    } else {
      logInfo('#217', "No name for ${dni}")
    }
  }
}

Map RenameDevicesPage() {
  return dynamicPage(
    name: 'RenameDevicesPage',
    install: true,
    uninstall: true
  ) {
    section {
      idDimmers()
      idKpads1()
      idKpads2()
      idMS()
      idPicos()
      idSwitches()
      idKpads3()
    }
  }
}
