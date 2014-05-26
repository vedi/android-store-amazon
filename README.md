*This project is a billing provider plugin to [android-store](https://github.com/soomla/android-store).*


## android-store-amazon

android-store-amazon is a billing service plugin for android-store.


## Getting Started

In order to work with this plugin you first need to go over android-store's [Getting Started](https://github.com/soomla/android-store#getting-started).

The steps to integrate this billing service are also in android-store's [Selecting Billing Service](https://github.com/soomla/android-store#amazon) but we will also write them here for convenience:


1. Add `in-app-purchasing-1.0.3.jar` (from `libs` folder) and `AndroidStoreAmazon.jar` (from `build` folder) to your project.

2. Make the following changes in AndroidManifest.xml:

Add amazon's ResponseReceiver to your `application` element. Also, you need to tell us what plugin you're using so add a meta-data tag for that:

  ```xml
  <receiver android:name = "com.amazon.inapp.purchasing.ResponseReceiver" >
    <intent-filter>
      <action android:name = "com.amazon.inapp.purchasing.NOTIFY"
              android:permission = "com.amazon.inapp.purchasing.Permission.NOTIFY" />
    </intent-filter>
  </receiver>
  <meta-data android:name="billing.service" android:value="amazon.AmazonIabService" />
  ```

## Contribution


We want you!

Fork -> Clone -> Implement -> Test -> Pull-Request. We have great RESPECT for contributors.

## SOOMLA, Elsewhere ...


+ [Framework Page](http://project.soom.la/)
+ [On Facebook](https://www.facebook.com/pages/The-SOOMLA-Project/389643294427376)
+ [On AngelList](https://angel.co/the-soomla-project)

## License

MIT License. Copyright (c) 2014 SOOMLA. http://soom.la
+ http://www.opensource.org/licenses/MIT
