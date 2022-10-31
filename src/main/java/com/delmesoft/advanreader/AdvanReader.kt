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

import com.delmesoft.advanreader.Device.ReadMode
import com.delmesoft.advanreader.ReaderException
import com.delmesoft.advanreader.op.*
import com.delmesoft.advanreader.utils.DataReader
import com.delmesoft.advanreader.utils.InputSource
import com.delmesoft.advanreader.utils.RestUtils
import com.delmesoft.advanreader.utils.Utils.toHexString
import com.delmesoft.advanreader.utils.Utils.toUpperCase
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.StringReader
import java.io.StringWriter
import java.net.URL
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpression
import javax.xml.xpath.XPathExpressionException
import javax.xml.xpath.XPathFactory

class AdvanReader : Reader {
    enum class MemoryBank {
        RESERVED, EPC, TID, USER
    }

    private var expressionVersion: XPathExpression? = null
    private var expressionDevice: XPathExpression? = null
    private var expressionReadModes: XPathExpression? = null
    private var expressionResult: XPathExpression? = null
    private var expressionGpiAll: XPathExpression? = null
    private var expressionError: XPathExpression? = null
    private var expressionTimestamp: XPathExpression? = null
    private val inputSource: InputSource
    private val restUtils: RestUtils
    private val readMap: MutableMap<String?, Read>
    private val opList: MutableList<AdvanOp>
    override var readerListener: ReaderListener? = null
    override var settings: Settings? = null
    override var gpoCount = 0
        private set
    override var gpiCount = 0
        private set
    var device: Device? = null
        private set

    @get:Synchronized
    override var isReading = false
        private set
    private var singularizing = false
    private var timestampStart = Long.MAX_VALUE
    private val dataReader: DataReader = object : DataReader() {
        override fun handleError(e: Exception?) {
            // e.printStackTrace();
            readerListener!!.onConnectionLost()
        }

        override fun handleDocument(document: Document?) {
            var element = document!!.documentElement
            when (element.nodeName) {
                "inventory" -> {
                    // INVENTORY :
                    element = element.getElementsByTagName("data").item(0) as Element
                    element = element.getElementsByTagName("inventory").item(0) as Element
                    element = element.getElementsByTagName("items").item(0) as Element
                    val itemList = element.getElementsByTagName("item")
                    var i = 0
                    while (i < itemList.length) {
                        element = itemList.item(i) as Element // item
                        var value = element.getElementsByTagName("ts").item(0).textContent
                        val timestamp = java.lang.Long.valueOf(value)
                        if (timestamp >= timestampStart) { // fix bug keonn :)
                            element = element.getElementsByTagName("data").item(0) as Element
                            val hexEpc = (element.getElementsByTagName("hexepc").item(0) as Element).textContent
                            val read = Read()
                            read.epc = toUpperCase(hexEpc)
                            element = element.getElementsByTagName("props").item(0) as Element
                            val propList = element.getElementsByTagName("prop")
                            var j = 0
                            while (j < propList.length) {
                                element = propList.item(j) as Element // prop
                                value = element.textContent
                                if (value.contains("RSSI")) {
                                    val result = getPropertyValue(value)
                                    read.rssi = java.lang.Double.valueOf(result)
                                } else if (value.contains("ANTENNA_PORT")) {
                                    val result = getPropertyValue(value)
                                    read.antennaId = Integer.valueOf(result)
                                }
                                ++j
                            }
                            handleRead(read)
                        } /* else {
						System.out.println(timestampStart);
						System.out.println(timestamp);
						System.out.println("-------------");
					}*/
                        ++i
                    }
                    handleOpList()
                }
                "deviceEventMessage" -> {
                    // EVENT :
                    element = element.getElementsByTagName("event").item(0) as Element
                    val type = (element.getElementsByTagName("type").item(0) as Element).textContent
                    when (type) {
                        "GPI" -> {
                            val line = (element.getElementsByTagName("line").item(0) as Element).textContent
                            val lowToHigh = (element.getElementsByTagName("lowToHigh").item(0) as Element).textContent
                            readerListener!!.onGpi(Integer.valueOf(line), java.lang.Boolean.parseBoolean(lowToHigh))
                        }
                    }
                }
                else -> {}
            }
        }
    }

    @Synchronized
    @Throws(ReaderException::class)
    override fun connect() {
        if (!isConnected) {
            applySettings(settings)
            dataReader.connect(settings.getHost(), TCP_PORT)
        }
    }

    @Throws(ReaderException::class)
    override fun applySettings(settings: Settings?) {
        try {
            val host = this.settings.getHost()
            val port = this.settings.getPort()
            device = getDevices(host, port).iterator().next()
            _stopDevice(device, port)
            val readModes = getDeviceModes(device, port)
            if (!readModes.contains(ReadMode.AUTONOMOUS.name)) {
                throw RuntimeException("Autonomous mode unsupported")
            }
            setAntennaConfiguration(device, port, settings)
            setWritePower(device, port, settings.getWritePower())
            setParameter(device, port, "GEN2_SESSION", GEN2_SESSION[settings.getSession()])
            setParameter(device, port, "GEN2_TARGET", GEN2_TARGET[settings.getSearchModeIndex()])
            gpiCount = toInt(getParameter(device, port, "DATA_GPI_NUMBER"))
            gpoCount = toInt(getParameter(device, port, "DATA_GPO_NUMBER"))
            if (isReading) {
                _startDevice(device, port)
            }

            // TODO : configure gpio ...
        } catch (e: Exception) {
            throw ReaderException("Error applying settings", e)
        }
    }

