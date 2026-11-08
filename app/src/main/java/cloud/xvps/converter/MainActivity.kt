package cloud.xvps.converter

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import cloud.xvps.converter.databinding.ActivityMainBinding
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    private var snapshot: RatesSnapshot = RatesSnapshot("—", emptyMap(), true)

    private val symbols = DecimalFormatSymbols(Locale("ru")).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }
    private val fmtMoney = DecimalFormat("#,##0.00", symbols)
    private val fmtSmall = DecimalFormat("#,##0.0000", symbols)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        val labels = RatesRepository.CODES.map { "$it — ${RatesRepository.NAMES[it]}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spFrom.adapter = adapter
        b.spTo.adapter = adapter
        b.spFrom.setSelection(RatesRepository.CODES.indexOf("USD"))
        b.spTo.setSelection(RatesRepository.CODES.indexOf("RUB"))

        val onPick = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = recalc()
            override fun onNothingSelected(p: AdapterView<*>?) = Unit
        }
        b.spFrom.onItemSelectedListener = onPick
        b.spTo.onItemSelectedListener = onPick

        b.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, c: Int, d: Int) = Unit
            override fun afterTextChanged(s: Editable?) = recalc()
        })

        b.btnSwap.setOnClickListener {
            val from = b.spFrom.selectedItemPosition
            b.spFrom.setSelection(b.spTo.selectedItemPosition)
            b.spTo.setSelection(from)
            recalc()
        }

        b.btnRefresh.setOnClickListener { load(force = true) }

        snapshot = RatesRepository.cached(this)
        render()
        load(force = false)
    }

    private fun load(force: Boolean) {
        b.progress.visibility = View.VISIBLE
        b.btnRefresh.isEnabled = false
        io.execute {
            val result = runCatching { RatesRepository.fetch(this) }
            ui.post {
                b.progress.visibility = View.GONE
                b.btnRefresh.isEnabled = true
                result.onSuccess {
                    snapshot = it
                    render()
                }.onFailure {
                    if (force) {
                        b.tvStatus.text = getString(R.string.status_offline_error)
                    }
                }
            }
        }
    }

    private fun render() {
        b.tvStatus.text = if (snapshot.offline) {
            getString(R.string.status_cached, snapshot.date)
        } else {
            getString(R.string.status_cbr, snapshot.date)
        }
        recalc()
    }

    private fun recalc() {
        val from = RatesRepository.CODES[b.spFrom.selectedItemPosition]
        val to = RatesRepository.CODES[b.spTo.selectedItemPosition]
        val rates = snapshot.rates.ifEmpty { RatesRepository.cached(this).rates }

        val rateFrom = rates[from]
        val rateTo = rates[to]
        if (rateFrom == null || rateTo == null || rateTo == 0.0) {
            b.tvResult.text = "—"
            b.tvRate.text = ""
            return
        }

        val raw = b.etAmount.text?.toString()?.replace(',', '.')?.replace(" ", "").orEmpty()
        val amount = raw.toDoubleOrNull()
        if (amount == null) {
            b.tvResult.text = "—"
            b.tvRate.text = pairLine(from, to, rateFrom / rateTo)
            return
        }

        val converted = amount * rateFrom / rateTo
        val f = if (converted != 0.0 && kotlin.math.abs(converted) < 1) fmtSmall else fmtMoney
        b.tvResult.text = "${f.format(converted)} ${RatesRepository.SYMBOLS[to] ?: to}"
        b.tvRate.text = pairLine(from, to, rateFrom / rateTo)
    }

    private fun pairLine(from: String, to: String, rate: Double): String {
        val f = if (rate < 1) fmtSmall else fmtMoney
        return "1 $from = ${f.format(rate)} $to"
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }
}
