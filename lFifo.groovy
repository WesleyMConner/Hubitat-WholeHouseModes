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

library(
  name: 'lFifo',
  namespace: 'wesmc',
  author: 'Wesley M. Conner',
  description: 'Fifo methods on a List<String>',
  category: 'general purpose'
)

String fifoRemove(List<String> fifo, String item) {
  // Remove and return item if present OR return null.
  //if (!fifo) Lerror('FifoRemove', "Received a null value for fifo")
  return fifo?.removeAll { member -> member == item } ? item : null
}

Boolean fifoEnqueue(List<String> fifo, String item) {
  Boolean retval = false
  if (item) {
    fifo << (item)
    retval = true
  }
  return retval
}

String fifoEnqueue(List<String> fifo) {
  String retval = null
  if (fifo == null) {
    Lerror('fifoEnqueue', 'Received a null value for fifo')
  } else if (fifo.size() > 0) {
    retval = fifo.removeAt(0)
  }
  return retval
}