    private fun getPropertyValue(value: String): String {
        var subString = value.split(":").toTypedArray()[1]
        val index = subString.indexOf(",")
        if (index > -1) {
            subString = subString.substring(0, index)
        }
        return subString
    }

    private fun handleRead(read: Read) {
        synchronized(readMap) { readMap.put(read.epc, read) }
        readerListener!!.onRead(read)
    }

    protected fun handleOpList() {
        try {
            var opList: MutableList<AdvanOp>
            synchronized(this@AdvanReader) {
                if (this.opList.size == 0) return
                opList = LinkedList(this.opList)
                this.opList.clear()
                singularizing = true
            }
            _stopDevice(device, settings.getPort())
            try {
                val it = opList.iterator()
                while (it.hasNext()) {
                    val advanOp = it.next()
                    val epc = advanOp.epc
                    if (epc == null) {
                        for (read in readMap.values) {
                            advanOp.perform(read, this, readerListener!!)
                        }
                    } else {
                        val read = readMap[advanOp.epc]
                        if (read != null) {
                            advanOp.perform(read, this, readerListener!!)
                            it.remove()
                        }
                    }
                }
            } finally {
                readMap.clear()
                synchronized(this@AdvanReader) {
                    if (opList.size > 0) {
                        this@AdvanReader.opList.addAll(opList)
                    }
                    singularizing = false
                    if (isReading) {
                        _startDevice(device, settings.getPort())
                    }
                }
            }
        } catch (ignore: Exception) {
        }
    }

    @get:Synchronized
    override val isConnected: Boolean
        get() = dataReader.isConnected

    override fun disconnect() {
        try {
            stop()
        } catch (e: Exception) {
        } finally {
            dataReader.disconnect()
        }
    }

    @Throws(Exception::class)
    protected fun getDevices(host: String?, port: Int): Set<Device> {
        val devicesURL = URL("http", host, port, "/devices")
        val xmlFile = restUtils.sendGet(devicesURL)
        inputSource.setString(xmlFile)
        var nodes = expressionVersion!!.evaluate(inputSource, XPathConstants.NODESET) as NodeList
        var advanNetVersion: String? = ""
        for (i in 0 until nodes.length) {
            advanNetVersion = nodes.item(i).nodeValue
        }
        val devices: MutableSet<Device> = HashSet()
        inputSource.restart()
        nodes = expressionDevice!!.evaluate(inputSource, XPathConstants.NODESET) as NodeList
        var i = 0
        while (i < nodes.length) {
            val id = nodes.item(i).nodeValue
            val serial = nodes.item(i + 1).nodeValue
            val family = nodes.item(i + 2).nodeValue
            devices.add(Device(id, serial, family, host, advanNetVersion))
            i += 3
        }
        return devices
    }

    @Throws(Exception::class)
    protected fun getDeviceModes(device: Device?, port: Int): Set<String> {
        val readModesURL = URL("http", device.getHost(), port, "/devices/" + device.getId() + "/deviceModes")
        val xmlFile = restUtils.sendGet(readModesURL)
        inputSource.setString(xmlFile)
        val nodes = expressionReadModes!!.evaluate(inputSource, XPathConstants.NODESET) as NodeList
        val results: MutableSet<String> = HashSet()
        for (i in 0 until nodes.length) {
            val name = nodes.item(i).nodeValue
            results.add(name)
        }
        return results
    }

    @Throws(Exception::class)
    protected fun getActiveReadMode(device: Device?, port: Int): String {
        val readModesURL = URL("http", device.getHost(), port, "/devices/" + device.getId() + "/activeReadMode")
        val xmlFile = restUtils.sendGet(readModesURL)
        inputSource.setString(xmlFile)
        val nodes = expressionResult!!.evaluate(inputSource, XPathConstants.NODESET) as NodeList
        if (nodes.length != 1) throw RuntimeException("Error getting ActiveReadMode")
        return nodes.item(0).nodeValue
    }

    @Throws(Exception::class)
    protected fun setActiveDeviceMode(device: Device?, port: Int, deviceMode: String) {
        val activeDeviceModeURL =
            URL("http", device.getHost(), port, "/devices/" + device.getId() + "/activeDeviceMode")
        val xmlFileResponse = restUtils.sendPut(activeDeviceModeURL, deviceMode) // TODO : page 51
        /*
		 * <request><class>READMODE_AUTONOMOUS</class><name>AUTONOMOUS</name><useFastSearch>false</useFastSearch><swFilterOnly>false</swFilterOnly><filterMaskOffset>32</filterMaskOffset><filterMaskBitLength>0</filterMaskBitLength><filterMaskHex/><calibratorsRefreshPeriod>60</calibratorsRefreshPeriod><keepAllReads>false</keepAllReads><asynch>true</asynch><cleanBufferOnSynchRead>false</cleanBufferOnSynchRead><timeWindow>2500</timeWindow><onTime>600</onTime><offTime>0</offTime></request>
		 */if (xmlFileResponse!!.contains("ERROR")) throw RuntimeException("Error, the device mode $deviceMode was not able to be set")
    }

