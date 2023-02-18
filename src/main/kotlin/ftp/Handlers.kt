package ftp

import CHUNK_SIZE
import NEW_LINE
import openPassivePort
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.InputStreamReader
import java.lang.NumberFormatException
import java.net.Socket
import java.text.SimpleDateFormat
import kotlin.collections.HashMap

val handlers = HashMap<String, (String, FTPSession) -> Unit>()

/*
 TODO CHMOD
 */

fun registerHandlers() {
    registerHandler("USER") { data, session ->
        session.username = data
        session.returnCode(usernameOkay)
    }
    registerHandler("PASS") { data, session ->
        session.password = data
        session.returnCode(loggedIn)
    }
    registerHandler("SYST") { _, session ->
        session.returnCode(system)
    }
    registerHandler("PORT") { data, session ->
        if(session.dataConnection != null && session.dataConnection!!.isConnected) {
            session.dataConnection?.close()
            session.dataConnection = null
        }

        val parts = data.split(",")
        val host = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3]
        val port = try {
            parts[4].toInt() * 256 + parts[5].toInt()
        } catch (e: NumberFormatException) {
            session.returnCode(wrongParameters)
            return@registerHandler
        }

        val socket = Socket(host, port)
        session.dataConnection = socket

        session.returnCode(success)
    }
    registerHandler("LIST") { data, session ->
        val connection = session.dataConnection
        if(connection == null || connection.isClosed) {
            session.returnCode(cantOpenDataConnection)
            return@registerHandler
        } else {
            val dir = File(session.currentFile, data.ifEmpty { "." })
            if(dir.exists()) {
                session.returnCode(dataConnectionAlreadyOpen)

                dir.list()?.forEach {
                    connection.getOutputStream().write((it + NEW_LINE).toByteArray())
                }

                session.returnCode(closingDataConnection)
                connection.close()
                session.dataConnection = null
            } else {
                session.returnCode(fileUnavailable)
                return@registerHandler
            }
        }
    }
    registerHandler("MKD") { data, session ->
        if(data.isBlank()) {
            session.returnCode(fileUnavailable)
            return@registerHandler
        }
        val dir = File(session.currentFile, data)

        if(dir.mkdirs()) {
            session.returnCode(successfulCreation(dir))
        } else {
            session.returnCode(fileUnavailable)
        }
    }
    registerHandler("CWD") { data, session ->
        val proposedFile = File(session.currentFile, data)
        if(proposedFile.exists()) {
            session.currentFile = proposedFile.canonicalFile
            session.returnCode(actionExecuted)
        } else {
            session.returnCode(fileUnavailable)
        }
    }
    registerHandler("PWD") { _, session ->
        session.returnCode(successfulCreation(session.currentFile))
    }
    registerHandler("RMD") { data, session ->
        if(data.isBlank()) {
            session.returnCode(fileUnavailable)
            return@registerHandler
        }
        val dir = File(session.currentFile, data)

        if(dir.exists() && dir.delete()) {
            session.returnCode(actionExecuted)
        } else {
            session.returnCode(fileUnavailable)
        }
    }
    registerHandler("QUIT") { _, session ->
        // You've been a good client
        // A client I'm going to remember
        // A client, which deserves loves
        // Farewell, may our paths cross again
        session.returnCode(serviceClosing)
        session.connection.disconnectAsync()
    }
    registerHandler("MDTM") { data, session ->
        if(data.isBlank()) {
            session.returnCode(fileUnavailable)
            return@registerHandler
        }

        val file = File(session.currentFile, data)

        if(file.exists()) {
            session.returnCode(fileStatus(SimpleDateFormat("YYYYMMDDhhmmss").format(file.lastModified())))
        } else {
            session.returnCode(fileUnavailable)
        }
    }
    registerHandler("PASV") { _, session ->
        if(session.dataConnection != null && session.dataConnection!!.isConnected) {
            session.dataConnection?.close()
            session.dataConnection = null
        }

        val serverSocket = openPassivePort()

        Thread({
            session.dataConnection = serverSocket.accept()
        }, "Passive mode, waiting for connection on " + serverSocket.localPort).start()

        session.returnCode(passiveMode(serverSocket))
    }
    registerHandler("RETR") { data, session ->
        val connection = session.dataConnection
        if(connection == null || connection.isClosed) {
            session.returnCode(cantOpenDataConnection)
            return@registerHandler
        } else {
            val file = File(session.currentFile, data)
            if(file.exists()) {
                session.returnCode(fileStatusOkay)

                when(session.transferType) {
                    TransferType.BINARY -> {
                        val fileBytes = BufferedInputStream(FileInputStream(file))
                        while(fileBytes.available() > 0)
                            connection.getOutputStream().write( fileBytes.readNBytes(CHUNK_SIZE))
                        fileBytes.close()
                    }
                    TransferType.ASCII -> {
                        val fileReader = BufferedReader(FileReader(file))
                        var currLine: String?
                        while(fileReader.readLine().also { currLine = it } != null)
                            connection.getOutputStream().write((currLine + NEW_LINE).toByteArray())
                        fileReader.close()
                    }
                }

                session.returnCode(closingDataConnection)
                connection.close()
                session.dataConnection = null
            } else {
                session.returnCode(fileUnavailable)
                return@registerHandler
            }
        }
    }
    registerHandler("SIZE") { data, session ->
        if(data.isBlank()) {
            session.returnCode(fileUnavailable)
            return@registerHandler
        }

        val file = File(session.currentFile, data)

        if(file.exists()) {
            session.returnCode(fileStatus(file.length().toString()))
        } else {
            session.returnCode(fileUnavailable)
        }
    }
    registerHandler("RNFR") { data, session ->
        if(data.isBlank()) {
            session.returnCode(fileUnavailable)
            return@registerHandler
        }
        val file = File(session.currentFile, data)

        if(file.exists()) {
            session.renameTransaction = file
            session.returnCode(requestedFileAction)
        } else {
            session.returnCode(fileUnavailable)
        }
    }
    registerHandler("RNTO") { data, session ->
        if(data.isBlank() || session.renameTransaction == null) {
            session.returnCode(fileUnavailable)
            return@registerHandler
        }
        val file = File(session.currentFile, data)

        if(!file.exists() && session.renameTransaction!!.renameTo(file)) {
            session.returnCode(actionExecuted)
        } else if(file.exists()) {
            session.returnCode(fileNameNotAllowed)
        } else {
            session.returnCode(fileUnavailable)
        }
    }
    registerHandler("TYPE") { data, session ->
        val transferType = TransferType.find(data.first())
        if(transferType != null) {
            session.transferType = transferType
            session.returnCode(success)
        } else {
            session.returnCode(invalidParameter)
        }
    }
    registerHandler("STOR") { data, session ->
        val connection = session.dataConnection
        if(connection == null || connection.isClosed) {
            session.returnCode(cantOpenDataConnection)
            return@registerHandler
        } else {
            val file = File(session.currentFile, data)
            if(!file.exists()) {
                session.returnCode(fileStatusOkay)

                when(session.transferType) {
                    TransferType.BINARY -> {
                        val input = connection.getInputStream()
                        val fileBytes = FileOutputStream(file)
                        while(input.available() > 0)
                            fileBytes.write(input.readNBytes(CHUNK_SIZE))
                        fileBytes.close()
                    }
                    TransferType.ASCII -> {
                        val input = BufferedReader(InputStreamReader(connection.getInputStream()))
                        val fileBytes = FileOutputStream(file)
                        var currLine: String?
                        while(input.readLine().also { currLine = it } != null)
                            fileBytes.write((currLine + NEW_LINE).toByteArray())
                        fileBytes.close()
                    }
                }

                session.returnCode(closingDataConnection)
                connection.close()
                session.dataConnection = null
            } else {
                session.returnCode(fileUnavailable)
                return@registerHandler
            }
        }
    }

    registerAlias("STOU", "STOR") // Technically not the same, but shouldn't matter for me
    registerAlias("NLST", "LIST")
    registerAlias("DELE", "RMD") // technically rmd is for directories and dele for files, but since the linux ftp program maps rmd to the rm command, it feels more reasonable to allow it to do both
}

fun registerHandler(command: String, handler: (data: String, session: FTPSession) -> Unit) {
    handlers[command] = handler
}

fun registerAlias(command: String, oldCommand: String) {
    handlers[command] = handlers[oldCommand]!!
}