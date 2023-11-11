/*
 * SYNCHRONOUS METHODS
 *
 *   manageVsws (List<String> buttons) : void
 *     - Reconciles VSW devices (by DNI) to Buttons
 *
 *   configureVsws (List<String> buttons, String onButton = null) : void
 *
 * ASYNCHRONOUS EVENTS CONSUMED
 *
 *   vswHandler (Event e) : void
 *     - Invoked libPbsgCore Methods
 *         on (String button) : LatestStateMap
 *         off (String button) : LatestStateMap
 *     - Actions taken Based on LatestStateMap
 *         turns On VSWs for 'enabled' buttons (if not already on)
 *         turns Off VSWs for 'disabled' buttons (if not already off)
 *
 * ASYNCHRONOUS EVENTS EMITTED
 *   none
 *
 * INTERNAL STATE
 *
 *   String dniPrefix
 *
 *   Map<String vsw, Boolean enabled|disabled> vswState
 *     - VSWs are Enabled (on) or Disabled (off)