    @Throws(Exception::class)
    protected fun startDevice(device: Device?, port: Int) {
        val xmlFileResponse = _startDevice(device, port)
        timestampStart = getTimestamp(xmlFileResponse)
    }

    @Throws(Exception::class)
    fun _startDevice(device: Device?, port: Int): String? {
        val startURL = URL("http", device.getHost(), port, "/devices/" + device.getId() + "/start")
        val xmlFileResponse = restUtils.sendGet(startURL)
        if (xmlFileResponse!!.contains("ERROR")) throw RuntimeException("Error, failed to start the device " + device.getId())
        return xmlFileResponse
    }

    @Throws(Exception::class)
    private fun getTimestamp(xmlFile: String?): Long {
        inputSource.setString(xmlFile)
        val nodes = expressionTimestamp!!.evaluate(inputSource, XPathConstants.NODESET) as NodeList
        if (nodes.length > 0) {
            val result = nodes.item(0).nodeValue
            return java.lang.Long.valueOf(result)
        }
        return -1
    }

    @Throws(Exception::class)
    protected fun stopDevice(device: Device?, port: Int) {
        timestampStart = Long.MAX_VALUE
        _stopDevice(device, port)
    }

    @Throws(Exception::class)
    fun _stopDevice(device: Device?, port: Int) {
        val stopURL = URL("http", device.getHost(), port, "/devices/" + device.getId() + "/stop")
        val xmlFileResponse = restUtils.sendGet(stopURL)
        if (xmlFileResponse!!.contains("ERROR")) throw RuntimeException("Error, failed to stop the device " + device.getId())
    }

    @Throws(Exception::class)
    protected fun isGpi(device: Device?, port: Int, gpiPort: Int): Boolean {
        val gpiUrl = URL("http", device.getHost(), port, "/devices/" + device.getId() + "/getGPI/" + gpiPort)
        val xml = restUtils.sendGet(gpiUrl)
        if (xml!!.contains("ERROR")) throw RuntimeException(String.format("Error, getting gpi '%d' state", gpiPort))
        inputSource.setString(xml)
        val nodes = expressionResult!!.evaluate(inputSource, XPathConstants.NODESET) as NodeList
        return java.lang.Boolean.parseBoolean(nodes.item(0).nodeValue)
    }

    // In AdvanReader Series 50 and 100, the GPI operations will stop temporarily theRF operations.
    @Throws(Exception::class)
    protected fun getGpiAll(device: Device?, port: Int): BooleanArray {
        val gpiAllUrl = URL("http", device.getHost(), port, "/devices/" + device.getId() + "/getGPIAll")
        val xml = restUtils.sendGet(gpiAllUrl)
        if (xml!!.contains("ERROR")) throw RuntimeException("Error, getting GPIAll")
        inputSource.setString(xml)
        val nodes = expressionGpiAll!!.evaluate(inputSource, XPathConstants.NODESET) as NodeList
        val results: MutableMap<Int, Boolean> = HashMap()
        var i = 0
        while (i < nodes.length) {
            val index = nodes.item(i).nodeValue
            val state = nodes.item(i + 1).nodeValue
            results[index.toInt()] = java.lang.Boolean.parseBoolean(state)
            i += 2
        }
        val tmp = BooleanArray(results.size)
        for ((key, value) in results) {
            tmp[key] = value
        }
        return tmp
    }

    @Throws(Exception::class)
    protected fun setGpo(device: Device?, port: Int, portNumber: Int, state: Boolean) {
        val setGpoURL =
            URL("http", device.getHost(), port, "/devices/" + device.getId() + "/setGPO/" + portNumber + "/" + state)
        val xmlFileResponse = restUtils.sendGet(setGpoURL)
        if (xmlFileResponse!!.contains("ERROR")) throw RuntimeException("Error, setting gpo state")
    }

    @Throws(Exception::class)
    protected fun setParameter(device: Device?, port: Int, paramId: String, paramValue: String?) {
        val setParameterURL =
            URL("http", device.getHost(), port, "/devices/" + device.getId() + "/reader/parameter/" + paramId)
        val xmlFileResponse = restUtils.sendPut(setParameterURL, paramValue)
        if (xmlFileResponse!!.contains("ERROR")) throw RuntimeException(
            String.format(
                "Error, setting parameter '%s' = '%s'",
                paramId,
                paramValue
            )
        )
    }

