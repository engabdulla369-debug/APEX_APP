package com.example.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object ScientificCalculatorEngine {

    sealed interface CalcResult {
        data class Value(val formatted: String, val raw: Double) : CalcResult
        data class Error(val message: String) : CalcResult
    }

    private val numberFormat = DecimalFormat("0.##########", DecimalFormatSymbols(Locale.US))

    fun evaluate(expression: String, useDegrees: Boolean = true, ansValue: Double = 0.0): CalcResult {
        val cleaned = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace("π", "pi")
            .replace("√", "sqrt")
            .replace("ANS", ansValue.toString(), ignoreCase = true)
            .trim()

        if (cleaned.isEmpty()) return CalcResult.Value("0", 0.0)

        return try {
            val value = Parser(cleaned, useDegrees).parse()
            if (value.isNaN() || value.isInfinite()) {
                CalcResult.Error("القيمة غير معرفة رياضياً")
            } else {
                val snapped = if (abs(value) < 1e-12) 0.0 else value
                CalcResult.Value(numberFormat.format(snapped), snapped)
            }
        } catch (e: ArithmeticException) {
            CalcResult.Error(e.message ?: "خطأ حسابي")
        } catch (_: Exception) {
            CalcResult.Error("صيغة المعادلة غير مكتملة")
        }
    }

    private class Parser(private val src: String, private val useDegrees: Boolean) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            pos++
            ch = if (pos < src.length) src[pos].code else -1
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
            nextChar()
            val x = parseExpression()
            while (ch == ' '.code) nextChar()
            if (pos < src.length) throw IllegalArgumentException("Unexpected char: ${ch.toChar()}")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                x = when {
                    eat('+'.code) -> x + parseTerm()
                    eat('-'.code) -> x - parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parsePower()
            while (true) {
                x = when {
                    eat('*'.code) -> x * parsePower()
                    eat('/'.code) -> {
                        val denom = parsePower()
                        if (denom == 0.0) throw ArithmeticException("لا يمكن القسمة على صفر")
                        x / denom
                    }
                    ch == '('.code || (ch in 'a'.code..'z'.code) -> x * parsePower()
                    else -> return x
                }
            }
        }

        private fun parsePower(): Double {
            var x = parseUnary()
            if (eat('^'.code)) {
                val expVal = parsePower()
                x = x.pow(expVal)
            }
            return x
        }

        private fun parseUnary(): Double {
            if (eat('+'.code)) return parseUnary()
            if (eat('-'.code)) return -parseUnary()
            return parsePostfix()
        }

        private fun parsePostfix(): Double {
            var x = parsePrimary()
            while (true) {
                x = when {
                    eat('!'.code) -> factorial(x)
                    eat('%'.code) -> x / 100.0
                    else -> return x
                }
            }
        }

        private fun parsePrimary(): Double {
            while (ch == ' '.code) nextChar()
            if (eat('('.code)) {
                val x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing )")
                return x
            }
            val start = pos
            return if (ch in '0'.code..'9'.code || ch == '.'.code) {
                while (ch in '0'.code..'9'.code || ch == '.'.code || ch == 'E'.code) {
                    if (ch == 'E'.code) {
                        nextChar()
                        if (ch == '+'.code || ch == '-'.code) nextChar()
                    } else {
                        nextChar()
                    }
                }
                src.substring(start, pos).toDouble()
            } else if (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code) {
                while (ch in 'a'.code..'z'.code || ch in 'A'.code..'Z'.code || ch in '0'.code..'9'.code) {
                    nextChar()
                }
                val ident = src.substring(start, pos).lowercase(Locale.US)
                when (ident) {
                    "pi" -> PI
                    "e" -> kotlin.math.E
                    else -> {
                        val arg = if (eat('('.code)) {
                            val inner = parseExpression()
                            if (!eat(')'.code)) throw IllegalArgumentException("Missing )")
                            inner
                        } else {
                            parsePower()
                        }
                        applyFunction(ident, arg)
                    }
                }
            } else {
                throw IllegalArgumentException("Unexpected token")
            }
        }

        private fun applyFunction(func: String, arg: Double): Double {
            val rad = if (useDegrees) Math.toRadians(arg) else arg
            return when (func) {
                "sin" -> sin(rad)
                "cos" -> cos(rad)
                "tan" -> tan(rad)
                "asin" -> if (useDegrees) Math.toDegrees(asin(arg)) else asin(arg)
                "acos" -> if (useDegrees) Math.toDegrees(acos(arg)) else acos(arg)
                "atan" -> if (useDegrees) Math.toDegrees(atan(arg)) else atan(arg)
                "sqrt" -> {
                    if (arg < 0) throw ArithmeticException("جذر سالب غير معرف في الأعداد الحقيقية")
                    sqrt(arg)
                }
                "ln" -> {
                    if (arg <= 0) throw ArithmeticException("اللوغاريتم للأعداد الموجبة فقط")
                    ln(arg)
                }
                "log" -> {
                    if (arg <= 0) throw ArithmeticException("اللوغاريتم للأعداد الموجبة فقط")
                    log10(arg)
                }
                "exp" -> exp(arg)
                "abs" -> abs(arg)
                "sq" -> arg * arg
                else -> throw IllegalArgumentException("Unknown function: $func")
            }
        }

        private fun factorial(x: Double): Double {
            if (x < 0 || x > 20 || x != x.toInt().toDouble()) {
                throw ArithmeticException("المضروب للأعداد الصحيحة من 0 إلى 20")
            }
            var res = 1.0
            for (i in 2..x.toInt()) res *= i
            return res
        }
    }
}
