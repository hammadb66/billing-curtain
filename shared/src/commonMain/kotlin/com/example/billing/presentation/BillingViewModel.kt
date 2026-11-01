package com.example.billing.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billing.auth.AppSettings
import com.example.billing.data.BillRepository
import com.example.billing.model.BillingFormState
import com.example.billing.model.MasterItem
import com.example.billing.model.RoomData
import com.example.billing.utils.nowIso
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BillingViewModel(
    private val repository: BillRepository,
    private val settings: AppSettings
) : ViewModel() {

    val form = BillingFormState()
    private val _masterItems = MutableStateFlow<List<MasterItem>>(emptyList())
    val masterItems: StateFlow<List<MasterItem>> = _masterItems.asStateFlow()
    private val _previousBalance = MutableStateFlow(0.0)
    val previousBalance: StateFlow<Double> = _previousBalance.asStateFlow()

    init { viewModelScope.launch { _masterItems.value = settings.getMasterItems() } }

    fun setDisplayOption(option: String) { applyDisplayOption(option) }

    private fun applyDisplayOption(option: String) {
        form.showProduct = option == "product"
        form.showCategory = option == "category"
        form.showSubcategory = option == "subcategory"
        form.showGroup = option == "group"       // NAYA
        form.showSubgroup = option == "subgroup" // NAYA
    }

    fun loadCustomerInfo(phone: String) {
        if (phone.length >= 7) {
            viewModelScope.launch {
                val info = runCatching { repository.getCustomerInfo(phone) }.getOrNull()
                _previousBalance.value = info?.previousBalance ?: 0.0
                if (info != null && form.customer.isBlank()) form.customer = info.name
            }
        } else _previousBalance.value = 0.0
    }

    fun saveShouldPrint(value: Boolean) {
        viewModelScope.launch {
            settings.put("should_print", value.toString())
        }
    }

    fun saveBill(
        userName: String,
        showProduct: Boolean, showCategory: Boolean, showSubcategory: Boolean,showGroup:Boolean,showSubGroup:Boolean,
        onSuccess: (String, String, String, String, List<RoomData>, Double, Double, Double) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val iso = nowIso()
                val prefix = com.example.billing.utils.Branding.licensedTo.ifBlank { "inv" }.take(6)
                val invoiceNo = repository.insertBill(
                    form.customer.trim(), form.phone, form.address.trim(), form.phone2.trim(), form.accountNo.trim(),
                    form.toRoomDataList(), iso, form.discountValue, form.extraValue, form.receivedValue,
                    form.selectedCustomerAccount?.id,
                    prefix, showProduct, showCategory, showSubcategory, showGroup, showSubGroup
                )
                onSuccess(invoiceNo, form.customer.trim(), form.phone, iso, form.toRoomDataList(), form.discountValue, form.extraValue, form.receivedValue)
            } catch (e: Exception) { onError(e.message ?: "Error") }
        }
    }
}