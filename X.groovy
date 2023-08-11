List<String> calledItems = ['A', 'B', 'C', ...]

settings.calledItems.each{ item ->
  href (
    title: "${item} Stuff",
    page: calledPage,
    param: [
      settingsPrefix: item,
    ],
    style: 'internal'
  )
}



void solicitInput1 (String settingsPrefix) {
  input (
    name: "${settingsPrefix}-thing",
      :
  )
}

void solicitInput2 (String settingsPrefix) {
  input (
    name: "${settingsPrefix}-anotherThing",
      :
  )
}

def calledPage (param) {
  if (param) state.XXXXX = param.settingsPrefix
  else param.settingsPrefix = state.XXXXX
  dynamicPage(name: 'calledPage') {
    section {
      String item = param.settingsPrefix
      solicitInput1(item)
      solicitInput2(item)
      solicitInput3(item)
        :
    }
  }
}

