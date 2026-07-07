package com.example

import java.util.regex.Pattern

class PythonInterpreter {
    private val variables = mutableMapOf<String, Any>()
    private val functions = mutableMapOf<String, Pair<List<String>, List<String>>>() // funcName -> Pair(argsList, bodyLines)
    private val outputBuffer = StringBuilder()

    fun execute(code: String): String {
        variables.clear()
        functions.clear()
        outputBuffer.clear()
        
        val lines = code.split("\n")
        executeBlock(lines, 0, lines.size, 0)
        
        return if (outputBuffer.isEmpty()) {
            "Script finished with no output logs."
        } else {
            outputBuffer.toString()
        }
    }

    private fun executeBlock(lines: List<String>, startIdx: Int, endIdx: Int, indentLevel: Int): Int {
        var i = startIdx
        while (i < endIdx) {
            val rawLine = lines[i]
            val trimmed = rawLine.trim()
            
            // Skip comments and empty lines
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                i++
                continue
            }

            // Verify indent level
            val currentIndent = getIndentLevel(rawLine)
            if (currentIndent < indentLevel) {
                // Exit block if indentation decreases
                return i
            }

            // Handle Import (Mocked standard libraries)
            if (trimmed.startsWith("import ")) {
                i++
                continue
            }

            // Handle Function Definitions (def name(args):)
            if (trimmed.startsWith("def ")) {
                val defMatch = Pattern.compile("def\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*:").matcher(trimmed)
                if (defMatch.matches()) {
                    val funcName = defMatch.group(1) ?: ""
                    val argsStr = defMatch.group(2) ?: ""
                    val args = argsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    
                    // Gather body lines (with higher indent)
                    val bodyLines = mutableListOf<String>()
                    var j = i + 1
                    while (j < lines.size) {
                        val bodyLine = lines[j]
                        if (bodyLine.trim().isEmpty()) {
                            j++
                            continue
                        }
                        if (getIndentLevel(bodyLine) <= currentIndent) {
                            break
                        }
                        bodyLines.add(bodyLine)
                        j++
                    }
                    functions[funcName] = Pair(args, bodyLines)
                    i = j
                    continue
                }
            }

            // Handle For Loops: for x in range(...):
            if (trimmed.startsWith("for ")) {
                val forMatch = Pattern.compile("for\\s+(\\w+)\\s+in\\s+range\\s*\\(([^)]+)\\)\\s*:").matcher(trimmed)
                if (forMatch.matches()) {
                    val loopVar = forMatch.group(1) ?: ""
                    val rangeArgsStr = forMatch.group(2) ?: ""
                    val rangeArgs = rangeArgsStr.split(",").map { evaluateExpression(it.trim()).toString().toDoubleOrNull()?.toInt() ?: 0 }
                    
                    val range = when (rangeArgs.size) {
                        1 -> 0 until rangeArgs[0]
                        2 -> rangeArgs[0] until rangeArgs[1]
                        3 -> rangeArgs[0] until rangeArgs[1] step rangeArgs[2]
                        else -> 0 until 0
                    }

                    // Gather loop body lines
                    val bodyLines = mutableListOf<String>()
                    var j = i + 1
                    while (j < lines.size) {
                        val bodyLine = lines[j]
                        if (bodyLine.trim().isEmpty()) {
                            j++
                            continue
                        }
                        if (getIndentLevel(bodyLine) <= currentIndent) {
                            break
                        }
                        bodyLines.add(bodyLine)
                        j++
                    }

                    // Run loop
                    for (valItem in range) {
                        variables[loopVar] = valItem
                        executeBlock(bodyLines, 0, bodyLines.size, getIndentLevel(bodyLines.firstOrNull() ?: ""))
                    }
                    i = j
                    continue
                }
            }

            // Handle If Conditions: if <cond>:
            if (trimmed.startsWith("if ")) {
                val condStr = trimmed.removePrefix("if ").substringBeforeLast(":").trim()
                val conditionMet = evaluateCondition(condStr)

                // Gather if-body lines
                val ifBodyLines = mutableListOf<String>()
                var j = i + 1
                while (j < lines.size) {
                    val bodyLine = lines[j]
                    if (bodyLine.trim().isEmpty()) {
                        j++
                        continue
                    }
                    if (getIndentLevel(bodyLine) <= currentIndent) {
                        break
                    }
                    ifBodyLines.add(bodyLine)
                    j++
                }

                // Gather else body if present
                val elseBodyLines = mutableListOf<String>()
                if (j < lines.size && lines[j].trim().startsWith("else:")) {
                    var k = j + 1
                    while (k < lines.size) {
                        val bodyLine = lines[k]
                        if (bodyLine.trim().isEmpty()) {
                            k++
                            continue
                        }
                        if (getIndentLevel(bodyLine) <= currentIndent) {
                            break
                        }
                        elseBodyLines.add(bodyLine)
                        k++
                    }
                    j = k
                }

                if (conditionMet) {
                    if (ifBodyLines.isNotEmpty()) {
                        executeBlock(ifBodyLines, 0, ifBodyLines.size, getIndentLevel(ifBodyLines.first()))
                    }
                } else {
                    if (elseBodyLines.isNotEmpty()) {
                        executeBlock(elseBodyLines, 0, elseBodyLines.size, getIndentLevel(elseBodyLines.first()))
                    }
                }
                i = j
                continue
            }

            // Handle Print Command
            if (trimmed.startsWith("print(") && trimmed.endsWith(")")) {
                val argStr = trimmed.substring(6, trimmed.length - 1)
                val output = evaluatePrintArgument(argStr)
                outputBuffer.append(output).append("\n")
                i++
                continue
            }

            // Handle Variable Assignment
            if (trimmed.contains("=")) {
                val parts = trimmed.split("=", limit = 2)
                val varName = parts[0].trim()
                val varValueExpr = parts[1].trim()
                if (isValidVariableName(varName)) {
                    val evaluated = evaluateExpression(varValueExpr)
                    variables[varName] = evaluated
                }
                i++
                continue
            }

            // Check if it's a raw function call: greet("hello")
            val funcCallMatch = Pattern.compile("(\\w+)\\s*\\(([^)]*)\\)").matcher(trimmed)
            if (funcCallMatch.matches()) {
                val funcName = funcCallMatch.group(1) ?: ""
                val argsStr = funcCallMatch.group(2) ?: ""
                val argsEvaluated = argsStr.split(",").map { evaluateExpression(it.trim()) }
                invokeFunction(funcName, argsEvaluated)
                i++
                continue
            }

            i++
        }
        return i
    }

    private fun getIndentLevel(line: String): Int {
        var count = 0
        for (char in line) {
            if (char == ' ') count++
            else if (char == '\t') count += 4
            else break
        }
        return count
    }

    private fun isValidVariableName(name: String): Boolean {
        return Pattern.matches("^[a-zA-Z_][a-zA-Z0-9_]*$", name)
    }

    private fun evaluatePrintArgument(arg: String): String {
        // Match f"text {var}"
        if (arg.startsWith("f\"") && arg.endsWith("\"") || arg.startsWith("f'") && arg.endsWith("'")) {
            val content = arg.substring(2, arg.length - 1)
            val matcher = Pattern.compile("\\{([^}]+)\\}").matcher(content)
            val sb = StringBuffer()
            while (matcher.find()) {
                val expr = matcher.group(1) ?: ""
                val evaluated = evaluateExpression(expr).toString()
                matcher.appendReplacement(sb, evaluated)
            }
            matcher.appendTail(sb)
            return sb.toString()
        }

        // Standard string
        if (arg.startsWith("\"") && arg.endsWith("\"") || arg.startsWith("'") && arg.endsWith("'")) {
            return arg.substring(1, arg.length - 1)
        }

        // Variables or expressions
        return evaluateExpression(arg).toString()
    }

    private fun evaluateExpression(expr: String): Any {
        val trimmed = expr.trim()
        
        // Is String
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") || trimmed.startsWith("'") && trimmed.endsWith("'")) {
            return trimmed.substring(1, trimmed.length - 1)
        }

        // Is Math function
        if (trimmed.startsWith("hex(") && trimmed.endsWith(")")) {
            val arg = trimmed.substring(4, trimmed.length - 1)
            val intVal = evaluateExpression(arg).toString().toDoubleOrNull()?.toInt() ?: 0
            return "0x" + Integer.toHexString(intVal).uppercase()
        }

        if (trimmed.startsWith("math.sqrt(") && trimmed.endsWith(")")) {
            val arg = trimmed.substring(10, trimmed.length - 1)
            val dVal = evaluateExpression(arg).toString().toDoubleOrNull() ?: 0.0
            return kotlin.math.sqrt(dVal)
        }

        // Check if expression is an existing variable
        if (variables.containsKey(trimmed)) {
            return variables[trimmed]!!
        }

        // Handle numeric values
        trimmed.toIntOrNull()?.let { return it }
        trimmed.toDoubleOrNull()?.let { return it }
        trimmed.toBooleanStrictOrNull()?.let { return it }

        // Math operation parsing: support standard addition/multiplication/concatenation
        if (trimmed.contains("+")) {
            val parts = splitByOutsideQuotes(trimmed, '+')
            if (parts.size > 1) {
                val left = evaluateExpression(parts[0])
                val right = evaluateExpression(parts[1])
                if (left is String || right is String) {
                    return left.toString() + right.toString()
                }
                if (left is Number && right is Number) {
                    return left.toDouble() + right.toDouble()
                }
            }
        }

        if (trimmed.contains("-")) {
            val parts = splitByOutsideQuotes(trimmed, '-')
            if (parts.size > 1) {
                val left = evaluateExpression(parts[0])
                val right = evaluateExpression(parts[1])
                if (left is Number && right is Number) {
                    return left.toDouble() - right.toDouble()
                }
            }
        }

        if (trimmed.contains("*")) {
            val parts = splitByOutsideQuotes(trimmed, '*')
            if (parts.size > 1) {
                val left = evaluateExpression(parts[0])
                val right = evaluateExpression(parts[1])
                if (left is Number && right is Number) {
                    return left.toDouble() * right.toDouble()
                }
            }
        }

        if (trimmed.contains("/")) {
            val parts = splitByOutsideQuotes(trimmed, '/')
            if (parts.size > 1) {
                val left = evaluateExpression(parts[0])
                val right = evaluateExpression(parts[1])
                if (left is Number && right is Number) {
                    val denom = right.toDouble()
                    if (denom != 0.0) return left.toDouble() / denom
                }
            }
        }

        // Check if custom function call inside expression
        val fCall = Pattern.compile("(\\w+)\\s*\\(([^)]*)\\)").matcher(trimmed)
        if (fCall.matches()) {
            val funcName = fCall.group(1) ?: ""
            val argsStr = fCall.group(2) ?: ""
            val argsEvaluated = argsStr.split(",").map { evaluateExpression(it.trim()) }
            return invokeFunction(funcName, argsEvaluated) ?: ""
        }

        return trimmed
    }

    private fun evaluateCondition(condStr: String): Boolean {
        var op = ""
        val operators = listOf("==", "!=", ">=", "<=", ">", "<")
        for (o in operators) {
            if (condStr.contains(o)) {
                op = o
                break
            }
        }

        if (op.isEmpty()) {
            val eval = evaluateExpression(condStr)
            if (eval is Boolean) return eval
            if (eval is Number) return eval.toDouble() != 0.0
            return eval.toString().isNotEmpty()
        }

        val parts = condStr.split(op, limit = 2)
        val left = evaluateExpression(parts[0].trim())
        val right = evaluateExpression(parts[1].trim())

        return when (op) {
            "==" -> left.toString() == right.toString()
            "!=" -> left.toString() != right.toString()
            ">" -> (left as? Number)?.toDouble() ?: 0.0 > (right as? Number)?.toDouble() ?: 0.0
            "<" -> (left as? Number)?.toDouble() ?: 0.0 < (right as? Number)?.toDouble() ?: 0.0
            ">=" -> (left as? Number)?.toDouble() ?: 0.0 >= (right as? Number)?.toDouble() ?: 0.0
            "<=" -> (left as? Number)?.toDouble() ?: 0.0 <= (right as? Number)?.toDouble() ?: 0.0
            else -> false
        }
    }

    private fun invokeFunction(funcName: String, args: List<Any>): Any? {
        val func = functions[funcName] ?: return null
        val paramNames = func.first
        val body = func.second

        // Backup current variables context
        val backupVars = HashMap(variables)

        // Set args as variables
        for (idx in paramNames.indices) {
            if (idx < args.size) {
                variables[paramNames[idx]] = args[idx]
            }
        }

        // Run body lines
        executeBlock(body, 0, body.size, if (body.isNotEmpty()) getIndentLevel(body.first()) else 0)

        // Recover variables context
        // In this basic version, functions print to output logs or return value can be extracted 
        // We restore original variables state except return values if we need to
        val returnVal = variables["return_val"] ?: ""
        variables.clear()
        variables.putAll(backupVars)

        return returnVal
    }

    private fun splitByOutsideQuotes(text: String, char: Char): List<String> {
        val parts = mutableListOf<String>()
        var inQuotes = false
        var quoteChar = ' '
        var currentPart = StringBuilder()

        for (c in text) {
            if ((c == '"' || c == '\'') && (quoteChar == ' ' || quoteChar == c)) {
                inQuotes = !inQuotes
                quoteChar = if (inQuotes) c else ' '
                currentPart.append(c)
            } else if (c == char && !inQuotes) {
                parts.add(currentPart.toString())
                currentPart = StringBuilder()
            } else {
                currentPart.append(c)
            }
        }
        parts.add(currentPart.toString())
        return parts
    }
}
