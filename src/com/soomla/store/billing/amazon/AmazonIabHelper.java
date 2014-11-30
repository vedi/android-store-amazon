/*
 * Copyright (C) 2012 Soomla Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.soomla.store.billing.amazon;

import android.app.Activity;
import android.text.TextUtils;

import com.amazon.device.iap.PurchasingListener;
import com.amazon.device.iap.PurchasingService;
import com.amazon.device.iap.model.Product;
import com.amazon.device.iap.model.ProductDataResponse;
import com.amazon.device.iap.model.PurchaseResponse;
import com.amazon.device.iap.model.PurchaseUpdatesResponse;
import com.amazon.device.iap.model.Receipt;
import com.amazon.device.iap.model.UserData;
import com.amazon.device.iap.model.UserDataResponse;
import com.soomla.SoomlaApp;
import com.soomla.SoomlaUtils;
import com.soomla.store.billing.IabHelper;
import com.soomla.store.billing.IabInventory;
import com.soomla.store.billing.IabPurchase;
import com.soomla.store.billing.IabResult;
import com.soomla.store.billing.IabSkuDetails;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * This is an implementation of SOOMLA's IabHelper to create a plugin of Amazon to SOOMLA.
 *
 * More docs in parent.
 */
public class AmazonIabHelper extends IabHelper {

    /**
     * see parent
     */
    @Override
    protected void startSetupInner() {
        if (mPurchasingObserver == null) {
            mPurchasingObserver = new PurchasingObserver();
        }
        PurchasingService.registerListener(SoomlaApp.getAppContext(), mPurchasingObserver);

        PurchasingService.getUserData();
    }

    /**
     * see parent
     */
    @Override
    protected void launchPurchaseFlowInner(Activity act, String sku, String extraData) {
        mExtraData = extraData;
        PurchasingService.purchase(sku);
    }

    /**
     * see parent
     */
    @Override
    protected void restorePurchasesAsyncInner() {
        PurchasingService.getPurchaseUpdates(false);
    }

    /**
     * see parent
     */
    @Override
    protected void fetchSkusDetailsAsyncInner(List<String> skus) {
        PurchasingService.getProductData(new HashSet<String>(skus));
    }

    private class PurchasingObserver implements PurchasingListener {

        /**
         * see parent (or https://developer.amazon.com/public/apis/earn/in-app-purchasing/docs/quick-start)
         */
        @Override
        public void onProductDataResponse(ProductDataResponse productDataResponse) {

            switch (productDataResponse.getRequestStatus()) {

                case SUCCESSFUL:

                    String unskus = "";
                    for (final String s : productDataResponse.getUnavailableSkus()) {
                        unskus += s + "/";
                    }
                    if (!TextUtils.isEmpty(unskus)) {
                        SoomlaUtils.LogError(TAG, "(onItemDataResponse) The following skus were unavailable: " + unskus);
                    }

                    final Map<String, Product> products = productDataResponse.getProductData();
                    IabInventory inventory = new IabInventory();
                    for (final String key : products.keySet()) {
                        Product product = products.get(key);
                        String currencyCode = AmazonIabUtils.getCurrencyCode(mCurrentUserData.getMarketplace());
                        long priceMicros = AmazonIabUtils.getPriceAmountMicros(product.getPrice());

                        IabSkuDetails skuDetails = new IabSkuDetails(ITEM_TYPE_INAPP,
                                product.getSku(), product.getPrice(), product.getTitle(), product.getDescription(), priceMicros, currencyCode);
                        inventory.addSkuDetails(skuDetails);
                        SoomlaUtils.LogDebug(TAG, String.format("Product: %s\n Type: %s\n SKU: %s\n Price: %s\n Description: %s\n",
                                product.getTitle(), product.getProductType(), product.getSku(), product.getPrice(), product.getDescription()));
                    }

                    AmazonIabHelper.this.fetchSkusDetailsSuccess(inventory);
                    break;

                case FAILED: // Fail gracefully on failed responses.
                    IabResult result = new IabResult(IabResult.BILLING_RESPONSE_RESULT_ERROR,
                            "Couldn't complete refresh operation.");
                    AmazonIabHelper.this.fetchSkusDetailsFailed(result);
//                    Log.v(TAG, "ItemDataRequestStatus: FAILED");
                    break;
            }

        }

