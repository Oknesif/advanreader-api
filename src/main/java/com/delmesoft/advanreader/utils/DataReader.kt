/* 
 * Copyright (c) 2022, Sergio S.- sergi.ss4@gmail.com http://sergiosoriano.com
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package com.delmesoft.advanreader.utils

import org.w3c.dom.Document
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilderFactory

abstract class DataReader {
    private var thread: Thread? = null
    private val stringBuffer: StringBuffer
    fun disconnect() {
        if (isConnected) {
            try {
                thread!!.interrupt()
            } catch (e: Exception) {
            } finally {
                thread = null
            }
        }
    }

    val isConnected: Boolean
        get() = thread != null && !thread!!.isInterrupted

    fun connect(host: String?, port: Int) {
        if (!isConnected) {
            thread = object : Thread(DataReader::class.java.name) {
                override fun run() {
                    try {
                        Socket(host, port).use { socket ->
                            socket.getInputStream().use { inputStream ->
                                val inputSource = InputSource()
                                val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                                val charset = Charset.forName("UTF-8")
                                while (!isInterrupted) {
                                    val xml = readXml(inputStream, charset)
                                    if (!xml!!.isEmpty()) {
                                        val document = documentBuilder.parse(inputSource.setString(xml))
                                        handleDocument(document)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (isConnected) {
                            handleError(e)
                        }
                    } finally {
                        disconnect()
                    }
                }
            }
            thread.start()
        }
    }

    abstract fun handleDocument(document: Document?)
    abstract fun handleError(e: Exception?)

    @Throws(IOException::class)
    protected fun readXml(`is`: InputStream, charset: Charset?): String? {
        return try {

            // *** Read Header ***
            var line: String?
            do {
                line = stringBuffer.readLine(`is`, charset)
            } while (line == null || !line.contains("ADVANNET"))

            // Content-Length:xxxx
            line = stringBuffer.readLine(`is`, charset)
            val contentLength = line!!.split(":").toTypedArray()[1].trim { it <= ' ' }.toInt()

            // Content-Type:text/xml
            line = stringBuffer.readLine(`is`, charset)
            val contenType = line!!.split(":").toTypedArray()[1].trim { it <= ' ' }
            if (contenType != "text/xml") {
                throw RuntimeException("Error unsupported content type: $contenType")
            }
            stringBuffer.readLine(`is`, charset) // empty line

            // *** end Read Header ***
            stringBuffer.read(`is`, contentLength - stringBuffer.size())
            if (stringBuffer.size() != contentLength) {
                throw RuntimeException("Error reading xml")
            }
            stringBuffer.toString(charset)
        } finally {
            stringBuffer.clear()
        }
    }

    init {
        stringBuffer = StringBuffer()
    }
}