package ftp

import Connection
import NEW_LINE
import java.io.File
import java.net.Socket
import java.nio.file.Paths

class FTPSession(val connection: Connection) {
    private var nextHandler: ((String, FTPSession) -> Unit)? = null

    var username: String? = null
    var password: String? = null

    var dataConnection: Socket? = null
    var renameTransaction: File? = null

    var transferType = TransferType.ASCII

    var currentFile: File = Paths.get(".").toAbsolutePath().normalize().toFile()

    fun handle(data: String) {
        nextHandler?.also {
            it.invoke(data, this)
            return
        }

        var command = if(data.contains(" ")) data.split(" ")[0] else data

        command = command.trim()

        val handler = handlers[command]
        if(handler == null) {
            returnCode(notImplemented)
            return
        }

        var parameters = data.substring(command.length)

        if(parameters.endsWith(NEW_LINE))
            parameters = parameters.substring(0, parameters.length - NEW_LINE.length)

        if(parameters.startsWith(" "))
            parameters = parameters.substring(1 /* remove space */)

        handler(parameters, this)
    }

    fun greet() {
        returnCode(greet("Welcome to the most experimental ftp server ever!"))
    }

    fun returnCode(error: Pair<Int, String>) {
        connection.send(error.first.toString() + " " + error.second)
    }

    fun close() {
        dataConnection?.close()
    }
}