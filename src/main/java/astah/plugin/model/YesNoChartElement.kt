package astah.plugin.model

sealed class YesNoChartElement(val statement: String) {
    class YourChoice(statement: String, val description: String): YesNoChartElement(statement)
    class Question(statement: String,
                   val yesOption: OptionModel, val noOption: OptionModel): YesNoChartElement(statement)
}

class OptionModel(val guard: String, val selection: YesNoChartElement)