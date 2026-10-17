package app.locker.plus

/** Package identity for a live Android window ID, never node contents.
 * Removal events, device lock and service lifecycle clear identities. A different
 * ID/type or an unknown new window can never inherit another window's owner. */
class WindowOwnerCache {
    private val owners = linkedMapOf<Pair<Int, Int>, String>()
    fun owner(id: Int, type: Int, observed: String?, allowCached: Boolean): String? {
        if (id < 0) return observed
        val key = id to type
        if (observed != null) {
            owners.remove(key)
            owners[key] = observed
            while (owners.size > 64) owners.remove(owners.keys.first())
            return observed
        }
        return if (allowCached) owners[key] else null
    }
    fun removed(id: Int) { if (id < 0) clear() else owners.keys.removeAll { it.first == id } }
    fun clear() = owners.clear()
}
