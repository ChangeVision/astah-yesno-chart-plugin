package astah.plugin

import com.change_vision.jude.api.inf.AstahAPI
import com.change_vision.jude.api.inf.project.ProjectEvent
import com.change_vision.jude.api.inf.project.ProjectEventListener
import com.change_vision.jude.api.inf.ui.IPluginExtraTabView
import com.change_vision.jude.api.inf.ui.ISelectionListener
import java.awt.Component
import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JComboBox
import javax.swing.JPanel

const val pptxFileName = "generatedYesNoChart.pptx"

enum class ColorPattern {
    Colorful {
        override fun toString(): String = "Color"
    },
    Monochrome {
        override fun toString(): String = "Monochrome"
    }
}

class YesNoChartView: JPanel(), IPluginExtraTabView, ProjectEventListener, ActionListener {
    val buttonGenerate = ButtonGenerate()
    val comboColorSelection = JComboBox(arrayOf(ColorPattern.Colorful.toString(), ColorPattern.Monochrome.toString()))

    init {
        val panelButtons = JPanel()
        panelButtons.layout = GridLayout(1, 2)
        panelButtons.add(buttonGenerate)
        panelButtons.add(comboColorSelection)
        buttonGenerate.addActionListener(this)
        add(panelButtons)
    }

    override fun actionPerformed(e: ActionEvent) {
        when (e.source) {
            buttonGenerate -> {
                val currentDiagram =
                    AstahAPI.getAstahAPI().projectAccessor.viewManager.diagramViewManager.currentDiagram
                val selectedColor =
                    if ((comboColorSelection.selectedItem as String) == ColorPattern.Colorful.toString())
                        ColorPattern.Colorful else ColorPattern.Monochrome
                buttonGenerate.push(currentDiagram, selectedColor)
            }
        }
    }

    override fun getTitle(): String = "Yes/No Chart"
    override fun getDescription(): String = "Yes/No Chart View"
    override fun getComponent(): Component = this
    override fun addSelectionListener(p0: ISelectionListener?) = Unit
    override fun activated() = Unit
    override fun deactivated() = Unit
    override fun projectOpened(p0: ProjectEvent?) = Unit
    override fun projectClosed(p0: ProjectEvent?) = Unit
    override fun projectChanged(p0: ProjectEvent?) = Unit
}