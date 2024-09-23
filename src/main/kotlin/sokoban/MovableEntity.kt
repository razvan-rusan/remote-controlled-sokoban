package sokoban
import processing.core.PGraphics
import processing.core.PImage
import processing.core.PVector

abstract class MovableEntity(open var look: PImage, open var position: PVector) {
    fun move(byv: PVector): Boolean {
        val ok : Boolean = checkBounds(byv)
        if (ok) {
            this.position + byv
            return true
        } else {
            return false
        }
    }

    abstract fun checkBounds(byv: PVector): Boolean
    abstract fun show(showOn: PGraphics)
}