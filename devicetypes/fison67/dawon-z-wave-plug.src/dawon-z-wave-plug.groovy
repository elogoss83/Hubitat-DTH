/**
 *  Dawon Z-Wave Plug (v.0.0.1)
 *
 * MIT License
 *
 * Copyright (c) 2018 fison67@nate.com
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
*/
metadata {
  definition (name: "Dawon Z-Wave Plug", namespace: "fison67", author: "fison67") {
    capability "Energy Meter"
    capability "Actuator"
    capability "Switch"
    capability "Power Meter"
    capability "Refresh"
    capability "Configuration"
    capability "Sensor"
    capability "Light"
    capability "Health Check"

    command "reset"

    attribute "overCurrent", "string"
    attribute "hardware", "string"

    fingerprint inClusters: "0x5E,0x25,0x85,0x59,0x86,0x72,0x5A,0x70,0x73,0x20,0x32,0x71,0x7A"
    fingerprint mfr: "018C", prod: "0042", model: "0005", deviceJoinName: "Dawon Smart Plug"
    fingerprint mfr: "018C", prod: "0042", model: "0008", deviceJoinName: "Dawon Smart Multitab", ocfDeviceType: "oic.d.smartplug"
  }

  preferences {
    input name: "stanbyPower", title:"Standby Power", type:"enum", options: ["Enable", "Disable"], description: "Stanby Power", required: true, defaultValue: "Disable"
    input name: "stanbyPowerValue", title:"Standby Power Value Setting" , type: "number", required: true, defaultValue: 00
    input name: "periodicMeasurement", type: "enum", title: "Periodic Measurement", options: ["Enable", "Disable"], description: "Periodic Measurement", required: true, defaultValue: "Enable"
    input name: "accumulation", type: "enum", title: "Accumulation", options: ["Stop", "Start"], description: "Accumulation", required: true, defaultValue: "Start"
    input name: "connectedDevice", type: "enum", title: "Connected Device", options: ["Use", "Not Use"], description: "Connected Device", required: true, defaultValue: "Use"
    input name: "minTimeInterval", type: "number", title: "Minimum time interval", description: "Minimum time interval", required: true, defaultValue: 1
  }

}

def installed() {
  log.debug "installed()"
  // Device-Watch simply pings if no device events received for 32min(checkInterval)
  initialize()
  if (zwaveInfo?.mfr?.equals("0063") || zwaveInfo?.mfr?.equals("014F")) { // These old GE devices have to be polled. GoControl Plug refresh status every 15 min.
    runEvery15Minutes("poll", [forceForLocallyExecuting: true])
  }
}

def updated() {
  // Device-Watch simply pings if no device events received for 32min(checkInterval)
  initialize()
  if (zwaveInfo?.mfr?.equals("0063") || zwaveInfo?.mfr?.equals("014F")) { // These old GE devices have to be polled. GoControl Plug refresh status every 15 min.
    unschedule("poll", [forceForLocallyExecuting: true])
    runEvery15Minutes("poll", [forceForLocallyExecuting: true])
  }
  try {
    if (!state.MSR) {
      response(zwave.manufacturerSpecificV2.manufacturerSpecificGet().format())
    }
  } catch (e) {
    log.debug e
  }
}

def initialize() {
  sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
    configure()
}

def getCommandClassVersions() {
  [
    0x20: 1,  // Basic
    0x32: 3,  // Meter
    0x56: 1,  // Crc16Encap
    0x70: 1,  // Configuration
    0x72: 2,  // ManufacturerSpecific
    0x25: 1,	// Switch Binary
    0x27: 1,	// All Switch
    0x55: 1,	// Transport Service
    0x59: 1,	// AssociationGrpInfo
    0x5A: 1,	// DeviceResetLocally
    0x5E: 2,	// ZwaveplusInfo
    0x6C: 1,	// Supervision
    0x71: 3,  	// Notification v8
    0x72: 2,	// ManufacturerSpecific
    0x73: 1,	// Powerlevel
    0x85: 2,	// Association
    0x86: 1,	// Version (2)
    0x8E: 2,	// Multi Channel Association
    0x98: 1,	// Security 0
    0x9F: 1		// Security S2
  ]
}

// parse events into attributes
def parse(String description) {
  log.debug "parse() - description: "+description
  def result = null
  if (description != "updated") {
    def cmd = zwave.parse(description, commandClassVersions)
    if (cmd) {
          log.debug cmd
      result = zwaveEvent(cmd)
      log.debug("'$description' parsed to $result")
    } else {
      log.debug("Couldn't zwave.parse '$description'")
    }
  }
    log.debug result
  result
}

