package fr.vueconfort.vision

/** One-use image hand-off within this process. Images are never persisted here. */
object OpticalSnapshot {
    private var pending: IntArray? = null

    @Synchronized
    fun put(pixels: IntArray) {
        require(pixels.size == 768 * 256) { "Optical snapshot must contain 768 × 256 pixels" }
        pending = pixels.copyOf()
    }

    @Synchronized
    fun take(): IntArray? = pending?.copyOf().also { pending = null }

    @Synchronized
    fun clear() { pending = null }
}
