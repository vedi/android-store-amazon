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
import com.soomla.store.SoomlaApp;
import com.soomla.store.StoreUtils;
import com.soomla.store.billing.IabHelper;
import com.soomla.store.billing.IabInventory;
import com.soomla.store.billing.IabPurchase;
import com.soomla.store.billing.IabResult;
import com.soomla.store.billing.IabSkuDetails;

import java.util.HashSet;
import java.util.List;
import java.util.Map;


public class AmazonIabHelper extends IabHelper {
    private static final String TAG = "SOOMLA AmazonIabHelper";
    private String mExtraData;


    @Override
    protected void startSetupInner() {
        if (mPurchasingObserver == null) {
            mPurchasingObserver = new PurchasingObserver();
        }
        PurchasingManager.registerObserver(mPurchasingObserver);
    }

    @Override
    protected void launchPurchaseFlowInner(Activity act, String sku, String extraData) {
        mExtraData = extraData;
        PurchasingManager.initiatePurchaseRequest(sku);
    }

    @Override
    protected void restorePurchasesAsyncInner() {
        PurchasingManager.initiatePurchaseUpdatesRequest(getPersistedOffset());
    }

    @Override
    protected void fetchSkusDetailsAsyncInner(List<String> skus) {
        PurchasingManager.initiateItemDataRequest(new HashSet<String>(skus));
    }

    private Offset getPersistedOffset() {
        return Offset.BEGINNING;
    }

    private PurchasingObserver mPurchasingObserver;

    private class PurchasingObserver extends BasePurchasingObserver {

        public PurchasingObserver() {
            super(SoomlaApp.getAppContext());
        }

        public void onSdkAvailable(final boolean isSandboxMode) {
            AmazonIabHelper.this.setRvsProductionMode(!isSandboxMode);

            PurchasingManager.initiateGetUserIdRequest();
        }

        public void onItemDataResponse(final ItemDataResponse response) {
            switch (response.getItemDataRequestStatus()) {
                case SUCCESSFUL_WITH_UNAVAILABLE_SKUS:
                    String unskus = "";
                    for (final String s : response.getUnavailableSkus()) {
                        unskus += s + "/";
                    }
                    StoreUtils.LogError(TAG, "(onItemDataResponse) The following skus were unavailable: " + unskus);

                case SUCCESSFUL:
                    final Map<String, Item> items = response.getItemData();
                    IabInventory inventory = new IabInventory();
                    for (final String key : items.keySet()) {
                        Item i = items.get(key);
                        IabSkuDetails skuDetails = new IabSkuDetails(ITEM_TYPE_INAPP,
                                i.getSku(), i.getPrice(), i.getTitle(), i.getDescription());
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
                    StoreUtils.LogError(TAG, msg);
                    IabResult result = new IabResult(IabResult.BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE, msg);
                    purchaseFailed(result, null);
                    break;
                case ALREADY_ENTITLED:

                    purchase = new IabPurchase(ITEM_TYPE_INAPP, mLastOperationSKU, "", response.getRequestId(), 0);

                    msg = "The purchase has failed. Entitlement already entitled.";
                    StoreUtils.LogError(TAG, msg);
                    result = new IabResult(IabResult.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED, msg);
                    purchaseFailed(result, purchase);
                    break;
                default:
                    msg = "The purchase has failed. No message.";
                    StoreUtils.LogError(TAG, msg);
                    result = new IabResult(IabResult.BILLING_RESPONSE_RESULT_ERROR, msg);
                    purchaseFailed(result, null);
                    break;
            }
        }



        public void onPurchaseUpdatesResponse(final PurchaseUpdatesResponse response) {
            if (mCurrentUserID != null && !mCurrentUserID.equals(response.getUserId())) {
                StoreUtils.LogError(TAG, "The updates is not for the current user id.");
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
                        StoreUtils.LogDebug(TAG, "Initiating Another Purchase Updates with offset: "
                            + newOffset.toString());
                        PurchasingManager.initiatePurchaseUpdatesRequest(newOffset);
                    } else {
                        AmazonIabHelper.this.restorePurchasesSuccess(mInventory);
                        mInventory = null;
                    }

                    break;

                case FAILED:
                    StoreUtils.LogError(TAG, "There was an error while trying to restore purchases. " +
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

        public void onGetUserIdResponse(final GetUserIdResponse response) {
            if (response.getUserIdRequestStatus() ==
                    GetUserIdResponse.GetUserIdRequestStatus.SUCCESSFUL) {
                mCurrentUserID = response.getUserId();
                AmazonIabHelper.this.setupSuccess();
            } else {
                String msg = "Unable to get userId";
                StoreUtils.LogError(TAG, msg);
                IabResult result = new IabResult(IabResult.BILLING_RESPONSE_RESULT_ERROR, msg);
                AmazonIabHelper.this.setupFailed(result);
            }
        }


        private static final String TAG = "SOOMLA AmazonIabHelper PurchasingObserver";

        private String mCurrentUserID = null;
        private IabInventory mInventory;

    }
}