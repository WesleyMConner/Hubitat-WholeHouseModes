
/*
pbsgGetOrCreateInstance (
  'ModePbsg',
  GetModeNames(),
  getGlobalVar('defaultMode').value
)

subscribe(vsw, "PbsgCurrentSwitch", modePbsgHandler, ['filterEvents': false])

void modePbsgHandler (Event e) {
  logDebug('modePbsgHandler()', e.descriptionText)
}
*/