    @Throws(Exception::class)
    protected fun getParameter(device: Device?, port: Int, paramName: String): String? {
        val url = URL("http", device.getHost(), port, "/devices/" + device.getId() + "/reader/parameter/" + paramName)
        val xmlFile = restUtils.sendGet(url)
        if (xmlFile!!.contains("ERROR")) throw RuntimeException("Error, failed to setting antenna configuration")
        inputSource.setString(xmlFile)
        val nodes = expressionResult!!.evaluate(inputSource, XPathConstants.NODESET) as NodeList
        return if (nodes.length > 0) {
            nodes.item(0).nodeValue
        } else null
    }

    protected fun toInt(value: String?): Int {
        return value?.toInt() ?: 0
    }

    @Throws(Exception::class)
    protected fun saveConfiguration(device: Device, port: Int) {
        val saveConfigurationURL = URL("http", device.host, port, "/devices/" + device.id + "/confSave")
        val xmlFileResponse = restUtils.sendGet(saveConfigurationURL)
        if (xmlFileResponse!!.contains("ERROR")) throw RuntimeException("Error, failed to setting write power")
    }

    @Throws(Exception::class)
    protected fun setWritePower(device: Device?, port: Int, power: Double) {
        val writePowerURL = URL(
            "http",
            device.getHost(),
            port,
            "/devices/" + device.getId() + "/reader/parameter/RF_WRITE_POWER/" + power
        )
        val xmlFileResponse = restUtils.sendGet(writePowerURL)
        if (xmlFileResponse!!.contains("ERROR")) throw RuntimeException("Error, failed to setting write power")
    }

    @Throws(Exception::class)
    protected fun setAntennaConfiguration(device: Device?, port: Int, settings: Settings?) {
        val antennasURL = URL("http", device.getHost(), port, "/devices/" + device.getId() + "/antennas")
        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = docBuilder.newDocument()
        val request = doc.createElement("request")
        doc.appendChild(request)
        val entries = doc.createElement("entries")
        request.appendChild(entries)
        for (i in settings.getAntennas().indices) {
            val antennaId = settings.getAntennas()[i]
            val txPower = settings.getTxPower()[i]
            val rxSensitivity = settings.getRxSensitivity()[i]
            val antElement = getAntennaElement(antennaId, txPower, rxSensitivity, device, doc)
            entries.appendChild(antElement)
        }
        val antennaConfiguration = documentToXML(doc)
        // System.out.println(antennaConfiguration);
        val xmlFileResponse = restUtils.sendPut(antennasURL, antennaConfiguration)
        if (xmlFileResponse!!.contains("ERROR")) throw RuntimeException("Error, failed to setting antenna configuration")
    }

    @Throws(Exception::class)
    fun readDataByEpc(
        device: Device,
        port: Int,
        epc: String?,
        bank: Int,
        address: Int,
        lenght: Int,
        accessPassword: String?
    ): String {
        val docFactory = DocumentBuilderFactory.newInstance()
        val docBuilder = docFactory.newDocumentBuilder()
        val doc = docBuilder.newDocument()
        val request = doc.createElement("request")
        doc.appendChild(request)
        val writeDataElement = getReadDataOpElement(MemoryBank.values()[bank].name, address, lenght, doc)
        request.appendChild(writeDataElement)
        val paramsElement = getParamsOpElement(epc, 0, TAG_OP_TIMEOUT, RF_WRITE_RETRIES, accessPassword, doc)
        request.appendChild(paramsElement)
        val xml = documentToXML(doc)
        val xmlFileResponse = execOp(device, port, xml)
        return expressionResult!!.evaluate(org.xml.sax.InputSource(StringReader(xmlFileResponse)))
    }

    @Throws(Exception::class)
    fun writeData(
        device: Device,
        port: Int,
        epc: String?,
        data: String,
        bank: Int,
        address: Int,
        accessPassword: String?
    ) {
        val docFactory = DocumentBuilderFactory.newInstance()
        val docBuilder = docFactory.newDocumentBuilder()
        val doc = docBuilder.newDocument()
        val request = doc.createElement("request")
        doc.appendChild(request)
        val writeDataElement = getWriteDataOpElement(MemoryBank.values()[bank].name, address, data, doc)
        request.appendChild(writeDataElement)
        val paramsElement = getParamsOpElement(epc, 0, TAG_OP_TIMEOUT, RF_WRITE_RETRIES, accessPassword, doc)
        request.appendChild(paramsElement)
        val xml = documentToXML(doc)
        execOp(device, port, xml)
    }

