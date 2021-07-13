package astah.plugin

import astah.plugin.model.OptionModel
import astah.plugin.model.YesNoChartElement
import astah.plugin.model.YesNoChartModel
import com.change_vision.jude.api.inf.AstahAPI
import com.change_vision.jude.api.inf.model.*
import com.change_vision.jude.api.inf.presentation.ILinkPresentation
import com.change_vision.jude.api.inf.presentation.INodePresentation
import org.apache.poi.sl.usermodel.LineDecoration
import org.apache.poi.sl.usermodel.ShapeType
import org.apache.poi.xslf.usermodel.XMLSlideShow
import java.awt.Color
import java.awt.Dimension
import java.awt.geom.Rectangle2D
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Double.min
import javax.swing.JButton
import kotlin.math.abs

val camellia = Color(218, 83, 110)
val lemon = Color(255, 253, 231)
val heavyLemon = Color(248, 255, 160)
val heavyYellow = Color(255, 222, 53)
val mediumblue = Color(0, 0, 205)
val skyblue = Color(160, 216, 239)
val lightGreen = Color(216, 240, 183)

val yesColorfulColor: Color = Color.red
val noColorfulColor: Color = mediumblue
val questionColorfulColor = lemon
val paperColorfulColor = heavyLemon
val yourSelectionColorfulColor = skyblue

val yesMonochromeColor: Color = Color.black
val noMonochromeColor: Color = Color.gray
val questionMonochromeColor: Color = Color.white
val paperMonochromeColor: Color = Color.white
val yourSelectionMonochromeColor: Color = Color.LIGHT_GRAY

class ButtonGenerate: JButton("Generate") {
    private fun constructYesNoChartModel(diagram: IActivityDiagram): YesNoChartElement? {
        val allNodePresentation = diagram.presentations.filterIsInstance<INodePresentation>()
        val allLinkPresentation = diagram.presentations.filterIsInstance<ILinkPresentation>()
        val allInitialNodes = allNodePresentation.filter { it.type == "InitialNode" }
        val allActionNodes = allNodePresentation.filter { it.type == "Action" }
        val allFlowPresentation = allLinkPresentation.filter { it.model is IFlow }
        val questionsAndChoice = mutableMapOf<String, YesNoChartElement>()

        fun getDirectChild(node: INodePresentation): List<INodePresentation> {
            val flows = allFlowPresentation.filter { it.source == node }
            return allActionNodes.filter { action -> flows.any { flow -> flow.target == action } }
        }

        fun getDirectChildWithGuards(node: INodePresentation): List<Pair<String, INodePresentation>> {
            val arrows =  allLinkPresentation.filter { it.source == node }
            val result = mutableListOf<Pair<String, INodePresentation>>()
            arrows.forEach { arrow ->
                val model = arrow.model
                if (model !is IFlow) {
                    throw Error()
                }
                result += Pair(model.guard, arrow.target)
            }
            return result
        }

        fun getEntry(action: INodePresentation): String {
            val model = action.model
            if (model !is INamedElement) {
                throw Error()
            }
            return model.name
        }

        fun isYesGuard(s: String): Boolean = listOf("yes", "はい").contains(s.toLowerCase())

        fun constructYesNoChartModelForAction(action: INodePresentation): YesNoChartElement {
            fun constructQuestion(question: String, yesOption: Pair<String, INodePresentation>,
                                  noOption: Pair<String, INodePresentation>): YesNoChartElement {
                if (!questionsAndChoice.containsKey(getEntry(yesOption.second))) {
                    questionsAndChoice[getEntry(yesOption.second)] = constructYesNoChartModelForAction(yesOption.second)
                }
                if (!questionsAndChoice.containsKey(getEntry(noOption.second))) {
                    questionsAndChoice[getEntry(noOption.second)] = constructYesNoChartModelForAction(noOption.second)
                }
                return YesNoChartElement.Question(question,
                    OptionModel(yesOption.first, questionsAndChoice[getEntry(yesOption.second)]!!),
                    OptionModel(noOption.first, questionsAndChoice[getEntry(noOption.second)]!!))
            }

            fun determineYesNo(statement: String, children: List<Pair<String, INodePresentation>>): YesNoChartElement =
                if (isYesGuard(children[0].first) ||
                    (children[0].second.location.x < children[1].second.location.x) && !isYesGuard(children[1].first))
                    constructQuestion(statement, children[0], children[1])
                else
                    constructQuestion(statement, children[1], children[0])

            val children = getDirectChildWithGuards(action)
            val actionStatement = getEntry(action)
            if (children.isEmpty()) {
                if (!questionsAndChoice.containsKey(actionStatement)) {
                    val dividedTexts = actionStatement.split(":")
                    val yourChoiceStatement = dividedTexts.first()
                    val description = if (dividedTexts.size > 1) dividedTexts[1] else ""
                    questionsAndChoice[actionStatement] =
                        YesNoChartElement.YourChoice(yourChoiceStatement, description)
                }
                return questionsAndChoice[actionStatement]!!
            } else {
                val firstNode = children.first().second
                val firstNodeModel = firstNode.model
                return when {
                    firstNodeModel is IControlNode && firstNodeModel.isDecisionMergeNode -> {
                        val nextNodes = getDirectChildWithGuards(firstNode)
                        if (nextNodes.size != 2) {
                            printError("The number of options for each decision node must be 2!")
                            throw Error()
                        }
                        determineYesNo(actionStatement, nextNodes)
                    }
                    children.size != 2 -> {
                        printError("The number of options for each question must be 2!")
                        throw Error()
                    }
                    else -> determineYesNo(actionStatement, children)
                }
            }
        }

        if (allInitialNodes.isEmpty()) {
            printError("Define one start node")
            return null
        }
        val firstQuestion = getDirectChild(allInitialNodes.first())
        if (firstQuestion.isEmpty()) {
            printError("Define at least one question")
            return null
        }
        return constructYesNoChartModelForAction(firstQuestion.first())
    }

