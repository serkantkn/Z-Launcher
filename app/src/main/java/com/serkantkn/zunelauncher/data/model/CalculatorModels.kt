package com.serkantkn.zunelauncher.data.model

import androidx.annotation.StringRes
import com.serkantkn.zunelauncher.R
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

/** One evaluated line of the calculator, kept in the "geçmiş" pivot. */
data class CalcHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    /** Rendered expression, e.g. "12 × (3 + 4)". */
    val expression: String,
    /** Exact result as a plain decimal string. */
    val result: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("expression", expression); put("result", result); put("timestamp", timestamp)
    }

    companion object {
        fun fromJson(o: JSONObject) = CalcHistoryEntry(
            id = o.getString("id"),
            expression = o.optString("expression", ""),
            result = o.optString("result", ""),
            timestamp = o.optLong("timestamp", 0L)
        )
    }
}

/** A unit of a [UnitCategory]: values convert through the base unit by [toBase]/[fromBase]. */
data class UnitDef(
    val id: String,
    val symbol: String,
    @StringRes val nameRes: Int,
    val toBase: (BigDecimal) -> BigDecimal,
    val fromBase: (BigDecimal) -> BigDecimal
) {
    companion object {
        /** Linear unit: base = value × [factor]. */
        fun linear(id: String, symbol: String, @StringRes nameRes: Int, factor: String) = UnitDef(
            id, symbol, nameRes,
            toBase = { it.multiply(BigDecimal(factor)) },
            fromBase = { it.divide(BigDecimal(factor), java.math.MathContext(20)) }
        )
    }
}

data class UnitCategory(val id: String, @StringRes val nameRes: Int, val units: List<UnitDef>)

/** Converter categories of the "dönüştürücü" pivot. Base units: m, kg, °C, m/s, m², L, byte, second. */
object UnitCategories {
    private val C20 = java.math.MathContext(20)

    val LENGTH = UnitCategory("length", R.string.calc_cat_length, listOf(
        UnitDef.linear("mm", "mm", R.string.unit_mm, "0.001"),
        UnitDef.linear("cm", "cm", R.string.unit_cm, "0.01"),
        UnitDef.linear("m", "m", R.string.unit_m, "1"),
        UnitDef.linear("km", "km", R.string.unit_km, "1000"),
        UnitDef.linear("in", "in", R.string.unit_inch, "0.0254"),
        UnitDef.linear("ft", "ft", R.string.unit_foot, "0.3048"),
        UnitDef.linear("yd", "yd", R.string.unit_yard, "0.9144"),
        UnitDef.linear("mi", "mi", R.string.unit_mile, "1609.344")
    ))
    val MASS = UnitCategory("mass", R.string.calc_cat_mass, listOf(
        UnitDef.linear("mg", "mg", R.string.unit_mg, "0.000001"),
        UnitDef.linear("g", "g", R.string.unit_g, "0.001"),
        UnitDef.linear("kg", "kg", R.string.unit_kg, "1"),
        UnitDef.linear("t", "t", R.string.unit_ton, "1000"),
        UnitDef.linear("oz", "oz", R.string.unit_ounce, "0.028349523125"),
        UnitDef.linear("lb", "lb", R.string.unit_pound, "0.45359237")
    ))
    val TEMPERATURE = UnitCategory("temperature", R.string.calc_cat_temperature, listOf(
        UnitDef("c", "°C", R.string.unit_celsius, { it }, { it }),
        UnitDef("f", "°F", R.string.unit_fahrenheit,
            toBase = { it.subtract(BigDecimal(32)).multiply(BigDecimal(5)).divide(BigDecimal(9), C20) },
            fromBase = { it.multiply(BigDecimal(9)).divide(BigDecimal(5), C20).add(BigDecimal(32)) }),
        UnitDef("k", "K", R.string.unit_kelvin,
            toBase = { it.subtract(BigDecimal("273.15")) },
            fromBase = { it.add(BigDecimal("273.15")) })
    ))
    val SPEED = UnitCategory("speed", R.string.calc_cat_speed, listOf(
        UnitDef.linear("mps", "m/s", R.string.unit_mps, "1"),
        UnitDef.linear("kmh", "km/h", R.string.unit_kmh, "0.27777777777777778"),
        UnitDef.linear("mph", "mph", R.string.unit_mph, "0.44704"),
        UnitDef.linear("kn", "kn", R.string.unit_knot, "0.51444444444444444")
    ))
    val AREA = UnitCategory("area", R.string.calc_cat_area, listOf(
        UnitDef.linear("cm2", "cm²", R.string.unit_cm2, "0.0001"),
        UnitDef.linear("m2", "m²", R.string.unit_m2, "1"),
        UnitDef.linear("ha", "ha", R.string.unit_hectare, "10000"),
        UnitDef.linear("km2", "km²", R.string.unit_km2, "1000000"),
        UnitDef.linear("ft2", "ft²", R.string.unit_ft2, "0.09290304"),
        UnitDef.linear("ac", "ac", R.string.unit_acre, "4046.8564224")
    ))
    val VOLUME = UnitCategory("volume", R.string.calc_cat_volume, listOf(
        UnitDef.linear("ml", "mL", R.string.unit_ml, "0.001"),
        UnitDef.linear("l", "L", R.string.unit_l, "1"),
        UnitDef.linear("m3", "m³", R.string.unit_m3, "1000"),
        UnitDef.linear("cup", "cup", R.string.unit_cup, "0.2365882365"),
        UnitDef.linear("gal", "gal", R.string.unit_gallon, "3.785411784")
    ))
    val DATA = UnitCategory("data", R.string.calc_cat_data, listOf(
        UnitDef.linear("b", "B", R.string.unit_byte, "1"),
        UnitDef.linear("kb", "KB", R.string.unit_kb, "1024"),
        UnitDef.linear("mb", "MB", R.string.unit_mb, "1048576"),
        UnitDef.linear("gb", "GB", R.string.unit_gb, "1073741824"),
        UnitDef.linear("tb", "TB", R.string.unit_tb, "1099511627776")
    ))
    val TIME = UnitCategory("time", R.string.calc_cat_time, listOf(
        UnitDef.linear("s", "s", R.string.unit_second, "1"),
        UnitDef.linear("min", "min", R.string.unit_minute, "60"),
        UnitDef.linear("h", "h", R.string.unit_hour, "3600"),
        UnitDef.linear("d", "d", R.string.unit_day, "86400"),
        UnitDef.linear("wk", "wk", R.string.unit_week, "604800")
    ))

    val all: List<UnitCategory> = listOf(LENGTH, MASS, TEMPERATURE, SPEED, AREA, VOLUME, DATA, TIME)

    fun convert(value: BigDecimal, from: UnitDef, to: UnitDef): BigDecimal = to.fromBase(from.toBase(value))
}
