package com.ashenlantern.holdtheroad;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Interstitial ads between battles, the ad-consent form, and the one-time "remove_ads" purchase. */
public class Monetize {
    static final String PRODUCT_NO_ADS = "remove_ads";
    private static final String PREFS = "monetize";

    private final Activity act;
    private final WebView web;
    private final ConsentInformation consent;
    private final AtomicBoolean adsStarted = new AtomicBoolean(false);
    private BillingClient billing;
    private volatile ProductDetails noAdsProduct;
    private volatile InterstitialAd interstitial;
    private volatile boolean noAds;

    Monetize(Activity act, WebView web) {
        this.act = act;
        this.web = web;
        this.noAds = act.getSharedPreferences(PREFS, 0).getBoolean("noAds", false);
        this.consent = UserMessagingPlatform.getConsentInformation(act);
    }

    /** Called once from onCreate. */
    void start() {
        startBilling();
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consent.requestConsentInfoUpdate(act, params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(act, formError -> {
                    sendPrivacy();
                    if (consent.canRequestAds()) startAds();
                }),
                error -> { if (consent.canRequestAds()) startAds(); });
        if (consent.canRequestAds()) startAds();
    }

    // ---------------- ads ----------------
    private void startAds() {
        if (noAds || !adsStarted.compareAndSet(false, true)) return;
        new Thread(() -> MobileAds.initialize(act, status -> act.runOnUiThread(this::loadInterstitial))).start();
    }

    private void loadInterstitial() {
        if (noAds || interstitial != null) return;
        InterstitialAd.load(act, BuildConfig.ADMOB_INTERSTITIAL_ID, new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override public void onAdLoaded(@NonNull InterstitialAd ad) { interstitial = ad; }
                    @Override public void onAdFailedToLoad(@NonNull LoadAdError err) { interstitial = null; }
                });
    }

    private boolean showInterstitialNow() {
        InterstitialAd ad = interstitial;
        if (noAds || ad == null) { loadInterstitial(); js("adclosed", "true"); return false; }
        interstitial = null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { js("adclosed", "true"); loadInterstitial(); }
            @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) { js("adclosed", "true"); loadInterstitial(); }
        });
        ad.show(act);
        return true;
    }

    // ---------------- billing ----------------
    private void startBilling() {
        billing = BillingClient.newBuilder(act)
                .setListener((result, purchases) -> {
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                        handlePurchases(purchases, false);
                    } else if (result.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                        queryOwned();
                    } else if (result.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED) {
                        js("buyfail", "The purchase didn't go through. Please try again.");
                    }
                })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
        connect(null);
    }

    private void connect(Runnable then) {
        if (billing.isReady()) { if (then != null) then.run(); return; }
        billing.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(@NonNull BillingResult r) {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryOwned();
                    queryPrice();
                    if (then != null) then.run();
                }
            }
            @Override public void onBillingServiceDisconnected() { }
        });
    }

    private void queryOwned() {
        billing.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                (r, purchases) -> { if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) handlePurchases(purchases, true); });
    }

    private void queryPrice() {
        QueryProductDetailsParams q = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_NO_ADS).setProductType(BillingClient.ProductType.INAPP).build()))
                .build();
        billing.queryProductDetailsAsync(q, (r, res) -> {
            List<ProductDetails> list = res.getProductDetailsList();
            if (r.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null && !list.isEmpty()) {
                noAdsProduct = list.get(0);
                ProductDetails.OneTimePurchaseOfferDetails o = noAdsProduct.getOneTimePurchaseOfferDetails();
                if (o != null) js("price", o.getFormattedPrice());
            }
        });
    }

    /** fullList: this is every purchase the user owns, so a missing one means it was refunded. */
    private void handlePurchases(List<Purchase> purchases, boolean fullList) {
        boolean owned = false;
        for (Purchase p : purchases) {
            if (!p.getProducts().contains(PRODUCT_NO_ADS)) continue;
            if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                owned = true;
                if (!p.isAcknowledged()) {
                    billing.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(p.getPurchaseToken()).build(), r -> { });
                }
            } else if (p.getPurchaseState() == Purchase.PurchaseState.PENDING && !fullList) {
                js("buyfail", "Your purchase is pending. Ads will go once it completes.");
            }
        }
        if (owned || fullList) setNoAds(owned);
    }

    private void setNoAds(boolean v) {
        noAds = v;
        act.getSharedPreferences(PREFS, 0).edit().putBoolean("noAds", v).apply();
        if (v) interstitial = null; else if (consent.canRequestAds()) startAds();
        js("noads", v ? "true" : "false");
    }

    private void sendPrivacy() {
        boolean req = consent.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
        js("privacy", req ? "true" : "false");
    }

    private void js(String ev, String val) {
        String v = val.equals("true") || val.equals("false") ? val : "'" + val.replace("\\", "\\\\").replace("'", "\\'") + "'";
        act.runOnUiThread(() -> web.evaluateJavascript("window.htrNative&&window.htrNative('" + ev + "'," + v + ")", null));
    }

    // ---------------- called from the game (runs on a WebView thread) ----------------
    @JavascriptInterface public void ready() {
        js("noads", noAds ? "true" : "false");
        act.runOnUiThread(() -> { sendPrivacy(); connect(null); if (billing.isReady()) { queryOwned(); queryPrice(); } });
    }

    @JavascriptInterface public boolean showInterstitial() {
        if (noAds || interstitial == null) { act.runOnUiThread(this::loadInterstitial); return false; }
        act.runOnUiThread(this::showInterstitialNow);
        return true;
    }

    @JavascriptInterface public void buyNoAds() {
        act.runOnUiThread(() -> connect(() -> {
            if (noAdsProduct == null) { js("buyfail", "The store isn't available right now. Please try again later."); return; }
            BillingFlowParams f = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(
                            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(noAdsProduct).build()))
                    .build();
            billing.launchBillingFlow(act, f);
        }));
    }

    @JavascriptInterface public void privacyOptions() {
        act.runOnUiThread(() -> UserMessagingPlatform.showPrivacyOptionsForm(act, e -> sendPrivacy()));
    }

    void destroy() { if (billing != null) billing.endConnection(); }
}
