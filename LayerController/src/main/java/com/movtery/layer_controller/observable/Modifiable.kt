package com.movtery.layer_controller.observable

interface Modifiable<E> {

    fun isModified(other: E): Boolean
}

fun <E> List<E>.isModified(
    others: List<E>
): Boolean where E : Modifiable<E> {
    if (this.size != others.size) return true

    for (i in this.indices) {
        val current = this[i]
        val other = others[i]

        if (current.isModified(other)) {
            return true
        }
    }

    return false
}