    @Throws(Exception::class)
    fun lockTag(
        device: Device,
        port: Int,
        epc: String?,
        accessPassword: String?,
        newAccessPassword: String?,
        newKillPassword: String?,
        lockOptions: LockOptions?
    ) {
        var accessPassword = accessPassword
        val docFactory = DocumentBuilderFactory.newInstance()
        if (accessPassword == null) accessPassword = ""
        if (newAccessPassword != null || newKillPassword != null) {
            val docBuilder = docFactory.newDocumentBuilder()
            val doc = docBuilder.newDocument()
            val request = doc.createElement("request")
            doc.appendChild(request)
            val CommissioningElement =
                getCommissioningOpElement(epc, accessPassword, newAccessPassword, newKillPassword, doc)
            request.appendChild(CommissioningElement)
            val paramsElement =
                getParamsOpElement(epc, 0, TAG_OP_TIMEOUT, RF_WRITE_RETRIES, null, doc) // no requiere password
            request.appendChild(paramsElement)
            val xml = documentToXML(doc)
            execOp(device, port, xml)
        }
        if (lockOptions != null) {
            if (newAccessPassword != null) // Si se ha cambiado el password
                accessPassword = newAccessPassword
            val docBuilder = docFactory.newDocumentBuilder()
            val doc = docBuilder.newDocument()
            val request = doc.createElement("request")
            doc.appendChild(request)
            val lockTagElement = getLockTagOpElement(accessPassword, lockOptions, doc)
            request.appendChild(lockTagElement)
            val paramsElement = getParamsOpElement(epc, 0, TAG_OP_TIMEOUT, RF_WRITE_RETRIES, null, doc)
            request.appendChild(paramsElement)
            val xml = documentToXML(doc)
            execOp(device, port, xml)
        }
    }

    private fun getCommissioningOpElement(
        epc: String?,
        accessPassword: String,
        newAccessPassword: String?,
        newKillPassword: String?,
        doc: Document
    ): Element {
        val op = doc.createElement("op")
        val opClass = doc.createElement("class")
        opClass.appendChild(doc.createTextNode("com.keonn.spec.reader.op.CommissionTagOp"))
        op.appendChild(opClass)
        val opBank = doc.createElement("accessPwd")
        opBank.appendChild(doc.createTextNode(accessPassword))
        op.appendChild(opBank)
        if (epc != null) { // new epc
            val opEpc = doc.createElement("epc")
            opEpc.appendChild(doc.createTextNode(epc))
            op.appendChild(opEpc)
        }
        if (newAccessPassword != null) { // new access password
            val opNewPass = doc.createElement("newAccessPwd")
            opNewPass.appendChild(doc.createTextNode(newAccessPassword))
            op.appendChild(opNewPass)
        }
        if (newKillPassword != null) { // new kill password
            val opLocks = doc.createElement("newKillPwd")
            opLocks.appendChild(doc.createTextNode(newKillPassword))
            op.appendChild(opLocks)
        }
        return op
    }

    @Throws(Exception::class)
    private fun getAntennaElement(
        antennaId: Int,
        txPower: Double,
        rxSensitivity: Double,
        device: Device?,
        doc: Document
    ): Element {
        val entry = doc.createElement("entry")
        val antennaClass = doc.createElement("class")
        antennaClass.appendChild(doc.createTextNode("ANTENNA_DEFINITION"))
        entry.appendChild(antennaClass)
        val cid = doc.createElement("def")
        // TODO : future 
        val mux1 = 0
        val mux2 = 0
        val direction = -1
        val location = "antenna_$antennaId"
        val lx = 0
        val ly = 0
        val lz = 0
        val def = String.format(
            "%s,%d,%d,%d,%d,%s,%d,%d,%d",
            device.getId(),
            antennaId,
            mux1,
            mux2,
            direction,
            location,
            lx,
            ly,
            lz
        )
        cid.appendChild(doc.createTextNode(def))
        entry.appendChild(cid)
        val conf = doc.createElement("conf")
        entry.appendChild(conf)
        val confClass = doc.createElement("class")
        confClass.appendChild(doc.createTextNode("ANTENNA_CONF"))
        conf.appendChild(confClass)
        val power = doc.createElement("power")
        if (txPower != 0.0) {
            power.appendChild(doc.createTextNode("" + txPower))
        }
        conf.appendChild(power)
        val sensitivity = doc.createElement("sensitivity")
        if (rxSensitivity != 0.0) {
            sensitivity.appendChild(doc.createTextNode("" + rxSensitivity))
        }
        conf.appendChild(sensitivity)
        val readTime = doc.createElement("readTime")
        conf.appendChild(readTime)
        return entry
    }

    private fun getLockTagOpElement(accessPassword: String, lockOptions: LockOptions, doc: Document): Element {
        val op = doc.createElement("op")
        val opClass = doc.createElement("class")
        opClass.appendChild(doc.createTextNode("com.keonn.spec.reader.op.LockOp"))
        op.appendChild(opClass)
        val opBank = doc.createElement("accessPwd")
        opBank.appendChild(doc.createTextNode("0x$accessPassword"))
        op.appendChild(opBank)
        val opOffset = doc.createElement("mask")
        opOffset.appendChild(doc.createTextNode(Integer.toString(0)))
        op.appendChild(opOffset)
        val opData = doc.createElement("action")
        opData.appendChild(doc.createTextNode(Integer.toString(0)))
        op.appendChild(opData)
        val opLocks = doc.createElement("locks")
        opLocks.appendChild(doc.createTextNode(toString(lockOptions)))
        op.appendChild(opLocks)
        return op
    }

    private fun toString(lockOptions: LockOptions): String {
        val sb = StringBuilder()
        getOption("ACCESS_", lockOptions.accessPasswordLockType, sb)
        getOption("KILL_", lockOptions.killPasswordLockType, sb)
        getOption("EPC_", lockOptions.epcLockType, sb)
        getOption("TID_", lockOptions.tidLockType, sb)
        getOption("USER_", lockOptions.userLockType, sb)
        return sb.toString()
    }

