package r4.jvm

/** Fail even if production catches the exception: test.doLast checks this persistent marker. */
fun unavailable(symbol: String): Nothing {
    java.io.File(checkNotNull(System.getProperty("r4.shimLog")))
        .appendText("$symbol\n", Charsets.UTF_8)
    throw UnsupportedOperationException("r4-jvm: shim solo de compilación: $symbol")
}