        /**
         * see parent (or https://developer.amazon.com/public/apis/earn/in-app-purchasing/docs/quick-start)
         */
        @Override
        public void onPurchaseResponse(final PurchaseResponse response) {
            switch (response.getRequestStatus()) {
                case SUCCESSFUL:
                    Receipt receipt = response.getReceipt();
//                Item.ItemType itemType = receipt.getItemType();
                    String sku = receipt.getSku();
                    String purchaseToken = receipt.getReceiptId();

                    IabPurchase purchase = new IabPurchase(ITEM_TYPE_INAPP, sku, purchaseToken, response.getRequestId().toString(), 0);
                    purchase.setDeveloperPayload(AmazonIabHelper.this.mExtraData);
                    purchaseSucceeded(purchase);
                    AmazonIabHelper.this.mExtraData = "";
                    break;
                case INVALID_SKU:
                    String msg = "The purchase has failed. Invalid sku given.";
                    SoomlaUtils.LogError(TAG, msg);
                    IabResult result = new IabResult(IabResult.BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE, msg);
                    purchaseFailed(result, null);
                    break;
                case ALREADY_PURCHASED:

                    purchase = new IabPurchase(ITEM_TYPE_INAPP, mLastOperationSKU, "", response.getRequestId().toString(), 0);

                    msg = "The purchase has failed. Product already purchased.";
                    SoomlaUtils.LogError(TAG, msg);
                    result = new IabResult(IabResult.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED, msg);
                    purchaseFailed(result, purchase);
                    break;
                default:
                    msg = "The purchase has failed. No message.";
                    SoomlaUtils.LogError(TAG, msg);
                    result = new IabResult(IabResult.BILLING_RESPONSE_RESULT_ERROR, msg);
                    purchaseFailed(result, null);
                    break;
            }
        }

        @Override
        public void onPurchaseUpdatesResponse(PurchaseUpdatesResponse purchaseUpdatesResponse) {

            if (mCurrentUserData.getUserId() != null && !mCurrentUserData.getUserId().equals(purchaseUpdatesResponse.getUserData().getUserId())) {
                SoomlaUtils.LogError(TAG, "The updates is not for the current user id.");
                IabResult result = new IabResult(IabResult.BILLING_RESPONSE_RESULT_ERROR,
                            "Couldn't complete restore purchases operation.");
                AmazonIabHelper.this.restorePurchasesFailed(result);
                return;
            }

            switch (purchaseUpdatesResponse.getRequestStatus()) {
                case SUCCESSFUL:
                    if (mInventory == null) {
                        mInventory = new IabInventory();
                    }

                    // Process receipts
                    for (final Receipt receipt : purchaseUpdatesResponse.getReceipts()) {
                        IabPurchase purchase = new IabPurchase(ITEM_TYPE_INAPP,
                                receipt.getSku(), "NO TOKEN",
                                purchaseUpdatesResponse.getRequestId().toString(), 0);
                        mInventory.addPurchase(purchase);
                    }

                    if (purchaseUpdatesResponse.hasMore()) {
                        SoomlaUtils.LogDebug(TAG, "Initiating Another Purchase Updates");
                        PurchasingService.getPurchaseUpdates(false);
                    } else {
                        AmazonIabHelper.this.restorePurchasesSuccess(mInventory);
                        mInventory = null;
                    }

                    break;

                case FAILED:
                    SoomlaUtils.LogError(TAG, "There was an error while trying to restore purchases. " +
                            "Finishing with those that were accumulated until now.");
                    if (mInventory != null) {
                        AmazonIabHelper.this.restorePurchasesSuccess(mInventory);
                        mInventory = null;
                    } else {
                        IabResult result = new IabResult(IabResult.BILLING_RESPONSE_RESULT_ERROR,
                            "Couldn't complete restore purchases operation.");
                        AmazonIabHelper.this.restorePurchasesFailed(result);
                    }
                    break;
            }
        }


        /** Private Members */

        private static final String TAG = "SOOMLA AmazonIabHelper PurchasingObserver";

        private UserData mCurrentUserData = null;
        private IabInventory mInventory;




        @Override
        public void onUserDataResponse(UserDataResponse userDataResponse) {
            if (userDataResponse.getRequestStatus() == UserDataResponse.RequestStatus.SUCCESSFUL) {
                mCurrentUserData = userDataResponse.getUserData();
                AmazonIabHelper.this.setupSuccess();
            } else {
                String msg = "Unable to get userId";
                SoomlaUtils.LogError(TAG, msg);
                IabResult result = new IabResult(IabResult.BILLING_RESPONSE_RESULT_ERROR, msg);
                AmazonIabHelper.this.setupFailed(result);
            }
        }
    }


    /** Private Members */

    private static final String TAG = "SOOMLA AmazonIabHelper";

    private String mExtraData;
    private PurchasingObserver mPurchasingObserver;
}