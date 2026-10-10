package com.android.billingclient.api;
/** Synthetic product; its price/token are fixtures, never commercial configuration. */
public final class LocalSubscriptionFixture {
    public static ProductDetails product() throws org.json.JSONException {
        org.json.JSONObject phase = new org.json.JSONObject().put("priceAmountMicros", 1000000)
            .put("priceCurrencyCode", "USD").put("formattedPrice", "QA price").put("billingPeriod", "P1M")
            .put("recurrenceMode", 1).put("billingCycleCount", 0);
        org.json.JSONObject offer = new org.json.JSONObject().put("basePlanId", "qa-monthly")
            .put("offerId", "qa").put("offerIdToken", "synthetic-qa-offer")
            .put("offerTags", new org.json.JSONArray()).put("pricingPhases", new org.json.JSONArray().put(phase));
        org.json.JSONObject product = new org.json.JSONObject().put("productId", "kpkn_pro").put("type", "subs")
            .put("title", "QA").put("name", "QA").put("description", "Local deferred callback")
            .put("subscriptionOfferDetails", new org.json.JSONArray().put(offer));
        return new ProductDetails(product.toString());
    }
}
