package com.hyntix.pdf.viewer.util

object ArrayUtils {
    /** Transforms (0,1,2,2,3) to (0,1,2,3)  */
    fun deleteDuplicatedPages(pages: IntArray): IntArray {
        val result = ArrayList<Int>()
        var lastInt = -1
        for (currentInt in pages) {
            if (lastInt != currentInt) {
                result.add(currentInt)
            }
            lastInt = currentInt
        }
        val arrayResult = IntArray(result.size)
        for (i in result.indices) {
            arrayResult[i] = result[i]
        }
        return arrayResult
    }

    /** Transforms (0, 4, 4, 6, 6, 6, 3) into (0, 1, 1, 2, 2, 2, 3)  */
    fun calculateIndexesInDuplicateArray(originalUserPages: IntArray): IntArray {
        if (originalUserPages.isEmpty()) {
            return IntArray(0)
        }
        val result = IntArray(originalUserPages.size)
        var index = 0
        result[0] = index
        for (i in 1 until originalUserPages.size) {
            if (originalUserPages[i] != originalUserPages[i - 1]) {
                index++
            }
            result[i] = index
        }
        return result
    }

    fun arrayToString(array: IntArray): String {
        return array.joinToString(prefix = "[", postfix = "]", separator = ",")
    }
}
