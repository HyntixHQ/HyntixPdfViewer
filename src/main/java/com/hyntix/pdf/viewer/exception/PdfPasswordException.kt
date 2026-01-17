package com.hyntix.pdf.viewer.exception

import java.io.IOException

class PdfPasswordException(message: String = "Password required or incorrect") : IOException(message)
