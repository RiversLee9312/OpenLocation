package dev.openlocation.runtime

/** A broker may never replace an aggregate stream containing a recipient that did not opt in. */
object TargetPolicy {
    fun allows(callerUid: Int, workSourceUids: List<Int>, selected: Set<Int>): Boolean {
        val recipients = workSourceUids.filter { it != callerUid }
        return if (recipients.isEmpty()) callerUid in selected
        else recipients.all { it in selected }
    }
}
