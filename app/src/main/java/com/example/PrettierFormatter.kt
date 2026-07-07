package com.example

import org.json.JSONArray
import org.json.JSONObject

class PrettierFormatter {
    fun format(code: String, fileName: String): String {
        val extension = fileName.substringAfterLast(".").lowercase()
        return try {
            when (extension) {
                "py" -> formatPython(code)
                "html", "htm" -> formatHtml(code)
                "js", "jsx", "ts", "tsx" -> formatJs(code)
                "json" -> formatJson(code)
                "css" -> formatCss(code)
                else -> formatGeneral(code)
            }
        } catch (e: Exception) {
            code // Fallback to raw code on error
        }
    }

    private fun formatPython(code: String): String {
        val lines = code.split("\n")
        val formattedLines = mutableListOf<String>()
        var consecutiveEmptyLines = 0

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                consecutiveEmptyLines++
                if (consecutiveEmptyLines <= 1) {
                    formattedLines.add("")
                }
                continue
            }
            consecutiveEmptyLines = 0

            // Keep indentation of original line
            val indent = rawLine.takeWhile { it == ' ' || it == '\t' }
            
            // Format spacing around operators where safe
            var lineFormatted = trimmed
                .replace(Regex("\\s*=\\s*"), " = ")
                .replace(Regex("\\s*\\+\\s*"), " + ")
                .replace(Regex("\\s*-\\s*"), " - ")
                .replace(Regex("\\s*\\*\\s*"), " * ")
                .replace(Regex("\\s*/\\s*"), " / ")
                .replace(Regex(",\\s*"), ", ")
                .replace(Regex("\\s+"), " ")

            // Clean spaces around parens
            lineFormatted = lineFormatted
                .replace("( ", "(")
                .replace(" )", ")")
                .replace("def  ", "def ")
                .replace("class  ", "class ")

            formattedLines.add(indent + lineFormatted)
        }
        return formattedLines.joinToString("\n")
    }

    private fun formatHtml(code: String): String {
        val lines = code.split("\n")
        val formattedLines = mutableListOf<String>()
        var indentLevel = 0

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                formattedLines.add("")
                continue
            }

            // Adjust indent level based on tag closing/opening
            val isClosing = trimmed.startsWith("</")
            val isOpening = trimmed.startsWith("<") && !trimmed.startsWith("</") && !trimmed.endsWith("/>") && !trimmed.contains("<!")

            if (isClosing && indentLevel > 0) {
                indentLevel--
            }

            val spaces = "    ".repeat(indentLevel)
            formattedLines.add(spaces + trimmed)

            if (isOpening) {
                indentLevel++
            }
        }
        return formattedLines.joinToString("\n")
    }

    private fun formatJs(code: String): String {
        val lines = code.split("\n")
        val formattedLines = mutableListOf<String>()
        var indentLevel = 0

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                formattedLines.add("")
                continue
            }

            // Decrease indent before printing if this line ends with closing brace
            val isClosing = trimmed.startsWith("}") || trimmed.startsWith("]")
            if (isClosing && indentLevel > 0) {
                indentLevel--
            }

            val spaces = "    ".repeat(indentLevel)
            
            // Standardize spaces around common tokens
            var formatted = trimmed
                .replace(Regex("\\s*=\\s*"), " = ")
                .replace(Regex("\\s*\\+\\s*"), " + ")
                .replace(Regex("\\s*-\\s*"), " - ")
                .replace(Regex(",\\s*"), ", ")
                .replace(Regex("\\s*\\{\\s*"), " {")

            formattedLines.add(spaces + formatted)

            // Increase indent after printing if this line opens brace
            val isOpening = trimmed.endsWith("{") || trimmed.endsWith("[")
            if (isOpening) {
                indentLevel++
            }
        }
        return formattedLines.joinToString("\n")
    }

    private fun formatJson(code: String): String {
        val trimmed = code.trim()
        return if (trimmed.startsWith("{")) {
            JSONObject(trimmed).toString(4)
        } else if (trimmed.startsWith("[")) {
            JSONArray(trimmed).toString(4)
        } else {
            code
        }
    }

    private fun formatCss(code: String): String {
        val lines = code.split("\n")
        val formattedLines = mutableListOf<String>()
        var indentLevel = 0

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) {
                formattedLines.add("")
                continue
            }

            if (trimmed.startsWith("}") && indentLevel > 0) {
                indentLevel--
            }

            val spaces = "    ".repeat(indentLevel)
            formattedLines.add(spaces + trimmed)

            if (trimmed.endsWith("{")) {
                indentLevel++
            }
        }
        return formattedLines.joinToString("\n")
    }

    private fun formatGeneral(code: String): String {
        // Just trim spaces per line
        return code.split("\n").joinToString("\n") { it.trimEnd() }
    }
}
