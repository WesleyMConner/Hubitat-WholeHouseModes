// ---------------------------------------------------------------------------------
// RA2 Integration Report (RA2IR)
//
// Copyright (C) 2023-Present Wesley M. Conner
//
// LICENSE
// Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
// "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
// not use this file except in compliance with the License. Unless
// required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied.
// ---------------------------------------------------------------------------------

library(
  name: 'lRa2',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Isolate RA2 Methods',
  category: 'general purpose'
)

String ra2ModelToHubitatCode(String model) {
  switch (model) {
    case 'LRF2-OCR2B-P-WH':
      return 'm'
    case 'PJ2-3BRL-GWH-L01':
      return 'q'
    case 'RR-MAIN-REP-WH':
    case 'RR-T15RL-SW':
    case 'RR-VCRX-WH':
      return 'k'
    case 'RRD-H6BRL-WH':
    case 'RRD-W7B-WH':
      return 'w'
    default:
      return 'unknown'
  }
}

String normalizeRowCsv(String raw) {
  return raw?.trim()?.replaceAll(', ', ',')
}

Map ra2IR_Retrieve() {
  // The ra2IR Map has two keys
  //   rows: An ArrayList of raw row data excluding empty rows.
  //    row: The current index in 'rows', which advances as raw data is processed.
  Map ra2IR = atomicState.ra2IR
  if (!ra2IR) {
    if (!settings.ra2IntegReport) {
      logError('ra2IR_Retrieve', 'Missing "settings.ra2IntegReport"')
    } else {
      ra2IR.rows = settings.ra2IntegReport.tokenize('\n')
      ra2IR.row = 0  // Current index in the
    }
  }
  return ra2IR
}

Boolean ra2IR_hasUnprocessedRows() {
  Map rpt = ra2IR_Retrieve()
  return (rpt.row + 1 < rpt.rows.size())
}

String ra2IR_currRow() {
  Map rpt = ra2IR_Retrieve()
  return normalizeRowCsv(rpt.rows[rpt.row])
}

String ra2IR_nextRow() {
  String result
  if (ra2IR_hasUnprocessedRows()) {
    Map rpt = ra2IR_Retrieve()
    rpt.row = ++rpt.row
    atomicState.ra2IR = [ 'rows': rpt.rows, 'row': rpt.row ]
    result = normalizeRowCsv(rpt.rows[rpt.row])
  } else {
    result = 'EOF'
  }
  return result
}

String ra2IR_nextNonEmptyRow() {
  String result
  Boolean emptyRow = true
  while (emptyRow) {
    result = ra2IR_nextRow()
    emptyRow = (result == '')
  }
  return result
}

String[] ra2IR_currRowAsCols() {
  // Use split (vs tokenize) to preserve empty rows.
  return ra2IR_currRow().split(',')
}

String[] ra2IR_nextRowAsCols() {
  // Use split (vs tokenize) to preserve empty rows.
  return ra2IR_nextRow().split(',')
}

String[] ra2IR_nextNonEmptyRowAsCols() {
  // Use split (vs tokenize) to preserve empty rows.
  return ra2IR_nextNonEmptyRow().split(',')
}
