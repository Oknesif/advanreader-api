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

class LockOptions {
    var accessPasswordLockType: Int
    var epcLockType: Int
    var killPasswordLockType: Int
    var tidLockType: Int
    var userLockType: Int

    constructor() {
        accessPasswordLockType = NONE
        epcLockType = NONE
        killPasswordLockType = NONE
        tidLockType = NONE
        userLockType = NONE
    }

    constructor(
        epcLockType: Int,
        tidLockType: Int,
        userLockType: Int,
        accessPasswordLockType: Int,
        killPasswordLockType: Int
    ) : super() {
        this.epcLockType = epcLockType
        this.tidLockType = tidLockType
        this.userLockType = userLockType
        this.accessPasswordLockType = accessPasswordLockType
        this.killPasswordLockType = killPasswordLockType
    }

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append("LockOptions [accessPasswordLockType=")
        builder.append(accessPasswordLockType)
        builder.append(", epcLockType=")
        builder.append(epcLockType)
        builder.append(", killPasswordLockType=")
        builder.append(killPasswordLockType)
        builder.append(", tidLockType=")
        builder.append(tidLockType)
        builder.append(", userLockType=")
        builder.append(userLockType)
        builder.append("]")
        return builder.toString()
    }

    companion object {
        const val LOCK = 0 // Write Lock
        const val PERMA_LOCK = 1 // Permanent Write Lock
        const val PERMA_UNLOCK = 2 // Permanent Write Unlock
        const val UNLOCK = 3 // Write Unlock
        const val NONE = 4 // No Action
        val UNLOCK_EPC = valueOf(UNLOCK, NONE, NONE, NONE, NONE)
        val UNLOCK_ALL = valueOf(UNLOCK, NONE, UNLOCK, UNLOCK, NONE)
        val LOCK_ALL = valueOf(LOCK, NONE, LOCK, LOCK, NONE)
        val LOCK_USER = valueOf(NONE, NONE, LOCK, NONE, NONE)
        fun valueOf(
            epcLockType: Int,
            tidLockType: Int,
            userLockType: Int,
            accessPasswordLockType: Int,
            killPasswordLockType: Int
        ): LockOptions {
            return LockOptions(epcLockType, tidLockType, userLockType, accessPasswordLockType, killPasswordLockType)
        }
    }
}