def handleMeterReport(cmd){
  if (cmd.meterType == 1) {
    if (cmd.scale == 0) {
      createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
    } else if (cmd.scale == 1) {
      createEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kVAh")
    } else if (cmd.scale == 2) {
      createEvent(name: "power", value: Math.round(cmd.scaledMeterValue), unit: "W")
    }
  }
}


def zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd) {
  log.debug "v3 Meter report: "+cmd
  handleMeterReport(cmd)
}

def zwaveEvent(hubitat.zwave.commands.notificationv3.NotificationReport cmd){
    if(cmd.notificationType == 0x08){
      switch(cmd.event){
        case 0x02:
          sendEvent(name: "switch", value: "off")
          sendEvent(name: "overCurrent", value: "off")
          sendEvent(name: "hardware", value: "normal")
          break
        case 0x03:
          sendEvent(name: "switch", value: "on")
          sendEvent(name: "overCurrent", value: "off")
          sendEvent(name: "hardware", value: "normal")
          break
        case 0x06:
          sendEvent(name: "overCurrent", value: "on")
          sendEvent(name: "hardware", value: "normal")
          break
        }
    }else if(cmd.notificationType == 0x09){
        sendEvent(name: "overCurrent", value: "off")
        sendEvent(name: "hardware", value: "broken")
    }
    return []
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd){
  def value = (cmd.value ? "on" : "off")
  def evt = createEvent(name: "switch", value: value, type: "physical", descriptionText: "$device.displayName was turned $value")
  if (evt.isStateChange) {
    [evt, response(["delay 3000", meterGet(scale: 2).format()])]
  } else {
    evt
  }
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd){
    switch(cmd.parameterNumber){
    case 1:
      def value = ( cmd.configurationValue[0] * 100 + cmd.configurationValue[1] * 10 + cmd.configurationValue[2] ) / 100
      def enable = cmd.configurationValue[3] == 1 ? "Enable" : "Disable"
      log.info " - Standby Power: ${value}, Set: ${enable}"
      break
    case 2:
      log.info (cmd.configurationValue[0] == 0  ? " - Periodic Measurement Value Transmission Enable" : " - Periodic Measurement Value Transmission disable")
      break
    case 3:
      log.info (cmd.configurationValue[0] == 0  ? " - Accumulation Stop" : " - Accumulation Start")
      break
    case 4:
      log.info (cmd.configurationValue[0] == 0 ? " - Connected Device Not Use" : " - Connected Device Use")
      break
    case 5:
      log.info " - Time interval: " + (cmd.configurationValue[0] * 10) + "mins"
      break
     }
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd)
{
  log.debug "Switch binary report: "+cmd
  def value = (cmd.value ? "on" : "off")
  createEvent(name: "switch", value: value, type: "digital", descriptionText: "$device.displayName was turned $value")
}

def zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
  def result = []

  def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
  log.debug "msr: $msr"
  updateDataValue("MSR", msr)

  result << createEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false)
}

def zwaveEvent(hubitat.zwave.Command cmd) {
  log.debug "${device.displayName}: Unhandled: $cmd"
    return []
}

def on() {
  encapSequence([
    zwave.basicV1.basicSet(value: 0xFF),
    zwave.switchBinaryV1.switchBinaryGet(),
    meterGet(scale: 2)
  ], 3000)
}

def off() {
  encapSequence([
    zwave.basicV1.basicSet(value: 0x00),
    zwave.switchBinaryV1.switchBinaryGet(),
    meterGet(scale: 2)
  ], 3000)
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
  log.debug "ping()"
  refresh()
}

def poll() {
  sendHubCommand(refresh())
}

def refresh() {
  log.debug "refresh()"
  encapSequence([
    zwave.switchBinaryV1.switchBinaryGet(),
    meterGet(scale: 0),
    meterGet(scale: 2),
        zwave.configurationV1.configurationGet(parameterNumber: 1),
        zwave.configurationV1.configurationGet(parameterNumber: 2),
        zwave.configurationV1.configurationGet(parameterNumber: 3),
        zwave.configurationV1.configurationGet(parameterNumber: 4),
        zwave.configurationV1.configurationGet(parameterNumber: 5)
  ])
}