    private fun getOption(prefix: String, type: Int, sb: StringBuilder) {
        when (type) {
            LockOptions.Companion.LOCK -> sb.append(prefix).append("LOCK")
            LockOptions.Companion.UNLOCK -> sb.append(prefix).append("UNLOCK")
            LockOptions.Companion.PERMA_LOCK -> sb.append(prefix).append("PERMALOCK")
            LockOptions.Companion.PERMA_UNLOCK -> sb.append(prefix).append("PERMAUNLOCK")
            else -> {}
        }
    }

    @Throws(Exception::class)
    private fun getReadDataOpElement(bank: String, offset: Int, lenght: Int, doc: Document): Element {
        val op = doc.createElement("op")
        val opClass = doc.createElement("class")
        opClass.appendChild(doc.createTextNode("com.keonn.spec.reader.op.ReadDataOp"))
        op.appendChild(opClass)
        val opBank = doc.createElement("bank")
        opBank.appendChild(doc.createTextNode(bank))
        op.appendChild(opBank)
        val opOffset = doc.createElement("offset")
        opOffset.appendChild(doc.createTextNode(Integer.toString(offset)))
        op.appendChild(opOffset)
        val opData = doc.createElement("length")
        opData.appendChild(doc.createTextNode(Integer.toString(lenght)))
        op.appendChild(opData)
        return op
    }

    @Throws(Exception::class)
    private fun getWriteDataOpElement(bank: String, offset: Int, data: String, doc: Document): Element {
        val op = doc.createElement("op")
        val opClass = doc.createElement("class")
        opClass.appendChild(doc.createTextNode("com.keonn.spec.reader.op.WriteDataOp"))
        op.appendChild(opClass)
        val opBank = doc.createElement("bank")
        opBank.appendChild(doc.createTextNode(bank))
        op.appendChild(opBank)
        val opOffset = doc.createElement("offset")
        opOffset.appendChild(doc.createTextNode(Integer.toString(offset)))
        op.appendChild(opOffset)

        //		Element opLenght = doc.createElement("length");
        //		opLenght.appendChild(doc.createTextNode(Integer.toString(data.length())));
        //		op.appendChild(opLenght);
        val opData = doc.createElement("data")
        opData.appendChild(doc.createTextNode(data))
        op.appendChild(opData)
        return op
    }

    @Throws(Exception::class)
    private fun getParamsOpElement(
        epc: String?,
        antenna: Int,
        opTimeout: Long,
        retries: Int,
        accessPassword: String?,
        doc: Document
    ): Element {
        val params = doc.createElement("params")
        if (epc != null && !epc.trim { it <= ' ' }.isEmpty()) {
            val param = doc.createElement("param")
            val paramId = doc.createElement("id")
            paramId.appendChild(doc.createTextNode("GEN2_FILTER"))
            param.appendChild(paramId)
            val paramObj = doc.createElement("obj")
            val objClass = doc.createElement("class")
            objClass.appendChild(doc.createTextNode("com.keonn.spec.filter.SelectTagFilter"))
            paramObj.appendChild(objClass)
            val objBank = doc.createElement("bank")
            objBank.appendChild(doc.createTextNode("EPC"))
            paramObj.appendChild(objBank)
            val objBitPointer = doc.createElement("bitPointer")
            objBitPointer.appendChild(doc.createTextNode(Integer.toString(32)))
            paramObj.appendChild(objBitPointer)
            val objBitLength = doc.createElement("bitLength")
            objBitLength.appendChild(doc.createTextNode(Integer.toString(epc.length shl 2)))
            paramObj.appendChild(objBitLength)
            val objMask = doc.createElement("mask")
            objMask.appendChild(doc.createTextNode(epc))
            paramObj.appendChild(objMask)
            param.appendChild(paramObj)
            params.appendChild(param)
        }
        if (antenna > 0) {
            val param = doc.createElement("param")
            val paramId = doc.createElement("id")
            paramId.appendChild(doc.createTextNode("TAG_OP_ANTENNA"))
            param.appendChild(paramId)
            val paramObj = doc.createElement("obj")
            paramObj.appendChild(doc.createTextNode(Integer.toString(antenna)))
            param.appendChild(paramObj)
            params.appendChild(param)
        }
        if (opTimeout > 0) {
            val param = doc.createElement("param")
            val paramId = doc.createElement("id")
            paramId.appendChild(doc.createTextNode("TAG_OP_TIMEOUT"))
            param.appendChild(paramId)
            val paramObj = doc.createElement("obj")
            paramObj.appendChild(doc.createTextNode(java.lang.Long.toString(opTimeout)))
            param.appendChild(paramObj)
            params.appendChild(param)
        }
        if (retries > 0) {
            val param = doc.createElement("param")
            val paramId = doc.createElement("id")
            paramId.appendChild(doc.createTextNode("RF_WRITE_RETRIES"))
            param.appendChild(paramId)
            val paramObj = doc.createElement("obj")
            paramObj.appendChild(doc.createTextNode(Integer.toString(retries)))
            param.appendChild(paramObj)
            params.appendChild(param)
        }
        if (accessPassword != null && !accessPassword.trim { it <= ' ' }.isEmpty()) {
            val param = doc.createElement("param")
            val paramId = doc.createElement("id")
            paramId.appendChild(doc.createTextNode("GEN2_ACCESS_PASSWORD"))
            param.appendChild(paramId)
            val paramObj = doc.createElement("obj")
            paramObj.appendChild(doc.createTextNode(accessPassword))
            param.appendChild(paramObj)
            params.appendChild(param)
        }
        return params
    }

