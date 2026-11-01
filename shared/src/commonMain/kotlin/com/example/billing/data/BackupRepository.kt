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
                id = it.id, customer = it.customer, phone = it.phone, address = it.address, phone2 = it.phone2,
                accountNo = it.accountNo, date = it.date, rooms = it.rooms, discount = it.discount,
                extraCharges = it.extraCharges, paidAmount = it.paidAmount, invoiceLabel = it.invoiceLabel,
                showProduct = it.showProduct, showCategory = it.showCategory, showSubcategory = it.showSubcategory,showGroup = it.showGroup, showSubgroup = it.showSubgroup, // <-- NAYA
                salesmanId = it.salesmanId,
            )
        }
        val vendorsList = vendors.listVendors().map { it.vendor }
        val allVendorPayments = vendorsList.flatMap { v ->
            vendors.paymentsFor(v.id).map { p -> VendorPaymentBackup(p.id, p.vendorId, p.amount, p.discount, p.date, p.note) }
        }
        val allGroups = vendors.listGroups().map { GroupBackup(it.id, it.name) }
        val allSubgroups = vendors.listSubgroups().map { SubgroupBackup(it.id, it.groupId, it.name) }
        val allCategories = vendors.listCategories().map { CategoryBackup(it.id, it.name, it.subgroupId) }
        val allSubcategories = vendors.listSubcategories().map { SubcategoryBackup(it.id, it.categoryId, it.name) }
        val allProducts = vendors.listProducts().map {
            ProductBackup(it.id, it.name, it.groupId, it.subgroupId, it.categoryId, it.subcategoryId,
                it.price, it.salePrice, it.discountPct, it.openingStock, it.unit)
        }
        val allPurchases = vendors.listPurchases().map {
            PurchaseBackup(it.id, it.vendorId, it.date, it.items, it.total, it.discount, it.paid, it.notes)
        }
        val allCustomers = vendors.listCustomerAccounts().map {
            CustomerAccountBackup(it.id, it.name, it.phone1, it.phone2, it.address, it.accountNo)
        }
        val allCustomerPayments = allCustomers.flatMap { c ->
            vendors.getCustomerPayments(c.id).map { p ->
                CustomerPaymentBackup(p.id, p.customerId, p.amount, p.discount, p.date, p.note)
            }
        }
        val backup = FullBackup(
            bills = billsList,
            vendors = vendorsList.map { VendorBackup(it.id, it.name, it.address, it.phone1, it.phone2, it.accountDescription, it.createdAt) },
            vendorPayments = allVendorPayments,
            groups = allGroups,
            subgroups = allSubgroups,
            categories = allCategories,
            subcategories = allSubcategories,
            products = allProducts,
            purchases = allPurchases,
            customerAccounts = allCustomers,
            customerPayments = allCustomerPayments,
        )
        json.encodeToString(FullBackup.serializer(), backup)
    }

    suspend fun importAll(jsonString: String): String? = withContext(Dispatchers.Default) {
        try {
            val backup = json.decodeFromString(FullBackup.serializer(), jsonString)

            // 1. Clear (children pehle, parents baad me)
            bills.deleteAll()

// vendor payments + purchases pehle delete karo
            vendors.listVendors().forEach { v ->
                vendors.paymentsFor(v.vendor.id).forEach { p -> vendors.deletePayment(p.id) }
                vendors.listPurchases(v.vendor.id).forEach { p -> vendors.deletePurchase(p.id) }
            }
// ab vendors delete ho sakte hain
            vendors.listVendors().forEach { vendors.deleteVendor(it.vendor.id) }

// customer payments pehle, phir customer
            vendors.listCustomerAccounts().forEach { c ->
                vendors.getCustomerPayments(c.id).forEach { p -> vendors.deleteCustomerPayment(p.id) }
            }
            vendors.listProducts().forEach { vendors.deleteProduct(it.id) }
            vendors.listSubcategories().forEach { vendors.deleteSubcategory(it.id) }
            vendors.listCategories().forEach { vendors.deleteCategory(it.id) }
            vendors.listSubgroups().forEach { vendors.deleteSubgroup(it.id) }
            vendors.listGroups().forEach { vendors.deleteGroup(it.id) }
            vendors.listCustomerAccounts().forEach { vendors.deleteCustomerAccount(it.id) }

            // 2. Groups
            val groupMap = mutableMapOf<Long, Long>()
            for (g in backup.groups) {
                val newId = vendors.addGroup(g.name)
                groupMap[g.id] = newId
            }

            // 3. Subgroups
            val subgroupMap = mutableMapOf<Long, Long>()
            for (sg in backup.subgroups) {
                val newGroupId = sg.groupId?.let { groupMap[it] }
                val newId = vendors.addSubgroup(newGroupId, sg.name)
                subgroupMap[sg.id] = newId
            }


            // 4. Categories
            val catMap = mutableMapOf<Long, Long>()
            for (c in backup.categories) {
                val newSgId = c.subgroupId?.let { subgroupMap[it] }
                val newId = vendors.addCategory(newSgId, c.name)
                catMap[c.id] = newId
            }

            // 5. Subcategories
            val subMap = mutableMapOf<Long, Long>()
            for (s in backup.subcategories) {
                val newCatId = s.categoryId?.let { catMap[it] }
                val err = vendors.addSubcategory(newCatId, s.name)
                if (err == null) {
                    val newId = vendors.listSubcategories().firstOrNull {
                        it.categoryId == newCatId && it.name.equals(s.name, ignoreCase = true)
                    }?.id
                    if (newId != null) subMap[s.id] = newId
                }
            }

            // 6. Products
            val prodMap = mutableMapOf<Long, Long>()
            for (p in backup.products) {
                val newProduct = Product(
                    id = 0L, name = p.name,
                    groupId = p.groupId?.let { groupMap[it] },
                    subgroupId = p.subgroupId?.let { subgroupMap[it] },
                    categoryId = p.categoryId?.let { catMap[it] },
                    subcategoryId = p.subcategoryId?.let { subMap[it] },
                    price = p.price, salePrice = p.salePrice, discountPct = p.discountPct,
                    openingStock = p.openingStock, unit = p.unit,
                )
                val err = vendors.addProduct(newProduct)
                if (err == null) {
                    val newId = vendors.listProducts().firstOrNull {
                        it.name == p.name && it.categoryId == newProduct.categoryId && it.subcategoryId == newProduct.subcategoryId
                    }?.id
                    if (newId != null) prodMap[p.id] = newId
                }
            }

            // 7. Vendors
            val vendMap = mutableMapOf<Long, Long>()
            for (v in backup.vendors) {
                val newId = vendors.addVendor(v.name, v.address, v.phone1, v.phone2, v.accountDescription)
                vendMap[v.id] = newId
            }

            // 8. Vendor payments
            for (p in backup.vendorPayments) {
                val newVendId = vendMap[p.vendorId] ?: continue
                vendors.addPayment(newVendId, p.amount, p.discount, p.date, p.note)
            }

            // 9. Purchases
            for (p in backup.purchases) {
                val newVendId = vendMap[p.vendorId] ?: continue
                val newItems = p.items.map { item ->
                    item.copy(productId = item.productId?.let { prodMap[it] })
                }
                vendors.savePurchase(Purchase(0L, newVendId, "", p.date, newItems, p.total, p.discount, p.paid, p.notes))
            }

            // 10. Customers
            val custMap = mutableMapOf<Long, Long>()
            for (c in backup.customerAccounts) {
                val newId = vendors.addCustomerAccount(c.name, c.phone1, c.phone2, c.address, c.accountNo)
                custMap[c.id] = newId
            }

            // 11. Customer payments
            for (p in backup.customerPayments) {
                val newCustId = custMap[p.customerId] ?: continue
                vendors.addCustomerPayment(newCustId, p.amount, p.discount, p.date, p.note)
            }

            // 12. Bills
            val updatedBills = backup.bills.map { b ->
                b.copy(salesmanId = b.salesmanId?.let { custMap[it] })
            }
            bills.importFromJson(json.encodeToString(ListSerializer(BillBackup.serializer()), updatedBills))

            null
        } catch (e: Exception) {
            e.message ?: "Import failed"
        }
    }

    suspend fun clearLocalData() = withContext(Dispatchers.Default) {
        bills.deleteAll()

        vendors.listVendors().forEach { v ->
            vendors.paymentsFor(v.vendor.id).forEach { p -> vendors.deletePayment(p.id) }
            vendors.listPurchases(v.vendor.id).forEach { p -> vendors.deletePurchase(p.id) }
        }
        vendors.listVendors().forEach { vendors.deleteVendor(it.vendor.id) }

        vendors.listCustomerAccounts().forEach { c ->
            vendors.getCustomerPayments(c.id).forEach { p -> vendors.deleteCustomerPayment(p.id) }
        }
        vendors.listProducts().forEach { vendors.deleteProduct(it.id) }
        vendors.listSubcategories().forEach { vendors.deleteSubcategory(it.id) }
        vendors.listCategories().forEach { vendors.deleteCategory(it.id) }
        vendors.listSubgroups().forEach { vendors.deleteSubgroup(it.id) }
        vendors.listGroups().forEach { vendors.deleteGroup(it.id) }
        vendors.listCustomerAccounts().forEach { vendors.deleteCustomerAccount(it.id) }
    }
}