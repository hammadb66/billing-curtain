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
        q.selectAllVendorsWithBalance { id, name, address, p1, p2, desc, created, balance ->
            VendorWithBalance(
                Vendor(id, name, address, p1, p2, desc, created),
                balance
            )
        }.executeAsList()
    }
    suspend fun getVendorBalance(vendorId: Long): Double = withContext(Dispatchers.Default) {
        q.selectVendorBalance(vendorId, vendorId).executeAsOne()
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

    suspend fun deleteVendor(id: Long): Boolean = withContext(Dispatchers.Default) {
        val hasPurchases = listPurchases(id).isNotEmpty()
        val hasPayments = paymentsFor(id).isNotEmpty()
        if (hasPurchases || hasPayments) return@withContext false // Delete nahi hone dein

        q.transaction {
            q.selectPaymentsForVendor(id) { pid, vid, amt, disc, date, note -> pid }.executeAsList().forEach { q.deleteVendorPayment(it) }
            q.selectPurchasesForVendor(id) { pid, vid, date, data, total, disc, paid, notes -> pid }.executeAsList().forEach { q.deletePurchase(it) }
            q.deleteVendor(id)
        }
        true
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

    // ==================== GROUP (5-level) ====================
    suspend fun listGroups(): List<Group> = withContext(Dispatchers.Default) {
        q.selectAllGroups { id, name -> Group(id, name) }.executeAsList()
    }
    suspend fun addGroup(name: String): Long = withContext(Dispatchers.Default) {
        q.transactionWithResult { q.insertGroup(name.trim()); q.lastInsertedId().executeAsOne() }
    }
    suspend fun updateGroup(id: Long, name: String) = withContext(Dispatchers.Default) { q.updateGroup(name.trim(), id) }
    suspend fun deleteGroup(id: Long) = withContext(Dispatchers.Default) { q.deleteGroup(id) }

    // ==================== SUBGROUP ====================
    suspend fun listSubgroups(groupId: Long? = null): List<Subgroup> = withContext(Dispatchers.Default) {
        val rows = if (groupId == null) q.selectAllSubgroups { id, gid, n -> Triple(id, gid, n) }.executeAsList()
        else q.selectSubgroupsForGroup(groupId) { id, gid, n -> Triple(id, gid, n) }.executeAsList()
        rows.map { Subgroup(it.first, it.second, it.third) }
    }

    suspend fun addSubgroup(groupId: Long?, name: String): Long = withContext(Dispatchers.Default) {
        q.transactionWithResult { q.insertSubgroup(groupId, name.trim()); q.lastInsertedId().executeAsOne() }
    }
    suspend fun updateSubgroup(id: Long, name: String) = withContext(Dispatchers.Default) { q.updateSubgroup(name.trim(), id) }
    suspend fun deleteSubgroup(id: Long) = withContext(Dispatchers.Default) { q.deleteSubgroup(id) }

    // ==================== CATEGORY (subgroup-linked) ====================
    suspend fun listCategories(subgroupId: Long? = null): List<Category> = withContext(Dispatchers.Default) {
        val rows = if (subgroupId == null)
            q.selectAllCategories { id, sgid, name -> Triple(id, sgid, name) }.executeAsList()
        else
            q.selectCategoriesForSubgroup(subgroupId) { id, sgid, name -> Triple(id, sgid, name) }.executeAsList()
        rows.map { Category(it.first, it.third, it.second) }
    }
    suspend fun addCategory(subgroupId: Long?, name: String): Long = withContext(Dispatchers.Default) {
        q.transactionWithResult { q.insertCategory(subgroupId, name.trim()); q.lastInsertedId().executeAsOne() }
    }
    suspend fun updateCategory(id: Long, name: String) = withContext(Dispatchers.Default) { q.updateCategory(name.trim(), id) }
    suspend fun deleteCategory(id: Long) = withContext(Dispatchers.Default) { q.deleteCategory(id) }

    // ==================== SUBCATEGORY ====================
    suspend fun listSubcategories(categoryId: Long? = null): List<Subcategory> = withContext(Dispatchers.Default) {
        val rows = if (categoryId == null) q.selectAllSubcategories { id, cid, name -> Triple(id, cid, name) }.executeAsList()
        else q.selectSubcategoriesForCategory(categoryId) { id, cid, name -> Triple(id, cid, name) }.executeAsList()
        rows.map { Subcategory(it.first, it.second, it.third) }
    }

    // VendorRepository.kt

    // NAYA FUNCTION: Specific Bill mein payment aur discount adjust karne ke liye
    suspend fun payBill(billId: Long, additionalPaid: Double, additionalDiscount: Double) {
        val bill = bills.getBill(billId) ?: return
        val newPaid = (bill.paidAmount ?: 0.0) + additionalPaid
        val newDiscount = bill.discount + additionalDiscount

        // Bill update karein
        bills.updateBill(
            bill.id, bill.customer, bill.phone, bill.address, bill.phone2, bill.accountNo,
            bill.rooms, newDiscount, bill.extraCharges, newPaid, bill.salesmanId,
            bill.showProduct, bill.showCategory, bill.showSubcategory, bill.showGroup, bill.showSubgroup
        )

    }
    suspend fun addSubcategory(categoryId: Long?, name: String): String? = withContext(Dispatchers.Default) {
        //                                                    ↑ Long? (pehle Long)
        val clean = name.trim()
        if (clean.isBlank()) return@withContext "Name is required."
        // Kotlin-level duplicate check (null parent ke liye DB UNIQUE kaam nahi karta)
        val dup = listSubcategories().any { it.categoryId == categoryId && it.name.equals(clean, true) }
        if (dup) return@withContext "Subcategory '$clean' already exists."
        q.insertSubcategory(categoryId, clean)
        null
    }
    suspend fun updateSubcategory(id: Long, name: String) = withContext(Dispatchers.Default) { q.updateSubcategory(name.trim(), id) }
    suspend fun deleteSubcategory(id: Long) = withContext(Dispatchers.Default) { q.deleteSubcategory(id) }

    // ==================== PRODUCTS (5-level + discount) ====================
    suspend fun listProducts(): List<Product> = withContext(Dispatchers.Default) {
        val groups = listGroups().associateBy { it.id }
        val subgroups = listSubgroups().associateBy { it.id }
        val cats = listCategories().associateBy { it.id }
        val subs = listSubcategories().associateBy { it.id }
        q.selectAllProducts { id, name, gid, sgid, cid, sid, price, sp, disc, stock, unit ->
            Product(
                id = id, name = name,
                groupId = gid, groupName = groups[gid]?.name ?: "",
                subgroupId = sgid, subgroupName = subgroups[sgid]?.name ?: "",
                categoryId = cid, categoryName = cats[cid]?.name ?: "",
                subcategoryId = sid, subcategoryName = subs[sid]?.name ?: "",
                price = price, salePrice = sp, discountPct = disc,
                openingStock = stock, unit = unit,
            )
        }.executeAsList()
    }

    suspend fun getProduct(id: Long): Product? = listProducts().firstOrNull { it.id == id }

    suspend fun addProduct(p: Product): String? = withContext(Dispatchers.Default) {
        val existing = listProducts().any {
            it.name.equals(p.name.trim(), true) && it.categoryId == p.categoryId && it.subcategoryId == p.subcategoryId
        }
        if (existing) return@withContext "Product '${p.name}' already exists."
        q.transactionWithResult {
            q.insertProduct(p.name.trim(), p.groupId, p.subgroupId, p.categoryId, p.subcategoryId,
                p.price, p.salePrice, p.discountPct, p.openingStock, p.unit.trim())
            q.lastInsertedId().executeAsOne()
        }
        null
    }

    suspend fun updateProduct(p: Product): String? = withContext(Dispatchers.Default) {
        val existing = listProducts().any {
            it.name.equals(p.name.trim(), true) && it.categoryId == p.categoryId && it.subcategoryId == p.subcategoryId && it.id != p.id
        }
        if (existing) return@withContext "Another product '${p.name}' already exists."
        q.updateProduct(p.name.trim(), p.groupId, p.subgroupId, p.categoryId, p.subcategoryId,
            p.price, p.salePrice, p.discountPct, p.openingStock, p.unit.trim(), p.id)
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
        listPurchases().forEach { p -> p.items.forEach { if (it.productId == productId) stock += it.qtyValue } }
        stock
    }

    suspend fun getLiveStock(productId: Long): Double = withContext(Dispatchers.Default) {
        val product = getProduct(productId) ?: return@withContext 0.0
        var stock = product.openingStock
        listPurchases().forEach { p -> p.items.forEach { if (it.productId == productId) stock += it.qtyValue } }
        bills.getAllBills().forEach { b -> b.rooms.forEach { r -> r.items.forEach { if (it.productId == productId) stock -= it.qtyValue } } }
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

    // ==================== CUSTOMERS ====================
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
    suspend fun deleteCustomerAccount(id: Long): Boolean = withContext(Dispatchers.Default) {
        if (isCustomerAccountUsedInBills(id) || getCustomerPayments(id).isNotEmpty()) return@withContext false

        q.deleteSalesman(id)
        true
    }
    suspend fun isCustomerAccountUsedInBills(id: Long): Boolean = withContext(Dispatchers.Default) {
        bills.getAllBills().any { it.salesmanId == id }
    }

    // ==================== CUSTOMER PAYMENTS ====================
    data class CustomerPayment(
        val id: Long, val customerId: Long, val amount: Double,
        val discount: Double, val date: String, val note: String
    )

    suspend fun addCustomerPayment(customerId: Long, amount: Double, discount: Double, date: String, note: String) = withContext(Dispatchers.Default) {
        q.insertCustomerPayment(customerId, amount, discount, date, note)
    }
    suspend fun getCustomerPayments(customerId: Long): List<CustomerPayment> = withContext(Dispatchers.Default) {
        q.selectCustomerPayments(customerId) { id, cid, amt, disc, date, note ->
            CustomerPayment(id, cid, amt, disc, date, note)
        }.executeAsList()
    }
    suspend fun updateCustomerPayment(p: CustomerPayment) = withContext(Dispatchers.Default) {
        q.updateCustomerPayment(p.amount, p.discount, p.date, p.note, p.id)
    }
    suspend fun deleteCustomerPayment(id: Long) = withContext(Dispatchers.Default) { q.deleteCustomerPayment(id) }

    suspend fun getCustomerBalance(customerId: Long): Double = withContext(Dispatchers.Default) {
        val bills = bills.getAllBills().filter { it.salesmanId == customerId }
        val totalPurchase = bills.sumOf { it.grandTotal }
        val totalPaid = bills.sumOf { it.received } + getCustomerPayments(customerId).sumOf { it.amount + it.discount }
        totalPurchase - totalPaid
    }
    suspend fun getCustomerPurchases(customerId: Long): List<Bill> = withContext(Dispatchers.Default) {
        bills.getAllBills().filter { it.salesmanId == customerId }
    }
}