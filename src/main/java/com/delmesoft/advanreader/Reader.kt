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
package com.delmesoft.advanreader

import com.delmesoft.advanreader.ReaderException

interface Reader {
    @Throws(ReaderException::class)
    fun connect()
    val isConnected: Boolean
    fun disconnect()

    @Throws(ReaderException::class)
    fun readData(bank: Int, address: Int, lenght: Int)

    @Throws(ReaderException::class)
    fun readData(bank: Int, address: Int, lenght: Int, accessPassword: String?)

    @Throws(ReaderException::class)
    fun readData(epc: String?, bank: Int, address: Int, lenght: Int)

    @Throws(ReaderException::class)
    fun readData(epc: String?, bank: Int, address: Int, lenght: Int, accessPassword: String?)

    @Throws(ReaderException::class)
    fun startRead()
    val isReading: Boolean

    @Throws(ReaderException::class)
    fun setKillPassword(epc: String?, killPassword: String?)

    @Throws(ReaderException::class)
    fun setKillPassword(epc: String?, accessPassword: String?, killPassword: String?)

    @Throws(ReaderException::class)
    fun lockTag(epc: String?, accessPassword: String?, lockOptions: LockOptions?)

    @Throws(ReaderException::class)
    fun lockTag(epc: String?, oldAccessPassword: String?, newAccessPassword: String?, lockOptions: LockOptions?)

    @Throws(ReaderException::class)
    fun writeEpc(srcEpc: String, tgtEpc: String)

    @Throws(ReaderException::class)
    fun writeEpc(srcEpc: String, tgtEpc: String, accessPassword: String?)

    @Throws(ReaderException::class)
    fun writeData(epc: String?, data: String?, memoryBank: Int, wordPointer: Short)

    @Throws(ReaderException::class)
    fun writeData(epc: String?, data: String?, memoryBank: Int, wordPointer: Short, accessPassword: String?)

    @Throws(ReaderException::class)
    fun stop()
    val gpoCount: Int

    @Throws(ReaderException::class)
    fun setGpo(portNumber: Int, state: Boolean)

    @Throws(ReaderException::class)
    fun setGpo(stateMap: Map<Int, Boolean>)

    @Throws(ReaderException::class)
    fun setGpo(state: BooleanArray)
    val gpiCount: Int

    @Throws(ReaderException::class)
    fun isGpi(portNumber: Int): Boolean

    @get:Throws(ReaderException::class)
    val gpiState: BooleanArray
    var settings: Settings?

    @Throws(ReaderException::class)
    fun applySettings(settings: Settings?)
    val serial: String?
    val modelName: String?
    var readerListener: ReaderListener?
}