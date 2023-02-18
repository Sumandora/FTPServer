package ftp

import localHostname
import java.io.File
import java.net.ServerSocket

val dataConnectionAlreadyOpen = Pair(125, "Data connection already open; transfer starting.")
val fileStatusOkay = Pair(150, "File status okay; about to open data connection.")

val success = Pair(200, "The requested action has been successfully completed.")
val system = Pair(215, "EXPERIMENTAL_FTP_SERVER") // NAME system type. Where NAME is an official system name from the registry kept by IANA. ; Not true for us
val closingDataConnection = Pair(226, "Closing data connection. Requested file action successful (for example, file transfer or file abort).")
val loggedIn = Pair(230, "User logged in, proceed.")
val actionExecuted = Pair(250, "Requested file action okay, completed.")
val serviceClosing = Pair(221, "Service closing control connection. Logged out if appropriate.")

val usernameOkay = Pair(331, "User name okay, need password.")
val requestedFileAction = Pair(350, "Requested file action pending further information")

val cantOpenDataConnection = Pair(425, "Can't open data connection.")

val wrongParameters = Pair(501, "Syntax error in parameters or arguments.")
val invalidParameter = Pair(504, "Command not implemented for that parameter.")
val notImplemented = Pair(520, "Command not implemented.")
val fileUnavailable = Pair(550, "Requested action not taken. File unavailable (e.g., file not found, no access).")
val fileNameNotAllowed = Pair(553, "Requested action not taken. File name not allowed.")

fun successfulCreation(file: File) = Pair(257, "\"" + file.absolutePath + "\" created.")
fun greet(motd: String) = Pair(200, motd)
fun fileStatus(comment: String) = Pair(213, comment)
fun passiveMode(serverSocket: ServerSocket) = Pair(227, localHostname.joinToString(",") + "," + serverSocket.localPort.let { val p1 = it / 256; val p2 = it % 256; "$p1,$p2" })