library (
  name: 'libModePbsgPublic',
  namespace: 'wesmc',
  author: 'WesleyMConner',
  description: 'Methods on a Mode PBSG (Pushbutton Switch Group) Instance',
  category: 'general purpose',
  documentationLink: '',
  importUrl: ''
)

/*
InstAppW getModePbsg () {
  InstAppW modePbsg = getChildAppByLabel(modePbsgName)
  if (!modePbsg) {
    Lerror('getModePbsg()', 'Mode PBSG is NOT FOUND')
  }
  if (!modePbsg.vswDnis) {
    Lerror('getModePbsg()', 'Found modePbsg, but WITHOUT vswDnis')
  } else {
    Ldebug('getModePbsg()', "modePbsg.vswDnis: >${modePbsg.vswDnis}<")
  }
  //Ltrace('getModePbsg()', 'Invoking manageChildDevices()')
  //manageChildDevices()
  return modePbsg
}
*/

InstAppW createModePbsg (
    String modePbsgName,
    List<String> modes,
    String defaultMode,
    String logThreshold
  ) {
  Ltrace('createModePbsg()', "At entry")
  // The recommended modePbsgName is 'whaModePbsg'.
  InstAppW modePbsg = getChildAppByLabel(modePbsgName)
  if (modePbsg) {
    Ltrace('createModePbsg()', "is using existing '${getAppInfo(modePbsg)}'")
    //-> if (modePbsg.isPbsgHealthy() == false) {
    Ltrace(
      'createModePbsg()',
      modePbsg.pbsgStateAndSettings('PEEK AT EXISTING MODE PBSG')
    )
    modePbsg.configPbsg(modePbsgName, modes, defaultMode, logThreshold)
    Ltrace(
      'createModePbsg()',
      modePbsg.pbsgStateAndSettings('PEEK AFTER FRESH createModePbsg() CALL')
    )
  } else {
    modePbsg = app.addChildApp(
      'wesmc',      // See modePBSG.groovy 'definition.namespace'
      'modePBSG',   // See modePBSG.groovy 'definition.name'
      modePbsgName  // PBSG's label/name (id will be a generated integer)
    )
    Ldebug('createModePbsg()', "created new '${getAppInfo(modePbsg)}'")
    configPbsg(modePbsgName, modes, defaultMode, logThreshold)
  }
  return modePbsg
}
