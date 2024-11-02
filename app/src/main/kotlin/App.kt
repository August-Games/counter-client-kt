package games.august.app

import games.august.utils.Printer

// This is the main entrypoint of the application.
// It uses the Printer class, from the `:utils` subproject.
fun main() {
    val message = "Hello JetBrains!"
    val printer = Printer(message)
    printer.printMessage()
}
