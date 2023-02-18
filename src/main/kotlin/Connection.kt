import ftp.FTPSession
import java.net.Socket

class Connection(private val socket: Socket) {

    private val ftpSession = FTPSession(this)

    private val thread = Thread({
        while (true) {
            synchronized(socket) {
                if(!socket.isConnected || socket.isClosed) {
                    onDisconnect()
                    return@Thread
                }
                socket.getInputStream().also { stream ->
                    val size = stream.available()
                    if(size > 0) {
                        val bytes = ByteArray(size)
                        stream.read(bytes)
                        onReceive(bytes.decodeToString())
                    }
                }
            }
        }
    }, "Connection with " + socket.inetAddress.toString())

    init {
        socket.soTimeout = 1000 * 60 * 5
        println("New connection with " + socket.inetAddress.toString())
        thread.start()
        ftpSession.greet()
    }

    private fun onDisconnect() {
        ftpSession.close()
        println("Disconnected")
    }

    private fun onReceive(data: String) {
        print("RECV: $data")
        ftpSession.handle(data)
    }

    fun send(data: String) {
        socket.getOutputStream().apply {
            write((data + NEW_LINE).also { print("SEND: $it") }.toByteArray())
            flush()
        }
    }

    fun disconnectAsync() {
        socket.close()
    }

}