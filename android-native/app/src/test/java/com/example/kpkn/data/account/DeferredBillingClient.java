package com.example.kpkn.data.account;
import com.android.billingclient.api.*;
/** Delivers callbacks even after endConnection to model an already queued SDK response. */
public final class DeferredBillingClient extends BillingClient {
    public boolean ended;
    public volatile ProductDetailsResponseListener products;
    public final PurchasesUpdatedListener purchases;
    public DeferredBillingClient(PurchasesUpdatedListener listener) { purchases = listener; }
    public static BillingResult ok() { return BillingResult.newBuilder().setResponseCode(0).build(); }
    public void deliverPurchase() { purchases.onPurchasesUpdated(ok(), java.util.Collections.emptyList()); }
    @Override public int getConnectionState() { return 2; }
    @Override public com.android.billingclient.api.BillingResult isFeatureSupported(java.lang.String p0) { throw new UnsupportedOperationException("Unexpected SDK call: isFeatureSupported"); }
    @Override public com.android.billingclient.api.BillingResult launchBillingFlow(android.app.Activity p0, com.android.billingclient.api.BillingFlowParams p1) { throw new UnsupportedOperationException("Unexpected SDK call: launchBillingFlow"); }
    @Override public com.android.billingclient.api.BillingResult showAlternativeBillingOnlyInformationDialog(android.app.Activity p0, com.android.billingclient.api.AlternativeBillingOnlyInformationDialogListener p1) { throw new UnsupportedOperationException("Unexpected SDK call: showAlternativeBillingOnlyInformationDialog"); }
    @Override public com.android.billingclient.api.BillingResult showExternalOfferInformationDialog(android.app.Activity p0, com.android.billingclient.api.ExternalOfferInformationDialogListener p1) { throw new UnsupportedOperationException("Unexpected SDK call: showExternalOfferInformationDialog"); }
    @Override public com.android.billingclient.api.BillingResult showInAppMessages(android.app.Activity p0, com.android.billingclient.api.InAppMessageParams p1, com.android.billingclient.api.InAppMessageResponseListener p2) { throw new UnsupportedOperationException("Unexpected SDK call: showInAppMessages"); }
    @Override public void acknowledgePurchase(com.android.billingclient.api.AcknowledgePurchaseParams p0, com.android.billingclient.api.AcknowledgePurchaseResponseListener p1) { throw new UnsupportedOperationException("Unexpected SDK call: acknowledgePurchase"); }
    @Override public void consumeAsync(com.android.billingclient.api.ConsumeParams p0, com.android.billingclient.api.ConsumeResponseListener p1) { throw new UnsupportedOperationException("Unexpected SDK call: consumeAsync"); }
    @Override public void createAlternativeBillingOnlyReportingDetailsAsync(com.android.billingclient.api.AlternativeBillingOnlyReportingDetailsListener p0) { throw new UnsupportedOperationException("Unexpected SDK call: createAlternativeBillingOnlyReportingDetailsAsync"); }
    @Override public void createBillingProgramReportingDetailsAsync(com.android.billingclient.api.BillingProgramReportingDetailsParams p0, com.android.billingclient.api.BillingProgramReportingDetailsListener p1) { throw new UnsupportedOperationException("Unexpected SDK call: createBillingProgramReportingDetailsAsync"); }
    @Override public void createExternalOfferReportingDetailsAsync(com.android.billingclient.api.ExternalOfferReportingDetailsListener p0) { throw new UnsupportedOperationException("Unexpected SDK call: createExternalOfferReportingDetailsAsync"); }
    @Override public void endConnection() { ended = true; }
    @Override public void getBillingChoiceInfoAsync(com.android.billingclient.api.GetBillingChoiceInfoParams p0, com.android.billingclient.api.BillingChoiceInfoResponseListener p1) { throw new UnsupportedOperationException("Unexpected SDK call: getBillingChoiceInfoAsync"); }
    @Override public void getBillingConfigAsync(com.android.billingclient.api.GetBillingConfigParams p0, com.android.billingclient.api.BillingConfigResponseListener p1) { throw new UnsupportedOperationException("Unexpected SDK call: getBillingConfigAsync"); }
    @Override public void isAlternativeBillingOnlyAvailableAsync(com.android.billingclient.api.AlternativeBillingOnlyAvailabilityListener p0) { throw new UnsupportedOperationException("Unexpected SDK call: isAlternativeBillingOnlyAvailableAsync"); }
    @Override public void isBillingProgramAvailableAsync(int p0, com.android.billingclient.api.BillingProgramAvailabilityListener p1) { throw new UnsupportedOperationException("Unexpected SDK call: isBillingProgramAvailableAsync"); }
    @Override public void isExternalOfferAvailableAsync(com.android.billingclient.api.ExternalOfferAvailabilityListener p0) { throw new UnsupportedOperationException("Unexpected SDK call: isExternalOfferAvailableAsync"); }
    @Override public void launchExternalLink(android.app.Activity p0, com.android.billingclient.api.LaunchExternalLinkParams p1, com.android.billingclient.api.LaunchExternalLinkResponseListener p2) { throw new UnsupportedOperationException("Unexpected SDK call: launchExternalLink"); }
    @Override public void queryProductDetailsAsync(com.android.billingclient.api.QueryProductDetailsParams p0, com.android.billingclient.api.ProductDetailsResponseListener p1) { products = p1; }
    @Override public void queryPurchasesAsync(com.android.billingclient.api.QueryPurchasesParams p0, com.android.billingclient.api.PurchasesResponseListener p1) { throw new UnsupportedOperationException("Unexpected SDK call: queryPurchasesAsync"); }
    @Override public void showBillingProgramInformationDialog(android.app.Activity p0, com.android.billingclient.api.BillingProgramInformationDialogParams p1, com.android.billingclient.api.BillingProgramInformationDialogListener p2) { throw new UnsupportedOperationException("Unexpected SDK call: showBillingProgramInformationDialog"); }
    @Override public void startConnection(com.android.billingclient.api.BillingClientStateListener p0) { p0.onBillingSetupFinished(ok()); }
    @Override public boolean isReady() { return true; }
}