def configure() {
  log.info "configure()"
  def result = []

  log.debug "Configure zwaveInfo: "+zwaveInfo

  if (zwaveInfo.mfr == "0086") {	// Aeon Labs meter
    result << response(encap(zwave.configurationV1.configurationSet(parameterNumber: 80, size: 1, scaledConfigurationValue: 2)))	// basic report cc
    result << response(encap(zwave.configurationV1.configurationSet(parameterNumber: 101, size: 4, scaledConfigurationValue: 12)))	// report power in watts
    result << response(encap(zwave.configurationV1.configurationSet(parameterNumber: 111, size: 4, scaledConfigurationValue: 300)))	 // every 5 min
  } else if (zwaveInfo.mfr == "010F" && zwaveInfo.prod == "1801" && zwaveInfo.model == "1000") { // Fibaro Wall Plug UK
    result << response(encap(zwave.configurationV1.configurationSet(parameterNumber: 11, size: 1, scaledConfigurationValue: 2))) // 2% power change results in report
    result << response(encap(zwave.configurationV1.configurationSet(parameterNumber: 13, size: 2, scaledConfigurationValue: 5*60))) // report every 5 minutes
  } else if (zwaveInfo.mfr == "014F" && zwaveInfo.prod == "5053" && zwaveInfo.model == "3531") {
    result << response(encap(zwave.configurationV1.configurationSet(parameterNumber: 13, size: 2, scaledConfigurationValue: 15))) //report kWH every 15 min
  }
  result << response(encap(meterGet(scale: 0)))
  result << response(encap(meterGet(scale: 2)))

    def _stanbyPower = stanbyPower
    if(_stanbyPower == null){
      _stanbyPower = "Disable"
    }
    def _stanbyPowerValue = stanbyPowerValue
    if(_stanbyPowerValue == null){
      _stanbyPowerValue = 0
    }
    def _periodicMeasurement = periodicMeasurement
    if(_periodicMeasurement == null){
      _periodicMeasurement = 1
    }
    def _accumulation = accumulation
    if(_accumulation == null){
      _accumulation = 1
    }
    def _connectedDevice = connectedDevice
    if(_connectedDevice == null){
      _connectedDevice = 1
    }
    def _minTimeInterval = minTimeInterval
    if(_minTimeInterval == null){
      _minTimeInterval = 1
    }


    result << response(encap(zwave.configurationV1.configurationSet(parameterNumber: 1, size: 4, configurationValue: [_stanbyPowerValue, (_stanbyPower == "Enable" ? 1 : 0)])))
    result << response(encap(zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: (_periodicMeasurement == "Enable" ? 1 : 0) )))
    result << response(encap(zwave.configurationV1.configurationSet(parameterNumber: 3, size: 1, scaledConfigurationValue: (_accumulation == "Stop" ? 0 : 1) )))
    result << response(encap(zwave.configurationV1.configurationSet(parameterNumber: 4, size: 1, scaledConfigurationValue: (_connectedDevice == "Use" ? 1 : 0) )))
    result << response(encap(zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: _minTimeInterval)))
  result
}

def reset() {
  encapSequence([
    meterReset(),
    meterGet(scale: 0)
  ])
}

def meterGet(map){
  return zwave.meterV2.meterGet(map)
}

def meterReset(){
  return zwave.meterV2.meterReset()
}

/*
 * Security encapsulation support:
 */
def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
  def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
  if (encapsulatedCommand) {
    log.debug "Parsed SecurityMessageEncapsulation into: ${encapsulatedCommand}"
    zwaveEvent(encapsulatedCommand)
  } else {
    log.warn "Unable to extract Secure command from $cmd"
  }
}

def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
  def version = commandClassVersions[cmd.commandClass as Integer]
  def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
  def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
  if (encapsulatedCommand) {
    log.debug "Parsed Crc16Encap into: ${encapsulatedCommand}"
    zwaveEvent(encapsulatedCommand)
  } else {
    log.warn "Unable to extract CRC16 command from $cmd"
  }
}

private secEncap(hubitat.zwave.Command cmd) {
  log.debug "encapsulating command using Secure Encapsulation, command: $cmd"
  zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
}

private crcEncap(hubitat.zwave.Command cmd) {
  log.debug "encapsulating command using CRC16 Encapsulation, command: $cmd"
  zwave.crc16EncapV1.crc16Encap().encapsulate(cmd).format()
}

private encap(hubitat.zwave.Command cmd) {
  if (zwaveInfo?.zw?.contains("s")) {
    secEncap(cmd)
  } else if (zwaveInfo?.cc?.contains("56")){
    crcEncap(cmd)
  } else {
//		log.debug "no encapsulation supported for command: $cmd"
    cmd.format()
  }
}

private encapSequence(cmds, Integer delay=250) {
  delayBetween(cmds.collect{ encap(it) }, delay)
}