    @Throws(Exception::class)
    private fun execOp(device: Device, port: Int, opData: String): String? {
        val execOpURL = URL("http", device.host, port, "/devices/" + device.id + "/execOp")
        val xmlFileResponse = restUtils.sendPut(execOpURL, opData)
        if (xmlFileResponse!!.contains("ERROR")) {
            throw ReaderException(getMessageError(xmlFileResponse))
        }
        return xmlFileResponse // response
    }

    @Throws(XPathExpressionException::class)
    private fun getMessageError(xmlFile: String?): String {
        inputSource.setString(xmlFile)
        val nodes = expressionError!!.evaluate(inputSource, XPathConstants.NODESET) as NodeList
        return if (nodes.length > 0) {
            nodes.item(0).nodeValue
        } else ""
    }

    @Throws(ReaderException::class)
    fun addOp(op: AdvanOp) {
        synchronized(this@AdvanReader) { opList.add(op) }
        startRead()
    }

    @Synchronized
    @Throws(ReaderException::class)
    override fun startRead() {
        if (!isReading && !singularizing) {
            try {
                val port = settings.getPort()
                val readMode = getActiveReadMode(device, port)
                if (ReadMode.AUTONOMOUS.name != readMode) {
                    setActiveDeviceMode(device, port, "Autonomous")
                }
                startDevice(device, port)
                isReading = true
            } catch (e: Exception) {
                throw ReaderException("Error starting read", e)
            }
        }
    }

    @Synchronized
    @Throws(ReaderException::class)
    override fun stop() {
        if (isReading) {
            try {
                isReading = false
                singularizing = false
                val port = settings.getPort()
                stopDevice(device, port)
            } catch (e: Exception) {
                throw ReaderException("Error stopping read", e)
            }
        }
    }

    @Throws(ReaderException::class)
    override fun setGpo(portNumber: Int, state: Boolean) {
        try {
            val port = settings.getPort()
            setGpo(device, port, portNumber, state)
        } catch (e: Exception) {
            throw ReaderException("Error setting gpo state", e)
        }
    }

    @Throws(ReaderException::class)
    override fun setGpo(stateMap: Map<Int, Boolean>) {
        try {
            val port = settings.getPort()
            for ((key, value) in stateMap) {
                setGpo(device, port, key, value)
            }
        } catch (e: Exception) {
            throw ReaderException("Error setting gpo states", e)
        }
    }

    @Throws(ReaderException::class)
    override fun setGpo(state: BooleanArray) {
        try {
            val port = settings.getPort()
            for (i in state.indices) {
                setGpo(device, port, i + 1, state[i])
            }
        } catch (e: Exception) {
            throw ReaderException("Error setting gpo states", e)
        }
    }

    @Throws(ReaderException::class)
    override fun isGpi(portNumber: Int): Boolean {
        return try {
            val port = settings.getPort()
            isGpi(device, port, portNumber)
        } catch (e: Exception) {
            throw ReaderException("Error getting gpi state", e)
        }
    }

    @get:Throws(ReaderException::class)
    override val gpiState: BooleanArray
        get() = try {
            val port = settings.getPort()
            getGpiAll(device, port)
        } catch (e: Exception) {
            throw ReaderException("Error getting gpi states", e)
        }
    override val serial: String?
        get() = device.getSerial()
    override val modelName: String?
        get() = device.getFamily()

    @Throws(ReaderException::class)
    override fun readData(bank: Int, address: Int, lenght: Int) {
        readData(bank, address, lenght, null)
    }

    @Throws(ReaderException::class)
    override fun readData(bank: Int, address: Int, lenght: Int, accessPassword: String?) {
        val readDataOp = ReadDataOp()
        readDataOp.accessPassword = accessPassword
        readDataOp.bank = bank
        readDataOp.address = address
        readDataOp.lenght = lenght
        addOp(readDataOp)
    }

    @Throws(ReaderException::class)
    override fun readData(epc: String?, bank: Int, address: Int, lenght: Int) {
        readData(epc, bank, address, lenght, null)
    }

    @Throws(ReaderException::class)
    override fun readData(epc: String?, bank: Int, address: Int, lenght: Int, accessPassword: String?) {
        val readDataByEpcOp = ReadDataByEpcOp()
        readDataByEpcOp.accessPassword = accessPassword
        readDataByEpcOp.bank = bank
        readDataByEpcOp.address = address
        readDataByEpcOp.lenght = lenght
        readDataByEpcOp.epc = epc
        addOp(readDataByEpcOp)
    }

