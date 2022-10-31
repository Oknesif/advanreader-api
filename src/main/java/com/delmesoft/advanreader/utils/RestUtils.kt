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

import java.io.*
import java.net.HttpURLConnection
import java.net.URL

class RestUtils {
    private val sb = StringBuilder()
    var connectTimeout = 0

    @Throws(IOException::class)
    fun sendGet(url: URL): String? {
        val conn = url.openConnection() as HttpURLConnection
        return try {
            getData(conn.inputStream)
        } catch (e: Exception) {
            getData(conn.errorStream)
        }
    }

    @Throws(IOException::class)
    fun sendPut(url: URL, postData: String?): String? {
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeout
        conn.requestMethod = "PUT"
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5")

        // Send post request
        conn.doOutput = true
        DataOutputStream(conn.outputStream).use { dos -> dos.writeBytes(postData) }
        return try {
            getData(conn.inputStream)
        } catch (e: Exception) {
            getData(conn.errorStream)
        }
    }

    @Synchronized
    @Throws(IOException::class)
    private fun getData(`is`: InputStream?): String? {
        if (`is` != null) {
            try {
                BufferedReader(InputStreamReader(`is`, "UTF-8")).use { `in` ->
                    var line: String?
                    while (`in`.readLine().also { line = it } != null) {
                        sb.append(line)
                    }
                    return sb.toString()
                }
            } finally {
                sb.setLength(0)
            }
        }
        return null
    }
}