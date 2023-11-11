/*
 * SYNCHRONOUS METHODS
 *
 *   init (List<String> buttons, String dfltButton = null) : LatestStateMap
 *
 *   configure (
 *     List<String> buttons,
 *     String dfltButton = null,
 *     String onButton = null
 *   ) : LatestStateMap
 *
 *   on (String button) : LatestStateMap
 *
 *   off (String button) : LatestStateMap
 *
 *   isOn (Button x) : Boolean
 *
 *   isOff (Button x) : Boolean
 *
 *   onButtons () : List<String>
 *
 *   offButtons () : List<String>
 *
 *   currentState () : Map<String button, Boolean enabled|disabled>
 *
 * ASYNCHRONOUS EVENTS EMITTED
 *
 *   currentState is published as a String
 *     - Example: "[ sw1: false, sw2: true, sw3: false ... swN: false]
 *
 * INTERNAL STATE
 *
 *   Map<String button, Boolean enabled|disabled> buttonState
 *     - Button are Enabled (on) or Disabled (off)
