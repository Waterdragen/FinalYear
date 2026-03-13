package com.example.finalyear.util

class OverwritingQueue<T>(val capacity: Int) : Iterable<T> {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val buffer: MutableList<T?> = MutableList(capacity) { null }
    private var size = 0
    private var headPos = 0
    private var tailPos = 0

    fun isEmpty(): Boolean = size == 0
    fun isFull(): Boolean = size == capacity
    fun size(): Int = size

    fun push(item: T) {
        buffer[tailPos] = item

        if (size < capacity) {
            // Queue not full, only increase size
            size++
        } else {
            // Queue is full, wrap around head
            headPos++
            if (headPos == capacity) headPos = 0
        }
        tailPos++
        if (tailPos == capacity) tailPos = 0
    }

    fun get(index: Int): T? {
        require(index in 0 until size) { "index $index out of bounds (size=$size)"}
        var pos = headPos + index
        if (pos >= capacity) pos -= capacity
        return buffer[pos]
    }

    fun clear() {
        size = 0
        headPos = 0
        tailPos = 0
        buffer.fill(null)
    }

    override fun iterator(): Iterator<T> = object : Iterator<T> {
        var index = 0
        override fun hasNext(): Boolean = index < size

        override fun next(): T {
            if (!hasNext()) throw NoSuchElementException()
            return get(index++) ?: throw IllegalStateException("Unexpected null in non null queue")
        }
    }

    // this iterator disregards the items pushed
    fun unorderedIterator(): Iterator<T> = buffer.filterNotNull().iterator()

    override fun toString(): String {
        return iterator().asSequence()
            .joinToString(prefix = "[", postfix = "]") { it.toString() }
    }
}