    @Throws(ReaderException::class)
    override fun setKillPassword(epc: String?, killPassword: String?) {
        setKillPassword(epc, null, killPassword)
    }

    @Throws(ReaderException::class)
    override fun setKillPassword(epc: String?, accessPassword: String?, killPassword: String?) {
        val setKillPwdOp = SetKillPwdOp()
        setKillPwdOp.epc = epc
        setKillPwdOp.accessPassword = accessPassword
        setKillPwdOp.killPassword = killPassword
        addOp(setKillPwdOp)
    }

    @Throws(ReaderException::class)
    override fun lockTag(epc: String?, accessPassword: String?, lockOptions: LockOptions?) {
        lockTag(epc, null, accessPassword, lockOptions)
    }

    @Throws(ReaderException::class)
    override fun lockTag(
        epc: String?,
        oldAccessPassword: String?,
        newAccessPassword: String?,
        lockOptions: LockOptions?
    ) {
        val lockTagOp = LockTagOp()
        lockTagOp.epc = epc
        lockTagOp.oldAccessPassword = oldAccessPassword
        lockTagOp.newAccessPassword = newAccessPassword
        lockTagOp.lockOptions = lockOptions
        addOp(lockTagOp)
    }

    @Throws(ReaderException::class)
    override fun writeEpc(srcEpc: String, tgtEpc: String) {
        writeEpc(srcEpc, tgtEpc, null)
    }

    @Throws(ReaderException::class)
    override fun writeEpc(srcEpc: String, tgtEpc: String, accessPassword: String?) {
        var tgtEpc = tgtEpc
        val srcEpcLen = srcEpc.length
        val tgtEpcLen = tgtEpc.length
        if (tgtEpcLen and 3 != 0 || srcEpcLen and 3 != 0) {
            throw ReaderException("EPCs must be a multiple of 16-bits: $srcEpc, $tgtEpc")
        }
        var wordPointer: Short = 2
        if (srcEpcLen != tgtEpcLen) {
            wordPointer = 1
            val currentPC = (srcEpc.length shr 2 shl 11).toShort()
            // keep other PC bits the same.
            val newPC: Short = (currentPC and 0x7FF or (tgtEpc.length shr 2 shl 11).toShort()).toShort()
            val newPCString = toHexString(newPC)
            tgtEpc = newPCString + tgtEpc
        }
        writeData(srcEpc, tgtEpc, 1, wordPointer, accessPassword)
        //		ChangeEpcOp changeEpcOp = new ChangeEpcOp();		
        //		changeEpcOp.setEpc(srcEpc);
        //		changeEpcOp.setNewEpc(tgtEpc);
        //		changeEpcOp.setAccessPassword(accessPassword);
        //		addOp(changeEpcOp);
    }

    @Throws(ReaderException::class)
    override fun writeData(epc: String?, data: String?, memoryBank: Int, wordPointer: Short) {
        this.writeData(epc, data, memoryBank, wordPointer, null)
    }

    @Throws(ReaderException::class)
    override fun writeData(epc: String?, data: String?, memoryBank: Int, wordPointer: Short, accessPassword: String?) {
        val writeDataOp = WriteDataOp()
        writeDataOp.epc = epc
        writeDataOp.data = data
        writeDataOp.accessPassword = accessPassword
        writeDataOp.memoryBank = memoryBank
        writeDataOp.wordPointer = wordPointer
        addOp(writeDataOp)
    }

    companion object {
        val TX_FREQUENCIES = intArrayOf(865700, 866300, 866900, 867500)
        val GEN2_SESSION = arrayOf("S0", "S1", "S2", "S3")
        val GEN2_TARGET = arrayOf("A", "B", "AB", "BA")
        const val TCP_PORT = 3177
        const val TAG_OP_TIMEOUT: Long = 1000
        const val RF_WRITE_RETRIES = 2

        @Throws(Exception::class)
        private fun documentToXML(document: Document): String {
            val transformer = TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(OutputKeys.METHOD, "xml")
            // transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            val sw = StringWriter()
            val source = DOMSource(document.documentElement)
            transformer.transform(source, StreamResult(sw))
            return sw.toString()
        }
    }

    init {
        try {
            val xPath = XPathFactory.newInstance().newXPath()
            expressionVersion = xPath.compile("/response/msg-version/text()")
            expressionDevice =
                xPath.compile("/response/data/devices/device/*[self::id or self::serial or self::family]/text()")
            expressionReadModes = xPath.compile("/response/data/entries/entry/readModes/readMode/name/text()")
            expressionResult = xPath.compile("/response/data/result/text()")
            expressionGpiAll = xPath.compile("/response/data/entries/entry/*[self::index or self::result]/text()")
            expressionError = xPath.compile("/response/msg/text()")
            expressionTimestamp = xPath.compile("/response/ts/text()")
        } catch (e: Exception) {
            throw RuntimeException("Error initializing extensions", e)
        }
        inputSource = InputSource()
        restUtils = RestUtils()
        opList = ArrayList()
        readMap = HashMap()
    }
}