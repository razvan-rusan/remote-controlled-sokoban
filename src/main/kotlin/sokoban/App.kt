package sokoban

import kotlinx.coroutines.Dispatchers
import processing.core.PApplet

val networkDispatcher = Dispatchers.IO

fun main(args: Array<String>) {
    PApplet.main(arrayOf("sokoban.${args[0]}"))
}