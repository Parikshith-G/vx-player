/**
 * Role: Natural alphanumeric comparator for human-friendly ordering.
 * Responsibility: Compares strings by treating multi-digit numeric blocks numerically.
 * Details: Ensures episode numbers and names sort naturally (e.g. animename_2 before animename_1000).
 */
package com.example.mxoffline.util

object NaturalOrderComparator : Comparator<String> {

    override fun compare(s1: String?, s2: String?): Int {
        if (s1 === s2) return 0
        if (s1 == null) return -1
        if (s2 == null) return 1

        val len1 = s1.length
        val len2 = s2.length
        var i1 = 0
        var i2 = 0

        while (i1 < len1 && i2 < len2) {
            val c1 = s1[i1]
            val c2 = s2[i2]

            if (isDigit(c1) && isDigit(c2)) {
                while (i1 < len1 && s1[i1] == '0') {
                    i1++
                }
                val sigStart1 = i1
                while (i1 < len1 && isDigit(s1[i1])) {
                    i1++
                }
                val sigLen1 = i1 - sigStart1

                while (i2 < len2 && s2[i2] == '0') {
                    i2++
                }
                val sigStart2 = i2
                while (i2 < len2 && isDigit(s2[i2])) {
                    i2++
                }
                val sigLen2 = i2 - sigStart2

                if (sigLen1 != sigLen2) {
                    return sigLen1.compareTo(sigLen2)
                }

                for (k in 0 until sigLen1) {
                    val d1 = s1[sigStart1 + k]
                    val d2 = s2[sigStart2 + k]
                    if (d1 != d2) {
                        return d1.compareTo(d2)
                    }
                }
            } else {
                val diff = c1.lowercaseChar().compareTo(c2.lowercaseChar())
                if (diff != 0) {
                    return diff
                }
                i1++
                i2++
            }
        }

        if (i1 < len1) return 1
        if (i2 < len2) return -1

        return s1.compareTo(s2)
    }

    private fun isDigit(c: Char): Boolean = c in '0'..'9'
}
