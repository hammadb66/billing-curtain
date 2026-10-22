package com.example.billing.data

import com.example.billing.model.Product
import com.example.billing.model.Purchase
import com.example.billing.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class BackupRepository(
    private val bills: BillRepository,
    private val vendors: VendorRepository
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun exportAll(): String = withContext(Dispatchers.Default) {
        val billsList = bills.getAllBills().map {
            BillBackup(
                id = it.id,
                customer = it.customer,
                phone = it.phone,
                address = it.address,
                phone2 = it.phone2,
                accountNo = it.accountNo,
                date = it.date,
                rooms = it.rooms,
                discount = it.discount,
                extraCharges = it.extraCharges,
                paidAmount = it.paidAmount,
                invoiceLabel = it.invoiceLabel,
                showProduct = it.showProduct,
                showCategory = it.showCategory,
                showSubcategory = it.showSubcategory,
                salesmanId = it.salesmanId,
            )
        }

        val vendorsList = vendors.listVendors().map { it.vendor }
        val allPayments = vendorsList.flatMap { v ->
            vendors.paymentsFor(v.id).map { p ->
                VendorPaymentBackup(p.id, p.vendorId, p.amount, p.discount, p.date, p.note)
            }
        }

        val allCategories = vendors.listCategories().map { CategoryBackup(it.id, it.name) }
        val allSubcategories = vendors.listSubcategories().map { SubcategoryBackup(it.id, it.categoryId, it.name) }
        val allProducts = vendors.listProducts().map {
            ProductBackup(it.id, it.name, it.categoryId, it.subcategoryId, it.price, it.salePrice, it.openingStock, it.unit)
        }
        val allPurchases = vendors.listPurchases().map {
            PurchaseBackup(it.id, it.vendorId, it.date, it.items, it.total, it.discount, it.paid, it.notes)
        }

        val backup = FullBackup(
            bills = billsList,
            vendors = vendorsList.map { VendorBackup(it.id, it.name, it.address, it.phone1, it.phone2, it.accountDescription, it.createdAt) },
            vendorPayments = allPayments,
            categories = allCategories,
            subcategories = allSubcategories,
            products = allProducts,
            purchases = allPurchases
        )
        json.encodeToString(FullBackup.serializer(), backup)
    }

    suspend fun importAll(jsonString: String): String? = withContext(Dispatchers.Default) {
        try {
            val backup = json.decodeFromString(FullBackup.serializer(), jsonString)

            // 1. Delete existing data (Clean Slate)
            bills.deleteAll()
            val existingVendors = vendors.listVendors()
            existingVendors.forEach { vendors.deleteVendor(it.vendor.id) }
            val existingProducts = vendors.listProducts()
            existingProducts.forEach { vendors.deleteProduct(it.id) }
            val existingSubs = vendors.listSubcategories()
            existingSubs.forEach { vendors.deleteSubcategory(it.id) }
            val existingCats = vendors.listCategories()
            existingCats.forEach { vendors.deleteCategory(it.id) }

            // 2. Import Categories
            val catMap = mutableMapOf<Long, Long>()
            for (c in backup.categories) {
                vendors.addCategory(c.name)
                val newId = vendors.listCategories().firstOrNull { it.name == c.name }?.id
                if (newId != null) catMap[c.id] = newId
            }

            // 3. Import Subcategories
            val subMap = mutableMapOf<Long, Long>()
            for (s in backup.subcategories) {
                val newCatId = catMap[s.categoryId] ?: continue
                vendors.addSubcategory(newCatId, s.name)
                val newId = vendors.listSubcategories(newCatId).firstOrNull { it.name == s.name }?.id
                if (newId != null) subMap[s.id] = newId
            }

            // 4. Import Products
            val prodMap = mutableMapOf<Long, Long>()
            for (p in backup.products) {
                val newCatId = p.categoryId?.let { catMap[it] }
                val newSubId = p.subcategoryId?.let { subMap[it] }
                val newProduct = Product(0L, p.name, newCatId, "", newSubId, "", p.price, p.salePrice, p.openingStock, p.unit)
                val err = vendors.addProduct(newProduct)
                if (err == null) {
                    val newId = vendors.listProducts().firstOrNull { it.name == p.name && it.categoryId == newCatId && it.subcategoryId == newSubId }?.id
                    if (newId != null) prodMap[p.id] = newId
                }
            }

            // 5. Import Vendors
            val vendMap = mutableMapOf<Long, Long>()
            for (v in backup.vendors) {
                val newId = vendors.addVendor(v.name, v.address, v.phone1, v.phone2, v.accountDescription)
                vendMap[v.id] = newId
            }

            // 6. Import Vendor Payments
            for (p in backup.vendorPayments) {
                val newVendId = vendMap[p.vendorId] ?: continue
                vendors.addPayment(newVendId, p.amount, p.discount, p.date, p.note)
            }

            // 7. Import Purchases
            for (p in backup.purchases) {
                val newVendId = vendMap[p.vendorId] ?: continue
                val newItems = p.items.map { item ->
                    val newProdId = item.productId?.let { prodMap[it] }
                    item.copy(productId = newProdId)
                }
                val newPurchase = Purchase(0L, newVendId, "", p.date, newItems, p.total, p.discount, p.paid, p.notes)
                vendors.savePurchase(newPurchase)
            }

            // 8. Import Bills
            val billsJson = json.encodeToString(ListSerializer(BillBackup.serializer()), backup.bills)
            bills.importFromJson(billsJson)

            null // Success
        } catch (e: Exception) {
            e.message ?: "Import failed"
        }
    }
}