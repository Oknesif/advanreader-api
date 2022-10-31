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

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset

class StringBuffer {
    private var buffer = ByteArray(1024)
    private var index = 0
    fun storeByte(b: Int) {
        resize(index)
        buffer[index++] = b.toByte()
    }

    @Throws(IOException::class)
    fun readLine(`is`: InputStream, charset: Charset?): String? {
        var b: Int
        while (`is`.read().also { b = it } != -1) { // next byte
            if (b == 13) { // carriage return (/r)				
                b = `is`.read() // next byte
                val result = String(buffer, 0, index, charset!!)
                index = 0
                if (b != 10 && b != -1) { // not new line (/n) and not end of input stream
                    storeByte(b) // store byte
                }
                return result // done
            } else if (b == 10) { // new line (/n)
                val i = index
                index = 0
                return String(buffer, 0, i, charset!!) // done
            }
            storeByte(b) // store byte
        }
        return null
    }

    @Throws(IOException::class)
    fun read(`is`: InputStream, length: Int) {
        resize(index + length)
        var count: Int
        var n = 0
        while (n < length) {
            count = `is`.read(buffer, index + n, length - n)
            if (count < 0) throw EOFException()
            n += count
        }
        index += n
    }

    fun resize(size: Int) {
        if (size >= buffer.size) {
            val tmp = ByteArray(size)
            System.arraycopy(buffer, 0, tmp, 0, index)
            buffer = tmp
        }
    }

    fun size(): Int {
        return index
    }

    fun clear() {
        index = 0
    }

    fun toString(charset: Charset?): String {
        return String(buffer, 0, index)
    }
}