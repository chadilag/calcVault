package com.example.calculator

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object CalculatorEngine {

    fun evaluate(rawExpression: String): Result<String> {
        val cleaned = rawExpression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("π", Math.PI.toString())
            .replace("e", Math.E.toString())
            .replace("√(", "sqrt(")
            .replace("√", "sqrt")
            .trim()

        if (cleaned.isEmpty()) return Result.success("0")

        return try {
            val parser = ExpressionParser(cleaned)
            val value = parser.parse()
            if (value.isNaN() || value.isInfinite()) {
                Result.failure(ArithmeticException("Math Error"))
            } else {
                Result.success(formatNumber(value))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun formatNumber(value: Double): String {
        if (value == 0.0) return "0"
        val absVal = kotlin.math.abs(value)
        if (absVal >= 1e12 || (absVal > 0 && absVal < 1e-7)) {
            return String.format(java.util.Locale.US, "%.6E", value)
        }
        val bd = BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
        return bd.toPlainString()
    }

    private class ExpressionParser(private val input: String) {
        private var pos = -1
        private var ch = 0

        init {
            nextChar()
        }

        private fun nextChar() {
            pos++
            ch = if (pos < input.length) input[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            while (ch == ' '.code) nextChar()
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            val x = parseExpression()
            if (pos < input.length) throw IllegalArgumentException("Unexpected char: ${input[pos]}")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                when {
                    eat('+'.code) -> x += parseTerm()
                    eat('-'.code) -> x -= parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                when {
                    eat('*'.code) -> x *= parseFactor()
                    eat('/'.code) -> {
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("Division by zero")
                        x /= divisor
                    }
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()

            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                eat(')'.code) // Auto-close trailing parenthesis if omitted
            } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                x = input.substring(startPos, pos).toDouble()
            } else if (ch in 'a'.code..'z'.code) {
                while (ch in 'a'.code..'z'.code) nextChar()
                val func = input.substring(startPos, pos)
                x = parseFactor()
                x = when (func) {
                    "sqrt" -> {
                        if (x < 0) throw ArithmeticException("Negative sqrt")
                        sqrt(x)
                    }
                    "sin" -> sin(Math.toRadians(x))
                    "cos" -> cos(Math.toRadians(x))
                    "tan" -> tan(Math.toRadians(x))
                    "ln" -> ln(x)
                    "log" -> log10(x)
                    else -> throw IllegalArgumentException("Unknown function: $func")
                }
            } else {
                throw IllegalArgumentException("Unexpected character")
            }

            if (eat('^'.code)) {
                x = x.pow(parseFactor())
            }
            while (eat('%'.code)) {
                x /= 100.0
            }
            while (eat('!'.code)) {
                x = factorial(x)
            }
            return x
        }

        private fun factorial(n: Double): Double {
            val intN = n.toInt()
            if (intN < 0 || intN > 170) return Double.NaN
            var result = 1.0
            for (i in 2..intN) result *= i
            return result
        }
    }
}
