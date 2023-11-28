// ---------------------------------------------------------------------------------
// F I F O   Q U E U E   M E T H O D S
//   Creates a List<String> fifo that reads left (first) to right (last)
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
  name: 'libFifo',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Fifo methods on a List<String>',
  category: 'general purpose'
)

String FifoRemove (List<String> fifo, String item) {
  // Remove and return item if present OR return null.
  //if (!fifo) Lerror('FifoRemove()', "Received a null value for fifo")
  return fifo?.removeAll{ it == item } ? item : null
  Boolean retval = null
  if (fifo == null) {
    Lerror('FifoRemove()', "Received a null value for fifo")
  } else if (fifo.removeAll{ it == item }) {
    retval = item
  }
  return retval
}

Boolean FifoEnqueue (List<String> fifo, String item) {
  Boolean retval = false
  if (fifo == null) {
    Lerror('FifoEnqueue()', "Received a null value for fifo")
  } else if (item) {
    fifo.leftShift(item)
    retval = true
  }
  return retval
}

String FifoDequeue (List<String> fifo) {
  String retval = null
  if (fifo == null) {
    Lerror('FifoDequeue()', "Received a null value for fifo")
  } else if (fifo.size() > 0) {
    retval = fifo.removeAt(0)
  }
  return retval
}

void FifoTest () {
  List<String> fifo = []
  Linfo('#52', "fifo: ${fifo}")
  FifoEnqueue(fifo, 'A')
  Linfo('#54', "fifo: ${fifo}")
  FifoEnqueue(fifo, 'B')
  Linfo('#56', "fifo: ${fifo}")
  FifoEnqueue(fifo, 'C')
  Linfo('#58', "fifo: ${fifo}")
  FifoEnqueue(fifo, 'D')
  Linfo('#60', "fifo: ${fifo}")
  String s1 = FifoRemove(fifo, 'C')
  Linfo('#62', "s1: ${s1}, fifo: ${fifo}")
  s1 = FifoDequeue(fifo)
  Linfo('#64', "s1: ${s1}, fifo: ${fifo}")
  s1 = FifoDequeue(fifo)
  Linfo('#66', "s1: ${s1}, fifo: ${fifo}")
  s1 = FifoDequeue(fifo)
  Linfo('#68', "s1: ${s1}, fifo: ${fifo}")
  s1 = FifoDequeue(fifo)
  Linfo('#70', "s1: ${s1}, fifo: ${fifo}")
}
