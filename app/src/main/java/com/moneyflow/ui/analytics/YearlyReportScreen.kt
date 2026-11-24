package com.moneyflow.ui.analytics

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.moneyflow.data.model.FinanceCategories
import com.moneyflow.data.repository.LedgerRepository
import com.moneyflow.domain.AppClock
import com.moneyflow.domain.MonthlyStatsCalculator
import com.moneyflow.domain.report.YearlyReport
import com.moneyflow.domain.report.YearlyReports
import com.moneyflow.ui.common.MonthSwitcher
import com.moneyflow.ui.settings.SettingsItem
import com.moneyflow.ui.settings.SettingsScaffold
import com.moneyflow.ui.settings.SettingsSection
import com.moneyflow.ui.theme.Gray100
import com.moneyflow.ui.theme.Gray600
import com.moneyflow.ui.theme.Gray900
import com.moneyflow.ui.theme.TossBlue
import com.moneyflow.util.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class YearlyReportViewModel @Inject constructor(
  @ApplicationContext private val context: Context,
  ledger: LedgerRepository,
  private val stats: MonthlyStatsCalculator,
  private val clock: AppClock
) : ViewModel() {
  private val year = MutableStateFlow(clock.today().year)

  val report: StateFlow<YearlyReport?> = combine(ledger.transactions, year) { all, y ->
    YearlyReports.build(all, y, clock.zone, stats)
  }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

  private val _share = Channel<File>(Channel.BUFFERED)
  val share = _share.receiveAsFlow()

  fun move(delta: Int) { year.value = (year.value + delta).coerceAtMost(clock.today().year) }
  val isThisYear: Boolean get() = year.value == clock.today().year

  fun shareImage() = viewModelScope.launch {
    val r = report.value ?: return@launch
    val file = withContext(Dispatchers.IO) {
      val bmp = YearlyReportImage.draw(r)
      val dir = File(context.cacheDir, "exports").apply { mkdirs() }
      File(dir, "moneyflow-${r.year}.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; bmp.recycle() }
    }
    _share.send(file)
  }
}

/** Analytics → 연간 결산. */
@Composable
fun YearlyReportScreen(onBack: () -> Unit, viewModel: YearlyReportViewModel = hiltViewModel()) {
  val r by viewModel.report.collectAsStateWithLifecycle()
  val context = LocalContext.current
  LaunchedEffect(viewModel) {
    viewModel.share.collect { file ->
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      context.startActivity(Intent.createChooser(send, "연간 결산 공유"))
    }
  }
  SettingsScaffold(title = "연간 결산", onBack = onBack) {
    val report = r ?: return@SettingsScaffold
    MonthSwitcher(label = "${report.year}년", onPrevious = { viewModel.move(-1) }, onNext = { viewModel.move(1) }, modifier = Modifier.padding(horizontal = 8.dp))
    if (report.isEmpty) {
      Text("${report.year}년에 기록된 거래가 없어요.", color = Gray600, modifier = Modifier.padding(16.dp))
      return@SettingsScaffold
    }
    SettingsSection(null) {
      Column(Modifier.padding(16.dp).testTag("yearly_summary")) {
        Text("${report.year}년에 쓴 돈", style = MaterialTheme.typography.bodyMedium, color = Gray600)
        Text(Formatters.formatCurrency(report.expense), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Gray900)
        Text("번 돈 ${Formatters.formatCurrency(report.income)} · " +
          (report.savingRate?.let { "남긴 돈 ${Formatters.formatCurrency(report.saved)} (${it}%)" } ?: "남긴 돈 ${Formatters.formatCurrency(report.saved)}"),
          style = MaterialTheme.typography.bodySmall, color = Gray600)
        Text("한 달 평균 지출 ${Formatters.formatCurrency(report.monthlyAverage)}", style = MaterialTheme.typography.bodySmall, color = Gray600)
        report.previousExpense?.let { prev ->
          val diff = report.expense - prev
          Text(if (diff >= 0) "${report.year - 1}년보다 ${Formatters.formatCurrency(diff)} 더 썼어요" else "${report.year - 1}년보다 ${Formatters.formatCurrency(-diff)} 덜 썼어요",
            style = MaterialTheme.typography.bodySmall, color = Gray600)
        }
      }
    }
    SettingsSection("월별 지출") {
      val max = report.months.maxOf { it.expense }.coerceAtLeast(1)
      Row(Modifier.fillMaxWidth().height(140.dp).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        report.months.forEach { m ->
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
              Modifier.width(14.dp).height((80 * m.expense / max).toInt().coerceAtLeast(2).dp).clip(RoundedCornerShape(4.dp))
                .background(if (m == report.topMonth) TossBlue else TossBlue.copy(alpha = 0.35f))
            )
            Text("${m.month}", style = MaterialTheme.typography.labelSmall, color = Gray600)
          }
        }
      }
      report.topMonth?.let { Text("가장 많이 쓴 달: ${it.month}월 ${Formatters.formatCurrency(it.expense)}", style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)) }
    }
    SettingsSection("카테고리") {
      report.categories.take(6).forEach { c ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.width(10.dp).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(c.colorHex)))
          Text(c.category, modifier = Modifier.padding(start = 8.dp).weight(1f), color = Gray900)
          Text("${Formatters.formatCurrency(c.amount)} · ${(c.percentage * 100).toInt()}%", color = Gray600, style = MaterialTheme.typography.bodySmall)
        }
      }
    }
    SettingsSection("가장 많이 쓴 곳") {
      report.places.forEachIndexed { i, p ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
          Text("${i + 1}. ${p.name}", modifier = Modifier.weight(1f), color = Gray900)
          Text("${p.count}번 · ${Formatters.formatCurrency(p.amount)}", color = Gray600, style = MaterialTheme.typography.bodySmall)
        }
      }
      report.biggest?.let { b ->
        Text("가장 큰 한 번: ${b.memo.ifBlank { b.category }} ${Formatters.formatCurrency(b.amount)} (${Formatters.formatFullDate(b.timestamp)})",
          style = MaterialTheme.typography.bodySmall, color = Gray600, modifier = Modifier.padding(16.dp))
      }
    }
    SettingsSection(null) {
      SettingsItem(
        title = "이미지로 공유",
        subtitle = "결산을 그림 한 장으로 만들어 가족에게 보내요 (거래 상세는 빠져요)",
        icon = Icons.Filled.Share,
        showChevron = false,
        onClick = viewModel::shareImage,
        modifier = Modifier.testTag("yearly_share")
      )
    }
  }
}

