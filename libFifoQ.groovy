// ---------------------------------------------------------------------------------
// F I F O   Q U E U E   M E T H O D S
//
//   Copyright (C) 2023-Present Wesley M. Conner
//
//   LICENSE
//     Licensed under the Apache License, Version 2.0 (aka Apache-2.0, the
//     "License"), see http://www.apache.org/licenses/LICENSE-2.0. You may
//     not use this file except in compliance with the License. Unless
//     required by applicable law or agreed to in writing, software
//     distributed under the License is distributed on an "AS IS" BASIS,
//     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
//     implied.
// ---------------------------------------------------------------------------------

library (
  name: 'libFifoQ',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Fifo methods on a List<String>',
  category: 'general purpose'
)

void FifoPush (List<String> q, String item) {
  q.leftShift(item)
  Ltrace('FifoPush()', "Pushed: ${item}, Queue: ${q}")
}

void FifoPushUnique (List<String> q, String item) {
  if (!q.contains(item)) {
    q.leftShift(item)
    Ltrace('FifoPushUnique()', "Pushed: ${item}, Queue: ${q}")
  } else {
    Ltrace('FifoPushUnique()', "Unchanged, ${item} is already present in Queue: ${q}")
  }
}

String FifoPop (List<String> q) {
  String x = q.size() > 0 ? q.removeAt(0) : null
  Ltrace('FifoPop()', "Popped: ${x}, Queue: ${q}")
  return x
}

void FifoRemove (List<String> q, String item) {
  q.removeAll(item)
  Ltrace('FifoRemove()', "Removed: ${item}, Queue: ${q}")
}

String FifoContents (List<String> q) {
  String out = "[${q.join(', ')}]"
  Ltrace('FifoContents()', "Queue: ${out}")
  return out
}

void FifoDemo () {
  List<String> q = []
  FifoContents(q)
  FifoPush(q, 'A')
  FifoPush(q, 'B')
  FifoPush(q, 'C')
  FifoPush(q, 'D')
  FifoPushUnique(q, 'A')
  FifoPush(q, 'E')
  FifoPop(q)
  FifoPop(q)
  FifoRemove(q, 'D')
  FifoPush(q, 'F')
  FifoPop(q)
  FifoPush(q, 'G')
  FifoPop(q)
  FifoPushUnique(q, 'C')
  FifoPop(q)
  FifoPop(q)
  FifoPushUnique(q, 'B')
  FifoPop(q)
  FifoPop(q)
  FifoPop(q)
  FifoPop(q)
  FifoContents(q)
}

//  FifoSize() - Prefer the INSTEAD List<String> size() method.