package com.soomla.store.billing.amazon;

import com.soomla.Soomla;
import com.soomla.SoomlaUtils;

import java.util.Currency;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AmazonIabUtils {

    /**
     * Gets currency code from market place code
     *
     * @param marketPlace The market place code, for example US
     * @return Currency code corresponding to marketPlace
     *
     */
    public static String getCurrencyCode(String marketPlace){
        try{
            Locale locale = new Locale("", marketPlace);
            return Currency.getInstance(locale).getCurrencyCode();
        }catch (Exception ex){
            SoomlaUtils.LogError(TAG, "Error while retrieving currency code from market place: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Gets the price micros from price,
     * For example, if price is "€7.99", return is "7990000".
     *
     * @param price
     * @return Price micros amount for the given price string, 0 by default
     */
    public static long getPriceAmountMicros(String price) {
        long priceAmountMicros = 0;
        Pattern regex = Pattern.compile("\\d+(\\.\\d+)?");
        Matcher regexMatcher = regex.matcher(price);
        if (regexMatcher.find()) {
            priceAmountMicros = (long)(Double.parseDouble(regexMatcher.group(0)) * MICROS_MULTIPLIER);
        }
        return priceAmountMicros;
    }

    private static final long MICROS_MULTIPLIER = 1000000;
    private static final String TAG = "AmazonIabUtils";
}
