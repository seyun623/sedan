package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsSubway
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.model.FinanceCategories

/**
 * Helper to map financial transaction expense/income/transfer categories
 * to their designated Material 3 icons and colors.
 */
object CategoryIconHelper {

  fun getIconForCategory(category: String): ImageVector {
    return when (category) {
      "식비" -> Icons.Default.Restaurant
      "카페/간식" -> Icons.Default.LocalCafe
      "쇼핑" -> Icons.Default.ShoppingBag
      "교통" -> Icons.Default.DirectionsSubway
      "주거/통신" -> Icons.Default.Home
      "금융/보험" -> Icons.Default.AccountBalance
      "의료/건강" -> Icons.Default.LocalHospital
      "문화/여가" -> Icons.Default.SportsEsports
      "생활/마트" -> Icons.Default.LocalGroceryStore
      "기타지출" -> Icons.Default.Receipt
      "급여" -> Icons.Default.Payments
      "상여금" -> Icons.Default.MonetizationOn
      "금융소득/배당" -> Icons.Default.TrendingUp
      "용돈" -> Icons.Default.CardGiftcard
      "부수입/중고거래" -> Icons.Default.Storefront
      "기타수입" -> Icons.Default.Savings
      "계좌이체" -> Icons.Default.SwapHoriz
      "저축/투자이체" -> Icons.Default.AccountBalanceWallet
      "카드대금납부" -> Icons.Default.CreditCard
      else -> {
        when {
          category.contains("식") || category.contains("밥") || category.contains("음식") || category.contains("외식") -> Icons.Default.Restaurant
          category.contains("카페") || category.contains("커피") || category.contains("간식") || category.contains("디저트") -> Icons.Default.LocalCafe
          category.contains("쇼핑") || category.contains("패션") || category.contains("의류") || category.contains("잡화") -> Icons.Default.ShoppingBag
          category.contains("교통") || category.contains("지하철") || category.contains("버스") || category.contains("택시") || category.contains("주유") -> Icons.Default.DirectionsSubway
          category.contains("주거") || category.contains("통신") || category.contains("관리비") || category.contains("월세") -> Icons.Default.Home
          category.contains("병원") || category.contains("의료") || category.contains("약국") || category.contains("건강") -> Icons.Default.LocalHospital
          category.contains("문화") || category.contains("여가") || category.contains("영화") || category.contains("게임") -> Icons.Default.SportsEsports
          category.contains("마트") || category.contains("편의점") || category.contains("생활") -> Icons.Default.LocalGroceryStore
          category.contains("급여") || category.contains("월급") || category.contains("수입") -> Icons.Default.Payments
          category.contains("이체") || category.contains("송금") -> Icons.Default.SwapHoriz
          else -> Icons.Default.ReceiptLong
        }
      }
    }
  }

  fun getColorForCategory(category: String): Long {
    return FinanceCategories.getColorForCategory(category)
  }
}
