// ---------------------------------------------------------------------------------
// ( L U T R O N )   R A 2
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

/*
String normalizeRowCsv(String raw) {
  return raw?.trim()?.replaceAll(', ', ',')
}

void ra2IR_Ingest(String rawData) {
  // Use split (vs tokenize) to preserve empty rows.
  state.ra2IR = [ 'rows': rawData.split('\n'), 'row': 0 ]
}

Map ra2IR() {
  if (!state.ra2IR) {
    logError('ra2IR', 'Cannot find state.ra2IR. Missing ra2IR_Ingest(...)?')
  }
  return state.ra2IR
}

String ra2IR_currRow() {
  Map rpt = ra2IR()
  return normalizeRowCsv(rpt.rows[rpt.row])
}

String ra2IR_nextRow() {
  Map rpt = ra2IR()
  rpt.row = ++rpt.row
  state.ra2IR = [ 'rows': rpt.rows, 'row': rpt.row ]
  return normalizeRowCsv(rpt.rows[rpt.row])
}

String[] ra2IR_currRowAsCols() {
  // Use split (vs tokenize) to preserve empty rows.
  return ra2IR_currRow().split(',')
}

String[] ra2IR_nextRowAsCols() {
  // Use split (vs tokenize) to preserve empty rows.
  return ra2IR_nextRow().split(',')
}

void skipEmptyRows() {
  Map rpt = ra2IR()
  while (rpt.rows[rpt.row] == '') { rpt.row = ++rpt.row }
  state.ra2IR = [ 'rows': rpt.rows, 'row': rpt.row ]
}
*/