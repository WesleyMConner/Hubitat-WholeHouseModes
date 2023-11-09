createPbsg (
  'ModePbsg',
  GetModeNames(),
  getGlobalVar('DEFAULT_MODE').value,
)

subscribe(vsw, "PbsgCurrentSwitch", modePbsgHandler, ['filterEvents': false])

void modePbsgHandler (Event e) {
  logDebug('modePbsgHandler()', e.descriptionText)
}
