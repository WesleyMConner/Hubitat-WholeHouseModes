  //===== T E S T   B E G I N =============================================
  //===== Closure handlerFactory = { e, pbsgInst ->
  //=====   "Arg '${e}', '${pbsgInst.a}' and '${pbsgInst.b}'."
  //===== }
  //===== def pbsgA = [
  //=====   a: "This is a string",
  //=====   b: "another string,"
  //===== ]
  //===== if (settings.LOG) log.trace "pbsgA: ${pbsgA}"
  //===== def handler = { e -> handlerFactory.call(e, pbsgA) }
  //===== if (settings.LOG) log.trace "handler('puppies'): ${handler('puppies')}"
  //===== T E S T   E N D =================================================