package com.example

import java.util.regex.Pattern

class JSInterpreter {
    private val variables = mutableMapOf<String, Any>()
    private val functions = mutableMapOf<String, Pair<List<String>, List<String>>>() // name -> Pair(args, lines)
    private val outputBuffer = StringBuilder()

    fun execute(code: String): String {
        variables.clear()
        functions.clear()
        outputBuffer.clear()

        // Normalize text lines, remove semicolons where safe or clean them
        val lines = code.split("\n")
        executeBlock(lines, 0, lines.size)

        return if (outputBuffer.isEmpty()) {
            "Script finished with no console output."
        } else {
            outputBuffer.toString()
        }
    }

    private fun executeBlock(lines: List<String>, startIdx: Int, endIdx: Int): Int {
        var i = startIdx
        while (i < endIdx) {
            val rawLine = lines[i]
            var trimmed = rawLine.trim()

            // Skip comments and empty lines
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                i++
                continue
            }

            // Clean trailing semicolon for easier parsing
            if (trimmed.endsWith(";")) {
                trimmed = trimmed.substring(0, trimmed.length - 1).trim()
            }

            // Handle console.log
            if (trimmed.startsWith("console.log(") && trimmed.endsWith(")")) {
                val argStr = trimmed.substring(12, trimmed.length - 1)
                val output = evaluatePrintArgument(argStr)
                outputBuffer.append(output).append("\n")
                i++
                continue
            }

            // Handle variable declaration (let / const / var)
            if (trimmed.startsWith("let ") || trimmed.startsWith("const ") || trimmed.startsWith("var ")) {
                val stmt = trimmed.substringAfter(" ")
                if (stmt.contains("=")) {
                    val parts = stmt.split("=", limit = 2)
                    val varName = parts[0].trim()
                    val varValueExpr = parts[1].trim()
                    if (isValidVariableName(varName)) {
                        variables[varName] = evaluateExpression(varValueExpr)
                    }
                }
                i++
                continue
            }

            // Handle standard variable assignment
            if (trimmed.contains("=") && !trimmed.startsWith("if") && !trimmed.startsWith("for") && !trimmed.startsWith("while")) {
                val parts = trimmed.split("=", limit = 2)
                val varName = parts[0].trim()
                val varValueExpr = parts[1].trim()
                if (variables.containsKey(varName) && isValidVariableName(varName)) {
                    variables[varName] = evaluateExpression(varValueExpr)
                }
                i++
                continue
            }

            // Handle Functions: function test(a, b) {
            if (trimmed.startsWith("function ")) {
                val defMatch = Pattern.compile("function\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*\\{?").matcher(trimmed)
                if (defMatch.matches()) {
                    val funcName = defMatch.group(1) ?: ""
                    val argsStr = defMatch.group(2) ?: ""
                    val args = argsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                    // Find matching brace block
                    val bodyLines = mutableListOf<String>()
                    var braceCount = 1
                    var j = i + 1
                    while (j < lines.size && braceCount > 0) {
                        val bodyLine = lines[j]
                        if (bodyLine.contains("{")) braceCount++
                        if (bodyLine.contains("}")) braceCount--
                        if (braceCount > 0) {
                            bodyLines.add(bodyLine)
                        }
                        j++
                    }
                    functions[funcName] = Pair(args, bodyLines)
                    i = j
                    continue
                }
            }

            // Handle simple for loop: for (let i = 0; i < 5; i++) {
            if (trimmed.startsWith("for ") && trimmed.contains("(")) {
                val insideParens = trimmed.substringAfter("(").substringBeforeLast(")")
                val loopParts = insideParens.split(";")
                if (loopParts.size >= 3) {
                    val initPart = loopParts[0].trim() // let i = 0
                    val condPart = loopParts[1].trim() // i < 5
                    val postPart = loopParts[2].trim() // i++

                    // Initialize variable
                    var varName = ""
                    var varVal = 0
                    if (initPart.startsWith("let ") || initPart.startsWith("var ")) {
                        val cleanInit = initPart.substringAfter(" ")
                        val initSplit = cleanInit.split("=")
                        varName = initSplit[0].trim()
                        varVal = initSplit[1].trim().toIntOrNull() ?: 0
                    }

                    // Gather loop body lines
                    val bodyLines = mutableListOf<String>()
                    var braceCount = 1
                    var j = i + 1
                    while (j < lines.size && braceCount > 0) {
                        val bodyLine = lines[j]
                        if (bodyLine.contains("{")) braceCount++
                        if (bodyLine.contains("}")) braceCount--
                        if (braceCount > 0) {
                            bodyLines.add(bodyLine)
                        }
                        j++
                    }

                    if (varName.isNotEmpty()) {
                        variables[varName] = varVal
                        while (evaluateCondition(condPart)) {
                            executeBlock(bodyLines, 0, bodyLines.size)
                            // Apply increment/decrement
                            if (postPart == "$varName++" || postPart == "++$varName" || postPart == "$varName += 1") {
                                val cur = variables[varName] as? Number ?: 0
                                variables[varName] = cur.toInt() + 1
                            } else if (postPart == "$varName--" || postPart == "--$varName" || postPart == "$varName -= 1") {
                                val cur = variables[varName] as? Number ?: 0
                                variables[varName] = cur.toInt() - 1
                            }
                        }
                    }
                    i = j
                    continue
                }
            }

            // Handle if condition: if (x > 5) {
            if (trimmed.startsWith("if ") && trimmed.contains("(")) {
                val condStr = trimmed.substringAfter("(").substringBeforeLast(")").trim()
                val conditionMet = evaluateCondition(condStr)

                // Gather if body
                val ifBodyLines = mutableListOf<String>()
                var braceCount = 1
                var j = i + 1
                while (j < lines.size && braceCount > 0) {
                    val bodyLine = lines[j]
                    if (bodyLine.contains("{")) braceCount++
                    if (bodyLine.contains("}")) braceCount--
                    if (braceCount > 0) {
                        ifBodyLines.add(bodyLine)
                    }
                    j++
                }

                // Gather else body if present
                val elseBodyLines = mutableListOf<String>()
                if (j < lines.size && lines[j].trim().contains("else")) {
                    var k = j
                    if (lines[j].trim().contains("{")) {
                        braceCount = 1
                        k++
                    } else {
                        // find next line with {
                        braceCount = 0
                        while (k < lines.size && !lines[k].contains("{")) {
                            k++
                        }
                        if (k < lines.size) {
                            braceCount = 1
                            k++
                        }
                    }
                    while (k < lines.size && braceCount > 0) {
                        val bodyLine = lines[k]
                        if (bodyLine.contains("{")) braceCount++
                        if (bodyLine.contains("}")) braceCount--
                        if (braceCount > 0) {
                            elseBodyLines.add(bodyLine)
                        }
                        k++
                    }
                    j = k
                }

                if (conditionMet) {
                    executeBlock(ifBodyLines, 0, ifBodyLines.size)
                } else {
                    executeBlock(elseBodyLines, 0, elseBodyLines.size)
                }
                i = j
                continue
            }

            // Handle function call: greet("hello")
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

    private fun isValidVariableName(name: String): Boolean {
        return Pattern.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*$", name)
    }

    private fun evaluatePrintArgument(arg: String): String {
        // Backtick string interpolation: `text ${var}`
        if (arg.startsWith("`") && arg.endsWith("`")) {
            val content = arg.substring(1, arg.length - 1)
            val matcher = Pattern.compile("\\$\\{([^}]+)\\}").matcher(content)
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

        return evaluateExpression(arg).toString()
    }

    private fun evaluateExpression(expr: String): Any {
        val trimmed = expr.trim()

        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") || trimmed.startsWith("'") && trimmed.endsWith("'")) {
            return trimmed.substring(1, trimmed.length - 1)
        }

        if (variables.containsKey(trimmed)) {
            return variables[trimmed]!!
        }

        trimmed.toIntOrNull()?.let { return it }
        trimmed.toDoubleOrNull()?.let { return it }
        trimmed.toBooleanStrictOrNull()?.let { return it }

        if (trimmed.contains("+")) {
            val parts = trimmed.split("+", limit = 2)
            val left = evaluateExpression(parts[0])
            val right = evaluateExpression(parts[1])
            if (left is String || right is String) {
                return left.toString() + right.toString()
            }
            if (left is Number && right is Number) {
                return left.toDouble() + right.toDouble()
            }
        }

        if (trimmed.contains("-")) {
            val parts = trimmed.split("-", limit = 2)
            val left = evaluateExpression(parts[0])
            val right = evaluateExpression(parts[1])
            if (left is Number && right is Number) {
                return left.toDouble() - right.toDouble()
            }
        }

        if (trimmed.contains("*")) {
            val parts = trimmed.split("*", limit = 2)
            val left = evaluateExpression(parts[0])
            val right = evaluateExpression(parts[1])
            if (left is Number && right is Number) {
                return left.toDouble() * right.toDouble()
            }
        }

        if (trimmed.contains("/")) {
            val parts = trimmed.split("/", limit = 2)
            val left = evaluateExpression(parts[0])
            val right = evaluateExpression(parts[1])
            if (left is Number && right is Number) {
                val denom = right.toDouble()
                if (denom != 0.0) return left.toDouble() / denom
            }
        }

        return trimmed
    }

    private fun evaluateCondition(condStr: String): Boolean {
        var op = ""
        val operators = listOf("===", "==", "!==", "!=", ">=", "<=", ">", "<")
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
            "===", "==" -> left.toString() == right.toString()
            "!==", "!=" -> left.toString() != right.toString()
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

        val backupVars = HashMap(variables)

        for (idx in paramNames.indices) {
            if (idx < args.size) {
                variables[paramNames[idx]] = args[idx]
            }
        }

        executeBlock(body, 0, body.size)

        val returnVal = variables["return"] ?: ""
        variables.clear()
        variables.putAll(backupVars)

        return returnVal
    }
}
