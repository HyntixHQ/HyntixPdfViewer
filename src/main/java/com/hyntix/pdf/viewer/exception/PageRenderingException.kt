package com.hyntix.pdf.viewer.exception

class PageRenderingException(val page: Int, cause: Throwable?) : Exception(cause)
