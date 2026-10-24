package com.example.billing.data

import com.example.billing.BillingDatabase
import com.example.billing.model.*
import com.example.billing.utils.nowIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class VendorRepository(database: BillingDatabase, private val bills: BillRepository) {
    private val q = database.vendorQueries
    private val json = Json { ignoreUnknownKeys = true }
    private val itemsSerializer = ListSerializer(PurchaseItem.serializer())

    // ==================== VENDOR ====================
    suspend fun listVendors(): List<VendorWithBalance> = withContext(Dispatchers.Default) {
        q.selectAllVendors { id, name, address, p1, p2, desc, created ->
            Vendor(id, name, address, p1, p2, desc, created)
        }.executeAsList().map { v ->
            val bal = q.selectVendorBalance(v.id, v.id).executeAsOne()
            VendorWithBalance(v, bal)
        }
    }

    suspend fun getVendor(id: Long): Vendor? = withContext(Dispatchers.Default) {
        q.selectVendorById(id) { vid, name, address, p1, p2, desc, created ->
            Vendor(vid, name, address, p1, p2, desc, created)
        }.executeAsOneOrNull()
    }

    suspend fun addVendor(name: String, address: String, phone1: String, phone2: String, desc: String): Long =
        withContext(Dispatchers.Default) {
            q.transactionWithResult {
                q.insertVendor(name.trim(), address.trim(), phone1.trim(), phone2.trim(), desc.trim(), nowIso())
                q.lastInsertedId().executeAsOne()
            }
        }

    suspend fun updateVendor(v: Vendor) = withContext(Dispatchers.Default) {
        q.updateVendor(v.name.trim(), v.address.trim(), v.phone1.trim(), v.phone2.trim(), v.accountDescription.trim(), v.id)
    }

    suspend fun deleteVendor(id: Long) = withContext(Dispatchers.Default) {
        q.transaction {
            q.selectPaymentsForVendor(id) { pid, vid, amt, disc, date, note -> pid }.executeAsList().forEach { q.deleteVendorPayment(it) }
            q.selectPurchasesForVendor(id) { pid, vid, date, data, total, disc, paid, notes -> pid }.executeAsList().forEach { q.deletePurchase(it) }
            q.deleteVendor(id)
        }
    }

    // ==================== VENDOR PAYMENTS ====================
    suspend fun paymentsFor(vendorId: Long): List<VendorPayment> = withContext(Dispatchers.Default) {
        q.selectPaymentsForVendor(vendorId) { id, vid, amt, disc, date, note ->
            VendorPayment(id, vid, amt, disc, date, note)
        }.executeAsList()
    }

    suspend fun addPayment(vendorId: Long, amount: Double, discount: Double, date: String, note: String) =
        withContext(Dispatchers.Default) { q.insertVendorPayment(vendorId, amount, discount, date, note) }

    suspend fun updatePayment(p: VendorPayment) = withContext(Dispatchers.Default) {
        q.updateVendorPayment(p.amount, p.discount, p.date, p.note, p.id)
    }
    suspend fun deletePayment(id: Long) = withContext(Dispatchers.Default) { q.deleteVendorPayment(id) }

    // ==================== CATEGORIES ====================
    suspend fun listCategories(): List<Category> = withContext(Dispatchers.Default) {
        q.selectAllCategories { id, name -> Category(id, name) }.executeAsList()
    }

    suspend fun addCategory(name: String): String? = withContext(Dispatchers.Default) {
        val clean = name.trim()
        if (clean.isBlank()) return@withContext "Name is required."
        if (q.selectCategoryByName(clean) { _, _ -> 1L }.executeAsOneOrNull() != null)
            return@withContext "Category '$clean' already exists."
        q.insertCategory(clean)
        null
    }

    suspend fun updateCategory(id: Long, name: String): String? = withContext(Dispatchers.Default) {
        val clean = name.trim()
        if (clean.isBlank()) return@withContext "Name is required."
        val exists = q.selectCategoryByName(clean) { cid, _ -> cid }.executeAsOneOrNull()
        if (exists != null && exists != id) return@withContext "Category '$clean' already exists."
        q.updateCategory(clean, id)
        null
    }
    suspend fun deleteCategory(id: Long) = withContext(Dispatchers.Default) { q.deleteCategory(id) }

    // ==================== SUBCATEGORIES ====================
    suspend fun listSubcategories(categoryId: Long? = null): List<Subcategory> = withContext(Dispatchers.Default) {
        val rows = if (categoryId == null) q.selectAllSubcategories { id, cid, name -> Triple(id, cid, name) }.executeAsList()
        else q.selectSubcategoriesForCategory(categoryId) { id, cid, name -> Triple(id, cid, name) }.executeAsList()
        rows.map { Subcategory(it.first, it.second, it.third) }
    }

    suspend fun addSubcategory(categoryId: Long, name: String): String? = withContext(Dispatchers.Default) {
        val clean = name.trim()
        if (clean.isBlank()) return@withContext "Name is required."
        if (q.selectSubcategoryByName(categoryId, clean) { _, _, _ -> 1L }.executeAsOneOrNull() != null)
            return@withContext "Subcategory '$clean' already exists in this category."
        q.insertSubcategory(categoryId, clean)
        null
    }

    suspend fun updateSubcategory(id: Long, name: String): String? = withContext(Dispatchers.Default) {
        val clean = name.trim()
        if (clean.isBlank()) return@withContext "Name is required."
        q.updateSubcategory(clean, id)
        null
    }
    suspend fun deleteSubcategory(id: Long) = withContext(Dispatchers.Default) { q.deleteSubcategory(id) }

    // ==================== PRODUCTS ====================
    suspend fun listProducts(): List<Product> = withContext(Dispatchers.Default) {
        val cats = listCategories().associateBy { it.id }
        val subs = listSubcategories().associateBy { it.id }
        q.selectAllProducts { id, name, cid, sid, price, sp, stock, unit ->
            Product(
                id = id, name = name,
                categoryId = cid, categoryName = cats[cid]?.name ?: "",
                subcategoryId = sid, subcategoryName = subs[sid]?.name ?: "",
                price = price, salePrice = sp, openingStock = stock, unit = unit,
            )
        }.executeAsList()
    }

    suspend fun getProduct(id: Long): Product? = listProducts().firstOrNull { it.id == id }

    suspend fun addProduct(p: Product): String? = withContext(Dispatchers.Default) {
        val existing = listProducts().any {
            it.name.equals(p.name.trim(), true) && it.categoryId == p.categoryId && it.subcategoryId == p.subcategoryId
        }
        if (existing) return@withContext "Product '${p.name}' already exists in this Category/Subcategory."

        q.transactionWithResult {
            q.insertProduct(p.name.trim(), p.categoryId, p.subcategoryId, p.price, p.salePrice, p.openingStock, p.unit.trim())
            q.lastInsertedId().executeAsOne()
        }
        null
    }

    suspend fun updateProduct(p: Product): String? = withContext(Dispatchers.Default) {
        val existing = listProducts().any {
            it.name.equals(p.name.trim(), true) && it.categoryId == p.categoryId && it.subcategoryId == p.subcategoryId && it.id != p.id
        }
        if (existing) return@withContext "Another product '${p.name}' already exists in this Category/Subcategory."

        q.updateProduct(p.name.trim(), p.categoryId, p.subcategoryId, p.price, p.salePrice, p.openingStock, p.unit.trim(), p.id)
        null
    }
    suspend fun deleteProduct(id: Long) = withContext(Dispatchers.Default) { q.deleteProduct(id) }

    // ==================== PURCHASES ====================
    suspend fun listPurchases(vendorId: Long? = null): List<Purchase> = withContext(Dispatchers.Default) {
        val vendors = listVendors().associate { it.vendor.id to it.vendor.name }
        val mapper: (Long, Long, String, String, Double, Double, Double, String) -> Purchase =
            { id, vid, date, data, total, discount, paid, notes ->
                Purchase(
                    id = id, vendorId = vid, vendorName = vendors[vid] ?: "", date = date,
                    items = runCatching { json.decodeFromString(itemsSerializer, data) }.getOrDefault(emptyList()),
                    total = total, discount = discount, paid = paid, notes = notes,
                )
            }
        if (vendorId == null) q.selectAllPurchases(mapper).executeAsList()
        else q.selectPurchasesForVendor(vendorId, mapper).executeAsList()
    }

    suspend fun getPurchase(id: Long): Purchase? = listPurchases().firstOrNull { it.id == id }

    suspend fun savePurchase(p: Purchase): Long = withContext(Dispatchers.Default) {
        val data = json.encodeToString(itemsSerializer, p.items)
        val itemsTotal = p.items.sumOf { it.total }
        q.transactionWithResult {
            if (p.id == 0L) {
                q.insertPurchase(p.vendorId, p.date, data, itemsTotal, p.discount, p.paid, p.notes)
                q.lastInsertedId().executeAsOne()
            } else {
                q.updatePurchase(p.vendorId, p.date, data, itemsTotal, p.discount, p.paid, p.notes, p.id)
                p.id
            }
        }
    }

    suspend fun deletePurchase(id: Long) = withContext(Dispatchers.Default) { q.deletePurchase(id) }

    suspend fun getProductStock(productId: Long): Double = withContext(Dispatchers.Default) {
        val product = getProduct(productId) ?: return@withContext 0.0
        var stock = product.openingStock
        listPurchases().forEach { purchase -> purchase.items.forEach { item -> if (item.productId == productId) stock += item.qtyValue } }
        return@withContext stock
    }

    suspend fun getLiveStock(productId: Long): Double = withContext(Dispatchers.Default) {
        val product = getProduct(productId) ?: return@withContext 0.0
        var stock = product.openingStock
        listPurchases().forEach { purchase -> purchase.items.forEach { item -> if (item.productId == productId) stock += item.qtyValue } }
        bills.getAllBills().forEach { bill -> bill.rooms.forEach { room -> room.items.forEach { item -> if (item.productId == productId) stock -= item.qtyValue } } }
        stock
    }

    suspend fun listProductsWithStock(): List<Product> = withContext(Dispatchers.Default) {
        val prods = listProducts()
        val purchases = listPurchases()
        val allBills = bills.getAllBills()
        val stockMap = mutableMapOf<Long, Double>()
        prods.forEach { stockMap[it.id] = it.openingStock }
        purchases.forEach { pu -> pu.items.forEach { item -> item.productId?.let { pid -> stockMap[pid] = (stockMap[pid] ?: 0.0) + item.qtyValue } } }
        allBills.forEach { b -> b.rooms.forEach { r -> r.items.forEach { item -> item.productId?.let { pid -> stockMap[pid] = (stockMap[pid] ?: 0.0) - item.qtyValue } } } }
        prods.map { it.copy(liveStock = stockMap[it.id] ?: it.openingStock) }
    }

    suspend fun isProductUsedInAnyBill(productId: Long): Boolean = withContext(Dispatchers.Default) {
        bills.getAllBills().any { bill -> bill.rooms.any { room -> room.items.any { it.productId == productId } } }
    }

    // ==================== CUSTOMER ACCOUNTS ====================
    suspend fun listCustomerAccounts(): List<CustomerAccount> = withContext(Dispatchers.Default) {
        q.selectAllSalesmen { id, name, p1, p2, addr, acc -> CustomerAccount(id, name, p1, p2, addr, acc) }.executeAsList()
    }

    suspend fun addCustomerAccount(name: String, phone1: String, phone2: String, address: String, accountNo: String): Long = withContext(Dispatchers.Default) {
        q.transactionWithResult {
            q.insertSalesman(name.trim(), phone1.trim(), phone2.trim(), address.trim(), accountNo.trim())
            q.lastInsertedId().executeAsOne()
        }
    }

    suspend fun updateCustomerAccount(account: CustomerAccount) = withContext(Dispatchers.Default) {
        q.updateSalesman(account.name.trim(), account.phone1.trim(), account.phone2.trim(), account.address.trim(), account.accountNo.trim(), account.id)
    }

    suspend fun deleteCustomerAccount(id: Long) = withContext(Dispatchers.Default) { q.deleteSalesman(id) }

    suspend fun isCustomerAccountUsedInBills(id: Long): Boolean = withContext(Dispatchers.Default) {
        bills.getAllBills().any { it.salesmanId == id }
    }
}