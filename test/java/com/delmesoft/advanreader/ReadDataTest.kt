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

import com.delmesoft.advanreader.AdvanReader.MemoryBank
import org.junit.Test
import java.util.*

class ReadDataTest {
    @Test
    @Throws(ReaderException::class)
    fun test() {
        val host = "192.168.1.109"
        val settings = Settings()
        settings.port = 3161
        settings.host = host
        settings.antennas = intArrayOf(1)
        settings.session = 1
        settings.searchModeIndex = 2 // 2 = AB
        val txPower = 10.0 // Power
        val txPowers = DoubleArray(settings.antennas.size)
        for (i in txPowers.indices) {
            txPowers[i] = txPower
        }
        settings.txPower = txPowers
        val sensitivity = -70.0
        val txSensitivities = DoubleArray(settings.antennas.size)
        for (i in txSensitivities.indices) {
            txSensitivities[i] = sensitivity
        }
        settings.rxSensitivity = txSensitivities
        val reader: Reader = AdvanReader()
        reader.settings = settings
        reader.readerListener = object : ReaderListenerAdapter() {
            override fun onWrite(read: Read?, result: Int, message: String?) {}
            override fun onRead(read: Read?) {
                // System.out.println("READ: " + read);				
            }

            override fun onConnectionLost() {
                println("Connection Lost")
            }

            override fun onGpi(portNumber: Int, state: Boolean) {
                println("GPI: $portNumber, state: $state")
            }

            override fun onReadData(read: Read?, result: Int, message: String?) {
                System.out.printf("%s (code: %d): %s\n", message, result, read)
            }
        }
        reader.connect()
        println(reader.modelName)

        // EPC
        // reader.readData(MemoryBank.EPC.ordinal(), 2, 6);

        // TID                                              
        reader.readData(MemoryBank.TID.ordinal, 2, 4)

        // Chip Id
        // reader.readData(MemoryBank.TID.ordinal(), 0, 2);

        // Access Pwd
        // reader.readData(MemoryBank.RESERVED.ordinal(), 2, 2);

        // Kill Pwd
        // reader.readData(MemoryBank.RESERVED.ordinal(), 0, 2);
        println("Reading..., press enter to finish.")
        Scanner(System.`in`).use { `in` ->
            `in`.nextLine()
            reader.disconnect()
        }
    }
}