    private fun drawYesNoChart(yesNoChartModel: YesNoChartModel, colorPattern: ColorPattern) {
        val ppt = XMLSlideShow()
        val slide = ppt.createSlide()
        val elementModel = mutableListOf<YesNoChartElement>()
        val yesNoChartElement = yesNoChartModel.root
        val positions = yesNoChartModel.positions
        val projectDirectory = File(AstahAPI.getAstahAPI().projectAccessor.projectPath).parent

        val yesColor = if (colorPattern == ColorPattern.Colorful) yesColorfulColor else yesMonochromeColor
        val noColor = if (colorPattern == ColorPattern.Colorful) noColorfulColor else noMonochromeColor
        val questionColor =
            if (colorPattern == ColorPattern.Colorful) questionColorfulColor else questionMonochromeColor
        val paperColor = if (colorPattern == ColorPattern.Colorful) paperColorfulColor else paperMonochromeColor
        val yourSelectionColor =
            if (colorPattern == ColorPattern.Colorful) yourSelectionColorfulColor else yourSelectionMonochromeColor

        fun drawYesNoChart1(yesNoChartElement: YesNoChartElement, source: YesNoChartElement?, isYes: Boolean?) {
            fun fontSize(string: String): Double {
                val length = string.length
                return when {
                    length < 7 -> 24.0
                    length in 7..19 -> 16.0
                    else -> 10.5
                }
            }
            val targetPosition = positions[yesNoChartElement]
            val sourcePosition = positions[source]

            if (targetPosition == null) {
                throw Error()
            }

            if (sourcePosition != null && isYes != null) {
                val arrow = slide.createConnector()
                val x0 = sourcePosition.getX() + yesNoChartModel.elementWidth / 2
                val y0 = sourcePosition.getY() + yesNoChartModel.elementHeight
                val x1 = targetPosition.getX() + yesNoChartModel.elementWidth / 2
                val y1 = targetPosition.getY()
                val x = min(x0, x1)
                val y = min(y0, y1)
                val w = abs(x1 - x0)
                val h = abs(y1 - y0)
                arrow.shapeType = ShapeType.LINE
                arrow.anchor = Rectangle2D.Double(x, y, w, h)
                arrow.flipHorizontal = x0 > x1
                arrow.flipVertical = y0 > y1
                arrow.lineWidth = 4.0
                arrow.lineTailDecoration = LineDecoration.DecorationShape.ARROW
                arrow.lineColor = if (isYes) yesColor else noColor
            }

            if (!elementModel.contains(yesNoChartElement)) {
                val statementBox = slide.createTextBox()
                statementBox.shapeType = ShapeType.RECT
                statementBox.anchor = Rectangle2D.Double(targetPosition.getX(), targetPosition.getY(),
                        yesNoChartModel.elementWidth, yesNoChartModel.elementHeight)
                val paragraph = statementBox.textParagraphs[0]
                val statement = paragraph.addNewTextRun()
                elementModel += yesNoChartElement

                if (yesNoChartElement is YesNoChartElement.YourChoice) {
                    statementBox.lineWidth = 0.0
                    statementBox.fillColor = yourSelectionColor
                    statement.fontSize = fontSize(yesNoChartElement.statement)
                    statement.isBold = true
                    statement.setText(yesNoChartElement.statement)
                    if (yesNoChartElement.description != "") {
                        val descriptionParagraph = statementBox.addNewTextParagraph().addNewTextRun()
                        descriptionParagraph.fontSize = yesNoChartModel.yourChoiceDescriptionFontSize
                        descriptionParagraph.setFontColor(Color.black)
                        descriptionParagraph.setText(yesNoChartElement.description)
                    }
                } else if (yesNoChartElement is YesNoChartElement.Question) {
                    val fontSize =
                        min(fontSize(yesNoChartElement.yesOption.guard), fontSize(yesNoChartElement.yesOption.guard))
                    statementBox.lineWidth = 3.0
                    statementBox.lineColor = Color.black
                    statementBox.fillColor = questionColor
                    statement.fontSize = fontSize(yesNoChartElement.statement)
                    statement.setText(yesNoChartElement.statement)
                    val yesParagraph = statementBox.addNewTextParagraph().addNewTextRun()
                    yesParagraph.fontSize = fontSize(yesNoChartElement.yesOption.guard)
                    yesParagraph.setFontColor(yesColor)
                    yesParagraph.isBold = true
                    yesParagraph.setText(yesNoChartElement.yesOption.guard)
                    val noParagraph = statementBox.addNewTextParagraph().addNewTextRun()
                    noParagraph.fontSize = fontSize
                    noParagraph.setFontColor(noColor)
                    noParagraph.isBold = true
                    noParagraph.setText(yesNoChartElement.noOption.guard)

                    drawYesNoChart1(yesNoChartElement.yesOption.selection, yesNoChartElement, true)
                    drawYesNoChart1(yesNoChartElement.noOption.selection, yesNoChartElement, false)
                }
            }
        }

        ppt.pageSize = Dimension(yesNoChartModel.paperWidth, yesNoChartModel.paperHeight)
        ppt.slideMasters[0].background.fillColor = paperColor

        try  {
            drawYesNoChart1(yesNoChartElement, null, null)
            FileOutputStream(projectDirectory + File.separator + pptxFileName).use { out -> ppt.write(out) }
            printMessage("pptx file is created!")
        } catch (ioe: IOException) {
            printError("IO Error has occurred")
        }
    }

    fun push(diagram: IDiagram?, colorPattern: ColorPattern) {
        if (diagram == null || diagram !is IActivityDiagram) {
            printError("Please select any activity diagram")
            return
        }
        constructYesNoChartModel(diagram)?.let { yesNoModel ->
            drawYesNoChart(YesNoChartModel(yesNoModel), colorPattern)
        }
    }
}
