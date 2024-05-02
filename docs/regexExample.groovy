
{
  String ws = "\\s+"
  String any = ".*"
  String captureStr = "(\"[^\"]*\")"
  String captureInt = "(\\d+)"
  String row = pro2IR_CurrRow(irMap)
  // Note: EOF should be sufficient in the following while expression
  while (pro2IR_hasUnprocessedRows(irMap) && row != 'EOF') {
    switch (row) {
      // Case statement REs MUST match the entire row !!!
      case ~/$any$captureStr:$ws$captureInt,?/ :         // + named string
        String label = Matcher.lastMatcher.group(1)
        Integer iVal = Matcher.lastMatcher.group(2)
        rootMap_InsertKV(rootMap, label, iVal)
        break
      case ~/$any$captureStr:\s+$captureStr,?/ :         // + named integer
        String label = Matcher.lastMatcher.group(1)
        String sVal = Matcher.lastMatcher.group(2)
        rootMap_InsertKV(rootMap, label, sVal)
        break
      case ~/$any$captureStr:$ws\{$/ :                   // + named map
        String mapName = Matcher.lastMatcher.group(1)
        rootMap_BeginNamedMap(rootMap, mapName)
        break
      case ~/$any$captureStr:$ws\[$/ :                   // + named list
        rootMap_BeginNamedList(rootMap, listName)
        break
      case ~/$any\{$/ :                                  // + unnamed map
        // Assume the current node is a list!
        // Begin a temporary Map for subsequent lines.
        // When popped, left-shift the temporary map onto the list.
        rootMap_BeginUnnamedMap(rootMap)
        break
      //--NONE-> case ~/$any\[$/ :                                  // + unnamed list
      //--NONE->   rootMap_BeginNamedList(rootMap, 'UNNAMED')
      //--NONE->   break
      case ~/$any\}$any/ :                               // complete map
      case ~/$any\]$any/ :                               // complete list
        rootMap_EndNode(rootMap)
        break
      default:
        logError('parsePro2IntegRpt', ['No switch..case match for row',
          "${paddedRowNumber(irMap)}: [${currIndex}] DEFAULT >${row}<"
        ])
    }
  }
}
