package com.hyntix.pdf.viewer.exception

@Deprecated("")
class FileNotFoundException : RuntimeException {
    constructor(detailMessage: String?) : super(detailMessage)
    constructor(detailMessage: String?, throwable: Throwable?) : super(detailMessage, throwable)
}