/** Draws the yearly report as one shareable picture (no transaction details). */
object YearlyReportImage {
  fun draw(r: YearlyReport): Bitmap {
    val w = 1080; val h = 1500
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(0xFFF2F4F6.toInt())
    val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    c.drawRoundRect(RectF(48f, 48f, w - 48f, h - 48f), 48f, 48f, card)
    fun text(size: Float, color: Long, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textSize = size; this.color = color.toInt(); typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
    val gray = 0xFF6B7684; val dark = 0xFF191F28; val blue = 0xFF0064FF
    var y = 150f
    c.drawText("${r.year}년 우리집 가계부 결산", 100f, y, text(52f, dark, true)); y += 110
    c.drawText("쓴 돈", 100f, y, text(36f, gray)); y += 80
    c.drawText(Formatters.formatCurrency(r.expense), 100f, y, text(84f, blue, true)); y += 70
    c.drawText("번 돈 ${Formatters.formatCurrency(r.income)}" + (r.savingRate?.let { " · 저축률 ${it}%" } ?: ""), 100f, y, text(36f, gray)); y += 56
    c.drawText("한 달 평균 ${Formatters.formatCurrency(r.monthlyAverage)}", 100f, y, text(36f, gray)); y += 90

    // Monthly bars.
    val max = r.months.maxOf { it.expense }.coerceAtLeast(1)
    val barW = (w - 200f) / 12
    val base = y + 260
    r.months.forEach { m ->
      val x = 100f + (m.month - 1) * barW
      val bh = 240f * m.expense / max
      val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (if (m == r.topMonth) blue else 0x590064FF).toInt() }
      c.drawRoundRect(RectF(x + 12, base - bh.coerceAtLeast(4f), x + barW - 12, base), 10f, 10f, p)
      c.drawText("${m.month}", x + barW / 2 - (if (m.month >= 10) 16 else 9), base + 40, text(28f, gray))
    }
    y = base + 120

    c.drawText("많이 쓴 카테고리", 100f, y, text(40f, dark, true)); y += 64
    r.categories.take(4).forEach { cat ->
      val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FinanceCategories.getColorForCategory(cat.category).toInt() }
      c.drawCircle(116f, y - 12, 12f, dot)
      c.drawText(cat.category, 146f, y, text(36f, dark))
      val amount = "${Formatters.formatCurrency(cat.amount)} (${(cat.percentage * 100).toInt()}%)"
      val tp = text(36f, gray)
      c.drawText(amount, w - 100f - tp.measureText(amount), y, tp)
      y += 58
    }
    y += 30
    c.drawText("자주 간 곳", 100f, y, text(40f, dark, true)); y += 64
    r.places.take(3).forEachIndexed { i, p ->
      c.drawText("${i + 1}. ${p.name.take(16)}", 100f, y, text(36f, dark))
      val t = "${p.count}번"
      val tp = text(36f, gray)
      c.drawText(t, w - 100f - tp.measureText(t), y, tp)
      y += 58
    }
    val foot = text(28f, gray)
    c.drawText("머니플로우", w - 100f - foot.measureText("머니플로우"), h - 90f, foot)
    return bmp
  }
}
