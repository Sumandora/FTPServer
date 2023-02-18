import ftp.registerHandlers
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ThreadLocalRandom

lateinit var ftpSocket: ServerSocket
val connections = ArrayList<Connection>()

const val NEW_LINE = "\r\n"
const val CHUNK_SIZE = 4096
// For passive mode we need to know how others reach us.
val localHostname = arrayOf("127", "0", "0", "1")

fun main(args: Array<String>) {
    ftpSocket = ServerSocket(args[0].toInt())
    registerHandlers()
    Runtime.getRuntime().addShutdownHook(Thread({
        connections.forEach { it.disconnectAsync() }
        ftpSocket.close()
    }, "Shutdown Hook"))
    while(true) {
        connections.add(Connection(ftpSocket.accept()))
    }
}

fun openPassivePort(): ServerSocket {
    return try {
        val serverSocket = ServerSocket(ThreadLocalRandom.current().nextInt(UShort.MAX_VALUE.toInt()))
        if(serverSocket.isBound)
            serverSocket
        else
            openPassivePort()
    } catch (t: Throwable) {
        openPassivePort()
    }
}