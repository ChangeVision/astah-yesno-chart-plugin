package astah.plugin.model

import java.awt.Point
import kotlin.math.max
import kotlin.random.Random

class YesNoChartModel(val root: YesNoChartElement) {
    val elementWidth: Double
    val elementHeight: Double
    val paperWidth: Int
    val paperHeight: Int
    val yourChoiceDescriptionFontSize = 10.5
    val positions = mutableMapOf<YesNoChartElement, Point>()

    private val treePositions = mutableMapOf<YesNoChartElement, List<Int>>()
    private val rankFrequency = mutableMapOf<Int, MutableList<List<Int>>>()
    private val possibleUnitWidthRange = mutableMapOf<Int, Int>()
    private val orderX = mutableMapOf<List<Int>, Int>()
    private val maxStringLength: Int

    private fun calculateTreePositions(element: YesNoChartElement, currentTreePosition: List<Int>) {
        if (!treePositions.containsKey(element)) {
            treePositions[element] = currentTreePosition
            if (element is YesNoChartElement.Question) {
                val yesPosition = mutableListOf<Int>()
                val noPosition = mutableListOf<Int>()
                yesPosition.addAll(currentTreePosition)
                noPosition.addAll(currentTreePosition)
                yesPosition.add(1)
                noPosition.add(2)
                calculateTreePositions(element.yesOption.selection, yesPosition)
                calculateTreePositions(element.noOption.selection, noPosition)
            }
        } else if (element is YesNoChartElement.YourChoice) {
            if (treePositions[element]!!.size < currentTreePosition.size) {
                treePositions.remove(element)
                treePositions[element] = currentTreePosition
            }
        }
    }

    private fun calculateMaxStingLength(element: YesNoChartElement, currentMax: Int): Int {
        fun stringWidth(s: String): Int =
             s.toCharArray().count { it.toInt() < 128 } + s.toCharArray().count { it.toInt() >= 128 } * 2

        return when (element) {
            is YesNoChartElement.YourChoice -> {
                val stringWidth = stringWidth(element.description) + stringWidth(element.statement)
                max(stringWidth, currentMax)
            }
            is YesNoChartElement.Question -> {
                val stringWidth = stringWidth(element.statement) +
                        stringWidth(element.noOption.guard) + stringWidth(element.yesOption.guard)
                val yesOptionMaxLength = calculateMaxStingLength(element.yesOption.selection, stringWidth)
                val noOptionMaxLength = calculateMaxStingLength(element.noOption.selection, stringWidth)
                max(currentMax, max(yesOptionMaxLength, noOptionMaxLength))
            }
        }
    }

    private fun calculatePositions(element: YesNoChartElement) {
        val rank = treePositions[element]!!
        val unitSize = possibleUnitWidthRange[rank.size]!!
        val randomShift = if (unitSize > elementWidth) Random.nextInt(unitSize - elementWidth.toInt()) else 0
        positions[element] =
            Point(orderX[rank]!! * unitSize + randomShift, rank.size * (elementHeight.toInt() + 30))
        if (element is YesNoChartElement.Question) {
            calculatePositions(element.yesOption.selection)
            calculatePositions(element.noOption.selection)
        }
    }

    init {
        calculateTreePositions(root, listOf())
        maxStringLength = calculateMaxStingLength(root, 0)

        if (maxStringLength < 180) {
            elementWidth = 200.0
            elementHeight = 100.0
        } else {
            elementWidth = maxStringLength * 0.7
            elementHeight = elementWidth / 2.0
        }

        treePositions.forEach { (_, rank) ->
            if (!rankFrequency.containsKey(rank.size)) {
                rankFrequency[rank.size] = mutableListOf(rank)
            } else {
                rankFrequency[rank.size]!!.add(rank)
            }
        }

        paperHeight = treePositions.maxBy { (_, rank) -> rank.size }!!.let { maxLevel ->
            (maxLevel.value.size + 1 ) * (elementHeight.toInt() + 30)
        }
        val mostWideRankSize = rankFrequency.maxBy { (_, frequency) -> frequency.size }!!.value.size

        paperWidth = ((mostWideRankSize - 1) * (elementWidth.toInt() + 10) + elementWidth.toInt())

        rankFrequency.forEach { (_, rankList) ->
            val sortedRanks = rankList.sortedBy { rank -> rank.joinToString(".") { it.toString() } }
            var i = 0
            sortedRanks.forEach { orderX[it] = i++ }
        }

        rankFrequency.forEach { (rank, frequency) ->
            possibleUnitWidthRange[rank] = ((mostWideRankSize - 1) * (elementWidth.toInt() + 10) +
                    elementWidth.toInt()) / frequency.size
        }
        calculatePositions(root)
    }
}