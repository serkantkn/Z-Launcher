package com.serkantkn.zeta.data.billing

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.serkantkn.zeta.BuildConfig
import com.serkantkn.zeta.util.Pro
import com.serkantkn.zeta.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Pro, bought once inside the app through Google Play.
 *
 * There is no server: Google Play is asked which purchases this account holds, and the answer is
 * remembered on the phone so Pro stays on with no connection and from the first frame. A purchase
 * Play no longer reports - refunded, or bought back - turns Pro off again the next time Play is
 * asked and answers.
 *
 * The premium flavour is Pro without asking anybody.
 */
object ProStore {

    /** The one-time product in Play Console. */
    const val PRODUCT_ID = "z_launcher_pro"

    enum class Status {
        /** Still asking Google Play. */
        CONNECTING,
        /** Play answered: Pro can be bought. */
        READY,
        /** Play cannot be reached, or does not sell this app (not installed from Play). */
        UNAVAILABLE,
        /** Bought, but the payment has not gone through yet (cash at a shop, a slow bank). */
        PENDING,
        OWNED
    }

    private const val PREFS = "pro_store"
    private const val KEY_OWNED = "owned"
    private const val TAG = "ProStore"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var app: Context
    private var client: BillingClient? = null

    private val _status = MutableStateFlow(Status.CONNECTING)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _product = MutableStateFlow<ProductDetails?>(null)

    /** Pro as Play sells it, with its price in the buyer's currency; null until Play says. */
    val product: StateFlow<ProductDetails?> = _product.asStateFlow()

    /** Called once, as the process starts: Pro from what was last known, then from Play. */
    fun start(context: Context) {
        app = context.applicationContext
        if (BuildConfig.IS_PREMIUM) {
            Pro.setPurchased(true)
            _status.value = Status.OWNED
            return
        }
        val remembered = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_OWNED, false)
        apply(owned = remembered)
        ProTrial.start(app)
        // The keyboard is Pro's, bought or on trial: it comes and goes with Pro itself.
        scope.launch { Pro.flow.collect { setKeyboardEnabled(it) } }
        connect()
    }

    private val purchasesUpdated = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch { take(purchases) }
        } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            ZuneLog.w(TAG, "the purchase did not go through: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private fun connect() {
        val billing = client ?: BillingClient.newBuilder(app)
            .setListener(purchasesUpdated)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
            .also { client = it }
        if (billing.isReady) {
            scope.launch { refresh() }
            return
        }
        if (_status.value != Status.OWNED && _status.value != Status.PENDING) _status.value = Status.CONNECTING
        runCatching {
            billing.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        scope.launch { refresh() }
                    } else {
                        ZuneLog.w(TAG, "Google Play would not sell: ${result.responseCode} ${result.debugMessage}")
                        if (_status.value == Status.CONNECTING) _status.value = Status.UNAVAILABLE
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // Reconnecting is the client's own business (enableAutoServiceReconnection).
                }
            })
        }.onFailure {
            ZuneLog.w(TAG, "Google Play could not be reached", it)
            _status.value = if (Pro.purchased) Status.OWNED else Status.UNAVAILABLE
        }
    }

    /** Asks Play again what this account holds, and what Pro costs: the "restore" button. */
    fun restore() {
        if (BuildConfig.IS_PREMIUM) return
        val billing = client
        if (billing == null || !billing.isReady) connect() else scope.launch { refresh() }
    }

    private suspend fun refresh() {
        val billing = client ?: return
        val owned = runCatching {
            billing.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
            )
        }.getOrNull()
        if (owned != null && owned.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            take(owned.purchasesList)
        }
        if (_product.value == null) {
            val details = runCatching {
                billing.queryProductDetails(
                    QueryProductDetailsParams.newBuilder()
                        .setProductList(
                            listOf(
                                QueryProductDetailsParams.Product.newBuilder()
                                    .setProductId(PRODUCT_ID)
                                    .setProductType(BillingClient.ProductType.INAPP)
                                    .build()
                            )
                        )
                        .build()
                )
            }.getOrNull()
            _product.value = details?.productDetailsList?.firstOrNull { it.productId == PRODUCT_ID }
        }
        if (_status.value == Status.CONNECTING || _status.value == Status.READY || _status.value == Status.UNAVAILABLE) {
            _status.value = when {
                Pro.purchased -> Status.OWNED
                _product.value != null -> Status.READY
                else -> Status.UNAVAILABLE
            }
        }
    }

    /** What Play says this account holds: Pro, Pro on its way, or neither. */
    private suspend fun take(purchases: List<Purchase>) {
        when (proStanding(purchases.map { it.products to it.purchaseState })) {
            Standing.OWNED -> {
                purchases.filter { PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .forEach { acknowledge(it) }
                apply(owned = true)
            }
            Standing.PENDING -> {
                apply(owned = false)
                _status.value = Status.PENDING
            }
            Standing.NONE -> {
                // Never bought, or refunded: Pro goes off. Until Play says what it costs the page
                // is still asking; refresh() settles it once the product has been asked for.
                apply(owned = false)
                _status.value = if (_product.value != null) Status.READY else Status.CONNECTING
            }
        }
    }

    /** A purchase not acknowledged within three days is refunded by Google. */
    private suspend fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val client = client ?: return
        val result = runCatching {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build())
        }.getOrNull()
        if (result?.responseCode != BillingClient.BillingResponseCode.OK) {
            ZuneLog.w(TAG, "the purchase could not be acknowledged: ${result?.responseCode}; it is tried again next time")
        }
    }

    private fun apply(owned: Boolean) {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_OWNED, owned).apply()
        Pro.setPurchased(owned)
        if (owned) _status.value = Status.OWNED
    }

    /** Opens Google Play's own purchase sheet for Pro. False when Play has nothing to sell yet. */
    fun buy(activity: Activity): Boolean {
        val billing = client
        val details = _product.value
        if (billing == null || !billing.isReady || details == null) {
            restore()
            return false
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build())
            )
            .build()
        val result = billing.launchBillingFlow(activity, params)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    /**
     * The Windows Phone keyboard is Pro's, and a keyboard is a system service the manifest
     * declares: the free build ships it switched off, and Pro switches it on.
     */
    private fun setKeyboardEnabled(on: Boolean) {
        val pm = app.packageManager
        val state = if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        for (name in KEYBOARD_COMPONENTS) {
            runCatching {
                val component = ComponentName(app, name)
                if (pm.getComponentEnabledSetting(component) != state) {
                    pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
                }
            }.onFailure { ZuneLog.w(TAG, "$name could not be switched", it) }
        }
    }

    private val KEYBOARD_COMPONENTS = listOf(
        "com.serkantkn.zeta.data.service.ZuneKeyboardService",
        "com.serkantkn.zeta.ui.keyboard.KeyboardMicPermissionActivity"
    )

    /** Where an account stands with Pro, from its purchases. */
    enum class Standing { OWNED, PENDING, NONE }

    /**
     * From each purchase's products and state: Pro paid for is Pro; Pro bought and still being
     * paid for is pending; anything else is neither.
     */
    fun proStanding(purchases: List<Pair<List<String>, Int>>): Standing {
        val pro = purchases.filter { (products, _) -> PRODUCT_ID in products }
        return when {
            pro.any { it.second == Purchase.PurchaseState.PURCHASED } -> Standing.OWNED
            pro.any { it.second == Purchase.PurchaseState.PENDING } -> Standing.PENDING
            else -> Standing.NONE
        }
    }
}
