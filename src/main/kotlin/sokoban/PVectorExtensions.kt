package sokoban

import processing.core.PVector

operator fun PVector.plus(other: PVector): PVector {
    this.add(other)
    return this
}

operator fun PVector.minus(other: PVector): PVector {
    this.sub(other)
    return this
}

operator fun PVector.times(scalar: Float): PVector {
    this.mult(scalar)
    return this
}

operator fun PVector.times(other: PVector): Float {
    return this.dot(other)
}