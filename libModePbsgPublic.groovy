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
  // The recommended modePbsgName is 'whaModePbsg'.
  InstAppW modePbsg = getChildAppByLabel(modePbsgName)
  Ldebug('createModePbsg()', modePbsgName)
  if (!modePbsg) {
    Ldebug('createModePbsg()', "creating new Mode PBSG instance '${modePbsgName}'")
    modePbsg = app.addChildApp(
      'wesmc',      // See modePBSG.groovy 'definition.namespace'
      'modePBSG',   // See modePBSG.groovy 'definition.name'
      modePbsgName  // PBSG's label/name (id will be a generated integer)
    )
    configPbsg(modePbsgName, modes, defaultMode, logThreshold)
  } else {
    Ltrace('createModePbsg()', "using existing modePbsg instance '${modePbsgName}'")
  }
  return modePbsg
}
