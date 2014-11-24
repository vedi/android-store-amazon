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

import com.amazon.inapp.purchasing.BasePurchasingObserver;
import com.amazon.inapp.purchasing.GetUserIdResponse;
import com.amazon.inapp.purchasing.Item;
import com.amazon.inapp.purchasing.ItemDataResponse;
import com.amazon.inapp.purchasing.Offset;
import com.amazon.inapp.purchasing.PurchaseResponse;
import com.amazon.inapp.purchasing.PurchaseUpdatesResponse;
import com.amazon.inapp.purchasing.PurchasingManager;
import com.amazon.inapp.purchasing.Receipt;
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
        PurchasingManager.registerObserver(mPurchasingObserver);
    }

    /**
     * see parent
     */
    @Override
    protected void launchPurchaseFlowInner(Activity act, String sku, String extraData) {
        mExtraData = extraData;
        PurchasingManager.initiatePurchaseRequest(sku);
    }

    /**
     * see parent
     */
    @Override
    protected void restorePurchasesAsyncInner() {
        PurchasingManager.initiatePurchaseUpdatesRequest(Offset.BEGINNING);
    }

    /**
     * see parent
     */
    @Override
    protected void fetchSkusDetailsAsyncInner(List<String> skus) {
        PurchasingManager.initiateItemDataRequest(new HashSet<String>(skus));
    }

    private class PurchasingObserver extends BasePurchasingObserver {

        public PurchasingObserver() {
            super(SoomlaApp.getAppContext());
        }

        /**
         * see parent (or https://developer.amazon.com/public/apis/earn/in-app-purchasing/docs/quick-start)
         */
        @Override
        public void onSdkAvailable(final boolean isSandboxMode) {
            AmazonIabHelper.this.setRvsProductionMode(!isSandboxMode);

            PurchasingManager.initiateGetUserIdRequest();
        }

        /**
         * see parent (or https://developer.amazon.com/public/apis/earn/in-app-purchasing/docs/quick-start)
         */
        @Override
        public void onItemDataResponse(final ItemDataResponse response) {
            switch (response.getItemDataRequestStatus()) {
                case SUCCESSFUL_WITH_UNAVAILABLE_SKUS:
                    String unskus = "";
                    for (final String s : response.getUnavailableSkus()) {
                        unskus += s + "/";
                    }
                    SoomlaUtils.LogError(TAG, "(onItemDataResponse) The following skus were unavailable: " + unskus);

                case SUCCESSFUL:
                    final Map<String, Item> items = response.getItemData();
                    IabInventory inventory = new IabInventory();
                    for (final String key : items.keySet()) {
                        Item i = items.get(key);
                        IabSkuDetails skuDetails = new IabSkuDetails(ITEM_TYPE_INAPP,
                                i.getSku(), i.getPrice(), i.getTitle(), i.getDescription(), 0, "");
                        inventory.addSkuDetails(skuDetails);

//                        Log.v(TAG, String.format("Item: %s\n Type: %s\n SKU: %s\n Price: %s\n Description: %s\n", i.getTitle(), i.getItemType(), i.getSku(), i.getPrice(), getDescription()));
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
            final PurchaseResponse.PurchaseRequestStatus status = response.getPurchaseRequestStatus();
            switch (status) {
                case SUCCESSFUL:
                    Receipt receipt = response.getReceipt();
//                Item.ItemType itemType = receipt.getItemType();
                    String sku = receipt.getSku();
                    String purchaseToken = receipt.getPurchaseToken();

                    IabPurchase purchase = new IabPurchase(ITEM_TYPE_INAPP, sku, purchaseToken, response.getRequestId(), 0);
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
                case ALREADY_ENTITLED:

                    purchase = new IabPurchase(ITEM_TYPE_INAPP, mLastOperationSKU, "", response.getRequestId(), 0);

                    msg = "The purchase has failed. Entitlement already entitled.";
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


        /**
         * see parent (or https://developer.amazon.com/public/apis/earn/in-app-purchasing/docs/quick-start)
         */
        @Override
        public void onPurchaseUpdatesResponse(final PurchaseUpdatesResponse response) {
            if (mCurrentUserID != null && !mCurrentUserID.equals(response.getUserId())) {
                SoomlaUtils.LogError(TAG, "The updates is not for the current user id.");
                IabResult result = new IabResult(IabResult.BILLING_RESPONSE_RESULT_ERROR,
                            "Couldn't complete restore purchases operation.");
                AmazonIabHelper.this.restorePurchasesFailed(result);
                return;
            }

            switch (response.getPurchaseUpdatesRequestStatus()) {
                case SUCCESSFUL:
                    if (mInventory == null) {
                        mInventory = new IabInventory();
                    }

                    // Check for revoked SKUs
                    for (final String sku : response.getRevokedSkus()) {
                        IabPurchase purchase = new IabPurchase(ITEM_TYPE_INAPP,
                                sku, "",
                                response.getRequestId(), 2);
                        mInventory.addPurchase(purchase);
                    }

                    // Process receipts
                    for (final Receipt receipt : response.getReceipts()) {
                        switch (receipt.getItemType()) {
                            case ENTITLED: // Re-entitle the customer
                                IabPurchase purchase = new IabPurchase(ITEM_TYPE_INAPP,
                                        receipt.getSku(), receipt.getPurchaseToken(),
                                        response.getRequestId(), 0);
                                mInventory.addPurchase(purchase);
                                break;
                        }
                    }

                    final Offset newOffset = response.getOffset();
                    if (response.isMore()) {
                        SoomlaUtils.LogDebug(TAG, "Initiating Another Purchase Updates with offset: "
                            + newOffset.toString());
                        PurchasingManager.initiatePurchaseUpdatesRequest(newOffset);
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

        /**
         * see parent (or https://developer.amazon.com/public/apis/earn/in-app-purchasing/docs/quick-start)
         */
        @Override
        public void onGetUserIdResponse(final GetUserIdResponse response) {
            if (response.getUserIdRequestStatus() ==
                    GetUserIdResponse.GetUserIdRequestStatus.SUCCESSFUL) {
                mCurrentUserID = response.getUserId();
                AmazonIabHelper.this.setupSuccess();
            } else {
                String msg = "Unable to get userId";
                SoomlaUtils.LogError(TAG, msg);
                IabResult result = new IabResult(IabResult.BILLING_RESPONSE_RESULT_ERROR, msg);
                AmazonIabHelper.this.setupFailed(result);
            }
        }


        /** Private Members */

        private static final String TAG = "SOOMLA AmazonIabHelper PurchasingObserver";

        private String mCurrentUserID = null;
        private IabInventory mInventory;

    }


    /** Private Members */

    private static final String TAG = "SOOMLA AmazonIabHelper";

    private String mExtraData;
    private PurchasingObserver mPurchasingObserver;
}