package com.jupiter.smartwhitecane

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class MainActivity : Activity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvReceived: TextView
    private lateinit var btnConnect: Button
    private lateinit var btnNightOn: Button
    private lateinit var btnNightOff: Button
    private lateinit var btnLock: Button

    private val bluetoothAdapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    private val sppUuid: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    companion object {
        private const val REQUEST_BLUETOOTH = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvReceived = findViewById(R.id.tvReceived)

        btnConnect = findViewById(R.id.btnConnect)
        btnNightOn = findViewById(R.id.btnNightOn)
        btnNightOff = findViewById(R.id.btnNightOff)
        btnLock = findViewById(R.id.btnLock)

        requestBluetoothPermission()

        btnConnect.setOnClickListener {
            connectToHC05()
        }

        btnNightOn.setOnClickListener {
            sendCommand("N")
        }

        btnNightOff.setOnClickListener {
            sendCommand("n")
        }

        btnLock.setOnClickListener {
            sendCommand("L")
        }
    }

    private fun requestBluetoothPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    ),
                    REQUEST_BLUETOOTH
                )
            }
        }
    }

    private fun connectToHC05() {

        if (bluetoothAdapter == null) {
            tvStatus.text = "● Bluetooth not supported"
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            tvStatus.text = "● Please turn ON Bluetooth"
            return
        }

        if (android.os.Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestBluetoothPermission()
            return
        }

        val device: BluetoothDevice? =
            bluetoothAdapter.bondedDevices.firstOrNull {
                it.name.equals("HC-05", ignoreCase = true)
            }

        if (device == null) {
            tvStatus.text = "● HC-05 not paired"
            tvReceived.text =
                "Please pair HC-05 from phone Bluetooth settings first."
            return
        }

        tvStatus.text = "● Connecting to HC-05..."

        Thread {
            try {
                bluetoothAdapter.cancelDiscovery()

                bluetoothSocket =
                    device.createRfcommSocketToServiceRecord(sppUuid)

                bluetoothSocket?.connect()

                outputStream = bluetoothSocket?.outputStream
                inputStream = bluetoothSocket?.inputStream

                runOnUiThread {
                    tvStatus.text = "● HC-05 CONNECTED"
                    btnConnect.text = "CONNECTED"
                }

                readIncomingData()

            } catch (e: Exception) {

                runOnUiThread {
                    tvStatus.text = "● Connection failed"
                    tvReceived.text =
                        "HC-05 connection failed.\n\n${e.message}"
                }

                closeConnection()
            }
        }.start()
    }

    private fun sendCommand(command: String) {

        Thread {
            try {
                outputStream?.write(command.toByteArray())
                outputStream?.flush()

                runOnUiThread {
                    tvReceived.text =
                        "Sent to HC-05: $command\n\n" +
                        tvReceived.text
                }

            } catch (e: Exception) {

                runOnUiThread {
                    tvStatus.text = "● Not connected"
                }
            }
        }.start()
    }

    private fun readIncomingData() {

        val buffer = ByteArray(1024)

        try {
            while (bluetoothSocket?.isConnected == true) {

                val bytes = inputStream?.read(buffer) ?: -1

                if (bytes > 0) {

                    val received =
                        String(buffer, 0, bytes)

                    runOnUiThread {

                        tvReceived.text =
                            received.trim() +
                            "\n\n" +
                            tvReceived.text
                    }
                }
            }

        } catch (_: Exception) {
            runOnUiThread {
                tvStatus.text = "● Bluetooth disconnected"
            }
        }
    }

    private fun closeConnection() {

        try {
            inputStream?.close()
        } catch (_: Exception) {
        }

        try {
            outputStream?.close()
        } catch (_: Exception) {
        }

        try {
            bluetoothSocket?.close()
        } catch (_: Exception) {
        }

        inputStream = null
        outputStream = null
        bluetoothSocket = null
    }

    override fun onDestroy() {
        super.onDestroy()
        closeConnection()
    }
}
